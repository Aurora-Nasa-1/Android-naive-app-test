package com.ncm.player.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ncm.player.model.Song
import com.ncm.player.util.RustServerManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong

data class AudioFeatures(
    val bpm: Double,
    val energy: Double,
    val brightness: Double
)

data class SongWithEmotion(
    val song: Song,
    val path: String,
    val bpm: Double,
    val energy: Double,
    val brightness: Double,
    val startBpm: Double = bpm,
    val endBpm: Double = bpm,
    val startEnergy: Double = energy,
    val endEnergy: Double = energy,
    val emotionScore: Double = 0.0
)

sealed class LiveSortState {
    object Idle : LiveSortState()
    data class Analyzing(val progress: Int, val total: Int, val currentSong: String) : LiveSortState()
    object Sorting : LiveSortState()
    data class Completed(
        val sortedSongs: List<SongWithEmotion>,
        val actualCurve: List<Double>,
        val idealCurve: List<Double>
    ) : LiveSortState()
    data class Error(val message: String) : LiveSortState()
}

class LiveSortViewModel : ViewModel() {
    private val _sortState = MutableStateFlow<LiveSortState>(LiveSortState.Idle)
    val sortState: StateFlow<LiveSortState> = _sortState.asStateFlow()

    fun processPlaylist(songsWithPaths: List<Pair<Song, String>>) {
        viewModelScope.launch(Dispatchers.Default) {
            try {
                val total = songsWithPaths.size
                if (total == 0) {
                    _sortState.value = LiveSortState.Completed(emptyList(), emptyList(), emptyList())
                    return@launch
                }

                val analyzedSongs = mutableListOf<SongWithEmotion>()

                // 1. Parse JSON response from RustServerManager.analyzeAudio(path)
                for ((index, item) in songsWithPaths.withIndex()) {
                    val (song, path) = item
                    _sortState.value = LiveSortState.Analyzing(index + 1, total, song.name)

                    val jsonResult = RustServerManager.analyzeAudio(path)
                    val features = parseAudioFeatures(jsonResult)

                    val jsonObject = JSONObject(jsonResult)
                    val startBpm = jsonObject.optDouble("start_bpm", features.bpm)
                    val endBpm = jsonObject.optDouble("end_bpm", features.bpm)
                    val startEnergy = jsonObject.optDouble("start_energy", features.energy)
                    val endEnergy = jsonObject.optDouble("end_energy", features.energy)

                    analyzedSongs.add(
                        SongWithEmotion(
                            song = song,
                            path = path,
                            bpm = features.bpm,
                            energy = features.energy,
                            brightness = features.brightness,
                            startBpm = startBpm,
                            endBpm = endBpm,
                            startEnergy = startEnergy,
                            endEnergy = endEnergy
                        )
                    )
                }

                _sortState.value = LiveSortState.Sorting

                // 2. Compute emotionScore
                val scoredSongs = computeEmotionScores(analyzedSongs)

                // 4. Implement sortSongsLocallyByFlow matching the Greedy Algorithm cost function
                val sortedSongs = sortSongsLocallyByFlow(scoredSongs)
                
                val actualCurve = sortedSongs.map { it.emotionScore }
                val idealCurve = generateIdealCurveLocal(sortedSongs.size)

                _sortState.value = LiveSortState.Completed(sortedSongs, actualCurve, idealCurve)

            } catch (e: Exception) {
                _sortState.value = LiveSortState.Error(e.message ?: "Unknown error")
            }
        }
    }

    private fun parseAudioFeatures(jsonString: String): AudioFeatures {
        return try {
            val jsonObject = JSONObject(jsonString)
            AudioFeatures(
                bpm = jsonObject.optDouble("bpm", 120.0),
                energy = jsonObject.optDouble("energy", 0.5),
                brightness = jsonObject.optDouble("brightness", 0.5)
            )
        } catch (e: Exception) {
            AudioFeatures(120.0, 0.5, 0.5)
        }
    }

