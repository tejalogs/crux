package org.crux.common

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Process
import androidx.annotation.OptIn
import androidx.compose.runtime.Stable
import androidx.core.net.toUri
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.DefaultHlsDataSourceFactory
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.session.MediaController
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommands
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import org.crux.data.network.PublicUrlValidator
import org.crux.ui.model.UiPodcast
import org.koin.core.annotation.Provided
import org.koin.core.annotation.Single

@Stable
@Single
internal class PodcastManager(
    @Provided private val context: Context,
) : AutoCloseable {
    private var currentController: MediaController? = null
    private val _currentPodcast = MutableStateFlow<UiPodcast?>(null)
    val currentPodcast: Flow<UiPodcast?> = _currentPodcast.asSharedFlow()

    @OptIn(UnstableApi::class)
    fun playPodcast(podcast: UiPodcast) {
        if (!PublicUrlValidator.isSafePublicHttpUrl(podcast.playbackUrl)) {
            return
        }
        stopPodcast()
        _currentPodcast.value = podcast
        val sessionToken =
            SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val controllerFuture =
            MediaController
                .Builder(context, sessionToken)
                .buildAsync()
        controllerFuture.addListener({
            currentController =
                controllerFuture.get().also {
                    it.playWhenReady = true
                    it.setMediaItem(
                        androidx.media3.common.MediaItem
                            .Builder()
                            .setMediaId("podcast_session")
                            .setUri(podcast.playbackUrl)
                            .setMediaMetadata(
                                androidx.media3.common.MediaMetadata
                                    .Builder()
                                    .setTitle(podcast.title)
                                    .setArtist(podcast.creator.name.innerText)
                                    .setArtworkUri(
                                        podcast.creator.avatar
                                            ?.url
                                            ?.toUri(),
                                    ).build(),
                            ).build(),
                    )
                    it.prepare()
                }
        }, MoreExecutors.directExecutor())
    }

    fun stopPodcast() {
        currentController?.stop()
        currentController?.release()
        currentController = null
        _currentPodcast.value = null
    }

    override fun close() {
        stopPodcast()
    }
}

internal class PlaybackService : MediaSessionService() {
    private var mediaSession: MediaSession? = null

    @OptIn(UnstableApi::class)
    override fun onTaskRemoved(rootIntent: Intent?) {
        pauseAllPlayersAndStopSelf()
    }

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        val player =
            ExoPlayer
                .Builder(this)
                .setMediaSourceFactory(
                    HlsMediaSource.Factory(DefaultHlsDataSourceFactory(DefaultHttpDataSource.Factory())),
                ).setAudioAttributes(
                    androidx.media3.common.AudioAttributes
                        .Builder()
                        .setContentType(androidx.media3.common.C.AUDIO_CONTENT_TYPE_MUSIC)
                        .setUsage(androidx.media3.common.C.USAGE_MEDIA)
                        .build(),
                    true,
                ).build()
        mediaSession =
            MediaSession
                .Builder(this, player)
                .setCallback(
                    object : MediaSession.Callback {
                        override fun onConnect(
                            session: MediaSession,
                            controller: MediaSession.ControllerInfo,
                        ): MediaSession.ConnectionResult {
                            // Only Crux's own process may set or change playback media items.
                            if (controller.uid == Process.myUid()) {
                                return super.onConnect(session, controller)
                            }
                            // System media UI / Bluetooth / Auto receive read + transport controls only.
                            val restrictedPlayerCommands =
                                Player.Commands
                                    .Builder()
                                    .addAll(
                                        Player.COMMAND_PLAY_PAUSE,
                                        Player.COMMAND_STOP,
                                        Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,
                                        Player.COMMAND_GET_CURRENT_MEDIA_ITEM,
                                        Player.COMMAND_GET_METADATA,
                                    ).build()
                            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                                .setAvailableSessionCommands(SessionCommands.EMPTY)
                                .setAvailablePlayerCommands(restrictedPlayerCommands)
                                .build()
                        }
                    },
                ).setCommandButtonsForMediaItems(
                    listOf(),
                ).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }
}
