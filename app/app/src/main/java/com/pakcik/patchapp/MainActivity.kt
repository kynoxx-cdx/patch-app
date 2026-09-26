package com.pakcik.patchapp

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : AppCompatActivity() {

    private lateinit var etKey: EditText
    private lateinit var btnLogin: Button
    private lateinit var btnInject: Button
    private lateinit var tvStatus: TextView
    private lateinit var rgGame: RadioGroup

    private var selectedPackage: String = ""
    private var licenseKey: String = ""
    private var hwid: String = ""

    private val SHIZUKU_REQUEST_CODE = 1001
    private val MANAGE_STORAGE_CODE = 1002
    private val SERVER_URL = "https://novaext.my.id"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        etKey = findViewById(R.id.etKey)
        btnLogin = findViewById(R.id.btnLogin)
        btnInject = findViewById(R.id.btnInject)
        tvStatus = findViewById(R.id.tvStatus)
        rgGame = findViewById(R.id.rgGame)

        hwid = HwidHelper.getHwid(this)
        tvStatus.text = "HWID: $hwid"

        Shizuku.addRequestPermissionResultListener { requestCode, grantResult ->
            if (requestCode == SHIZUKU_REQUEST_CODE) {
                if (grantResult == PackageManager.PERMISSION_GRANTED) {
                    tvStatus.text = "✓ Izin Shizuku diberikan"
                    pushFiles()
                } else {
                    tvStatus.text = "✗ Izin Shizuku ditolak"
                }
            }
        }

        btnLogin.setOnClickListener {
            val key = etKey.text.toString().trim().uppercase()
            if (key.isEmpty()) {
                toast("Masukkan license key")
                return@setOnClickListener
            }
            verifyLicense(key)
        }

        btnInject.setOnClickListener {
            val checked = rgGame.checkedRadioButtonId
            if (checked == -1) {
                toast("Pilih game dulu")
                return@setOnClickListener
            }
            selectedPackage = if (checked == R.id.rbFFMax) {
                "com.dts.freefiremax"
            } else {
                "com.dts.freefireth"
            }

            if (!FfDetector.isInstalled(this, selectedPackage)) {
                toast("Game belum terinstall")
                return@setOnClickListener
            }
            if (licenseKey.isEmpty()) {
                toast("Cek license dulu")
                return@setOnClickListener
            }

            if (!hasStoragePermission()) {
                requestStoragePermission()
            } else if (!Shizuku.pingBinder()) {
                toast("Shizuku belum aktif")
                requestShizuku()
            } else if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                requestShizuku()
            } else {
                pushFiles()
            }
        }

        checkStoragePermission()
    }

    private fun verifyLicense(key: String) {
        btnLogin.isEnabled = false
        tvStatus.text = "Cek license..."

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val url = URL("$SERVER_URL/api/user/verify")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.doOutput = true
                conn.connectTimeout = 10000
                conn.readTimeout = 10000

                val body = """{"key":"$key"}"""
                conn.outputStream.write(body.toByteArray())

                val response = conn.inputStream.bufferedReader().readText()
                conn.disconnect()

                withContext(Dispatchers.Main) {
                    btnLogin.isEnabled = true
                    if (response.contains("\"ok\":true")) {
                        licenseKey = key
                        tvStatus.text = "✓ License valid"
                        toast("License valid")
                    } else {
                        val err = response.substringAfter("\"error\":\"").substringBefore("\"")
                        tvStatus.text = "✗ $err"
                        toast("Invalid: $err")
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    btnLogin.isEnabled = true
                    tvStatus.text = "✗ Error: ${e.message}"
                    toast("Error: ${e.message}")
                }
            }
        }
    }

    private fun pushFiles() {
        tvStatus.text = "Download & tempel file..."

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val targetDir = "/storage/emulated/0/Android/data/$selectedPackage/files"
                val tempDir = File(cacheDir, "patch")
                tempDir.mkdirs()

                val files = listOf("localConfig.json", "Assembly-CSharp-patch.bytes")

                files.forEach { name ->
                    val url = URL("$SERVER_URL/api/download/$licenseKey/$name?hwid=$hwid")
                    val conn = url.openConnection() as HttpURLConnection
                    conn.connectTimeout = 15000
                    conn.readTimeout = 15000
                    val input = conn.inputStream
                    val out = FileOutputStream(File(tempDir, name))
                    input.copyTo(out)
                    out.close()
                    input.close()
                }

                files.forEach { name ->
                    val src = File(tempDir, name).absolutePath
                    val dst = "$targetDir/$name"
                    val process = Shizuku.newProcess(arrayOf("cp", src, dst), null, null)
                    process.waitFor()
                }

                withContext(Dispatchers.Main) {
                    tvStatus.text = "✓ Patch terpasang"
                    AlertDialog.Builder(this@MainActivity)
                        .setTitle("Berhasil")
                        .setMessage("File berhasil dipasang.\n\nBuka game?")
                        .setPositiveButton("Buka") { _, _ ->
                            FfLauncher.launch(this@MainActivity, selectedPackage)
                        }
                        .setNegativeButton("Nanti", null)
                        .show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    tvStatus.text = "✗ Error: ${e.message}"
                    toast("Gagal: ${e.message}")
                }
            }
        }
    }

    private fun hasStoragePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
                PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                intent.data = Uri.parse("package:$packageName")
                startActivityForResult(intent, MANAGE_STORAGE_CODE)
            } catch (e: Exception) {
                startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
            }
        }
    }

    private fun requestShizuku() {
        if (!Shizuku.pingBinder()) {
            toast("Shizuku belum aktif — buka app Shizuku dulu")
            return
        }
        if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
            pushFiles()
        } else {
            Shizuku.requestPermission(SHIZUKU_REQUEST_CODE)
        }
    }

    private fun checkStoragePermission() {
        if (hasStoragePermission()) {
            tvStatus.text = "Storage OK"
        } else {
            tvStatus.text = "Butuh izin storage"
        }
    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == MANAGE_STORAGE_CODE) checkStoragePermission()
    }
}
