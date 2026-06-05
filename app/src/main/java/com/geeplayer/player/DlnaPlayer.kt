package com.geeplayer.player

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.media3.common.*
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.MediaSource
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class DlnaPlayer(private val context: Context) {
    private companion object {
        private const val TAG = "DlnaPlayer"
    }

    data class PlayerState(
        val isPlaying: Boolean = false,
        val isLoading: Boolean = false,
        val currentUri: String = "",
        val duration: Long = 0L,
        val currentPosition: Long = 0L,
        val bufferedPosition: Long = 0L,
        val volume: Float = 1.0f,
        val isMuted: Boolean = false,
        val error: String? = null,
        val metadataTitle: String = "",
        val metadataArtist: String = "",
        val coverUrl: String? = null
    )

    private val _playerState = MutableStateFlow(PlayerState())
    val playerState: StateFlow<PlayerState> = _playerState.asStateFlow()

    private var exoPlayer: ExoPlayer? = null
    private var lastKnownUri: String = ""
    private val mainHandler = Handler(Looper.getMainLooper())
    private val positionUpdateRunnable = object : Runnable {
        override fun run() {
            updateState()
            mainHandler.postDelayed(this, 250)
        }
    }

    fun initialize() {
        if (exoPlayer != null) return

        exoPlayer = ExoPlayer.Builder(context)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                true
            )
            .setHandleAudioBecomingNoisy(true)
            .build()
            .also { player ->
                player.addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        updateState()
                        when (playbackState) {
                            Player.STATE_READY -> Log.d(TAG, "Player ready")
                            Player.STATE_ENDED -> {
                                Log.d(TAG, "Playback ended")
                                _playerState.value = _playerState.value.copy(isPlaying = false)
                            }
                        }
                    }

                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        _playerState.value = _playerState.value.copy(isPlaying = isPlaying)
                        if (isPlaying) {
                            mainHandler.post(positionUpdateRunnable)
                        } else {
                            mainHandler.removeCallbacks(positionUpdateRunnable)
                        }
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        Log.e(TAG, "Player error: ${error.message}", error)
                        _playerState.value = _playerState.value.copy(
                            isPlaying = false,
                            error = error.message
                        )
                    }
                })
            }

        Log.i(TAG, "ExoPlayer initialized")
    }

    fun load(uri: String, autoPlay: Boolean = true) {
        lastKnownUri = uri

        _playerState.value = _playerState.value.copy(
            currentUri = uri,
            isLoading = true,
            error = null
        )

        mainHandler.post {
            val player = exoPlayer ?: return@post
            try {
                val mediaSource = buildMediaSource(uri)
                player.setMediaSource(mediaSource)
                player.prepare()
                if (autoPlay) {
                    player.play()
                }
                Log.i(TAG, "Loaded: $uri (autoPlay=$autoPlay)")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load: $uri", e)
                _playerState.value = _playerState.value.copy(
                    isLoading = false,
                    error = "加载失败: ${e.message}"
                )
            }
        }
    }

    fun play() {
        mainHandler.post { exoPlayer?.play() }
        Log.d(TAG, "Play")
    }

    fun pause() {
        mainHandler.post { exoPlayer?.pause() }
        Log.d(TAG, "Pause")
    }

    fun stop() {
        mainHandler.post {
            exoPlayer?.stop()
            _playerState.value = _playerState.value.copy(isPlaying = false)
        }
        Log.d(TAG, "Stop")
    }

    fun seekTo(positionMs: Long) {
        mainHandler.post {
            exoPlayer?.seekTo(positionMs)
            updateState()
        }
    }

    fun setVolume(volume: Float) {
        val vol = volume.coerceIn(0f, 1f)
        mainHandler.post { exoPlayer?.volume = vol }
        _playerState.value = _playerState.value.copy(volume = vol)
    }

    fun setMute(mute: Boolean) {
        mainHandler.post {
            exoPlayer?.volume = if (mute) 0f else _playerState.value.volume
        }
        _playerState.value = _playerState.value.copy(isMuted = mute)
    }


    fun updateMetadata(title: String, artist: String) {
        _playerState.value = _playerState.value.copy(
            metadataTitle = title,
            metadataArtist = artist
        )
        Log.i(TAG, "Metadata updated: title=$title, artist=$artist")
    }

    fun updateCoverUrl(url: String) {
        _playerState.value = _playerState.value.copy(
            coverUrl = url
        )
        Log.i(TAG, "Cover URL updated: $url")
    }

    fun next() {
        onNextCallback?.invoke()
    }

    fun previous() {
        onPreviousCallback?.invoke()
    }

    var onNextCallback: (() -> Unit)? = null
    var onPreviousCallback: (() -> Unit)? = null

    fun release() {
        mainHandler.removeCallbacks(positionUpdateRunnable)
        mainHandler.post {
            exoPlayer?.release()
            exoPlayer = null
        }
        Log.i(TAG, "Player released")
    }

    private fun buildMediaSource(uri: String): MediaSource {
        val uriObj = Uri.parse(uri)
        val dataSourceFactory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setUserAgent("Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
            .setKeepPostFor302Redirects(false)
            .setConnectTimeoutMs(15000)
            .setReadTimeoutMs(30000)

        val mediaItem = MediaItem.Builder()
            .setUri(uriObj)
            .build()

        return if (uri.contains(".m3u8", ignoreCase = true)) {
            HlsMediaSource.Factory(dataSourceFactory).createMediaSource(mediaItem)
        } else {
            ProgressiveMediaSource.Factory(dataSourceFactory).createMediaSource(mediaItem)
        }
    }

    private fun updateState() {
        val player = exoPlayer ?: return
        _playerState.value = _playerState.value.copy(
            isPlaying = player.isPlaying,
            isLoading = player.playbackState == Player.STATE_BUFFERING,
            duration = player.duration.coerceAtLeast(0),
            currentPosition = player.currentPosition.coerceAtLeast(0),
            bufferedPosition = player.bufferedPosition.coerceAtLeast(0)
        )
    }
}
