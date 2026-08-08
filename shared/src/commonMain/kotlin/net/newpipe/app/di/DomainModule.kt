package net.newpipe.app.di

import net.newpipe.app.domain.HomeViewModel
import net.newpipe.app.domain.PlayerViewModel
import net.newpipe.app.domain.SettingsViewModel
import net.newpipe.app.domain.DownloadViewModel
import org.koin.dsl.module

val domainModule = module {
    single { SettingsViewModel(get()) }
    factory { HomeViewModel(get(), get()) }
    factory { PlayerViewModel() }
    factory { DownloadViewModel() }
}
