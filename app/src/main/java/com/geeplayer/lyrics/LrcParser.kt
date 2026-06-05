package com.geeplayer.lyrics

import android.util.Log

/**
 * LRC 歌词解析器
 *
 * 支持格式:
 * - [mm:ss.xx]歌词
 * - [mm:ss]歌词
 * - [mm:ss.xxx]歌词 (千分位)
 * - 增强标签: [00:12.50]<00:12.50>我<00:12.80>爱<00:13.10>你
 */
class LrcParser {
    private companion object {
        private const val TAG = "LrcParser"
    }

    data class LrcLine(
        val timeMs: Long,
        val text: String,
        val wordTimings: List<WordTiming> = emptyList()
    )

    data class WordTiming(
        val startMs: Long,
        val endMs: Long,
        val word: String
    )

    data class LrcResult(
        val title: String = "",
        val artist: String = "",
        val album: String = "",
        val offset: Long = 0L,
        val lines: List<LrcLine> = emptyList()
    )

    /**
     * 解析 LRC 文本
     */
    fun parse(lrcText: String): LrcResult {
        val lines = lrcText.lines().filter { it.isNotBlank() }

        var title = ""
        var artist = ""
        var album = ""
        var offset = 0L
        val lrcLines = mutableListOf<LrcLine>()

        for (line in lines) {
            val trimmed = line.trim()

            // 元数据标签
            when {
                trimmed.startsWith("[ti:") -> title = extractMeta(trimmed)
                trimmed.startsWith("[ar:") -> artist = extractMeta(trimmed)
                trimmed.startsWith("[al:") -> album = extractMeta(trimmed)
                trimmed.startsWith("[offset:") -> offset = extractMeta(trimmed).toLongOrNull() ?: 0L
                trimmed.startsWith("[") && trimmed.contains("]") -> {
                    val parsedLines = parseTimeTaggedLine(trimmed)
                    lrcLines.addAll(parsedLines)
                }
            }
        }

        lrcLines.sortBy { it.timeMs }

        return LrcResult(title, artist, album, offset, lrcLines)
    }

    /**
     * 解析带时间标签的一行
     */
    private fun parseTimeTaggedLine(line: String): List<LrcLine> {
        val result = mutableListOf<LrcLine>()

        // 匹配所有 [mm:ss.xx] 时间标签
        val timePattern = Regex("""\[(\d{1,3}):(\d{2})(?:[\.:](\d{2,3}))?\]""")
        val matches = timePattern.findAll(line)

        // 提取最后的文本内容
        val textPart = line.replace(timePattern, "").trim()

        if (textPart.isEmpty()) return result

        // 检查是否包含逐字标签
        val wordTimings = parseWordTimings(textPart)

        for (match in matches) {
            val minutes = match.groupValues[1].toLong()
            val seconds = match.groupValues[2].toLong()
            val millisStr = match.groupValues[3]
            val millis = when (millisStr.length) {
                2 -> millisStr.toLong() * 10   // .xx -> xxx
                3 -> millisStr.toLong()         // .xxx
                else -> 0L
            }
            val timeMs = minutes * 60000 + seconds * 1000 + millis

            result.add(LrcLine(timeMs, textPart, wordTimings))
        }

        return result
    }

    /**
     * 解析逐字标签: <00:12.50>我<00:12.80>爱<00:13.10>你
     */
    private fun parseWordTimings(text: String): List<WordTiming> {
        val wordPattern = Regex("""<(\d{1,3}):(\d{2})(?:[\.:](\d{2,3}))?>([^<]*)""")
        val matches = wordPattern.findAll(text)

        if (!matches.any()) return emptyList()

        val timings = mutableListOf<WordTiming>()
        for (match in matches) {
            val minutes = match.groupValues[1].toLong()
            val seconds = match.groupValues[2].toLong()
            val millisStr = match.groupValues[3]
            val millis = when (millisStr.length) {
                2 -> millisStr.toLong() * 10
                3 -> millisStr.toLong()
                else -> 0L
            }
            val startMs = minutes * 60000 + seconds * 1000 + millis
            val word = match.groupValues[4]

            if (word.isNotBlank()) {
                // end time 从下一个标签或 start+500ms 估算
                val endMs = startMs + 500L
                timings.add(WordTiming(startMs, endMs, word))
            }
        }

        // 填充结束时间
        for (i in 0 until timings.size - 1) {
            timings[i] = timings[i].copy(endMs = timings[i + 1].startMs)
        }

        return timings
    }

    private fun extractMeta(line: String): String {
        return line.substringAfter("[").substringBeforeLast("]").substringAfter(":")
    }
}
