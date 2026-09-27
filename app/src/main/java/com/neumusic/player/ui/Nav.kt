package com.neumusic.player.ui

/**
 * 页面栈里的目的地。
 *
 * App 没有导航条，栈是手写的：[AppRoot] 持有一个 [Nav] 列表，**界面上的返回按钮与
 * 系统返回键都只做一件事——弹出栈顶**。所以「从哪来就回哪去」天然成立：同一个页面
 * （比如专辑详情）从主页卡片、歌单详情、搜索结果进来时，各自压在自己的来源之上，
 * 返回自然回到各自的来源，而不是一律回主页。
 *
 * [Player] 是特例：它是整块自下方上滑的全屏覆盖层，压在有内容的页面之上；
 * [Equalizer] 从播放页进入，于是栈是 `[…, Player, Equalizer]`，返回即回到播放页。
 */
sealed interface Nav {
    data object Home : Nav
    data object Search : Nav
    data object Settings : Nav
    /** 收藏的歌单列表（主页该栏的「更多」）。 */
    data object Playlists : Nav
    /** 收藏的专辑列表（主页该栏的「更多」）。 */
    data object Albums : Nav
    /** 「我喜欢」。 */
    data object Liked : Nav
    /** 播放页（全屏覆盖层）。 */
    data object Player : Nav
    /** 均衡器 / 音效页。 */
    data object Equalizer : Nav
    data class PlaylistDetail(val tid: Long, val name: String, val songnum: Int? = null) : Nav
    data class AlbumDetail(val mid: String, val name: String, val songnum: Int? = null) : Nav
    data class RadioDetail(val id: Int, val title: String) : Nav
}
