package net.newpipe.app.backend

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.media.app.NotificationCompat.MediaStyle
import android.support.v4.media.MediaMetadataCompat
import androidx.media.session.MediaButtonReceiver
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.media3.common.Player
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URL

/**
 * Bridges the Compose Media3 player to Android's system media controls.
 * The notification exposes play/pause, seek, previous and next actions and
 * publishes duration/current position so Android can render an interactive
 * timeline in the media controls panel.
 */
class MediaNotificationController(
    private val context: Context,
    private val player: Player,
    private val onPrevious: () -> Unit,
    private val onNext: () -> Unit
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val notificationManager = NotificationManagerCompat.from(context)
    private val mediaSession = MediaSessionCompat(context, "ONewPipeComposePlayer")
    private var title = "ONewPipe"
    private var artist = ""
    private var artworkUrl = ""
    private var artwork: Bitmap? = null

    init {
        createNotificationChannel()
        mediaSession.setCallback(object : MediaSessionCompat.Callback() {
            override fun onPlay() = player.play()
            override fun onPause() = player.pause()
            override fun onSeekTo(pos: Long) = player.seekTo(pos)
            override fun onSkipToPrevious() = onPrevious()
            override fun onSkipToNext() = onNext()
        })
        mediaSession.isActive = true
    }

    fun updateMetadata(title: String, artist: String, artworkUrl: String?) {
        this.title = title.ifBlank { "ONewPipe" }
        this.artist = artist
        val nextArtworkUrl = artworkUrl.orEmpty()
        if (nextArtworkUrl != this.artworkUrl) {
            this.artworkUrl = nextArtworkUrl
            this.artwork = null
            if (nextArtworkUrl.isNotBlank()) loadArtwork(nextArtworkUrl)
        }
        publish()
    }

    fun updatePlaybackState() {
        val duration = player.duration.takeIf { it > 0 } ?: 0L
        val position = player.currentPosition.coerceAtLeast(0L)
        val state = when {
            player.playbackState == Player.STATE_BUFFERING -> PlaybackStateCompat.STATE_BUFFERING
            player.isPlaying -> PlaybackStateCompat.STATE_PLAYING
            player.playbackState == Player.STATE_ENDED -> PlaybackStateCompat.STATE_STOPPED
            else -> PlaybackStateCompat.STATE_PAUSED
        }
        val actions = PlaybackStateCompat.ACTION_PLAY or
            PlaybackStateCompat.ACTION_PAUSE or
            PlaybackStateCompat.ACTION_PLAY_PAUSE or
            PlaybackStateCompat.ACTION_SEEK_TO or
            PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
            PlaybackStateCompat.ACTION_SKIP_TO_NEXT
        mediaSession.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setActions(actions)
                .setState(state, position, if (player.isPlaying) 1f else 0f)
                .setBufferedPosition(player.bufferedPosition.coerceAtLeast(0L))
                .build()
        )
        mediaSession.setMetadata(
            MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, title)
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, artist)
                .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, "ONewPipe")
                .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, duration)
                .apply { artwork?.let { putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, it) } }
                .build()
        )
        publish()
    }

    fun release() {
        scope.cancel()
        mediaSession.isActive = false
        mediaSession.release()
        runCatching { notificationManager.cancel(NOTIFICATION_ID) }
    }

    private fun publish() {
        val icon = context.resources.getIdentifier(
            "onewpipe_logo_mark",
            "drawable",
            context.packageName
        ).takeIf { it != 0 } ?: android.R.drawable.ic_media_play
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(icon)
            .setContentTitle(title)
            .setContentText(artist)
            .setLargeIcon(artwork)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setOngoing(player.isPlaying)
            .setStyle(
                MediaStyle()
                    .setMediaSession(mediaSession.sessionToken)
                    .setShowActionsInCompactView(0, 1, 2)
            )
            .addAction(
                NotificationCompat.Action(
                    android.R.drawable.ic_media_previous,
                    "Previous",
                    MediaButtonReceiver.buildMediaButtonPendingIntent(
                        context,
                        PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
                    )
                )
            )
            .addAction(
                NotificationCompat.Action(
                    if (player.isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                    if (player.isPlaying) "Pause" else "Play",
                    MediaButtonReceiver.buildMediaButtonPendingIntent(
                        context,
                        PlaybackStateCompat.ACTION_PLAY_PAUSE
                    )
                )
            )
            .addAction(
                NotificationCompat.Action(
                    android.R.drawable.ic_media_next,
                    "Next",
                    MediaButtonReceiver.buildMediaButtonPendingIntent(
                        context,
                        PlaybackStateCompat.ACTION_SKIP_TO_NEXT
                    )
                )
            )
        runCatching { notificationManager.notify(NOTIFICATION_ID, builder.build()) }
    }

    private fun loadArtwork(url: String) {
        scope.launch {
            val bitmap = runCatching {
                (URL(url).openConnection().apply {
                    connectTimeout = 4_000
                    readTimeout = 6_000
                }).getInputStream().use(BitmapFactory::decodeStream)
            }.getOrNull()
            if (bitmap != null && url == artworkUrl) {
                withContext(Dispatchers.Main) {
                    artwork = bitmap
                    publish()
                }
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "ONewPipe playback",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Playback controls and current video information"
                    setShowBadge(false)
                }
            )
        }
    }

    companion object {
        private const val CHANNEL_ID = "onewpipe_playback"
        private const val NOTIFICATION_ID = 2107
    }
}
