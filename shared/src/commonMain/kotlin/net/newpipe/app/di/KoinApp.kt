/*
 * SPDX-FileCopyrightText: 2026 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app.di

import org.koin.core.context.startKoin
import org.koin.dsl.KoinAppDeclaration

object KoinApp {
    fun init(appDeclaration: KoinAppDeclaration? = null) {
        startKoin {
            if (appDeclaration != null) {
                appDeclaration()
            }
            modules(domainModule, platformModule)
        }
    }
}
