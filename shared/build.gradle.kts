/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.jetbrains.kotlin.multiplatform)
    alias(libs.plugins.jetbrains.kotlin.compose)
    alias(libs.plugins.jetbrains.compose.multiplatform)
    alias(libs.plugins.jetbrains.kotlinx.serialization)
}

// Better than adding a third-party dependency for something as simple as this
// https://stackoverflow.com/a/74771876/8446131
val buildConfigGenerator by tasks.registering(Sync::class) {
    val buildConfigPackage = NEWPIPE_APPLICATION_ID_NEW
    val rawClass = """
        package $buildConfigPackage

        object BuildConfig {
            const val VERSION_NAME = "$NEWPIPE_VERSION_NAME"
        }
    """.trimIndent()
    from(resources.text.fromString(rawClass)) {
        rename { "BuildConfig.kt" }
        into(buildConfigPackage.replace(".", "/"))
    }
    into(layout.buildDirectory.dir("generated/kotlin/"))
}

kotlin {
    jvmToolchain(17)

    compilerOptions {
        freeCompilerArgs.addAll(
            "-Xexpect-actual-classes"
        )
        optIn.addAll(
            "androidx.compose.material3.ExperimentalMaterial3Api",
            "androidx.compose.material3.ExperimentalMaterial3ExpressiveApi",
            "androidx.compose.foundation.layout.ExperimentalLayoutApi"
        )
    }

    android {
        namespace = NEWPIPE_APPLICATION_ID_NEW
        compileSdk {
            version = release(NEWPIPE_VERSION_SDK_COMPILE_MAJOR) {
                minorApiLevel = NEWPIPE_VERSION_SDK_COMPILE_MINOR
            }
        }
        minSdk {
            version = release(NEWPIPE_VERSION_SDK_MIN)
        }
        androidResources {
            enable = true
        }

        optimization {
            consumerKeepRules.apply {
                publish = true
                file("consumer-proguard-rules.pro")
            }
        }

        withHostTest {
            isIncludeAndroidResources = true
        }
        withDeviceTestBuilder {
            sourceSetTreeName = "test"
        }.configure {
            instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        }
    }

    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
        }
    }
    applyDefaultHierarchyTemplate()

    jvm()

    sourceSets {
        commonMain {
            kotlin.srcDir(buildConfigGenerator.map { it.destinationDir })
            dependencies {
                implementation(libs.jetbrains.compose.runtime)
                implementation(libs.jetbrains.compose.foundation)
                implementation(libs.jetbrains.compose.material3)
                implementation(libs.jetbrains.compose.ui)
                implementation(libs.jetbrains.compose.resources)
                implementation(libs.jetbrains.compose.preview)
                implementation(compose.materialIconsExtended)

                implementation(libs.jetbrains.lifecycle.viewmodel)

                implementation(libs.jetbrains.navigation3.ui)
                implementation(libs.jetbrains.lifecycle.navigation3)
                implementation(libs.kotlinx.serialization.json)

                implementation(libs.koin.compose.viewmodel)

                implementation(libs.russhwolf.settings.core)
                implementation(libs.coil.compose)

                implementation(libs.ktor.client.core)
                implementation(libs.ktor.client.content.negotiation)
                implementation(libs.ktor.serialization.kotlinx.json)
            }
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test.core)
            implementation(libs.jetbrains.compose.test.ui)
            implementation(libs.russhwolf.settings.test)
        }
        androidMain.dependencies {
            implementation(libs.jetbrains.compose.preview)
            implementation(libs.androidx.activity)
            implementation(libs.androidx.preference)
            implementation(libs.newpipe.extractor)
            implementation(libs.squareup.okhttp)
            implementation(libs.coil.network.okhttp)
            implementation(libs.koin.android)
            implementation(libs.media3.exoplayer)
            implementation(libs.media3.ui)
            implementation(libs.ktor.client.okhttp)
        }
        val iosMain by getting {
            dependencies {
                implementation(libs.ktor.client.darwin)
            }
        }
        val jvmMain by getting {
            dependencies {
                api(libs.newpipe.extractor)
                api(libs.squareup.okhttp)
                api(libs.coil.network.okhttp)
                implementation(libs.ktor.client.okhttp)
                
                val osName = System.getProperty("os.name").lowercase()
                val osClassifier = when {
                    osName.contains("win") -> "win"
                    osName.contains("mac") -> "mac"
                    osName.contains("nux") || osName.contains("nix") -> "linux"
                    else -> "linux"
                }
                
                implementation("org.openjfx:javafx-base:17.0.2:$osClassifier")
                implementation("org.openjfx:javafx-graphics:17.0.2:$osClassifier")
                implementation("org.openjfx:javafx-media:17.0.2:$osClassifier")
                implementation("org.openjfx:javafx-controls:17.0.2:$osClassifier")
                implementation("org.openjfx:javafx-web:17.0.2:$osClassifier")
                implementation("org.openjfx:javafx-swing:17.0.2:$osClassifier")
                
                implementation("uk.co.caprica:vlcj:4.8.2")
            }
        }
        val androidDeviceTest by getting {
            dependencies {
                implementation(libs.androidx.compose.test.ui.manifest)
                implementation(libs.androidx.compose.test.ui.junit)

                // Needed because androidx.compose.test.ui.junit pulls an older dependency
                // which crashes on new Android versions
                implementation(libs.androidx.test.espresso.core)
            }
        }
        val jvmTest by getting {
            dependencies {
                implementation(compose.desktop.currentOs)
            }
        }
    }
}

dependencies {
    androidRuntimeClasspath(libs.jetbrains.compose.tooling)
}


