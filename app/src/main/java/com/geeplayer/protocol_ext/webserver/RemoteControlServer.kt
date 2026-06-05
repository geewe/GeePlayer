package com.geeplayer.protocol_ext.webserver

import android.util.Log
import com.geeplayer.player.DlnaPlayer
import com.google.gson.Gson
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.flow.StateFlow

/**
 * 远程控制 Web 服务器 — 局域网内浏览器控制播放
 *
 * URL 规划:
 * - /                  控制面板 (HTML)
 * - /api/status        获取当前播放状态 (JSON)
 * - /api/play          播放
 * - /api/pause         暂停
 * - /api/stop          停止
 * - /api/skip          下一曲
 * - /api/prev          上一曲
 * - /api/volume?level=xx   设置音量
 * - /api/seek?pos=xxx      跳转进度
 */
class RemoteControlServer(port: Int = 8088) : NanoHTTPD(port) {
    private companion object {
        private const val TAG = "RemoteControlServer"
    }

    private val gson = Gson()

    // 播放控制回调
    var onPlay: (() -> Unit)? = null
    var onPause: (() -> Unit)? = null
    var onStop: (() -> Unit)? = null
    var onSkip: (() -> Unit)? = null
    var onPrev: (() -> Unit)? = null
    var onSeek: ((Long) -> Unit)? = null
    var onVolumeChange: ((Int) -> Unit)? = null

    // 状态读取
    var playerStateFlow: StateFlow<DlnaPlayer.PlayerState>? = null
    var currentTitle: String = "等待推送..."
    var currentArtist: String = ""

