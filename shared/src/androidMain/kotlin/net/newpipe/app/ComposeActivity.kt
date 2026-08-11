/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app

import android.Manifest
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import net.newpipe.app.composable.AndroidPlayerWindowState
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import net.newpipe.app.backend.OkHttpDownloader
import okhttp3.OkHttpClient
import org.koin.android.ext.koin.androidContext
import org.schabi.newpipe.extractor.NewPipe

/**
 * Primary mobile entry point for the modern Compose interface.
 */
class ComposeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 779)
        }

        UpdateInstaller.initialize(this)

        try {
            NewPipe.init(OkHttpDownloader(OkHttpClient.Builder().build()))
            // Use the device locale for trending/search results.
            net.newpipe.app.backend.applySystemGeoLocalization()
            // Keep the initial extraction to the fast progressive response. The
            // quality menu explicitly enables the slower iOS response when HD
            // and 4K formats are requested.
            org.schabi.newpipe.extractor.services.youtube.extractors.YoutubeStreamExtractor
                .setFetchIosClient(false)
            net.newpipe.app.di.KoinApp.init {
                androidContext(this@ComposeActivity)
            }
        } catch (_: Exception) {
            // The legacy Android application may already have initialized these singletons.
        }

        // A launcher intent has no navigation payload. App currently owns the home shell,
        // while deep-link destinations can be added here without making startup nullable.
        setContent { App() }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        AndroidPlayerWindowState.isPictureInPicture = isInPictureInPictureMode
    }

    override fun onResume() {
        super.onResume()
        UpdateInstaller.resumePendingInstall()
    }
}
