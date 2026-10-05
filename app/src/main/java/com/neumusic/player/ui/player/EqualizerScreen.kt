package com.neumusic.player.ui.player

import android.Manifest
import android.content.pm.PackageManager
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.neumusic.player.data.*
import com.neumusic.player.player.*
import com.neumusic.player.player.EqualizerHost.EqPreset
import com.neumusic.player.shade.*
import com.neumusic.player.ui.common.*
import com.neumusic.player.ui.home.DetailTopBar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 三个分区共用滚动页头；各项根据自身能力启停，速度/音调始终可用。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EqualizerScreen(onBack: () -> Unit) {
    val colors = LocalShadeColors.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val barSpace = rememberPlayerBarSpace()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val scroll = rememberScrollState()
    val engine by EqualizerHost.engine.collectAsState()
    val enabled by EqualizerHost.enabled.collectAsState()
    val mounted by EqualizerHost.mounted.collectAsState()
    val available by EqualizerHost.available.collectAsState()
    val control by EqualizerHost.hasControl.collectAsState()
    val bands by EqualizerHost.bands.collectAsState()
    val presets by EqualizerHost.presets.collectAsState()
    val selected by EqualizerHost.selectedPreset.collectAsState()
    val bass by EqualizerHost.bass.collectAsState()
    val bassAvailable by EqualizerHost.bassAvailable.collectAsState()
    val bassControl by EqualizerHost.bassHasControl.collectAsState()
    val bassAdjustable by EqualizerHost.bassStrengthSupported.collectAsState()
    val actualEqEnabled by EqualizerHost.actualEnabled.collectAsState()
    val generation by EqualizerHost.editGeneration.collectAsState()
    val approximation by EqualizerHost.approximation.collectAsState()
    val dynamicsAvailable by DynamicsFxHost.available.collectAsState()
    val dynamicsMounted by DynamicsFxHost.mounted.collectAsState()
    val dynamicsControl by DynamicsFxHost.hasControl.collectAsState()
    val dvcMode by DynamicsFxHost.dvcMode.collectAsState()
    val balance by DynamicsFxHost.balance.collectAsState()
    val limiter by DynamicsFxHost.limiterEnabled.collectAsState()
    val threshold by DynamicsFxHost.limiterThreshold.collectAsState()
    val release by DynamicsFxHost.limiterRelease.collectAsState()
    val loudnessAvailable by LoudnessHost.available.collectAsState()
    val loudnessMounted by LoudnessHost.mounted.collectAsState()
    val loudnessControl by LoudnessHost.hasControl.collectAsState()
    val loudness by LoudnessHost.gain.collectAsState()
    val speed by PlayerHost.speed.collectAsState()
    val pitch by PlayerHost.pitch.collectAsState()
    val vizOn by Prefs.barVizFlow.collectAsState()
    val fft by VizHost.usingFft.collectAsState()
    val sid by AudioFxController.sessionId.collectAsState()
    var smartEq by remember { mutableStateOf(Prefs.smartEq) }
    val lastApplied by SmartEq.lastApplied.collectAsState()
    var mapVersion by remember { mutableIntStateOf(0) }
    var add by remember { mutableStateOf(false) }
    var delete by remember { mutableStateOf<EqPreset?>(null) }
    var genrePick by remember { mutableStateOf<Pair<Int, String>?>(null) }
    var busy by remember { mutableStateOf(false) }
    var autoEqOpen by remember { mutableStateOf(false) }
    var exportText by remember { mutableStateOf("") }
    val curve = remember { mutableStateOf(EqualizerHost.curveSnapshot()) }
    LaunchedEffect(bands, engine, selected, bass, enabled, mounted) { curve.value = EqualizerHost.curveSnapshot() }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) AudioFxController.retryFft()
    }
    fun switchViz(on: Boolean) {
        Prefs.barViz = on
        AudioFxController.setVisualizationEnabled(on)
        if (on && ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED && !Prefs.vizPermissionAsked) {
            Prefs.vizPermissionAsked = true
            permission.launch(Manifest.permission.RECORD_AUDIO)
        } else if (on) AudioFxController.retryFft()
    }
    val importFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            busy = true
            val result = runCatching {
                val pair = withContext(Dispatchers.IO) {
                    val name = ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                        if (c.moveToFirst()) c.getString(0) else null
                    } ?: "导入预设"
                    val text = ctx.contentResolver.openInputStream(uri)?.use {
                        val bytes = readPresetBytes(it)
                        require(bytes.size <= 256 * 1024) { "预设文件超过 256 KB" }
                        bytes.toString(Charsets.UTF_8)
                    } ?: error("无法读取文件")
                    name.substringBeforeLast('.') to text
                }
                EqualizerHost.importPreset(pair.first, pair.second)
            }
            toastMain(ctx, result.fold({ importMessage(it) }, { it.message ?: "导入失败" }))
            busy = false
        }
    }
    val exportFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri != null) scope.launch {
            val text = exportText
            val result = withContext(Dispatchers.IO) { runCatching {
                val out = ctx.contentResolver.openOutputStream(uri) ?: error("无法写入文件")
                out.use { it.write(text.toByteArray(Charsets.UTF_8)) }
            } }
            toastMain(ctx, if (result.isSuccess) "已导出预设" else result.exceptionOrNull()?.message ?: "导出失败")
        }
    }
    val precise = engine.name == "PRECISE"
    val eqReady = mounted && (precise || (available && control))
    val dynReady = dynamicsAvailable && dynamicsMounted && dynamicsControl
    Box(Modifier.fillMaxSize().background(colors.background).navigationBarsPadding()) {
        Column(Modifier.fillMaxSize().verticalScroll(scroll)) {
            DetailTopBar("音效", onBack, horizontalPadding = 16.dp)
            SegmentedControl(listOf("均衡", "动态", "其它"), tab, { tab = it },
                Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            AnimatedContent(tab, transitionSpec = { fadeIn(tween(170)) togetherWith fadeOut(tween(120)) }, label = "fxTab") { section ->
                Column(verticalArrangement = Arrangement.spacedBy(18.dp), modifier = Modifier.padding(top = 10.dp)) {
                    when (section) {
                        0 -> {
                            SectionCard("均衡引擎") {
                                ToggleRow("启用均衡器", enabled, eqReady) { EqualizerHost.setEnabled(it) }
                                SegmentedControl(listOf("系统", "精确"), if (precise) 1 else 0,
                                    { EqualizerHost.setEngine(EqualizerHost.EqEngine.entries[it]) }, Modifier.padding(vertical = 8.dp))
                                Help(if (precise) "10 段均衡 · 参数滤波器 · 自动前级与峰值保护" else "频段与精度由设备决定；参数预设在系统引擎中近似转换。")
                                if (!eqReady) Help(when {
                                    !precise && sid > 0 && !available -> "此设备不支持系统均衡器，可切换精确引擎。"
                                    !mounted -> "暂未挂载，播放器就绪后可用。"
                                    else -> "系统均衡器已被其他应用接管。"
                                })
                            }
                            SectionCard("预设") {
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    presets.forEach { p ->
                                        val selectable = eqReady && EqualizerHost.canSelectPreset(p)
                                        Text(p.name, color = if (!selectable) colors.textTertiary else if (selected == p.name) colors.accent else colors.textPrimary,
                                            fontSize = 13.sp, modifier = Modifier
                                                .then(if (selected == p.name) Modifier.shadeInset(14.dp, 3.dp, 4.dp) else Modifier)
                                                .semantics { this.selected = selected == p.name; if (!selectable) disabled() }
                                                .pointerInput(p.name, selectable, selected) {
                                                    detectTapGestures(onTap = {
                                                        if (selectable && !EqualizerHost.selectPreset(p.name)) toastMain(ctx, "此预设不适用于当前引擎")
                                                    }, onLongPress = { if (!p.builtin) delete = p })
                                                }.padding(horizontal = 12.dp, vertical = 14.dp))
                                    }
                                    FlatAction("＋ 新预设", eqReady) { add = true }
                                }
                                Help("拖动会修改选中的预设；长按自建预设可删除。灰色预设仍可保留和导出。")
                                approximation?.let { Help(it) }
                                presets.firstOrNull { it.name == selected }?.source?.takeIf { it.startsWith("autoeq:") }?.let { Help("来源：${it.removePrefix("autoeq:")}") }
                            }
                            SectionCard("响应曲线") {
                                EqCurveView(curve, precise)
                                if (!enabled) Help("均衡器已关闭，曲线显示保存的设置。")
                                Help(if (precise) "显示滤波器及自动前级后的响应；实际采样率会影响最高频段。" else "系统响应为估算，厂商实现可能不同。")
                            }
                            SectionCard("频段") {
                                if (EqualizerHost.activeParametric) Help("当前使用参数滤波器；拖动频段将转换成 10 段近似曲线并修改此预设。")
                                if (bands.isEmpty()) Help("此引擎暂未提供频段。") else {
                                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        bands.indices.forEach { i ->
                                            VerticalBandSlider(freqLabel(EqualizerHost.bandFreqs.getOrElse(i) { 0 }), EqualizerHost.bandProgress(i),
                                                EqualizerHost.bandLevelRange, eqReady && enabled, generation,
                                                onProgress = { EqualizerHost.setBandProgress(i, it, generation); curve.value = EqualizerHost.curveSnapshot() },
                                                onCommit = { EqualizerHost.commitBands(generation) })
                                        }
                                    }
                                    FlatAction("归零", eqReady && enabled) {
                                        bands.indices.forEach { EqualizerHost.setBandProgress(it, .5f) }
                                        EqualizerHost.commitBands(); curve.value = EqualizerHost.curveSnapshot()
                                    }
                                }
                            }
                            SectionCard("预设交换与耳机补偿") {
                                FlatAction(if (busy) "导入中…" else "导入 EqualizerAPO / GraphicEQ", !busy) { importFile.launch(arrayOf("text/plain", "application/octet-stream")) }
                                FlatAction("导出选中预设 / 当前曲线") {
                                    runCatching { EqualizerHost.exportPreset(selected) ?: error("无可导出的预设") }.onSuccess {
                                        exportText = it; exportFile.launch("${selected ?: "NeuMusic"}.txt")
                                    }.onFailure { toastMain(ctx, it.message ?: "无法导出") }
                                }
                                FlatAction("搜索耳机补偿 · AutoEq") { autoEqOpen = true }
                                Help("耳机补偿来自 AutoEq 测量结果，按型号选择；导入后可在精确引擎完整使用。")
                            }
                        }
                        1 -> {
                            SectionCard("动态范围压缩 · DVC") {
                                if (!dynReady) Help(if (!dynamicsAvailable) "此设备不支持动态处理。" else "动态处理暂未挂载或已被其他应用接管。")
                                Row(Modifier.horizontalScroll(rememberScrollState())) {
                                    SegmentedControl(listOf("关", "轻", "中", "强", "夜间"), listOf("off", "light", "medium", "strong", "night").indexOf(dvcMode).coerceAtLeast(0),
                                        { if (dynReady) DynamicsFxHost.setDvcMode(listOf("off", "light", "medium", "strong", "night")[it]) }, Modifier.alphaDisabled(!dynReady))
                                }
                                Help("压低安静段与高潮的响度差；强档会更明显改变动态。")
                            }
                            SectionCard("限幅保护") {
                                ToggleRow("系统限幅", limiter, dynReady) { DynamicsFxHost.setLimiterEnabled(it) }
                                ValueSlider("阈值", threshold, -6f..-.1f, dynReady && limiter, { "%.1f dB".format(it) },
                                    { DynamicsFxHost.previewLimiterThreshold(it) }, { DynamicsFxHost.commitLimiterThreshold() })
                                ValueSlider("释放", release, 1.5f..500f, dynReady && limiter, { "%.0f ms".format(it) },
                                    { DynamicsFxHost.previewLimiterRelease(it) }, { DynamicsFxHost.commitLimiterRelease() })
                                Help("精确均衡另有内部峰值保护，先限幅再输出音频。")
                            }
                            SectionCard("声道平衡") {
                                ValueSlider("左 ← 居中 → 右", balance.toFloat(), -100f..100f, dynReady, {
                                    when { it.toInt() == 0 -> "居中"; it < 0 -> "偏左 ${-it.toInt()}%"; else -> "偏右 ${it.toInt()}%" }
                                }, { DynamicsFxHost.previewBalance(it.toInt()) }, { DynamicsFxHost.commitBalance() })
                                FlatAction("居中", dynReady) { DynamicsFxHost.commitBalance(0) }
                            }
                            SectionCard("低音增强") {
                                if (bassAvailable && bassAdjustable) ValueSlider("强度", bass.toFloat(), 0f..1000f, eqReady && enabled && bassControl, { "${(it / 10).toInt()}%" },
                                    { EqualizerHost.setBass(it / 1000f) }, { EqualizerHost.commitBass() })
                                else if (bassAvailable) {
                                    ToggleRow("固定强度低音", bass > 0, eqReady && enabled && bassControl) {
                                        EqualizerHost.setBass(if (it) 1f else 0f); EqualizerHost.commitBass()
                                    }
                                    Help("设备只支持固定强度。")
                                } else Help("此设备暂不支持低音增强。")
                            }
                            SectionCard("响度增强") {
                                ValueSlider("增益", loudness / 100f, 0f..9f, loudnessAvailable && loudnessMounted && loudnessControl, { "+%.1f dB".format(it) },
                                    { LoudnessHost.previewGain((it * 100).toInt()) }, { LoudnessHost.commitGain() })
                                Help(if (loudnessAvailable) "提升响度并压缩峰值；与 DVC 叠加时建议使用低档。" else "此设备暂不支持响度增强。")
                            }
                        }
                        else -> {
                            SectionCard("速度与音调") {
                                ValueSlider("速度", speed, .5f..2f, true, { "×%.2f".format(it) },
                                    { PlayerHost.previewPlaybackParams(speed = it) }, { PlayerHost.commitPlaybackParams() })
                                ValueSlider("音调", pitch, .5f..2f, true, { "×%.2f".format(it) },
                                    { PlayerHost.previewPlaybackParams(pitch = it) }, { PlayerHost.commitPlaybackParams() })
                                FlatAction("重置速度与音调") { PlayerHost.setPlaybackParams(1f, 1f) }
                            }
                            SectionCard("智能调音") {
                                ToggleRow("按曲目风格自动选预设", smartEq) { Prefs.smartEq = it; smartEq = it }
                                Help(lastApplied ?: "换歌时按下面的映射套用预设。")
                                key(mapVersion, presets) {
                                    FlowRow {
                                        SmartEq.GENRES.forEach { (code, label) ->
                                            FlatAction("$label · ${EqualizerHost.genreMap()[code]?.takeIf { it.isNotBlank() } ?: "未映射"}") { genrePick = code to label }
                                        }
                                    }
                                }
                            }
                            SectionCard("音频可视化") {
                                ToggleRow("播放栏与播放页可视化", vizOn) { switchViz(it) }
                                Help(when {
                                    !vizOn -> "关闭后停止电平采样与发布。"
                                    fft -> "系统 FFT 频谱"
                                    ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED -> "录音权限未授予，使用波形电平；不采集麦克风。"
                                    else -> "系统 Visualizer 不可用，使用波形电平。"
                                })
                            }
                            SectionCard("空间音频") {
                                val info by SpatialInfo.state.collectAsState()
                                Help(if (!info.supported) "当前系统不提供空间音频状态。" else
                                    "${if (info.available) "输出设备可用" else "输出设备不可用"} · ${if (info.enabled) "系统已开启" else "系统未开启"}")
                                if (info.supported) Help("头部跟踪：${if (info.headTracker) "可用" else "不可用"} · 多声道能力：${if (info.multichannelCapable) "支持" else "不支持"}")
                                FlatAction("刷新状态") { SpatialInfo.refresh() }
                                Help("空间音频由系统与输出设备决定。")
                            }
                            SectionCard("诊断与系统音效") {
                                Help("当前引擎：${if (precise) "精确" else "系统"} · 会话 $sid")
                                Help("系统均衡：${if (available) "支持" else "不可用"} · ${if (control) "拥有控制权" else "未控制"}")
                                Help("动态处理：${if (dynamicsAvailable) "支持" else "不可用"} · 可视化：${if (fft) "FFT" else "PCM"}")
                                Help("均衡实际状态：${if (actualEqEnabled) "已启用" else "未启用"} · 低音控制权：${if (bassControl) "拥有" else "未控制"}")
                                Help(lastApplied ?: "智能调音尚未应用")
                                val panel = AudioFxController.systemPanelIntent()
                                FlatAction("打开系统音效面板", panel != null) {
                                    runCatching { ctx.startActivity(panel) }.onFailure { toastMain(ctx, "无法打开系统音效面板") }
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp + barSpace))
        }
        if (scroll.value > 0) TopEdgeFade()
    }
    if (add) AddPresetDialog({ add = false }) { EqualizerHost.addPreset(it); add = false }
    delete?.let { p ->
        ShadeDialog(onDismiss = { delete = null }, title = "删除预设") {
            Help("删除「${p.name}」？")
            FlatAction("取消") { delete = null }
            FlatAction("删除") { EqualizerHost.deletePreset(p.name); delete = null }
            FlatAction("导出此预设") { exportText = EqualizerHost.exportPreset(p.name).orEmpty(); exportFile.launch("${p.name}.txt") }
        }
    }
    genrePick?.let { (code, label) ->
        ShadeDialog(onDismiss = { genrePick = null }, title = "$label 的预设") {
            androidx.compose.foundation.lazy.LazyColumn(Modifier.heightIn(max = 360.dp)) {
                item { ShadeDialogRow("未映射") { EqualizerHost.setGenrePreset(code, null); mapVersion++; genrePick = null } }
                items(presets.size) { i ->
                    ShadeDialogRow(presets[i].name) { EqualizerHost.setGenrePreset(code, presets[i].name); mapVersion++; genrePick = null }
                }
            }
        }
    }
    if (autoEqOpen) AutoEqDialog({ autoEqOpen = false }) { entry ->
        toastMain(ctx, importMessage(EqualizerHost.importPreset(entry.name, entry.text, source = "autoeq:${entry.source}")))
        autoEqOpen = false
    }
}

@Composable
private fun ValueSlider(label: String, initial: Float, range: ClosedFloatingPointRange<Float>, enabled: Boolean,
    format: (Float) -> String, preview: (Float) -> Unit, commit: () -> Unit) {
    val colors = LocalShadeColors.current
    var value by remember(initial) { mutableFloatStateOf(initial) }
    Column(Modifier.padding(horizontal = 6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = colors.textSecondary, fontSize = 13.sp)
            Text(format(value), color = colors.textSecondary, fontSize = 13.sp)
        }
        HorizontalShadeSlider((value - range.start) / (range.endInclusive - range.start),
            { value = range.start + it * (range.endInclusive - range.start); preview(value) }, enabled,
            Modifier.fillMaxWidth().semantics { contentDescription = label }, { commit() })
    }
}

@Composable
private fun VerticalBandSlider(label: String, progress: Float, range: IntArray, enabled: Boolean, editKey: Long,
    onProgress: (Float) -> Unit, onCommit: () -> Unit) {
    val colors = LocalShadeColors.current
    var height by remember { mutableFloatStateOf(1f) }
    var value by remember(progress) { mutableFloatStateOf(progress) }
    val preview by rememberUpdatedState(onProgress)
    val commit by rememberUpdatedState(onCommit)
    val latestKey by rememberUpdatedState(editKey)
    fun update(y: Float) { value = (1 - y / height).coerceIn(0f, 1f); preview(value) }
    Column(Modifier.width(48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("%+.1f".format((range[0] + value * (range[1] - range[0])) / 100f), color = colors.textSecondary, fontSize = 11.sp)
        Box(Modifier.width(48.dp).height(156.dp).semantics {
            contentDescription = "$label Hz 增益"
            progressBarRangeInfo = ProgressBarRangeInfo(value, 0f..1f)
            if (!enabled) disabled()
            setProgress { target -> if (enabled) { value = target.coerceIn(0f, 1f); preview(value); commit(); true } else false }
        }.pointerInput(enabled, editKey) {
            val captured = editKey
            if (enabled) detectTapGestures { if (captured == latestKey) { update(it.y); commit() } }
        }.pointerInput(enabled, editKey) {
            val captured = editKey
            if (enabled) detectVerticalDragGestures(
                onDragStart = { if (captured == latestKey) update(it.y) },
                onDragEnd = { if (captured == latestKey) commit() },
                onDragCancel = { if (captured == latestKey) commit() },
            ) { c, _ -> if (captured == latestKey) update(c.position.y) }
        }, contentAlignment = Alignment.Center) {
            Box(Modifier.width(26.dp).fillMaxHeight().onSizeChanged { height = it.height.toFloat() }
                .shadeInset(13.dp, 3.dp, 4.dp)) {
                Box(Modifier.fillMaxWidth().fillMaxHeight(value).align(Alignment.BottomCenter).padding(3.dp)
                    .background(if (enabled) colors.accent else colors.textTertiary, RoundedCornerShape(10.dp)))
            }
        }
        Text(label, color = colors.textSecondary, fontSize = 11.sp)
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    val colors = LocalShadeColors.current
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).alphaDisabled(!enabled)
        .semantics(mergeDescendants = true) {
            role = Role.Switch
            toggleableState = if (checked) androidx.compose.ui.state.ToggleableState.On else androidx.compose.ui.state.ToggleableState.Off
            if (!enabled) disabled()
            onClick { if (enabled) { onChange(!checked); true } else false }
        }.pointerInput(checked, enabled) { detectTapGestures { if (enabled) onChange(!checked) } },
        verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = colors.textPrimary, fontSize = 14.sp, modifier = Modifier.weight(1f))
        ShadeSwitch(checked, { if (enabled) onChange(it) }, enabled = enabled)
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    val colors = LocalShadeColors.current
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).shadeSurface(24.dp, 6.dp, 10.dp).padding(12.dp)) {
        Text(title, color = colors.textSecondary, fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 6.dp, bottom = 8.dp))
        content()
    }
}

