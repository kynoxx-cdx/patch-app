package com.pakcik.patchapp

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.provider.Settings

object HwidHelper {
    @SuppressLint("HardwareIds")
    fun getHwid(context: Context): String {
        val androidId = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ANDROID_ID
        ) ?: "unknown"
        val fingerprint = Build.FINGERPRINT.hashCode().toString()
        return "$androidId-$fingerprint"
    }
}
