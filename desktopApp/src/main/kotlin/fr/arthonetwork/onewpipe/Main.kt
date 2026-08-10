/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package fr.arthonetwork.onewpipe

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
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
    // Geo-localize trending and search (YouTube gl/hl params) from the system
    // locale, so a French user gets French content instead of US content.
    net.newpipe.app.backend.applySystemGeoLocalization()
    // Do not fetch the optional iOS player response on the desktop fast path.
    // It adds another YouTube request even though playback starts from the
    // progressive Android stream; a real integration run resolved the same
    // video in about 2.2s with this disabled.
    org.schabi.newpipe.extractor.services.youtube.extractors.YoutubeStreamExtractor.setFetchIosClient(false)
    KoinApp.init()
    
    application {
        val windowState = rememberWindowState(width = 1440.dp, height = 900.dp)
        Window(
            onCloseRequest = ::exitApplication,
            title = "ONewPipe",
            state = windowState
        ) {
            App()
        }
    }
}