@Composable
private fun Help(text: String) {
    Text(text, color = LocalShadeColors.current.textSecondary, fontSize = 12.sp, lineHeight = 18.sp, modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp))
}

@Composable
private fun FlatAction(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    Text(label, color = if (enabled) LocalShadeColors.current.accent else LocalShadeColors.current.textTertiary,
        fontSize = 13.sp, modifier = Modifier.heightIn(min = 48.dp)
            .flatPressable { if (enabled) onClick() }.semantics { if (!enabled) disabled() }
            .padding(horizontal = 12.dp, vertical = 14.dp))
}
private fun Modifier.alphaDisabled(disabled: Boolean) = if (disabled) alpha(.45f) else this

private fun freqLabel(hz: Int): String = if (hz >= 1000) "${if (hz % 1000 == 0) (hz / 1000).toString() else "%.1f".format(hz / 1000f)}k" else "$hz"
private fun importMessage(result: EqTextCodec.ParseResult): String = result.error ?:
    "已导入 ${result.accepted} 条，跳过 ${result.skipped} 条；参数预设请使用精确引擎。"
private fun readPresetBytes(input: java.io.InputStream): ByteArray {
    val out = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    while (out.size() <= 256 * 1024) {
        val count = input.read(buffer, 0, minOf(buffer.size, 256 * 1024 + 1 - out.size()))
        if (count < 0) break
        if (count == 0) break
        out.write(buffer, 0, count)
    }
    return out.toByteArray()
}

