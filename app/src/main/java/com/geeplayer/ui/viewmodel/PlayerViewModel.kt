package com.geeplayer.ui.viewmodel

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.geeplayer.lyrics.LyricsSyncEngine
import com.geeplayer.player.DlnaPlayer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import javax.inject.Inject

/**
 * 播放器 ViewModel — 管理播放状态、歌词同步、队列
 *
 * 与 DlnaPlayer 绑定后自动同步播放进度
 */
@HiltViewModel
class PlayerViewModel @Inject constructor() : ViewModel() {
    private companion object { private const val TAG = "PlayerViewModel" }

    // 基本播放状态
    var isPlaying by mutableStateOf(false); private set
    var isLoading by mutableStateOf(false); private set
    var currentTitle by mutableStateOf("未在播放"); private set
    var currentArtist by mutableStateOf(""); private set
    var currentAlbum by mutableStateOf(""); private set
    var currentUri by mutableStateOf(""); private set
    var coverUrl by mutableStateOf<String?>(null); private set
    var errorMessage by mutableStateOf<String?>(null)

    // 进度 (使用 Compose 1.6+ 原生 mutableLongStateOf)
    var durationMs by mutableLongStateOf(0L); private set
    var positionMs by mutableLongStateOf(0L); private set
    var bufferedMs by mutableLongStateOf(0L); private set

    // 音量控制
    var volume by mutableFloatStateOf(1f); private set
    var isMuted by mutableStateOf(false); private set

    // 歌词状态
    var lyricsState by mutableStateOf(LyricsSyncEngine.LyricsState()); private set

    // 播放队列
    var queue by mutableStateOf<List<QueueItem>>(emptyList()); private set

    data class QueueItem(
        val id: String,
        val title: String,
        val artist: String,
        val uri: String,
        val coverUrl: String? = null
    )

    private val lyricsSyncEngine = LyricsSyncEngine()
    private var syncJob: Job? = null

    /**
     * 绑定 DlnaPlayer，开始监听播放状态变化
     */
    fun bindPlayer(player: DlnaPlayer) {
        syncJob?.cancel()
        val searchApi = com.geeplayer.lyrics.LyricsSearchApi()
        var lastSearchedSong = ""

        syncJob = CoroutineScope(Dispatchers.Default).launch {
            player.playerState.collect { state ->
                isPlaying = state.isPlaying
                isLoading = state.isLoading
                positionMs = state.currentPosition
                durationMs = state.duration
                bufferedMs = state.bufferedPosition
                errorMessage = state.error

                // 优先使用 DLNA DIDL-Lite 元数据，回退到 URI 解析
                if (state.metadataTitle.isNotBlank()) {
                    currentTitle = state.metadataTitle
                    currentArtist = state.metadataArtist
                    // 新歌曲触发歌词搜索
                    val songKey = "${state.metadataTitle}|${state.metadataArtist}"
                    if (songKey != lastSearchedSong && state.metadataTitle.isNotBlank()) {
                        lastSearchedSong = songKey
                        lyricsState = lyricsState.copy(isSearching = true)
                        launch {
                            try {
                                val result = searchApi.search(state.metadataTitle, state.metadataArtist)
                                if (result?.lrcText != null) {
                                    lyricsSyncEngine.loadLrc(result.lrcText)
                                    lyricsState = lyricsSyncEngine.state.value
                                }
                            } catch (_: Exception) { }
                        }
                    }
                }
                // 更新封面
                if (state.coverUrl != null && state.coverUrl != coverUrl) {
                    coverUrl = state.coverUrl
                }
                if (state.currentUri != currentUri && state.currentUri.isNotBlank()) {
                    currentUri = state.currentUri
                    if (state.metadataTitle.isBlank()) {
                        extractMetadata(state.currentUri)
                    }
                }

                // 歌词同步
                if (durationMs > 0) {
                    lyricsSyncEngine.seekTo(positionMs)
                    lyricsState = lyricsSyncEngine.state.value
                }
            }
        }
    }

    /**
     * 从 URI 中提取歌曲名和艺术家
     */
    private fun extractMetadata(uri: String) {
        val name = uri.substringAfterLast("/")
            .substringBefore("?")
            .substringBeforeLast(".")
        if (name.isBlank()) return

        val parts = name.split(" - ", "-")
        if (parts.size >= 2) {
            currentArtist = parts[0].trim()
            currentTitle = parts.drop(1).joinToString(" - ").trim()
        } else {
            currentTitle = name
            currentArtist = "未知艺术家"
        }
    }

    fun setLyrics(text: String) {
        lyricsSyncEngine.loadLrc(text)
        lyricsState = lyricsSyncEngine.state.value
    }

    fun addToQueue(item: QueueItem) {
        queue = queue + item
        Log.d(TAG, "Added to queue: ${item.title}, size=${queue.size}")
    }

    fun removeFromQueue(index: Int) {
        if (index in queue.indices) {
            queue = queue.toMutableList().also { it.removeAt(index) }
        }
    }

    fun clearQueue() {
        queue = emptyList()
        Log.d(TAG, "Queue cleared")
    }

    /**
     * 格式化时间为 MM:SS
     */
    fun formatTime(ms: Long): String {
        if (ms <= 0) return "0:00"
        val t = ms / 1000
        return "%d:%02d".format(t / 60, t % 60)
    }

    override fun onCleared() {
        super.onCleared()
        syncJob?.cancel()
    }
}