    // 2. Compute emotionScore for each song in a playlist by normalizing the bpm, energy, brightness values across the playlist using max values and weights 0.4, 0.4, 0.2
    private fun computeEmotionScores(songs: List<SongWithEmotion>): List<SongWithEmotion> {
        if (songs.isEmpty()) return emptyList()
        val maxBpm = max(songs.maxOfOrNull { it.bpm } ?: 1.0, 1.0)
        val maxEnergy = max(songs.maxOfOrNull { it.energy } ?: 1e-9, 1e-9)
        val maxBrightness = max(songs.maxOfOrNull { it.brightness } ?: 1e-9, 1e-9)

        return songs.map { song ->
            val bpmNorm = song.bpm / maxBpm
            val energyNorm = song.energy / maxEnergy
            val brightnessNorm = song.brightness / maxBrightness
            val emotionScoreRaw = (bpmNorm * 0.4 + energyNorm * 0.4 + brightnessNorm * 0.2) * 100.0
            val emotionScore = Math.round(emotionScoreRaw * 100.0) / 100.0
            
            song.copy(emotionScore = emotionScore)
        }
    }

    // 3. Implement generateIdealCurveLocal(numSongs: Int): List<Double> matching the JS waypoints
    fun generateIdealCurveLocal(numSongs: Int): List<Double> {
        if (numSongs <= 0) return emptyList()
        val timelineNorm = listOf(0.0, 0.15, 0.22, 0.32, 0.40, 0.55, 0.65, 0.80, 1.0)
        val emotions = listOf(0.1, 0.6, 0.25, 0.45, 0.35, 0.75, 0.4, 1.0, 0.05)
        val xs = if (numSongs == 1) listOf(0.5) else List(numSongs) { it.toDouble() / (numSongs - 1) }

        fun interp(x: Double): Double {
            if (x <= timelineNorm.first()) return emotions.first()
            if (x >= timelineNorm.last()) return emotions.last()
            for (i in 0 until timelineNorm.size - 1) {
                val left = timelineNorm[i]
                val right = timelineNorm[i + 1]
                if (x in left..right) {
                    val t = (x - left) / max(1e-9, right - left)
                    return emotions[i] * (1 - t) + emotions[i + 1] * t
                }
            }
            return emotions.last()
        }

        return xs.map { interp(it) * 80 + 10 }
    }

    // 4. Implement sortSongsLocallyByFlow(songs: List<SongWithEmotion>): List<SongWithEmotion> matching the Greedy Algorithm cost function
    fun sortSongsLocallyByFlow(songs: List<SongWithEmotion>): List<SongWithEmotion> {
        if (songs.isEmpty()) return emptyList()
        
        val idealCurve = generateIdealCurveLocal(songs.size)
        val remaining = songs.toMutableList()
        
        var firstSong = remaining[0]
        var firstDiff = abs(firstSong.emotionScore - idealCurve[0])
        for (i in 1 until remaining.size) {
            val diff = abs(remaining[i].emotionScore - idealCurve[0])
            if (diff < firstDiff) {
                firstDiff = diff
                firstSong = remaining[i]
            }
        }
        
        val sorted = mutableListOf<SongWithEmotion>()
        sorted.add(firstSong)
        remaining.remove(firstSong)
        
        for (i in 1 until idealCurve.size) {
            val targetEmotion = idealCurve[i]
            val prevSong = sorted.last()
            val prevEndBpm = prevSong.endBpm
            val prevEndEnergy = prevSong.endEnergy
            
            var bestIndex = 0
            var bestCost = Double.POSITIVE_INFINITY
            
            for (j in 0 until remaining.size) {
                val candidate = remaining[j]
                val candStartBpm = candidate.startBpm
                val candStartEnergy = candidate.startEnergy
                val candEmotion = candidate.emotionScore
                
                val emotionDiff = abs(candEmotion - targetEmotion)
                val maxBpm = max(candStartBpm, prevEndBpm)
                val minBpm = max(min(candStartBpm, prevEndBpm), 1.0)
                val bpmRatio = maxBpm / minBpm
                val bpmDiff = min(abs(bpmRatio - 1.0), abs(bpmRatio - 2.0)) * 100.0
                
                val energyDiff = abs(candStartEnergy - prevEndEnergy) * 50.0
                
                val cost = emotionDiff * 1.5 + bpmDiff * 1.0 + energyDiff * 0.5
                if (cost < bestCost) {
                    bestCost = cost
                    bestIndex = j
                }
            }
            
            sorted.add(remaining.removeAt(bestIndex))
        }
        
        return sorted
    }
}