@Composable
private fun AddPresetDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    val colors = LocalShadeColors.current
    var name by remember { mutableStateOf("") }
    ShadeDialog(onDismiss = onDismiss, title = "新增预设") {
        Help("保存当前频段与参数。")
        BasicTextField(name, { name = it }, singleLine = true, textStyle = TextStyle(color = colors.textPrimary, fontSize = 15.sp),
            cursorBrush = SolidColor(colors.accent), modifier = Modifier.fillMaxWidth().padding(16.dp)
                .semantics { contentDescription = "预设名称" }.shadeInset(12.dp, 2.dp, 3.dp).padding(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            FlatAction("取消", onClick = onDismiss)
            FlatAction("创建") { onConfirm(name) }
        }
    }
}

@Composable
private fun AutoEqDialog(onDismiss: () -> Unit, onSelect: (AutoEq.Entry) -> Unit) {
    val ctx = LocalContext.current
    val colors = LocalShadeColors.current
    var query by remember { mutableStateOf("") }
    var entries by remember { mutableStateOf<List<AutoEq.Entry>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        runCatching { withContext(Dispatchers.IO) { AutoEq.load(ctx) } }
            .onSuccess { entries = it }.onFailure { error = "无法加载耳机补偿库" }
    }
    val filtered = remember(entries, query) { entries?.filter { it.name.contains(query.trim(), ignoreCase = true) }.orEmpty() }
    // 型号数从 catalog 实际条目推导（scripts/update-autoeq.mjs 随上游重生成，写死会失真）；
    // 加载完成前不显示数字。
    val catalogCount = entries?.size
    val catalogHelp = if (catalogCount == null) "离线精选型号（Score ≥ 80），仅覆盖库内型号；请选择完全一致的耳机。"
        else "离线精选 $catalogCount 个型号（Score ≥ 80），仅覆盖库内型号；请选择完全一致的耳机。"
    ShadeDialog(onDismiss = onDismiss, title = "耳机补偿 · AutoEq") {
        Help(catalogHelp)
        BasicTextField(query, { query = it }, singleLine = true, textStyle = TextStyle(color = colors.textPrimary, fontSize = 14.sp),
            cursorBrush = SolidColor(colors.accent), modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                .semantics { contentDescription = "搜索耳机型号" }.shadeInset(12.dp, 2.dp, 3.dp).padding(12.dp),
            decorationBox = { inner -> if (query.isEmpty()) Text("搜索型号", color = colors.textTertiary, fontSize = 14.sp); inner() })
        when { error != null -> Help(error!!); entries == null -> Help("加载中…"); filtered.isEmpty() -> Help("库内没有匹配型号，可导入外部参数文本。") }
        androidx.compose.foundation.lazy.LazyColumn(Modifier.heightIn(max = 340.dp)) {
            items(filtered, key = { "${it.name} · ${it.source}" }) { entry -> ShadeDialogRow("${entry.name} · ${entry.source}") { onSelect(entry) } }
        }
    }
}
