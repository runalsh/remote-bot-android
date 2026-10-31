package com.remotebot.app.util

import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import com.remotebot.domain.model.BotLanguage
import java.util.Locale

object AppLocaleManager {
    fun getLocaleForLanguage(language: BotLanguage): Locale? {
        return when (language) {
            BotLanguage.EN -> Locale.ENGLISH
            BotLanguage.RU -> Locale("ru")
            BotLanguage.AUTO -> null
        }
    }

    fun applyLocale(context: Context, language: BotLanguage) {
        val targetLocale = getLocaleForLanguage(language)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val localeManager = context.getSystemService(LocaleManager::class.java)
            if (localeManager != null) {
                if (targetLocale == null) {
                    localeManager.applicationLocales = LocaleList.getEmptyLocaleList()
                } else {
                    localeManager.applicationLocales = LocaleList(targetLocale)
                }
            }
        }

        val locale = targetLocale ?: Locale.getDefault()
        Locale.setDefault(locale)
        val res = context.resources
        val config = Configuration(res.configuration)
        config.setLocale(locale)
        val localeList = if (targetLocale != null) LocaleList(locale) else LocaleList.getDefault()
        LocaleList.setDefault(localeList)
        config.setLocales(localeList)
        @Suppress("DEPRECATION")
        res.updateConfiguration(config, res.displayMetrics)
    }

    fun wrapContext(context: Context, language: BotLanguage): Context {
        val targetLocale = getLocaleForLanguage(language) ?: return context
        val config = Configuration(context.resources.configuration)
        config.setLocale(targetLocale)
        val localeList = LocaleList(targetLocale)
        LocaleList.setDefault(localeList)
        config.setLocales(localeList)
        return context.createConfigurationContext(config)
    }
}
