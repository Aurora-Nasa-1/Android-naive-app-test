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
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import java.io.File
import com.ncm.player.api.NcmApiService
import com.ncm.player.util.UserPreferences
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
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
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

    private val apiService = Retrofit.Builder()
        .baseUrl("http://127.0.0.1:3000/")
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(NcmApiService::class.java)

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
                    fadeAudioProcessor.startFadeIn()
                }
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int
            ) {
                if (reason == Player.DISCONTINUITY_REASON_AUTO_TRANSITION || reason == Player.DISCONTINUITY_REASON_SEEK) {
                    fadeAudioProcessor.startFadeIn()
                }
            }
        })

        mediaSession = MediaSession.Builder(this, player!!)
            .setCallback(object : MediaSession.Callback {})
            .build()

        initLyricon()
    }

    private fun initLyricon() {
        lyriconProvider = LyriconFactory.createProvider(this).apply {
            // service.addConnectionListener { ... }
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

    private fun updateLyriconSong(mediaItem: MediaItem) {
        val songId = mediaItem.mediaId
        val metadata = mediaItem.mediaMetadata
        val title = metadata.title?.toString() ?: "Unknown"
        val artist = metadata.artist?.toString() ?: "Unknown"

        // Set placeholder
        lyriconProvider?.player?.setSong(Song(id = songId, name = title, artist = artist))

        lyricJob?.cancel()
        lyricJob = serviceScope.launch {
            try {
                val response = apiService.getLyric(songId)
                if (response.isSuccessful) {
                    val body = response.body()
                    val lrc = body?.get("lrc")?.asJsonObject?.get("lyric")?.asString
                    val tlyric = body?.get("tlyric")?.asJsonObject?.get("lyric")?.asString
                    val yrc = body?.get("yrc")?.asJsonObject?.get("lyric")?.asString

                    val lyricLines = if (!yrc.isNullOrEmpty()) {
                        parseYrc(yrc)
                    } else if (!lrc.isNullOrEmpty()) {
                        parseLrc(lrc)
                    } else {
                        emptyList()
                    }

                    // Apply translations if available
                    val finalLines = if (!tlyric.isNullOrEmpty() && lyricLines.isNotEmpty()) {
                        mergeTranslations(lyricLines, tlyric)
                    } else {
                        lyricLines
                    }

                    lyriconProvider?.player?.setSong(
                        Song(
                            id = songId,
                            name = title,
                            artist = artist,
                            duration = player?.duration ?: 0L,
                            lyrics = finalLines
                        )
                    )
                    lyriconProvider?.player?.setDisplayTranslation(!tlyric.isNullOrEmpty())
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun parseLrc(lrc: String): List<RichLyricLine> {
        val lines = mutableListOf<RichLyricLine>()
        val pattern = Pattern.compile("\\[(\\d{2}):(\\d{2})\\.(\\d{2,3})](.*)")
        lrc.lines().forEach { line ->
            val matcher = pattern.matcher(line)
            if (matcher.find()) {
                val min = matcher.group(1)?.toLong() ?: 0L
                val sec = matcher.group(2)?.toLong() ?: 0L
                val msStr = matcher.group(3) ?: "0"
                val ms = if (msStr.length == 2) msStr.toLong() * 10 else msStr.toLong()
                val time = min * 60000 + sec * 1000 + ms
                val text = matcher.group(4)?.trim() ?: ""
                if (text.isNotEmpty()) {
                    lines.add(RichLyricLine(begin = time, text = text))
                }
            }
        }
        // Set end times based on next line's start
        for (i in 0 until lines.size - 1) {
            lines[i] = lines[i].copy(end = lines[i + 1].begin)
        }
        if (lines.isNotEmpty()) {
            lines[lines.size - 1] = lines[lines.size - 1].copy(end = player?.duration ?: (lines.last().begin + 5000))
        }
        return lines
    }

    private fun parseYrc(yrc: String): List<RichLyricLine> {
        val lines = mutableListOf<RichLyricLine>()
        // YRC format: [time,duration]text(time,duration)word(time,duration)word...
        val linePattern = Pattern.compile("\\[(\\d+),(\\d+)](.*)")
        val wordPattern = Pattern.compile("\\((\\d+),(\\d+),(\\d+)\\)([^\\(]*)")

        yrc.lines().forEach { line ->
            val lineMatcher = linePattern.matcher(line)
            if (lineMatcher.find()) {
                val lineBegin = lineMatcher.group(1)?.toLong() ?: 0L
                val lineDur = lineMatcher.group(2)?.toLong() ?: 0L
                val content = lineMatcher.group(3) ?: ""

                val words = mutableListOf<LyricWord>()
                val wordMatcher = wordPattern.matcher(content)
                var fullText = ""

                while (wordMatcher.find()) {
                    val wBeginOffset = wordMatcher.group(1)?.toLong() ?: 0L
                    val wDur = wordMatcher.group(2)?.toLong() ?: 0L
                    // third group is usually unused or type
                    val wText = wordMatcher.group(4) ?: ""

                    words.add(LyricWord(
                        text = wText,
                        begin = lineBegin + wBeginOffset,
                        end = lineBegin + wBeginOffset + wDur
                    ))
                    fullText += wText
                }

                if (words.isNotEmpty()) {
                    lines.add(RichLyricLine(
                        begin = lineBegin,
                        end = lineBegin + lineDur,
                        text = fullText,
                        words = words
                    ))
                } else if (content.isNotEmpty()) {
                    // Fallback for lines without word timing but have line timing
                    lines.add(RichLyricLine(
                        begin = lineBegin,
                        end = lineBegin + lineDur,
                        text = content
                    ))
                }
            }
        }
        return lines
    }

    private fun mergeTranslations(lines: List<RichLyricLine>, tlyric: String): List<RichLyricLine> {
        val translations = parseLrc(tlyric)
        return lines.map { line ->
            val trans = translations.find { it.begin >= line.begin - 500 && it.begin <= line.begin + 500 }
            if (trans != null) {
                line.copy(translation = trans.text)
            } else {
                line
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onDestroy() {
        mediaSession?.run {
            player?.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }
}
