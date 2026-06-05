package com.geeplayer.lyrics

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 歌词同步引擎 — 根据播放进度计算当前歌词行
 */
class LyricsSyncEngine {
    private companion object {
        private const val TAG = "LyricsSyncEngine"
    }

    data class LyricsState(
        val lrcResult: LrcParser.LrcResult? = null,
        val currentLineIndex: Int = -1,
        val currentLine: LrcParser.LrcLine? = null,
        val currentWordIndex: Int = -1,
        val progressInLine: Float = 0f,  // 0f..1f 当前行播放进度
        val isSearching: Boolean = false,
        val isLoaded: Boolean = false
    )

    private val _state = MutableStateFlow(LyricsState())
    val state: StateFlow<LyricsState> = _state.asStateFlow()

    private var syncJob: Job? = null

    private val lrcParser = LrcParser()

    /**
     * 加载 LRC 歌词文本
     */
    fun loadLrc(lrcText: String) {
        val result = lrcParser.parse(lrcText)
        _state.value = _state.value.copy(
            lrcResult = result,
            currentLineIndex = -1,
            currentLine = null,
            currentWordIndex = -1,
            progressInLine = 0f,
            isLoaded = true
        )
        Log.d(TAG, "Loaded LRC: ${result.lines.size} lines")
    }

    /**
     * 清除歌词
     */
    fun clear() {
        syncJob?.cancel()
        _state.value = LyricsState()
    }

    /**
     * 根据播放位置同步歌词（需持续调用）
     */
    fun seekTo(positionMs: Long) {
        val result = _state.value.lrcResult ?: return
        val lines = result.lines
        if (lines.isEmpty()) return

        var index = -1
        for (i in lines.indices) {
            if (positionMs >= lines[i].timeMs) {
                index = i
            } else {
                break
            }
        }

        if (index >= 0 && index < lines.size) {
            val currentLine = lines[index]
            val nextTime = if (index + 1 < lines.size) lines[index + 1].timeMs
            else currentLine.timeMs + 5000L
            val lineDuration = (nextTime - currentLine.timeMs).coerceAtLeast(1L)
            val progress = ((positionMs - currentLine.timeMs).toFloat() / lineDuration).coerceIn(0f, 1f)

            // 逐字进度
            val wordIndex = if (currentLine.wordTimings.isNotEmpty()) {
                var wi = -1
                for (j in currentLine.wordTimings.indices) {
                    if (positionMs >= currentLine.wordTimings[j].startMs) wi = j
                }
                wi
            } else -1

            _state.value = _state.value.copy(
                currentLineIndex = index,
                currentLine = currentLine,
                currentWordIndex = wordIndex,
                progressInLine = progress
            )
        }
    }
}
