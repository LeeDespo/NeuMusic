package com.neumusic.player.ui

import androidx.compose.ui.geometry.Rect

/**
 * 页面栈里的目的地。
 *
 * App 没有导航条，栈是手写的：[AppRoot] 持有一个 [Nav] 列表，**界面上的返回按钮与
 * 系统返回键都只做一件事——弹出栈顶**。所以「从哪来就回哪去」天然成立：同一个页面
 * （比如专辑详情）从主页卡片、歌单详情、搜索结果进来时，各自压在自己的来源之上，
 * 返回自然回到各自的来源，而不是一律回主页。
 *
 * [Player] 与 [Singer] 是特例：它们是整块自下方上滑的全屏覆盖层（播放栏同步上移「带出」），
 * 压在有内容的页面之上；[Equalizer] 从播放页进入，于是栈是 `[…, Player, Equalizer]`，返回即回到播放页。
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
    /** 歌手页（全屏覆盖层，升起动画与播放页同款）。歌数/专辑数来自搜索解析，供页首凹陷标签。 */
    data class Singer(
        val mid: String,
        val name: String,
        val pic: String,
        val songNum: Int = 0,
        val albumNum: Int = 0,
    ) : Nav
    /** 均衡器 / 音效页。 */
    data object Equalizer : Nav
    data class PlaylistDetail(val tid: Long, val name: String, val songnum: Int? = null) : Nav
    data class AlbumDetail(val mid: String, val name: String, val songnum: Int? = null) : Nav
    data class RadioDetail(val id: Int, val title: String) : Nav
}

/**
 * 页面栈的一项。[origin] 是压栈来源卡片的屏幕坐标（场景推拉动画的焦点），
 * [settled] 表示进场动画已完成——完成后页面回归普通渲染，返回时再做回缩。
 */
data class StackEntry(
    val nav: Nav,
    val origin: Rect? = null,
    val settled: Boolean = false,
)

/** 一次压栈请求：目的地 + 可选的来源卡片信息（有则播放场景推拉转场）。 */
data class NavRequest(
    val nav: Nav,
    val origin: Rect? = null,
)
