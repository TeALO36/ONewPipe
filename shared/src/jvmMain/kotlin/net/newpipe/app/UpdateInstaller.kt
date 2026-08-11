package net.newpipe.app

import net.newpipe.app.domain.GitHubRelease
import net.newpipe.app.domain.UpdateInstallState

actual object UpdateInstaller {
    actual fun initialize(context: Any) = Unit

    actual fun install(release: GitHubRelease, onState: (UpdateInstallState) -> Unit) {
        openExternalUrl(release.html_url)
        onState(UpdateInstallState.Unsupported)
    }

    actual fun resumePendingInstall() = Unit
}
