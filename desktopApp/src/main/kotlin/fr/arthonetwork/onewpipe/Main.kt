/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package fr.arthonetwork.onewpipe

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import net.newpipe.app.App

import org.schabi.newpipe.extractor.NewPipe
import net.newpipe.app.backend.OkHttpDownloader
import okhttp3.OkHttpClient
import net.newpipe.app.di.KoinApp

/**
 * Entry point for compose-related UI components on Desktop
 */
fun main() {
    NewPipe.init(OkHttpDownloader(OkHttpClient.Builder().build()), org.schabi.newpipe.extractor.localization.Localization("en", "US"))
    KoinApp.init()
    
    application {
        Window(onCloseRequest = ::exitApplication, title = "ONewPipe") {
            App()
        }
    }
}
