package net.newpipe.app.di

import org.koin.dsl.module
import org.koin.core.module.Module
import net.newpipe.app.di.settings.provideSettings
import net.newpipe.app.backend.backendModule

actual val platformModule: Module = module {
    includes(backendModule)
    single { provideSettings() }
}
