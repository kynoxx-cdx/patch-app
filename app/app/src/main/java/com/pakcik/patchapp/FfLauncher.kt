package com.pakcik.patchapp

import android.content.Context
import android.content.Intent

object FfLauncher {
    fun launch(context: Context, packageName: String) {
        val intent = context.packageManager.getLaunchIntentForPackage(packageName)
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }
}
