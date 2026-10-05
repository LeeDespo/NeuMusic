package com.neumusic.player.player

import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import com.neumusic.player.data.EqPresetStore
import com.neumusic.player.data.EqTextCodec
import com.neumusic.player.data.Prefs
import com.neumusic.player.data.AppLog
import com.neumusic.player.player.eq.Biquad
import com.neumusic.player.player.eq.EqChain
import com.neumusic.player.player.eq.EqFitter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject
import java.util.UUID
import kotlin.math.abs

/** Main-thread audiofx ownership and committed UI state. Drag previews never write preferences. */
object EqualizerHost {
    enum class EqEngine { PLATFORM, PRECISE }
    data class EqPreset(
        val name: String, val gains: IntArray, val builtin: Boolean,
        val id: String = UUID.randomUUID().toString(), val frequenciesHz: List<Double> = emptyList(),
        val preampDb: Double = 0.0, val filters: List<Biquad.Filter> = emptyList(),
        val source: String = "user", val engine: String = "PLATFORM",
        val graphicResponse: Boolean = false,
    )
    private val _available = MutableStateFlow(false)
    val available: StateFlow<Boolean> = _available
    private val _mounted = MutableStateFlow(false)
    val mounted: StateFlow<Boolean> = _mounted
    private val _hasControl = MutableStateFlow(false)
    val hasControl: StateFlow<Boolean> = _hasControl
    private val _actualEnabled = MutableStateFlow(false)
    val actualEnabled: StateFlow<Boolean> = _actualEnabled
    private val _bassHasControl = MutableStateFlow(false)
    val bassHasControl: StateFlow<Boolean> = _bassHasControl
    private val _editGeneration = MutableStateFlow(0L)
    val editGeneration: StateFlow<Long> = _editGeneration
    val activeParametric: Boolean get() = !activeFilters.isNullOrEmpty()
    private var filtersDirty = false
    private var commitCount = 0L
    private val _approximation = MutableStateFlow<String?>(null)
    val approximation: StateFlow<String?> = _approximation
    private val _bassAvailable = MutableStateFlow(false)
    val bassAvailable: StateFlow<Boolean> = _bassAvailable
    private val _bassStrengthSupported = MutableStateFlow(false)
    val bassStrengthSupported: StateFlow<Boolean> = _bassStrengthSupported
    private val _enabled = MutableStateFlow(false)
    val enabled: StateFlow<Boolean> = _enabled
    private val _engine = MutableStateFlow(EqEngine.PLATFORM)
    val engine: StateFlow<EqEngine> = _engine
    private val _presets = MutableStateFlow<List<EqPreset>>(emptyList())
    val presets: StateFlow<List<EqPreset>> = _presets
    private val _selected = MutableStateFlow<String?>(null)
    val selectedPreset: StateFlow<String?> = _selected
    private val _bands = MutableStateFlow(IntArray(0))
    val bands: StateFlow<IntArray> = _bands
    private val _bass = MutableStateFlow(0)
    val bass: StateFlow<Int> = _bass
    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var session = 0
    private var platformFreqs = emptyList<Double>()
    private var platformRange = intArrayOf(-1500, 1500)
    private var pendingBands = IntArray(0)
    private var pendingBass = 0
    private var activeFilters: List<Biquad.Filter>? = null
    private var preamp = 0.0
    var onProcessingChanged: (() -> Unit)? = null
    val bandFreqs: IntArray get() = currentFrequencies().map { it.toInt() }.toIntArray()
    val bandLevelRange: IntArray get() = if (_engine.value == EqEngine.PRECISE) intArrayOf(-1200, 1200) else platformRange.copyOf()
    val numberOfBands: Int get() = currentFrequencies().size
    private fun currentFrequencies() = if (_engine.value == EqEngine.PRECISE) EqChain.FREQUENCIES_HZ else platformFreqs

