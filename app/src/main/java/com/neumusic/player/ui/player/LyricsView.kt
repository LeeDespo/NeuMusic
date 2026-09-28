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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
 *
 * 附加行（各自由右上角开关控制，数据来自 [com.neumusic.player.data.api.LyricApi]）：
 * - **注音**（`kana`）：汉字上方的假名读音（逐字行）或整行读音（行级）；
 * - **音译**（`roman`）：主行下方的罗马音行；
 * - **翻译**（`translation`）：主行下方的译文行。
 */
@Composable
fun LyricsView(
    lyrics: Lyrics?,
    positionMs: Long,
    textSize: LyricTextSize,
    showTranslation: Boolean,
    showRoman: Boolean,
    showKana: Boolean,
    canToggleTranslation: Boolean,
    canToggleRoman: Boolean,
    canToggleKana: Boolean,
    onToggleTranslation: () -> Unit,
    onToggleRoman: () -> Unit,
    onToggleKana: () -> Unit,
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
                    val mainSize = if (focused) (textSize.baseSp + 2).sp else textSize.baseSp.sp
                    // 注音（行级，无逐字数据的行）：主行上方的整行假名读音
                    if (showKana && line.kana.isNotEmpty()) {
                        Text(
                            text = line.kana,
                            color = colors.textTertiary,
                            fontSize = (textSize.baseSp - 4).sp,
                            lineHeight = (textSize.baseSp - 1).sp,
                            textAlign = TextAlign.Start,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    if (line.hasWords && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        // QRC 逐字行：AMLL 式扫色 + 字上注音。任何有逐字数据的行都按
                        // 当前位置画填充，因此浏览时历史行是整句已唱、未到的行是未唱。
                        KaraokeWords(
                            words = line.words,
                            positionMs = finePosition,
                            fontSize = mainSize,
                            lineHeight = (textSize.baseSp + 9).sp,
                            kanaSize = (textSize.baseSp - 4).sp,
                            showKana = showKana,
                            bold = focused,
                            baseColor = colors.textPrimary,
                            fillColor = colors.accent,
                            kanaColor = colors.textTertiary,
                        )
                    } else {
                        Text(
                            text = line.text,
                            color = if (focused) colors.accent else colors.textPrimary,
                            fontSize = mainSize,
                            fontWeight = if (focused) FontWeight.Bold else FontWeight.Normal,
                            textAlign = TextAlign.Start,
                            lineHeight = (textSize.baseSp + 9).sp,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    if (showRoman && line.roman.isNotEmpty()) {
                        Text(
                            text = line.roman,
                            color = colors.textTertiary,
                            fontSize = (textSize.baseSp - 4).sp,
                            lineHeight = (textSize.baseSp - 1).sp,
                            textAlign = TextAlign.Start,
                            modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
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

        // ── 译 / 音 / 注 三个开关：同一行，右上角 ──
        AnnotationToggles(
            showTranslation = showTranslation,
            showRoman = showRoman,
            showKana = showKana,
            canToggleTranslation = canToggleTranslation,
            canToggleRoman = canToggleRoman,
            canToggleKana = canToggleKana,
            onToggleTranslation = onToggleTranslation,
            onToggleRoman = onToggleRoman,
            onToggleKana = onToggleKana,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(end = 24.dp, top = 6.dp),
        )
    }
}

/** 译（翻译）/ 音（音译）/ 注（注音）开关行：开=accent，关=textTertiary；只变色、不变凸凹。 */
@Composable
private fun AnnotationToggles(
    showTranslation: Boolean,
    showRoman: Boolean,
    showKana: Boolean,
    canToggleTranslation: Boolean,
    canToggleRoman: Boolean,
    canToggleKana: Boolean,
    onToggleTranslation: () -> Unit,
    onToggleRoman: () -> Unit,
    onToggleKana: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalShadeColors.current
    if (!canToggleTranslation && !canToggleRoman && !canToggleKana) return
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
        ToggleLabel("译", showTranslation && canToggleTranslation, canToggleTranslation, onToggleTranslation)
        ToggleLabel("音", showRoman && canToggleRoman, canToggleRoman, onToggleRoman)
        ToggleLabel("注", showKana && canToggleKana, canToggleKana, onToggleKana)
    }
}

@Composable
private fun ToggleLabel(label: String, on: Boolean, enabled: Boolean, onTap: () -> Unit) {
    val colors = LocalShadeColors.current
    val tint by animateColorAsState(
        when {
            !enabled -> colors.textTertiary.copy(alpha = 0.35f)
            on -> colors.accent
            else -> colors.textTertiary
        },
        tween(200), label = "toggle_$label",
    )
    Text(
        label,
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        color = tint,
        modifier = Modifier
            .padding(horizontal = 4.dp, vertical = 8.dp)
            .pointerInput(label, enabled) {
                if (enabled) detectTapGestures(onTap = { onTap() })
            },
    )
}

/** 某个字在播放位置下已唱的比例（0..1）；整字唱完返回 1。 */
private fun wordFraction(w: LyricWord, positionMs: Long): Float = when {
    positionMs >= w.endMs -> 1f
    positionMs <= w.startMs -> 0f
    else -> ((positionMs - w.startMs).toFloat() / (w.endMs - w.startMs).coerceAtLeast(1L))
        .coerceIn(0f, 1f)
}

/**
 * QRC 逐字行（AMLL 式扫色的原生实现）：
 * 每个字一个单元——注音（可选）在上，字文本双层（底层未唱色 + 顶层 accent 填充），
 * 按「该字已唱比例」横向裁剪填充层。字内按时间线性插值，逐字自然衔接成扫色。
 *
 * 用 FlowRow 让字单元自然换行；相比「整行两层文本 + getHorizontalPosition 裁剪」，
 * 这里不需要按文本行换算 x，长句折行天然正确（早前折行后只按 x 裁会切掉前面整行）。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun KaraokeWords(
    words: List<LyricWord>,
    positionMs: Long,
    fontSize: androidx.compose.ui.unit.TextUnit,
    lineHeight: androidx.compose.ui.unit.TextUnit,
    kanaSize: androidx.compose.ui.unit.TextUnit,
    showKana: Boolean,
    bold: Boolean,
    baseColor: Color,
    fillColor: Color,
    kanaColor: Color,
) {
    val weight = if (bold) FontWeight.Bold else FontWeight.Normal
    FlowRow {
        words.forEach { w ->
            val frac = wordFraction(w, positionMs)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (showKana && w.kana.isNotEmpty() && w.kana != w.text) {
                    Text(
                        text = w.kana,
                        color = kanaColor,
                        fontSize = kanaSize,
                        lineHeight = kanaSize,
                        maxLines = 1,
                    )
                }
                Box {
                    Text(
                        text = w.text,
                        color = baseColor,
                        fontSize = fontSize,
                        fontWeight = weight,
                        lineHeight = lineHeight,
                    )
                    if (frac > 0f) {
                        var widthPx by remember(w.text) { mutableStateOf(0f) }
                        Text(
                            text = w.text,
                            color = fillColor,
                            fontSize = fontSize,
                            fontWeight = weight,
                            lineHeight = lineHeight,
                            modifier = Modifier
                                .onSizeChanged { widthPx = it.width.toFloat() }
                                .drawWithContent {
                                    when {
                                        // 整字唱完直接画；尺寸未量出一帧都不画，避免闪成整字填充
                                        frac >= 1f -> drawContent()
                                        widthPx <= 0f -> Unit
                                        else -> clipRect(right = widthPx * frac) { this@drawWithContent.drawContent() }
                                    }
                                },
                        )
                    }
                }
            }
        }
    }
}
