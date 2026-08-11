package net.newpipe.app

import net.newpipe.app.domain.GitHubRelease
import net.newpipe.app.domain.UpdateInstallState

/** Platform bridge for downloading and handing an update APK to the OS installer. */
expect object UpdateInstaller {
    fun initialize(context: Any)
    fun install(release: GitHubRelease, onState: (UpdateInstallState) -> Unit)
    fun resumePendingInstall()
}