    override fun serve(session: IHTTPSession): Response {
        return try {
            when (session.uri) {
                "/api/status" -> handleStatus()
                "/api/play" -> handleAction("play")
                "/api/pause" -> handleAction("pause")
                "/api/stop" -> handleAction("stop")
                "/api/skip" -> handleAction("skip")
                "/api/prev" -> handleAction("prev")
                "/api/volume" -> handleVolume(session)
                "/api/seek" -> handleSeek(session)
                else -> {
                    if (session.uri.startsWith("/api/")) {
                        newFixedLengthResponse(Response.Status.NOT_FOUND, "application/json", """{"error":"unknown endpoint"}""")
                    } else {
                        newFixedLengthResponse(Response.Status.OK, "text/html", buildControlPanelHtml())
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Serve error", e)
            newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "text/plain", "Error")
        }
    }

    private fun handleStatus(): Response {
        val state = playerStateFlow?.value
        val json = mapOf(
            "playing" to (state?.isPlaying ?: false),
            "loading" to (state?.isLoading ?: false),
            "title" to (currentTitle.ifBlank { "等待推送..." }),
            "artist" to currentArtist,
            "position" to ((state?.currentPosition ?: 0L) / 1000),
            "duration" to ((state?.duration ?: 0L) / 1000),
            "volume" to (state?.volume?.times(100)?.toInt() ?: 100),
            "error" to (state?.error ?: "")
        )
        return newFixedLengthResponse(
            Response.Status.OK, "application/json",
            gson.toJson(json)
        )
    }

    private fun handleAction(action: String): Response {
        Log.i(TAG, "Remote action: $action")
        when (action) {
            "play" -> onPlay?.invoke()
            "pause" -> onPause?.invoke()
            "stop" -> onStop?.invoke()
            "skip" -> onSkip?.invoke()
            "prev" -> onPrev?.invoke()
        }
        return jsonOk()
    }

    private fun handleVolume(session: IHTTPSession): Response {
        val level = session.parameters["level"]?.firstOrNull()?.toIntOrNull()
        if (level != null) {
            onVolumeChange?.invoke(level.coerceIn(0, 100))
        }
        return jsonOk()
    }

    private fun handleSeek(session: IHTTPSession): Response {
        val posSec = session.parameters["pos"]?.firstOrNull()?.toLongOrNull()
        if (posSec != null) {
            onSeek?.invoke(posSec * 1000)
        }
        return jsonOk()
    }

    private fun jsonOk(): Response {
        return newFixedLengthResponse(Response.Status.OK, "application/json", """{"status":"ok"}""")
    }

    private fun buildControlPanelHtml(): String {
        return """<!DOCTYPE html>
<html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>DLNA Receiver 控制面板</title>
<style>
*{margin:0;padding:0;box-sizing:border-box}
body{font-family:-apple-system,sans-serif;background:#121224;color:#fff;display:flex;justify-content:center;align-items:center;min-height:100vh}
.card{background:#1E1E3A;border-radius:20px;padding:32px;width:360px;text-align:center}
.cover{width:200px;height:200px;border-radius:16px;background:#6750A4;margin:16px auto;display:flex;align-items:center;justify-content:center;font-size:64px}
.controls{display:flex;justify-content:center;gap:24px;margin:24px 0}
.controls button{width:56px;height:56px;border-radius:50%;border:none;font-size:24px;cursor:pointer;background:#BB86FC;color:#fff;transition:transform 0.1s}
.controls button:active{transform:scale(0.9)}
.progress-container{width:100%;margin:16px 0;cursor:pointer}
.progress{width:100%;height:6px;background:#3D3D5C;border-radius:3px;overflow:hidden}
.progress-fill{height:100%;background:linear-gradient(90deg,#BB86FC,#9C6ADE);border-radius:3px;transition:width 0.3s}
.time-row{display:flex;justify-content:space-between;font-size:12px;color:rgba(255,255,255,0.5);margin-top:4px}
.info{margin:16px 0}
.info h2{font-size:18px;margin-bottom:4px}
.info p{color:rgba(255,255,255,0.5);font-size:14px}
.volume-row{display:flex;align-items:center;gap:8px;margin-top:16px}
.volume-row input{flex:1;accent-color:#BB86FC}
</style></head><body>
<div class="card">
<div class="cover">♫</div>
<div class="info"><h2 id="title">等待推送...</h2><p id="artist"></p></div>
<div class="progress-container" onclick="seek(event)">
<div class="progress"><div class="progress-fill" id="progress" style="width:0%"></div></div>
<div class="time-row"><span id="currentTime">0:00</span><span id="totalTime">0:00</span></div>
</div>
<div class="controls">
<button onclick="api('prev')">⏮</button>
<button id="playBtn" onclick="togglePlay()">▶</button>
<button onclick="api('skip')">⏭</button>
</div>
<div class="volume-row">
<span>🔊</span>
<input type="range" id="volumeSlider" min="0" max="100" value="80" oninput="setVolume(this.value)">
</div>
</div>
<script>
var isPlaying=false;
function api(cmd,extra){fetch('/api/'+cmd+(extra||'')).catch(function(){})}
function togglePlay(){api(isPlaying?'pause':'play');isPlaying=!isPlaying;updatePlayBtn()}
function updatePlayBtn(){document.getElementById('playBtn').textContent=isPlaying?'⏸':'▶'}
function seek(e){var rect=e.currentTarget.getBoundingClientRect();var pct=(e.clientX-rect.left)/rect.width;api('seek?pos='+Math.round(pct*getDuration()))}
function getDuration(){return parseFloat(document.getElementById('totalTime').textContent.split(':').reduce(function(a,t){return a*60+parseInt(t)},0))}
function setVolume(v){api('volume?level='+v)}
setInterval(function(){
fetch('/api/status').then(function(r){return r.json()}).then(function(d){
isPlaying=d.playing;updatePlayBtn();
document.getElementById('title').textContent=d.title||'无';
document.getElementById('artist').textContent=d.artist||'';
var pct=d.duration>0?(d.position/d.duration*100)+'%':'0%';
document.getElementById('progress').style.width=pct;
document.getElementById('currentTime').textContent=formatTime(d.position);
document.getElementById('totalTime').textContent=formatTime(d.duration);
document.getElementById('volumeSlider').value=d.volume;
}).catch(function(){})
},1000);
function formatTime(s){if(s<=0)return'0:00';var m=Math.floor(s/60);return m+':'+(s%60).toString().padStart(2,'0')}
</script></body></html>"""
    }
}
