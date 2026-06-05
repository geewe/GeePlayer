package com.geeplayer.lyrics

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * 在线歌词搜索 API — 网易云音乐 + QQ 音乐双源
 *
 * 搜索策略: 先查网易云，未命中再查 QQ 音乐
 */
class LyricsSearchApi {
    private companion object {
        private const val TAG = "LyricsSearchApi"
        private const val TIMEOUT_SEC = 10L
    }

    data class SearchResult(
        val title: String,
        val artist: String,
        val lrcText: String?,
        val source: String
    )

    private val client = OkHttpClient.Builder()
        .connectTimeout(TIMEOUT_SEC, TimeUnit.SECONDS)
        .readTimeout(TIMEOUT_SEC, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val gson = Gson()

    /**
     * 搜索歌词 — 多源自动 fallback
     */
    suspend fun search(title: String, artist: String): SearchResult? = withContext(Dispatchers.IO) {
        Log.d(TAG, "Searching lyrics: $title - $artist")

        // 1. 先搜网易云
        var result = searchNetease(title, artist)
        if (result?.lrcText != null) {
            Log.i(TAG, "Found lyrics from NetEase: $title")
            return@withContext result
        }

        // 2. 降级到 QQ 音乐
        result = searchQQMusic(title, artist)
        if (result?.lrcText != null) {
            Log.i(TAG, "Found lyrics from QQ Music: $title")
            return@withContext result
        }

        Log.w(TAG, "Lyrics not found for: $title - $artist")
        null
    }

    // ======================== 网易云音乐 ========================

    /**
     * 网易云音乐歌词搜索
     */
    private fun searchNetease(title: String, artist: String): SearchResult? {
        try {
            // Step 1: 搜索歌曲
            val keyword = URLEncoder.encode("$title $artist", "UTF-8")
            val searchUrl = "https://music.163.com/api/search/get?s=$keyword&type=1&limit=5&offset=0"
            
            val searchResponse = client.newCall(
                Request.Builder()
                    .url(searchUrl)
                    .header("Referer", "https://music.163.com")
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36")
                    .build()
            ).execute()

            if (!searchResponse.isSuccessful) return null

            val searchBody = searchResponse.body?.string() ?: return null
            val searchJson = JsonParser.parseString(searchBody).asJsonObject

            val code = searchJson.get("code")?.asInt ?: -1
            if (code != 200) return null

            val songs = searchJson.getAsJsonObject("result")?.getAsJsonArray("songs")
            if (songs == null || songs.size() == 0) return null

            // 取最佳匹配
            val bestSong = songs[0].asJsonObject
            val songId = bestSong.get("id").asLong
            val songName = bestSong.get("name").asString
            val artists = bestSong.getAsJsonArray("artists")
                ?.joinToString(", ") { it.asJsonObject.get("name").asString }
                ?: artist

            // Step 2: 获取歌词
            val lyricUrl = "https://music.163.com/api/song/lyric?lv=-1&kv=-1&tv=-1&id=$songId"
            val lyricResponse = client.newCall(
                Request.Builder()
                    .url(lyricUrl)
                    .header("Referer", "https://music.163.com")
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36")
                    .build()
            ).execute()

            if (!lyricResponse.isSuccessful) return null

            val lyricBody = lyricResponse.body?.string() ?: return null
            val lyricJson = JsonParser.parseString(lyricBody).asJsonObject

            // 优先取逐行歌词 lrc，没有则取逐字歌词 klyric
            val lrcStr = if (lyricJson.has("lrc") && lyricJson.getAsJsonObject("lrc").get("lyric")?.isJsonNull?.not() ?: true) {
                lyricJson.getAsJsonObject("lrc").get("lyric").asString
            } else if (lyricJson.has("klyric") && lyricJson.getAsJsonObject("klyric").get("lyric")?.isJsonNull?.not() ?: true) {
                lyricJson.getAsJsonObject("klyric").get("lyric").asString
            } else null

            if (lrcStr.isNullOrBlank()) return null

            // 解码 Unicode 转义
            val decodedLrc = unescapeUnicode(lrcStr)

            return SearchResult(songName, artists, decodedLrc, "网易云音乐")

        } catch (e: Exception) {
            Log.w(TAG, "Netease search failed", e)
            return null
        }
    }

    // ======================== QQ 音乐 ========================

    /**
     * QQ 音乐歌词搜索
     */
    private fun searchQQMusic(title: String, artist: String): SearchResult? {
        try {
            // Step 1: 搜索歌曲
            val keyword = URLEncoder.encode("$title $artist", "UTF-8")
            val searchUrl = "https://c.y.qq.com/soso/fcgi-bin/client_search_cp?w=$keyword&format=json&p=1&n=5&cr=1"

            val searchResponse = client.newCall(
                Request.Builder()
                    .url(searchUrl)
                    .header("Referer", "https://y.qq.com")
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36")
                    .build()
            ).execute()

            if (!searchResponse.isSuccessful) return null

            val searchBody = searchResponse.body?.string() ?: return null
            val searchJson = JsonParser.parseString(searchBody).asJsonObject

            val data = searchJson.getAsJsonObject("data")
            val songList = data?.getAsJsonObject("song")?.getAsJsonArray("list")
            if (songList == null || songList.size() == 0) return null

            val bestSong = songList[0].asJsonObject
            val songMid = bestSong.get("mid").asString
            val songName = bestSong.get("title").asString
            val singer = bestSong.getAsJsonArray("singer")
                ?.joinToString(", ") { it.asJsonObject.get("name").asString }
                ?: artist

            // Step 2: 获取歌词
            val lyricUrl = "https://c.y.qq.com/lyric/fcgi-bin/fcg_query_lyric_new.fcg?songmid=$songMid&format=json"
            val lyricResponse = client.newCall(
                Request.Builder()
                    .url(lyricUrl)
                    .header("Referer", "https://y.qq.com")
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36")
                    .build()
            ).execute()

            if (!lyricResponse.isSuccessful) return null

            val lyricBody = lyricResponse.body?.string() ?: return null
            val lyricJson = JsonParser.parseString(lyricBody).asJsonObject

            val lyricBase64 = lyricJson.get("lyric")?.asString ?: return null
            if (lyricBase64.isBlank()) return null

            // QQ 音乐返回的是 Base64 编码的歌词
            val lrcBytes = android.util.Base64.decode(lyricBase64, android.util.Base64.DEFAULT)
            val decodedLrc = String(lrcBytes, Charsets.UTF_8)

            return SearchResult(songName, singer, decodedLrc, "QQ音乐")

        } catch (e: Exception) {
            Log.w(TAG, "QQ Music search failed", e)
            return null
        }
    }

    /**
     * 解码 Unicode 转义序列（网易云返回的歌词可能含 \uXXXX）
     */
    private fun unescapeUnicode(text: String): String {
        val sb = StringBuilder()
        var i = 0
        while (i < text.length) {
            if (text[i] == '\\' && i + 5 < text.length && text[i + 1] == 'u') {
                val hex = text.substring(i + 2, i + 6)
                try {
                    sb.append(hex.toInt(16).toChar())
                    i += 6
                } catch (e: Exception) {
                    sb.append(text[i])
                    i++
                }
            } else {
                sb.append(text[i])
                i++
            }
        }
        return sb.toString()
    }
}

