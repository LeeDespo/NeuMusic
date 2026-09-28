package com.neumusic.player.data

/** 逐字时间（QRC）。[startMs]/[endMs] 为该字的绝对起止；[kana] 是这个字的注音（假名读音，可空）。 */
data class LyricWord(
    val text: String,
    val startMs: Long,
    val endMs: Long,
    val kana: String = "",
)

/**
 * 一行歌词。[timeMs] 为该行起始时间。
 * [translation] 是可选的翻译（QQ 仅在部分曲目提供，多数为空）。
 * [words] 非空时为 **QRC 逐字行**（每字带时间），渲染为卡拉 OK 扫色。
 * [roman] 是该行的音译（罗马音，来自 `roma:1`，逐字数据合成为行文本）。
 * [kana] 是该行的注音（假名读音行，来自翻译里的 `[kana:…]` 元数据）。
 */
data class LyricLine(
    val timeMs: Long,
    val text: String,
    val translation: String = "",
    val words: List<LyricWord> = emptyList(),
    val roman: String = "",
    val kana: String = "",
) {
    val hasWords: Boolean get() = words.isNotEmpty()

    /** 该行结束时间：逐字行取末字结束；行级 LRC 无从得知，返回 -1。 */
    val endMs: Long get() = if (words.isNotEmpty()) words.maxOf { it.endMs } else -1L
}

/** 一首歌的完整歌词（按时间升序）。 */
data class Lyrics(val lines: List<LyricLine>) {
    /** 是否至少有一行带逐字时间（QRC）。 */
    val hasWordTiming: Boolean get() = lines.any { it.hasWords }
    /** 是否真的带回了翻译（多数曲目没有，UI 据此决定要不要显示翻译开关）。 */
    val hasTranslation: Boolean get() = lines.any { it.translation.isNotEmpty() }
    /** 是否带回了音译（罗马音）。 */
    val hasRoman: Boolean get() = lines.any { it.roman.isNotEmpty() }
    /** 是否带回了注音（假名读音）。 */
    val hasKana: Boolean get() = lines.any { it.kana.isNotEmpty() || it.words.any { w -> w.kana.isNotEmpty() } }

    /**
     * 当前播放位置对应的行下标；未到第一行时返回 0，超出末尾返回最后一行。
     * 用二分查找，歌词行数上千时也不会成为每帧的开销。
     */
    fun indexAt(positionMs: Long): Int {
        if (lines.isEmpty()) return -1
        var lo = 0
        var hi = lines.lastIndex
        var found = 0
        while (lo <= hi) {
            val mid = (lo + hi) / 2
            if (lines[mid].timeMs <= positionMs) {
                found = mid
                lo = mid + 1
            } else {
                hi = mid - 1
            }
        }
        return found
    }
}

/**
 * 歌词文字大小。
 *
 * 早前正文写死 16sp 偏小，改为可调：当前行按 [baseSp] 放大强调，其余行用 [baseSp] 略小。
 */
enum class LyricTextSize(val label: String, val baseSp: Int) {
    SMALL("小", 14),
    NORMAL("标准", 17),
    LARGE("大", 20),
    XLARGE("特大", 24);

    fun next(): LyricTextSize = entries[(ordinal + 1) % entries.size]
}
