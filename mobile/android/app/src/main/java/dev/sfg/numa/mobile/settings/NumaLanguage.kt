package dev.sfg.numa.mobile.settings

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList

object NumaLanguage {
    private val mutableLanguage = kotlinx.coroutines.flow.MutableStateFlow("es")
    val language: kotlinx.coroutines.flow.StateFlow<String> = mutableLanguage

    fun selected(context: Context): String {
        val saved = context.getSharedPreferences("interface", Context.MODE_PRIVATE).getString("language", "es").orEmpty()
        val application = if (Build.VERSION.SDK_INT >= 33)
            context.getSystemService(LocaleManager::class.java).applicationLocales.toLanguageTags().substringBefore('-') else saved
        return explicitInterfaceLanguage(application.ifBlank { saved })
    }

    fun apply(activity: Activity, tag: String) {
        require(tag in listOf("es", "en"))
        activity.getSharedPreferences("interface", Context.MODE_PRIVATE).edit().putString("language", tag).apply()
        if (Build.VERSION.SDK_INT >= 33) {
            activity.getSystemService(LocaleManager::class.java).applicationLocales = LocaleList.forLanguageTags(tag)
        } else activity.recreate()
        java.util.Locale.setDefault(java.util.Locale.forLanguageTag(tag))
        mutableLanguage.value = tag
        refreshWidgets(activity)
    }

    private fun refreshWidgets(context: Context) {
        val manager = android.appwidget.AppWidgetManager.getInstance(context)
        for (provider in listOf(dev.sfg.numa.mobile.widget.NumaPlayerWidgetProvider::class.java,
            dev.sfg.numa.mobile.widget.NumaRecentWidgetProvider::class.java)) {
            val component = android.content.ComponentName(context, provider)
            val ids = manager.getAppWidgetIds(component)
            if (ids.isNotEmpty()) context.sendBroadcast(android.content.Intent(android.appwidget.AppWidgetManager.ACTION_APPWIDGET_UPDATE)
                .setComponent(component).putExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_IDS, ids))
        }
    }

    fun wrap(context: Context): Context {
        val tag = selected(context)
        java.util.Locale.setDefault(java.util.Locale.forLanguageTag(tag))
        mutableLanguage.value = tag
        val config = Configuration(context.resources.configuration)
        config.setLocales(LocaleList.forLanguageTags(tag))
        return context.createConfigurationContext(config)
    }
}

internal fun explicitInterfaceLanguage(saved: String): String = if (saved.substringBefore('-') == "en") "en" else "es"
