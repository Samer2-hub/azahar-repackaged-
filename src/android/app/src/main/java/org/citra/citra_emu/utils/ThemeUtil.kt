// Copyright 2023-2026 Citra Emulator Project / Azahar Emulator Project
// Licensed under GPLv3 or any later version
// Refer to the LICENSE.txt file included.

package org.citra.citra_emu.utils

import android.content.SharedPreferences
import android.graphics.Color
import androidx.annotation.ColorInt
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.preference.PreferenceManager
import kotlin.math.roundToInt
import org.citra.citra_emu.CitraApplication
import org.citra.citra_emu.R
import org.citra.citra_emu.features.settings.model.CitraSettings
import org.citra.citra_emu.ui.main.ThemeProvider

object ThemeUtil {
    const val SYSTEM_BAR_ALPHA = 0.9f

    private val preferences: SharedPreferences get() =
        PreferenceManager.getDefaultSharedPreferences(CitraApplication.appContext)

    private fun getSelectedStaticThemeColor(): Int {
        val themeIndex = preferences.getInt(CitraSettings.PREF_STATIC_THEME_COLOR, 0)
        val themes = arrayOf(
            R.style.Theme_Citra_Blue,
            R.style.Theme_Citra_Cyan,
            R.style.Theme_Citra_Red,
            R.style.Theme_Citra_Green,
            R.style.Theme_Citra_Yellow,
            R.style.Theme_Citra_Orange,
            R.style.Theme_Citra_Violet,
            R.style.Theme_Citra_Pink,
            R.style.Theme_Citra_Gray
        )
        return themes[themeIndex]
    }

    fun setTheme(activity: AppCompatActivity) {
        setThemeMode(activity)
        if (preferences.getBoolean(CitraSettings.PREF_MATERIAL_YOU, false)) {
            activity.setTheme(R.style.Theme_Citra_Main_MaterialYou)
        } else {
            activity.setTheme(getSelectedStaticThemeColor())
        }

        // Nebula redesign: the app is always dark, so black backgrounds apply whenever enabled
        if (preferences.getBoolean(CitraSettings.PREF_BLACK_BACKGROUNDS, false)) {
            activity.setTheme(R.style.ThemeOverlay_Citra_Dark)
        }
    }

    fun setThemeMode(activity: AppCompatActivity) {
        // Nebula redesign: the app is always dark, so the light/dark setting is not applied
        activity.delegate.localNightMode = AppCompatDelegate.MODE_NIGHT_YES
        val windowController = WindowCompat.getInsetsController(
            activity.window,
            activity.window.decorView
        )
        setDarkModeSystemBars(windowController)
    }

    private fun setDarkModeSystemBars(windowController: WindowInsetsControllerCompat) {
        windowController.isAppearanceLightStatusBars = false
        windowController.isAppearanceLightNavigationBars = false
    }

    fun setCorrectTheme(activity: AppCompatActivity) {
        val currentTheme = (activity as ThemeProvider).themeId
        setTheme(activity)
        if (currentTheme != (activity as ThemeProvider).themeId) {
            activity.recreate()
        }
    }

    @ColorInt
    fun getColorWithOpacity(@ColorInt color: Int, alphaFactor: Float): Int = Color.argb(
        (alphaFactor * Color.alpha(color)).roundToInt(),
        Color.red(color),
        Color.green(color),
        Color.blue(color)
    )

    // Listener that detects if the theme keys are being changed from the setting menu and recreates the activity
    private var listener: SharedPreferences.OnSharedPreferenceChangeListener? = null

    fun themeChangeListener(activity: AppCompatActivity) {
        listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            val relevantKeys =
                listOf(
                    CitraSettings.PREF_STATIC_THEME_COLOR,
                    CitraSettings.PREF_MATERIAL_YOU,
                    CitraSettings.PREF_BLACK_BACKGROUNDS
                )
            if (key in relevantKeys) {
                activity.recreate()
            }
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)
    }
}
