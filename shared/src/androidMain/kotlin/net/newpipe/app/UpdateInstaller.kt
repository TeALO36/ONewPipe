package net.newpipe.app

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import android.os.Build
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import fr.arthonetwork.onewpipe.BuildConfig
import net.newpipe.app.domain.GitHubRelease
import net.newpipe.app.domain.UpdateInstallState
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

actual object UpdateInstaller {
    private const val UPDATE_DIRECTORY = "updates"
    private const val PROVIDER_SUFFIX = ".update-provider"
    private const val APK_MIME_TYPE = "application/vnd.android.package-archive"
    private const val TAG = "UpdateInstaller"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var applicationContext: Context? = null
    private var pendingInstall: PendingInstall? = null

    private data class PendingInstall(
        val uri: Uri,
        val callback: (UpdateInstallState) -> Unit
    )

    actual fun initialize(context: Any) {
        applicationContext = (context as Context).applicationContext
    }

    actual fun install(release: GitHubRelease, onState: (UpdateInstallState) -> Unit) {
        Log.i(TAG, "Starting update for ${release.tag_name}, assets=${release.assets.size}, apk=${release.androidApk?.name}")
        val context = applicationContext
        if (context == null) {
            onState(UpdateInstallState.Failed("The Android update service is not initialized."))
            return
        }
        val asset = release.androidApk
        if (asset == null) {
            onState(UpdateInstallState.Failed("This release does not contain an Android APK."))
            return
        }

        onState(UpdateInstallState.Downloading(0))
        scope.launch {
            try {
                Log.i(TAG, "Downloading ${asset.name} from GitHub")
                val apk = downloadApk(context, asset.browser_download_url, asset.name, asset.size) { percent ->
                    withContext(Dispatchers.Main.immediate) {
                        onState(UpdateInstallState.Downloading(percent))
                    }
                }
                Log.i(TAG, "Downloaded ${apk.length()} bytes")
                verifyDigest(apk, asset.digest)
                val uri = FileProvider.getUriForFile(
                    context,
                    context.packageName + PROVIDER_SUFFIX,
                    apk
                )
                withContext(Dispatchers.Main.immediate) {
                    Log.i(TAG, "Opening package installer")
                    openInstallerOrPermission(context, uri, onState)
                }
            } catch (error: Exception) {
                Log.e(TAG, "Update failed", error)
                withContext(Dispatchers.Main.immediate) {
                    onState(UpdateInstallState.Failed(error.message ?: "Unable to download the update."))
                }
            }
        }
    }

    actual fun resumePendingInstall() {
        val context = applicationContext ?: return
        val pending = pendingInstall ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !context.packageManager.canRequestPackageInstalls()
        ) {
            pending.callback(UpdateInstallState.WaitingForPermission)
            return
        }
        pendingInstall = null
        pending.callback(UpdateInstallState.OpeningInstaller)
        launchInstaller(context, pending.uri)
    }

    private fun openInstallerOrPermission(
        context: Context,
        uri: Uri,
        callback: (UpdateInstallState) -> Unit
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !context.packageManager.canRequestPackageInstalls()
        ) {
            pendingInstall = PendingInstall(uri, callback)
            callback(UpdateInstallState.WaitingForPermission)
            val settingsIntent = Intent(
                "android.settings.MANAGE_UNKNOWN_APP_SOURCES",
                Uri.parse("package:${context.packageName}")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(settingsIntent)
        } else {
            callback(UpdateInstallState.OpeningInstaller)
            launchInstaller(context, uri)
        }
    }

    private fun launchInstaller(context: Context, uri: Uri) {
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, APK_MIME_TYPE)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            .addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        context.startActivity(intent)
    }

    private suspend fun downloadApk(
        context: Context,
        downloadUrl: String,
        assetName: String,
        expectedSize: Long,
        onProgress: suspend (Int) -> Unit
    ): File = withContext(Dispatchers.IO) {
        // Keep the APK in persistent private storage while Package Installer
        // stages it. Android may evict cache files during a large install.
        val directory = File(context.filesDir, UPDATE_DIRECTORY).apply { mkdirs() }
        val safeName = assetName.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val temporaryFile = File(directory, "$safeName.part")
        val targetFile = File(directory, safeName)
        val connection = (URL(downloadUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            requestMethod = "GET"
            setRequestProperty("User-Agent", "ONewPipe/${BuildConfig.VERSION_NAME}")
            setRequestProperty("Accept", "application/vnd.android.package-archive")
        }

        try {
            if (connection.responseCode !in 200..299) {
                throw IOException("GitHub download failed (HTTP ${connection.responseCode})")
            }
            val totalBytes = connection.contentLengthLong.takeIf { it > 0 } ?: expectedSize
            var downloadedBytes = 0L
            connection.inputStream.buffered().use { input ->
                temporaryFile.outputStream().buffered().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        downloadedBytes += read
                        if (totalBytes > 0) {
                            onProgress((downloadedBytes * 100 / totalBytes).toInt().coerceIn(0, 100))
                        }
                    }
                }
            }
            if (temporaryFile.length() < 1_024) {
                throw IOException("The downloaded APK is empty or incomplete.")
            }
            if (targetFile.exists()) targetFile.delete()
            if (!temporaryFile.renameTo(targetFile)) {
                throw IOException("Unable to finalize the downloaded APK.")
            }
            targetFile
        } finally {
            connection.disconnect()
            if (temporaryFile.exists()) temporaryFile.delete()
        }
    }

    private fun verifyDigest(file: File, expectedDigest: String?) {
        val expected = expectedDigest?.takeIf { it.startsWith("sha256:") }
            ?.removePrefix("sha256:")
            ?.lowercase()
            ?: return
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var read: Int
            while (input.read(buffer).also { read = it } != -1) {
                digest.update(buffer, 0, read)
            }
        }
        val actual = digest.digest().joinToString("") { byte ->
            "%02x".format(byte.toInt() and 0xff)
        }
        if (actual != expected) {
            file.delete()
            throw IOException("The APK checksum does not match the GitHub release.")
        }
    }
}
