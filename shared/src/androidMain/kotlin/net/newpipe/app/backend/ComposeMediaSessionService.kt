package net.newpipe.app.backend

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

/**
 * Owns the Compose player's ExoPlayer and Media3 session independently of the
 * activity. Media3 keeps this service foreground while playback is active and
 * publishes the system media notification from the session state.
 */
class ComposeMediaSessionService : MediaSessionService() {
    private lateinit var exoPlayer: ExoPlayer
    private lateinit var session: MediaSession
    private lateinit var sessionPlayer: Player

    private var previousCallback: (() -> Unit)? = null
    private var nextCallback: (() -> Unit)? = null

    override fun onCreate() {
        super.onCreate()

        exoPlayer = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(this))
            .build()

        // The forwarding player gives notification and headset commands a
        // useful previous/next action even though the Compose UI owns queue
        // navigation callbacks.
        sessionPlayer = object : ForwardingPlayer(exoPlayer) {
            override fun seekToPreviousMediaItem() {
                previousCallback?.invoke() ?: super.seekToPreviousMediaItem()
            }

            override fun seekToNextMediaItem() {
                nextCallback?.invoke() ?: super.seekToNextMediaItem()
            }
        }
        session = MediaSession.Builder(this, sessionPlayer).build()
        instance = this
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession = session

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // The service is deliberately sticky while audio is playing. The media
        // session can then be recreated by Android after process pressure and
        // the activity is not required for pause/resume controls.
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Do not stop or release an active player when the user swipes the
        // activity away. This is the behavior expected from a media service.
        val playbackOngoing = exoPlayer.playWhenReady &&
            exoPlayer.playbackState != Player.STATE_IDLE &&
            exoPlayer.playbackState != Player.STATE_ENDED
        if (!playbackOngoing) stopSelf()
    }

    fun player(): ExoPlayer = exoPlayer

    fun playVideo(
        videoUrl: String,
        audioUrl: String?,
        title: String,
        artist: String,
        thumbnailUrl: String?,
        startPositionMs: Long
    ) {
        if (videoUrl.isBlank()) return

        val metadata = MediaMetadata.Builder()
            .setTitle(title.ifBlank { "ONewPipe" })
            .setArtist(artist)
            .setAlbumTitle("ONewPipe")
            .apply {
                thumbnailUrl.orEmpty().takeIf { it.isNotBlank() }?.let {
                    setArtworkUri(Uri.parse(it))
                }
            }
            .build()
        val videoItem = MediaItem.Builder()
            .setMediaId(videoUrl)
            .setUri(Uri.parse(videoUrl))
            .setMediaMetadata(metadata)
            .build()

        val mediaSourceFactory = DefaultMediaSourceFactory(this)
        val videoSource = mediaSourceFactory.createMediaSource(videoItem)
        val source = if (!audioUrl.isNullOrBlank()) {
            val audioItem = MediaItem.Builder()
                .setMediaId("$videoUrl#audio")
                .setUri(Uri.parse(audioUrl))
                .build()
            MergingMediaSource(
                videoSource,
                mediaSourceFactory.createMediaSource(audioItem)
            )
        } else {
            videoSource
        }

        exoPlayer.setMediaSource(source, startPositionMs.coerceAtLeast(0L))
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
    }

    fun setNavigationCallbacks(previous: (() -> Unit)?, next: (() -> Unit)?) {
        previousCallback = previous
        nextCallback = next
    }

    override fun onDestroy() {
        if (::session.isInitialized) session.release()
        if (::exoPlayer.isInitialized) exoPlayer.release()
        if (instance === this) instance = null
        super.onDestroy()
    }

    companion object {
        @Volatile
        var instance: ComposeMediaSessionService? = null
            private set

        /** Start from the visible activity; the service then survives activity/task removal. */
        fun start(context: Context) {
            val intent = Intent(context, ComposeMediaSessionService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ContextCompat.startForegroundService(context, intent)
            } else {
                context.startService(intent)
            }
        }
    }
}
