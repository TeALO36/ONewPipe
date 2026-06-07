/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.di.settings

import android.content.Context
import androidx.preference.PreferenceManager
import com.russhwolf.settings.Settings
import com.russhwolf.settings.SharedPreferencesSettings

/**
 * Settings for Android devices based on SharedPreferences
 */
fun provideSettings(context: Context): Settings = SharedPreferencesSettings(
    PreferenceManager.getDefaultSharedPreferences(context)
)
