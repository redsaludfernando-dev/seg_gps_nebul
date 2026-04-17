package com.redsalud.seggpsnebul.location

import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings

actual object DeviceIdProvider {
    private var appContext: Context? = null

    /** Llamar desde MainActivity.onCreate() antes de usar getDeviceId(). */
    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun getAppContext(): android.content.Context =
        appContext ?: throw IllegalStateException("DeviceIdProvider not initialized")

    @SuppressLint("HardwareIds")
    actual fun getDeviceId(): String {
        val ctx = appContext ?: return "unknown-android"
        return Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ANDROID_ID)
            ?: "unknown-android"
    }
}
