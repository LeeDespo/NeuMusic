package com.neumusic.player.ui.player

import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neumusic.player.data.LyricTextSize
import com.neumusic.player.data.LyricWord
import com.neumusic.player.data.Lyrics
import com.neumusic.player.player.PlayerHost
import com.neumusic.player.shade.LocalShadeColors
import com.neumusic.player.ui.common.VerticalEdgeFades
import kotlin.math.abs
import kotlinx.coroutines.delay

/** 聚焦行在视口中的高度占比（AMLL 的 alignPosition 默认 0.35）。 */
private const val ANCHOR_FRACTION = 0.35f

/** 拖动/双击后无操作的回归时限（ms）。 */
private const val RETURN_DELAY_MS = 3000L

/**
 * 歌词页：左对齐、聚焦行落在视口 35% 处。
 *
 * 交互（用户要求）：
 * - **聚焦行跟随浏览**：拖动时高亮的是停在锚点上的那句，不再死盯着正在播放的那句；
 * - **双击任意歌词行** → 播放进度跳到该句；
 * - **拖动/双击后 3 秒无操作** → 自动回归正在播放的行；
 * - 逐字扫色（QRC）按当前播放位置算，所以浏览时看到的历史行是整句已唱、未来的行是未唱。
 */
@Composable
fun LyricsView(
    lyrics: Lyrics?,
    positionMs: Long,
    textSize: LyricTextSize,
    showTranslation: Boolean,
    canToggleTranslation: Boolean,
    onToggleTranslation: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalShadeColors.current

    if (lyrics == null || lyrics.lines.isEmpty()) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("暂无歌词", color = colors.textTertiary, fontSize = 14.sp)
        }
        return
    }

    val listState = rememberLazyListState()
    val haptic = LocalHapticFeedback.current

    // 逐字扫色需要细粒度进度：页内 33ms 自轮询（外部 500ms 轮询只负责行切换）。
    var finePosition by remember { mutableStateOf(positionMs) }
    LaunchedEffect(Unit) {
        while (true) {
            finePosition = PlayerHost.positionMs()
            delay(33)
        }
    }

    val playingIndex = lyrics.indexAt(positionMs)

    // ── 浏览态 ──
    // 拖动开始即进入浏览；松手/双击后 3 秒无操作退出。浏览期间聚焦行 = 锚点行，
    // 退出后回归正在播放行（用户反馈"滑动歌词不切换聚焦的歌词"）。
    var browsing by remember { mutableStateOf(false) }
    var lastTouchMs by remember { mutableLongStateOf(0L) }

    // LazyListState 的 interactionSource 只对**用户手势**发 DragInteraction；
    // 程序化 animateScrollToItem 不发 —— 以此区分拖动与自动跟随。
    LaunchedEffect(listState) {
        listState.interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is DragInteraction.Start -> {
                    browsing = true
                    lastTouchMs = System.currentTimeMillis()
                }
                is DragInteraction.Stop, is DragInteraction.Cancel -> {
                    lastTouchMs = System.currentTimeMillis()
                }
                else -> Unit
            }
        }
    }
    LaunchedEffect(browsing, lastTouchMs) {
        if (!browsing || lastTouchMs == 0L) return@LaunchedEffect
        delay(RETURN_DELAY_MS)
        browsing = false
    }

    BoxWithConstraints(modifier.fillMaxSize()) {
        val heightPx = with(LocalDensity.current) { maxHeight.toPx() }

        // 锚点行 = 占据「视口 35% 高度」那一句。拖动时它就是聚焦行。
        val anchorIndex by remember(heightPx) {
            derivedStateOf {
                val info = listState.layoutInfo
                if (info.visibleItemsInfo.isEmpty()) return@derivedStateOf -1
                val y = heightPx * ANCHOR_FRACTION
                info.visibleItemsInfo.firstOrNull {
                    val top = it.offset - info.viewportStartOffset
                    y >= top && y < top + it.size
                }?.index ?: info.visibleItemsInfo.minByOrNull {
                    abs((it.offset - info.viewportStartOffset + it.size / 2f) - y)
                }?.index ?: -1
            }
        }
        val focusIndex = if (browsing && anchorIndex >= 0) anchorIndex else playingIndex

        // 非浏览态自动跟随正在播放的行（首次进入、切歌、3 秒回归都走这里）。
        LaunchedEffect(playingIndex, browsing) {
            if (!browsing && playingIndex >= 0) {
                listState.animateScrollToItem(index = playingIndex, scrollOffset = 0)
            }
        }

        val density = LocalDensity.current
        val topPad = with(density) { (maxHeight * ANCHOR_FRACTION).toPx().toInt().coerceAtLeast(0) }
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 28.dp, end = 28.dp,
                top = with(density) { topPad.toDp() },
                bottom = maxHeight - with(density) { topPad.toDp() } + with(density) { 24.dp },
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            itemsIndexed(lyrics.lines, key = { i, _ -> i }) { i, line ->
                val distance = if (focusIndex < 0) 99 else abs(i - focusIndex)
                val focused = i == focusIndex

                val targetAlpha = when {
                    focused -> 1f
                    distance == 1 -> 0.62f
                    distance == 2 -> 0.42f
                    distance == 3 -> 0.28f
                    else -> 0.16f
                }
                val targetBlur = if (focused || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) 0f
                else minOf(distance, 4).toFloat() * 0.9f

                val alpha by animateFloatAsState(targetAlpha, tween(260), label = "lyricAlpha")
                val blurPx by animateFloatAsState(targetBlur, tween(260), label = "lyricBlur")

                Column(
                    Modifier
                        .fillMaxWidth()
                        .graphicsLayer { this.alpha = alpha }
                        .then(if (blurPx > 0.05f) Modifier.blur(blurPx.dp) else Modifier)
                        // 双击该句 → 播放进度跳到这句
                        .pointerInput(line.timeMs) {
                            detectTapGestures(
                                onDoubleTap = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    PlayerHost.seekTo(line.timeMs)
                                    // 留 3 秒缓冲：seek 后外部位置轮询要 500ms 才跟上，
                                    // 立刻恢复跟随会让高亮闪回上一句。
                                    browsing = true
                                    lastTouchMs = System.currentTimeMillis()
                                },
                            )
                        },
                ) {
                    val lineFontSize = if (focused) (textSize.baseSp + 2).sp else textSize.baseSp.sp
                    if (line.hasWords && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        // QRC 逐字行：AMLL 式扫色。任何有逐字数据的行都按当前位置画填充，
                        // 因此浏览时历史行是整句已唱、未到的行是未唱。
                        KaraokeLine(
                            text = line.text,
                            words = line.words,
                            positionMs = finePosition,
                            fontSize = lineFontSize,
                            lineHeight = (textSize.baseSp + 9).sp,
                            bold = focused,
                            baseColor = colors.textPrimary,
                            fillColor = colors.accent,
                        )
                    } else {
                        Text(
                            text = line.text,
                            color = if (focused) colors.accent else colors.textPrimary,
                            fontSize = lineFontSize,
                            fontWeight = if (focused) FontWeight.Bold else FontWeight.Normal,
                            textAlign = TextAlign.Start,
                            lineHeight = (textSize.baseSp + 9).sp,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    if (showTranslation && line.translation.isNotEmpty()) {
                        Text(
                            text = line.translation,
                            color = if (focused) colors.accent.copy(alpha = 0.75f) else colors.textTertiary,
                            fontSize = (textSize.baseSp - 3).sp,
                            textAlign = TextAlign.Start,
                            lineHeight = (textSize.baseSp + 3).sp,
                            modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                        )
                    }
                }
            }
        }

        // 边缘渐隐（与主页同一处理）
        VerticalEdgeFades(state = listState, height = 30.dp)

        // 翻译开关：右上角与顶栏均衡器同横坐标；只变图标颜色、不变凸凹
        if (canToggleTranslation) {
            val iconTint by animateColorAsState(
                if (showTranslation) colors.accent else colors.textTertiary,
                tween(200), label = "transTint",
            )
            Text(
                "译",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = iconTint,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 24.dp, top = 10.dp)
                    .pointerInput(Unit) { detectTapGestures(onTap = { onToggleTranslation() }) },
            )
        }
    }
}

