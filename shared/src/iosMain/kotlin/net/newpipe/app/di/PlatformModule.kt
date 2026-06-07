package net.newpipe.app.di

import org.koin.core.module.Module
import org.koin.dsl.module
import net.newpipe.app.di.settings.provideSettings

actual val platformModule: Module = module {
    single { provideSettings() }
}
