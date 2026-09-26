package com.pakcik.patchapp

import android.content.Context
import android.content.pm.PackageManager

object FfDetector {
    const val FF_MAX = "com.dts.freefiremax"
    const val FF_TH = "com.dts.freefireth"

    fun isInstalled(context: Context, pkg: String): Boolean {
        return try {
            context.packageManager.getPackageInfo(pkg, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }
}