/**
 * 已唱到的字符下标：播放位置的**纯函数**。
 *
 * 早前用 `LaunchedEffect(positionMs, words)` + `mutableStateOf` 写这个值，而
 * `positionMs` 每 33ms 变一次 —— 每帧都在「取消旧协程、启动新协程」。协程是
 * dispatch 调度的，被取消的 job 可能一次都没跑过，填充下标就永远停在 0。
 * 改成组合期直接算：没有协程、没有延迟、没有跳帧。
 */
private fun fillCharIndex(positionMs: Long, words: List<LyricWord>, textLen: Int): Int {
    if (words.isEmpty()) return 0
    if (positionMs <= words.first().startMs) return 0
    if (positionMs >= words.last().endMs) return textLen
    var acc = 0
    for (w in words) {
        if (positionMs < w.endMs) {
            val span = (w.endMs - w.startMs).coerceAtLeast(1L)
            val frac = ((positionMs - w.startMs).toFloat() / span).coerceIn(0f, 1f)
            return acc + (w.text.length * frac).toInt()
        }
        acc += w.text.length
    }
    return textLen
}

/** 填充区域：未唱 / 整句已唱 / 唱到某行的某个 x。 */
private sealed interface Fill {
    data object None : Fill
    data object All : Fill
    data class Part(val top: Float, val bottom: Float, val right: Float) : Fill
}

