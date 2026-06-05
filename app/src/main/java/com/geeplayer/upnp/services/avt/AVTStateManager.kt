package com.geeplayer.upnp.services.avt

import android.util.Log
import com.geeplayer.upnp.core.UpnpConstants
import com.geeplayer.player.DlnaPlayer
import java.util.concurrent.CopyOnWriteArrayList

/**
 * AVTransport:1 服务状态机
 */
class AVTStateManager {
    companion object {
        private const val TAG = "AVTStateManager"
    }

    // 传输状态
    enum class TransportState {
        STOPPED, PLAYING, PAUSED_PLAYBACK, TRANSITIONING, NO_MEDIA_PRESENT
    }

    // 传输状态
    enum class TransportStatus {
        OK, ERROR_OCCURRED
    }

    // 播放模式
    enum class PlayMode {
        NORMAL, REPEAT_ONE, REPEAT_ALL, SHUFFLE, RANDOM
    }

    @Volatile
    var state: TransportState = TransportState.NO_MEDIA_PRESENT
        private set

    @Volatile
    var status: TransportStatus = TransportStatus.OK
        private set

    @Volatile
    var currentUri: String = ""
        private set

    @Volatile
    var currentUriMetadata: String = ""

    @Volatile
    var currentTitle: String = ""

    @Volatile
    var currentArtist: String = ""

    @Volatile
    var playMode: PlayMode = PlayMode.NORMAL

    @Volatile
    var volume: Int = 50  // 0-100

    @Volatile
    var mute: Boolean = false

    // 持续时间（秒）
    @Volatile
    var trackDuration: Long = 0L

    // 当前进度（毫秒）
    @Volatile
    var position: Long = 0L

    // 当前曲目号（用于队列）
    @Volatile
    var trackNumber: Int = 0

    // 曲目数（队列中）
    @Volatile
    var numberOfTracks: Int = 0

    private val stateListeners = CopyOnWriteArrayList<(TransportState) -> Unit>()

    fun addStateListener(listener: (TransportState) -> Unit) {
        stateListeners.add(listener)
    }

    fun removeStateListener(listener: (TransportState) -> Unit) {
        stateListeners.remove(listener)
    }

    fun setTransportState(newState: TransportState) {
        if (state != newState) {
            state = newState
            Log.d(TAG, "State changed to $newState")
            stateListeners.forEach { it(newState) }
        }
    }

    fun setCurrentUri(uri: String, metadata: String = "") {
        currentUri = uri
        currentUriMetadata = metadata
        trackDuration = 0L
        position = 0L
        setTransportState(TransportState.STOPPED)
    }

    fun clear() {
        currentUri = ""
        currentUriMetadata = ""
        trackDuration = 0L
        position = 0L
        setTransportState(TransportState.NO_MEDIA_PRESENT)
    }

    /**
     * 构建 GetTransportInfo 响应
     */
    fun buildTransportInfoXml(): String {
        return """<CurrentTransportState>${state.name}</CurrentTransportState>
<CurrentTransportStatus>${status.name}</CurrentTransportStatus>
<CurrentSpeed>1</CurrentSpeed>"""
    }

    /**
     * 构建 GetPositionInfo 响应
     */
    fun buildPositionInfoXml(): String {
        val trackDurationStr = formatDuration(trackDuration * 1000)
        val positionStr = formatDuration(position)
        return """<Track>$trackNumber</Track>
<TrackDuration>$trackDurationStr</TrackDuration>
<TrackMetaData>$currentUriMetadata</TrackMetaData>
<TrackURI>$currentUri</TrackURI>
<RelTime>$positionStr</RelTime>
<AbsTime>$positionStr</AbsTime>
<RelCount>0</RelCount>
<AbsCount>0</AbsCount>"""
    }

    /**
     * 构建 GetTransportSettings 响应
     */
    fun buildTransportSettingsXml(): String {
        return """<PlayMode>${playMode.name}</PlayMode>
<RecQualityMode>EP_0</RecQualityMode>"""
    }

    private fun formatDuration(millis: Long): String {
        if (millis <= 0) return "0:00:00"
        val totalSec = millis / 1000
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        val s = totalSec % 60
        return "%d:%02d:%02d".format(h, m, s)
    }
}
