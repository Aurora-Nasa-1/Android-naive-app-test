package com.ncm.player.service

import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import android.app.PendingIntent
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import androidx.media3.session.MediaSessionService
import androidx.media3.session.CommandButton
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.Futures
import androidx.media3.common.Rating
import androidx.media3.common.HeartRating
import android.content.Context
import java.io.File
import com.ncm.player.util.UserPreferences
import com.ncm.player.util.RustServerManager
import io.github.proify.lyricon.provider.LyriconFactory
import io.github.proify.lyricon.provider.LyriconProvider
import io.github.proify.lyricon.lyric.model.LyricWord
import io.github.proify.lyricon.lyric.model.RichLyricLine
import io.github.proify.lyricon.lyric.model.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.regex.Pattern

class MusicService : MediaSessionService() {
    private var player: ExoPlayer? = null
    private var mediaSession: MediaSession? = null

    companion object {
        private var cache: SimpleCache? = null
        fun getCache(context: android.content.Context): SimpleCache {
            if (cache == null) {
                val cacheDir = File(context.cacheDir, "media")
                val cacheSize = UserPreferences.getCacheSize(context) * 1024L * 1024L
                val evictor = LeastRecentlyUsedCacheEvictor(cacheSize)
                cache = SimpleCache(cacheDir, evictor, androidx.media3.database.StandaloneDatabaseProvider(context))
            }
            return cache!!
        }
    }
    private var lyriconProvider: LyriconProvider? = null
    private var lyricJob: Job? = null
    private val fadeAudioProcessor = FadeAudioProcessor()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)


    override fun onCreate() {
        super.onCreate()

        val fadeDuration = UserPreferences.getFadeDuration(this)
        fadeAudioProcessor.setFadeDuration((fadeDuration * 1000).toLong())

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setAllowedCapturePolicy(C.ALLOW_CAPTURE_BY_NONE)
            .build()

        val renderersFactory = object : DefaultRenderersFactory(this) {
            override fun buildAudioSink(
                context: android.content.Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean
            ): androidx.media3.exoplayer.audio.AudioSink {
                return DefaultAudioSink.Builder(this@MusicService)
                    .setAudioProcessors(arrayOf(fadeAudioProcessor))
                    .setEnableFloatOutput(true)
                    .build()
            }
        }

        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)

        val dataSourceFactory: DataSource.Factory = CacheDataSource.Factory()
            .setCache(getCache(this))
            .setUpstreamDataSourceFactory(DefaultDataSource.Factory(this, httpDataSourceFactory))
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

        val mediaSourceFactory = DefaultMediaSourceFactory(this)
            .setDataSourceFactory(dataSourceFactory)

        // Preload next item

        player = ExoPlayer.Builder(this, renderersFactory)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .setMediaSourceFactory(mediaSourceFactory)
            .build()

        player?.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) {
                    // startFadeIn() is now handled by onFlush() for most cases (seek, transition).
                    // We only trigger it here for resume-from-pause.
                    fadeAudioProcessor.startFadeIn()
                }
            }
        })

        val intent = Intent(this, com.ncm.player.MainActivity::class.java).apply {
            action = "ACTION_SHOW_PLAYER"
        }
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

        mediaSession = MediaSession.Builder(this, player!!)
            .setSessionActivity(pendingIntent)
            .setCallback(object : MediaSession.Callback {
                override fun onSetRating(
                    session: MediaSession,
                    controller: MediaSession.ControllerInfo,
                    rating: Rating
                ): ListenableFuture<SessionResult> {
                    if (rating is HeartRating) {
                        val mediaId = session.player.currentMediaItem?.mediaId
                        if (mediaId != null) {
                            val cookie = UserPreferences.getCookie(this@MusicService)
                            serviceScope.launch(Dispatchers.IO) {
                                RustServerManager.callApi("like", mapOf("id" to mediaId, "like" to rating.isHeart.toString(), "cookie" to (cookie ?: "")))
                            }
                        }
                    }
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }

                override fun onCustomCommand(
                    session: MediaSession,
                    controller: MediaSession.ControllerInfo,
                    customCommand: SessionCommand,
                    args: android.os.Bundle
                ): ListenableFuture<SessionResult> {
                    if (customCommand.customAction == "ACTION_LIKE") {
                        val currentMediaItem = session.player.currentMediaItem
                        val mediaId = currentMediaItem?.mediaId
                        if (mediaId != null) {
                            val isLiked = currentMediaItem.mediaMetadata.userRating?.isRated == true && (currentMediaItem.mediaMetadata.userRating as? HeartRating)?.isHeart == true
                            val nextLikeState = !isLiked
                            val cookie = UserPreferences.getCookie(this@MusicService)
                            serviceScope.launch(Dispatchers.IO) {
                                // Toggle like logic
                                RustServerManager.callApi("like", mapOf("id" to mediaId, "like" to nextLikeState.toString(), "cookie" to (cookie ?: "")))

                                // Update metadata locally to reflect change in notification immediately
                                withContext(Dispatchers.Main) {
                                    val index = session.player.currentMediaItemIndex
                                    val currentItem = session.player.currentMediaItem
                                    if (currentItem != null) {
                                        val newMetadata = currentItem.mediaMetadata.buildUpon()
                                            .setUserRating(HeartRating(nextLikeState))
                                            .build()
                                        val newItem = currentItem.buildUpon()
                                            .setMediaMetadata(newMetadata)
                                            .build()
                                        session.player.replaceMediaItem(index, newItem)
                                    }
                                }
                            }
                        }
                        return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                    }
                    return super.onCustomCommand(session, controller, customCommand, args)
                }
            })
            .build()

        initLyricon()
    }

    private fun initLyricon() {
        lyriconProvider = LyriconFactory.createProvider(this).apply {
            service.addConnectionListener(object : io.github.proify.lyricon.provider.ConnectionListener {
                override fun onConnected(provider: LyriconProvider) {
                    syncToLyricon()
                }
                override fun onReconnected(provider: LyriconProvider) {
                    syncToLyricon()
                }
                override fun onDisconnected(provider: LyriconProvider) {}
                override fun onConnectTimeout(provider: LyriconProvider) {}
            })
            register()
        }

        player?.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                lyriconProvider?.player?.setPlaybackState(isPlaying)
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                mediaItem?.let {
                    updateLyriconSong(it)
                }
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int
            ) {
                lyriconProvider?.player?.setPosition(newPosition.positionMs)
            }
        })

        // Regular position sync for Lyricon
        serviceScope.launch {
            while (true) {
                player?.let {
                    if (it.isPlaying) {
                        lyriconProvider?.player?.setPosition(it.currentPosition)
                    }
                }
                delay(500)
            }
        }
    }

    private fun syncToLyricon() {
        val p = player ?: return
        val provider = lyriconProvider ?: return

        provider.player.setPlaybackState(p.isPlaying)
        provider.player.setPosition(p.currentPosition)
        p.currentMediaItem?.let { updateLyriconSong(it) }
    }

    private fun updateLyriconSong(mediaItem: MediaItem) {
        val songId = mediaItem.mediaId
        val metadata = mediaItem.mediaMetadata
        val title = metadata.title?.toString() ?: "Unknown"
        val artist = metadata.artist?.toString() ?: "Unknown"

        // Reset state
        lyriconProvider?.player?.setPlaybackState(player?.isPlaying ?: false)
        lyriconProvider?.player?.setSong(Song(id = songId, name = title, artist = artist))

        lyricJob?.cancel()
        lyricJob = serviceScope.launch {
            try {
                val result = RustServerManager.callApi("lyric/new", mapOf("id" to songId))
                val body = com.google.gson.JsonParser.parseString(result).asJsonObject

                if (body.has("lrc") || body.has("yrc")) {
                    val lrc = body?.get("lrc")?.asJsonObject?.get("lyric")?.asString ?: ""
                    val tlyric = body?.get("tlyric")?.asJsonObject?.get("lyric")?.asString ?: ""
                    val yrc = body?.get("yrc")?.asJsonObject?.get("lyric")?.asString ?: ""

                    val lyricLines = if (yrc.isNotEmpty()) {
                        com.ncm.player.util.LyricUtils.parseYrc(yrc)
                    } else {
                        com.ncm.player.util.LyricUtils.parseLrc(lrc, player?.duration ?: 0L)
                    }

                    val finalLines = if (tlyric.isNotEmpty()) {
                        val tlines = com.ncm.player.util.LyricUtils.parseLrc(tlyric).associateBy { it.time }
                        lyricLines.map { line ->
                            val trans = tlines.entries.find { it.key >= line.time - 500 && it.key <= line.time + 500 }
                            if (trans != null) {
                                line.copy(translation = trans.value.text)
                            } else {
                                line
                            }
                        }
                    } else {
                        lyricLines
                    }

                    lyriconProvider?.player?.setSong(
                        io.github.proify.lyricon.lyric.model.Song(
                            id = songId,
                            name = title,
                            artist = artist,
                            duration = player?.duration?.coerceAtLeast(0L) ?: 0L,
                            lyrics = com.ncm.player.util.LyricUtils.toRichLyricLines(finalLines)
                        )
                    )
                    lyriconProvider?.player?.setPosition(player?.currentPosition ?: 0L)
                    lyriconProvider?.player?.setDisplayTranslation(tlyric.isNotEmpty())
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        val session = mediaSession ?: return null

        val currentMediaItem = player?.currentMediaItem
        val isLiked = currentMediaItem?.mediaMetadata?.userRating?.isRated == true && (currentMediaItem.mediaMetadata.userRating as? HeartRating)?.isHeart == true

        // Update custom layout to include the Like button
        val likeCommand = SessionCommand("ACTION_LIKE", android.os.Bundle.EMPTY)
        val likeButton = CommandButton.Builder()
            .setSessionCommand(likeCommand)
            .setDisplayName("Like")
            .setIconResId(if (isLiked) com.ncm.player.R.drawable.ic_heart_filled else com.ncm.player.R.drawable.ic_heart_outline)
            .setEnabled(true)
            .build()

        session.setCustomLayout(listOf(likeButton))
        return session
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = player ?: return
        // If not playing or queue is empty, stop the service to clean up.
        // Otherwise, keep it running for background playback.
        if (!player.isPlaying || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        mediaSession?.run {
            player?.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }
}