/**
 * QRC 逐字行（AMLL 式扫色的原生简化实现）：
 * 同一文本画两层——底层为未唱色，顶层为 accent 填充色，按「唱到的位置」用 `clipRect` 裁剪。
 * 字边界取自 QRC 时间，字内按时间线性插值。
 *
 * 裁剪要**跟着换行走**：`getHorizontalPosition` 返回的是「所在行内」的 x，
 * 长句折行后只按 x 裁会把前面几行整行切掉。所以整行以上的部分整行画，
 * 只有当前行按 x 裁。
 */
@Composable
private fun KaraokeLine(
    text: String,
    words: List<LyricWord>,
    positionMs: Long,
    fontSize: TextUnit,
    lineHeight: TextUnit,
    bold: Boolean,
    baseColor: Color,
    fillColor: Color,
) {
    var layoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
    val charIndex = remember(positionMs, words, text) { fillCharIndex(positionMs, words, text.length) }
    val layout = layoutResult
    val fill: Fill = remember(charIndex, layout, text) {
        when {
            layout == null || charIndex <= 0 -> Fill.None
            charIndex >= text.length -> Fill.All
            else -> {
                val line = layout.getLineForOffset(charIndex)
                Fill.Part(
                    top = layout.getLineTop(line),
                    bottom = layout.getLineBottom(line),
                    right = layout.getHorizontalPosition(charIndex, usePrimaryDirection = true),
                )
            }
        }
    }

    val weight = if (bold) FontWeight.Bold else FontWeight.Normal
    Box {
        Text(
            text = text,
            color = baseColor,
            fontSize = fontSize,
            fontWeight = weight,
            textAlign = TextAlign.Start,
            lineHeight = lineHeight,
            onTextLayout = { layoutResult = it },
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = text,
            color = fillColor,
            fontSize = fontSize,
            fontWeight = weight,
            textAlign = TextAlign.Start,
            lineHeight = lineHeight,
            modifier = Modifier
                .fillMaxWidth()
                .drawWithContent {
                    when (val f = fill) {
                        Fill.None -> Unit
                        Fill.All -> drawContent()
                        is Fill.Part -> {
                            if (f.top > 0f) {
                                clipRect(top = 0f, bottom = f.top) { this@drawWithContent.drawContent() }
                            }
                            clipRect(top = f.top, bottom = f.bottom, right = f.right) {
                                this@drawWithContent.drawContent()
                            }
                        }
                    }
                },
        )
    }
}
