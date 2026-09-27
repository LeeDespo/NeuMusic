package com.neumusic.player.ui.player

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neumusic.player.data.Prefs
import com.neumusic.player.player.DynamicsFxHost
import com.neumusic.player.player.EqualizerHost
import com.neumusic.player.player.PlayerHost
import com.neumusic.player.player.SmartEq
import com.neumusic.player.shade.LocalShadeColors
import com.neumusic.player.shade.shadeInset
import com.neumusic.player.shade.shadeSurface
import com.neumusic.player.ui.common.toastMain
import com.neumusic.player.ui.common.HorizontalShadeSlider
import com.neumusic.player.ui.home.DetailTopBar

/** 均衡器页（播放页顶栏"均衡器"进入）。 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun EqualizerScreen(onBack: () -> Unit) {
    val colors = LocalShadeColors.current
    val ctx = LocalContext.current

    if (!EqualizerHost.available.collectAsState().value) {
        Column(Modifier.fillMaxSize().background(colors.background).statusBarsPadding()) {
            DetailTopBar("音效", onBack)
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("此设备不支持均衡器", color = colors.textTertiary, fontSize = 14.sp)
            }
        }
        return
    }

    val enabled by EqualizerHost.enabled.collectAsState()
    val dynamicsAvailable by DynamicsFxHost.available.collectAsState()
    val dvc by DynamicsFxHost.dvc.collectAsState()
    val balance by DynamicsFxHost.balance.collectAsState()
    val speed by PlayerHost.speed.collectAsState()
    val pitch by PlayerHost.pitch.collectAsState()
    var smartEq by remember { mutableStateOf(Prefs.smartEq) }
    val lastApplied by SmartEq.lastApplied.collectAsState()
    val bands by EqualizerHost.bands.collectAsState()
    val preset by EqualizerHost.preset.collectAsState()
    val bass by EqualizerHost.bass.collectAsState()
    val freqs = remember { EqualizerHost.bandFreqs }
    val presets = remember { EqualizerHost.presetNames }

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        DetailTopBar("音效", onBack)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        ) {
            // ── 开关 ──
            Column(
                Modifier.fillMaxWidth().shadeSurface(cornerRadius = 24.dp, offset = 6.dp, blur = 10.dp).padding(12.dp),
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(46.dp)
                        // 位于凸起卡内：启用=凹陷，停用=平（凸起中以凹陷表选中）
                        .then(
                            if (enabled) Modifier.shadeInset(cornerRadius = 16.dp, offset = 3.dp, blur = 5.dp)
                            else Modifier
                        )
                        .flatTap { EqualizerHost.setEnabled(!enabled) }
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "启用均衡器",
                        fontSize = 14.sp,
                        color = if (enabled) colors.accent else colors.textPrimary,
                        fontWeight = if (enabled) FontWeight.SemiBold else FontWeight.Normal,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        if (enabled) "开" else "关",
                        fontSize = 13.sp,
                        color = if (enabled) colors.accent else colors.textTertiary,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
            Spacer(Modifier.height(18.dp))

            // ── 预设 ──
            SectionCard(title = "预设") {
                androidx.compose.foundation.layout.FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    presets.forEachIndexed { i, name ->
                        val selected = preset == i
                        Text(
                            name,
                            fontSize = 13.sp,
                            color = if (selected) colors.accent else colors.textPrimary,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            modifier = Modifier
                                .then(
                                    if (selected) Modifier.shadeInset(cornerRadius = 14.dp, offset = 3.dp, blur = 4.dp)
                                    else Modifier
                                )
                                .flatTap { EqualizerHost.setPreset(i) }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                        )
                    }
                    val custom = preset == -1
                    Text(
                        "自定义",
                        fontSize = 13.sp,
                        color = if (custom) colors.accent else colors.textPrimary,
                        fontWeight = if (custom) FontWeight.SemiBold else FontWeight.Normal,
                        modifier = Modifier
                            .then(
                                if (custom) Modifier.shadeInset(cornerRadius = 14.dp, offset = 3.dp, blur = 4.dp)
                                else Modifier
                            )
                            .flatTap {
                                // 选自定义 = 以当前频段值进入手调模式
                                Prefs.eqPreset = -1
                                EqualizerHost.setPreset(-1)
                            }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }
            }
            Spacer(Modifier.height(18.dp))

            // ── 频段滑杆（竖直，凹陷轨道 + accent 填充）──
            SectionCard(title = "频段") {
                if (bands.isEmpty()) {
                    Text("暂不可用", color = colors.textTertiary, fontSize = 13.sp, modifier = Modifier.padding(8.dp))
                } else {
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        bands.indices.forEach { i ->
                            VerticalBandSlider(
                                label = freqLabel(freqs.getOrNull(i) ?: 0),
                                progress = EqualizerHost.bandProgress(i),
                                onProgress = { EqualizerHost.setBandProgress(i, it) },
                                enabled = enabled,
                            )
                        }
                    }
                }
                Text(
                    "拖动频段后进入「自定义」；标准档下拖动任意频段会自动切换。",
                    fontSize = 11.sp, color = colors.textTertiary, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
            Spacer(Modifier.height(18.dp))

            // ── 低音增强 ──
            SectionCard(title = "低音增强") {
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("0", color = colors.textTertiary, fontSize = 12.sp)
                    HorizontalShadeSlider(
                        progress = bass / 1000f,
                        onProgress = { EqualizerHost.setBass(it) },
                        enabled = enabled,
                        modifier = Modifier.weight(1f),
                    )
                    Text("100", color = colors.textTertiary, fontSize = 12.sp)
                }
            }
            // ── 动态范围压缩（DVC）──（设备不支持 DynamicsProcessing 时隐藏）
            if (dynamicsAvailable) {
                Spacer(Modifier.height(18.dp))
                SectionCard(title = "动态范围压缩 (DVC)") {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(46.dp)
                            .then(
                                if (dvc) Modifier.shadeInset(cornerRadius = 16.dp, offset = 3.dp, blur = 5.dp)
                                else Modifier
                            )
                            .flatTap { DynamicsFxHost.setDvc(!dvc) }
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "压低响度差",
                            fontSize = 14.sp,
                            color = if (dvc) colors.accent else colors.textPrimary,
                            fontWeight = if (dvc) FontWeight.SemiBold else FontWeight.Normal,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            if (dvc) "开" else "关",
                            fontSize = 13.sp,
                            color = if (dvc) colors.accent else colors.textTertiary,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                    Text(
                        "副歌不再突然炸耳，夜间/通勤听歌更舒适；对音质有轻微影响，默认关闭。",
                        fontSize = 11.sp, color = colors.textTertiary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )

                    Spacer(Modifier.height(6.dp))

                    // 声道平衡
                    Text(
                        "声道平衡", fontSize = 13.sp, color = colors.textSecondary,
                        modifier = Modifier.padding(start = 6.dp),
                    )
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text("左", color = colors.textTertiary, fontSize = 12.sp)
                        HorizontalShadeSlider(
                            progress = (balance + 100) / 200f,
                            onProgress = {
                                DynamicsFxHost.setBalance((it * 200 - 100).toInt())
                            },
                            enabled = true,
                            modifier = Modifier.weight(1f),
                        )
                        Text("右", color = colors.textTertiary, fontSize = 12.sp)
                    }
                    Text(
                        when {
                            balance == 0 -> "居中"
                            balance < 0 -> "偏左 ${-balance}%"
                            else -> "偏右 ${balance}%"
                        },
                        color = colors.textTertiary, fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 8.dp),
                    )
                }
            }

            // ── 播放速度 / 音调 ──
            Spacer(Modifier.height(18.dp))
            SectionCard(title = "速度与音调") {
                Text(
                    "速度 ×%.2f".format(speed),
                    fontSize = 13.sp, color = colors.textSecondary,
                    modifier = Modifier.padding(start = 6.dp),
                )
                HorizontalShadeSlider(
                    progress = (speed - 0.5f) / 1.5f,
                    onProgress = { PlayerHost.setPlaybackParams(0.5f + it * 1.5f, pitch) },
                    enabled = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                )
                Text(
                    "音调 ×%.2f".format(pitch),
                    fontSize = 13.sp, color = colors.textSecondary,
                    modifier = Modifier.padding(start = 6.dp),
                )
                HorizontalShadeSlider(
                    progress = (pitch - 0.5f) / 1.5f,
                    onProgress = { PlayerHost.setPlaybackParams(speed, 0.5f + it * 1.5f) },
                    enabled = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                )
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "变速不变调可分开调；音调变高/变低即升/降 key。",
                        fontSize = 11.sp, color = colors.textTertiary, modifier = Modifier.weight(1f),
                    )
                    Text(
                        "重置",
                        color = colors.accent, fontSize = 12.sp, fontWeight = FontWeight.Medium,
                        modifier = Modifier.flatTap {
                            PlayerHost.setPlaybackParams(1f, 1f)
                        }.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }

            // ── 智能调音 ──
            Spacer(Modifier.height(18.dp))
            SectionCard(title = "智能调音") {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(46.dp)
                        .then(
                            if (smartEq) Modifier.shadeInset(cornerRadius = 16.dp, offset = 3.dp, blur = 5.dp)
                            else Modifier
                        )
                        .flatTap {
                            Prefs.smartEq = !smartEq
                            smartEq = !smartEq
                        }
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "按曲目风格自动选预设",
                        fontSize = 14.sp,
                        color = if (smartEq) colors.accent else colors.textPrimary,
                        fontWeight = if (smartEq) FontWeight.SemiBold else FontWeight.Normal,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        if (smartEq) "开" else "关",
                        fontSize = 13.sp,
                        color = if (smartEq) colors.accent else colors.textTertiary,
                        fontWeight = FontWeight.Medium,
                    )
                }
                Text(
                    lastApplied ?: "开启后换歌时自动套用（流行→Pop、摇滚→Rock、古典→Classical…）",
                    fontSize = 11.sp, color = colors.textTertiary,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }

            Spacer(Modifier.height(132.dp))   // 底部留白（播放栏）
        }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    val colors = LocalShadeColors.current
    Column(
        Modifier.fillMaxWidth().shadeSurface(cornerRadius = 24.dp, offset = 6.dp, blur = 10.dp).padding(12.dp),
    ) {
        Text(
            title, fontSize = 13.sp, color = colors.textSecondary,
            modifier = Modifier.padding(start = 6.dp, bottom = 8.dp),
        )
        content()
    }
}

/** 平面点击（凸起容器内部用；不给阴影）。 */
private fun Modifier.flatTap(onTap: () -> Unit): Modifier = this.pointerInput(onTap) {
    detectTapGestures(onTap = { onTap() })
}

