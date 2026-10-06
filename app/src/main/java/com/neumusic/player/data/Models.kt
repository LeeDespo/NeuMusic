package com.neumusic.player.data

/** 一首可播放的曲目（来源：网页搜索 / 电台 / 我喜欢 / 歌单 / 专辑）。 */
data class Track(
    val mid: String,
    val name: String,
    val mediaMid: String,
    val singer: String,
    val albumName: String,
    val albumMid: String,
    val intervalSec: Int,
    val isVip: Boolean,
    /** 数字歌曲 id；「我喜欢」写操作（AddSonglist）需要它，0 表示不可用。 */
    val songId: Long = 0L,
    /** 该曲可用的格式与文件大小（字节），来自 `file.*`，供「查看格式」用。 */
    val fileSizes: Map<String, Long> = emptyMap(),
    /** 风格码（实测：1=流行 2=古典 34=说唱 23=民谣 50/22=摇滚 33/20=电子 19=乡村 28=金属 27=爵士…），0=未知。 */
    val genre: Int = 0,
) {
    val coverUrl: String
        get() = if (albumMid.isNotEmpty())
            "https://y.gtimg.cn/music/photo_new/T002R300x300M000$albumMid.jpg"
        else "https://y.gtimg.cn/music/photo_new/T002R300x300M000.jpg"

    val durationText: String
        get() = "%d:%02d".format(intervalSec / 60, intervalSec % 60)
}

/** 电台分组（热门 / 最近 / 心情 …）。 */
data class RadioGroup(val title: String, val stations: List<RadioStation>)

data class RadioStation(
    val id: Int,
    val title: String,
    val listenDesc: String,
    val picUrl: String,
)

/** 收藏的歌单。 */
data class PlaylistItem(val tid: Long, val name: String, val logo: String, val songnum: Int)

/** 收藏的专辑。[singerName] 仅搜索结果填充（收藏列表接口不给）。 */
data class AlbumItem(
    val mid: String,
    val name: String,
    val logo: String,
    val songnum: Int,
    val singerName: String = "",
)

/** 搜索到的歌手（typed 搜索无数字 id，识别一律用 mid）。 */
data class SearchSinger(
    val mid: String,
    val name: String,
    val pic: String,
    val songNum: Int,
    val albumNum: Int,
)
