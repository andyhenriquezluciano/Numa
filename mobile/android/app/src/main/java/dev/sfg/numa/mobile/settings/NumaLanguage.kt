package dev.sfg.numa.mobile.settings

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList

object NumaLanguage {
    fun selected(context: Context): String = if (Build.VERSION.SDK_INT >= 33) {
        context.getSystemService(LocaleManager::class.java).applicationLocales.toLanguageTags()
    } else context.getSharedPreferences("interface", Context.MODE_PRIVATE).getString("language", "").orEmpty()

    fun apply(activity: Activity, tag: String) {
        require(tag in listOf("", "es", "en"))
        activity.getSharedPreferences("interface", Context.MODE_PRIVATE).edit().putString("language", tag).apply()
        if (Build.VERSION.SDK_INT >= 33) {
            activity.getSystemService(LocaleManager::class.java).applicationLocales = LocaleList.forLanguageTags(tag)
        } else activity.recreate()
    }

    fun wrap(context: Context): Context {
        val requested = selected(context)
        val locale = if (requested.isBlank()) android.content.res.Resources.getSystem().configuration.locales[0]
            else java.util.Locale.forLanguageTag(requested)
        java.util.Locale.setDefault(locale)
        if (Build.VERSION.SDK_INT >= 33) return context
        val tag = selected(context)
        if (tag.isBlank()) return context
        val config = Configuration(context.resources.configuration)
        config.setLocales(LocaleList.forLanguageTags(tag))
        return context.createConfigurationContext(config)
    }
}