/** 竖直频段滑杆：凹陷轨道 + accent 填充（自下而上）。 */
@Composable
private fun VerticalBandSlider(
    label: String,
    progress: Float,
    onProgress: (Float) -> Unit,
    enabled: Boolean,
) {
    val colors = LocalShadeColors.current
    var trackH by remember { mutableFloatStateOf(1f) }
    var dragging by remember { mutableStateOf(false) }
    var value by remember(progress) { mutableFloatStateOf(progress) }

    val fill by animateColorAsState(
        if (enabled) colors.accent else colors.textTertiary.copy(alpha = 0.4f),
        tween(200), label = "bandFill",
    )

    fun update(y: Float) {
        if (!enabled) return
        value = (1f - y / trackH).coerceIn(0f, 1f)
        onProgress(value)
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(dbLabel(value), color = colors.textSecondary, fontSize = 11.sp)
        Spacer(Modifier.height(6.dp))
        Box(
            Modifier
                .width(26.dp)
                .height(150.dp)
                .onSizeChanged { trackH = it.height.toFloat().coerceAtLeast(1f) }
                .shadeInset(cornerRadius = 13.dp, offset = 3.dp, blur = 4.dp)
                .pointerInput(enabled) {
                    detectTapGestures { offset -> update(offset.y) }
                }
                .pointerInput(enabled) {
                    detectDragGestures(
                        onDragStart = { offset -> dragging = true; update(offset.y) },
                        onDragEnd = { dragging = false },
                        onDragCancel = { dragging = false },
                    ) { change, _ -> update(change.position.y) }
                },
        ) {
            // 填充：从底部往上
            Box(
                Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(value)
                    .align(Alignment.BottomCenter)
                    .padding(3.dp)
                    .background(fill, RoundedCornerShape(10.dp)),
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(label, color = colors.textSecondary, fontSize = 11.sp, textAlign = TextAlign.Center)
    }
}

/** Hz → 人话（60 / 230 / 1k / 4k / 14k）。 */
private fun freqLabel(hz: Int): String = when {
    hz >= 1000 -> "${hz / 1000}k"
    hz > 0 -> "$hz"
    else -> "—"
}

/** 进度 → dB 文案（范围 -15..+15dB）。 */
private fun dbLabel(progress: Float): String {
    val db = (progress * 30 - 15)
    return "%+.0f".format(db) + "dB"
}
