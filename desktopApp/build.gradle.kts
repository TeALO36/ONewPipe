/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.jetbrains.kotlin.jvm)
    alias(libs.plugins.jetbrains.kotlin.compose)
    alias(libs.plugins.jetbrains.compose.multiplatform)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(projects.shared)
    implementation(compose.desktop.currentOs)
    implementation(libs.jetbrains.coroutines.swing)
    implementation(libs.jetbrains.compose.preview)
}

compose.desktop {
    application {
        mainClass = "fr.arthonetwork.onewpipe.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = NEWPIPE_APPLICATION_ID_NEW
            packageVersion = System.getProperty("versionNameOverride") ?: NEWPIPE_VERSION_NAME

            // vlcj's video surface needs sun.misc.Unsafe, which lives in the
            // jdk.unsupported module. jpackage/jlink omits it by default, which
            // makes the video player fail with "NoClassDefFoundError: sun/misc/Unsafe"
            // (black screen) in packaged builds.
            modules("jdk.unsupported")
        }
    }
}
