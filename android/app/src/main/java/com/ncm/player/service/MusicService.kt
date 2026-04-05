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
import androidx.media3.session.MediaNotification
import androidx.media3.session.DefaultMediaNotificationProvider
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
    private var playbackInfoJob: Job? = null
    private val likedSongIds = mutableSetOf<String>()
    private val fadeAudioProcessor = FadeAudioProcessor()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)


    override fun onCreate() {
        super.onCreate()
        android.util.Log.d("MusicService", "Service onCreate")

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
                    fadeAudioProcessor.startFadeIn()
                }
                updateMediaSessionLayout()
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                android.util.Log.d("MusicService", "MediaItem transition to: ${mediaItem?.mediaId}")
                updateMediaSessionLayout()
                // Ensure fade-in on transition
                fadeAudioProcessor.startFadeIn()
            }

            override fun onEvents(player: Player, events: Player.Events) {
                if (events.contains(Player.EVENT_MEDIA_METADATA_CHANGED)) {
                    updateMediaSessionLayout()
                }
                if (events.contains(Player.EVENT_PLAYBACK_STATE_CHANGED)) {
                    val state = player.playbackState
                    android.util.Log.d("MusicService", "Playback State changed: $state, isPlaying: ${player.isPlaying}")
                    updateMediaSessionLayout()
                    if (state == Player.STATE_READY) {
                        startPlaybackInfoLoop()
                    }
                }
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                val errorMsg = "${error.errorCodeName} (${error.errorCode}): ${error.message}"
                android.util.Log.e("MusicService", "Player Error: $errorMsg", error)

                val args = android.os.Bundle().apply {
                    putString("error", errorMsg)
                }
                mediaSession?.broadcastCustomCommand(SessionCommand("ACTION_PLAYER_ERROR", android.os.Bundle.EMPTY), args)

                if (error.errorCode == androidx.media3.common.PlaybackException.ERROR_CODE_IO_UNSPECIFIED ||
                    error.errorCode == androidx.media3.common.PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED) {
                    // Retry once on network/IO error
                    player?.let {
                        val currentItem = it.currentMediaItem
                        if (currentItem != null) {
                            it.prepare()
                            it.play()
                        }
                    }
                }
            }
        })

        startPlaybackInfoLoop()

        val intent = Intent(this, com.ncm.player.MainActivity::class.java).apply {
            action = "ACTION_SHOW_PLAYER"
        }
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

        val notificationProvider = DefaultMediaNotificationProvider.Builder(this).build()
        setMediaNotificationProvider(notificationProvider)

        mediaSession = MediaSession.Builder(this, player!!)
            .setSessionActivity(pendingIntent)
            .setCallback(object : MediaSession.Callback {
                override fun onConnect(
                    session: MediaSession,
                    controller: MediaSession.ControllerInfo
                ): MediaSession.ConnectionResult {
                    val availableSessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                        .add(SessionCommand("ACTION_LIKE", android.os.Bundle.EMPTY))
                        .add(SessionCommand("UPDATE_PLAYBACK_INFO", android.os.Bundle.EMPTY))
                        .add(SessionCommand("ACTION_PLAYER_ERROR", android.os.Bundle.EMPTY))
                        .build()
                    return MediaSession.ConnectionResult.accept(
                        availableSessionCommands,
                        MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS
                    )
                }

                override fun onSetRating(
                    session: MediaSession,
                    controller: MediaSession.ControllerInfo,
                    rating: Rating
                ): ListenableFuture<SessionResult> {
                    if (rating is HeartRating) {
                        val mediaId = session.player.currentMediaItem?.mediaId
                        if (mediaId != null) {
                            if (rating.isHeart) likedSongIds.add(mediaId) else likedSongIds.remove(mediaId)
                            val cookie = UserPreferences.getCookie(this@MusicService)
                            serviceScope.launch(Dispatchers.IO) {
                                RustServerManager.callApi("like", mapOf("id" to mediaId, "like" to rating.isHeart.toString(), "cookie" to (cookie ?: "")))
                                withContext(Dispatchers.Main) { updateMediaSessionLayout() }
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
                            val isLiked = likedSongIds.contains(mediaId) || (currentMediaItem.mediaMetadata.userRating?.isRated == true && (currentMediaItem.mediaMetadata.userRating as? HeartRating)?.isHeart == true)
                            val nextLikeState = !isLiked
                            if (nextLikeState) likedSongIds.add(mediaId) else likedSongIds.remove(mediaId)

                            val cookie = UserPreferences.getCookie(this@MusicService)
                            serviceScope.launch(Dispatchers.IO) {
                                // Toggle like logic
                                RustServerManager.callApi("like", mapOf("id" to mediaId, "like" to nextLikeState.toString(), "cookie" to (cookie ?: "")))
                                withContext(Dispatchers.Main) { updateMediaSessionLayout() }
                            }
                        }
                        return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                    }
                    return super.onCustomCommand(session, controller, customCommand, args)
                }
            })
            .build()

        initLyricon()
        updateMediaSessionLayout()
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

    private fun startPlaybackInfoLoop() {
        if (playbackInfoJob?.isActive == true) return
        playbackInfoJob = serviceScope.launch {
            while (true) {
                player?.let { p ->
                    if (p.playbackState != Player.STATE_READY) return@let

                    // Try to get format from current tracks if direct access fails
                    var activeFormat = p.audioFormat
                    if (activeFormat == null || activeFormat.sampleRate == -1) {
                        val currentTracks = p.currentTracks
                        for (group in currentTracks.groups) {
                            if (group.type == C.TRACK_TYPE_AUDIO && group.isSelected) {
                                for (i in 0 until group.length) {
                                    if (group.isTrackSelected(i)) {
                                        activeFormat = group.getTrackFormat(i)
                                        break
                                    }
                                }
                            }
                            if (activeFormat != null && activeFormat!!.sampleRate != -1) break
                        }
                    }

                    if (activeFormat != null) {
                        val sampleRate = if (activeFormat.sampleRate != -1) activeFormat.sampleRate else 0
                        val bitrate = if (activeFormat.bitrate != -1) activeFormat.bitrate else 0

                        android.util.Log.d("MusicService", "Playback Info: sampleRate=$sampleRate, bitrate=$bitrate")

                        if (sampleRate > 0 || bitrate > 0) {
                            val extras = android.os.Bundle().apply {
                                putInt("sampleRate", sampleRate)
                                putInt("bitrate", bitrate)
                            }

                            mediaSession?.setSessionExtras(extras)
                            mediaSession?.broadcastCustomCommand(SessionCommand("UPDATE_PLAYBACK_INFO", android.os.Bundle.EMPTY), extras)
                        }
                    } else {
                        android.util.Log.d("MusicService", "Playback Info: activeFormat is null")
                    }
                }
                delay(1500)
            }
        }
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

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    private fun updateMediaSessionLayout() {
        val session = mediaSession ?: return
        val currentMediaItem = player?.currentMediaItem
        val mediaId = currentMediaItem?.mediaId
        val isLiked = (mediaId != null && likedSongIds.contains(mediaId)) || (currentMediaItem?.mediaMetadata?.userRating?.isRated == true && (currentMediaItem.mediaMetadata.userRating as? HeartRating)?.isHeart == true)

        android.util.Log.d("MusicService", "Updating layout. MediaId: $mediaId, isLiked: $isLiked")

        val likeCommand = SessionCommand("ACTION_LIKE", android.os.Bundle.EMPTY)
        val likeButton = CommandButton.Builder()
            .setSessionCommand(likeCommand)
            .setDisplayName("Like")
            .setIconResId(if (isLiked) com.ncm.player.R.drawable.ic_heart_filled else com.ncm.player.R.drawable.ic_heart_outline)
            .setEnabled(true)
            .build()

        val customLayout = com.google.common.collect.ImmutableList.of(likeButton)
        session.setCustomLayout(customLayout)
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = player ?: return
        // If not playing or queue is empty, stop the service to clean up.
        // Otherwise, keep it running for background playback.
        if ((!player.isPlaying && player.playbackState != Player.STATE_BUFFERING) || player.mediaItemCount == 0) {
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
