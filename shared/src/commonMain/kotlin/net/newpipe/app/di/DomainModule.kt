package net.newpipe.app.di

import org.koin.dsl.module
import net.newpipe.app.domain.HomeViewModel

val domainModule = module {
    factory { HomeViewModel(get()) }
}
