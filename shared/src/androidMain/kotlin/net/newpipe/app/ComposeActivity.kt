/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import kotlinx.serialization.json.Json
import net.newpipe.Constants
import net.newpipe.app.navigation.Screen

import org.schabi.newpipe.extractor.NewPipe
import net.newpipe.app.backend.OkHttpDownloader
import okhttp3.OkHttpClient
import org.koin.android.ext.koin.androidContext

/**
 * Entry point for compose-related UI components on Android
 */
class ComposeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        try {
            NewPipe.init(OkHttpDownloader(OkHttpClient.Builder().build()))
            // Geo-localize trending and search (YouTube gl/hl params) from the system
            // locale, so a French user gets French content instead of US content.
            net.newpipe.app.backend.applySystemGeoLocalization()
            // YouTube throttles the plain WEB client to ~360p; the iOS client returns
            // the full format range (720p/1080p/4K + audio) without a poToken.
            org.schabi.newpipe.extractor.services.youtube.extractors.YoutubeStreamExtractor.setFetchIosClient(true)
            net.newpipe.app.di.KoinApp.init {
                androidContext(this@ComposeActivity)
            }
        } catch (e: Exception) {
            // Already initialized
        }

        setContent {
            App(
                // TODO: Change when everything is in compose and this is the primary activity
                startDestination = Json.decodeFromString<Screen>(
                    intent.getStringExtra(Constants.INTENT_SCREEN_KEY)!!
                )
            )
        }
    }
}
