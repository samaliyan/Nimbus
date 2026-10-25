package com.samaliyan.nimbus

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.res.Configuration
import com.samaliyan.nimbus.core.SettingsStore
import java.util.Locale

class NimbusApp : Application() {
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(Locales.wrap(base))
    }

    override fun onCreate() {
        super.onCreate()
        SettingsStore.init(this)
        val nm = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_CONNECTION,
            getString(R.string.channel_connection),
            NotificationManager.IMPORTANCE_LOW,
        ).apply { setShowBadge(false) }
        nm.createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_CONNECTION = "connection"
    }
}

/** The app's own language choice, applied to every context the app shows text from. */
object Locales {
    fun locale(context: Context): Locale {
        SettingsStore.init(context)
        return Locale.forLanguageTag(SettingsStore.current.lang.tag)
    }

    fun wrap(base: Context): Context {
        val locale = locale(base)
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        config.setLayoutDirection(locale)
        return base.createConfigurationContext(config)
    }
}
