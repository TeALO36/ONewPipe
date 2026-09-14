package net.newpipe.app.backend

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.serialization.json.Json
import net.newpipe.app.ComposeActivity
import net.newpipe.app.di.settings.provideSettings
import net.newpipe.app.domain.MediaItem
import net.newpipe.app.domain.NewVideo
import net.newpipe.app.domain.NewVideosTracker
import net.newpipe.app.domain.SettingsViewModel
import net.newpipe.app.domain.Subscription
import okhttp3.OkHttpClient
import org.schabi.newpipe.extractor.NewPipe
import java.util.concurrent.TimeUnit

/**
 * Periodically loads the newest videos of the channels followed in the app
 * and posts a notification for the ones published since the previous check.
 */
class NewVideosWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val settings = provideSettings(applicationContext)
        val forced = inputData.getBoolean(KEY_FORCED, false)
        if (!forced && !settings.getBoolean(SettingsViewModel.KEY_NEW_VIDEO_NOTIFICATIONS, false)) {
            return Result.success()
        }
        val subscriptions = runCatching {
            json.decodeFromString<List<Subscription>>(
                settings.getString(SettingsViewModel.KEY_SUBSCRIPTIONS, "[]")
            )
        }.getOrDefault(emptyList())
        if (subscriptions.isEmpty()) return Result.success()

        // The worker can run in a fresh process where the UI never started.
        if (NewPipe.getDownloader() == null) {
            NewPipe.init(OkHttpDownloader(OkHttpClient.Builder().build()))
        }

        val repository = NewPipeMediaRepository()
        val latest = mutableMapOf<String, List<MediaItem>>()
        for (subscription in subscriptions.take(MAX_CHANNELS)) {
            val serviceId = runCatching { NewPipe.getServiceByUrl(subscription.url).serviceId }
                .getOrNull() ?: continue
            runCatching { repository.getChannel(serviceId, subscription.url).items }
                .getOrNull()
                ?.let { latest[subscription.url] = it.take(VIDEOS_PER_CHANNEL) }
        }
        if (latest.isEmpty()) return Result.retry()

        val result = NewVideosTracker.compare(
            subscriptions = subscriptions,
            latest = latest,
            seen = NewVideosTracker.decode(settings.getString(NewVideosTracker.KEY_SEEN, ""))
        )
        settings.putString(NewVideosTracker.KEY_SEEN, NewVideosTracker.encode(result.seen))
        if (result.newVideos.isNotEmpty()) notify(applicationContext, result.newVideos)
        return Result.success()
    }

    private fun notify(context: Context, videos: List<NewVideo>) {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "New videos",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply { description = "New videos from the channels you follow" }
            )
        }
        val icon = context.resources.getIdentifier("onewpipe_logo_mark", "drawable", context.packageName)
            .takeIf { it != 0 } ?: android.R.drawable.ic_dialog_info
        val openApp = PendingIntent.getActivity(
            context,
            REQUEST_CODE,
            Intent(context, ComposeActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(icon)
            .setContentIntent(openApp)
            .setAutoCancel(true)
        if (videos.size == 1) {
            val (channel, video) = videos.single()
            builder.setContentTitle(video.title).setContentText(channel.name)
        } else {
            val channels = videos.map { it.channel.name }.distinct()
            builder.setContentTitle("${videos.size} new videos")
                .setContentText(channels.joinToString(", "))
                .setStyle(
                    NotificationCompat.InboxStyle().also { style ->
                        videos.take(6).forEach { style.addLine("${it.channel.name} · ${it.video.title}") }
                    }
                )
        }
        runCatching { manager.notify(NOTIFICATION_ID, builder.build()) }
    }

    companion object {
        const val KEY_FORCED = "forced"
        private const val CHANNEL_ID = "onewpipe_new_videos"
        private const val NOTIFICATION_ID = 2201
        private const val REQUEST_CODE = 2202
        private const val MAX_CHANNELS = 50
        private const val VIDEOS_PER_CHANNEL = 10
        private val json = Json { ignoreUnknownKeys = true }
    }
}

/** Starts, stops or immediately runs the background check for new videos. */
object NewVideosScheduler {
    private const val PERIODIC_WORK = "onewpipe_new_videos"
    private const val IMMEDIATE_WORK = "onewpipe_new_videos_now"

    private val networkConstraint = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    fun apply(context: Context, enabled: Boolean) {
        val workManager = WorkManager.getInstance(context)
        if (enabled) {
            workManager.enqueueUniquePeriodicWork(
                PERIODIC_WORK,
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<NewVideosWorker>(3, TimeUnit.HOURS)
                    .setConstraints(networkConstraint)
                    // A periodic request otherwise runs at once. The job is
                    // started on the main thread, which is still busy while the
                    // app launches, and Android reported an ANR
                    // ("No response to onStartJob") on a cold start.
                    .setInitialDelay(30, TimeUnit.MINUTES)
                    .build()
            )
        } else {
            workManager.cancelUniqueWork(PERIODIC_WORK)
        }
    }

    fun checkNow(context: Context) {
        WorkManager.getInstance(context).enqueueUniqueWork(
            IMMEDIATE_WORK,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<NewVideosWorker>()
                .setConstraints(networkConstraint)
                .setInputData(androidx.work.workDataOf(NewVideosWorker.KEY_FORCED to true))
                .build()
        )
    }
}
