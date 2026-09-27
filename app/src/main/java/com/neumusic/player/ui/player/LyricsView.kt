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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neumusic.player.data.LyricTextSize
import com.neumusic.player.data.Lyrics
import com.neumusic.player.player.PlayerHost
import com.neumusic.player.shade.LocalShadeColors
import com.neumusic.player.ui.common.VerticalEdgeFades
import kotlin.math.abs

/** 当前行在视口中的高度占比（AMLL 的 alignPosition 默认 0.35）。 */
private const val ANCHOR_FRACTION = 0.35f

/** 用户拖动后无操作的回归时限（ms）。 */
private const val RETURN_DELAY_MS = 3000L

/**
 * 歌词页：左对齐、当前行 35% 锚点、随播放自动滚动。
 *
 * 交互（用户要求）：
 * - **可拖动浏览**：拖动期间不自动跟随；
 * - **双击任意歌词行** → 播放进度跳到该句；
 * - **拖动后 3 秒无操作** → 自动回归正在播放的歌词行；聚焦行始终是正在播放的行。
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
            kotlinx.coroutines.delay(33)
        }
    }

    val currentIndex = lyrics.indexAt(positionMs)

    // 用户拖动检测：LazyListState 的 interactionSource 只对**用户手势**发
    // DragInteraction；程序化 animateScrollToItem 不发 —— 以此区分拖动与自动跟随。
    var lastUserScrollMs by remember { mutableStateOf(0L) }
    var userDragging by remember { mutableStateOf(false) }

    LaunchedEffect(listState) {
        listState.interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is DragInteraction.Start -> userDragging = true
                is DragInteraction.Stop, is DragInteraction.Cancel -> {
                    userDragging = false
                    lastUserScrollMs = System.currentTimeMillis()
                }
            }
        }
    }

    BoxWithConstraints(modifier.fillMaxSize()) {
        // 自动跟随：正在拖动、或拖动后 3 秒内，不跟随。
        val followEnabled by remember {
            derivedStateOf {
                !userDragging && System.currentTimeMillis() - lastUserScrollMs > RETURN_DELAY_MS
            }
        }
        LaunchedEffect(currentIndex) {
            if (currentIndex >= 0 && followEnabled) {
                listState.animateScrollToItem(index = currentIndex, scrollOffset = 0)
            }
        }
        // 拖动结束计时满 3 秒时触发回归。
        LaunchedEffect(lastUserScrollMs) {
            if (lastUserScrollMs > 0) {
                kotlinx.coroutines.delay(RETURN_DELAY_MS)
                if (!userDragging && currentIndex >= 0) {
                    listState.animateScrollToItem(index = currentIndex, scrollOffset = 0)
                }
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
                val distance = if (currentIndex < 0) 99 else abs(i - currentIndex)
                val active = i == currentIndex

                val targetAlpha = when {
                    active -> 1f
                    distance == 1 -> 0.62f
                    distance == 2 -> 0.42f
                    distance == 3 -> 0.28f
                    else -> 0.16f
                }
                val targetBlur = if (active || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) 0f
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
                                    lastUserScrollMs = System.currentTimeMillis()
                                },
                            )
                        },
                ) {
                    if (active && line.hasWords && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        // QRC 逐字行：AMLL 式扫色（accent 填充随唱到位置扫过）
                        KaraokeLine(
                            text = line.text,
                            words = line.words,
                            positionMs = finePosition,
                            fontSize = (textSize.baseSp + 2).sp,
                            lineHeight = (textSize.baseSp + 9).sp,
                            baseColor = colors.textPrimary,
                            fillColor = colors.accent,
                        )
                    } else {
                        Text(
                            text = line.text,
                            color = if (active) colors.accent else colors.textPrimary,
                            fontSize = if (active) (textSize.baseSp + 2).sp else textSize.baseSp.sp,
                            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                            textAlign = TextAlign.Start,
                            lineHeight = (textSize.baseSp + 9).sp,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    if (showTranslation && line.translation.isNotEmpty()) {
                        Text(
                            text = line.translation,
                            color = if (active) colors.accent.copy(alpha = 0.75f) else colors.textTertiary,
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
 * QRC 逐字行（AMLL 式扫色的原生简化实现）：
 * 同一文本画两层——底层为未唱色，顶层为 accent 填充色，按「唱到的位置」
 * 用 `clipRect` 裁剪。字边界取自 QRC 时间，字内按时间线性插值。
 */
@Composable
private fun KaraokeLine(
    text: String,
    words: List<com.neumusic.player.data.LyricWord>,
    positionMs: Long,
    fontSize: androidx.compose.ui.unit.TextUnit,
    lineHeight: androidx.compose.ui.unit.TextUnit,
    baseColor: androidx.compose.ui.graphics.Color,
    fillColor: androidx.compose.ui.graphics.Color,
) {
    var layoutResult by remember { mutableStateOf<androidx.compose.ui.text.TextLayoutResult?>(null) }

    // 当前填充截止的字符偏移（字内分数并入偏移的小数部分影响可忽略，取整字边界）
    var charIndex by remember { mutableStateOf(0) }
    LaunchedEffect(positionMs, words) {
        if (words.isEmpty()) return@LaunchedEffect
        val first = words.first()
        val last = words.last()
        when {
            positionMs <= first.startMs -> charIndex = 0
            positionMs >= last.endMs -> charIndex = text.length
            else -> {
                var acc = 0
                for (w in words) {
                    if (positionMs < w.endMs) {
                        val span = (w.endMs - w.startMs).coerceAtLeast(1)
                        val frac = ((positionMs - w.startMs).toFloat() / span).coerceIn(0f, 1f)
                        charIndex = acc + (w.text.length * frac).toInt()
                        break
                    }
                    acc += w.text.length
                }
            }
        }
    }

    val fillCut = remember(charIndex, layoutResult) {
        val off = charIndex.coerceIn(0, text.length)
        when {
            off <= 0 -> 0f
            off >= text.length -> Float.MAX_VALUE
            else -> runCatching {
                layoutResult?.getHorizontalPosition(off, usePrimaryDirection = true) ?: 0f
            }.getOrDefault(0f)
        }
    }

    Box {
        Text(
            text = text,
            color = baseColor,
            fontSize = fontSize,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Start,
            lineHeight = lineHeight,
            onTextLayout = { layoutResult = it },
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = text,
            color = fillColor,
            fontSize = fontSize,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Start,
            lineHeight = lineHeight,
            modifier = Modifier
                .fillMaxWidth()
                .drawWithContent {
                    when {
                        fillCut == Float.MAX_VALUE -> drawContent()
                        fillCut > 0f -> clipRect(right = fillCut) { this@drawWithContent.drawContent() }
                        // fillCut == 0 → 未唱，不画填充层
                    }
                },
        )
    }
}
