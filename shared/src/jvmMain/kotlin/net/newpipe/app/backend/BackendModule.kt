package net.newpipe.app.backend

import org.koin.dsl.module
import net.newpipe.app.domain.MediaRepository

val backendModule = module {
    single<MediaRepository> { NewPipeMediaRepository() }
}