    fun attach(sessionId: Int) {
        if (sessionId <= 0) { release(); return }
        if (session == sessionId && _mounted.value) return
        release()
        _editGeneration.value++
        filtersDirty = false
        session = sessionId
        _engine.value = runCatching { EqEngine.valueOf(Prefs.eqEngine) }.getOrDefault(EqEngine.PLATFORM)
        _enabled.value = Prefs.eqEnabled
        pendingBass = Prefs.eqBass.coerceIn(0, 1000)
        _bass.value = pendingBass
        preamp = Prefs.eqPreampDb.toDouble()
        _presets.value = EqPresetStore.read()
        if (_engine.value == EqEngine.PLATFORM) attachPlatform(sessionId)
        else { _mounted.value = true; _hasControl.value = true; _bassHasControl.value = true; _bassAvailable.value = true; _bassStrengthSupported.value = true }
        val saved = if (_engine.value == EqEngine.PRECISE) Prefs.eqPreciseBands else Prefs.eqBands
        pendingBands = if (saved.size == numberOfBands) saved.copyOf() else IntArray(numberOfBands)
        _bands.value = pendingBands.copyOf()
        _selected.value = Prefs.eqSelected?.takeIf { name -> _presets.value.any { it.name == name } }
        activeFilters = _presets.value.firstOrNull { it.name == _selected.value }?.filters?.takeIf { it.isNotEmpty() && _engine.value == EqEngine.PRECISE }
        migrateGenreMap()
        applyAll()
        AppLog.i("EqualizerHost", "attach session=$session engine=${_engine.value} mounted=${_mounted.value} bands=${pendingBands.joinToString()}")
    }
    private fun attachPlatform(sessionId: Int) {
        runCatching {
            val eq = Equalizer(0, sessionId)
            equalizer = eq
            platformFreqs = List(eq.numberOfBands.toInt()) { eq.getCenterFreq(it.toShort()) / 1000.0 }
            platformRange = eq.bandLevelRange.map { it.toInt() }.toIntArray()
            _available.value = true
            _mounted.value = true
            _hasControl.value = eq.hasControl()
            _actualEnabled.value = eq.enabled
            eq.setControlStatusListener { _, control ->
                if (equalizer === eq) {
                    _hasControl.value = control
                    AppLog.i("EqualizerHost", "control session=$session control=$control")
                    if (control) applyAll()
                }
            }
            eq.setEnableStatusListener { _, enabled ->
                if (equalizer === eq) {
                    _actualEnabled.value = enabled
                    AppLog.i("EqualizerHost", "actual-enabled session=$session enabled=$enabled")
                }
            }
            // Legacy rows came from this installation's current audiofx device. Annotate once.
            val migrated = _presets.value.map { p ->
                if (p.frequenciesHz.isEmpty() && p.filters.isEmpty() && p.engine == "PLATFORM" && p.gains.size == platformFreqs.size)
                    p.copy(frequenciesHz = platformFreqs.toList()) else p
            }
            if (migrated != _presets.value) {
                saveStore(migrated)
                AppLog.i("EqualizerHost", "migrate legacy device frequencies=${platformFreqs.joinToString()}")
            }
            if (!Prefs.eqSeeded) {
                val additions = (0 until eq.numberOfPresets.toInt()).mapNotNull { i -> runCatching {
                    eq.usePreset(i.toShort())
                    EqPreset(eq.getPresetName(i.toShort()).replace(Regex("[\\u0000-\\u001F]"), "").trim().ifEmpty { "未命名" },
                        IntArray(eq.numberOfBands.toInt()) { eq.getBandLevel(it.toShort()).toInt() }, true,
                        frequenciesHz = platformFreqs.toList(), source = "device")
                }.getOrNull() }
                saveStore(_presets.value + additions.filter { added -> _presets.value.none { it.name == added.name } })
                Prefs.eqSeeded = true
            }
            runCatching {
                val bb = BassBoost(0, sessionId)
                bassBoost = bb
                _bassAvailable.value = true
                _bassHasControl.value = bb.hasControl()
                bb.setControlStatusListener { _, control ->
                    if (bassBoost === bb) {
                        _bassHasControl.value = control
                        AppLog.i("EqualizerHost", "bass-control session=$session control=$control")
                        if (control) applyBass()
                    }
                }
                _bassStrengthSupported.value = bb.strengthSupported
                bb.setStrength(pendingBass.toShort())
                if (pendingBass > 0) pendingBass = bb.roundedStrength.toInt().coerceIn(0, 1000)
                _bass.value = pendingBass
            }
        }.onFailure { error -> releasePlatform(); _available.value = false; _mounted.value = false; _hasControl.value = false; AppLog.w("EqualizerHost", "attach failed session=$sessionId", error) }
    }
    private fun releasePlatform() {
        equalizer?.let { runCatching { it.setControlStatusListener(null); it.setEnableStatusListener(null); it.release() } }
        bassBoost?.let { runCatching { it.setControlStatusListener(null); it.release() } }
        equalizer = null; bassBoost = null; _bassAvailable.value = false; _bassStrengthSupported.value = false; _bassHasControl.value = false; _actualEnabled.value = false
    }
    fun release() {
        // Commit a gesture interrupted by session destruction.
        if (pendingBands.size == _bands.value.size && (filtersDirty || !pendingBands.contentEquals(_bands.value))) commitBands()
        if (pendingBass != _bass.value) commitBass()
        if (session > 0) AppLog.i("EqualizerHost", "release session=$session engine=${_engine.value}")
        _editGeneration.value++
        releasePlatform(); session = 0; _mounted.value = false; _hasControl.value = false; _actualEnabled.value = false
        onProcessingChanged?.invoke()
    }
    fun detach() = release()
    fun setEngine(value: EqEngine) {
        if (_engine.value == value) return
        commitBands(); commitBass(); _editGeneration.value++; filtersDirty = false
        val originalPresetName = _selected.value
        val oldFilters = activeFilters ?:  if (pendingBands.size == currentFrequencies().size) EqChain.graphicFilters(pendingBands, currentFrequencies(), if (_engine.value == EqEngine.PLATFORM) 0.96 else kotlin.math.sqrt(2.0)) else emptyList()
        releasePlatform()
        _mounted.value = false; _hasControl.value = false
        _engine.value = value; Prefs.eqEngine = value.name
        activeFilters = null
        if (value == EqEngine.PLATFORM && session > 0) attachPlatform(session)
        else if (value == EqEngine.PRECISE) { _mounted.value = session > 0; _hasControl.value = true; _bassHasControl.value = true; _bassAvailable.value = true; _bassStrengthSupported.value = true }
        if (originalPresetName != null && selectPreset(originalPresetName)) {
            AppLog.i("EqualizerHost", "engine=$value preserved-source=$originalPresetName approximation=${_approximation.value}")
            return
        }
        pendingBands = fittedFilters(oldFilters).gains
        AppLog.i("EqualizerHost", "engine=$value approximation=${_approximation.value}")
        _bands.value = pendingBands.copyOf(); _selected.value = null; Prefs.eqSelected = null
        persistBands(); applyAll()
    }
    private fun saveStore(value: List<EqPreset>) { EqPresetStore.save(value); _presets.value = value }
    fun canSelectPreset(p: EqPreset): Boolean = numberOfBands > 0 && (
        p.filters.isNotEmpty() ||
        p.frequenciesHz.size == p.gains.size && p.frequenciesHz.isNotEmpty() ||
        p.gains.size == numberOfBands && p.frequenciesHz.isEmpty() && p.engine == _engine.value.name)
    private fun q() = if (_engine.value == EqEngine.PLATFORM) 0.96 else kotlin.math.sqrt(2.0)
    private fun fittedFilters(filters: List<Biquad.Filter>): EqFitter.Fit {
        val r = bandLevelRange
        val fit = EqFitter.fitFilters(filters, currentFrequencies(), q(), r[0] / 100.0, r[1] / 100.0)
        _approximation.value = "近似转换（均方根误差 %.2f dB）".format(java.util.Locale.US, fit.rmsErrorDb)
        return fit
    }
    private fun sameFrequencies(a: List<Double>, b: List<Double>) = a.size == b.size && a.indices.all { abs(a[it] - b[it]) < 0.01 }
    fun selectPreset(name: String?): Boolean {
        val preset = name?.let { n -> _presets.value.firstOrNull { it.name == n } ?: return false }
        if (preset != null && !canSelectPreset(preset)) return false
        _editGeneration.value++
        filtersDirty = false
        _approximation.value = null
        if (preset != null) {
            activeFilters = preset.filters.takeIf { it.isNotEmpty() && _engine.value == EqEngine.PRECISE }
            pendingBands = when {
                preset.filters.isNotEmpty() -> fittedFilters(preset.filters).gains
                preset.graphicResponse -> {
                    val r = bandLevelRange
                    val fit = EqFitter.fitGraphic(preset.frequenciesHz, preset.gains.map { it / 100.0 }, currentFrequencies(), q(), r[0] / 100.0, r[1] / 100.0)
                    _approximation.value = "图形曲线近似拟合（均方根误差 %.2f dB）".format(java.util.Locale.US, fit.rmsErrorDb)
                    fit.gains
                }
                sameFrequencies(preset.frequenciesHz, currentFrequencies()) || preset.frequenciesHz.isEmpty() -> preset.gains.copyOf()
                else -> fittedFilters(EqChain.graphicFilters(preset.gains, preset.frequenciesHz, if (preset.engine == "PLATFORM") 0.96 else kotlin.math.sqrt(2.0))).gains
            }
            preamp = preset.preampDb; Prefs.eqPreampDb = preamp.toFloat()
            if (activeParametric) _approximation.value = null // Original filters are applied exactly in PRECISE.
        } else activeFilters = null
        _selected.value = name; Prefs.eqSelected = name
        _bands.value = pendingBands.copyOf(); persistBands(); applyAll()
        return true
    }
    fun addPreset(name: String) {
        commitBands()
        val p = EqPreset(uniqueName(name), pendingBands.copyOf(), false, frequenciesHz = currentFrequencies().toList(), preampDb = preamp,
            filters = activeFilters.orEmpty().toList(), engine = _engine.value.name)
        saveStore(_presets.value + p); selectPreset(p.name)
    }
    private fun uniqueName(raw: String): String {
        val base = raw.replace(Regex("[\\u0000-\\u001F]"), "").trim().ifEmpty { "新预设" }
        var result = base; var i = 2
        while (_presets.value.any { it.name == result }) result = "$base ${i++}"
        return result
    }
    fun deletePreset(name: String) {
        if (_presets.value.firstOrNull { it.name == name }?.builtin != false) return
        saveStore(_presets.value.filterNot { it.name == name }); if (_selected.value == name) selectPreset(null)
    }
    fun genreMap(): Map<Int, String> = runCatching {
        val o = JSONObject(Prefs.eqGenreMap)
        o.keys().asSequence().mapNotNull { key -> key.toIntOrNull()?.let { it to o.optString(key) } }.toMap()
    }.getOrDefault(emptyMap())
    fun setGenrePreset(genre: Int, presetName: String?) {
        val map = genreMap().toMutableMap(); map[genre] = presetName.orEmpty(); saveGenreMap(map)
    }
    private fun saveGenreMap(map: Map<Int, String>) {
        val o = JSONObject(); map.forEach { (k, v) -> o.put(k.toString(), v) }
        if (Prefs.eqGenreMap != o.toString()) Prefs.eqGenreMap = o.toString()
    }
    private fun migrateGenreMap() {
        val map = genreMap().toMutableMap()
        val defaults = SmartEq.defaultGenreMap()
        fun normalized(s: String) = s.lowercase().replace(Regex("\\s+"), "")
        for ((code, default) in defaults) {
            val existing = map[code]
            if (existing == "" || _presets.value.any { it.name == existing }) continue
            val wanted = existing ?: default
            // 预设库还没播种出同名预设时保留原名、留待下次 attach 再解析；
            // 写空串会被 attach 当作用户主动清除（"" 直接 continue），默认映射就永久丢了。
            val found = _presets.value.firstOrNull { normalized(it.name) == normalized(wanted) }?.name
            if (found != null) map[code] = found
        }
        saveGenreMap(map)
    }
    private fun applyAll() {
        runCatching { equalizer?.let { eq -> if (eq.hasControl()) { eq.enabled = _enabled.value && _engine.value == EqEngine.PLATFORM; _actualEnabled.value = eq.enabled; pendingBands.forEachIndexed { i, gain -> eq.setBandLevel(i.toShort(), gain.coerceIn(platformRange[0], platformRange[1]).toShort()) } } } }
        if (_engine.value == EqEngine.PRECISE) _actualEnabled.value = _enabled.value && _mounted.value
        applyBass(); onProcessingChanged?.invoke()
    }
    private fun applyBass() {
        runCatching { bassBoost?.let { bb -> if (bb.hasControl()) { if (!_enabled.value || pendingBass == 0) { bb.setStrength(0); bb.enabled = false } else { bb.setStrength(pendingBass.toShort()); bb.enabled = true } } } }
    }
    fun setEnabled(value: Boolean) { _enabled.value = value; Prefs.eqEnabled = value; applyAll() }
    fun setBandProgress(index: Int, progress: Float, generation: Long? = null) {
        if (generation != null && generation != _editGeneration.value) return
        if (index !in pendingBands.indices || !progress.isFinite()) return
        val r = bandLevelRange; val level = (r[0] + (r[1] - r[0]) * progress.coerceIn(0f, 1f)).toInt()
        if (pendingBands[index] == level) return
        pendingBands[index] = level
        // Explicit manual edit replaces imported parameter filters with the visible graphic controls.
        if (activeFilters != null || _selected.value?.let { n -> _presets.value.any { it.name == n && (it.graphicResponse || it.engine != _engine.value.name) } } == true) filtersDirty = true
        activeFilters = null
        if (_enabled.value) runCatching { equalizer?.let { if (it.hasControl()) it.setBandLevel(index.toShort(), level.toShort()) } }
        onProcessingChanged?.invoke()
    }
    fun commitBands(generation: Long? = null) {
        if (generation != null && generation != _editGeneration.value) return
        if (!filtersDirty && pendingBands.contentEquals(_bands.value)) return
        _bands.value = pendingBands.copyOf(); persistBands()
        _selected.value?.let { name -> saveStore(_presets.value.map { if (it.name == name) it.copy(gains = pendingBands.copyOf(), frequenciesHz = currentFrequencies().toList(), filters = activeFilters.orEmpty(), engine = _engine.value.name, graphicResponse = false) else it }) }
        filtersDirty = false
        AppLog.i("EqualizerHost", "commit-bands count=${++commitCount} engine=${_engine.value} bands=${pendingBands.joinToString()}")
    }
    private fun persistBands() {
        if (_engine.value == EqEngine.PRECISE) { if (!Prefs.eqPreciseBands.contentEquals(pendingBands)) Prefs.eqPreciseBands = pendingBands.copyOf() }
        else if (!Prefs.eqBands.contentEquals(pendingBands)) Prefs.eqBands = pendingBands.copyOf()
    }
    fun bandProgress(index: Int): Float {
        if (index !in pendingBands.indices) return 0.5f
        val r = bandLevelRange; return ((pendingBands[index] - r[0]).toFloat() / (r[1] - r[0])).coerceIn(0f, 1f)
    }
    fun setBass(progress: Float) {
        if (!progress.isFinite()) return
        val value = (progress.coerceIn(0f, 1f) * 1000).toInt()
        if (value == pendingBass) return
        pendingBass = value; applyBass(); onProcessingChanged?.invoke()
    }
    fun commitBass() { if (pendingBass != _bass.value) { _bass.value = pendingBass; Prefs.eqBass = pendingBass; AppLog.i("EqualizerHost", "commit-bass count=${++commitCount} strength=$pendingBass") } }
    fun processingSnapshot(): EqChain.ProcessingSnapshot {
        val filters = (activeFilters ?: if (pendingBands.size == currentFrequencies().size) EqChain.graphicFilters(pendingBands, currentFrequencies(), if (_engine.value == EqEngine.PLATFORM) 0.96 else kotlin.math.sqrt(2.0)) else emptyList()).toMutableList()
        if (_engine.value == EqEngine.PRECISE && pendingBass > 0) filters += Biquad.Filter(Biquad.Type.LSC, 120.0, pendingBass / 1000.0 * 9.0, 0.707)
        return EqChain.ProcessingSnapshot(_enabled.value && _engine.value == EqEngine.PRECISE && _mounted.value, filters.toList(), preamp)
    }
    /** Response view excludes automatic preamp for platform audiofx. */
    fun curveSnapshot(): EqChain.ProcessingSnapshot = processingSnapshot().let {
        if (_engine.value == EqEngine.PLATFORM) it.copy(preampDb = 0.0) else it
    }
    fun importPreset(name: String, text: String, source: String = "import"): EqTextCodec.ParseResult {
        val result = EqTextCodec.parse(text)
        val doc = result.document ?: return result
        val p = EqPreset(uniqueName(name), doc.gainsDb.map { (it * 100).toInt() }.toIntArray(), false,
            frequenciesHz = doc.frequenciesHz, preampDb = doc.preampDb, filters = doc.filters, source = source, engine = "PRECISE", graphicResponse = doc.filters.isEmpty())
        saveStore(_presets.value + p)
        // Preserve source nodes, then separately fit bounded engine controls with an error estimate.
        if (canSelectPreset(p)) selectPreset(p.name)
        return result
    }
    fun exportPreset(name: String? = _selected.value): String? {
        val p = _presets.value.firstOrNull { it.name == name } ?: EqPreset("手动", pendingBands.copyOf(), false,
            frequenciesHz = currentFrequencies(), preampDb = preamp, filters = activeFilters.orEmpty(), engine = _engine.value.name)
        if (p.filters.isEmpty() && (p.frequenciesHz.isEmpty() || p.frequenciesHz.size != p.gains.size)) return null
        // GraphicEQ nodes describe a target response, not individual biquad gains. Export native
        // graphic controls as APO PK filters so a precise import preserves their actual cascade.
        val filters = if (p.filters.isNotEmpty() || p.graphicResponse) p.filters
            else EqChain.graphicFilters(p.gains, p.frequenciesHz, if (p.engine == "PLATFORM") 0.96 else kotlin.math.sqrt(2.0))
        return EqTextCodec.export(EqTextCodec.Document(p.preampDb, filters, p.frequenciesHz, p.gains.map { it / 100.0 }))
    }
}
