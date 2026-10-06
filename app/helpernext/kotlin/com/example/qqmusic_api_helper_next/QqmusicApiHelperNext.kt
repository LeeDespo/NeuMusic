@file:OptIn(kotlin.ExperimentalUnsignedTypes::class)

package com.example.qqmusic_api_helper_next

private object Utf8Codec {
    fun maxBytes(value: String): Int = value.length * 3
}

private val <First, Second> Pair<First, Second>.field0: First get() = first
private val <First, Second> Pair<First, Second>.field1: Second get() = second
private val <First, Second, Third> Triple<First, Second, Third>.field0: First get() = first
private val <First, Second, Third> Triple<First, Second, Third>.field1: Second get() = second
private val <First, Second, Third> Triple<First, Second, Third>.field2: Third get() = third

class FfiException(message: String) : RuntimeException(message)

internal class BoltFfiErrorBufferException(val bytes: ByteArray) : RuntimeException("BoltFFI call failed")

private object DirectVectorCodec {
    fun readBooleanArray(bytes: ByteArray): BooleanArray =
        BooleanArray(bytes.size) { index -> bytes[index] != 0.toByte() }

    fun readByteArray(bytes: ByteArray): ByteArray = bytes

    fun writeBooleanArray(values: BooleanArray): ByteArray =
        ByteArray(values.size) { index -> if (values[index]) 1.toByte() else 0.toByte() }

    fun writeByteArray(values: ByteArray): ByteArray = values

    fun <T> readRecordList(
        bytes: ByteArray,
        width: Int,
        read: (java.nio.ByteBuffer, Int) -> T
    ): List<T> {
        val count = elementCount(bytes, width)
        val buffer = nativeBuffer(bytes)
        return List(count) { index -> read(buffer, index * width) }
    }

    fun <T> writeRecordList(
        values: List<T>,
        width: Int,
        write: (T, java.nio.ByteBuffer, Int) -> Unit
    ): ByteArray {
        val bytes = ByteArray(values.size * width)
        val buffer = nativeBuffer(bytes)
        values.forEachIndexed { index, value -> write(value, buffer, index * width) }
        return bytes
    }

    fun readShortArray(bytes: ByteArray): ShortArray {
        val values = ShortArray(elementCount(bytes, 2))
        nativeBuffer(bytes).asShortBuffer().get(values)
        return values
    }

    fun readUShortArray(bytes: ByteArray): UShortArray =
        readShortArray(bytes).toUShortArray()

    fun writeShortArray(values: ShortArray): ByteArray {
        val bytes = ByteArray(values.size * 2)
        nativeBuffer(bytes).asShortBuffer().put(values)
        return bytes
    }

    fun writeUShortArray(values: UShortArray): ByteArray =
        writeShortArray(values.asShortArray())

    fun readIntArray(bytes: ByteArray): IntArray {
        val values = IntArray(elementCount(bytes, 4))
        nativeBuffer(bytes).asIntBuffer().get(values)
        return values
    }

    fun readUIntArray(bytes: ByteArray): UIntArray =
        readIntArray(bytes).toUIntArray()

    fun writeIntArray(values: IntArray): ByteArray {
        val bytes = ByteArray(values.size * 4)
        nativeBuffer(bytes).asIntBuffer().put(values)
        return bytes
    }

    fun writeUIntArray(values: UIntArray): ByteArray =
        writeIntArray(values.asIntArray())

    fun readLongArray(bytes: ByteArray): LongArray {
        val values = LongArray(elementCount(bytes, 8))
        nativeBuffer(bytes).asLongBuffer().get(values)
        return values
    }

    fun readULongArray(bytes: ByteArray): ULongArray =
        readLongArray(bytes).toULongArray()

    fun writeLongArray(values: LongArray): ByteArray {
        val bytes = ByteArray(values.size * 8)
        nativeBuffer(bytes).asLongBuffer().put(values)
        return bytes
    }

    fun writeULongArray(values: ULongArray): ByteArray =
        writeLongArray(values.asLongArray())

    fun readFloatArray(bytes: ByteArray): FloatArray {
        val values = FloatArray(elementCount(bytes, 4))
        nativeBuffer(bytes).asFloatBuffer().get(values)
        return values
    }

    fun writeFloatArray(values: FloatArray): ByteArray {
        val bytes = ByteArray(values.size * 4)
        nativeBuffer(bytes).asFloatBuffer().put(values)
        return bytes
    }

    fun readDoubleArray(bytes: ByteArray): DoubleArray {
        val values = DoubleArray(elementCount(bytes, 8))
        nativeBuffer(bytes).asDoubleBuffer().get(values)
        return values
    }

    fun writeDoubleArray(values: DoubleArray): ByteArray {
        val bytes = ByteArray(values.size * 8)
        nativeBuffer(bytes).asDoubleBuffer().put(values)
        return bytes
    }

    private fun nativeBuffer(bytes: ByteArray): java.nio.ByteBuffer =
        java.nio.ByteBuffer
            .wrap(bytes)
            .order(java.nio.ByteOrder.nativeOrder())

    private fun elementCount(bytes: ByteArray, width: Int): Int {
        require(bytes.size % width == 0)
        return bytes.size / width
    }
}

internal class WireReader(private val bytes: ByteArray) {
    private var position = 0

    fun skip(count: Int): WireReader {
        if (count > bytes.size - position) {
            throw IndexOutOfBoundsException("Wire read past the end of the payload")
        }
        position += count
        return this
    }

    fun readBool(): Boolean = readI8() != 0.toByte()

    fun readI8(): Byte {
        val value = bytes[position]
        position += 1
        return value
    }

    fun readU8(): UByte = readI8().toUByte()

    fun readI16(): Short {
        val value =
            (bytes[position].toInt() and 0xff) or
                ((bytes[position + 1].toInt() and 0xff) shl 8)
        position += 2
        return value.toShort()
    }

    fun readU16(): UShort = readI16().toUShort()

    fun readI32(): Int {
        val value =
            (bytes[position].toInt() and 0xff) or
                ((bytes[position + 1].toInt() and 0xff) shl 8) or
                ((bytes[position + 2].toInt() and 0xff) shl 16) or
                ((bytes[position + 3].toInt() and 0xff) shl 24)
        position += 4
        return value
    }

    fun readU32(): UInt = readI32().toUInt()

    fun readI64(): Long {
        val low = readI32().toLong() and 0xffffffffL
        val high = readI32().toLong() and 0xffffffffL
        return low or (high shl 32)
    }

    fun readU64(): ULong = readI64().toULong()

    fun readF32(): Float = java.lang.Float.intBitsToFloat(readI32())

    fun readF64(): Double = java.lang.Double.longBitsToDouble(readI64())

    fun readOptionalBool(): Boolean? = readOptional { it.readBool() }

    fun readOptionalI8(): Byte? = readOptional { it.readI8() }

    fun readOptionalU8(): UByte? = readOptional { it.readU8() }

    fun readOptionalI16(): Short? = readOptional { it.readI16() }

    fun readOptionalU16(): UShort? = readOptional { it.readU16() }

    fun readOptionalI32(): Int? = readOptional { it.readI32() }

    fun readOptionalU32(): UInt? = readOptional { it.readU32() }

    fun readOptionalI64(): Long? = readOptional { it.readI64() }

    fun readOptionalU64(): ULong? = readOptional { it.readU64() }

    fun readOptionalF32(): Float? = readOptional { it.readF32() }

    fun readOptionalF64(): Double? = readOptional { it.readF64() }

    fun readString(): String {
        val length = readU32().toInt()
        val value = String(bytes, position, length, Charsets.UTF_8)
        position += length
        return value
    }

    fun readBytes(): ByteArray {
        val length = readU32().toInt()
        val value = bytes.copyOfRange(position, position + length)
        position += length
        return value
    }

    fun readBooleanArray(): BooleanArray {
        val length = readU32().toInt()
        return BooleanArray(length) { readBool() }
    }

    fun readByteArray(): ByteArray = readBytes()

    fun readShortArray(): ShortArray {
        val length = readU32().toInt()
        val byteCount = length * 2
        val values = ShortArray(length)
        java.nio.ByteBuffer
            .wrap(bytes, position, byteCount)
            .order(java.nio.ByteOrder.LITTLE_ENDIAN)
            .asShortBuffer()
            .get(values)
        position += byteCount
        return values
    }

    fun readUShortArray(): UShortArray =
        readShortArray().toUShortArray()

    fun readIntArray(): IntArray {
        val length = readU32().toInt()
        val byteCount = length * 4
        val values = IntArray(length)
        java.nio.ByteBuffer
            .wrap(bytes, position, byteCount)
            .order(java.nio.ByteOrder.LITTLE_ENDIAN)
            .asIntBuffer()
            .get(values)
        position += byteCount
        return values
    }

    fun readUIntArray(): UIntArray =
        readIntArray().toUIntArray()

    fun readLongArray(): LongArray {
        val length = readU32().toInt()
        val byteCount = length * 8
        val values = LongArray(length)
        java.nio.ByteBuffer
            .wrap(bytes, position, byteCount)
            .order(java.nio.ByteOrder.LITTLE_ENDIAN)
            .asLongBuffer()
            .get(values)
        position += byteCount
        return values
    }

    fun readULongArray(): ULongArray =
        readLongArray().toULongArray()

    fun readFloatArray(): FloatArray {
        val length = readU32().toInt()
        val byteCount = length * 4
        val values = FloatArray(length)
        java.nio.ByteBuffer
            .wrap(bytes, position, byteCount)
            .order(java.nio.ByteOrder.LITTLE_ENDIAN)
            .asFloatBuffer()
            .get(values)
        position += byteCount
        return values
    }

    fun readDoubleArray(): DoubleArray {
        val length = readU32().toInt()
        val byteCount = length * 8
        val values = DoubleArray(length)
        java.nio.ByteBuffer
            .wrap(bytes, position, byteCount)
            .order(java.nio.ByteOrder.LITTLE_ENDIAN)
            .asDoubleBuffer()
            .get(values)
        position += byteCount
        return values
    }

    fun <T> readOptionalValue(read: (WireReader) -> T): T? = readOptional(read)

    fun <T> readSequence(read: (WireReader) -> T): List<T> {
        val length = readU32().toInt()
        return List(length) { read(this) }
    }

    fun <K, V> readMap(readKey: (WireReader) -> K, readValue: (WireReader) -> V): Map<K, V> {
        val length = readU32().toInt()
        val values = LinkedHashMap<K, V>(length)
        repeat(length) {
            val key = readKey(this)
            if (values.containsKey(key)) {
                throw IllegalArgumentException("duplicate map key")
            }
            values[key] = readValue(this)
        }
        return values
    }

    private inline fun <T> readOptional(read: (WireReader) -> T): T? {
        return when (readU8()) {
            0.toUByte() -> null
            1.toUByte() -> read(this)
            else -> throw IllegalArgumentException("invalid optional wire tag")
        }
    }
}

internal class WireWriter(initialCapacity: Int) {
    private var buffer = java.nio.ByteBuffer
        .allocateDirect(initialCapacity)
        .order(java.nio.ByteOrder.LITTLE_ENDIAN)
    private var position = 0

    fun reset(requiredCapacity: Int) {
        if (buffer.capacity() < requiredCapacity) {
            buffer = java.nio.ByteBuffer
                .allocateDirect(requiredCapacity)
                .order(java.nio.ByteOrder.LITTLE_ENDIAN)
        }
        position = 0
    }

    fun toByteArray(): ByteArray {
        val bytes = ByteArray(position)
        val view = buffer.duplicate()
        view.position(0)
        view.get(bytes, 0, position)
        return bytes
    }

    fun directBuffer(): java.nio.ByteBuffer = buffer

    fun size(): Int = position

    fun writeBool(value: Boolean) {
        ensureCapacity(1)
        buffer.put(position, if (value) 1.toByte() else 0.toByte())
        position += 1
    }

    fun writeI8(value: Byte) {
        ensureCapacity(1)
        buffer.put(position, value)
        position += 1
    }

    fun writeU8(value: UByte) {
        writeI8(value.toByte())
    }

    fun writeI16(value: Short) {
        ensureCapacity(2)
        buffer.putShort(position, value)
        position += 2
    }

    fun writeU16(value: UShort) {
        writeI16(value.toShort())
    }

    fun writeI32(value: Int) {
        ensureCapacity(4)
        buffer.putInt(position, value)
        position += 4
    }

    fun writeU32(value: UInt) {
        writeI32(value.toInt())
    }

    fun writeI64(value: Long) {
        ensureCapacity(8)
        buffer.putLong(position, value)
        position += 8
    }

    fun writeU64(value: ULong) {
        writeI64(value.toLong())
    }

    fun writeF32(value: Float) {
        writeI32(java.lang.Float.floatToRawIntBits(value))
    }

    fun writeF64(value: Double) {
        writeI64(java.lang.Double.doubleToRawLongBits(value))
    }

    fun writeOptionalBool(value: Boolean?) = writeOptional(value) { writer, present ->
        writer.writeBool(present)
    }

    fun writeOptionalI8(value: Byte?) = writeOptional(value) { writer, present ->
        writer.writeI8(present)
    }

    fun writeOptionalU8(value: UByte?) = writeOptional(value) { writer, present ->
        writer.writeU8(present)
    }

    fun writeOptionalI16(value: Short?) = writeOptional(value) { writer, present ->
        writer.writeI16(present)
    }

    fun writeOptionalU16(value: UShort?) = writeOptional(value) { writer, present ->
        writer.writeU16(present)
    }

    fun writeOptionalI32(value: Int?) = writeOptional(value) { writer, present ->
        writer.writeI32(present)
    }

    fun writeOptionalU32(value: UInt?) = writeOptional(value) { writer, present ->
        writer.writeU32(present)
    }

    fun writeOptionalI64(value: Long?) = writeOptional(value) { writer, present ->
        writer.writeI64(present)
    }

    fun writeOptionalU64(value: ULong?) = writeOptional(value) { writer, present ->
        writer.writeU64(present)
    }

    fun writeOptionalF32(value: Float?) = writeOptional(value) { writer, present ->
        writer.writeF32(present)
    }

    fun writeOptionalF64(value: Double?) = writeOptional(value) { writer, present ->
        writer.writeF64(present)
    }

    fun writeString(value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        writeU32(bytes.size.toUInt())
        writeBytesRaw(bytes)
    }

    fun writeBytes(value: ByteArray) {
        writeU32(value.size.toUInt())
        writeBytesRaw(value)
    }

    fun pad(count: Int): WireWriter {
        repeat(count) { writeI8(0) }
        return this
    }

    fun writeBooleanArray(values: BooleanArray) {
        writeU32(values.size.toUInt())
        values.forEach { writeBool(it) }
    }

    fun writeByteArray(values: ByteArray) = writeBytes(values)

    fun writeShortArray(values: ShortArray) {
        writeU32(values.size.toUInt())
        val byteCount = values.size * 2
        ensureCapacity(byteCount)
        val view = buffer.duplicate().order(java.nio.ByteOrder.LITTLE_ENDIAN)
        view.position(position)
        view.asShortBuffer().put(values)
        position += byteCount
    }

    fun writeUShortArray(values: UShortArray) =
        writeShortArray(values.asShortArray())

    fun writeIntArray(values: IntArray) {
        writeU32(values.size.toUInt())
        val byteCount = values.size * 4
        ensureCapacity(byteCount)
        val view = buffer.duplicate().order(java.nio.ByteOrder.LITTLE_ENDIAN)
        view.position(position)
        view.asIntBuffer().put(values)
        position += byteCount
    }

    fun writeUIntArray(values: UIntArray) =
        writeIntArray(values.asIntArray())

    fun writeLongArray(values: LongArray) {
        writeU32(values.size.toUInt())
        val byteCount = values.size * 8
        ensureCapacity(byteCount)
        val view = buffer.duplicate().order(java.nio.ByteOrder.LITTLE_ENDIAN)
        view.position(position)
        view.asLongBuffer().put(values)
        position += byteCount
    }

    fun writeULongArray(values: ULongArray) =
        writeLongArray(values.asLongArray())

    fun writeFloatArray(values: FloatArray) {
        writeU32(values.size.toUInt())
        val byteCount = values.size * 4
        ensureCapacity(byteCount)
        val view = buffer.duplicate().order(java.nio.ByteOrder.LITTLE_ENDIAN)
        view.position(position)
        view.asFloatBuffer().put(values)
        position += byteCount
    }

    fun writeDoubleArray(values: DoubleArray) {
        writeU32(values.size.toUInt())
        val byteCount = values.size * 8
        ensureCapacity(byteCount)
        val view = buffer.duplicate().order(java.nio.ByteOrder.LITTLE_ENDIAN)
        view.position(position)
        view.asDoubleBuffer().put(values)
        position += byteCount
    }

    fun <T> writeOptionalValue(value: T?, write: (WireWriter, T) -> Unit) {
        writeOptional(value, write)
    }

    fun <T> writeSequence(value: Iterable<T>, count: Int, write: (WireWriter, T) -> Unit) {
        writeU32(count.toUInt())
        value.forEach { item -> write(this, item) }
    }

    fun <K, V> writeMap(
        value: Map<K, V>,
        writeKey: (WireWriter, K) -> Unit,
        writeValue: (WireWriter, V) -> Unit,
    ) {
        writeU32(value.size.toUInt())
        value.entries.forEach { entry ->
            writeKey(this, entry.key)
            writeValue(this, entry.value)
        }
    }

    private fun writeBytesRaw(bytes: ByteArray) {
        ensureCapacity(bytes.size)
        val view = buffer.duplicate().order(java.nio.ByteOrder.LITTLE_ENDIAN)
        view.position(position)
        view.put(bytes)
        position += bytes.size
    }

    private fun ensureCapacity(needed: Int) {
        val required = position + needed
        if (required <= buffer.capacity()) {
            return
        }
        val nextCapacity = maxOf(buffer.capacity() * 2, required)
        val next = java.nio.ByteBuffer
            .allocateDirect(nextCapacity)
            .order(java.nio.ByteOrder.LITTLE_ENDIAN)
        val source = buffer.duplicate().order(java.nio.ByteOrder.LITTLE_ENDIAN)
        source.limit(position)
        source.position(0)
        next.put(source)
        buffer = next
    }

    private inline fun <T> writeOptional(value: T?, write: (WireWriter, T) -> Unit) {
        if (value == null) {
            writeU8(0.toUByte())
            return
        }
        writeU8(1.toUByte())
        write(this, value)
    }
}

private const val MAX_CACHED_WIRE_WRITER_BYTES: Int = 1024 * 1024

private class WireWriterPoolState(private val cacheSize: Int = 4) {
    private val cachedWriters: Array<WireWriter?> = arrayOfNulls(cacheSize)
    private var depth = 0

    fun acquire(requiredCapacity: Int): BorrowedWireWriter {
        val slot = depth
        depth = slot + 1
        val shouldCache = requiredCapacity <= MAX_CACHED_WIRE_WRITER_BYTES && slot < cacheSize
        val writer = if (shouldCache) {
            cachedWriters[slot] ?: WireWriter(requiredCapacity).also { cachedWriters[slot] = it }
        } else {
            WireWriter(requiredCapacity)
        }

        writer.reset(requiredCapacity)
        return BorrowedWireWriter(this, writer)
    }

    fun release() {
        depth -= 1
    }
}

private class BorrowedWireWriter(
    private val state: WireWriterPoolState,
    val writer: WireWriter,
) : AutoCloseable {
    fun bytes(): ByteArray = writer.toByteArray()

    fun directBuffer(): java.nio.ByteBuffer = writer.directBuffer()

    fun size(): Int = writer.size()

    override fun close() {
        state.release()
    }
}

private object WireWriterPool {
    private val state: ThreadLocal<WireWriterPoolState> =
        ThreadLocal.withInitial { WireWriterPoolState() }

    fun acquire(requiredCapacity: Int): BorrowedWireWriter {
        val poolState = state.get() ?: WireWriterPoolState().also { state.set(it) }
        return poolState.acquire(requiredCapacity)
    }
}

private inline fun <K, V> Map<K, V>.wireSize(
    keySize: (K) -> Int,
    valueSize: (V) -> Int,
): Int = 4 + entries.sumOf { entry -> keySize(entry.key) + valueSize(entry.value) }

@Suppress("FunctionName")
private object Native {
    fun ensureInitialized() {}
    init {
        val androidLibrary = "qqmusic_api_helper_next"
        val desktopPreferredLibrary = "qqmusic_api_helper_next_jni"
        val desktopFallbackLibrary = "qqmusic_api_helper_next"
        val vmName = System.getProperty("java.vm.name").orEmpty()
        val isAndroidRuntime =
            vmName.contains("dalvik", ignoreCase = true) ||
            vmName.contains("art", ignoreCase = true)
        if (isAndroidRuntime) {
            System.loadLibrary(androidLibrary)
        } else {
            loadDesktopLibraries(desktopPreferredLibrary, desktopFallbackLibrary)
        }
    }

    @Volatile
    private var bundledLibraryDirectory: java.io.File? = null

    private fun loadDesktopLibraries(preferredLibrary: String, fallbackLibrary: String) {
        var preferredFailure = tryLoadDesktopLibrary(preferredLibrary)
        if (preferredFailure == null) {
            return
        }

        if (tryLoadOptionalDesktopLibrary(fallbackLibrary)) {
            preferredFailure = tryLoadDesktopLibrary(preferredLibrary)
            if (preferredFailure == null) {
                return
            }
        }

        throw preferredFailure
    }

    private fun tryLoadDesktopLibrary(libraryName: String): UnsatisfiedLinkError? {
        try {
            if (loadBundledLibraryIfPresent(libraryName) || loadExternalLibraryIfPresent(libraryName)) {
                return null
            }
            return UnsatisfiedLinkError("Could not load native library '$libraryName'")
        } catch (error: UnsatisfiedLinkError) {
            return error
        }
    }

    private fun tryLoadOptionalDesktopLibrary(libraryName: String): Boolean {
        return try {
            loadBundledLibraryIfPresent(libraryName) || loadExternalLibraryIfPresent(libraryName)
        } catch (_: UnsatisfiedLinkError) {
            false
        }
    }

    private fun loadExternalLibraryIfPresent(libraryName: String): Boolean {
        return try {
            System.loadLibrary(libraryName)
            true
        } catch (_: UnsatisfiedLinkError) {
            false
        }
    }

    private fun loadBundledLibraryIfPresent(libraryName: String): Boolean {
        val mappedName = System.mapLibraryName(libraryName)
        for (resourcePath in bundledLibraryResourceCandidates(mappedName)) {
            Native::class.java.getResourceAsStream(resourcePath)?.use { input ->
                val extracted = extractBundledLibrary(resourcePath, input)
                System.load(extracted.absolutePath)
                return true
            }
        }
        return false
    }

    private fun extractBundledLibrary(
        resourcePath: String,
        input: java.io.InputStream,
    ): java.io.File {
        val fileName = resourcePath.substringAfterLast('/')
        val extracted = java.io.File(bundledLibraryDirectory(), fileName)
        if (!extracted.isFile) {
            java.io.FileOutputStream(extracted).use { output ->
                input.copyTo(output)
            }
            extracted.deleteOnExit()
        }
        return extracted
    }

    private fun bundledLibraryDirectory(): java.io.File {
        bundledLibraryDirectory?.let { return it }
        synchronized(this) {
            bundledLibraryDirectory?.let { return it }
            val created = java.io.File.createTempFile("boltffi-native-", "")
            if (!created.delete() || !created.mkdir()) {
                throw java.io.IOException("failed to create temp directory for bundled native extraction")
            }
            created.deleteOnExit()
            bundledLibraryDirectory = created
            return created
        }
    }

    private fun bundledLibraryResourceCandidates(mappedName: String): List<String> {
        val candidates = mutableListOf<String>()
        for (directory in desktopNativeDirectories()) {
            candidates += "/$directory/$mappedName"
            candidates += "/native/$directory/$mappedName"
        }
        candidates += "/$mappedName"
        return candidates
    }

    private fun desktopNativeDirectories(): List<String> {
        val osName = System.getProperty("os.name").orEmpty().lowercase()
        val osArch = System.getProperty("os.arch").orEmpty().lowercase()
        return when {
            (osName.contains("mac") || osName.contains("darwin")) &&
                (osArch == "aarch64" || osArch == "arm64") ->
                listOf("darwin-arm64", "darwin-aarch64")
            (osName.contains("mac") || osName.contains("darwin")) &&
                (osArch == "x86_64" || osArch == "amd64") ->
                listOf("darwin-x86_64", "darwin-x86-64")
            (osName.contains("linux")) &&
                (osArch == "x86_64" || osArch == "amd64") ->
                listOf("linux-x86_64", "linux-x86-64")
            (osName.contains("linux")) &&
                (osArch == "aarch64" || osArch == "arm64") ->
                listOf("linux-aarch64", "linux-arm64")
            (osName.contains("windows")) &&
                (osArch == "x86_64" || osArch == "amd64") ->
                listOf("windows-x86_64", "windows-x86-64", "win32-x86_64")
            (osName.contains("windows")) &&
                (osArch == "aarch64" || osArch == "arm64") ->
                listOf("windows-aarch64", "windows-arm64", "win32-arm64")
            else -> emptyList()
        }
    }
    @JvmStatic external fun boltffi_function_qqmusic_api_helper_next_port_collection_write_create_playlist(dirname: java.nio.ByteBuffer, __boltffi_dirname_len: Int): ByteArray?
    @JvmStatic external fun boltffi_function_qqmusic_api_helper_next_port_collection_write_delete_playlist(dirid: Long): ByteArray?
    @JvmStatic external fun boltffi_function_qqmusic_api_helper_next_port_collection_write_add_playlist_songs(dirid: Long, song_info: ByteArray, tid: java.nio.ByteBuffer, __boltffi_tid_len: Int): Boolean
    @JvmStatic external fun boltffi_function_qqmusic_api_helper_next_port_collection_write_remove_playlist_songs(dirid: Long, song_info: ByteArray, tid: java.nio.ByteBuffer, __boltffi_tid_len: Int): Boolean
    @JvmStatic external fun boltffi_function_qqmusic_api_helper_next_port_collection_write_fav_album(album_ids: LongArray): ByteArray?
    @JvmStatic external fun boltffi_function_qqmusic_api_helper_next_port_collection_write_unfav_album(album_ids: LongArray): ByteArray?
    @JvmStatic external fun boltffi_function_qqmusic_api_helper_next_port_collection_write_fav_playlist(playlist_id: Long): Boolean
    @JvmStatic external fun boltffi_function_qqmusic_api_helper_next_port_collection_write_unfav_playlist(playlist_id: Long): Boolean
    @JvmStatic external fun boltffi_function_qqmusic_api_helper_next_port_comment_fetch_comment_count(biz_id: Long, biz_type: java.nio.ByteBuffer, __boltffi_biz_type_len: Int, biz_sub_type: java.nio.ByteBuffer, __boltffi_biz_sub_type_len: Int): ByteArray?
    @JvmStatic external fun boltffi_function_qqmusic_api_helper_next_port_comment_fetch_hot_comments(biz_id: Long, page: java.nio.ByteBuffer, __boltffi_page_len: Int, page_size: java.nio.ByteBuffer, __boltffi_page_size_len: Int, last_comment_seq_no: java.nio.ByteBuffer, __boltffi_last_comment_seq_no_len: Int, biz_type: java.nio.ByteBuffer, __boltffi_biz_type_len: Int, biz_sub_type: java.nio.ByteBuffer, __boltffi_biz_sub_type_len: Int): ByteArray?
    @JvmStatic external fun boltffi_function_qqmusic_api_helper_next_port_comment_fetch_new_comments(biz_id: Long, page: java.nio.ByteBuffer, __boltffi_page_len: Int, page_size: java.nio.ByteBuffer, __boltffi_page_size_len: Int, last_comment_seq_no: java.nio.ByteBuffer, __boltffi_last_comment_seq_no_len: Int, biz_type: java.nio.ByteBuffer, __boltffi_biz_type_len: Int, biz_sub_type: java.nio.ByteBuffer, __boltffi_biz_sub_type_len: Int): ByteArray?
    @JvmStatic external fun boltffi_function_qqmusic_api_helper_next_port_comment_fetch_recommend_comments(biz_id: Long, page: java.nio.ByteBuffer, __boltffi_page_len: Int, page_size: java.nio.ByteBuffer, __boltffi_page_size_len: Int, last_comment_seq_no: java.nio.ByteBuffer, __boltffi_last_comment_seq_no_len: Int, biz_type: java.nio.ByteBuffer, __boltffi_biz_type_len: Int, biz_sub_type: java.nio.ByteBuffer, __boltffi_biz_sub_type_len: Int): ByteArray?
    @JvmStatic external fun boltffi_function_qqmusic_api_helper_next_port_comment_fetch_moment_comments(biz_id: Long, page_size: java.nio.ByteBuffer, __boltffi_page_size_len: Int, last_comment_seq_no: java.nio.ByteBuffer, __boltffi_last_comment_seq_no_len: Int, biz_type: java.nio.ByteBuffer, __boltffi_biz_type_len: Int, biz_sub_type: java.nio.ByteBuffer, __boltffi_biz_sub_type_len: Int): ByteArray?
    @JvmStatic external fun boltffi_function_qqmusic_api_helper_next_port_comment_add_comment(biz_id: Long, content: java.nio.ByteBuffer, __boltffi_content_len: Int, reply_cmt_id: java.nio.ByteBuffer, __boltffi_reply_cmt_id_len: Int, biz_type: java.nio.ByteBuffer, __boltffi_biz_type_len: Int, biz_sub_type: java.nio.ByteBuffer, __boltffi_biz_sub_type_len: Int): ByteArray?
    @JvmStatic external fun boltffi_function_qqmusic_api_helper_next_port_comment_delete_comment(cm_id: java.nio.ByteBuffer, __boltffi_cm_id_len: Int): Boolean
    @JvmStatic external fun boltffi_function_qqmusic_api_helper_next_port_library_extra_fetch_new_albums(area: java.nio.ByteBuffer, __boltffi_area_len: Int, num: java.nio.ByteBuffer, __boltffi_num_len: Int, page: java.nio.ByteBuffer, __boltffi_page_len: Int): ByteArray?
    @JvmStatic external fun boltffi_function_qqmusic_api_helper_next_port_library_extra_fetch_singing_annotations(song_id: Long): ByteArray?
    @JvmStatic external fun boltffi_function_qqmusic_api_helper_next_port_library_extra_fetch_multi_style_lyrics(song_id: Long): ByteArray?
    @JvmStatic external fun boltffi_function_qqmusic_api_helper_next_port_library_extra_has_ai_dictionary(song_id: Long): ByteArray?
    @JvmStatic external fun boltffi_function_qqmusic_api_helper_next_port_library_extra_fetch_ai_dictionary(song_id: Long): ByteArray?
    @JvmStatic external fun boltffi_function_qqmusic_api_helper_next_port_library_extra_fetch_user_liked_songs(euin: java.nio.ByteBuffer, __boltffi_euin_len: Int, page: java.nio.ByteBuffer, __boltffi_page_len: Int, num: java.nio.ByteBuffer, __boltffi_num_len: Int): ByteArray?
    @JvmStatic external fun boltffi_function_qqmusic_api_helper_next_port_login_extra_fetch_wx_qrcode(): ByteArray?
    @JvmStatic external fun boltffi_function_qqmusic_api_helper_next_port_login_extra_check_wx_qrcode(identifier: java.nio.ByteBuffer, __boltffi_identifier_len: Int): ByteArray?
    @JvmStatic external fun boltffi_function_qqmusic_api_helper_next_port_login_extra_send_phone_authcode(phone: java.nio.ByteBuffer, __boltffi_phone_len: Int, encrypted_phone: java.nio.ByteBuffer, __boltffi_encrypted_phone_len: Int, country_code: java.nio.ByteBuffer, __boltffi_country_code_len: Int): ByteArray?
    @JvmStatic external fun boltffi_function_qqmusic_api_helper_next_port_login_extra_phone_login(phone: java.nio.ByteBuffer, __boltffi_phone_len: Int, encrypted_phone: java.nio.ByteBuffer, __boltffi_encrypted_phone_len: Int, auth_code: java.nio.ByteBuffer, __boltffi_auth_code_len: Int): ByteArray?
    @JvmStatic external fun boltffi_function_qqmusic_api_helper_next_port_login_extra_refresh_credential(): ByteArray?
    @JvmStatic external fun boltffi_function_qqmusic_api_helper_next_port_mv_fetch_mv_detail(vids: java.nio.ByteBuffer, __boltffi_vids_len: Int): ByteArray?
    @JvmStatic external fun boltffi_function_qqmusic_api_helper_next_port_mv_resolve_mv_urls(vids: java.nio.ByteBuffer, __boltffi_vids_len: Int): ByteArray?
    @JvmStatic external fun boltffi_function_qqmusic_api_helper_next_port_mv_fetch_mv_list(area: java.nio.ByteBuffer, __boltffi_area_len: Int, version: java.nio.ByteBuffer, __boltffi_version_len: Int, order: java.nio.ByteBuffer, __boltffi_order_len: Int, num: java.nio.ByteBuffer, __boltffi_num_len: Int, page: java.nio.ByteBuffer, __boltffi_page_len: Int): ByteArray?
    @JvmStatic external fun boltffi_function_qqmusic_api_helper_next_port_recommend_extra_fetch_home_feed(page: java.nio.ByteBuffer, __boltffi_page_len: Int, direction: java.nio.ByteBuffer, __boltffi_direction_len: Int, s_num: java.nio.ByteBuffer, __boltffi_s_num_len: Int, v_cache: java.nio.ByteBuffer, __boltffi_v_cache_len: Int): ByteArray?
    @JvmStatic external fun boltffi_function_qqmusic_api_helper_next_port_recommend_extra_fetch_radar_recommend(page: java.nio.ByteBuffer, __boltffi_page_len: Int): ByteArray?
    @JvmStatic external fun boltffi_function_qqmusic_api_helper_next_port_recommend_extra_fetch_recommend_playlists(page: java.nio.ByteBuffer, __boltffi_page_len: Int, num: java.nio.ByteBuffer, __boltffi_num_len: Int): ByteArray?
    @JvmStatic external fun boltffi_function_qqmusic_api_helper_next_port_search_extra_fetch_search_hotkeys(): ByteArray?
    @JvmStatic external fun boltffi_function_qqmusic_api_helper_next_port_search_extra_complete_search(keyword: java.nio.ByteBuffer, __boltffi_keyword_len: Int): ByteArray?
    @JvmStatic external fun boltffi_function_qqmusic_api_helper_next_port_search_extra_quick_search(keyword: java.nio.ByteBuffer, __boltffi_keyword_len: Int): ByteArray?
    @JvmStatic external fun boltffi_function_qqmusic_api_helper_next_port_search_extra_general_search(keyword: java.nio.ByteBuffer, __boltffi_keyword_len: Int, page: java.nio.ByteBuffer, __boltffi_page_len: Int, num: java.nio.ByteBuffer, __boltffi_num_len: Int, searchid: java.nio.ByteBuffer, __boltffi_searchid_len: Int, page_start: java.nio.ByteBuffer, __boltffi_page_start_len: Int, highlight: java.nio.ByteBuffer, __boltffi_highlight_len: Int): ByteArray?
    @JvmStatic external fun boltffi_function_qqmusic_api_helper_next_port_search_extra_search_extra(keyword: java.nio.ByteBuffer, __boltffi_keyword_len: Int, search_type: java.nio.ByteBuffer, __boltffi_search_type_len: Int, page: java.nio.ByteBuffer, __boltffi_page_len: Int, num: java.nio.ByteBuffer, __boltffi_num_len: Int, searchid: java.nio.ByteBuffer, __boltffi_searchid_len: Int, selectors: java.nio.ByteBuffer, __boltffi_selectors_len: Int, highlight: java.nio.ByteBuffer, __boltffi_highlight_len: Int): ByteArray?
    @JvmStatic external fun boltffi_function_qqmusic_api_helper_next_port_singer_extra_fetch_singer_list(area: java.nio.ByteBuffer, __boltffi_area_len: Int, sex: java.nio.ByteBuffer, __boltffi_sex_len: Int, genre: java.nio.ByteBuffer, __boltffi_genre_len: Int): ByteArray?
    @JvmStatic external fun boltffi_function_qqmusic_api_helper_next_port_singer_extra_fetch_singer_index(area: java.nio.ByteBuffer, __boltffi_area_len: Int, sex: java.nio.ByteBuffer, __boltffi_sex_len: Int, genre: java.nio.ByteBuffer, __boltffi_genre_len: Int, index: java.nio.ByteBuffer, __boltffi_index_len: Int, page: java.nio.ByteBuffer, __boltffi_page_len: Int, num: java.nio.ByteBuffer, __boltffi_num_len: Int): ByteArray?
    @JvmStatic external fun boltffi_function_qqmusic_api_helper_next_port_singer_extra_fetch_similar_artists(singer_mid: java.nio.ByteBuffer, __boltffi_singer_mid_len: Int, number: java.nio.ByteBuffer, __boltffi_number_len: Int): ByteArray?
    @JvmStatic external fun boltffi_function_qqmusic_api_helper_next_port_singer_extra_fetch_artist_tab(singer_mid: java.nio.ByteBuffer, __boltffi_singer_mid_len: Int, tab_type: java.nio.ByteBuffer, __boltffi_tab_type_len: Int, page: java.nio.ByteBuffer, __boltffi_page_len: Int, num: java.nio.ByteBuffer, __boltffi_num_len: Int): ByteArray?
    @JvmStatic external fun boltffi_function_qqmusic_api_helper_next_port_singer_extra_fetch_artist_display_name(singer_mid: java.nio.ByteBuffer, __boltffi_singer_mid_len: Int): ByteArray?
    @JvmStatic external fun boltffi_function_qqmusic_api_helper_next_port_singer_extra_fetch_artist_mvs(singer_mid: java.nio.ByteBuffer, __boltffi_singer_mid_len: Int, num: java.nio.ByteBuffer, __boltffi_num_len: Int, page: java.nio.ByteBuffer, __boltffi_page_len: Int): ByteArray?
    @JvmStatic external fun boltffi_function_qqmusic_api_helper_next_port_song_asset_query_songs(songs: java.nio.ByteBuffer, __boltffi_songs_len: Int): ByteArray?
    @JvmStatic external fun boltffi_function_qqmusic_api_helper_next_port_song_asset_fetch_cdn_dispatch(): ByteArray?
    @JvmStatic external fun boltffi_function_qqmusic_api_helper_next_port_song_asset_resolve_song_urls(file_info: java.nio.ByteBuffer, __boltffi_file_info_len: Int, file_type: java.nio.ByteBuffer, __boltffi_file_type_len: Int): ByteArray?
    @JvmStatic external fun boltffi_function_qqmusic_api_helper_next_port_song_asset_fetch_other_versions(value: java.nio.ByteBuffer, __boltffi_value_len: Int): ByteArray?
    @JvmStatic external fun boltffi_function_qqmusic_api_helper_next_port_song_asset_fetch_song_producer(value: java.nio.ByteBuffer, __boltffi_value_len: Int): ByteArray?
    @JvmStatic external fun boltffi_function_qqmusic_api_helper_next_port_song_asset_fetch_song_fav_count(song_ids: LongArray): ByteArray?
    @JvmStatic external fun boltffi_function_qqmusic_api_helper_next_port_song_related_fetch_similar_songs(song_id: Long): ByteArray?
    @JvmStatic external fun boltffi_function_qqmusic_api_helper_next_port_song_related_fetch_song_labels(song_id: Long): ByteArray?
    @JvmStatic external fun boltffi_function_qqmusic_api_helper_next_port_song_related_fetch_related_playlists(song_id: Long, last: java.nio.ByteBuffer, __boltffi_last_len: Int): ByteArray?
    @JvmStatic external fun boltffi_function_qqmusic_api_helper_next_port_song_related_fetch_related_mvs(song_id: Long, last_mvid: java.nio.ByteBuffer, __boltffi_last_mvid_len: Int): ByteArray?
    @JvmStatic external fun boltffi_function_qqmusic_api_helper_next_port_song_related_fetch_sheet_music(mid: java.nio.ByteBuffer, __boltffi_mid_len: Int, ttype: java.nio.ByteBuffer, __boltffi_ttype_len: Int): ByteArray?
    @JvmStatic external fun boltffi_function_qqmusic_api_helper_next_port_song_related_has_sheet_music(mid: java.nio.ByteBuffer, __boltffi_mid_len: Int): ByteArray?
    @JvmStatic external fun boltffi_function_qqmusic_api_helper_next_port_user_asset_fetch_fav_playlists(euin: java.nio.ByteBuffer, __boltffi_euin_len: Int, page: java.nio.ByteBuffer, __boltffi_page_len: Int, num: java.nio.ByteBuffer, __boltffi_num_len: Int): ByteArray?
    @JvmStatic external fun boltffi_function_qqmusic_api_helper_next_port_user_asset_fetch_fav_albums(euin: java.nio.ByteBuffer, __boltffi_euin_len: Int, page: java.nio.ByteBuffer, __boltffi_page_len: Int, num: java.nio.ByteBuffer, __boltffi_num_len: Int): ByteArray?
    @JvmStatic external fun boltffi_function_qqmusic_api_helper_next_port_user_asset_fetch_fav_mvs(euin: java.nio.ByteBuffer, __boltffi_euin_len: Int, page: java.nio.ByteBuffer, __boltffi_page_len: Int, num: java.nio.ByteBuffer, __boltffi_num_len: Int): ByteArray?
    @JvmStatic external fun boltffi_function_qqmusic_api_helper_next_port_user_asset_fetch_music_gene(euin: java.nio.ByteBuffer, __boltffi_euin_len: Int): ByteArray?
    @JvmStatic external fun boltffi_function_qqmusic_api_helper_next_port_user_asset_fetch_dislike_list(cmd: java.nio.ByteBuffer, __boltffi_cmd_len: Int, page: java.nio.ByteBuffer, __boltffi_page_len: Int, lastid: java.nio.ByteBuffer, __boltffi_lastid_len: Int): ByteArray?
    @JvmStatic external fun boltffi_function_qqmusic_api_helper_next_port_user_asset_add_dislike(id_type: Long, values: LongArray): Boolean
    @JvmStatic external fun boltffi_function_qqmusic_api_helper_next_port_user_asset_cancel_dislike(id_type: Long, values: LongArray): Boolean
    @JvmStatic external fun boltffi_function_qqmusic_api_helper_next_port_user_asset_clear_dislike_songs(): Boolean
    @JvmStatic external fun boltffi_function_qqmusic_api_helper_next_port_user_relation_fetch_user_homepage(euin: java.nio.ByteBuffer, __boltffi_euin_len: Int): ByteArray?
    @JvmStatic external fun boltffi_function_qqmusic_api_helper_next_port_user_relation_fetch_vip_info(): ByteArray?
    @JvmStatic external fun boltffi_function_qqmusic_api_helper_next_port_user_relation_fetch_follow_singers(euin: java.nio.ByteBuffer, __boltffi_euin_len: Int, page: java.nio.ByteBuffer, __boltffi_page_len: Int, num: java.nio.ByteBuffer, __boltffi_num_len: Int): ByteArray?
    @JvmStatic external fun boltffi_function_qqmusic_api_helper_next_port_user_relation_fetch_follow_singers_at_offset(euin: java.nio.ByteBuffer, __boltffi_euin_len: Int, offset: Long, num: java.nio.ByteBuffer, __boltffi_num_len: Int): ByteArray?
    @JvmStatic external fun boltffi_function_qqmusic_api_helper_next_port_user_relation_fetch_fans(euin: java.nio.ByteBuffer, __boltffi_euin_len: Int, page: java.nio.ByteBuffer, __boltffi_page_len: Int, num: java.nio.ByteBuffer, __boltffi_num_len: Int): ByteArray?
    @JvmStatic external fun boltffi_function_qqmusic_api_helper_next_port_user_relation_fetch_friends(page: java.nio.ByteBuffer, __boltffi_page_len: Int, num: java.nio.ByteBuffer, __boltffi_num_len: Int): ByteArray?
    @JvmStatic external fun boltffi_function_qqmusic_api_helper_next_port_user_relation_fetch_followed_users(euin: java.nio.ByteBuffer, __boltffi_euin_len: Int, page: java.nio.ByteBuffer, __boltffi_page_len: Int, num: java.nio.ByteBuffer, __boltffi_num_len: Int): ByteArray?
    @JvmStatic external fun boltffi_function_qqmusic_api_helper_next_port_user_relation_fetch_created_playlists(uin: java.nio.ByteBuffer, __boltffi_uin_len: Int): ByteArray?
    @JvmStatic external fun boltffi_function_qqmusic_api_helper_next_api_login_status(): ByteArray?
    @JvmStatic external fun boltffi_function_qqmusic_api_helper_next_api_import_credential(uin: java.nio.ByteBuffer, __boltffi_uin_len: Int, qm_keyst: java.nio.ByteBuffer, __boltffi_qm_keyst_len: Int): Unit
    @JvmStatic external fun boltffi_function_qqmusic_api_helper_next_api_import_credential_with_encrypt_uin(uin: java.nio.ByteBuffer, __boltffi_uin_len: Int, qm_keyst: java.nio.ByteBuffer, __boltffi_qm_keyst_len: Int, encrypt_uin: java.nio.ByteBuffer, __boltffi_encrypt_uin_len: Int): Unit
    @JvmStatic external fun boltffi_function_qqmusic_api_helper_next_api_logout(): Unit
    @JvmStatic external fun boltffi_function_qqmusic_api_helper_next_api_liked_songs(page: Int, limit: Int): ByteArray?
    @JvmStatic external fun boltffi_function_qqmusic_api_helper_next_api_playlist_tracks(list_id: Long, offset: Int, limit: Int): ByteArray?
    @JvmStatic external fun boltffi_function_qqmusic_api_helper_next_api_playlist_tracks_page(list_id: Long, dir_id: java.nio.ByteBuffer, __boltffi_dir_id_len: Int, offset: Int, limit: Int): ByteArray?
    @JvmStatic external fun boltffi_function_qqmusic_api_helper_next_api_set_liked_by_id(song_id: Long, liked: Boolean): ByteArray?
    @JvmStatic external fun boltffi_function_qqmusic_api_helper_next_api_user_playlists(limit: Int): ByteArray?
    @JvmStatic external fun boltffi_function_qqmusic_api_helper_next_api_liked_albums(limit: Int): ByteArray?
    @JvmStatic external fun boltffi_function_qqmusic_api_helper_next_api_followed_artists(page: Int, limit: Int): ByteArray?
    @JvmStatic external fun boltffi_function_qqmusic_api_helper_next_api_component_info(): ByteArray?
    @JvmStatic external fun boltffi_function_qqmusic_api_helper_next_api_guard_status(): ByteArray?
    @JvmStatic external fun boltffi_function_qqmusic_api_helper_next_api_song_detail(song_mid: java.nio.ByteBuffer, __boltffi_song_mid_len: Int): ByteArray?
    @JvmStatic external fun boltffi_function_qqmusic_api_helper_next_api_album_detail(album_mid: java.nio.ByteBuffer, __boltffi_album_mid_len: Int, album_id: java.nio.ByteBuffer, __boltffi_album_id_len: Int): ByteArray?
    @JvmStatic external fun boltffi_function_qqmusic_api_helper_next_api_album_tracks(album_mid: java.nio.ByteBuffer, __boltffi_album_mid_len: Int, album_id: java.nio.ByteBuffer, __boltffi_album_id_len: Int, offset: Long, limit: Long): ByteArray?
    @JvmStatic external fun boltffi_function_qqmusic_api_helper_next_api_artist_songs(singer_mid: java.nio.ByteBuffer, __boltffi_singer_mid_len: Int, sort: java.nio.ByteBuffer, __boltffi_sort_len: Int, page: Long, limit: Long): ByteArray?
    @JvmStatic external fun boltffi_function_qqmusic_api_helper_next_api_artist_songs_page(singer_mid: java.nio.ByteBuffer, __boltffi_singer_mid_len: Int, sort: java.nio.ByteBuffer, __boltffi_sort_len: Int, offset: Int, limit: Int): ByteArray?
    @JvmStatic external fun boltffi_function_qqmusic_api_helper_next_api_artist_albums(singer_mid: java.nio.ByteBuffer, __boltffi_singer_mid_len: Int, sort: java.nio.ByteBuffer, __boltffi_sort_len: Int, page: Long, limit: Long): ByteArray?
    @JvmStatic external fun boltffi_function_qqmusic_api_helper_next_api_artist_albums_page(singer_mid: java.nio.ByteBuffer, __boltffi_singer_mid_len: Int, sort: java.nio.ByteBuffer, __boltffi_sort_len: Int, offset: Int, limit: Int): ByteArray?
    @JvmStatic external fun boltffi_function_qqmusic_api_helper_next_api_artist_detail(singer_mid: java.nio.ByteBuffer, __boltffi_singer_mid_len: Int): ByteArray?
    @JvmStatic external fun boltffi_function_qqmusic_api_helper_next_api_toplist_categories(): ByteArray?
    @JvmStatic external fun boltffi_function_qqmusic_api_helper_next_api_toplist_tracks(top_id: Long, offset: Long, limit: Long): ByteArray?
    @JvmStatic external fun boltffi_function_qqmusic_api_helper_next_api_radio_stations(): ByteArray?
    @JvmStatic external fun boltffi_function_qqmusic_api_helper_next_api_radio_tracks(station_id: Long, limit: Long, first_play: Boolean): ByteArray?
    @JvmStatic external fun boltffi_function_qqmusic_api_helper_next_api_radio_track_batch(station_id: Long, first_play: Boolean): ByteArray?
    @JvmStatic external fun boltffi_function_qqmusic_api_helper_next_api_new_songs(region_type: Long): ByteArray?
    @JvmStatic external fun boltffi_function_qqmusic_api_helper_next_api_recommend_feed(): ByteArray?
    @JvmStatic external fun boltffi_function_qqmusic_api_helper_next_api_lyric(song_mid: java.nio.ByteBuffer, __boltffi_song_mid_len: Int, song_id: java.nio.ByteBuffer, __boltffi_song_id_len: Int, word_timing: Boolean, translation: Boolean): ByteArray?
    @JvmStatic external fun boltffi_function_qqmusic_api_helper_next_api_resolve_song_url(song_mid: java.nio.ByteBuffer, __boltffi_song_mid_len: Int, media_mid: java.nio.ByteBuffer, __boltffi_media_mid_len: Int, song_type: Long, preferred_quality: java.nio.ByteBuffer, __boltffi_preferred_quality_len: Int): ByteArray?
    @JvmStatic external fun boltffi_function_qqmusic_api_helper_next_api_call_with_platform(method: java.nio.ByteBuffer, __boltffi_method_len: Int, params_json: java.nio.ByteBuffer, __boltffi_params_json_len: Int, platform: java.nio.ByteBuffer, __boltffi_platform_len: Int): ByteArray?
    @JvmStatic external fun boltffi_function_qqmusic_api_helper_next_api_set_liked(song_mid: java.nio.ByteBuffer, __boltffi_song_mid_len: Int, song_type: Long, liked: Boolean): Unit
    @JvmStatic external fun boltffi_function_qqmusic_api_helper_next_api_start_login(): ByteArray?
    @JvmStatic external fun boltffi_function_qqmusic_api_helper_next_api_poll_login(identifier: java.nio.ByteBuffer, __boltffi_identifier_len: Int): ByteArray?
    @JvmStatic external fun boltffi_function_qqmusic_api_helper_next_api_search_songs(keyword: java.nio.ByteBuffer, __boltffi_keyword_len: Int, page: Long, limit: Long): ByteArray?
    @JvmStatic external fun boltffi_function_qqmusic_api_helper_next_api_search_artists(keyword: java.nio.ByteBuffer, __boltffi_keyword_len: Int, page: Long, limit: Long): ByteArray?
    @JvmStatic external fun boltffi_function_qqmusic_api_helper_next_api_search_albums(keyword: java.nio.ByteBuffer, __boltffi_keyword_len: Int, page: Long, limit: Long): ByteArray?
    @JvmStatic external fun boltffi_function_qqmusic_api_helper_next_api_search_playlists(keyword: java.nio.ByteBuffer, __boltffi_keyword_len: Int, page: Long, limit: Long): ByteArray?
    @JvmStatic external fun boltffi_function_qqmusic_api_helper_next_api_search_track_artwork(title: java.nio.ByteBuffer, __boltffi_title_len: Int, artist: java.nio.ByteBuffer, __boltffi_artist_len: Int, album: java.nio.ByteBuffer, __boltffi_album_len: Int, limit: Long): ByteArray?
    @JvmStatic external fun boltffi_function_qqmusic_api_helper_next_api_search_artist_artwork(name: java.nio.ByteBuffer, __boltffi_name_len: Int, limit: Long): ByteArray?
    @JvmStatic external fun boltffi_function_qqmusic_api_helper_next_api_search_album_artwork(album: java.nio.ByteBuffer, __boltffi_album_len: Int, artist: java.nio.ByteBuffer, __boltffi_artist_len: Int, limit: Long): ByteArray?
    @JvmStatic external fun boltffi_function_qqmusic_api_helper_next_api_set_rate_limit(enabled: Boolean, window_seconds: Long, max_requests: Long): ByteArray?
    @JvmStatic external fun boltffi_function_qqmusic_api_helper_next_api_set_breaker(enabled: Boolean, failure_threshold: Long, failure_window_seconds: Long, open_seconds: Long): ByteArray?
    @JvmStatic external fun boltffi_function_qqmusic_api_helper_next_api_aria2_status(ensure: Boolean): ByteArray?
    @JvmStatic external fun boltffi_function_qqmusic_api_helper_next_api_aria2_restart(): ByteArray?
    @JvmStatic external fun boltffi_function_qqmusic_api_helper_next_api_aria2_configure(split: Long, max_connection_per_server: Long, max_concurrent_downloads: Long, min_split_size_mib: Long, max_overall_download_limit_kib: Long, port: Long): ByteArray?
    @JvmStatic external fun boltffi_function_qqmusic_api_helper_next_api_aria2_add(url: java.nio.ByteBuffer, __boltffi_url_len: Int, `out`: java.nio.ByteBuffer, __boltffi_out_len: Int): ByteArray?
    @JvmStatic external fun boltffi_function_qqmusic_api_helper_next_api_aria2_tell(gid: java.nio.ByteBuffer, __boltffi_gid_len: Int): ByteArray?
    @JvmStatic external fun boltffi_function_qqmusic_api_helper_next_api_aria2_list(): ByteArray?
    @JvmStatic external fun boltffi_function_qqmusic_api_helper_next_api_aria2_pause(gid: java.nio.ByteBuffer, __boltffi_gid_len: Int): ByteArray?
    @JvmStatic external fun boltffi_function_qqmusic_api_helper_next_api_aria2_unpause(gid: java.nio.ByteBuffer, __boltffi_gid_len: Int): ByteArray?
    @JvmStatic external fun boltffi_function_qqmusic_api_helper_next_api_aria2_cancel(gid: java.nio.ByteBuffer, __boltffi_gid_len: Int): ByteArray?
    @JvmStatic external fun boltffi_function_qqmusic_api_helper_next_api_fetch_artist_biography(name: java.nio.ByteBuffer, __boltffi_name_len: Int, singer_mid: java.nio.ByteBuffer, __boltffi_singer_mid_len: Int): ByteArray?
    @JvmStatic external fun boltffi_function_qqmusic_api_helper_next_initialize(data_dir: java.nio.ByteBuffer, __boltffi_data_dir_len: Int, platform: java.nio.ByteBuffer, __boltffi_platform_len: Int): Unit
}


/**
 * 参考 `song_info` 的 `(song_id, song_type)` 对.
 *
 * 宿主侧用具名结构而不是 `Vec<(i64, i64)>`：BoltFFI 渲染不了元组向量
 * （见 docs/ffi.md「生成器的限制」）。JSON 上就是 `{songId, songType}`。
 *
 * 两个字段都是标量，`#[data]` 把它当 blittable，因此必须 `Copy`
 * （与 `models.rs` 的 `RateLimitUsage` 同款）。
 */
data class SongInfoPair(
    /**
     * 歌曲数字 ID.
     */
    val songId: Long,
    /**
     * 歌曲类型（1=普通歌曲，2=长音频，6=视频/直播……）.
     */
    val songType: Long
) {
    internal fun toByteArray(): ByteArray {
        val buffer = java.nio.ByteBuffer
            .allocate(STRUCT_SIZE)
            .order(java.nio.ByteOrder.nativeOrder())
        writeTo(buffer, 0)
        return buffer.array()
    }

    internal fun toDirectBuffer(): java.nio.ByteBuffer {
        val buffer = java.nio.ByteBuffer
            .allocateDirect(STRUCT_SIZE)
            .order(java.nio.ByteOrder.nativeOrder())
        writeTo(buffer, 0)
        return buffer
    }

    internal fun writeTo(buffer: java.nio.ByteBuffer, offset: Int) {
        buffer.putLong(offset, songId)
        buffer.putLong(offset + 8, songType)
    }

    companion object {
        internal const val STRUCT_SIZE: Int = 16

        internal fun fromByteArray(bytes: ByteArray): SongInfoPair {
            require(bytes.size == STRUCT_SIZE)
            val buffer = java.nio.ByteBuffer
                .wrap(bytes)
                .order(java.nio.ByteOrder.nativeOrder())
            return fromBuffer(buffer, 0)
        }

        internal fun fromBuffer(buffer: java.nio.ByteBuffer, offset: Int): SongInfoPair {
            return SongInfoPair(
                buffer.getLong(offset),
                buffer.getLong(offset + 8)
            )
        }
    }
}


/**
 * 参考 `CreateDeleteSonglistResp`：建 / 删歌单的响应体.
 *
 * 参考模型里 `retCode` 是必填，这里给 `Option`：上游缺了就是 `null`，不假装
 * 有值（`id`/`dirid`/`name` 在参考里带 jsonpath，指向 `result` 块）。
 */
data class CreateDeleteSonglistResp(
    /**
     * 返回码（为 0 表示成功）.
     */
    val retCode: Long?,
    /**
     * 创建成功的歌单 ID（上游 `$.result.tid`）.
     */
    val id: Long?,
    /**
     * 创建成功的歌单目录 ID（上游 `$.result.dirId`；删除不存在的歌单时是 0）.
     */
    val dirid: Long?,
    /**
     * 创建成功的歌单名称（上游 `$.result.dirName`）.
     */
    val name: String?
) {
    internal fun wireSize(): Int {
        return 1 + (this.retCode?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.id?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.dirid?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.name?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0)
    }

    internal fun writeTo(writer: WireWriter) {
        writer.writeOptionalValue(this.retCode, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.id, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.dirid, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.name, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
    }

    internal fun toByteArray(): ByteArray {
        val buffer = WireWriterPool.acquire(wireSize())
        val writer = buffer.writer
        try {
            writeTo(writer)
            return buffer.bytes()
        } finally {
            buffer.close()
        }
    }

    companion object {
        internal fun fromReader(reader: WireReader): CreateDeleteSonglistResp {
            return CreateDeleteSonglistResp(
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readString() })
            )
        }

        internal fun fromByteArray(bytes: ByteArray): CreateDeleteSonglistResp {
            val reader = WireReader(bytes)
            return fromReader(reader)
        }
    }
}


/**
 * 参考 `AlbumFavWriteResponse`：收藏 / 取消收藏专辑的写操作响应.
 *
 * 参考模型的 `result` 与 `failed_album_id` 都有缺省（0 / `[]`），`success` 是
 * 它的计算属性（`result == 0 and not failed_album_id`），这里一并给出来，
 * 宿主不必自己算。
 */
data class AlbumFavWriteResponse(
    /**
     * 操作结果码，0 表示成功.
     */
    val result: Long?,
    /**
     * 操作失败的专辑 ID 列表（上游 `v_failedAlbumId`），全部成功时为空.
     */
    val failedAlbumId: LongArray?,
    /**
     * 是否操作成功（`result` 为 0 且无失败项）.
     */
    val success: Boolean?
) {
    internal fun wireSize(): Int {
        return 1 + (this.result?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.failedAlbumId?.let { __boltffi_value_0 -> 4 + __boltffi_value_0.size * 8 } ?: 0) + 1 + (this.success?.let { __boltffi_value_0 -> 1 } ?: 0)
    }

    internal fun writeTo(writer: WireWriter) {
        writer.writeOptionalValue(this.result, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.failedAlbumId, { writer, __boltffi_value_0 -> writer.writeLongArray(__boltffi_value_0) })
        writer.writeOptionalValue(this.success, { writer, __boltffi_value_0 -> writer.writeBool(__boltffi_value_0) })
    }

    internal fun toByteArray(): ByteArray {
        val buffer = WireWriterPool.acquire(wireSize())
        val writer = buffer.writer
        try {
            writeTo(writer)
            return buffer.bytes()
        } finally {
            buffer.close()
        }
    }

    companion object {
        internal fun fromReader(reader: WireReader): AlbumFavWriteResponse {
            return AlbumFavWriteResponse(
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readLongArray() }),
                reader.readOptionalValue({ reader -> reader.readBool() })
            )
        }

        internal fun fromByteArray(bytes: ByteArray): AlbumFavWriteResponse {
            val reader = WireReader(bytes)
            return fromReader(reader)
        }
    }
}


/**
 * 收藏歌单的布尔回值，与参考 user.fav_songlist / unfav_songlist 一致。
 */
data class PlaylistFavWriteResponse(
    val success: Boolean
) {
    internal fun toByteArray(): ByteArray {
        val buffer = java.nio.ByteBuffer
            .allocate(STRUCT_SIZE)
            .order(java.nio.ByteOrder.nativeOrder())
        writeTo(buffer, 0)
        return buffer.array()
    }

    internal fun toDirectBuffer(): java.nio.ByteBuffer {
        val buffer = java.nio.ByteBuffer
            .allocateDirect(STRUCT_SIZE)
            .order(java.nio.ByteOrder.nativeOrder())
        writeTo(buffer, 0)
        return buffer
    }

    internal fun writeTo(buffer: java.nio.ByteBuffer, offset: Int) {
        buffer.put(offset, if (success) 1.toByte() else 0.toByte())
    }

    companion object {
        internal const val STRUCT_SIZE: Int = 1

        internal fun fromByteArray(bytes: ByteArray): PlaylistFavWriteResponse {
            require(bytes.size == STRUCT_SIZE)
            val buffer = java.nio.ByteBuffer
                .wrap(bytes)
                .order(java.nio.ByteOrder.nativeOrder())
            return fromBuffer(buffer, 0)
        }

        internal fun fromBuffer(buffer: java.nio.ByteBuffer, offset: Int): PlaylistFavWriteResponse {
            return PlaylistFavWriteResponse(
                buffer.`get`(offset) != 0.toByte()
            )
        }
    }
}


/**
 * 参考 `IconTextInfo`：评论数量接口附带的角标文案。
 */
data class CommentIcon(
    /**
     * 角标展示文案.
     */
    val txt: String?,
    /**
     * 角标唯一标识.
     */
    val uniqueId: String?,
    /**
     * 角标类型（参考的字段就叫 `type`，Rust 侧换个名字、JSON 上仍是 `type`）.
     */
    val kind: Long?,
    /**
     * 关联评论 ID.
     */
    val cmid: String?,
    /**
     * 是否为动态角标.
     */
    val isDynamic: Boolean?
) {
    internal fun wireSize(): Int {
        return 1 + (this.txt?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.uniqueId?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.kind?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.cmid?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.isDynamic?.let { __boltffi_value_0 -> 1 } ?: 0)
    }

    internal fun writeTo(writer: WireWriter) {
        writer.writeOptionalValue(this.txt, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.uniqueId, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.kind, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.cmid, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.isDynamic, { writer, __boltffi_value_0 -> writer.writeBool(__boltffi_value_0) })
    }

    internal fun toByteArray(): ByteArray {
        val buffer = WireWriterPool.acquire(wireSize())
        val writer = buffer.writer
        try {
            writeTo(writer)
            return buffer.bytes()
        } finally {
            buffer.close()
        }
    }

    companion object {
        internal fun fromReader(reader: WireReader): CommentIcon {
            return CommentIcon(
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readBool() })
            )
        }

        internal fun fromByteArray(bytes: ByteArray): CommentIcon {
            val reader = WireReader(bytes)
            return fromReader(reader)
        }
    }
}


/**
 * 参考 `CommentCountResponse`：评论数量接口的统计结果.
 */
data class CommentCount(
    val bizType: Long?,
    val bizId: String?,
    val bizSubType: Long?,
    /**
     * 评论总数.
     */
    val count: Long?,
    /**
     * 计数字段版本.
     */
    val countVer: String?,
    /**
     * 面向展示的计数文案.
     */
    val countView: String?,
    val relatedId: String?,
    /**
     * 附加提示文案.
     */
    val tip: String?,
    val iconList: List<CommentIcon>?,
    /**
     * 评论标签页类型（在 `response` 的兄弟键 `cmTabType` 上）.
     */
    val cmTabType: Long?
) {
    internal fun wireSize(): Int {
        return 1 + (this.bizType?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.bizId?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.bizSubType?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.count?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.countVer?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.countView?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.relatedId?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.tip?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.iconList?.let { __boltffi_value_0 -> 4 + __boltffi_value_0.sumOf { __boltffi_value_1 -> val __boltffi_size: kotlin.Int = __boltffi_value_1.wireSize(); __boltffi_size } } ?: 0) + 1 + (this.cmTabType?.let { __boltffi_value_0 -> 8 } ?: 0)
    }

    internal fun writeTo(writer: WireWriter) {
        writer.writeOptionalValue(this.bizType, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.bizId, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.bizSubType, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.count, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.countVer, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.countView, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.relatedId, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.tip, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.iconList, { writer, __boltffi_value_0 -> writer.writeSequence(__boltffi_value_0, __boltffi_value_0.size, { writer, __boltffi_value_1 -> __boltffi_value_1.writeTo(writer) }) })
        writer.writeOptionalValue(this.cmTabType, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
    }

    internal fun toByteArray(): ByteArray {
        val buffer = WireWriterPool.acquire(wireSize())
        val writer = buffer.writer
        try {
            writeTo(writer)
            return buffer.bytes()
        } finally {
            buffer.close()
        }
    }

    companion object {
        internal fun fromReader(reader: WireReader): CommentCount {
            return CommentCount(
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readSequence({ reader -> CommentIcon.fromReader(reader) }) }),
                reader.readOptionalValue({ reader -> reader.readI64() })
            )
        }

        internal fun fromByteArray(bytes: ByteArray): CommentCount {
            val reader = WireReader(bytes)
            return fromReader(reader)
        }
    }
}


/**
 * 参考 `CommentItem`：标准评论列表里的单条评论.
 *
 * `song_ts_elems` / `hash_tag_list` / `little_tails` / `icon_list` /
 * `vip_ui` / `sub_comments` 在参考里是 `list[dict]` / `dict`（原样透传的
 * 上游形状），这里存 JSON 文本：`#[data]` 认不出 `serde_json::Value`，
 * 而经 serde 进出时它们仍是真正的嵌套对象/数组（见 `raw_json_serialize`）。
 */
data class Comment(
    val cmid: String?,
    val seqNo: String?,
    val nick: String?,
    val avatar: String?,
    val encryptUin: String?,
    val content: String?,
    val pubTime: Long?,
    val praiseNum: Long?,
    val replyCnt: Long?,
    val isPraised: Long?,
    val isSelf: Long?,
    val state: Long?,
    val hotScore: String?,
    val recScore: String?,
    val songId: Long?,
    val songName: String?,
    val singerNames: String?,
    val songTsElems: String?,
    val hashTagList: String?,
    val littleTails: String?,
    val iconList: String?,
    val vipUi: String?,
    val subComments: String?
) {
    internal fun wireSize(): Int {
        return 1 + (this.cmid?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.seqNo?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.nick?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.avatar?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.encryptUin?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.content?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.pubTime?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.praiseNum?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.replyCnt?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.isPraised?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.isSelf?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.state?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.hotScore?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.recScore?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.songId?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.songName?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.singerNames?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.songTsElems?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.hashTagList?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.littleTails?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.iconList?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.vipUi?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.subComments?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0)
    }

    internal fun writeTo(writer: WireWriter) {
        writer.writeOptionalValue(this.cmid, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.seqNo, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.nick, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.avatar, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.encryptUin, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.content, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.pubTime, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.praiseNum, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.replyCnt, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.isPraised, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.isSelf, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.state, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.hotScore, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.recScore, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.songId, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.songName, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.singerNames, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.songTsElems, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.hashTagList, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.littleTails, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.iconList, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.vipUi, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.subComments, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
    }

    internal fun toByteArray(): ByteArray {
        val buffer = WireWriterPool.acquire(wireSize())
        val writer = buffer.writer
        try {
            writeTo(writer)
            return buffer.bytes()
        } finally {
            buffer.close()
        }
    }

    companion object {
        internal fun fromReader(reader: WireReader): Comment {
            return Comment(
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() })
            )
        }

        internal fun fromByteArray(bytes: ByteArray): Comment {
            val reader = WireReader(bytes)
            return fromReader(reader)
        }
    }
}


/**
 * 参考 `CommentListResponse`：常规评论列表接口的响应体（一页）.
 */
data class CommentList(
    val comments: List<Comment>?,
    val commentIds: List<String>?,
    /**
     * 是否还有更多结果（0/1）.
     */
    val hasMore: Long?,
    val nextOffset: Long?,
    /**
     * 评论总数.
     */
    val total: Long?,
    val totalCmNum: Long?,
    val commentTip: String?,
    val commentH5Page: String?,
    val hasTsCm: Long?,
    val shareCnt: Long?,
    val msg: String?,
    val subCode: Long?
) {
    internal fun wireSize(): Int {
        return 1 + (this.comments?.let { __boltffi_value_0 -> 4 + __boltffi_value_0.sumOf { __boltffi_value_1 -> val __boltffi_size: kotlin.Int = __boltffi_value_1.wireSize(); __boltffi_size } } ?: 0) + 1 + (this.commentIds?.let { __boltffi_value_0 -> 4 + __boltffi_value_0.sumOf { __boltffi_value_1 -> val __boltffi_size: kotlin.Int = 4 + Utf8Codec.maxBytes(__boltffi_value_1); __boltffi_size } } ?: 0) + 1 + (this.hasMore?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.nextOffset?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.total?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.totalCmNum?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.commentTip?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.commentH5Page?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.hasTsCm?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.shareCnt?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.msg?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.subCode?.let { __boltffi_value_0 -> 8 } ?: 0)
    }

    internal fun writeTo(writer: WireWriter) {
        writer.writeOptionalValue(this.comments, { writer, __boltffi_value_0 -> writer.writeSequence(__boltffi_value_0, __boltffi_value_0.size, { writer, __boltffi_value_1 -> __boltffi_value_1.writeTo(writer) }) })
        writer.writeOptionalValue(this.commentIds, { writer, __boltffi_value_0 -> writer.writeSequence(__boltffi_value_0, __boltffi_value_0.size, { writer, __boltffi_value_1 -> writer.writeString(__boltffi_value_1) }) })
        writer.writeOptionalValue(this.hasMore, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.nextOffset, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.total, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.totalCmNum, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.commentTip, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.commentH5Page, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.hasTsCm, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.shareCnt, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.msg, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.subCode, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
    }

    internal fun toByteArray(): ByteArray {
        val buffer = WireWriterPool.acquire(wireSize())
        val writer = buffer.writer
        try {
            writeTo(writer)
            return buffer.bytes()
        } finally {
            buffer.close()
        }
    }

    companion object {
        internal fun fromReader(reader: WireReader): CommentList {
            return CommentList(
                reader.readOptionalValue({ reader -> reader.readSequence({ reader -> Comment.fromReader(reader) }) }),
                reader.readOptionalValue({ reader -> reader.readSequence({ reader -> reader.readString() }) }),
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readI64() })
            )
        }

        internal fun fromByteArray(bytes: ByteArray): CommentList {
            val reader = WireReader(bytes)
            return fromReader(reader)
        }
    }
}


/**
 * 参考 `MomentCommentItem`：时刻评论流里的单条评论.
 */
data class MomentComment(
    val cmid: String?,
    val seqNo: String?,
    val content: String?,
    val encryptUin: String?,
    val pubTime: Long?,
    val praiseNum: Long?,
    val replyCnt: Long?,
    val state: Long?,
    val isSelf: Long?,
    val location: String?,
    val phoneType: String?,
    val pic: String?,
    val picSize: String?,
    val songTsElems: String?,
    val hashTagList: String?,
    val littleTails: String?
) {
    internal fun wireSize(): Int {
        return 1 + (this.cmid?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.seqNo?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.content?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.encryptUin?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.pubTime?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.praiseNum?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.replyCnt?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.state?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.isSelf?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.location?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.phoneType?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.pic?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.picSize?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.songTsElems?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.hashTagList?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.littleTails?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0)
    }

    internal fun writeTo(writer: WireWriter) {
        writer.writeOptionalValue(this.cmid, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.seqNo, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.content, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.encryptUin, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.pubTime, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.praiseNum, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.replyCnt, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.state, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.isSelf, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.location, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.phoneType, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.pic, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.picSize, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.songTsElems, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.hashTagList, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.littleTails, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
    }

    internal fun toByteArray(): ByteArray {
        val buffer = WireWriterPool.acquire(wireSize())
        val writer = buffer.writer
        try {
            writeTo(writer)
            return buffer.bytes()
        } finally {
            buffer.close()
        }
    }

    companion object {
        internal fun fromReader(reader: WireReader): MomentComment {
            return MomentComment(
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() })
            )
        }

        internal fun fromByteArray(bytes: ByteArray): MomentComment {
            val reader = WireReader(bytes)
            return fromReader(reader)
        }
    }
}


/**
 * 参考 `MomentCommentResponse`：时刻评论列表接口的响应体.
 */
data class MomentCommentList(
    val comments: List<MomentComment>?,
    /**
     * 是否还有更多结果（0/1）.
     */
    val hasMore: Long?,
    /**
     * 下一页游标（回填到请求的 `LastPos`）.
     */
    val nextPos: String?,
    val hint: String?,
    val prevListLoaded: Long?,
    val mapCmExt: String?
) {
    internal fun wireSize(): Int {
        return 1 + (this.comments?.let { __boltffi_value_0 -> 4 + __boltffi_value_0.sumOf { __boltffi_value_1 -> val __boltffi_size: kotlin.Int = __boltffi_value_1.wireSize(); __boltffi_size } } ?: 0) + 1 + (this.hasMore?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.nextPos?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.hint?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.prevListLoaded?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.mapCmExt?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0)
    }

    internal fun writeTo(writer: WireWriter) {
        writer.writeOptionalValue(this.comments, { writer, __boltffi_value_0 -> writer.writeSequence(__boltffi_value_0, __boltffi_value_0.size, { writer, __boltffi_value_1 -> __boltffi_value_1.writeTo(writer) }) })
        writer.writeOptionalValue(this.hasMore, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.nextPos, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.hint, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.prevListLoaded, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.mapCmExt, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
    }

    internal fun toByteArray(): ByteArray {
        val buffer = WireWriterPool.acquire(wireSize())
        val writer = buffer.writer
        try {
            writeTo(writer)
            return buffer.bytes()
        } finally {
            buffer.close()
        }
    }

    companion object {
        internal fun fromReader(reader: WireReader): MomentCommentList {
            return MomentCommentList(
                reader.readOptionalValue({ reader -> reader.readSequence({ reader -> MomentComment.fromReader(reader) }) }),
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readString() })
            )
        }

        internal fun fromByteArray(bytes: ByteArray): MomentCommentList {
            val reader = WireReader(bytes)
            return fromReader(reader)
        }
    }
}


/**
 * 参考 `AddCommentResponse`：发评论的响应体.
 */
data class AddedComment(
    val subcode: Long?,
    val msg: String?,
    /**
     * 新增评论 ID（参考字段名是 `id`）.
     */
    val id: String?,
    /**
     * 父评论 ID.
     */
    val parent: String?,
    /**
     * 楼层号（在 `Floor.Num` 上）.
     */
    val floor: Long?,
    /**
     * 验证码 URL（如果需要）.
     */
    val verifyUrl: String?
) {
    internal fun wireSize(): Int {
        return 1 + (this.subcode?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.msg?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.id?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.parent?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.floor?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.verifyUrl?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0)
    }

    internal fun writeTo(writer: WireWriter) {
        writer.writeOptionalValue(this.subcode, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.msg, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.id, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.parent, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.floor, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.verifyUrl, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
    }

    internal fun toByteArray(): ByteArray {
        val buffer = WireWriterPool.acquire(wireSize())
        val writer = buffer.writer
        try {
            writeTo(writer)
            return buffer.bytes()
        } finally {
            buffer.close()
        }
    }

    companion object {
        internal fun fromReader(reader: WireReader): AddedComment {
            return AddedComment(
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readString() })
            )
        }

        internal fun fromByteArray(bytes: ByteArray): AddedComment {
            val reader = WireReader(bytes)
            return fromReader(reader)
        }
    }
}


/**
 * 新碟摘要, 扩展参考基础 Album 字段.
 */
data class NewAlbumItem(
    val id: Long?,
    val mid: String?,
    val name: String?,
    val title: String?,
    val subtitle: String?,
    val timePublic: String?,
    val pmid: String?,
    val singers: List<Singer>?,
    val releaseTime: String?,
    val kind: Long?,
    val area: Long?,
    val genre: Long?,
    val language: Long?
) {
    internal fun wireSize(): Int {
        return 1 + (this.id?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.mid?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.name?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.title?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.subtitle?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.timePublic?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.pmid?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.singers?.let { __boltffi_value_0 -> 4 + __boltffi_value_0.sumOf { __boltffi_value_1 -> val __boltffi_size: kotlin.Int = __boltffi_value_1.wireSize(); __boltffi_size } } ?: 0) + 1 + (this.releaseTime?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.kind?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.area?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.genre?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.language?.let { __boltffi_value_0 -> 8 } ?: 0)
    }

    internal fun writeTo(writer: WireWriter) {
        writer.writeOptionalValue(this.id, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.mid, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.name, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.title, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.subtitle, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.timePublic, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.pmid, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.singers, { writer, __boltffi_value_0 -> writer.writeSequence(__boltffi_value_0, __boltffi_value_0.size, { writer, __boltffi_value_1 -> __boltffi_value_1.writeTo(writer) }) })
        writer.writeOptionalValue(this.releaseTime, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.kind, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.area, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.genre, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.language, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
    }

    internal fun toByteArray(): ByteArray {
        val buffer = WireWriterPool.acquire(wireSize())
        val writer = buffer.writer
        try {
            writeTo(writer)
            return buffer.bytes()
        } finally {
            buffer.close()
        }
    }

    companion object {
        internal fun fromReader(reader: WireReader): NewAlbumItem {
            return NewAlbumItem(
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readSequence({ reader -> Singer.fromReader(reader) }) }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readI64() })
            )
        }

        internal fun fromByteArray(bytes: ByteArray): NewAlbumItem {
            val reader = WireReader(bytes)
            return fromReader(reader)
        }
    }
}


/**
 * 当前地区的新碟总数与当前页.
 */
data class NewAlbumResponse(
    val total: Long?,
    val albums: List<NewAlbumItem>?
) {
    internal fun wireSize(): Int {
        return 1 + (this.total?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.albums?.let { __boltffi_value_0 -> 4 + __boltffi_value_0.sumOf { __boltffi_value_1 -> val __boltffi_size: kotlin.Int = __boltffi_value_1.wireSize(); __boltffi_size } } ?: 0)
    }

    internal fun writeTo(writer: WireWriter) {
        writer.writeOptionalValue(this.total, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.albums, { writer, __boltffi_value_0 -> writer.writeSequence(__boltffi_value_0, __boltffi_value_0.size, { writer, __boltffi_value_1 -> __boltffi_value_1.writeTo(writer) }) })
    }

    internal fun toByteArray(): ByteArray {
        val buffer = WireWriterPool.acquire(wireSize())
        val writer = buffer.writer
        try {
            writeTo(writer)
            return buffer.bytes()
        } finally {
            buffer.close()
        }
    }

    companion object {
        internal fun fromReader(reader: WireReader): NewAlbumResponse {
            return NewAlbumResponse(
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readSequence({ reader -> NewAlbumItem.fromReader(reader) }) })
            )
        }

        internal fun fromByteArray(bytes: ByteArray): NewAlbumResponse {
            val reader = WireReader(bytes)
            return fromReader(reader)
        }
    }
}


/**
 * 助唱标注可用性, 本接口不返回歌词正文.
 */
data class SingingAnnotationsResponse(
    val hasSingingAnnotationsLyric: Boolean?
) {
    internal fun wireSize(): Int {
        return 1 + (this.hasSingingAnnotationsLyric?.let { __boltffi_value_0 -> 1 } ?: 0)
    }

    internal fun writeTo(writer: WireWriter) {
        writer.writeOptionalValue(this.hasSingingAnnotationsLyric, { writer, __boltffi_value_0 -> writer.writeBool(__boltffi_value_0) })
    }

    internal fun toByteArray(): ByteArray {
        val buffer = WireWriterPool.acquire(wireSize())
        val writer = buffer.writer
        try {
            writeTo(writer)
            return buffer.bytes()
        } finally {
            buffer.close()
        }
    }

    companion object {
        internal fun fromReader(reader: WireReader): SingingAnnotationsResponse {
            return SingingAnnotationsResponse(
                reader.readOptionalValue({ reader -> reader.readBool() })
            )
        }

        internal fun fromByteArray(bytes: ByteArray): SingingAnnotationsResponse {
            val reader = WireReader(bytes)
            return fromReader(reader)
        }
    }
}


/**
 * 多风格翻译歌词的一种风格.
 */
data class MultiStyleLyricItem(
    val style: Long?,
    val styleName: String?,
    val lyric: String?,
    val timestamp: Long?
) {
    internal fun wireSize(): Int {
        return 1 + (this.style?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.styleName?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.lyric?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.timestamp?.let { __boltffi_value_0 -> 8 } ?: 0)
    }

    internal fun writeTo(writer: WireWriter) {
        writer.writeOptionalValue(this.style, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.styleName, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.lyric, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.timestamp, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
    }

    internal fun toByteArray(): ByteArray {
        val buffer = WireWriterPool.acquire(wireSize())
        val writer = buffer.writer
        try {
            writeTo(writer)
            return buffer.bytes()
        } finally {
            buffer.close()
        }
    }

    companion object {
        internal fun fromReader(reader: WireReader): MultiStyleLyricItem {
            return MultiStyleLyricItem(
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readI64() })
            )
        }

        internal fun fromByteArray(bytes: ByteArray): MultiStyleLyricItem {
            val reader = WireReader(bytes)
            return fromReader(reader)
        }
    }
}


/**
 * 多风格翻译列表, 无翻译时为空数组.
 */
data class MultiStyleLyricsResponse(
    val lyrics: List<MultiStyleLyricItem>?
) {
    internal fun wireSize(): Int {
        return 1 + (this.lyrics?.let { __boltffi_value_0 -> 4 + __boltffi_value_0.sumOf { __boltffi_value_1 -> val __boltffi_size: kotlin.Int = __boltffi_value_1.wireSize(); __boltffi_size } } ?: 0)
    }

    internal fun writeTo(writer: WireWriter) {
        writer.writeOptionalValue(this.lyrics, { writer, __boltffi_value_0 -> writer.writeSequence(__boltffi_value_0, __boltffi_value_0.size, { writer, __boltffi_value_1 -> __boltffi_value_1.writeTo(writer) }) })
    }

    internal fun toByteArray(): ByteArray {
        val buffer = WireWriterPool.acquire(wireSize())
        val writer = buffer.writer
        try {
            writeTo(writer)
            return buffer.bytes()
        } finally {
            buffer.close()
        }
    }

    companion object {
        internal fun fromReader(reader: WireReader): MultiStyleLyricsResponse {
            return MultiStyleLyricsResponse(
                reader.readOptionalValue({ reader -> reader.readSequence({ reader -> MultiStyleLyricItem.fromReader(reader) }) })
            )
        }

        internal fun fromByteArray(bytes: ByteArray): MultiStyleLyricsResponse {
            val reader = WireReader(bytes)
            return fromReader(reader)
        }
    }
}


/**
 * AI 歌词词典可用性.
 */
data class AiDictionaryExistsResponse(
    val exists: Boolean?
) {
    internal fun wireSize(): Int {
        return 1 + (this.exists?.let { __boltffi_value_0 -> 1 } ?: 0)
    }

    internal fun writeTo(writer: WireWriter) {
        writer.writeOptionalValue(this.exists, { writer, __boltffi_value_0 -> writer.writeBool(__boltffi_value_0) })
    }

    internal fun toByteArray(): ByteArray {
        val buffer = WireWriterPool.acquire(wireSize())
        val writer = buffer.writer
        try {
            writeTo(writer)
            return buffer.bytes()
        } finally {
            buffer.close()
        }
    }

    companion object {
        internal fun fromReader(reader: WireReader): AiDictionaryExistsResponse {
            return AiDictionaryExistsResponse(
                reader.readOptionalValue({ reader -> reader.readBool() })
            )
        }

        internal fun fromByteArray(bytes: ByteArray): AiDictionaryExistsResponse {
            val reader = WireReader(bytes)
            return fromReader(reader)
        }
    }
}


/**
 * AI 词典中的短语与解释, 时间戳保留参考的字符串类型.
 */
data class AiDictionaryItem(
    val phrase: String?,
    val explain: String?,
    val lyricText: String?,
    val transLyricText: String?,
    val lyricTimestamp: String?
) {
    internal fun wireSize(): Int {
        return 1 + (this.phrase?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.explain?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.lyricText?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.transLyricText?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.lyricTimestamp?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0)
    }

    internal fun writeTo(writer: WireWriter) {
        writer.writeOptionalValue(this.phrase, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.explain, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.lyricText, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.transLyricText, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.lyricTimestamp, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
    }

    internal fun toByteArray(): ByteArray {
        val buffer = WireWriterPool.acquire(wireSize())
        val writer = buffer.writer
        try {
            writeTo(writer)
            return buffer.bytes()
        } finally {
            buffer.close()
        }
    }

    companion object {
        internal fun fromReader(reader: WireReader): AiDictionaryItem {
            return AiDictionaryItem(
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() })
            )
        }

        internal fun fromByteArray(bytes: ByteArray): AiDictionaryItem {
            val reader = WireReader(bytes)
            return fromReader(reader)
        }
    }
}


/**
 * AI 歌词词典列表.
 */
data class AiDictionaryResponse(
    val dictList: List<AiDictionaryItem>?
) {
    internal fun wireSize(): Int {
        return 1 + (this.dictList?.let { __boltffi_value_0 -> 4 + __boltffi_value_0.sumOf { __boltffi_value_1 -> val __boltffi_size: kotlin.Int = __boltffi_value_1.wireSize(); __boltffi_size } } ?: 0)
    }

    internal fun writeTo(writer: WireWriter) {
        writer.writeOptionalValue(this.dictList, { writer, __boltffi_value_0 -> writer.writeSequence(__boltffi_value_0, __boltffi_value_0.size, { writer, __boltffi_value_1 -> __boltffi_value_1.writeTo(writer) }) })
    }

    internal fun toByteArray(): ByteArray {
        val buffer = WireWriterPool.acquire(wireSize())
        val writer = buffer.writer
        try {
            writeTo(writer)
            return buffer.bytes()
        } finally {
            buffer.close()
        }
    }

    companion object {
        internal fun fromReader(reader: WireReader): AiDictionaryResponse {
            return AiDictionaryResponse(
                reader.readOptionalValue({ reader -> reader.readSequence({ reader -> AiDictionaryItem.fromReader(reader) }) })
            )
        }

        internal fun fromByteArray(bytes: ByteArray): AiDictionaryResponse {
            val reader = WireReader(bytes)
            return fromReader(reader)
        }
    }
}


/**
 * 喜欢歌曲目录的创建者.
 */
data class LikedSongsCreator(
    val musicid: Long?,
    val nick: String?,
    val headurl: String?,
    val encryptUin: String?
) {
    internal fun wireSize(): Int {
        return 1 + (this.musicid?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.nick?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.headurl?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.encryptUin?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0)
    }

    internal fun writeTo(writer: WireWriter) {
        writer.writeOptionalValue(this.musicid, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.nick, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.headurl, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.encryptUin, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
    }

    internal fun toByteArray(): ByteArray {
        val buffer = WireWriterPool.acquire(wireSize())
        val writer = buffer.writer
        try {
            writeTo(writer)
            return buffer.bytes()
        } finally {
            buffer.close()
        }
    }

    companion object {
        internal fun fromReader(reader: WireReader): LikedSongsCreator {
            return LikedSongsCreator(
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() })
            )
        }

        internal fun fromByteArray(bytes: ByteArray): LikedSongsCreator {
            val reader = WireReader(bytes)
            return fromReader(reader)
        }
    }
}


/**
 * 喜欢歌曲的目录元数据, 对应参考 SonglistInfo.
 */
data class LikedSongsInfo(
    val id: Long?,
    val dirid: Long?,
    val title: String?,
    val picurl: String?,
    val desc: String?,
    val songnum: Long?,
    val listennum: Long?,
    val creator: LikedSongsCreator?
) {
    internal fun wireSize(): Int {
        return 1 + (this.id?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.dirid?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.title?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.picurl?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.desc?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.songnum?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.listennum?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.creator?.let { __boltffi_value_0 -> __boltffi_value_0.wireSize() } ?: 0)
    }

    internal fun writeTo(writer: WireWriter) {
        writer.writeOptionalValue(this.id, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.dirid, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.title, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.picurl, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.desc, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.songnum, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.listennum, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.creator, { writer, __boltffi_value_0 -> __boltffi_value_0.writeTo(writer) })
    }

    internal fun toByteArray(): ByteArray {
        val buffer = WireWriterPool.acquire(wireSize())
        val writer = buffer.writer
        try {
            writeTo(writer)
            return buffer.bytes()
        } finally {
            buffer.close()
        }
    }

    companion object {
        internal fun fromReader(reader: WireReader): LikedSongsInfo {
            return LikedSongsInfo(
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> LikedSongsCreator.fromReader(reader) })
            )
        }

        internal fun fromByteArray(bytes: ByteArray): LikedSongsInfo {
            val reader = WireReader(bytes)
            return fromReader(reader)
        }
    }
}


/**
 * 指定用户喜欢的歌曲一页, 歌曲沿用组件既有 Track 模型.
 */
data class UserLikedSongsResponse(
    val code: Long?,
    val subcode: Long?,
    val msg: String?,
    val info: LikedSongsInfo?,
    val size: Long?,
    val songs: List<Track>?,
    val total: Long?,
    val hasmore: Long?
) {
    internal fun wireSize(): Int {
        return 1 + (this.code?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.subcode?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.msg?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.info?.let { __boltffi_value_0 -> __boltffi_value_0.wireSize() } ?: 0) + 1 + (this.size?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.songs?.let { __boltffi_value_0 -> 4 + __boltffi_value_0.sumOf { __boltffi_value_1 -> val __boltffi_size: kotlin.Int = __boltffi_value_1.wireSize(); __boltffi_size } } ?: 0) + 1 + (this.total?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.hasmore?.let { __boltffi_value_0 -> 8 } ?: 0)
    }

    internal fun writeTo(writer: WireWriter) {
        writer.writeOptionalValue(this.code, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.subcode, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.msg, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.info, { writer, __boltffi_value_0 -> __boltffi_value_0.writeTo(writer) })
        writer.writeOptionalValue(this.size, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.songs, { writer, __boltffi_value_0 -> writer.writeSequence(__boltffi_value_0, __boltffi_value_0.size, { writer, __boltffi_value_1 -> __boltffi_value_1.writeTo(writer) }) })
        writer.writeOptionalValue(this.total, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.hasmore, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
    }

    internal fun toByteArray(): ByteArray {
        val buffer = WireWriterPool.acquire(wireSize())
        val writer = buffer.writer
        try {
            writeTo(writer)
            return buffer.bytes()
        } finally {
            buffer.close()
        }
    }

    companion object {
        internal fun fromReader(reader: WireReader): UserLikedSongsResponse {
            return UserLikedSongsResponse(
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> LikedSongsInfo.fromReader(reader) }),
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readSequence({ reader -> Track.fromReader(reader) }) }),
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readI64() })
            )
        }

        internal fun fromByteArray(bytes: ByteArray): UserLikedSongsResponse {
            val reader = WireReader(bytes)
            return fromReader(reader)
        }
    }
}


/**
 * 参考 `PhoneAuthCodeResult`：发验证码的结果。
 *
 * `event` 取参考枚举名（`SEND` / `CAPTCHA` / `FREQUENCY`）；`CAPTCHA` 时
 * `info` 是滑块验证地址（参考读 `data.securityURL`）。
 */
data class PhoneAuthCodeResult(
    val event: String,
    val info: String?
) {
    internal fun wireSize(): Int {
        return 4 + Utf8Codec.maxBytes(this.event) + 1 + (this.info?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0)
    }

    internal fun writeTo(writer: WireWriter) {
        writer.writeString(this.event)
        writer.writeOptionalValue(this.info, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
    }

    internal fun toByteArray(): ByteArray {
        val buffer = WireWriterPool.acquire(wireSize())
        val writer = buffer.writer
        try {
            writeTo(writer)
            return buffer.bytes()
        } finally {
            buffer.close()
        }
    }

    companion object {
        internal fun fromReader(reader: WireReader): PhoneAuthCodeResult {
            return PhoneAuthCodeResult(
                reader.readString(),
                reader.readOptionalValue({ reader -> reader.readString() })
            )
        }

        internal fun fromByteArray(bytes: ByteArray): PhoneAuthCodeResult {
            val reader = WireReader(bytes)
            return fromReader(reader)
        }
    }
}


/**
 * 参考 `MvDetail`（继承 `MV`）：详情接口的单个视频条目.
 *
 * `singers` 在参考里是 `list[dict[str, Any]]`（原样透传的上游形状），这里存
 * JSON 文本：`#[data]` 认不出 `serde_json::Value`，而经 serde 进出时它仍是
 * 真正的对象数组（见 `raw_json_serialize`）。
 */
data class MvDetail(
    /**
     * MV 数字 ID（参考 `MV.id`，别名 `sid`/`mvid`/`singerId`）.
     */
    val id: Long?,
    /**
     * MV VID，请求详情/播放地址的把手.
     */
    val vid: String?,
    /**
     * MV 类型（参考 `MV.type`，别名 `vt`；Rust 侧换个名字、JSON 上仍是 `type`）.
     */
    val kind: Long?,
    /**
     * MV 名称（参考 `MV.name`，别名 `mvname`/`title`）.
     */
    val name: String?,
    /**
     * MV 展示标题（参考 `MV.title`，别名 `title_main`/`name`）.
     */
    val title: String?,
    /**
     * 封面地址.
     */
    val coverPic: String?,
    /**
     * MV 时长.
     */
    val duration: Long?,
    /**
     * MV 歌手列表（参考声明为 dict 列表，原样透传）.
     */
    val singers: String?,
    /**
     * MV 开关位.
     */
    val videoSwitch: Long?,
    /**
     * 附加消息.
     */
    val msg: String?,
    /**
     * MV 描述.
     */
    val desc: String?,
    /**
     * MV 播放量.
     */
    val playcnt: Long?,
    /**
     * 发布时间戳.
     */
    val pubdate: Long?,
    /**
     * 是否已收藏.
     */
    val isfav: Long?,
    /**
     * 全局媒体标识.
     */
    val gmid: String?,
    /**
     * 上传者头像.
     */
    val uploaderHeadurl: String?,
    /**
     * 上传者昵称.
     */
    val uploaderNick: String?,
    /**
     * 上传者加密 UIN.
     */
    val uploaderEncuin: String?,
    /**
     * 上传者 UIN.
     */
    val uploaderUin: String?,
    /**
     * 是否已关注上传者.
     */
    val uploaderHasfollow: Long?,
    /**
     * 上传者粉丝数.
     */
    val uploaderFollowerNum: Long?,
    /**
     * 关联歌曲 ID 列表.
     */
    val relatedSongs: LongArray?
) {
    internal fun wireSize(): Int {
        return 1 + (this.id?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.vid?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.kind?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.name?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.title?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.coverPic?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.duration?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.singers?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.videoSwitch?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.msg?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.desc?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.playcnt?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.pubdate?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.isfav?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.gmid?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.uploaderHeadurl?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.uploaderNick?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.uploaderEncuin?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.uploaderUin?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.uploaderHasfollow?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.uploaderFollowerNum?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.relatedSongs?.let { __boltffi_value_0 -> 4 + __boltffi_value_0.size * 8 } ?: 0)
    }

    internal fun writeTo(writer: WireWriter) {
        writer.writeOptionalValue(this.id, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.vid, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.kind, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.name, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.title, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.coverPic, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.duration, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.singers, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.videoSwitch, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.msg, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.desc, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.playcnt, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.pubdate, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.isfav, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.gmid, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.uploaderHeadurl, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.uploaderNick, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.uploaderEncuin, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.uploaderUin, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.uploaderHasfollow, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.uploaderFollowerNum, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.relatedSongs, { writer, __boltffi_value_0 -> writer.writeLongArray(__boltffi_value_0) })
    }

    internal fun toByteArray(): ByteArray {
        val buffer = WireWriterPool.acquire(wireSize())
        val writer = buffer.writer
        try {
            writeTo(writer)
            return buffer.bytes()
        } finally {
            buffer.close()
        }
    }

    companion object {
        internal fun fromReader(reader: WireReader): MvDetail {
            return MvDetail(
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readLongArray() })
            )
        }

        internal fun fromByteArray(bytes: ByteArray): MvDetail {
            val reader = WireReader(bytes)
            return fromReader(reader)
        }
    }
}


/**
 * 参考 `GetMvDetailResponse`：以 VID 为键的 MV 详情映射.
 */
data class MvDetailResponse(
    val `data`: Map<String, MvDetail>?
) {
    internal fun wireSize(): Int {
        return 1 + (this.`data`?.let { __boltffi_value_0 -> __boltffi_value_0.wireSize({ __boltffi_value_1 -> 4 + Utf8Codec.maxBytes(__boltffi_value_1) }, { __boltffi_value_2 -> __boltffi_value_2.wireSize() }) } ?: 0)
    }

    internal fun writeTo(writer: WireWriter) {
        writer.writeOptionalValue(this.`data`, { writer, __boltffi_value_0 -> writer.writeMap(__boltffi_value_0, { writer, __boltffi_value_1 -> writer.writeString(__boltffi_value_1) }, { writer, __boltffi_value_2 -> __boltffi_value_2.writeTo(writer) }) })
    }

    internal fun toByteArray(): ByteArray {
        val buffer = WireWriterPool.acquire(wireSize())
        val writer = buffer.writer
        try {
            writeTo(writer)
            return buffer.bytes()
        } finally {
            buffer.close()
        }
    }

    companion object {
        internal fun fromReader(reader: WireReader): MvDetailResponse {
            return MvDetailResponse(
                reader.readOptionalValue({ reader -> reader.readMap({ reader -> reader.readString() }, { reader -> MvDetail.fromReader(reader) }) })
            )
        }

        internal fun fromByteArray(bytes: ByteArray): MvDetailResponse {
            val reader = WireReader(bytes)
            return fromReader(reader)
        }
    }
}


/**
 * 参考 `MvUrlItem`：单一路径规格下的 MV 播放地址信息.
 */
data class MvUrlItem(
    /**
     * 直连地址列表.
     */
    val url: List<String>?,
    /**
     * 免流地址列表.
     */
    val freeflowUrl: List<String>?,
    /**
     * 通用地址列表.
     */
    val commUrl: List<String>?,
    /**
     * 文件名.
     */
    val cn: String?,
    /**
     * 播放令牌.
     */
    val vkey: String?,
    /**
     * 过期时间.
     */
    val expire: Long?,
    /**
     * 结果码.
     */
    val code: Long?,
    /**
     * 文件类型.
     */
    val filetype: Long?,
    /**
     * m3u8 地址.
     */
    val m3u8: String?,
    /**
     * 新文件类型标识（参考字段名 `new_file_type`，上游键就是 `newFileType`）.
     */
    val newFileType: Long?,
    /**
     * 编码格式.
     */
    val format: Long?,
    /**
     * 文件大小（参考字段名 `file_size`，上游键就是 `fileSize`）.
     */
    val fileSize: Long?
) {
    internal fun wireSize(): Int {
        return 1 + (this.url?.let { __boltffi_value_0 -> 4 + __boltffi_value_0.sumOf { __boltffi_value_1 -> val __boltffi_size: kotlin.Int = 4 + Utf8Codec.maxBytes(__boltffi_value_1); __boltffi_size } } ?: 0) + 1 + (this.freeflowUrl?.let { __boltffi_value_0 -> 4 + __boltffi_value_0.sumOf { __boltffi_value_1 -> val __boltffi_size: kotlin.Int = 4 + Utf8Codec.maxBytes(__boltffi_value_1); __boltffi_size } } ?: 0) + 1 + (this.commUrl?.let { __boltffi_value_0 -> 4 + __boltffi_value_0.sumOf { __boltffi_value_1 -> val __boltffi_size: kotlin.Int = 4 + Utf8Codec.maxBytes(__boltffi_value_1); __boltffi_size } } ?: 0) + 1 + (this.cn?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.vkey?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.expire?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.code?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.filetype?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.m3u8?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.newFileType?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.format?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.fileSize?.let { __boltffi_value_0 -> 8 } ?: 0)
    }

    internal fun writeTo(writer: WireWriter) {
        writer.writeOptionalValue(this.url, { writer, __boltffi_value_0 -> writer.writeSequence(__boltffi_value_0, __boltffi_value_0.size, { writer, __boltffi_value_1 -> writer.writeString(__boltffi_value_1) }) })
        writer.writeOptionalValue(this.freeflowUrl, { writer, __boltffi_value_0 -> writer.writeSequence(__boltffi_value_0, __boltffi_value_0.size, { writer, __boltffi_value_1 -> writer.writeString(__boltffi_value_1) }) })
        writer.writeOptionalValue(this.commUrl, { writer, __boltffi_value_0 -> writer.writeSequence(__boltffi_value_0, __boltffi_value_0.size, { writer, __boltffi_value_1 -> writer.writeString(__boltffi_value_1) }) })
        writer.writeOptionalValue(this.cn, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.vkey, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.expire, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.code, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.filetype, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.m3u8, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.newFileType, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.format, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.fileSize, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
    }

    internal fun toByteArray(): ByteArray {
        val buffer = WireWriterPool.acquire(wireSize())
        val writer = buffer.writer
        try {
            writeTo(writer)
            return buffer.bytes()
        } finally {
            buffer.close()
        }
    }

    companion object {
        internal fun fromReader(reader: WireReader): MvUrlItem {
            return MvUrlItem(
                reader.readOptionalValue({ reader -> reader.readSequence({ reader -> reader.readString() }) }),
                reader.readOptionalValue({ reader -> reader.readSequence({ reader -> reader.readString() }) }),
                reader.readOptionalValue({ reader -> reader.readSequence({ reader -> reader.readString() }) }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readI64() })
            )
        }

        internal fun fromByteArray(bytes: ByteArray): MvUrlItem {
            val reader = WireReader(bytes)
            return fromReader(reader)
        }
    }
}


/**
 * 参考 `MvUrlSet`：同一 MV 在不同协议下的播放地址集合.
 */
data class MvUrlSet(
    /**
     * MP4 地址列表.
     */
    val mp4: List<MvUrlItem>?,
    /**
     * HLS 地址列表.
     */
    val hls: List<MvUrlItem>?,
    /**
     * 是否支持超清能力标记.
     */
    val svpFlag: Long?,
    /**
     * MV 时长.
     */
    val duration: Long?
) {
    internal fun wireSize(): Int {
        return 1 + (this.mp4?.let { __boltffi_value_0 -> 4 + __boltffi_value_0.sumOf { __boltffi_value_1 -> val __boltffi_size: kotlin.Int = __boltffi_value_1.wireSize(); __boltffi_size } } ?: 0) + 1 + (this.hls?.let { __boltffi_value_0 -> 4 + __boltffi_value_0.sumOf { __boltffi_value_1 -> val __boltffi_size: kotlin.Int = __boltffi_value_1.wireSize(); __boltffi_size } } ?: 0) + 1 + (this.svpFlag?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.duration?.let { __boltffi_value_0 -> 8 } ?: 0)
    }

    internal fun writeTo(writer: WireWriter) {
        writer.writeOptionalValue(this.mp4, { writer, __boltffi_value_0 -> writer.writeSequence(__boltffi_value_0, __boltffi_value_0.size, { writer, __boltffi_value_1 -> __boltffi_value_1.writeTo(writer) }) })
        writer.writeOptionalValue(this.hls, { writer, __boltffi_value_0 -> writer.writeSequence(__boltffi_value_0, __boltffi_value_0.size, { writer, __boltffi_value_1 -> __boltffi_value_1.writeTo(writer) }) })
        writer.writeOptionalValue(this.svpFlag, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.duration, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
    }

    internal fun toByteArray(): ByteArray {
        val buffer = WireWriterPool.acquire(wireSize())
        val writer = buffer.writer
        try {
            writeTo(writer)
            return buffer.bytes()
        } finally {
            buffer.close()
        }
    }

    companion object {
        internal fun fromReader(reader: WireReader): MvUrlSet {
            return MvUrlSet(
                reader.readOptionalValue({ reader -> reader.readSequence({ reader -> MvUrlItem.fromReader(reader) }) }),
                reader.readOptionalValue({ reader -> reader.readSequence({ reader -> MvUrlItem.fromReader(reader) }) }),
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readI64() })
            )
        }

        internal fun fromByteArray(bytes: ByteArray): MvUrlSet {
            val reader = WireReader(bytes)
            return fromReader(reader)
        }
    }
}


/**
 * 参考 `GetMvUrlsResponse`：以 MV 标识分组的播放地址集合.
 */
data class MvUrlResponse(
    val `data`: Map<String, MvUrlSet>?
) {
    internal fun wireSize(): Int {
        return 1 + (this.`data`?.let { __boltffi_value_0 -> __boltffi_value_0.wireSize({ __boltffi_value_1 -> 4 + Utf8Codec.maxBytes(__boltffi_value_1) }, { __boltffi_value_2 -> __boltffi_value_2.wireSize() }) } ?: 0)
    }

    internal fun writeTo(writer: WireWriter) {
        writer.writeOptionalValue(this.`data`, { writer, __boltffi_value_0 -> writer.writeMap(__boltffi_value_0, { writer, __boltffi_value_1 -> writer.writeString(__boltffi_value_1) }, { writer, __boltffi_value_2 -> __boltffi_value_2.writeTo(writer) }) })
    }

    internal fun toByteArray(): ByteArray {
        val buffer = WireWriterPool.acquire(wireSize())
        val writer = buffer.writer
        try {
            writeTo(writer)
            return buffer.bytes()
        } finally {
            buffer.close()
        }
    }

    companion object {
        internal fun fromReader(reader: WireReader): MvUrlResponse {
            return MvUrlResponse(
                reader.readOptionalValue({ reader -> reader.readMap({ reader -> reader.readString() }, { reader -> MvUrlSet.fromReader(reader) }) })
            )
        }

        internal fun fromByteArray(bytes: ByteArray): MvUrlResponse {
            val reader = WireReader(bytes)
            return fromReader(reader)
        }
    }
}


/**
 * 参考 `MvListItem`（继承 `MV`）：MV 分类列表中的单个 MV 摘要.
 */
data class MvListItem(
    val id: Long?,
    val vid: String?,
    val kind: Long?,
    val name: String?,
    val title: String?,
    /**
     * 歌手列表（参考用基础模型 `Singer`，这里复用组件既有的同名模型）.
     */
    val singers: List<Singer>?,
    /**
     * MV 副标题.
     */
    val subtitle: String?,
    /**
     * 播放量.
     */
    val playcnt: Long?,
    /**
     * 发布时间戳.
     */
    val pubdate: Long?,
    /**
     * 时长（秒）.
     */
    val duration: Long?,
    /**
     * 封面图片地址.
     */
    val picurl: String?
) {
    internal fun wireSize(): Int {
        return 1 + (this.id?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.vid?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.kind?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.name?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.title?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.singers?.let { __boltffi_value_0 -> 4 + __boltffi_value_0.sumOf { __boltffi_value_1 -> val __boltffi_size: kotlin.Int = __boltffi_value_1.wireSize(); __boltffi_size } } ?: 0) + 1 + (this.subtitle?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.playcnt?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.pubdate?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.duration?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.picurl?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0)
    }

    internal fun writeTo(writer: WireWriter) {
        writer.writeOptionalValue(this.id, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.vid, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.kind, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.name, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.title, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.singers, { writer, __boltffi_value_0 -> writer.writeSequence(__boltffi_value_0, __boltffi_value_0.size, { writer, __boltffi_value_1 -> __boltffi_value_1.writeTo(writer) }) })
        writer.writeOptionalValue(this.subtitle, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.playcnt, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.pubdate, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.duration, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.picurl, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
    }

    internal fun toByteArray(): ByteArray {
        val buffer = WireWriterPool.acquire(wireSize())
        val writer = buffer.writer
        try {
            writeTo(writer)
            return buffer.bytes()
        } finally {
            buffer.close()
        }
    }

    companion object {
        internal fun fromReader(reader: WireReader): MvListItem {
            return MvListItem(
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readSequence({ reader -> Singer.fromReader(reader) }) }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readString() })
            )
        }

        internal fun fromByteArray(bytes: ByteArray): MvListItem {
            val reader = WireReader(bytes)
            return fromReader(reader)
        }
    }
}


/**
 * 参考 `GetMvListResponse`：MV 分类列表接口的响应体.
 */
data class MvListResponse(
    /**
     * 该分类条件下的 MV 总数.
     */
    val total: Long?,
    /**
     * 当前页 MV 列表（参考模型字段名 `items`，上游键是 `list`）.
     */
    val items: List<MvListItem>?
) {
    internal fun wireSize(): Int {
        return 1 + (this.total?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.items?.let { __boltffi_value_0 -> 4 + __boltffi_value_0.sumOf { __boltffi_value_1 -> val __boltffi_size: kotlin.Int = __boltffi_value_1.wireSize(); __boltffi_size } } ?: 0)
    }

    internal fun writeTo(writer: WireWriter) {
        writer.writeOptionalValue(this.total, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.items, { writer, __boltffi_value_0 -> writer.writeSequence(__boltffi_value_0, __boltffi_value_0.size, { writer, __boltffi_value_1 -> __boltffi_value_1.writeTo(writer) }) })
    }

    internal fun toByteArray(): ByteArray {
        val buffer = WireWriterPool.acquire(wireSize())
        val writer = buffer.writer
        try {
            writeTo(writer)
            return buffer.bytes()
        } finally {
            buffer.close()
        }
    }

    companion object {
        internal fun fromReader(reader: WireReader): MvListResponse {
            return MvListResponse(
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readSequence({ reader -> MvListItem.fromReader(reader) }) })
            )
        }

        internal fun fromByteArray(bytes: ByteArray): MvListResponse {
            val reader = WireReader(bytes)
            return fromReader(reader)
        }
    }
}


/**
 * 参考 `RecommendNiche`：首页推荐楼层中的细分卡片分组.
 */
data class RecommendNiche(
    /**
     * 细分分组 ID.
     */
    val id: Long?,
    /**
     * 标题模板.
     */
    val titleTemplate: String?,
    /**
     * 标题实际展示内容.
     */
    val titleContent: String?,
    /**
     * 原始卡片列表（参考声明为 `list[dict]`，原样透传）.
     */
    val cards: String?
) {
    internal fun wireSize(): Int {
        return 1 + (this.id?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.titleTemplate?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.titleContent?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.cards?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0)
    }

    internal fun writeTo(writer: WireWriter) {
        writer.writeOptionalValue(this.id, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.titleTemplate, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.titleContent, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.cards, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
    }

    internal fun toByteArray(): ByteArray {
        val buffer = WireWriterPool.acquire(wireSize())
        val writer = buffer.writer
        try {
            writeTo(writer)
            return buffer.bytes()
        } finally {
            buffer.close()
        }
    }

    companion object {
        internal fun fromReader(reader: WireReader): RecommendNiche {
            return RecommendNiche(
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() })
            )
        }

        internal fun fromByteArray(bytes: ByteArray): RecommendNiche {
            val reader = WireReader(bytes)
            return fromReader(reader)
        }
    }
}


/**
 * 参考 `RecommendShelf`：首页推荐页中的单个楼层.
 */
data class RecommendShelf(
    /**
     * 楼层 ID.
     */
    val id: Long?,
    /**
     * 楼层标题模板.
     */
    val titleTemplate: String?,
    /**
     * 楼层标题实际展示内容.
     */
    val titleContent: String?,
    /**
     * 更多入口信息（参考声明为 `dict`，原样透传）.
     */
    val more: String?,
    /**
     * 楼层下属的细分分组列表.
     */
    val niches: List<RecommendNiche>?
) {
    internal fun wireSize(): Int {
        return 1 + (this.id?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.titleTemplate?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.titleContent?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.more?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.niches?.let { __boltffi_value_0 -> 4 + __boltffi_value_0.sumOf { __boltffi_value_1 -> val __boltffi_size: kotlin.Int = __boltffi_value_1.wireSize(); __boltffi_size } } ?: 0)
    }

    internal fun writeTo(writer: WireWriter) {
        writer.writeOptionalValue(this.id, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.titleTemplate, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.titleContent, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.more, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.niches, { writer, __boltffi_value_0 -> writer.writeSequence(__boltffi_value_0, __boltffi_value_0.size, { writer, __boltffi_value_1 -> __boltffi_value_1.writeTo(writer) }) })
    }

    internal fun toByteArray(): ByteArray {
        val buffer = WireWriterPool.acquire(wireSize())
        val writer = buffer.writer
        try {
            writeTo(writer)
            return buffer.bytes()
        } finally {
            buffer.close()
        }
    }

    companion object {
        internal fun fromReader(reader: WireReader): RecommendShelf {
            return RecommendShelf(
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readSequence({ reader -> RecommendNiche.fromReader(reader) }) })
            )
        }

        internal fun fromByteArray(bytes: ByteArray): RecommendShelf {
            val reader = WireReader(bytes)
            return fromReader(reader)
        }
    }
}


/**
 * 参考 `RecommendFeedCardResponse`：首页推荐首屏响应.
 */
data class RecommendFeedCardResponse(
    /**
     * 接口返回码.
     */
    val retcode: Long?,
    /**
     * 附加消息.
     */
    val msg: String?,
    /**
     * 提示信息.
     */
    val prompt: String?,
    /**
     * 分页或批次计数信息（参考字段是 snake_case，输出 `dNum`）.
     */
    val dNum: Long?,
    /**
     * 继续加载标记（参考字段是 snake_case，输出 `loadMark`）.
     */
    val loadMark: Long?,
    /**
     * 首页推荐楼层列表.
     */
    val shelves: List<RecommendShelf>?
) {
    internal fun wireSize(): Int {
        return 1 + (this.retcode?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.msg?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.prompt?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.dNum?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.loadMark?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.shelves?.let { __boltffi_value_0 -> 4 + __boltffi_value_0.sumOf { __boltffi_value_1 -> val __boltffi_size: kotlin.Int = __boltffi_value_1.wireSize(); __boltffi_size } } ?: 0)
    }

    internal fun writeTo(writer: WireWriter) {
        writer.writeOptionalValue(this.retcode, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.msg, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.prompt, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.dNum, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.loadMark, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.shelves, { writer, __boltffi_value_0 -> writer.writeSequence(__boltffi_value_0, __boltffi_value_0.size, { writer, __boltffi_value_1 -> __boltffi_value_1.writeTo(writer) }) })
    }

    internal fun toByteArray(): ByteArray {
        val buffer = WireWriterPool.acquire(wireSize())
        val writer = buffer.writer
        try {
            writeTo(writer)
            return buffer.bytes()
        } finally {
            buffer.close()
        }
    }

    companion object {
        internal fun fromReader(reader: WireReader): RecommendFeedCardResponse {
            return RecommendFeedCardResponse(
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readSequence({ reader -> RecommendShelf.fromReader(reader) }) })
            )
        }

        internal fun fromByteArray(bytes: ByteArray): RecommendFeedCardResponse {
            val reader = WireReader(bytes)
            return fromReader(reader)
        }
    }
}


/**
 * 参考 `RadarRecommendResponse`：雷达推荐响应.
 */
data class RadarRecommendResponse(
    /**
     * 推荐歌曲列表（参考的 jsonpath 是 `$.VecSongs[*].Track`）.
     */
    val songs: List<Track>?,
    /**
     * 推荐歌曲 ID 列表.
     */
    val recommendSongIds: LongArray?,
    /**
     * 作为推荐依据的基础歌曲 ID 列表.
     */
    val baseSongIds: LongArray?,
    /**
     * 是否还能继续获取更多推荐（翻页依据）.
     */
    val hasMore: Boolean?,
    /**
     * 提示信息块或提示文案.
     */
    val toast: String?,
    /**
     * 服务端时间戳.
     */
    val timestamp: Long?,
    /**
     * 关联视频卡片数据（参考声明为 `dict`，原样透传）.
     */
    val videoCards: String?
) {
    internal fun wireSize(): Int {
        return 1 + (this.songs?.let { __boltffi_value_0 -> 4 + __boltffi_value_0.sumOf { __boltffi_value_1 -> val __boltffi_size: kotlin.Int = __boltffi_value_1.wireSize(); __boltffi_size } } ?: 0) + 1 + (this.recommendSongIds?.let { __boltffi_value_0 -> 4 + __boltffi_value_0.size * 8 } ?: 0) + 1 + (this.baseSongIds?.let { __boltffi_value_0 -> 4 + __boltffi_value_0.size * 8 } ?: 0) + 1 + (this.hasMore?.let { __boltffi_value_0 -> 1 } ?: 0) + 1 + (this.toast?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.timestamp?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.videoCards?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0)
    }

    internal fun writeTo(writer: WireWriter) {
        writer.writeOptionalValue(this.songs, { writer, __boltffi_value_0 -> writer.writeSequence(__boltffi_value_0, __boltffi_value_0.size, { writer, __boltffi_value_1 -> __boltffi_value_1.writeTo(writer) }) })
        writer.writeOptionalValue(this.recommendSongIds, { writer, __boltffi_value_0 -> writer.writeLongArray(__boltffi_value_0) })
        writer.writeOptionalValue(this.baseSongIds, { writer, __boltffi_value_0 -> writer.writeLongArray(__boltffi_value_0) })
        writer.writeOptionalValue(this.hasMore, { writer, __boltffi_value_0 -> writer.writeBool(__boltffi_value_0) })
        writer.writeOptionalValue(this.toast, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.timestamp, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.videoCards, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
    }

    internal fun toByteArray(): ByteArray {
        val buffer = WireWriterPool.acquire(wireSize())
        val writer = buffer.writer
        try {
            writeTo(writer)
            return buffer.bytes()
        } finally {
            buffer.close()
        }
    }

    companion object {
        internal fun fromReader(reader: WireReader): RadarRecommendResponse {
            return RadarRecommendResponse(
                reader.readOptionalValue({ reader -> reader.readSequence({ reader -> Track.fromReader(reader) }) }),
                reader.readOptionalValue({ reader -> reader.readLongArray() }),
                reader.readOptionalValue({ reader -> reader.readLongArray() }),
                reader.readOptionalValue({ reader -> reader.readBool() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readString() })
            )
        }

        internal fun fromByteArray(bytes: ByteArray): RadarRecommendResponse {
            val reader = WireReader(bytes)
            return fromReader(reader)
        }
    }
}


/**
 * 参考 `RecommendSonglistItem`（继承基础模型 `SongList`）：推荐歌单里的单个条目.
 */
data class RecommendSonglistItem(
    /**
     * 歌单 ID（参考 `SongList.id`，别名 `tid`/`dissid`）.
     */
    val id: Long?,
    /**
     * 目录 ID.
     */
    val dirid: Long?,
    /**
     * 歌单标题.
     */
    val title: String?,
    /**
     * 歌单封面地址（参考的 jsonpath 是 `$.cover.default_url`）.
     */
    val picurl: String?,
    /**
     * 歌单简介.
     */
    val desc: String?,
    /**
     * 歌单歌曲数量（参考别名 `song_cnt`/`songnum`/`songNum`）.
     */
    val songnum: Long?,
    /**
     * 歌单播放量（参考别名 `play_cnt`/`listennum`/`playCnt`）.
     */
    val listennum: Long?,
    /**
     * 创建者昵称（参考的 jsonpath 是 `$.creator.nick`）.
     */
    val creatorNick: String?
) {
    internal fun wireSize(): Int {
        return 1 + (this.id?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.dirid?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.title?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.picurl?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.desc?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.songnum?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.listennum?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.creatorNick?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0)
    }

    internal fun writeTo(writer: WireWriter) {
        writer.writeOptionalValue(this.id, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.dirid, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.title, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.picurl, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.desc, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.songnum, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.listennum, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.creatorNick, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
    }

    internal fun toByteArray(): ByteArray {
        val buffer = WireWriterPool.acquire(wireSize())
        val writer = buffer.writer
        try {
            writeTo(writer)
            return buffer.bytes()
        } finally {
            buffer.close()
        }
    }

    companion object {
        internal fun fromReader(reader: WireReader): RecommendSonglistItem {
            return RecommendSonglistItem(
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readString() })
            )
        }

        internal fun fromByteArray(bytes: ByteArray): RecommendSonglistItem {
            val reader = WireReader(bytes)
            return fromReader(reader)
        }
    }
}


/**
 * 参考 `RecommendSonglistResponse`：推荐歌单分页响应.
 */
data class RecommendSonglistResponse(
    /**
     * 当前批次推荐歌单列表（参考的 jsonpath 是 `$.List[*].Playlist.basic`）.
     */
    val songlists: List<RecommendSonglistItem>?,
    /**
     * 是否还能继续拉取更多歌单.
     */
    val hasMore: Boolean?,
    /**
     * 当前批次对应的偏移（下一次请求的 `From` 就用它）.
     */
    val fromLimit: Long?,
    /**
     * 附加消息.
     */
    val msg: String?
) {
    internal fun wireSize(): Int {
        return 1 + (this.songlists?.let { __boltffi_value_0 -> 4 + __boltffi_value_0.sumOf { __boltffi_value_1 -> val __boltffi_size: kotlin.Int = __boltffi_value_1.wireSize(); __boltffi_size } } ?: 0) + 1 + (this.hasMore?.let { __boltffi_value_0 -> 1 } ?: 0) + 1 + (this.fromLimit?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.msg?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0)
    }

    internal fun writeTo(writer: WireWriter) {
        writer.writeOptionalValue(this.songlists, { writer, __boltffi_value_0 -> writer.writeSequence(__boltffi_value_0, __boltffi_value_0.size, { writer, __boltffi_value_1 -> __boltffi_value_1.writeTo(writer) }) })
        writer.writeOptionalValue(this.hasMore, { writer, __boltffi_value_0 -> writer.writeBool(__boltffi_value_0) })
        writer.writeOptionalValue(this.fromLimit, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.msg, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
    }

    internal fun toByteArray(): ByteArray {
        val buffer = WireWriterPool.acquire(wireSize())
        val writer = buffer.writer
        try {
            writeTo(writer)
            return buffer.bytes()
        } finally {
            buffer.close()
        }
    }

    companion object {
        internal fun fromReader(reader: WireReader): RecommendSonglistResponse {
            return RecommendSonglistResponse(
                reader.readOptionalValue({ reader -> reader.readSequence({ reader -> RecommendSonglistItem.fromReader(reader) }) }),
                reader.readOptionalValue({ reader -> reader.readBool() }),
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readString() })
            )
        }

        internal fun fromByteArray(bytes: ByteArray): RecommendSonglistResponse {
            val reader = WireReader(bytes)
            return fromReader(reader)
        }
    }
}


/**
 * 参考 `Hotkey`：热搜词条目.
 */
data class Hotkey(
    /**
     * 热搜词唯一标识.
     */
    val hotkeyId: String?,
    /**
     * 热搜关键词.
     */
    val query: String?,
    /**
     * 热搜展示标题.
     */
    val title: String?,
    /**
     * 热度得分.
     */
    val score: String?,
    /**
     * 条目类别（上游有时给数字字符串，pydantic 会转成 int）.
     */
    val kind: Long?,
    /**
     * 条目类型（参考字段名 `type`，Rust 侧换个名字、JSON 上仍是 `type`）.
     */
    val typeId: Long?,
    /**
     * 数据来源.
     */
    val source: Long?,
    /**
     * 是否置顶.
     */
    val needTop: Long?,
    /**
     * 排序位置.
     */
    val subpos: Long?,
    /**
     * 关联歌曲类型.
     */
    val songType: Long?,
    /**
     * 直接关联的歌曲 ID.
     */
    val directId: Long?,
    /**
     * 跳转标签页（参考缺省 `"0"`）.
     */
    val jumpTab: String?,
    /**
     * 跳转链接.
     */
    val jumpUrl: String?,
    /**
     * 封面图片地址.
     */
    val coverPicUrl: String?,
    /**
     * 图片地址.
     */
    val picUrl: String?,
    /**
     * 描述文案.
     */
    val description: String?
) {
    internal fun wireSize(): Int {
        return 1 + (this.hotkeyId?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.query?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.title?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.score?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.kind?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.typeId?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.source?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.needTop?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.subpos?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.songType?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.directId?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.jumpTab?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.jumpUrl?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.coverPicUrl?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.picUrl?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.description?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0)
    }

    internal fun writeTo(writer: WireWriter) {
        writer.writeOptionalValue(this.hotkeyId, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.query, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.title, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.score, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.kind, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.typeId, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.source, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.needTop, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.subpos, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.songType, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.directId, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.jumpTab, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.jumpUrl, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.coverPicUrl, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.picUrl, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.description, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
    }

    internal fun toByteArray(): ByteArray {
        val buffer = WireWriterPool.acquire(wireSize())
        val writer = buffer.writer
        try {
            writeTo(writer)
            return buffer.bytes()
        } finally {
            buffer.close()
        }
    }

    companion object {
        internal fun fromReader(reader: WireReader): Hotkey {
            return Hotkey(
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() })
            )
        }

        internal fun fromByteArray(bytes: ByteArray): Hotkey {
            val reader = WireReader(bytes)
            return fromReader(reader)
        }
    }
}


/**
 * 参考 `HotkeyResponse`：热搜词列表响应.
 */
data class HotkeyResponse(
    /**
     * 返回码.
     */
    val retCode: Long?,
    /**
     * 热搜时间戳.
     */
    val hotkeyTime: String?,
    /**
     * 歌单 ID.
     */
    val trackListId: String?,
    /**
     * 热搜词列表.
     */
    val vecHotkey: List<Hotkey>?,
    /**
     * 推荐搜索词列表（参考声明为 `list[dict]`，原样透传）.
     */
    val vecReckey: String?
) {
    internal fun wireSize(): Int {
        return 1 + (this.retCode?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.hotkeyTime?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.trackListId?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.vecHotkey?.let { __boltffi_value_0 -> 4 + __boltffi_value_0.sumOf { __boltffi_value_1 -> val __boltffi_size: kotlin.Int = __boltffi_value_1.wireSize(); __boltffi_size } } ?: 0) + 1 + (this.vecReckey?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0)
    }

    internal fun writeTo(writer: WireWriter) {
        writer.writeOptionalValue(this.retCode, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.hotkeyTime, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.trackListId, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.vecHotkey, { writer, __boltffi_value_0 -> writer.writeSequence(__boltffi_value_0, __boltffi_value_0.size, { writer, __boltffi_value_1 -> __boltffi_value_1.writeTo(writer) }) })
        writer.writeOptionalValue(this.vecReckey, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
    }

    internal fun toByteArray(): ByteArray {
        val buffer = WireWriterPool.acquire(wireSize())
        val writer = buffer.writer
        try {
            writeTo(writer)
            return buffer.bytes()
        } finally {
            buffer.close()
        }
    }

    companion object {
        internal fun fromReader(reader: WireReader): HotkeyResponse {
            return HotkeyResponse(
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readSequence({ reader -> Hotkey.fromReader(reader) }) }),
                reader.readOptionalValue({ reader -> reader.readString() })
            )
        }

        internal fun fromByteArray(bytes: ByteArray): HotkeyResponse {
            val reader = WireReader(bytes)
            return fromReader(reader)
        }
    }
}


/**
 * 参考 `CompleteItem`：搜索补全建议条目.
 */
data class CompleteItem(
    /**
     * 补全提示文本.
     */
    val hint: String?,
    /**
     * 高亮后的提示文本（含 `<em>` 标签）.
     */
    val hintHilight: String?,
    /**
     * docid.
     */
    val docid: String?,
    /**
     * 条目类型（参考字段名 `type`，Rust 侧换个名字、JSON 上仍是 `type`）.
     */
    val typeId: Long?,
    /**
     * 结果类型.
     */
    val resType: String?,
    /**
     * 匹配得分（上游有时给整数）。
     */
    val score: Double?,
    /**
     * 是否直接发起搜索.
     */
    val preSearch: Boolean?,
    /**
     * 图标地址.
     */
    val icon: String?,
    /**
     * 图标类型.
     */
    val iconType: Long?,
    /**
     * 跳转标签页（参考缺省 -1）.
     */
    val jumptab: Long?,
    /**
     * 跳转类型.
     */
    val jumpType: Long?,
    /**
     * 跳转链接.
     */
    val jumpUrl: String?,
    /**
     * 图片地址.
     */
    val picUrl: String?
) {
    internal fun wireSize(): Int {
        return 1 + (this.hint?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.hintHilight?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.docid?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.typeId?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.resType?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.score?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.preSearch?.let { __boltffi_value_0 -> 1 } ?: 0) + 1 + (this.icon?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.iconType?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.jumptab?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.jumpType?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.jumpUrl?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.picUrl?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0)
    }

    internal fun writeTo(writer: WireWriter) {
        writer.writeOptionalValue(this.hint, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.hintHilight, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.docid, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.typeId, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.resType, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.score, { writer, __boltffi_value_0 -> writer.writeF64(__boltffi_value_0) })
        writer.writeOptionalValue(this.preSearch, { writer, __boltffi_value_0 -> writer.writeBool(__boltffi_value_0) })
        writer.writeOptionalValue(this.icon, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.iconType, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.jumptab, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.jumpType, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.jumpUrl, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.picUrl, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
    }

    internal fun toByteArray(): ByteArray {
        val buffer = WireWriterPool.acquire(wireSize())
        val writer = buffer.writer
        try {
            writeTo(writer)
            return buffer.bytes()
        } finally {
            buffer.close()
        }
    }

    companion object {
        internal fun fromReader(reader: WireReader): CompleteItem {
            return CompleteItem(
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readF64() }),
                reader.readOptionalValue({ reader -> reader.readBool() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() })
            )
        }

        internal fun fromByteArray(bytes: ByteArray): CompleteItem {
            val reader = WireReader(bytes)
            return fromReader(reader)
        }
    }
}


/**
 * 参考 `CompleteResponse`：搜索词补全响应.
 */
data class CompleteResponse(
    /**
     * 补全建议条目列表.
     */
    val items: List<CompleteItem>?,
    /**
     * 补全结果总数.
     */
    val totalNum: Long?,
    /**
     * 搜索会话 ID.
     */
    val searchId: String?,
    /**
     * 过期时间戳.
     */
    val expireTime: Long?,
    /**
     * 是否使用默认搜索词.
     */
    val useDefaultSearch: Long?,
    /**
     * 调试信息.
     */
    val debugInfo: String?,
    /**
     * 实验 ID.
     */
    val expid: String?,
    /**
     * 历史搜索词列表（参考声明为 `list[dict]`，原样透传）.
     */
    val historyItems: String?,
    /**
     * 直达结果列表（参考声明为 `list[dict]`，原样透传）.
     */
    val vecDirectItems: String?,
    /**
     * 相关搜索词列表（参考声明为 `list[dict]`，原样透传）.
     */
    val vecRelatedItems: String?
) {
    internal fun wireSize(): Int {
        return 1 + (this.items?.let { __boltffi_value_0 -> 4 + __boltffi_value_0.sumOf { __boltffi_value_1 -> val __boltffi_size: kotlin.Int = __boltffi_value_1.wireSize(); __boltffi_size } } ?: 0) + 1 + (this.totalNum?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.searchId?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.expireTime?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.useDefaultSearch?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.debugInfo?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.expid?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.historyItems?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.vecDirectItems?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.vecRelatedItems?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0)
    }

    internal fun writeTo(writer: WireWriter) {
        writer.writeOptionalValue(this.items, { writer, __boltffi_value_0 -> writer.writeSequence(__boltffi_value_0, __boltffi_value_0.size, { writer, __boltffi_value_1 -> __boltffi_value_1.writeTo(writer) }) })
        writer.writeOptionalValue(this.totalNum, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.searchId, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.expireTime, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.useDefaultSearch, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.debugInfo, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.expid, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.historyItems, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.vecDirectItems, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.vecRelatedItems, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
    }

    internal fun toByteArray(): ByteArray {
        val buffer = WireWriterPool.acquire(wireSize())
        val writer = buffer.writer
        try {
            writeTo(writer)
            return buffer.bytes()
        } finally {
            buffer.close()
        }
    }

    companion object {
        internal fun fromReader(reader: WireReader): CompleteResponse {
            return CompleteResponse(
                reader.readOptionalValue({ reader -> reader.readSequence({ reader -> CompleteItem.fromReader(reader) }) }),
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() })
            )
        }

        internal fun fromByteArray(bytes: ByteArray): CompleteResponse {
            val reader = WireReader(bytes)
            return fromReader(reader)
        }
    }
}


/**
 * 参考 `QuickSearchItem`：快速搜索条目.
 */
data class QuickSearchItem(
    /**
     * docid.
     */
    val docid: String?,
    /**
     * 条目 ID.
     */
    val id: String?,
    /**
     * 条目 MID.
     */
    val mid: String?,
    /**
     * 名称.
     */
    val name: String?,
    /**
     * 歌手名称.
     */
    val singer: String?,
    /**
     * 封面图片地址.
     */
    val pic: String?,
    /**
     * MV ID.
     */
    val vid: String?
) {
    internal fun wireSize(): Int {
        return 1 + (this.docid?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.id?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.mid?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.name?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.singer?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.pic?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.vid?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0)
    }

    internal fun writeTo(writer: WireWriter) {
        writer.writeOptionalValue(this.docid, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.id, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.mid, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.name, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.singer, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.pic, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.vid, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
    }

    internal fun toByteArray(): ByteArray {
        val buffer = WireWriterPool.acquire(wireSize())
        val writer = buffer.writer
        try {
            writeTo(writer)
            return buffer.bytes()
        } finally {
            buffer.close()
        }
    }

    companion object {
        internal fun fromReader(reader: WireReader): QuickSearchItem {
            return QuickSearchItem(
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() })
            )
        }

        internal fun fromByteArray(bytes: ByteArray): QuickSearchItem {
            val reader = WireReader(bytes)
            return fromReader(reader)
        }
    }
}


/**
 * 参考 `QuickSearchCategory`：快速搜索分类.
 */
data class QuickSearchCategory(
    /**
     * 命中数量.
     */
    val count: Long?,
    /**
     * 条目列表.
     */
    val itemlist: List<QuickSearchItem>?,
    /**
     * 分类名称.
     */
    val name: String?,
    /**
     * 排序权重.
     */
    val order: Long?,
    /**
     * 分类类型（参考字段名 `type`，Rust 侧换个名字、JSON 上仍是 `type`）.
     */
    val typeId: Long?
) {
    internal fun wireSize(): Int {
        return 1 + (this.count?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.itemlist?.let { __boltffi_value_0 -> 4 + __boltffi_value_0.sumOf { __boltffi_value_1 -> val __boltffi_size: kotlin.Int = __boltffi_value_1.wireSize(); __boltffi_size } } ?: 0) + 1 + (this.name?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.order?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.typeId?.let { __boltffi_value_0 -> 8 } ?: 0)
    }

    internal fun writeTo(writer: WireWriter) {
        writer.writeOptionalValue(this.count, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.itemlist, { writer, __boltffi_value_0 -> writer.writeSequence(__boltffi_value_0, __boltffi_value_0.size, { writer, __boltffi_value_1 -> __boltffi_value_1.writeTo(writer) }) })
        writer.writeOptionalValue(this.name, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.order, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.typeId, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
    }

    internal fun toByteArray(): ByteArray {
        val buffer = WireWriterPool.acquire(wireSize())
        val writer = buffer.writer
        try {
            writeTo(writer)
            return buffer.bytes()
        } finally {
            buffer.close()
        }
    }

    companion object {
        internal fun fromReader(reader: WireReader): QuickSearchCategory {
            return QuickSearchCategory(
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readSequence({ reader -> QuickSearchItem.fromReader(reader) }) }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readI64() })
            )
        }

        internal fun fromByteArray(bytes: ByteArray): QuickSearchCategory {
            val reader = WireReader(bytes)
            return fromReader(reader)
        }
    }
}


/**
 * 参考 `QuickSearchResponse`：快速搜索响应.
 *
 * 参考模型的四个分类都用 jsonpath `$.data.<name>` 从 `data` 里取；这里在
 * 搬运时就展开到顶层，字段名与参考一致（`song` / `singer` / `album` / `mv`）。
 */
data class QuickSearchResponse(
    /**
     * 单曲结果.
     */
    val song: QuickSearchCategory?,
    /**
     * 歌手结果.
     */
    val singer: QuickSearchCategory?,
    /**
     * 专辑结果.
     */
    val album: QuickSearchCategory?,
    /**
     * MV 结果.
     */
    val mv: QuickSearchCategory?
) {
    internal fun wireSize(): Int {
        return 1 + (this.song?.let { __boltffi_value_0 -> __boltffi_value_0.wireSize() } ?: 0) + 1 + (this.singer?.let { __boltffi_value_0 -> __boltffi_value_0.wireSize() } ?: 0) + 1 + (this.album?.let { __boltffi_value_0 -> __boltffi_value_0.wireSize() } ?: 0) + 1 + (this.mv?.let { __boltffi_value_0 -> __boltffi_value_0.wireSize() } ?: 0)
    }

    internal fun writeTo(writer: WireWriter) {
        writer.writeOptionalValue(this.song, { writer, __boltffi_value_0 -> __boltffi_value_0.writeTo(writer) })
        writer.writeOptionalValue(this.singer, { writer, __boltffi_value_0 -> __boltffi_value_0.writeTo(writer) })
        writer.writeOptionalValue(this.album, { writer, __boltffi_value_0 -> __boltffi_value_0.writeTo(writer) })
        writer.writeOptionalValue(this.mv, { writer, __boltffi_value_0 -> __boltffi_value_0.writeTo(writer) })
    }

    internal fun toByteArray(): ByteArray {
        val buffer = WireWriterPool.acquire(wireSize())
        val writer = buffer.writer
        try {
            writeTo(writer)
            return buffer.bytes()
        } finally {
            buffer.close()
        }
    }

    companion object {
        internal fun fromReader(reader: WireReader): QuickSearchResponse {
            return QuickSearchResponse(
                reader.readOptionalValue({ reader -> QuickSearchCategory.fromReader(reader) }),
                reader.readOptionalValue({ reader -> QuickSearchCategory.fromReader(reader) }),
                reader.readOptionalValue({ reader -> QuickSearchCategory.fromReader(reader) }),
                reader.readOptionalValue({ reader -> QuickSearchCategory.fromReader(reader) })
            )
        }

        internal fun fromByteArray(bytes: ByteArray): QuickSearchResponse {
            val reader = WireReader(bytes)
            return fromReader(reader)
        }
    }
}


/**
 * 参考 `SearchSelector`：搜索筛选器选项.
 */
data class SearchSelector(
    /**
     * 选项 ID.
     */
    val id: Long?,
    /**
     * 选项名称.
     */
    val name: String?,
    /**
     * 选项类型（参考字段名 `type`，Rust 侧换个名字、JSON 上仍是 `type`）.
     */
    val typeId: Long?
) {
    internal fun wireSize(): Int {
        return 1 + (this.id?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.name?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.typeId?.let { __boltffi_value_0 -> 8 } ?: 0)
    }

    internal fun writeTo(writer: WireWriter) {
        writer.writeOptionalValue(this.id, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.name, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.typeId, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
    }

    internal fun toByteArray(): ByteArray {
        val buffer = WireWriterPool.acquire(wireSize())
        val writer = buffer.writer
        try {
            writeTo(writer)
            return buffer.bytes()
        } finally {
            buffer.close()
        }
    }

    companion object {
        internal fun fromReader(reader: WireReader): SearchSelector {
            return SearchSelector(
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readI64() })
            )
        }

        internal fun fromByteArray(bytes: ByteArray): SearchSelector {
            val reader = WireReader(bytes)
            return fromReader(reader)
        }
    }
}


/**
 * 参考搜索里的歌手条目（`SingerSearch`，继承基础模型 `Singer`）：基础字段 + 搜索附加字段.
 */
data class SingerSearchItem(
    /**
     * 歌手数字 ID.
     */
    val id: Long?,
    /**
     * 歌手 Media MID.
     */
    val mid: String?,
    /**
     * 歌手名称.
     */
    val name: String?,
    /**
     * 歌手展示标题（参考回退到名称）.
     */
    val title: String?,
    /**
     * 歌手类型（参考字段名 `type`，别名 `SingerType`/`vt`；Rust 侧换个名字）.
     */
    val kind: Long?,
    /**
     * 与歌手关联的用户 ID.
     */
    val uin: Long?,
    /**
     * 图片 Media ID.
     */
    val pmid: String?,
    /**
     * 歌手头像地址（参考别名 `singerPic`）.
     */
    val pic: String?,
    /**
     * 歌曲数量（参考别名 `songNum`）.
     */
    val songNum: Long?,
    /**
     * 专辑数量（参考别名 `albumNum`）.
     */
    val albumNum: Long?,
    /**
     * MV 数量（参考别名 `mvNum`）.
     */
    val mvNum: Long?,
    /**
     * 补充描述文案.
     */
    val subtitle: String?
) {
    internal fun wireSize(): Int {
        return 1 + (this.id?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.mid?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.name?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.title?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.kind?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.uin?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.pmid?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.pic?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.songNum?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.albumNum?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.mvNum?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.subtitle?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0)
    }

    internal fun writeTo(writer: WireWriter) {
        writer.writeOptionalValue(this.id, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.mid, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.name, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.title, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.kind, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.uin, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.pmid, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.pic, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.songNum, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.albumNum, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.mvNum, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.subtitle, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
    }

    internal fun toByteArray(): ByteArray {
        val buffer = WireWriterPool.acquire(wireSize())
        val writer = buffer.writer
        try {
            writeTo(writer)
            return buffer.bytes()
        } finally {
            buffer.close()
        }
    }

    companion object {
        internal fun fromReader(reader: WireReader): SingerSearchItem {
            return SingerSearchItem(
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readString() })
            )
        }

        internal fun fromByteArray(bytes: ByteArray): SingerSearchItem {
            val reader = WireReader(bytes)
            return fromReader(reader)
        }
    }
}


/**
 * 参考搜索里的专辑条目（`AlbumSearch`，继承基础模型 `Album`）.
 *
 * 参考模型里的 `desc_detail` / `hotness` / `label_new` / `audio_play` 是
 * `dict`，`singer_list` 是歌手列表——前四个在本层存 JSON 文本（见
 * `raw_json_serialize`），写出去仍是对象。
 */
data class AlbumSearchItem(
    /**
     * 专辑数字 ID（参考别名 `albumID`）.
     */
    val id: Long?,
    /**
     * 专辑 Media MID（参考别名 `albumMid`/`albumMID`/`albummid`）.
     */
    val mid: String?,
    /**
     * 专辑名称.
     */
    val name: String?,
    /**
     * 专辑展示标题.
     */
    val title: String?,
    /**
     * 专辑副标题（参考别名 `albumTranName`）.
     */
    val subtitle: String?,
    /**
     * 发行日期（参考别名 `publish_date`/`publishDate`）.
     */
    val timePublic: String?,
    /**
     * 图片 Media ID（参考别名 `logo`）.
     */
    val pmid: String?,
    /**
     * 专辑类型（参考 jsonpath `$.core_album_config.album_type`；JSON 上仍是 `type`）.
     */
    val kind: Long?,
    /**
     * 勋章文案（参考 jsonpath `$.core_album_config.award_label`）.
     */
    val awardLabel: String?,
    /**
     * 详尽描述对象（参考声明为 `dict`，原样透传）.
     */
    val descDetail: String?,
    /**
     * 简短描述文案.
     */
    val description: String?,
    /**
     * 备用描述文案.
     */
    val description2: String?,
    /**
     * 热度数据对象（参考声明为 `dict`，原样透传）.
     */
    val hotness: String?,
    /**
     * 热度简述文案.
     */
    val hotnessDesc: String?,
    /**
     * 专辑关联的特性标签对象（参考声明为 `dict`，原样透传）.
     */
    val labelNew: String?,
    /**
     * 播放排行信息（参考声明为 `dict`，原样透传）.
     */
    val audioPlay: String?,
    /**
     * 专辑封面地址.
     */
    val pic: String?,
    /**
     * 封面配套的勋章/类型图标.
     */
    val picIcon: String?,
    /**
     * 搜索命中的高亮歌手名称.
     */
    val singer: String?,
    /**
     * 结构化的歌手对象列表（复用组件既有的歌手模型）.
     */
    val singerList: List<Singer>?,
    /**
     * 专辑标签列表（参考声明为 `list[str]`）.
     */
    val tagList: List<String>?,
    /**
     * 静态元数据下载链接.
     */
    val url: String?
) {
    internal fun wireSize(): Int {
        return 1 + (this.id?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.mid?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.name?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.title?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.subtitle?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.timePublic?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.pmid?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.kind?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.awardLabel?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.descDetail?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.description?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.description2?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.hotness?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.hotnessDesc?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.labelNew?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.audioPlay?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.pic?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.picIcon?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.singer?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.singerList?.let { __boltffi_value_0 -> 4 + __boltffi_value_0.sumOf { __boltffi_value_1 -> val __boltffi_size: kotlin.Int = __boltffi_value_1.wireSize(); __boltffi_size } } ?: 0) + 1 + (this.tagList?.let { __boltffi_value_0 -> 4 + __boltffi_value_0.sumOf { __boltffi_value_1 -> val __boltffi_size: kotlin.Int = 4 + Utf8Codec.maxBytes(__boltffi_value_1); __boltffi_size } } ?: 0) + 1 + (this.url?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0)
    }

    internal fun writeTo(writer: WireWriter) {
        writer.writeOptionalValue(this.id, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.mid, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.name, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.title, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.subtitle, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.timePublic, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.pmid, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.kind, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.awardLabel, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.descDetail, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.description, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.description2, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.hotness, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.hotnessDesc, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.labelNew, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.audioPlay, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.pic, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.picIcon, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.singer, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.singerList, { writer, __boltffi_value_0 -> writer.writeSequence(__boltffi_value_0, __boltffi_value_0.size, { writer, __boltffi_value_1 -> __boltffi_value_1.writeTo(writer) }) })
        writer.writeOptionalValue(this.tagList, { writer, __boltffi_value_0 -> writer.writeSequence(__boltffi_value_0, __boltffi_value_0.size, { writer, __boltffi_value_1 -> writer.writeString(__boltffi_value_1) }) })
        writer.writeOptionalValue(this.url, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
    }

    internal fun toByteArray(): ByteArray {
        val buffer = WireWriterPool.acquire(wireSize())
        val writer = buffer.writer
        try {
            writeTo(writer)
            return buffer.bytes()
        } finally {
            buffer.close()
        }
    }

    companion object {
        internal fun fromReader(reader: WireReader): AlbumSearchItem {
            return AlbumSearchItem(
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readSequence({ reader -> Singer.fromReader(reader) }) }),
                reader.readOptionalValue({ reader -> reader.readSequence({ reader -> reader.readString() }) }),
                reader.readOptionalValue({ reader -> reader.readString() })
            )
        }

        internal fun fromByteArray(bytes: ByteArray): AlbumSearchItem {
            val reader = WireReader(bytes)
            return fromReader(reader)
        }
    }
}


/**
 * 参考搜索里的歌单条目（`SongListSearch`，继承基础模型 `SongList`）.
 */
data class SongListSearchItem(
    /**
     * 歌单数字 ID（参考别名 `tid`/`dissid`）.
     */
    val id: Long?,
    /**
     * 目录 ID（参考别名 `dirId`）.
     */
    val dirid: Long?,
    /**
     * 歌单标题（参考别名 `dissname`/`name`/`dirName`）.
     */
    val title: String?,
    /**
     * 歌单封面地址（参考别名 `cover`/`logo`/`picUrl`）.
     */
    val picurl: String?,
    /**
     * 歌单简介（参考别名 `description`）.
     */
    val desc: String?,
    /**
     * 歌曲数量（参考别名 `songNum`/`song_cnt`）.
     */
    val songnum: Long?,
    /**
     * 播放量（参考别名 `playCnt`/`play_cnt`）.
     */
    val listennum: Long?,
    /**
     * 歌单创建者昵称.
     */
    val nickname: String?,
    /**
     * 歌单目录类型标识.
     */
    val dirtype: Long?
) {
    internal fun wireSize(): Int {
        return 1 + (this.id?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.dirid?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.title?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.picurl?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.desc?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.songnum?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.listennum?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.nickname?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.dirtype?.let { __boltffi_value_0 -> 8 } ?: 0)
    }

    internal fun writeTo(writer: WireWriter) {
        writer.writeOptionalValue(this.id, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.dirid, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.title, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.picurl, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.desc, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.songnum, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.listennum, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.nickname, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.dirtype, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
    }

    internal fun toByteArray(): ByteArray {
        val buffer = WireWriterPool.acquire(wireSize())
        val writer = buffer.writer
        try {
            writeTo(writer)
            return buffer.bytes()
        } finally {
            buffer.close()
        }
    }

    companion object {
        internal fun fromReader(reader: WireReader): SongListSearchItem {
            return SongListSearchItem(
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readI64() })
            )
        }

        internal fun fromByteArray(bytes: ByteArray): SongListSearchItem {
            val reader = WireReader(bytes)
            return fromReader(reader)
        }
    }
}


/**
 * 参考搜索里的 MV 条目（`MvSearch`，继承基础模型 `MV`）.
 */
data class MvSearchItem(
    /**
     * MV 数字 ID（参考别名 `sid`/`mvid`/`singerId`）.
     */
    val id: Long?,
    /**
     * MV VID.
     */
    val vid: String?,
    /**
     * MV 类型（参考别名 `vt`；JSON 上仍是 `type`）.
     */
    val kind: Long?,
    /**
     * MV 名称（参考别名 `mvname`/`title`）.
     */
    val name: String?,
    /**
     * MV 展示标题（参考别名 `title_main`/`name`）.
     */
    val title: String?,
    /**
     * MV 封面地址.
     */
    val pic: String?,
    /**
     * MV 播放量（参考别名 `play_count`）.
     */
    val playCount: Long?,
    /**
     * MV 时长.
     */
    val duration: Long?,
    /**
     * 发布时间（参考别名 `publish_date`）.
     */
    val publishDate: String?,
    /**
     * 歌手 ID（参考别名 `singerid`）.
     */
    val singerId: Long?,
    /**
     * 歌手 MID（参考别名 `singermid`）.
     */
    val singerMid: String?,
    /**
     * 歌手名称（参考别名 `singername`）.
     */
    val singerName: String?
) {
    internal fun wireSize(): Int {
        return 1 + (this.id?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.vid?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.kind?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.name?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.title?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.pic?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.playCount?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.duration?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.publishDate?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.singerId?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.singerMid?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.singerName?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0)
    }

    internal fun writeTo(writer: WireWriter) {
        writer.writeOptionalValue(this.id, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.vid, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.kind, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.name, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.title, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.pic, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.playCount, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.duration, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.publishDate, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.singerId, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.singerMid, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.singerName, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
    }

    internal fun toByteArray(): ByteArray {
        val buffer = WireWriterPool.acquire(wireSize())
        val writer = buffer.writer
        try {
            writeTo(writer)
            return buffer.bytes()
        } finally {
            buffer.close()
        }
    }

    companion object {
        internal fun fromReader(reader: WireReader): MvSearchItem {
            return MvSearchItem(
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() })
            )
        }

        internal fun fromByteArray(bytes: ByteArray): MvSearchItem {
            val reader = WireReader(bytes)
            return fromReader(reader)
        }
    }
}


/**
 * 参考搜索里的歌曲条目（`SongSearch`，继承基础模型 `Song`）：曲目本体 +
 * 搜索场景附加字段.
 *
 * 前一组字段与组件既有的 [`crate::models::Track`] 同源（同一个曲目解码），
 * 后一组是参考 `SongSearch` 自己的（`search_title` / `hotness` / …）。
 */
data class SongSearchItem(
    /**
     * 歌曲数字 ID.
     */
    val songId: Long?,
    /**
     * 歌曲 Media MID.
     */
    val songMid: String?,
    /**
     * 基础媒体标识符.
     */
    val mediaMid: String?,
    /**
     * 歌曲名称.
     */
    val title: String?,
    /**
     * 全部歌手名，用 `", "` 连接.
     */
    val artist: String?,
    /**
     * 专辑名称.
     */
    val album: String?,
    /**
     * 专辑 Media MID.
     */
    val albumMid: String?,
    /**
     * 专辑数字 ID.
     */
    val albumId: Long?,
    /**
     * 封面地址（上游键 `imageURL`）.
     */
    val imageUrl: String?,
    /**
     * 时长（秒）.
     */
    val duration: Long?,
    /**
     * 播放付费标识.
     */
    val payPlay: Long?,
    /**
     * 首位歌手的 Media MID.
     */
    val singerMid: String?,
    /**
     * 全部歌手.
     */
    val singers: List<Singer>?,
    /**
     * 搜索命中的标题（可能含 `<em>` 高亮标签）.
     */
    val searchTitle: String?,
    /**
     * 歌曲主标题.
     */
    val titleMain: String?,
    /**
     * 歌曲附加标题.
     */
    val titleExtra: String?,
    /**
     * 收藏数展示文案.
     */
    val favShow: String?,
    /**
     * 歌曲描述文案.
     */
    val desc: String?,
    /**
     * 描述文案前的图标链接.
     */
    val descIcon: String?,
    /**
     * 搜索结果内容摘要（命中歌词/评论时的片段）.
     */
    val content: String?,
    /**
     * 热度数据对象（参考声明为 `dict`，原样透传）.
     */
    val hotness: String?,
    /**
     * 热度描述（如榜单名）.
     */
    val hotnessDesc: String?,
    /**
     * 热度榜单详情列表（参考声明为 `list[dict]`，原样透传）.
     */
    val vecHotness: String?,
    /**
     * 新版状态位（2: 正常）.
     */
    val newStatus: Long?,
    /**
     * 是否受到版权保护.
     */
    val protect: Long?,
    /**
     * 相关搜索词推荐组（参考声明为 `dict`，原样透传）.
     */
    val relatedwordGroup: String?
) {
    internal fun wireSize(): Int {
        return 1 + (this.songId?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.songMid?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.mediaMid?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.title?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.artist?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.album?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.albumMid?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.albumId?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.imageUrl?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.duration?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.payPlay?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.singerMid?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.singers?.let { __boltffi_value_0 -> 4 + __boltffi_value_0.sumOf { __boltffi_value_1 -> val __boltffi_size: kotlin.Int = __boltffi_value_1.wireSize(); __boltffi_size } } ?: 0) + 1 + (this.searchTitle?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.titleMain?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.titleExtra?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.favShow?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.desc?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.descIcon?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.content?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.hotness?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.hotnessDesc?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.vecHotness?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.newStatus?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.protect?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.relatedwordGroup?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0)
    }

    internal fun writeTo(writer: WireWriter) {
        writer.writeOptionalValue(this.songId, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.songMid, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.mediaMid, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.title, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.artist, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.album, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.albumMid, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.albumId, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.imageUrl, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.duration, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.payPlay, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.singerMid, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.singers, { writer, __boltffi_value_0 -> writer.writeSequence(__boltffi_value_0, __boltffi_value_0.size, { writer, __boltffi_value_1 -> __boltffi_value_1.writeTo(writer) }) })
        writer.writeOptionalValue(this.searchTitle, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.titleMain, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.titleExtra, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.favShow, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.desc, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.descIcon, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.content, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.hotness, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.hotnessDesc, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.vecHotness, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.newStatus, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.protect, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.relatedwordGroup, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
    }

    internal fun toByteArray(): ByteArray {
        val buffer = WireWriterPool.acquire(wireSize())
        val writer = buffer.writer
        try {
            writeTo(writer)
            return buffer.bytes()
        } finally {
            buffer.close()
        }
    }

    companion object {
        internal fun fromReader(reader: WireReader): SongSearchItem {
            return SongSearchItem(
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readSequence({ reader -> Singer.fromReader(reader) }) }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readString() })
            )
        }

        internal fun fromByteArray(bytes: ByteArray): SongSearchItem {
            val reader = WireReader(bytes)
            return fromReader(reader)
        }
    }
}


/**
 * 参考 `GeneralSearchRequestBody[SongSearch]`：综合搜索的单曲结果容器.
 */
data class SongSearchBucket(
    /**
     * 搜索命中的预估总记录数.
     */
    val estimateSum: Long?,
    /**
     * 搜索命中的确切总记录数.
     */
    val totalNum: Long?,
    /**
     * 当前分类下已展开的曲目.
     */
    val items: List<SongSearchItem>?,
    /**
     * 继续加载该分类结果时需要回传的翻页上下文（参考声明为 `dict`，原样透传）.
     */
    val moreInfo: String?
) {
    internal fun wireSize(): Int {
        return 1 + (this.estimateSum?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.totalNum?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.items?.let { __boltffi_value_0 -> 4 + __boltffi_value_0.sumOf { __boltffi_value_1 -> val __boltffi_size: kotlin.Int = __boltffi_value_1.wireSize(); __boltffi_size } } ?: 0) + 1 + (this.moreInfo?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0)
    }

    internal fun writeTo(writer: WireWriter) {
        writer.writeOptionalValue(this.estimateSum, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.totalNum, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.items, { writer, __boltffi_value_0 -> writer.writeSequence(__boltffi_value_0, __boltffi_value_0.size, { writer, __boltffi_value_1 -> __boltffi_value_1.writeTo(writer) }) })
        writer.writeOptionalValue(this.moreInfo, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
    }

    internal fun toByteArray(): ByteArray {
        val buffer = WireWriterPool.acquire(wireSize())
        val writer = buffer.writer
        try {
            writeTo(writer)
            return buffer.bytes()
        } finally {
            buffer.close()
        }
    }

    companion object {
        internal fun fromReader(reader: WireReader): SongSearchBucket {
            return SongSearchBucket(
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readSequence({ reader -> SongSearchItem.fromReader(reader) }) }),
                reader.readOptionalValue({ reader -> reader.readString() })
            )
        }

        internal fun fromByteArray(bytes: ByteArray): SongSearchBucket {
            val reader = WireReader(bytes)
            return fromReader(reader)
        }
    }
}


/**
 * 参考 `GeneralSearchRequestBody[SingerSearch]`：综合搜索的歌手结果容器.
 */
data class SingerSearchBucket(
    val estimateSum: Long?,
    val totalNum: Long?,
    val items: List<SingerSearchItem>?,
    val moreInfo: String?
) {
    internal fun wireSize(): Int {
        return 1 + (this.estimateSum?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.totalNum?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.items?.let { __boltffi_value_0 -> 4 + __boltffi_value_0.sumOf { __boltffi_value_1 -> val __boltffi_size: kotlin.Int = __boltffi_value_1.wireSize(); __boltffi_size } } ?: 0) + 1 + (this.moreInfo?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0)
    }

    internal fun writeTo(writer: WireWriter) {
        writer.writeOptionalValue(this.estimateSum, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.totalNum, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.items, { writer, __boltffi_value_0 -> writer.writeSequence(__boltffi_value_0, __boltffi_value_0.size, { writer, __boltffi_value_1 -> __boltffi_value_1.writeTo(writer) }) })
        writer.writeOptionalValue(this.moreInfo, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
    }

    internal fun toByteArray(): ByteArray {
        val buffer = WireWriterPool.acquire(wireSize())
        val writer = buffer.writer
        try {
            writeTo(writer)
            return buffer.bytes()
        } finally {
            buffer.close()
        }
    }

    companion object {
        internal fun fromReader(reader: WireReader): SingerSearchBucket {
            return SingerSearchBucket(
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readSequence({ reader -> SingerSearchItem.fromReader(reader) }) }),
                reader.readOptionalValue({ reader -> reader.readString() })
            )
        }

        internal fun fromByteArray(bytes: ByteArray): SingerSearchBucket {
            val reader = WireReader(bytes)
            return fromReader(reader)
        }
    }
}


/**
 * 参考 `GeneralSearchRequestBody[AlbumSearch]`：综合搜索的专辑/节目结果容器.
 */
data class AlbumSearchBucket(
    val estimateSum: Long?,
    val totalNum: Long?,
    val items: List<AlbumSearchItem>?,
    val moreInfo: String?
) {
    internal fun wireSize(): Int {
        return 1 + (this.estimateSum?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.totalNum?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.items?.let { __boltffi_value_0 -> 4 + __boltffi_value_0.sumOf { __boltffi_value_1 -> val __boltffi_size: kotlin.Int = __boltffi_value_1.wireSize(); __boltffi_size } } ?: 0) + 1 + (this.moreInfo?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0)
    }

    internal fun writeTo(writer: WireWriter) {
        writer.writeOptionalValue(this.estimateSum, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.totalNum, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.items, { writer, __boltffi_value_0 -> writer.writeSequence(__boltffi_value_0, __boltffi_value_0.size, { writer, __boltffi_value_1 -> __boltffi_value_1.writeTo(writer) }) })
        writer.writeOptionalValue(this.moreInfo, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
    }

    internal fun toByteArray(): ByteArray {
        val buffer = WireWriterPool.acquire(wireSize())
        val writer = buffer.writer
        try {
            writeTo(writer)
            return buffer.bytes()
        } finally {
            buffer.close()
        }
    }

    companion object {
        internal fun fromReader(reader: WireReader): AlbumSearchBucket {
            return AlbumSearchBucket(
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readSequence({ reader -> AlbumSearchItem.fromReader(reader) }) }),
                reader.readOptionalValue({ reader -> reader.readString() })
            )
        }

        internal fun fromByteArray(bytes: ByteArray): AlbumSearchBucket {
            val reader = WireReader(bytes)
            return fromReader(reader)
        }
    }
}


/**
 * 参考 `GeneralSearchRequestBody[SongListSearch]`：综合搜索的歌单结果容器.
 */
data class SongListSearchBucket(
    val estimateSum: Long?,
    val totalNum: Long?,
    val items: List<SongListSearchItem>?,
    val moreInfo: String?
) {
    internal fun wireSize(): Int {
        return 1 + (this.estimateSum?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.totalNum?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.items?.let { __boltffi_value_0 -> 4 + __boltffi_value_0.sumOf { __boltffi_value_1 -> val __boltffi_size: kotlin.Int = __boltffi_value_1.wireSize(); __boltffi_size } } ?: 0) + 1 + (this.moreInfo?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0)
    }

    internal fun writeTo(writer: WireWriter) {
        writer.writeOptionalValue(this.estimateSum, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.totalNum, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.items, { writer, __boltffi_value_0 -> writer.writeSequence(__boltffi_value_0, __boltffi_value_0.size, { writer, __boltffi_value_1 -> __boltffi_value_1.writeTo(writer) }) })
        writer.writeOptionalValue(this.moreInfo, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
    }

    internal fun toByteArray(): ByteArray {
        val buffer = WireWriterPool.acquire(wireSize())
        val writer = buffer.writer
        try {
            writeTo(writer)
            return buffer.bytes()
        } finally {
            buffer.close()
        }
    }

    companion object {
        internal fun fromReader(reader: WireReader): SongListSearchBucket {
            return SongListSearchBucket(
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readSequence({ reader -> SongListSearchItem.fromReader(reader) }) }),
                reader.readOptionalValue({ reader -> reader.readString() })
            )
        }

        internal fun fromByteArray(bytes: ByteArray): SongListSearchBucket {
            val reader = WireReader(bytes)
            return fromReader(reader)
        }
    }
}


/**
 * 参考 `GeneralSearchRequestBody[MvSearch]`：综合搜索的 MV 结果容器.
 */
data class MvSearchBucket(
    val estimateSum: Long?,
    val totalNum: Long?,
    val items: List<MvSearchItem>?,
    val moreInfo: String?
) {
    internal fun wireSize(): Int {
        return 1 + (this.estimateSum?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.totalNum?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.items?.let { __boltffi_value_0 -> 4 + __boltffi_value_0.sumOf { __boltffi_value_1 -> val __boltffi_size: kotlin.Int = __boltffi_value_1.wireSize(); __boltffi_size } } ?: 0) + 1 + (this.moreInfo?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0)
    }

    internal fun writeTo(writer: WireWriter) {
        writer.writeOptionalValue(this.estimateSum, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.totalNum, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.items, { writer, __boltffi_value_0 -> writer.writeSequence(__boltffi_value_0, __boltffi_value_0.size, { writer, __boltffi_value_1 -> __boltffi_value_1.writeTo(writer) }) })
        writer.writeOptionalValue(this.moreInfo, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
    }

    internal fun toByteArray(): ByteArray {
        val buffer = WireWriterPool.acquire(wireSize())
        val writer = buffer.writer
        try {
            writeTo(writer)
            return buffer.bytes()
        } finally {
            buffer.close()
        }
    }

    companion object {
        internal fun fromReader(reader: WireReader): MvSearchBucket {
            return MvSearchBucket(
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readSequence({ reader -> MvSearchItem.fromReader(reader) }) }),
                reader.readOptionalValue({ reader -> reader.readString() })
            )
        }

        internal fun fromByteArray(bytes: ByteArray): MvSearchBucket {
            val reader = WireReader(bytes)
            return fromReader(reader)
        }
    }
}


/**
 * 参考 `GeneralSearchRequestBody[RelatedSearchWord]`：相关搜索词结果容器.
 */
data class RelatedSearchBucket(
    val estimateSum: Long?,
    val totalNum: Long?,
    val items: List<RelatedSearchWord>?,
    val moreInfo: String?
) {
    internal fun wireSize(): Int {
        return 1 + (this.estimateSum?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.totalNum?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.items?.let { __boltffi_value_0 -> 4 + __boltffi_value_0.sumOf { __boltffi_value_1 -> val __boltffi_size: kotlin.Int = __boltffi_value_1.wireSize(); __boltffi_size } } ?: 0) + 1 + (this.moreInfo?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0)
    }

    internal fun writeTo(writer: WireWriter) {
        writer.writeOptionalValue(this.estimateSum, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.totalNum, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.items, { writer, __boltffi_value_0 -> writer.writeSequence(__boltffi_value_0, __boltffi_value_0.size, { writer, __boltffi_value_1 -> __boltffi_value_1.writeTo(writer) }) })
        writer.writeOptionalValue(this.moreInfo, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
    }

    internal fun toByteArray(): ByteArray {
        val buffer = WireWriterPool.acquire(wireSize())
        val writer = buffer.writer
        try {
            writeTo(writer)
            return buffer.bytes()
        } finally {
            buffer.close()
        }
    }

    companion object {
        internal fun fromReader(reader: WireReader): RelatedSearchBucket {
            return RelatedSearchBucket(
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readSequence({ reader -> RelatedSearchWord.fromReader(reader) }) }),
                reader.readOptionalValue({ reader -> reader.readString() })
            )
        }

        internal fun fromByteArray(bytes: ByteArray): RelatedSearchBucket {
            val reader = WireReader(bytes)
            return fromReader(reader)
        }
    }
}


/**
 * 参考 `RelatedSearchWord`：相关搜索词推荐.
 */
data class RelatedSearchWord(
    /**
     * 相关搜索词展示文案.
     */
    val display: String?,
    /**
     * 相关搜索词实际搜索关键词.
     */
    val search: String?
) {
    internal fun wireSize(): Int {
        return 1 + (this.display?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.search?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0)
    }

    internal fun writeTo(writer: WireWriter) {
        writer.writeOptionalValue(this.display, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.search, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
    }

    internal fun toByteArray(): ByteArray {
        val buffer = WireWriterPool.acquire(wireSize())
        val writer = buffer.writer
        try {
            writeTo(writer)
            return buffer.bytes()
        } finally {
            buffer.close()
        }
    }

    companion object {
        internal fun fromReader(reader: WireReader): RelatedSearchWord {
            return RelatedSearchWord(
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() })
            )
        }

        internal fun fromByteArray(bytes: ByteArray): RelatedSearchWord {
            val reader = WireReader(bytes)
            return fromReader(reader)
        }
    }
}


/**
 * 参考 `GeneralSearchResponse`：综合搜索响应.
 */
data class GeneralSearchResponse(
    /**
     * 搜索会话 ID（上游 `meta.sid`）.
     */
    val searchid: String?,
    /**
     * 每页结果数量.
     */
    val perpage: Long?,
    /**
     * 下一页页码，-1 表示已加载全部结果.
     */
    val nextpage: Long?,
    /**
     * 综合搜索继续翻页的关键参数（参考声明为 `dict`，原样透传）.
     */
    val nextpageStart: String?,
    /**
     * 单曲结果容器（曲目复用组件既有的曲目模型）.
     */
    val song: SongSearchBucket?,
    /**
     * 歌手结果容器.
     */
    val singer: SingerSearchBucket?,
    /**
     * MV 结果容器.
     */
    val mv: MvSearchBucket?,
    /**
     * 专辑结果容器.
     */
    val album: AlbumSearchBucket?,
    /**
     * 歌单结果容器.
     */
    val songlist: SongListSearchBucket?,
    /**
     * 节目结果容器.
     */
    val audio: AlbumSearchBucket?,
    /**
     * 直接命中结果分组（参考声明为 `list[dict]`，原样透传）.
     */
    val direct: String?,
    /**
     * 相关搜索词推荐结果容器.
     */
    val related: RelatedSearchBucket?
) {
    internal fun wireSize(): Int {
        return 1 + (this.searchid?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.perpage?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.nextpage?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.nextpageStart?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.song?.let { __boltffi_value_0 -> __boltffi_value_0.wireSize() } ?: 0) + 1 + (this.singer?.let { __boltffi_value_0 -> __boltffi_value_0.wireSize() } ?: 0) + 1 + (this.mv?.let { __boltffi_value_0 -> __boltffi_value_0.wireSize() } ?: 0) + 1 + (this.album?.let { __boltffi_value_0 -> __boltffi_value_0.wireSize() } ?: 0) + 1 + (this.songlist?.let { __boltffi_value_0 -> __boltffi_value_0.wireSize() } ?: 0) + 1 + (this.audio?.let { __boltffi_value_0 -> __boltffi_value_0.wireSize() } ?: 0) + 1 + (this.direct?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.related?.let { __boltffi_value_0 -> __boltffi_value_0.wireSize() } ?: 0)
    }

    internal fun writeTo(writer: WireWriter) {
        writer.writeOptionalValue(this.searchid, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.perpage, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.nextpage, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.nextpageStart, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.song, { writer, __boltffi_value_0 -> __boltffi_value_0.writeTo(writer) })
        writer.writeOptionalValue(this.singer, { writer, __boltffi_value_0 -> __boltffi_value_0.writeTo(writer) })
        writer.writeOptionalValue(this.mv, { writer, __boltffi_value_0 -> __boltffi_value_0.writeTo(writer) })
        writer.writeOptionalValue(this.album, { writer, __boltffi_value_0 -> __boltffi_value_0.writeTo(writer) })
        writer.writeOptionalValue(this.songlist, { writer, __boltffi_value_0 -> __boltffi_value_0.writeTo(writer) })
        writer.writeOptionalValue(this.audio, { writer, __boltffi_value_0 -> __boltffi_value_0.writeTo(writer) })
        writer.writeOptionalValue(this.direct, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.related, { writer, __boltffi_value_0 -> __boltffi_value_0.writeTo(writer) })
    }

    internal fun toByteArray(): ByteArray {
        val buffer = WireWriterPool.acquire(wireSize())
        val writer = buffer.writer
        try {
            writeTo(writer)
            return buffer.bytes()
        } finally {
            buffer.close()
        }
    }

    companion object {
        internal fun fromReader(reader: WireReader): GeneralSearchResponse {
            return GeneralSearchResponse(
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> SongSearchBucket.fromReader(reader) }),
                reader.readOptionalValue({ reader -> SingerSearchBucket.fromReader(reader) }),
                reader.readOptionalValue({ reader -> MvSearchBucket.fromReader(reader) }),
                reader.readOptionalValue({ reader -> AlbumSearchBucket.fromReader(reader) }),
                reader.readOptionalValue({ reader -> SongListSearchBucket.fromReader(reader) }),
                reader.readOptionalValue({ reader -> AlbumSearchBucket.fromReader(reader) }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> RelatedSearchBucket.fromReader(reader) })
            )
        }

        internal fun fromByteArray(bytes: ByteArray): GeneralSearchResponse {
            val reader = WireReader(bytes)
            return fromReader(reader)
        }
    }
}


/**
 * 参考 `SearchByTypeResponse`：按指定类型搜索时的响应模型.
 *
 * 列表里的条目按类型不同：歌曲/歌词/彩铃/节目是曲目模型，歌手/专辑/歌单/MV
 * 各是各自的基础模型，用户是原样透传的上游形状（JSON 文本）。参考对每个类型
 * 各写一遍，这里的字段与参考同名。
 */
data class SearchByTypeResponse(
    /**
     * 搜索会话 ID，用于后续相关请求（上游 `meta.searchid`）.
     */
    val searchid: String?,
    /**
     * 每页结果数量.
     */
    val perpage: Long?,
    /**
     * 下一页页码，-1 表示已加载全部结果.
     */
    val nextpage: Long?,
    /**
     * 搜索命中的预估总记录数.
     */
    val estimateSum: Long?,
    /**
     * 搜索命中的确切总记录数（上游 `meta.sum`）.
     */
    val totalNum: Long?,
    /**
     * 单曲、歌词或节目类型下的结果列表.
     */
    val song: List<Track>?,
    /**
     * 歌手结果列表.
     */
    val singer: List<SingerSearchItem>?,
    /**
     * 专辑结果列表.
     */
    val album: List<AlbumSearchItem>?,
    /**
     * 歌单结果列表.
     */
    val songlist: List<SongListSearchItem>?,
    /**
     * 用户结果列表（参考声明为 `list[dict]`，原样透传）.
     */
    val user: String?,
    /**
     * 节目专辑结果列表.
     */
    val audioAlum: List<AlbumSearchItem>?,
    /**
     * MV 结果列表.
     */
    val mv: List<MvSearchItem>?,
    /**
     * 搜索筛选器列表（上游 `body.multi_extern_info.selectors`，每组一个数组）.
     */
    val selectors: List<List<SearchSelector>>?
) {
    internal fun wireSize(): Int {
        return 1 + (this.searchid?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.perpage?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.nextpage?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.estimateSum?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.totalNum?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.song?.let { __boltffi_value_0 -> 4 + __boltffi_value_0.sumOf { __boltffi_value_1 -> val __boltffi_size: kotlin.Int = __boltffi_value_1.wireSize(); __boltffi_size } } ?: 0) + 1 + (this.singer?.let { __boltffi_value_0 -> 4 + __boltffi_value_0.sumOf { __boltffi_value_1 -> val __boltffi_size: kotlin.Int = __boltffi_value_1.wireSize(); __boltffi_size } } ?: 0) + 1 + (this.album?.let { __boltffi_value_0 -> 4 + __boltffi_value_0.sumOf { __boltffi_value_1 -> val __boltffi_size: kotlin.Int = __boltffi_value_1.wireSize(); __boltffi_size } } ?: 0) + 1 + (this.songlist?.let { __boltffi_value_0 -> 4 + __boltffi_value_0.sumOf { __boltffi_value_1 -> val __boltffi_size: kotlin.Int = __boltffi_value_1.wireSize(); __boltffi_size } } ?: 0) + 1 + (this.user?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.audioAlum?.let { __boltffi_value_0 -> 4 + __boltffi_value_0.sumOf { __boltffi_value_1 -> val __boltffi_size: kotlin.Int = __boltffi_value_1.wireSize(); __boltffi_size } } ?: 0) + 1 + (this.mv?.let { __boltffi_value_0 -> 4 + __boltffi_value_0.sumOf { __boltffi_value_1 -> val __boltffi_size: kotlin.Int = __boltffi_value_1.wireSize(); __boltffi_size } } ?: 0) + 1 + (this.selectors?.let { __boltffi_value_0 -> 4 + __boltffi_value_0.sumOf { __boltffi_value_1 -> val __boltffi_size: kotlin.Int = 4 + __boltffi_value_1.sumOf { __boltffi_value_2 -> val __boltffi_size: kotlin.Int = __boltffi_value_2.wireSize(); __boltffi_size }; __boltffi_size } } ?: 0)
    }

    internal fun writeTo(writer: WireWriter) {
        writer.writeOptionalValue(this.searchid, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.perpage, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.nextpage, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.estimateSum, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.totalNum, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.song, { writer, __boltffi_value_0 -> writer.writeSequence(__boltffi_value_0, __boltffi_value_0.size, { writer, __boltffi_value_1 -> __boltffi_value_1.writeTo(writer) }) })
        writer.writeOptionalValue(this.singer, { writer, __boltffi_value_0 -> writer.writeSequence(__boltffi_value_0, __boltffi_value_0.size, { writer, __boltffi_value_1 -> __boltffi_value_1.writeTo(writer) }) })
        writer.writeOptionalValue(this.album, { writer, __boltffi_value_0 -> writer.writeSequence(__boltffi_value_0, __boltffi_value_0.size, { writer, __boltffi_value_1 -> __boltffi_value_1.writeTo(writer) }) })
        writer.writeOptionalValue(this.songlist, { writer, __boltffi_value_0 -> writer.writeSequence(__boltffi_value_0, __boltffi_value_0.size, { writer, __boltffi_value_1 -> __boltffi_value_1.writeTo(writer) }) })
        writer.writeOptionalValue(this.user, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.audioAlum, { writer, __boltffi_value_0 -> writer.writeSequence(__boltffi_value_0, __boltffi_value_0.size, { writer, __boltffi_value_1 -> __boltffi_value_1.writeTo(writer) }) })
        writer.writeOptionalValue(this.mv, { writer, __boltffi_value_0 -> writer.writeSequence(__boltffi_value_0, __boltffi_value_0.size, { writer, __boltffi_value_1 -> __boltffi_value_1.writeTo(writer) }) })
        writer.writeOptionalValue(this.selectors, { writer, __boltffi_value_0 -> writer.writeSequence(__boltffi_value_0, __boltffi_value_0.size, { writer, __boltffi_value_1 -> writer.writeSequence(__boltffi_value_1, __boltffi_value_1.size, { writer, __boltffi_value_2 -> __boltffi_value_2.writeTo(writer) }) }) })
    }

    internal fun toByteArray(): ByteArray {
        val buffer = WireWriterPool.acquire(wireSize())
        val writer = buffer.writer
        try {
            writeTo(writer)
            return buffer.bytes()
        } finally {
            buffer.close()
        }
    }

    companion object {
        internal fun fromReader(reader: WireReader): SearchByTypeResponse {
            return SearchByTypeResponse(
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readSequence({ reader -> Track.fromReader(reader) }) }),
                reader.readOptionalValue({ reader -> reader.readSequence({ reader -> SingerSearchItem.fromReader(reader) }) }),
                reader.readOptionalValue({ reader -> reader.readSequence({ reader -> AlbumSearchItem.fromReader(reader) }) }),
                reader.readOptionalValue({ reader -> reader.readSequence({ reader -> SongListSearchItem.fromReader(reader) }) }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readSequence({ reader -> AlbumSearchItem.fromReader(reader) }) }),
                reader.readOptionalValue({ reader -> reader.readSequence({ reader -> MvSearchItem.fromReader(reader) }) }),
                reader.readOptionalValue({ reader -> reader.readSequence({ reader -> reader.readSequence({ reader -> SearchSelector.fromReader(reader) }) }) })
            )
        }

        internal fun fromByteArray(bytes: ByteArray): SearchByTypeResponse {
            val reader = WireReader(bytes)
            return fromReader(reader)
        }
    }
}


/**
 * 参考 `TagOption`：歌手筛选标签项.
 */
data class SingerTag(
    val id: Long?,
    val name: String?
) {
    internal fun wireSize(): Int {
        return 1 + (this.id?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.name?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0)
    }

    internal fun writeTo(writer: WireWriter) {
        writer.writeOptionalValue(this.id, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.name, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
    }

    internal fun toByteArray(): ByteArray {
        val buffer = WireWriterPool.acquire(wireSize())
        val writer = buffer.writer
        try {
            writeTo(writer)
            return buffer.bytes()
        } finally {
            buffer.close()
        }
    }

    companion object {
        internal fun fromReader(reader: WireReader): SingerTag {
            return SingerTag(
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readString() })
            )
        }

        internal fun fromByteArray(bytes: ByteArray): SingerTag {
            val reader = WireReader(bytes)
            return fromReader(reader)
        }
    }
}


/**
 * 参考 `SingerBrief`：歌手列表条目.
 */
data class SingerBrief(
    /**
     * 歌手数字 ID（参考别名 `singer_id` / `singerId` / `id`）.
     */
    val id: Long?,
    /**
     * 歌手 MID（别名 `singer_mid` / `singerMid` / `mid`）.
     */
    val mid: String?,
    /**
     * 歌手名称（别名 `singer_name` / `singerName` / `name`）.
     */
    val name: String?,
    /**
     * 图片标识（别名 `singer_pmid` / `singerPmid` / `pmid`）.
     */
    val pmid: String?,
    val areaId: Long?,
    val countryId: Long?,
    val country: String?,
    val otherName: String?,
    /**
     * 拼音.
     */
    val spell: String?,
    /**
     * 趋势标记.
     */
    val trend: Long?,
    /**
     * 关注数（上游键就是 `concernNum`）.
     */
    val concernNum: Long?,
    /**
     * 歌手图片地址.
     */
    val singerPic: String?
) {
    internal fun wireSize(): Int {
        return 1 + (this.id?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.mid?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.name?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.pmid?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.areaId?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.countryId?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.country?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.otherName?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.spell?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.trend?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.concernNum?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.singerPic?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0)
    }

    internal fun writeTo(writer: WireWriter) {
        writer.writeOptionalValue(this.id, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.mid, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.name, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.pmid, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.areaId, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.countryId, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.country, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.otherName, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.spell, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.trend, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.concernNum, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.singerPic, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
    }

    internal fun toByteArray(): ByteArray {
        val buffer = WireWriterPool.acquire(wireSize())
        val writer = buffer.writer
        try {
            writeTo(writer)
            return buffer.bytes()
        } finally {
            buffer.close()
        }
    }

    companion object {
        internal fun fromReader(reader: WireReader): SingerBrief {
            return SingerBrief(
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readString() })
            )
        }

        internal fun fromByteArray(bytes: ByteArray): SingerBrief {
            val reader = WireReader(bytes)
            return fromReader(reader)
        }
    }
}


/**
 * 参考 `SingerTagData`：歌手筛选标签集合.
 */
data class SingerTagData(
    val area: List<SingerTag>?,
    val genre: List<SingerTag>?,
    val sex: List<SingerTag>?,
    val index: List<SingerTag>?
) {
    internal fun wireSize(): Int {
        return 1 + (this.area?.let { __boltffi_value_0 -> 4 + __boltffi_value_0.sumOf { __boltffi_value_1 -> val __boltffi_size: kotlin.Int = __boltffi_value_1.wireSize(); __boltffi_size } } ?: 0) + 1 + (this.genre?.let { __boltffi_value_0 -> 4 + __boltffi_value_0.sumOf { __boltffi_value_1 -> val __boltffi_size: kotlin.Int = __boltffi_value_1.wireSize(); __boltffi_size } } ?: 0) + 1 + (this.sex?.let { __boltffi_value_0 -> 4 + __boltffi_value_0.sumOf { __boltffi_value_1 -> val __boltffi_size: kotlin.Int = __boltffi_value_1.wireSize(); __boltffi_size } } ?: 0) + 1 + (this.index?.let { __boltffi_value_0 -> 4 + __boltffi_value_0.sumOf { __boltffi_value_1 -> val __boltffi_size: kotlin.Int = __boltffi_value_1.wireSize(); __boltffi_size } } ?: 0)
    }

    internal fun writeTo(writer: WireWriter) {
        writer.writeOptionalValue(this.area, { writer, __boltffi_value_0 -> writer.writeSequence(__boltffi_value_0, __boltffi_value_0.size, { writer, __boltffi_value_1 -> __boltffi_value_1.writeTo(writer) }) })
        writer.writeOptionalValue(this.genre, { writer, __boltffi_value_0 -> writer.writeSequence(__boltffi_value_0, __boltffi_value_0.size, { writer, __boltffi_value_1 -> __boltffi_value_1.writeTo(writer) }) })
        writer.writeOptionalValue(this.sex, { writer, __boltffi_value_0 -> writer.writeSequence(__boltffi_value_0, __boltffi_value_0.size, { writer, __boltffi_value_1 -> __boltffi_value_1.writeTo(writer) }) })
        writer.writeOptionalValue(this.index, { writer, __boltffi_value_0 -> writer.writeSequence(__boltffi_value_0, __boltffi_value_0.size, { writer, __boltffi_value_1 -> __boltffi_value_1.writeTo(writer) }) })
    }

    internal fun toByteArray(): ByteArray {
        val buffer = WireWriterPool.acquire(wireSize())
        val writer = buffer.writer
        try {
            writeTo(writer)
            return buffer.bytes()
        } finally {
            buffer.close()
        }
    }

    companion object {
        internal fun fromReader(reader: WireReader): SingerTagData {
            return SingerTagData(
                reader.readOptionalValue({ reader -> reader.readSequence({ reader -> SingerTag.fromReader(reader) }) }),
                reader.readOptionalValue({ reader -> reader.readSequence({ reader -> SingerTag.fromReader(reader) }) }),
                reader.readOptionalValue({ reader -> reader.readSequence({ reader -> SingerTag.fromReader(reader) }) }),
                reader.readOptionalValue({ reader -> reader.readSequence({ reader -> SingerTag.fromReader(reader) }) })
            )
        }

        internal fun fromByteArray(bytes: ByteArray): SingerTagData {
            val reader = WireReader(bytes)
            return fromReader(reader)
        }
    }
}


/**
 * 参考 `SingerTypeListResponse`：歌手列表接口的响应体.
 */
data class SingerTypeList(
    /**
     * 当前地区筛选值.
     */
    val area: Long?,
    /**
     * 当前性别筛选值.
     */
    val sex: Long?,
    /**
     * 当前流派筛选值.
     */
    val genre: Long?,
    /**
     * 当前返回的歌手列表.
     */
    val singerlist: List<SingerBrief>?,
    val code: Long?,
    /**
     * 热门歌手列表.
     */
    val hotlist: List<SingerBrief>?,
    /**
     * 可选筛选标签集合.
     */
    val tags: SingerTagData?
) {
    internal fun wireSize(): Int {
        return 1 + (this.area?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.sex?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.genre?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.singerlist?.let { __boltffi_value_0 -> 4 + __boltffi_value_0.sumOf { __boltffi_value_1 -> val __boltffi_size: kotlin.Int = __boltffi_value_1.wireSize(); __boltffi_size } } ?: 0) + 1 + (this.code?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.hotlist?.let { __boltffi_value_0 -> 4 + __boltffi_value_0.sumOf { __boltffi_value_1 -> val __boltffi_size: kotlin.Int = __boltffi_value_1.wireSize(); __boltffi_size } } ?: 0) + 1 + (this.tags?.let { __boltffi_value_0 -> __boltffi_value_0.wireSize() } ?: 0)
    }

    internal fun writeTo(writer: WireWriter) {
        writer.writeOptionalValue(this.area, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.sex, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.genre, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.singerlist, { writer, __boltffi_value_0 -> writer.writeSequence(__boltffi_value_0, __boltffi_value_0.size, { writer, __boltffi_value_1 -> __boltffi_value_1.writeTo(writer) }) })
        writer.writeOptionalValue(this.code, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.hotlist, { writer, __boltffi_value_0 -> writer.writeSequence(__boltffi_value_0, __boltffi_value_0.size, { writer, __boltffi_value_1 -> __boltffi_value_1.writeTo(writer) }) })
        writer.writeOptionalValue(this.tags, { writer, __boltffi_value_0 -> __boltffi_value_0.writeTo(writer) })
    }

    internal fun toByteArray(): ByteArray {
        val buffer = WireWriterPool.acquire(wireSize())
        val writer = buffer.writer
        try {
            writeTo(writer)
            return buffer.bytes()
        } finally {
            buffer.close()
        }
    }

    companion object {
        internal fun fromReader(reader: WireReader): SingerTypeList {
            return SingerTypeList(
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readSequence({ reader -> SingerBrief.fromReader(reader) }) }),
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readSequence({ reader -> SingerBrief.fromReader(reader) }) }),
                reader.readOptionalValue({ reader -> SingerTagData.fromReader(reader) })
            )
        }

        internal fun fromByteArray(bytes: ByteArray): SingerTypeList {
            val reader = WireReader(bytes)
            return fromReader(reader)
        }
    }
}


/**
 * 参考 `SingerIndexPageResponse`：按索引分页的歌手列表响应体.
 */
data class SingerIndexPage(
    val area: Long?,
    val sex: Long?,
    val genre: Long?,
    val singerlist: List<SingerBrief>?,
    val code: Long?,
    val hotlist: List<SingerBrief>?,
    val tags: SingerTagData?,
    /**
     * 当前索引筛选值.
     */
    val index: Long?,
    /**
     * 总数量（宿主据此翻页）.
     */
    val total: Long?
) {
    internal fun wireSize(): Int {
        return 1 + (this.area?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.sex?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.genre?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.singerlist?.let { __boltffi_value_0 -> 4 + __boltffi_value_0.sumOf { __boltffi_value_1 -> val __boltffi_size: kotlin.Int = __boltffi_value_1.wireSize(); __boltffi_size } } ?: 0) + 1 + (this.code?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.hotlist?.let { __boltffi_value_0 -> 4 + __boltffi_value_0.sumOf { __boltffi_value_1 -> val __boltffi_size: kotlin.Int = __boltffi_value_1.wireSize(); __boltffi_size } } ?: 0) + 1 + (this.tags?.let { __boltffi_value_0 -> __boltffi_value_0.wireSize() } ?: 0) + 1 + (this.index?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.total?.let { __boltffi_value_0 -> 8 } ?: 0)
    }

    internal fun writeTo(writer: WireWriter) {
        writer.writeOptionalValue(this.area, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.sex, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.genre, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.singerlist, { writer, __boltffi_value_0 -> writer.writeSequence(__boltffi_value_0, __boltffi_value_0.size, { writer, __boltffi_value_1 -> __boltffi_value_1.writeTo(writer) }) })
        writer.writeOptionalValue(this.code, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.hotlist, { writer, __boltffi_value_0 -> writer.writeSequence(__boltffi_value_0, __boltffi_value_0.size, { writer, __boltffi_value_1 -> __boltffi_value_1.writeTo(writer) }) })
        writer.writeOptionalValue(this.tags, { writer, __boltffi_value_0 -> __boltffi_value_0.writeTo(writer) })
        writer.writeOptionalValue(this.index, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.total, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
    }

    internal fun toByteArray(): ByteArray {
        val buffer = WireWriterPool.acquire(wireSize())
        val writer = buffer.writer
        try {
            writeTo(writer)
            return buffer.bytes()
        } finally {
            buffer.close()
        }
    }

    companion object {
        internal fun fromReader(reader: WireReader): SingerIndexPage {
            return SingerIndexPage(
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readSequence({ reader -> SingerBrief.fromReader(reader) }) }),
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readSequence({ reader -> SingerBrief.fromReader(reader) }) }),
                reader.readOptionalValue({ reader -> SingerTagData.fromReader(reader) }),
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readI64() })
            )
        }

        internal fun fromByteArray(bytes: ByteArray): SingerIndexPage {
            val reader = WireReader(bytes)
            return fromReader(reader)
        }
    }
}


/**
 * 参考 `SimilarSinger`：相似歌手条目.
 */
data class SimilarSinger(
    val id: Long?,
    val mid: String?,
    val name: String?,
    /**
     * 图片标识（参考别名 `pic_mid`）.
     */
    val pmid: String?,
    val singerPic: String?,
    /**
     * 追踪信息.
     */
    val trace: String?,
    /**
     * 补充文案.
     */
    val abt: String?,
    /**
     * 附加标记.
     */
    val tf: String?
) {
    internal fun wireSize(): Int {
        return 1 + (this.id?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.mid?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.name?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.pmid?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.singerPic?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.trace?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.abt?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.tf?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0)
    }

    internal fun writeTo(writer: WireWriter) {
        writer.writeOptionalValue(this.id, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.mid, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.name, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.pmid, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.singerPic, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.trace, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.abt, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.tf, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
    }

    internal fun toByteArray(): ByteArray {
        val buffer = WireWriterPool.acquire(wireSize())
        val writer = buffer.writer
        try {
            writeTo(writer)
            return buffer.bytes()
        } finally {
            buffer.close()
        }
    }

    companion object {
        internal fun fromReader(reader: WireReader): SimilarSinger {
            return SimilarSinger(
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() })
            )
        }

        internal fun fromByteArray(bytes: ByteArray): SimilarSinger {
            val reader = WireReader(bytes)
            return fromReader(reader)
        }
    }
}


/**
 * 参考 `SimilarSingerResponse`：相似歌手列表接口的响应体.
 */
data class SimilarSingerList(
    val singerlist: List<SimilarSinger>?,
    val code: Long?,
    /**
     * 错误消息（上游键 `errMsg`）.
     */
    val errMsg: String?
) {
    internal fun wireSize(): Int {
        return 1 + (this.singerlist?.let { __boltffi_value_0 -> 4 + __boltffi_value_0.sumOf { __boltffi_value_1 -> val __boltffi_size: kotlin.Int = __boltffi_value_1.wireSize(); __boltffi_size } } ?: 0) + 1 + (this.code?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.errMsg?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0)
    }

    internal fun writeTo(writer: WireWriter) {
        writer.writeOptionalValue(this.singerlist, { writer, __boltffi_value_0 -> writer.writeSequence(__boltffi_value_0, __boltffi_value_0.size, { writer, __boltffi_value_1 -> __boltffi_value_1.writeTo(writer) }) })
        writer.writeOptionalValue(this.code, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.errMsg, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
    }

    internal fun toByteArray(): ByteArray {
        val buffer = WireWriterPool.acquire(wireSize())
        val writer = buffer.writer
        try {
            writeTo(writer)
            return buffer.bytes()
        } finally {
            buffer.close()
        }
    }

    companion object {
        internal fun fromReader(reader: WireReader): SimilarSingerList {
            return SimilarSingerList(
                reader.readOptionalValue({ reader -> reader.readSequence({ reader -> SimilarSinger.fromReader(reader) }) }),
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readString() })
            )
        }

        internal fun fromByteArray(bytes: ByteArray): SimilarSingerList {
            val reader = WireReader(bytes)
            return fromReader(reader)
        }
    }
}


/**
 * 参考 `TabMeta`：主页标签元信息.
 */
data class TabMeta(
    val tabId: String?,
    val tabName: String?,
    val title: String?
) {
    internal fun wireSize(): Int {
        return 1 + (this.tabId?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.tabName?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.title?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0)
    }

    internal fun writeTo(writer: WireWriter) {
        writer.writeOptionalValue(this.tabId, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.tabName, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.title, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
    }

    internal fun toByteArray(): ByteArray {
        val buffer = WireWriterPool.acquire(wireSize())
        val writer = buffer.writer
        try {
            writeTo(writer)
            return buffer.bytes()
        } finally {
            buffer.close()
        }
    }

    companion object {
        internal fun fromReader(reader: WireReader): TabMeta {
            return TabMeta(
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() })
            )
        }

        internal fun fromByteArray(bytes: ByteArray): TabMeta {
            val reader = WireReader(bytes)
            return fromReader(reader)
        }
    }
}


/**
 * 参考 `AlbumBrief`：主页专辑 Tab 里的专辑条目（别名与参考模型一致）.
 */
data class SingerAlbumBrief(
    /**
     * 专辑 ID（上游键是 `albumID`，大写 ID）.
     */
    val id: Long?,
    val mid: String?,
    val name: String?,
    /**
     * 专辑副标题（上游键 `albumTranName`）.
     */
    val subtitle: String?,
    /**
     * 发行日期.
     */
    val timePublic: String?,
    /**
     * 曲目数.
     */
    val totalNum: Long?,
    /**
     * 专辑类型文案.
     */
    val albumType: String?,
    val singerName: String?,
    val tags: List<String>?
) {
    internal fun wireSize(): Int {
        return 1 + (this.id?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.mid?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.name?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.subtitle?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.timePublic?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.totalNum?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.albumType?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.singerName?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.tags?.let { __boltffi_value_0 -> 4 + __boltffi_value_0.sumOf { __boltffi_value_1 -> val __boltffi_size: kotlin.Int = 4 + Utf8Codec.maxBytes(__boltffi_value_1); __boltffi_size } } ?: 0)
    }

    internal fun writeTo(writer: WireWriter) {
        writer.writeOptionalValue(this.id, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.mid, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.name, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.subtitle, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.timePublic, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.totalNum, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.albumType, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.singerName, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.tags, { writer, __boltffi_value_0 -> writer.writeSequence(__boltffi_value_0, __boltffi_value_0.size, { writer, __boltffi_value_1 -> writer.writeString(__boltffi_value_1) }) })
    }

    internal fun toByteArray(): ByteArray {
        val buffer = WireWriterPool.acquire(wireSize())
        val writer = buffer.writer
        try {
            writeTo(writer)
            return buffer.bytes()
        } finally {
            buffer.close()
        }
    }

    companion object {
        internal fun fromReader(reader: WireReader): SingerAlbumBrief {
            return SingerAlbumBrief(
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readSequence({ reader -> reader.readString() }) })
            )
        }

        internal fun fromByteArray(bytes: ByteArray): SingerAlbumBrief {
            val reader = WireReader(bytes)
            return fromReader(reader)
        }
    }
}


/**
 * 参考 `VideoBrief`：歌手 MV / 视频条目.
 */
data class SingerVideoBrief(
    /**
     * MV ID（参考别名 `mvid`）.
     */
    val id: Long?,
    val vid: String?,
    /**
     * MV 类型（参考字段名就是 `type`）.
     */
    val kind: Long?,
    val title: String?,
    val picurl: String?,
    val picformat: Long?,
    val duration: Long?,
    val playcnt: Long?,
    val pubdate: Long?,
    /**
     * 图标类型（上游键 `icon_type`）.
     */
    val iconType: Long?
) {
    internal fun wireSize(): Int {
        return 1 + (this.id?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.vid?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.kind?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.title?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.picurl?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.picformat?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.duration?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.playcnt?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.pubdate?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.iconType?.let { __boltffi_value_0 -> 8 } ?: 0)
    }

    internal fun writeTo(writer: WireWriter) {
        writer.writeOptionalValue(this.id, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.vid, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.kind, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.title, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.picurl, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.picformat, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.duration, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.playcnt, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.pubdate, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.iconType, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
    }

    internal fun toByteArray(): ByteArray {
        val buffer = WireWriterPool.acquire(wireSize())
        val writer = buffer.writer
        try {
            writeTo(writer)
            return buffer.bytes()
        } finally {
            buffer.close()
        }
    }

    companion object {
        internal fun fromReader(reader: WireReader): SingerVideoBrief {
            return SingerVideoBrief(
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readI64() })
            )
        }

        internal fun fromByteArray(bytes: ByteArray): SingerVideoBrief {
            val reader = WireReader(bytes)
            return fromReader(reader)
        }
    }
}


/**
 * 参考 `HomepageTabDetailResponse`：歌手主页标签页详情.
 *
 * `introduction_tab` 在参考里是 `list[dict]`（原样透传的简介结构），这里存
 * JSON 文本：`#[data]` 认不出 `serde_json::Value`，而经 serde 进出时它仍是
 * 真正的对象数组（见 `raw_json_serialize`）。`song_tab` 的行就是曲目本体
 * （参考 jsonpath 是 `$.SongTab.List[*]`），复用组件既有的曲目模型，
 * 宿主在歌手页与「歌手歌曲」看到同一种行。
 */
data class HomepageTabDetail(
    /**
     * 当前标签页 ID（上游键 `TabID`）.
     */
    val tabId: String?,
    /**
     * 是否还有更多结果.
     */
    val hasMore: Long?,
    /**
     * 是否需要展示标签.
     */
    val needShowTab: Long?,
    val order: Long?,
    /**
     * 标签页元信息列表（上游键 `TabList`）.
     */
    val tabList: List<TabMeta>?,
    /**
     * 简介标签内容（原样透传，JSON 文本）.
     */
    val introductionTab: String?,
    /**
     * 歌曲标签内容.
     */
    val songTab: List<Track>?,
    /**
     * 专辑标签内容.
     */
    val albumTab: List<SingerAlbumBrief>?,
    /**
     * 视频标签内容.
     */
    val videoTab: List<SingerVideoBrief>?
) {
    internal fun wireSize(): Int {
        return 1 + (this.tabId?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.hasMore?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.needShowTab?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.order?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.tabList?.let { __boltffi_value_0 -> 4 + __boltffi_value_0.sumOf { __boltffi_value_1 -> val __boltffi_size: kotlin.Int = __boltffi_value_1.wireSize(); __boltffi_size } } ?: 0) + 1 + (this.introductionTab?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.songTab?.let { __boltffi_value_0 -> 4 + __boltffi_value_0.sumOf { __boltffi_value_1 -> val __boltffi_size: kotlin.Int = __boltffi_value_1.wireSize(); __boltffi_size } } ?: 0) + 1 + (this.albumTab?.let { __boltffi_value_0 -> 4 + __boltffi_value_0.sumOf { __boltffi_value_1 -> val __boltffi_size: kotlin.Int = __boltffi_value_1.wireSize(); __boltffi_size } } ?: 0) + 1 + (this.videoTab?.let { __boltffi_value_0 -> 4 + __boltffi_value_0.sumOf { __boltffi_value_1 -> val __boltffi_size: kotlin.Int = __boltffi_value_1.wireSize(); __boltffi_size } } ?: 0)
    }

    internal fun writeTo(writer: WireWriter) {
        writer.writeOptionalValue(this.tabId, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.hasMore, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.needShowTab, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.order, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.tabList, { writer, __boltffi_value_0 -> writer.writeSequence(__boltffi_value_0, __boltffi_value_0.size, { writer, __boltffi_value_1 -> __boltffi_value_1.writeTo(writer) }) })
        writer.writeOptionalValue(this.introductionTab, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.songTab, { writer, __boltffi_value_0 -> writer.writeSequence(__boltffi_value_0, __boltffi_value_0.size, { writer, __boltffi_value_1 -> __boltffi_value_1.writeTo(writer) }) })
        writer.writeOptionalValue(this.albumTab, { writer, __boltffi_value_0 -> writer.writeSequence(__boltffi_value_0, __boltffi_value_0.size, { writer, __boltffi_value_1 -> __boltffi_value_1.writeTo(writer) }) })
        writer.writeOptionalValue(this.videoTab, { writer, __boltffi_value_0 -> writer.writeSequence(__boltffi_value_0, __boltffi_value_0.size, { writer, __boltffi_value_1 -> __boltffi_value_1.writeTo(writer) }) })
    }

    internal fun toByteArray(): ByteArray {
        val buffer = WireWriterPool.acquire(wireSize())
        val writer = buffer.writer
        try {
            writeTo(writer)
            return buffer.bytes()
        } finally {
            buffer.close()
        }
    }

    companion object {
        internal fun fromReader(reader: WireReader): HomepageTabDetail {
            return HomepageTabDetail(
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readSequence({ reader -> TabMeta.fromReader(reader) }) }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readSequence({ reader -> Track.fromReader(reader) }) }),
                reader.readOptionalValue({ reader -> reader.readSequence({ reader -> SingerAlbumBrief.fromReader(reader) }) }),
                reader.readOptionalValue({ reader -> reader.readSequence({ reader -> SingerVideoBrief.fromReader(reader) }) })
            )
        }

        internal fun fromByteArray(bytes: ByteArray): HomepageTabDetail {
            val reader = WireReader(bytes)
            return fromReader(reader)
        }
    }
}


/**
 * 参考 `SingerNameSpecialDisplayResponse`：歌手名称透明 PNG 展示信息.
 */
data class SingerNameSpecialDisplay(
    /**
     * 展示类型：2 表示名称图片，0 表示无特殊展示.
     */
    val displayType: Long?,
    /**
     * 透明 PNG 地址；无名称图片时为空.
     */
    val picFile: String?,
    /**
     * 签名与名称重叠比例.
     */
    val signatureNameOverlapRatio: Double?,
    /**
     * 歌手名称.
     */
    val name: String?
) {
    internal fun wireSize(): Int {
        return 1 + (this.displayType?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.picFile?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.signatureNameOverlapRatio?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.name?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0)
    }

    internal fun writeTo(writer: WireWriter) {
        writer.writeOptionalValue(this.displayType, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.picFile, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.signatureNameOverlapRatio, { writer, __boltffi_value_0 -> writer.writeF64(__boltffi_value_0) })
        writer.writeOptionalValue(this.name, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
    }

    internal fun toByteArray(): ByteArray {
        val buffer = WireWriterPool.acquire(wireSize())
        val writer = buffer.writer
        try {
            writeTo(writer)
            return buffer.bytes()
        } finally {
            buffer.close()
        }
    }

    companion object {
        internal fun fromReader(reader: WireReader): SingerNameSpecialDisplay {
            return SingerNameSpecialDisplay(
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readF64() }),
                reader.readOptionalValue({ reader -> reader.readString() })
            )
        }

        internal fun fromByteArray(bytes: ByteArray): SingerNameSpecialDisplay {
            val reader = WireReader(bytes)
            return fromReader(reader)
        }
    }
}


/**
 * 参考 `SingerMvListResponse`：歌手 MV 列表接口的响应体.
 */
data class SingerMvList(
    /**
     * MV 总数.
     */
    val total: Long?,
    /**
     * 当前页 MV 列表（参考字段名 `mv_list`，上游键是 `list`）.
     */
    val mvList: List<SingerVideoBrief>?
) {
    internal fun wireSize(): Int {
        return 1 + (this.total?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.mvList?.let { __boltffi_value_0 -> 4 + __boltffi_value_0.sumOf { __boltffi_value_1 -> val __boltffi_size: kotlin.Int = __boltffi_value_1.wireSize(); __boltffi_size } } ?: 0)
    }

    internal fun writeTo(writer: WireWriter) {
        writer.writeOptionalValue(this.total, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.mvList, { writer, __boltffi_value_0 -> writer.writeSequence(__boltffi_value_0, __boltffi_value_0.size, { writer, __boltffi_value_1 -> __boltffi_value_1.writeTo(writer) }) })
    }

    internal fun toByteArray(): ByteArray {
        val buffer = WireWriterPool.acquire(wireSize())
        val writer = buffer.writer
        try {
            writeTo(writer)
            return buffer.bytes()
        } finally {
            buffer.close()
        }
    }

    companion object {
        internal fun fromReader(reader: WireReader): SingerMvList {
            return SingerMvList(
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readSequence({ reader -> SingerVideoBrief.fromReader(reader) }) })
            )
        }

        internal fun fromByteArray(bytes: ByteArray): SingerMvList {
            val reader = WireReader(bytes)
            return fromReader(reader)
        }
    }
}


/**
 * 参考 `SongQueryInfo`：`query_songs` 的单项查询条件.
 *
 * `id` 与 `mid` 必须**二选一**（参考的 `ValueError`），`song_type` 缺省 0。
 */
data class SongQueryInfo(
    /**
     * 歌曲 ID.
     */
    val id: Long?,
    /**
     * 歌曲 MID.
     */
    val mid: String?,
    /**
     * 歌曲类型.
     */
    val songType: Long?
) {
    internal fun wireSize(): Int {
        return 1 + (this.id?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.mid?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.songType?.let { __boltffi_value_0 -> 8 } ?: 0)
    }

    internal fun writeTo(writer: WireWriter) {
        writer.writeOptionalValue(this.id, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.mid, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.songType, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
    }

    internal fun toByteArray(): ByteArray {
        val buffer = WireWriterPool.acquire(wireSize())
        val writer = buffer.writer
        try {
            writeTo(writer)
            return buffer.bytes()
        } finally {
            buffer.close()
        }
    }

    companion object {
        internal fun fromReader(reader: WireReader): SongQueryInfo {
            return SongQueryInfo(
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readI64() })
            )
        }

        internal fun fromByteArray(bytes: ByteArray): SongQueryInfo {
            val reader = WireReader(bytes)
            return fromReader(reader)
        }
    }
}


/**
 * 参考 `SongFileInfo`：`resolve_song_urls` 的单项取流条件.
 *
 * `file_type` 见 [`SongFileInfo::file_type`] 的说明；不给就用方法级的 `file_type`
 * （缺省 `MP3_128`），`song_type` 缺省 0。
 */
data class SongFileInfo(
    /**
     * 歌曲 MID.
     */
    val mid: String,
    /**
     * 歌曲文件类型：参考枚举的成员名（`MP3_128` / `FLAC` /
     * `EncryptedSongFileType.FLAC` / `RingSongFileType.RING_96` …）或 4 位前缀
     * （`M500` / `F0M0`）。
     */
    val fileType: String?,
    /**
     * 歌曲类型.
     */
    val songType: Long?,
    /**
     * 媒体文件 mid；给了就按它拼文件名（否则把歌曲 mid 写两遍）.
     */
    val mediaMid: String?
) {
    internal fun wireSize(): Int {
        return 4 + Utf8Codec.maxBytes(this.mid) + 1 + (this.fileType?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.songType?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.mediaMid?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0)
    }

    internal fun writeTo(writer: WireWriter) {
        writer.writeString(this.mid)
        writer.writeOptionalValue(this.fileType, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.songType, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.mediaMid, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
    }

    internal fun toByteArray(): ByteArray {
        val buffer = WireWriterPool.acquire(wireSize())
        val writer = buffer.writer
        try {
            writeTo(writer)
            return buffer.bytes()
        } finally {
            buffer.close()
        }
    }

    companion object {
        internal fun fromReader(reader: WireReader): SongFileInfo {
            return SongFileInfo(
                reader.readString(),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readString() })
            )
        }

        internal fun fromByteArray(bytes: ByteArray): SongFileInfo {
            val reader = WireReader(bytes)
            return fromReader(reader)
        }
    }
}


/**
 * 参考 `QuerySongResponse`：批量歌曲查询响应.
 */
data class QuerySongResponse(
    /**
     * 按请求条件返回的曲目列表（复用组件既有的曲目模型）.
     */
    val tracks: List<Track>?
) {
    internal fun wireSize(): Int {
        return 1 + (this.tracks?.let { __boltffi_value_0 -> 4 + __boltffi_value_0.sumOf { __boltffi_value_1 -> val __boltffi_size: kotlin.Int = __boltffi_value_1.wireSize(); __boltffi_size } } ?: 0)
    }

    internal fun writeTo(writer: WireWriter) {
        writer.writeOptionalValue(this.tracks, { writer, __boltffi_value_0 -> writer.writeSequence(__boltffi_value_0, __boltffi_value_0.size, { writer, __boltffi_value_1 -> __boltffi_value_1.writeTo(writer) }) })
    }

    internal fun toByteArray(): ByteArray {
        val buffer = WireWriterPool.acquire(wireSize())
        val writer = buffer.writer
        try {
            writeTo(writer)
            return buffer.bytes()
        } finally {
            buffer.close()
        }
    }

    companion object {
        internal fun fromReader(reader: WireReader): QuerySongResponse {
            return QuerySongResponse(
                reader.readOptionalValue({ reader -> reader.readSequence({ reader -> Track.fromReader(reader) }) })
            )
        }

        internal fun fromByteArray(bytes: ByteArray): QuerySongResponse {
            val reader = WireReader(bytes)
            return fromReader(reader)
        }
    }
}


/**
 * 参考 `UrlinfoItem`：单个文件的授权结果.
 */
data class UrlinfoItem(
    /**
     * 歌曲 mid（上游键 `songmid`）.
     */
    val mid: String?,
    /**
     * 请求的目标文件名.
     */
    val filename: String?,
    /**
     * 相对下载路径；要与 CDN 域名（`fetch_cdn_dispatch` 的 `sip`）拼接后才能访问.
     */
    val purl: String?,
    /**
     * 资源访问令牌.
     */
    val vkey: String?,
    /**
     * 加密资源解密密钥.
     */
    val ekey: String?,
    /**
     * 单个文件的业务结果码：`0` 成功、`104003` 无权限、`104004` 取票失败、
     * `104013` 播放设备受限.
     */
    val result: Long?
) {
    internal fun wireSize(): Int {
        return 1 + (this.mid?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.filename?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.purl?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.vkey?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.ekey?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.result?.let { __boltffi_value_0 -> 8 } ?: 0)
    }

    internal fun writeTo(writer: WireWriter) {
        writer.writeOptionalValue(this.mid, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.filename, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.purl, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.vkey, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.ekey, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.result, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
    }

    internal fun toByteArray(): ByteArray {
        val buffer = WireWriterPool.acquire(wireSize())
        val writer = buffer.writer
        try {
            writeTo(writer)
            return buffer.bytes()
        } finally {
            buffer.close()
        }
    }

    companion object {
        internal fun fromReader(reader: WireReader): UrlinfoItem {
            return UrlinfoItem(
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readI64() })
            )
        }

        internal fun fromByteArray(bytes: ByteArray): UrlinfoItem {
            val reader = WireReader(bytes)
            return fromReader(reader)
        }
    }
}


/**
 * 参考 `GetSongUrlsResponse`：歌曲播放地址响应.
 */
data class GetSongUrlsResponse(
    /**
     * 链接过期时间（秒）.
     */
    val expiration: Long?,
    /**
     * 每个目标文件对应的授权与路径信息（上游键 `midurlinfo`）.
     */
    val `data`: List<UrlinfoItem>?
) {
    internal fun wireSize(): Int {
        return 1 + (this.expiration?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.`data`?.let { __boltffi_value_0 -> 4 + __boltffi_value_0.sumOf { __boltffi_value_1 -> val __boltffi_size: kotlin.Int = __boltffi_value_1.wireSize(); __boltffi_size } } ?: 0)
    }

    internal fun writeTo(writer: WireWriter) {
        writer.writeOptionalValue(this.expiration, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.`data`, { writer, __boltffi_value_0 -> writer.writeSequence(__boltffi_value_0, __boltffi_value_0.size, { writer, __boltffi_value_1 -> __boltffi_value_1.writeTo(writer) }) })
    }

    internal fun toByteArray(): ByteArray {
        val buffer = WireWriterPool.acquire(wireSize())
        val writer = buffer.writer
        try {
            writeTo(writer)
            return buffer.bytes()
        } finally {
            buffer.close()
        }
    }

    companion object {
        internal fun fromReader(reader: WireReader): GetSongUrlsResponse {
            return GetSongUrlsResponse(
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readSequence({ reader -> UrlinfoItem.fromReader(reader) }) })
            )
        }

        internal fun fromByteArray(bytes: ByteArray): GetSongUrlsResponse {
            val reader = WireReader(bytes)
            return fromReader(reader)
        }
    }
}


/**
 * 参考 `CdnDispatchSipInfo`：CDN 调度中的单个节点信息.
 */
data class CdnDispatchSipInfo(
    /**
     * CDN 节点地址.
     */
    val cdn: String?,
    /**
     * 是否支持 QUIC.
     */
    val quic: Long?,
    /**
     * IP 栈类型.
     */
    val ipstack: Long?,
    /**
     * QUIC 主机名.
     */
    val quichost: String?,
    /**
     * 是否支持明文 QUIC（上游键 `plaintextquic`）.
     */
    val plaintextQuic: Long?,
    /**
     * 是否支持加密 QUIC（上游键 `encryptquic`）.
     */
    val encryptQuic: Long?
) {
    internal fun wireSize(): Int {
        return 1 + (this.cdn?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.quic?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.ipstack?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.quichost?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.plaintextQuic?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.encryptQuic?.let { __boltffi_value_0 -> 8 } ?: 0)
    }

    internal fun writeTo(writer: WireWriter) {
        writer.writeOptionalValue(this.cdn, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.quic, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.ipstack, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.quichost, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.plaintextQuic, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.encryptQuic, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
    }

    internal fun toByteArray(): ByteArray {
        val buffer = WireWriterPool.acquire(wireSize())
        val writer = buffer.writer
        try {
            writeTo(writer)
            return buffer.bytes()
        } finally {
            buffer.close()
        }
    }

    companion object {
        internal fun fromReader(reader: WireReader): CdnDispatchSipInfo {
            return CdnDispatchSipInfo(
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readI64() })
            )
        }

        internal fun fromByteArray(bytes: ByteArray): CdnDispatchSipInfo {
            val reader = WireReader(bytes)
            return fromReader(reader)
        }
    }
}


/**
 * 参考 `GetCdnDispatchResponse`：获取音频 CDN 调度响应.
 */
data class GetCdnDispatchResponse(
    /**
     * 接口返回码.
     */
    val retcode: Long?,
    /**
     * 可用 CDN 根地址列表（取流时把 `UrlinfoItem.purl` 拼在它后面）.
     */
    val sip: List<String>?,
    /**
     * 可用 CDN 节点明细列表.
     */
    val sipinfo: List<CdnDispatchSipInfo>?,
    /**
     * 用于测试 CDN 可用性的文件路径（上游键 `keepalivefile`）.
     */
    val testFile: String?,
    /**
     * 数据有效期（秒）.
     */
    val expiration: Long?,
    /**
     * 建议刷新间隔（秒）.
     */
    val refreshTime: Long?,
    /**
     * 建议缓存时长（秒）.
     */
    val cacheTime: Long?
) {
    internal fun wireSize(): Int {
        return 1 + (this.retcode?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.sip?.let { __boltffi_value_0 -> 4 + __boltffi_value_0.sumOf { __boltffi_value_1 -> val __boltffi_size: kotlin.Int = 4 + Utf8Codec.maxBytes(__boltffi_value_1); __boltffi_size } } ?: 0) + 1 + (this.sipinfo?.let { __boltffi_value_0 -> 4 + __boltffi_value_0.sumOf { __boltffi_value_1 -> val __boltffi_size: kotlin.Int = __boltffi_value_1.wireSize(); __boltffi_size } } ?: 0) + 1 + (this.testFile?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.expiration?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.refreshTime?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.cacheTime?.let { __boltffi_value_0 -> 8 } ?: 0)
    }

    internal fun writeTo(writer: WireWriter) {
        writer.writeOptionalValue(this.retcode, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.sip, { writer, __boltffi_value_0 -> writer.writeSequence(__boltffi_value_0, __boltffi_value_0.size, { writer, __boltffi_value_1 -> writer.writeString(__boltffi_value_1) }) })
        writer.writeOptionalValue(this.sipinfo, { writer, __boltffi_value_0 -> writer.writeSequence(__boltffi_value_0, __boltffi_value_0.size, { writer, __boltffi_value_1 -> __boltffi_value_1.writeTo(writer) }) })
        writer.writeOptionalValue(this.testFile, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.expiration, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.refreshTime, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.cacheTime, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
    }

    internal fun toByteArray(): ByteArray {
        val buffer = WireWriterPool.acquire(wireSize())
        val writer = buffer.writer
        try {
            writeTo(writer)
            return buffer.bytes()
        } finally {
            buffer.close()
        }
    }

    companion object {
        internal fun fromReader(reader: WireReader): GetCdnDispatchResponse {
            return GetCdnDispatchResponse(
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readSequence({ reader -> reader.readString() }) }),
                reader.readOptionalValue({ reader -> reader.readSequence({ reader -> CdnDispatchSipInfo.fromReader(reader) }) }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readI64() })
            )
        }

        internal fun fromByteArray(bytes: ByteArray): GetCdnDispatchResponse {
            val reader = WireReader(bytes)
            return fromReader(reader)
        }
    }
}


/**
 * 参考 `GetOtherVersionResponse`：获取歌曲其他版本结果.
 */
data class GetOtherVersionResponse(
    /**
     * 其他版本歌曲列表（上游键 `versionList`；复用组件既有的曲目模型）.
     */
    val `data`: List<Track>?
) {
    internal fun wireSize(): Int {
        return 1 + (this.`data`?.let { __boltffi_value_0 -> 4 + __boltffi_value_0.sumOf { __boltffi_value_1 -> val __boltffi_size: kotlin.Int = __boltffi_value_1.wireSize(); __boltffi_size } } ?: 0)
    }

    internal fun writeTo(writer: WireWriter) {
        writer.writeOptionalValue(this.`data`, { writer, __boltffi_value_0 -> writer.writeSequence(__boltffi_value_0, __boltffi_value_0.size, { writer, __boltffi_value_1 -> __boltffi_value_1.writeTo(writer) }) })
    }

    internal fun toByteArray(): ByteArray {
        val buffer = WireWriterPool.acquire(wireSize())
        val writer = buffer.writer
        try {
            writeTo(writer)
            return buffer.bytes()
        } finally {
            buffer.close()
        }
    }

    companion object {
        internal fun fromReader(reader: WireReader): GetOtherVersionResponse {
            return GetOtherVersionResponse(
                reader.readOptionalValue({ reader -> reader.readSequence({ reader -> Track.fromReader(reader) }) })
            )
        }

        internal fun fromByteArray(bytes: ByteArray): GetOtherVersionResponse {
            val reader = WireReader(bytes)
            return fromReader(reader)
        }
    }
}


/**
 * 参考 `SongProducer`：歌曲制作人项.
 */
data class SongProducer(
    /**
     * 制作人类型（上游键 `Type`；Rust 侧换个名字、JSON 上仍是 `type`）.
     */
    val kind: Long?,
    /**
     * 制作人名称（上游键 `Name`）.
     */
    val name: String?,
    /**
     * 制作人头像（上游键 `Icon`）.
     */
    val icon: String?,
    /**
     * 制作人跳转链接（上游键 `Scheme`）.
     */
    val scheme: String?,
    /**
     * 制作人 singer mid（上游键 `SingerMid`）.
     */
    val singerMid: String?,
    /**
     * 关注状态（上游键 `Follow`）.
     */
    val follow: Long?
) {
    internal fun wireSize(): Int {
        return 1 + (this.kind?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.name?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.icon?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.scheme?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.singerMid?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.follow?.let { __boltffi_value_0 -> 8 } ?: 0)
    }

    internal fun writeTo(writer: WireWriter) {
        writer.writeOptionalValue(this.kind, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.name, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.icon, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.scheme, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.singerMid, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.follow, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
    }

    internal fun toByteArray(): ByteArray {
        val buffer = WireWriterPool.acquire(wireSize())
        val writer = buffer.writer
        try {
            writeTo(writer)
            return buffer.bytes()
        } finally {
            buffer.close()
        }
    }

    companion object {
        internal fun fromReader(reader: WireReader): SongProducer {
            return SongProducer(
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readI64() })
            )
        }

        internal fun fromByteArray(bytes: ByteArray): SongProducer {
            val reader = WireReader(bytes)
            return fromReader(reader)
        }
    }
}


/**
 * 参考 `SongProducerGroup`：歌曲制作人信息分组.
 */
data class SongProducerGroup(
    /**
     * 分组标题（上游键 `Title`）.
     */
    val title: String?,
    /**
     * 该分组下的制作人列表（上游键 `Producers`）.
     */
    val producers: List<SongProducer>?,
    /**
     * 分组类型（上游键 `Type`；JSON 上仍是 `type`）.
     */
    val kind: Long?
) {
    internal fun wireSize(): Int {
        return 1 + (this.title?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.producers?.let { __boltffi_value_0 -> 4 + __boltffi_value_0.sumOf { __boltffi_value_1 -> val __boltffi_size: kotlin.Int = __boltffi_value_1.wireSize(); __boltffi_size } } ?: 0) + 1 + (this.kind?.let { __boltffi_value_0 -> 8 } ?: 0)
    }

    internal fun writeTo(writer: WireWriter) {
        writer.writeOptionalValue(this.title, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.producers, { writer, __boltffi_value_0 -> writer.writeSequence(__boltffi_value_0, __boltffi_value_0.size, { writer, __boltffi_value_1 -> __boltffi_value_1.writeTo(writer) }) })
        writer.writeOptionalValue(this.kind, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
    }

    internal fun toByteArray(): ByteArray {
        val buffer = WireWriterPool.acquire(wireSize())
        val writer = buffer.writer
        try {
            writeTo(writer)
            return buffer.bytes()
        } finally {
            buffer.close()
        }
    }

    companion object {
        internal fun fromReader(reader: WireReader): SongProducerGroup {
            return SongProducerGroup(
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readSequence({ reader -> SongProducer.fromReader(reader) }) }),
                reader.readOptionalValue({ reader -> reader.readI64() })
            )
        }

        internal fun fromByteArray(bytes: ByteArray): SongProducerGroup {
            val reader = WireReader(bytes)
            return fromReader(reader)
        }
    }
}


/**
 * 参考 `GetProducerResponse`：歌曲制作人响应.
 */
data class GetProducerResponse(
    /**
     * 按职责分组的制作人列表（上游键 `Lst`）.
     */
    val `data`: List<SongProducerGroup>?,
    /**
     * 附带的摘要说明文案（上游键 `ReinforceMsg`）.
     */
    val reinforceMsg: String?
) {
    internal fun wireSize(): Int {
        return 1 + (this.`data`?.let { __boltffi_value_0 -> 4 + __boltffi_value_0.sumOf { __boltffi_value_1 -> val __boltffi_size: kotlin.Int = __boltffi_value_1.wireSize(); __boltffi_size } } ?: 0) + 1 + (this.reinforceMsg?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0)
    }

    internal fun writeTo(writer: WireWriter) {
        writer.writeOptionalValue(this.`data`, { writer, __boltffi_value_0 -> writer.writeSequence(__boltffi_value_0, __boltffi_value_0.size, { writer, __boltffi_value_1 -> __boltffi_value_1.writeTo(writer) }) })
        writer.writeOptionalValue(this.reinforceMsg, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
    }

    internal fun toByteArray(): ByteArray {
        val buffer = WireWriterPool.acquire(wireSize())
        val writer = buffer.writer
        try {
            writeTo(writer)
            return buffer.bytes()
        } finally {
            buffer.close()
        }
    }

    companion object {
        internal fun fromReader(reader: WireReader): GetProducerResponse {
            return GetProducerResponse(
                reader.readOptionalValue({ reader -> reader.readSequence({ reader -> SongProducerGroup.fromReader(reader) }) }),
                reader.readOptionalValue({ reader -> reader.readString() })
            )
        }

        internal fun fromByteArray(bytes: ByteArray): GetProducerResponse {
            val reader = WireReader(bytes)
            return fromReader(reader)
        }
    }
}


/**
 * 参考 `GetFavNumResponse`：歌曲收藏人数响应.
 */
data class GetFavNumResponse(
    /**
     * 以歌曲标识为键的收藏人数原始值映射（上游键 `m_numbers`）.
     */
    val numbers: Map<String, Long>?,
    /**
     * 对应的收藏人数展示文案映射（上游键 `m_show`）.
     */
    val show: Map<String, String>?
) {
    internal fun wireSize(): Int {
        return 1 + (this.numbers?.let { __boltffi_value_0 -> __boltffi_value_0.wireSize({ __boltffi_value_1 -> 4 + Utf8Codec.maxBytes(__boltffi_value_1) }, { __boltffi_value_2 -> 8 }) } ?: 0) + 1 + (this.show?.let { __boltffi_value_0 -> __boltffi_value_0.wireSize({ __boltffi_value_1 -> 4 + Utf8Codec.maxBytes(__boltffi_value_1) }, { __boltffi_value_2 -> 4 + Utf8Codec.maxBytes(__boltffi_value_2) }) } ?: 0)
    }

    internal fun writeTo(writer: WireWriter) {
        writer.writeOptionalValue(this.numbers, { writer, __boltffi_value_0 -> writer.writeMap(__boltffi_value_0, { writer, __boltffi_value_1 -> writer.writeString(__boltffi_value_1) }, { writer, __boltffi_value_2 -> writer.writeI64(__boltffi_value_2) }) })
        writer.writeOptionalValue(this.show, { writer, __boltffi_value_0 -> writer.writeMap(__boltffi_value_0, { writer, __boltffi_value_1 -> writer.writeString(__boltffi_value_1) }, { writer, __boltffi_value_2 -> writer.writeString(__boltffi_value_2) }) })
    }

    internal fun toByteArray(): ByteArray {
        val buffer = WireWriterPool.acquire(wireSize())
        val writer = buffer.writer
        try {
            writeTo(writer)
            return buffer.bytes()
        } finally {
            buffer.close()
        }
    }

    companion object {
        internal fun fromReader(reader: WireReader): GetFavNumResponse {
            return GetFavNumResponse(
                reader.readOptionalValue({ reader -> reader.readMap({ reader -> reader.readString() }, { reader -> reader.readI64() }) }),
                reader.readOptionalValue({ reader -> reader.readMap({ reader -> reader.readString() }, { reader -> reader.readString() }) })
            )
        }

        internal fun fromByteArray(bytes: ByteArray): GetFavNumResponse {
            val reader = WireReader(bytes)
            return fromReader(reader)
        }
    }
}


/**
 * 参考 `SimilarSongGroup`：一组相似歌曲推荐卡片.
 *
 * 参考的 `song` 字段用 jsonpath `$.songs[*].track` 从每个分组自己的
 * `songs` 里抽出曲目，上游给的是 `{"track": {...}}` 或曲目本体；这里交给组件
 * 既有的曲目解码（`methods::decoded_tracks`，它认这两种形状）。
 */
data class SimilarSongGroup(
    /**
     * 推荐分组的标题模板（参考字段是 snake_case，上游也就给这个拼写）.
     */
    val titleTemplate: String?,
    /**
     * 标题模板里的实际内容.
     */
    val titleContent: String?,
    /**
     * 当前推荐分组下的歌曲列表.
     */
    val song: List<Track>?
) {
    internal fun wireSize(): Int {
        return 1 + (this.titleTemplate?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.titleContent?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.song?.let { __boltffi_value_0 -> 4 + __boltffi_value_0.sumOf { __boltffi_value_1 -> val __boltffi_size: kotlin.Int = __boltffi_value_1.wireSize(); __boltffi_size } } ?: 0)
    }

    internal fun writeTo(writer: WireWriter) {
        writer.writeOptionalValue(this.titleTemplate, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.titleContent, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.song, { writer, __boltffi_value_0 -> writer.writeSequence(__boltffi_value_0, __boltffi_value_0.size, { writer, __boltffi_value_1 -> __boltffi_value_1.writeTo(writer) }) })
    }

    internal fun toByteArray(): ByteArray {
        val buffer = WireWriterPool.acquire(wireSize())
        val writer = buffer.writer
        try {
            writeTo(writer)
            return buffer.bytes()
        } finally {
            buffer.close()
        }
    }

    companion object {
        internal fun fromReader(reader: WireReader): SimilarSongGroup {
            return SimilarSongGroup(
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readSequence({ reader -> Track.fromReader(reader) }) })
            )
        }

        internal fun fromByteArray(bytes: ByteArray): SimilarSongGroup {
            val reader = WireReader(bytes)
            return fromReader(reader)
        }
    }
}


/**
 * 参考 `GetSimilarSongResponse`：相似歌曲推荐响应.
 */
data class GetSimilarSongResponse(
    /**
     * 本次推荐附带的歌曲标签列表（参考声明为 `list[dict]`，原样透传）.
     */
    val tag: String?,
    /**
     * 按卡片分组组织的相似歌曲结果.
     */
    val song: List<SimilarSongGroup>?
) {
    internal fun wireSize(): Int {
        return 1 + (this.tag?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.song?.let { __boltffi_value_0 -> 4 + __boltffi_value_0.sumOf { __boltffi_value_1 -> val __boltffi_size: kotlin.Int = __boltffi_value_1.wireSize(); __boltffi_size } } ?: 0)
    }

    internal fun writeTo(writer: WireWriter) {
        writer.writeOptionalValue(this.tag, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.song, { writer, __boltffi_value_0 -> writer.writeSequence(__boltffi_value_0, __boltffi_value_0.size, { writer, __boltffi_value_1 -> __boltffi_value_1.writeTo(writer) }) })
    }

    internal fun toByteArray(): ByteArray {
        val buffer = WireWriterPool.acquire(wireSize())
        val writer = buffer.writer
        try {
            writeTo(writer)
            return buffer.bytes()
        } finally {
            buffer.close()
        }
    }

    companion object {
        internal fun fromReader(reader: WireReader): GetSimilarSongResponse {
            return GetSimilarSongResponse(
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readSequence({ reader -> SimilarSongGroup.fromReader(reader) }) })
            )
        }

        internal fun fromByteArray(bytes: ByteArray): GetSimilarSongResponse {
            val reader = WireReader(bytes)
            return fromReader(reader)
        }
    }
}


/**
 * 参考 `SongLabel`：歌曲标签项.
 */
data class SongLabel(
    /**
     * 标签 ID.
     */
    val id: Long?,
    /**
     * 标签文本（上游键 `tagTxt`）.
     */
    val tagTxt: String?,
    /**
     * 标签图标地址（上游键 `tagIcon`）.
     */
    val tagIcon: String?,
    /**
     * 标签跳转链接（上游键 `tagUrl`）.
     */
    val tagUrl: String?,
    /**
     * 标签类型（上游键 `tagType`）.
     */
    val tagType: Long?,
    /**
     * 标签所属分类.
     */
    val species: Long?
) {
    internal fun wireSize(): Int {
        return 1 + (this.id?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.tagTxt?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.tagIcon?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.tagUrl?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.tagType?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.species?.let { __boltffi_value_0 -> 8 } ?: 0)
    }

    internal fun writeTo(writer: WireWriter) {
        writer.writeOptionalValue(this.id, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.tagTxt, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.tagIcon, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.tagUrl, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.tagType, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.species, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
    }

    internal fun toByteArray(): ByteArray {
        val buffer = WireWriterPool.acquire(wireSize())
        val writer = buffer.writer
        try {
            writeTo(writer)
            return buffer.bytes()
        } finally {
            buffer.close()
        }
    }

    companion object {
        internal fun fromReader(reader: WireReader): SongLabel {
            return SongLabel(
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readI64() })
            )
        }

        internal fun fromByteArray(bytes: ByteArray): SongLabel {
            val reader = WireReader(bytes)
            return fromReader(reader)
        }
    }
}


/**
 * 参考 `GetSongLabelsResponse`：获取歌曲标签结果.
 */
data class GetSongLabelsResponse(
    /**
     * 歌曲标签列表.
     */
    val labels: List<SongLabel>?
) {
    internal fun wireSize(): Int {
        return 1 + (this.labels?.let { __boltffi_value_0 -> 4 + __boltffi_value_0.sumOf { __boltffi_value_1 -> val __boltffi_size: kotlin.Int = __boltffi_value_1.wireSize(); __boltffi_size } } ?: 0)
    }

    internal fun writeTo(writer: WireWriter) {
        writer.writeOptionalValue(this.labels, { writer, __boltffi_value_0 -> writer.writeSequence(__boltffi_value_0, __boltffi_value_0.size, { writer, __boltffi_value_1 -> __boltffi_value_1.writeTo(writer) }) })
    }

    internal fun toByteArray(): ByteArray {
        val buffer = WireWriterPool.acquire(wireSize())
        val writer = buffer.writer
        try {
            writeTo(writer)
            return buffer.bytes()
        } finally {
            buffer.close()
        }
    }

    companion object {
        internal fun fromReader(reader: WireReader): GetSongLabelsResponse {
            return GetSongLabelsResponse(
                reader.readOptionalValue({ reader -> reader.readSequence({ reader -> SongLabel.fromReader(reader) }) })
            )
        }

        internal fun fromByteArray(bytes: ByteArray): GetSongLabelsResponse {
            val reader = WireReader(bytes)
            return fromReader(reader)
        }
    }
}


/**
 * 参考 `RelatedPlaylist`（继承 `SongList`）：歌曲详情页关联歌单中的单个歌单摘要.
 */
data class RelatedPlaylist(
    /**
     * 歌单数字 ID（参考别名 `tid`/`dissid`）.
     */
    val id: Long?,
    /**
     * 目录 ID（参考别名 `dirId`）.
     */
    val dirid: Long?,
    /**
     * 歌单标题（参考别名 `dissname`/`name`/`dirName`）.
     */
    val title: String?,
    /**
     * 歌单封面地址（参考别名 `cover`/`logo`/`picUrl`）.
     */
    val picurl: String?,
    /**
     * 歌单简介（参考别名 `description`）.
     */
    val desc: String?,
    /**
     * 歌曲数量（参考别名 `songNum`/`song_cnt`）.
     */
    val songnum: Long?,
    /**
     * 播放量（参考别名 `playCnt`/`play_cnt`）.
     */
    val listennum: Long?,
    /**
     * 歌单创建者名称.
     */
    val creator: String?
) {
    internal fun wireSize(): Int {
        return 1 + (this.id?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.dirid?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.title?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.picurl?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.desc?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.songnum?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.listennum?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.creator?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0)
    }

    internal fun writeTo(writer: WireWriter) {
        writer.writeOptionalValue(this.id, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.dirid, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.title, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.picurl, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.desc, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.songnum, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.listennum, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.creator, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
    }

    internal fun toByteArray(): ByteArray {
        val buffer = WireWriterPool.acquire(wireSize())
        val writer = buffer.writer
        try {
            writeTo(writer)
            return buffer.bytes()
        } finally {
            buffer.close()
        }
    }

    companion object {
        internal fun fromReader(reader: WireReader): RelatedPlaylist {
            return RelatedPlaylist(
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readString() })
            )
        }

        internal fun fromByteArray(bytes: ByteArray): RelatedPlaylist {
            val reader = WireReader(bytes)
            return fromReader(reader)
        }
    }
}


/**
 * 参考 `GetRelatedSonglistResponse`：歌曲关联歌单响应.
 *
 * 参考的 `songlist` 用 jsonpath `$.vecPlaylistNew[*].playlists[*]` 把分组拍平；
 * 上游只在每组名下放 `playlists`。
 */
data class GetRelatedSonglistResponse(
    /**
     * 是否还有更多结果（上游键 `hasMore`）.
     */
    val hasMore: Long?,
    /**
     * 按推荐分组展开后的相关歌单列表.
     */
    val songlist: List<RelatedPlaylist>?
) {
    internal fun wireSize(): Int {
        return 1 + (this.hasMore?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.songlist?.let { __boltffi_value_0 -> 4 + __boltffi_value_0.sumOf { __boltffi_value_1 -> val __boltffi_size: kotlin.Int = __boltffi_value_1.wireSize(); __boltffi_size } } ?: 0)
    }

    internal fun writeTo(writer: WireWriter) {
        writer.writeOptionalValue(this.hasMore, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.songlist, { writer, __boltffi_value_0 -> writer.writeSequence(__boltffi_value_0, __boltffi_value_0.size, { writer, __boltffi_value_1 -> __boltffi_value_1.writeTo(writer) }) })
    }

    internal fun toByteArray(): ByteArray {
        val buffer = WireWriterPool.acquire(wireSize())
        val writer = buffer.writer
        try {
            writeTo(writer)
            return buffer.bytes()
        } finally {
            buffer.close()
        }
    }

    companion object {
        internal fun fromReader(reader: WireReader): GetRelatedSonglistResponse {
            return GetRelatedSonglistResponse(
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readSequence({ reader -> RelatedPlaylist.fromReader(reader) }) })
            )
        }

        internal fun fromByteArray(bytes: ByteArray): GetRelatedSonglistResponse {
            val reader = WireReader(bytes)
            return fromReader(reader)
        }
    }
}


/**
 * 参考 `RelatedMv.MVSinger`：关联 MV 中的歌手摘要（基础 `Singer` 多一个 `picurl`）.
 */
data class RelatedMvSinger(
    /**
     * 歌手数字 ID.
     */
    val id: Long?,
    /**
     * 歌手 Media MID.
     */
    val mid: String?,
    /**
     * 歌手名称.
     */
    val name: String?,
    /**
     * 歌手展示标题（参考回退到名称）.
     */
    val title: String?,
    /**
     * 歌手类型（参考字段名 `type`，别名 `SingerType`/`vt`）.
     */
    val kind: Long?,
    /**
     * 与歌手关联的用户 ID.
     */
    val uin: Long?,
    /**
     * 图片 Media ID.
     */
    val pmid: String?,
    /**
     * 歌手头像地址.
     */
    val picurl: String?
) {
    internal fun wireSize(): Int {
        return 1 + (this.id?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.mid?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.name?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.title?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.kind?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.uin?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.pmid?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.picurl?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0)
    }

    internal fun writeTo(writer: WireWriter) {
        writer.writeOptionalValue(this.id, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.mid, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.name, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.title, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.kind, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.uin, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.pmid, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.picurl, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
    }

    internal fun toByteArray(): ByteArray {
        val buffer = WireWriterPool.acquire(wireSize())
        val writer = buffer.writer
        try {
            writeTo(writer)
            return buffer.bytes()
        } finally {
            buffer.close()
        }
    }

    companion object {
        internal fun fromReader(reader: WireReader): RelatedMvSinger {
            return RelatedMvSinger(
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() })
            )
        }

        internal fun fromByteArray(bytes: ByteArray): RelatedMvSinger {
            val reader = WireReader(bytes)
            return fromReader(reader)
        }
    }
}


/**
 * 参考 `RelatedMv`（继承 `MV`）：歌曲详情页关联 MV 的摘要信息.
 */
data class RelatedMv(
    /**
     * MV 数字 ID（参考别名 `sid`/`mvid`/`singerId`）.
     */
    val id: Long?,
    /**
     * MV VID.
     */
    val vid: String?,
    /**
     * MV 类型（参考别名 `vt`）.
     */
    val kind: Long?,
    /**
     * MV 名称（参考别名 `mvname`/`title`）.
     */
    val name: String?,
    /**
     * MV 展示标题（参考别名 `title_main`/`name`）.
     */
    val title: String?,
    /**
     * MV 封面.
     */
    val picurl: String?,
    /**
     * MV 播放量.
     */
    val playcnt: Long?,
    /**
     * MV 关联歌手列表.
     */
    val singers: List<RelatedMvSinger>?
) {
    internal fun wireSize(): Int {
        return 1 + (this.id?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.vid?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.kind?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.name?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.title?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.picurl?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.playcnt?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.singers?.let { __boltffi_value_0 -> 4 + __boltffi_value_0.sumOf { __boltffi_value_1 -> val __boltffi_size: kotlin.Int = __boltffi_value_1.wireSize(); __boltffi_size } } ?: 0)
    }

    internal fun writeTo(writer: WireWriter) {
        writer.writeOptionalValue(this.id, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.vid, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.kind, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.name, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.title, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.picurl, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.playcnt, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.singers, { writer, __boltffi_value_0 -> writer.writeSequence(__boltffi_value_0, __boltffi_value_0.size, { writer, __boltffi_value_1 -> __boltffi_value_1.writeTo(writer) }) })
    }

    internal fun toByteArray(): ByteArray {
        val buffer = WireWriterPool.acquire(wireSize())
        val writer = buffer.writer
        try {
            writeTo(writer)
            return buffer.bytes()
        } finally {
            buffer.close()
        }
    }

    companion object {
        internal fun fromReader(reader: WireReader): RelatedMv {
            return RelatedMv(
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readSequence({ reader -> RelatedMvSinger.fromReader(reader) }) })
            )
        }

        internal fun fromByteArray(bytes: ByteArray): RelatedMv {
            val reader = WireReader(bytes)
            return fromReader(reader)
        }
    }
}


/**
 * 参考 `GetRelatedMvResponse`：歌曲关联 MV 响应.
 */
data class GetRelatedMvResponse(
    /**
     * 是否还有更多结果（上游键是小写的 `hasmore`）.
     */
    val hasMore: Long?,
    /**
     * 当前返回的相关 MV 列表（上游键 `list`）.
     */
    val mv: List<RelatedMv>?
) {
    internal fun wireSize(): Int {
        return 1 + (this.hasMore?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.mv?.let { __boltffi_value_0 -> 4 + __boltffi_value_0.sumOf { __boltffi_value_1 -> val __boltffi_size: kotlin.Int = __boltffi_value_1.wireSize(); __boltffi_size } } ?: 0)
    }

    internal fun writeTo(writer: WireWriter) {
        writer.writeOptionalValue(this.hasMore, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.mv, { writer, __boltffi_value_0 -> writer.writeSequence(__boltffi_value_0, __boltffi_value_0.size, { writer, __boltffi_value_1 -> __boltffi_value_1.writeTo(writer) }) })
    }

    internal fun toByteArray(): ByteArray {
        val buffer = WireWriterPool.acquire(wireSize())
        val writer = buffer.writer
        try {
            writeTo(writer)
            return buffer.bytes()
        } finally {
            buffer.close()
        }
    }

    companion object {
        internal fun fromReader(reader: WireReader): GetRelatedMvResponse {
            return GetRelatedMvResponse(
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readSequence({ reader -> RelatedMv.fromReader(reader) }) })
            )
        }

        internal fun fromByteArray(bytes: ByteArray): GetRelatedMvResponse {
            val reader = WireReader(bytes)
            return fromReader(reader)
        }
    }
}


/**
 * 参考 `SheetMusic`：曲谱项.
 */
data class SheetMusic(
    /**
     * 曲谱 MID（上游键 `scoreMID`）.
     */
    val scoreMid: String?,
    /**
     * 曲谱名称.
     */
    val scoreName: String?,
    /**
     * 曲谱图片列表（上游键 `picURLs`）.
     */
    val picUrls: List<String>?,
    /**
     * 曲谱版本说明.
     */
    val version: String?,
    /**
     * 调号.
     */
    val tonality: Long?,
    /**
     * 曲谱类型.
     */
    val scoreType: Long?,
    /**
     * 曲谱类型文本（上游键 `strScoreType`）.
     */
    val scoreTypeText: String?,
    /**
     * 上传者.
     */
    val uploader: String?,
    /**
     * 浏览量（上游键 `viewFrequency`）.
     */
    val viewFrequency: Long?,
    /**
     * 第二调号值.
     */
    val tonality2: Long?,
    /**
     * 作者.
     */
    val author: String?,
    /**
     * 作曲.
     */
    val composer: String?,
    /**
     * 作词.
     */
    val lyricist: String?,
    /**
     * 演唱者.
     */
    val singer: String?,
    /**
     * 演奏者.
     */
    val performer: String?,
    /**
     * 关联歌曲 MID（上游键 `songMID`）.
     */
    val songMid: String?,
    /**
     * 曲谱副标题（上游键 `subName`）.
     */
    val subName: String?,
    /**
     * 曲谱详情链接.
     */
    val url: String?,
    /**
     * 专辑链接（上游键 `albumURL`）.
     */
    val albumUrl: String?,
    /**
     * 乐器类型（上游键 `insType`）.
     */
    val insType: Long?,
    /**
     * 乐器类型文本（上游键 `strInsType`）.
     */
    val insTypeText: String?,
    /**
     * 乐器封面（上游键 `coverURL`）.
     */
    val coverUrl: String?,
    /**
     * 难度.
     */
    val difficulty: String?,
    /**
     * 曲谱文件地址（上游键 `sheetFile`）.
     */
    val sheetFile: String?
) {
    internal fun wireSize(): Int {
        return 1 + (this.scoreMid?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.scoreName?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.picUrls?.let { __boltffi_value_0 -> 4 + __boltffi_value_0.sumOf { __boltffi_value_1 -> val __boltffi_size: kotlin.Int = 4 + Utf8Codec.maxBytes(__boltffi_value_1); __boltffi_size } } ?: 0) + 1 + (this.version?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.tonality?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.scoreType?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.scoreTypeText?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.uploader?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.viewFrequency?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.tonality2?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.author?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.composer?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.lyricist?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.singer?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.performer?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.songMid?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.subName?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.url?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.albumUrl?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.insType?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.insTypeText?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.coverUrl?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.difficulty?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.sheetFile?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0)
    }

    internal fun writeTo(writer: WireWriter) {
        writer.writeOptionalValue(this.scoreMid, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.scoreName, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.picUrls, { writer, __boltffi_value_0 -> writer.writeSequence(__boltffi_value_0, __boltffi_value_0.size, { writer, __boltffi_value_1 -> writer.writeString(__boltffi_value_1) }) })
        writer.writeOptionalValue(this.version, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.tonality, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.scoreType, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.scoreTypeText, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.uploader, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.viewFrequency, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.tonality2, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.author, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.composer, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.lyricist, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.singer, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.performer, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.songMid, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.subName, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.url, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.albumUrl, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.insType, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.insTypeText, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.coverUrl, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.difficulty, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.sheetFile, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
    }

    internal fun toByteArray(): ByteArray {
        val buffer = WireWriterPool.acquire(wireSize())
        val writer = buffer.writer
        try {
            writeTo(writer)
            return buffer.bytes()
        } finally {
            buffer.close()
        }
    }

    companion object {
        internal fun fromReader(reader: WireReader): SheetMusic {
            return SheetMusic(
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readSequence({ reader -> reader.readString() }) }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() })
            )
        }

        internal fun fromByteArray(bytes: ByteArray): SheetMusic {
            val reader = WireReader(bytes)
            return fromReader(reader)
        }
    }
}


/**
 * 参考 `GetSheetResponse`：歌曲相关曲谱响应.
 */
data class GetSheetResponse(
    /**
     * 当前返回的曲谱列表；没有曲谱时是空列表（参考的 `NoneToEmptyList` 也把 null 收成空）.
     */
    val result: List<SheetMusic>?,
    /**
     * 各曲谱类型对应的数量聚合（上游键 `totalMap`）.
     */
    val totalMap: Map<String, Long>?
) {
    internal fun wireSize(): Int {
        return 1 + (this.result?.let { __boltffi_value_0 -> 4 + __boltffi_value_0.sumOf { __boltffi_value_1 -> val __boltffi_size: kotlin.Int = __boltffi_value_1.wireSize(); __boltffi_size } } ?: 0) + 1 + (this.totalMap?.let { __boltffi_value_0 -> __boltffi_value_0.wireSize({ __boltffi_value_1 -> 4 + Utf8Codec.maxBytes(__boltffi_value_1) }, { __boltffi_value_2 -> 8 }) } ?: 0)
    }

    internal fun writeTo(writer: WireWriter) {
        writer.writeOptionalValue(this.result, { writer, __boltffi_value_0 -> writer.writeSequence(__boltffi_value_0, __boltffi_value_0.size, { writer, __boltffi_value_1 -> __boltffi_value_1.writeTo(writer) }) })
        writer.writeOptionalValue(this.totalMap, { writer, __boltffi_value_0 -> writer.writeMap(__boltffi_value_0, { writer, __boltffi_value_1 -> writer.writeString(__boltffi_value_1) }, { writer, __boltffi_value_2 -> writer.writeI64(__boltffi_value_2) }) })
    }

    internal fun toByteArray(): ByteArray {
        val buffer = WireWriterPool.acquire(wireSize())
        val writer = buffer.writer
        try {
            writeTo(writer)
            return buffer.bytes()
        } finally {
            buffer.close()
        }
    }

    companion object {
        internal fun fromReader(reader: WireReader): GetSheetResponse {
            return GetSheetResponse(
                reader.readOptionalValue({ reader -> reader.readSequence({ reader -> SheetMusic.fromReader(reader) }) }),
                reader.readOptionalValue({ reader -> reader.readMap({ reader -> reader.readString() }, { reader -> reader.readI64() }) })
            )
        }

        internal fun fromByteArray(bytes: ByteArray): GetSheetResponse {
            val reader = WireReader(bytes)
            return fromReader(reader)
        }
    }
}


/**
 * 参考 `HasSheetMusicResponse`：检查歌曲曲谱存在状态响应.
 */
data class HasSheetMusicResponse(
    /**
     * 是否有 AI 生成曲谱（尤克里里等）.
     */
    val hasGuitar: Boolean?,
    /**
     * 是否有更多来源的曲谱.
     */
    val hasMore: Boolean?,
    /**
     * 是否有六线谱/吉他谱（参考字段名 `has_ldy`，上游键 `hasLDY`）.
     */
    val hasLdy: Boolean?,
    /**
     * 是否有标准五线谱/曲谱（参考字段名 `has_qrcx`，上游键 `hasQRCX`）.
     */
    val hasQrcx: Boolean?,
    /**
     * 是否有虫虫钢琴谱.
     */
    val hasChongChong: Boolean?
) {
    internal fun wireSize(): Int {
        return 1 + (this.hasGuitar?.let { __boltffi_value_0 -> 1 } ?: 0) + 1 + (this.hasMore?.let { __boltffi_value_0 -> 1 } ?: 0) + 1 + (this.hasLdy?.let { __boltffi_value_0 -> 1 } ?: 0) + 1 + (this.hasQrcx?.let { __boltffi_value_0 -> 1 } ?: 0) + 1 + (this.hasChongChong?.let { __boltffi_value_0 -> 1 } ?: 0)
    }

    internal fun writeTo(writer: WireWriter) {
        writer.writeOptionalValue(this.hasGuitar, { writer, __boltffi_value_0 -> writer.writeBool(__boltffi_value_0) })
        writer.writeOptionalValue(this.hasMore, { writer, __boltffi_value_0 -> writer.writeBool(__boltffi_value_0) })
        writer.writeOptionalValue(this.hasLdy, { writer, __boltffi_value_0 -> writer.writeBool(__boltffi_value_0) })
        writer.writeOptionalValue(this.hasQrcx, { writer, __boltffi_value_0 -> writer.writeBool(__boltffi_value_0) })
        writer.writeOptionalValue(this.hasChongChong, { writer, __boltffi_value_0 -> writer.writeBool(__boltffi_value_0) })
    }

    internal fun toByteArray(): ByteArray {
        val buffer = WireWriterPool.acquire(wireSize())
        val writer = buffer.writer
        try {
            writeTo(writer)
            return buffer.bytes()
        } finally {
            buffer.close()
        }
    }

    companion object {
        internal fun fromReader(reader: WireReader): HasSheetMusicResponse {
            return HasSheetMusicResponse(
                reader.readOptionalValue({ reader -> reader.readBool() }),
                reader.readOptionalValue({ reader -> reader.readBool() }),
                reader.readOptionalValue({ reader -> reader.readBool() }),
                reader.readOptionalValue({ reader -> reader.readBool() }),
                reader.readOptionalValue({ reader -> reader.readBool() })
            )
        }

        internal fun fromByteArray(bytes: ByteArray): HasSheetMusicResponse {
            val reader = WireReader(bytes)
            return fromReader(reader)
        }
    }
}


/**
 * 参考 `UserFavSonglistItem`（继承 `SongList`）：收藏歌单列表里的单个条目.
 */
data class UserFavSonglistItem(
    /**
     * 歌单 ID（参考 `SongList.id`，别名 `tid`/`dissid`）.
     */
    val id: Long?,
    /**
     * 目录 ID.
     */
    val dirid: Long?,
    /**
     * 歌单标题.
     */
    val title: String?,
    /**
     * 歌单封面地址.
     */
    val picurl: String?,
    /**
     * 歌单简介.
     */
    val desc: String?,
    /**
     * 歌曲数量.
     */
    val songnum: Long?,
    /**
     * 播放量.
     */
    val listennum: Long?,
    /**
     * 歌单所属用户 UIN.
     */
    val uin: String?,
    /**
     * 歌单拥有者昵称.
     */
    val nickname: String?,
    /**
     * 创建时间戳（上游键 `createtime`）.
     */
    val createTime: Long?,
    /**
     * 更新时间戳.
     */
    val updateTime: Long?,
    /**
     * 收藏排序时间戳.
     */
    val orderTime: Long?,
    /**
     * 目录展示标记.
     */
    val dirShow: Long?,
    /**
     * 目录类型.
     */
    val dirType: Long?,
    /**
     * 边角标识.
     */
    val edgeMark: String?,
    /**
     * 分层装饰地址.
     */
    val layerUrl: String?,
    /**
     * 专辑拼接封面地址.
     */
    val albumPicUrl: String?,
    /**
     * 操作类型标记.
     */
    val opType: Long?,
    /**
     * 排序权重.
     */
    val sortWeight: Long?,
    /**
     * 最近读取时间戳.
     */
    val readtime: Long?
) {
    internal fun wireSize(): Int {
        return 1 + (this.id?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.dirid?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.title?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.picurl?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.desc?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.songnum?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.listennum?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.uin?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.nickname?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.createTime?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.updateTime?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.orderTime?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.dirShow?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.dirType?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.edgeMark?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.layerUrl?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.albumPicUrl?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.opType?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.sortWeight?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.readtime?.let { __boltffi_value_0 -> 8 } ?: 0)
    }

    internal fun writeTo(writer: WireWriter) {
        writer.writeOptionalValue(this.id, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.dirid, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.title, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.picurl, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.desc, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.songnum, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.listennum, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.uin, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.nickname, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.createTime, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.updateTime, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.orderTime, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.dirShow, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.dirType, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.edgeMark, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.layerUrl, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.albumPicUrl, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.opType, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.sortWeight, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.readtime, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
    }

    internal fun toByteArray(): ByteArray {
        val buffer = WireWriterPool.acquire(wireSize())
        val writer = buffer.writer
        try {
            writeTo(writer)
            return buffer.bytes()
        } finally {
            buffer.close()
        }
    }

    companion object {
        internal fun fromReader(reader: WireReader): UserFavSonglistItem {
            return UserFavSonglistItem(
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readI64() })
            )
        }

        internal fun fromByteArray(bytes: ByteArray): UserFavSonglistItem {
            val reader = WireReader(bytes)
            return fromReader(reader)
        }
    }
}


/**
 * 参考 `UserFavSonglistResponse`：用户收藏的外部歌单列表页响应.
 */
data class UserFavSonglistResponse(
    /**
     * 当前页数量或请求数量.
     */
    val number: Long?,
    /**
     * 收藏歌单总数.
     */
    val total: Long?,
    /**
     * 是否还有更多结果.
     */
    val hasmore: Long?,
    /**
     * 列表是否隐藏.
     */
    val hide: Boolean?,
    /**
     * 当前页收藏歌单列表（上游键 `v_list`）.
     */
    val playlists: List<UserFavSonglistItem>?,
    /**
     * 上游返回的删除歌单 ID 列表（上游键 `v_delTids`）.
     */
    val deletedIds: LongArray?,
    /**
     * 拉取失败的歌单 ID 列表（上游键 `v_failTids`）.
     */
    val failedIds: LongArray?
) {
    internal fun wireSize(): Int {
        return 1 + (this.number?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.total?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.hasmore?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.hide?.let { __boltffi_value_0 -> 1 } ?: 0) + 1 + (this.playlists?.let { __boltffi_value_0 -> 4 + __boltffi_value_0.sumOf { __boltffi_value_1 -> val __boltffi_size: kotlin.Int = __boltffi_value_1.wireSize(); __boltffi_size } } ?: 0) + 1 + (this.deletedIds?.let { __boltffi_value_0 -> 4 + __boltffi_value_0.size * 8 } ?: 0) + 1 + (this.failedIds?.let { __boltffi_value_0 -> 4 + __boltffi_value_0.size * 8 } ?: 0)
    }

    internal fun writeTo(writer: WireWriter) {
        writer.writeOptionalValue(this.number, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.total, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.hasmore, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.hide, { writer, __boltffi_value_0 -> writer.writeBool(__boltffi_value_0) })
        writer.writeOptionalValue(this.playlists, { writer, __boltffi_value_0 -> writer.writeSequence(__boltffi_value_0, __boltffi_value_0.size, { writer, __boltffi_value_1 -> __boltffi_value_1.writeTo(writer) }) })
        writer.writeOptionalValue(this.deletedIds, { writer, __boltffi_value_0 -> writer.writeLongArray(__boltffi_value_0) })
        writer.writeOptionalValue(this.failedIds, { writer, __boltffi_value_0 -> writer.writeLongArray(__boltffi_value_0) })
    }

    internal fun toByteArray(): ByteArray {
        val buffer = WireWriterPool.acquire(wireSize())
        val writer = buffer.writer
        try {
            writeTo(writer)
            return buffer.bytes()
        } finally {
            buffer.close()
        }
    }

    companion object {
        internal fun fromReader(reader: WireReader): UserFavSonglistResponse {
            return UserFavSonglistResponse(
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readBool() }),
                reader.readOptionalValue({ reader -> reader.readSequence({ reader -> UserFavSonglistItem.fromReader(reader) }) }),
                reader.readOptionalValue({ reader -> reader.readLongArray() }),
                reader.readOptionalValue({ reader -> reader.readLongArray() })
            )
        }

        internal fun fromByteArray(bytes: ByteArray): UserFavSonglistResponse {
            val reader = WireReader(bytes)
            return fromReader(reader)
        }
    }
}


/**
 * 参考 `UserFavAlbumItem`（继承 `Album`）：收藏专辑列表里的单个条目.
 */
data class UserFavAlbumItem(
    /**
     * 专辑数字 ID（上游键 `albumID`，大写 ID 是上游的拼写）.
     */
    val id: Long?,
    /**
     * 专辑 MID.
     */
    val mid: String?,
    /**
     * 专辑名称.
     */
    val name: String?,
    /**
     * 专辑展示标题.
     */
    val title: String?,
    /**
     * 专辑副标题.
     */
    val subtitle: String?,
    /**
     * 发行日期（参考 `Album.time_public`）.
     */
    val timePublic: String?,
    /**
     * 图片 Media ID，用于拼接封面 URL.
     */
    val pmid: String?,
    /**
     * 专辑曲目数.
     */
    val songnum: Long?,
    /**
     * 发布时间戳.
     */
    val pubtime: Long?,
    /**
     * 收藏排序时间戳.
     */
    val ordertime: Long?,
    /**
     * 状态标记.
     */
    val status: Long?,
    /**
     * 位置或来源标记.
     */
    val loc: Long?,
    /**
     * 专辑歌手列表（上游键 `v_singer`，复用组件既有的歌手模型）.
     */
    val singers: List<Singer>?
) {
    internal fun wireSize(): Int {
        return 1 + (this.id?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.mid?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.name?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.title?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.subtitle?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.timePublic?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.pmid?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.songnum?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.pubtime?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.ordertime?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.status?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.loc?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.singers?.let { __boltffi_value_0 -> 4 + __boltffi_value_0.sumOf { __boltffi_value_1 -> val __boltffi_size: kotlin.Int = __boltffi_value_1.wireSize(); __boltffi_size } } ?: 0)
    }

    internal fun writeTo(writer: WireWriter) {
        writer.writeOptionalValue(this.id, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.mid, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.name, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.title, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.subtitle, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.timePublic, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.pmid, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.songnum, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.pubtime, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.ordertime, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.status, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.loc, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.singers, { writer, __boltffi_value_0 -> writer.writeSequence(__boltffi_value_0, __boltffi_value_0.size, { writer, __boltffi_value_1 -> __boltffi_value_1.writeTo(writer) }) })
    }

    internal fun toByteArray(): ByteArray {
        val buffer = WireWriterPool.acquire(wireSize())
        val writer = buffer.writer
        try {
            writeTo(writer)
            return buffer.bytes()
        } finally {
            buffer.close()
        }
    }

    companion object {
        internal fun fromReader(reader: WireReader): UserFavAlbumItem {
            return UserFavAlbumItem(
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readSequence({ reader -> Singer.fromReader(reader) }) })
            )
        }

        internal fun fromByteArray(bytes: ByteArray): UserFavAlbumItem {
            val reader = WireReader(bytes)
            return fromReader(reader)
        }
    }
}


/**
 * 参考 `UserFavAlbumResponse`：用户收藏的专辑列表页响应.
 */
data class UserFavAlbumResponse(
    /**
     * 当前页数量或请求数量.
     */
    val number: Long?,
    /**
     * 收藏专辑总数.
     */
    val total: Long?,
    /**
     * 是否还有更多结果.
     */
    val hasmore: Long?,
    /**
     * 列表是否隐藏.
     */
    val hide: Boolean?,
    /**
     * 当前页收藏专辑列表（上游键 `v_list`）.
     */
    val albums: List<UserFavAlbumItem>?,
    /**
     * 拉取失败的专辑 ID 列表（上游键 `v_failAlbumId`）.
     */
    val failedAlbumIds: LongArray?
) {
    internal fun wireSize(): Int {
        return 1 + (this.number?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.total?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.hasmore?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.hide?.let { __boltffi_value_0 -> 1 } ?: 0) + 1 + (this.albums?.let { __boltffi_value_0 -> 4 + __boltffi_value_0.sumOf { __boltffi_value_1 -> val __boltffi_size: kotlin.Int = __boltffi_value_1.wireSize(); __boltffi_size } } ?: 0) + 1 + (this.failedAlbumIds?.let { __boltffi_value_0 -> 4 + __boltffi_value_0.size * 8 } ?: 0)
    }

    internal fun writeTo(writer: WireWriter) {
        writer.writeOptionalValue(this.number, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.total, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.hasmore, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.hide, { writer, __boltffi_value_0 -> writer.writeBool(__boltffi_value_0) })
        writer.writeOptionalValue(this.albums, { writer, __boltffi_value_0 -> writer.writeSequence(__boltffi_value_0, __boltffi_value_0.size, { writer, __boltffi_value_1 -> __boltffi_value_1.writeTo(writer) }) })
        writer.writeOptionalValue(this.failedAlbumIds, { writer, __boltffi_value_0 -> writer.writeLongArray(__boltffi_value_0) })
    }

    internal fun toByteArray(): ByteArray {
        val buffer = WireWriterPool.acquire(wireSize())
        val writer = buffer.writer
        try {
            writeTo(writer)
            return buffer.bytes()
        } finally {
            buffer.close()
        }
    }

    companion object {
        internal fun fromReader(reader: WireReader): UserFavAlbumResponse {
            return UserFavAlbumResponse(
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readBool() }),
                reader.readOptionalValue({ reader -> reader.readSequence({ reader -> UserFavAlbumItem.fromReader(reader) }) }),
                reader.readOptionalValue({ reader -> reader.readLongArray() })
            )
        }

        internal fun fromByteArray(bytes: ByteArray): UserFavAlbumResponse {
            val reader = WireReader(bytes)
            return fromReader(reader)
        }
    }
}


/**
 * 参考 `UserFavMvItem`（继承 `MV`）：收藏 MV 列表里的单个条目.
 */
data class UserFavMvItem(
    /**
     * MV 数字 ID（参考 `MV.id`，别名 `sid`/`mvid`/`singerId`）.
     */
    val id: Long?,
    /**
     * MV VID.
     */
    val vid: String?,
    /**
     * MV 类型（参考 `MV.type`，上游键 `vt`；Rust 侧换个名字、JSON 上仍是 `type`）.
     */
    val kind: Long?,
    /**
     * MV 名称.
     */
    val name: String?,
    /**
     * MV 展示标题.
     */
    val title: String?,
    /**
     * MV 封面地址（上游键 `picUrl`）.
     */
    val picurl: String?,
    /**
     * 播放量.
     */
    val playcount: Long?,
    /**
     * 发布时间（参考字段名 `publish_date`）.
     */
    val publishDate: Long?,
    /**
     * 歌手 ID（上游键 `singerId`）.
     */
    val singerId: Long?,
    /**
     * 歌手 MID.
     */
    val singerMid: String?,
    /**
     * 歌手名称.
     */
    val singerName: String?,
    /**
     * 状态标记.
     */
    val status: Long?
) {
    internal fun wireSize(): Int {
        return 1 + (this.id?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.vid?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.kind?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.name?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.title?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.picurl?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.playcount?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.publishDate?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.singerId?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.singerMid?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.singerName?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.status?.let { __boltffi_value_0 -> 8 } ?: 0)
    }

    internal fun writeTo(writer: WireWriter) {
        writer.writeOptionalValue(this.id, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.vid, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.kind, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.name, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.title, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.picurl, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.playcount, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.publishDate, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.singerId, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.singerMid, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.singerName, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.status, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
    }

    internal fun toByteArray(): ByteArray {
        val buffer = WireWriterPool.acquire(wireSize())
        val writer = buffer.writer
        try {
            writeTo(writer)
            return buffer.bytes()
        } finally {
            buffer.close()
        }
    }

    companion object {
        internal fun fromReader(reader: WireReader): UserFavMvItem {
            return UserFavMvItem(
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readI64() })
            )
        }

        internal fun fromByteArray(bytes: ByteArray): UserFavMvItem {
            val reader = WireReader(bytes)
            return fromReader(reader)
        }
    }
}


/**
 * 参考 `UserFavMvResponse`：用户收藏 MV 列表视图响应.
 */
data class UserFavMvResponse(
    /**
     * 返回码.
     */
    val code: Long?,
    /**
     * 子返回码（参考同时接受 `subCode` 与 `subcode` 两种拼写）.
     */
    val subCode: Long?,
    /**
     * 附加消息.
     */
    val msg: String?,
    /**
     * 当前页收藏 MV 列表（上游键 `mvlist`）.
     */
    val mvList: List<UserFavMvItem>?
) {
    internal fun wireSize(): Int {
        return 1 + (this.code?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.subCode?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.msg?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.mvList?.let { __boltffi_value_0 -> 4 + __boltffi_value_0.sumOf { __boltffi_value_1 -> val __boltffi_size: kotlin.Int = __boltffi_value_1.wireSize(); __boltffi_size } } ?: 0)
    }

    internal fun writeTo(writer: WireWriter) {
        writer.writeOptionalValue(this.code, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.subCode, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.msg, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.mvList, { writer, __boltffi_value_0 -> writer.writeSequence(__boltffi_value_0, __boltffi_value_0.size, { writer, __boltffi_value_1 -> __boltffi_value_1.writeTo(writer) }) })
    }

    internal fun toByteArray(): ByteArray {
        val buffer = WireWriterPool.acquire(wireSize())
        val writer = buffer.writer
        try {
            writeTo(writer)
            return buffer.bytes()
        } finally {
            buffer.close()
        }
    }

    companion object {
        internal fun fromReader(reader: WireReader): UserFavMvResponse {
            return UserFavMvResponse(
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readSequence({ reader -> UserFavMvItem.fromReader(reader) }) })
            )
        }

        internal fun fromByteArray(bytes: ByteArray): UserFavMvResponse {
            val reader = WireReader(bytes)
            return fromReader(reader)
        }
    }
}


/**
 * 参考 `UserInfoCard`：用户音乐基因页头部卡片信息.
 *
 * `preferences` 在参考里是 `dict[str, Any]`（原样透传的上游形状），这里存
 * JSON 文本：`#[data]` 认不出 `serde_json::Value`，而经 serde 进出时它仍是
 * 真正的对象（见 `raw_json_serialize`）。
 */
data class UserInfoCard(
    /**
     * 头像地址（上游键 `HeadUrl`）.
     */
    val headUrl: String?,
    /**
     * 昵称（上游键 `NickName`）.
     */
    val nickName: String?,
    /**
     * 个性签名（上游键 `Signature`）.
     */
    val signature: String?,
    /**
     * 加密账号标识（上游键 `EncryptionAccount`）.
     */
    val encryptionAccount: String?,
    /**
     * 偏好信息块（上游键 `Preferences`，dict 原样透传）.
     */
    val preferences: String?
) {
    internal fun wireSize(): Int {
        return 1 + (this.headUrl?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.nickName?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.signature?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.encryptionAccount?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.preferences?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0)
    }

    internal fun writeTo(writer: WireWriter) {
        writer.writeOptionalValue(this.headUrl, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.nickName, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.signature, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.encryptionAccount, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.preferences, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
    }

    internal fun toByteArray(): ByteArray {
        val buffer = WireWriterPool.acquire(wireSize())
        val writer = buffer.writer
        try {
            writeTo(writer)
            return buffer.bytes()
        } finally {
            buffer.close()
        }
    }

    companion object {
        internal fun fromReader(reader: WireReader): UserInfoCard {
            return UserInfoCard(
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() })
            )
        }

        internal fun fromByteArray(bytes: ByteArray): UserInfoCard {
            val reader = WireReader(bytes)
            return fromReader(reader)
        }
    }
}


/**
 * 参考 `ListeningReport`：用户听歌报告摘要.
 */
data class ListeningReport(
    /**
     * 听歌报告分块列表（上游键 `Report`，dict 列表原样透传）.
     */
    val report: String?
) {
    internal fun wireSize(): Int {
        return 1 + (this.report?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0)
    }

    internal fun writeTo(writer: WireWriter) {
        writer.writeOptionalValue(this.report, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
    }

    internal fun toByteArray(): ByteArray {
        val buffer = WireWriterPool.acquire(wireSize())
        val writer = buffer.writer
        try {
            writeTo(writer)
            return buffer.bytes()
        } finally {
            buffer.close()
        }
    }

    companion object {
        internal fun fromReader(reader: WireReader): ListeningReport {
            return ListeningReport(
                reader.readOptionalValue({ reader -> reader.readString() })
            )
        }

        internal fun fromByteArray(bytes: ByteArray): ListeningReport {
            val reader = WireReader(bytes)
            return fromReader(reader)
        }
    }
}


/**
 * 参考 `UserMusicGeneResponse`：用户音乐基因视图响应.
 */
data class UserMusicGeneResponse(
    /**
     * 用户卡片信息（上游键 `UserInfoCard`）.
     */
    val userInfoCard: UserInfoCard?,
    /**
     * 听歌报告摘要（上游键 `ListeningReport`）.
     */
    val listeningReport: ListeningReport?,
    /**
     * 排序提示数组（上游键 `SortArray`）.
     */
    val sortArray: LongArray?,
    /**
     * 是否访问本人账号（上游键 `IsVisitAccount`）.
     */
    val isVisitAccount: Boolean?
) {
    internal fun wireSize(): Int {
        return 1 + (this.userInfoCard?.let { __boltffi_value_0 -> __boltffi_value_0.wireSize() } ?: 0) + 1 + (this.listeningReport?.let { __boltffi_value_0 -> __boltffi_value_0.wireSize() } ?: 0) + 1 + (this.sortArray?.let { __boltffi_value_0 -> 4 + __boltffi_value_0.size * 8 } ?: 0) + 1 + (this.isVisitAccount?.let { __boltffi_value_0 -> 1 } ?: 0)
    }

    internal fun writeTo(writer: WireWriter) {
        writer.writeOptionalValue(this.userInfoCard, { writer, __boltffi_value_0 -> __boltffi_value_0.writeTo(writer) })
        writer.writeOptionalValue(this.listeningReport, { writer, __boltffi_value_0 -> __boltffi_value_0.writeTo(writer) })
        writer.writeOptionalValue(this.sortArray, { writer, __boltffi_value_0 -> writer.writeLongArray(__boltffi_value_0) })
        writer.writeOptionalValue(this.isVisitAccount, { writer, __boltffi_value_0 -> writer.writeBool(__boltffi_value_0) })
    }

    internal fun toByteArray(): ByteArray {
        val buffer = WireWriterPool.acquire(wireSize())
        val writer = buffer.writer
        try {
            writeTo(writer)
            return buffer.bytes()
        } finally {
            buffer.close()
        }
    }

    companion object {
        internal fun fromReader(reader: WireReader): UserMusicGeneResponse {
            return UserMusicGeneResponse(
                reader.readOptionalValue({ reader -> UserInfoCard.fromReader(reader) }),
                reader.readOptionalValue({ reader -> ListeningReport.fromReader(reader) }),
                reader.readOptionalValue({ reader -> reader.readLongArray() }),
                reader.readOptionalValue({ reader -> reader.readBool() })
            )
        }

        internal fun fromByteArray(bytes: ByteArray): UserMusicGeneResponse {
            val reader = WireReader(bytes)
            return fromReader(reader)
        }
    }
}


/**
 * 参考 `DislikeItem`：不喜欢列表中的单个条目.
 */
data class DislikeItem(
    /**
     * 不喜欢实体的 ID（歌手 ID、歌曲 ID 等；参考声明为字符串）.
     */
    val id: String?,
    /**
     * 不喜欢实体的名称.
     */
    val name: String?,
    /**
     * 不喜欢实体的图片 / 封面 URL.
     */
    val img: String?,
    /**
     * 实体类型标识.
     */
    val idType: Long?,
    /**
     * 添加到不喜欢列表的时间戳.
     */
    val time: Long?
) {
    internal fun wireSize(): Int {
        return 1 + (this.id?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.name?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.img?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.idType?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.time?.let { __boltffi_value_0 -> 8 } ?: 0)
    }

    internal fun writeTo(writer: WireWriter) {
        writer.writeOptionalValue(this.id, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.name, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.img, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.idType, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.time, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
    }

    internal fun toByteArray(): ByteArray {
        val buffer = WireWriterPool.acquire(wireSize())
        val writer = buffer.writer
        try {
            writeTo(writer)
            return buffer.bytes()
        } finally {
            buffer.close()
        }
    }

    companion object {
        internal fun fromReader(reader: WireReader): DislikeItem {
            return DislikeItem(
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readI64() })
            )
        }

        internal fun fromByteArray(bytes: ByteArray): DislikeItem {
            val reader = WireReader(bytes)
            return fromReader(reader)
        }
    }
}


/**
 * 参考 `DislikeListData`：`GetDislikeList` 响应数据.
 *
 * 三个列表在参考里都带 `default_factory=list`——空列表是答案（没有不喜欢），
 * 所以 payload 会把它们补齐成数组。
 */
data class DislikeListData(
    /**
     * 业务返回码（参考的 `Retcode`）.
     */
    val retcode: Long?,
    /**
     * 业务返回信息.
     */
    val msg: String?,
    /**
     * 不喜欢的歌手列表.
     */
    val singers: List<DislikeItem>?,
    /**
     * 不喜欢的歌曲列表.
     */
    val songs: List<DislikeItem>?,
    /**
     * 不喜欢的风格 / 流派列表.
     */
    val styles: List<DislikeItem>?,
    /**
     * 当前页码.
     */
    val page: Long?,
    /**
     * 翻页 Token.
     */
    val token: String?
) {
    internal fun wireSize(): Int {
        return 1 + (this.retcode?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.msg?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.singers?.let { __boltffi_value_0 -> 4 + __boltffi_value_0.sumOf { __boltffi_value_1 -> val __boltffi_size: kotlin.Int = __boltffi_value_1.wireSize(); __boltffi_size } } ?: 0) + 1 + (this.songs?.let { __boltffi_value_0 -> 4 + __boltffi_value_0.sumOf { __boltffi_value_1 -> val __boltffi_size: kotlin.Int = __boltffi_value_1.wireSize(); __boltffi_size } } ?: 0) + 1 + (this.styles?.let { __boltffi_value_0 -> 4 + __boltffi_value_0.sumOf { __boltffi_value_1 -> val __boltffi_size: kotlin.Int = __boltffi_value_1.wireSize(); __boltffi_size } } ?: 0) + 1 + (this.page?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.token?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0)
    }

    internal fun writeTo(writer: WireWriter) {
        writer.writeOptionalValue(this.retcode, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.msg, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.singers, { writer, __boltffi_value_0 -> writer.writeSequence(__boltffi_value_0, __boltffi_value_0.size, { writer, __boltffi_value_1 -> __boltffi_value_1.writeTo(writer) }) })
        writer.writeOptionalValue(this.songs, { writer, __boltffi_value_0 -> writer.writeSequence(__boltffi_value_0, __boltffi_value_0.size, { writer, __boltffi_value_1 -> __boltffi_value_1.writeTo(writer) }) })
        writer.writeOptionalValue(this.styles, { writer, __boltffi_value_0 -> writer.writeSequence(__boltffi_value_0, __boltffi_value_0.size, { writer, __boltffi_value_1 -> __boltffi_value_1.writeTo(writer) }) })
        writer.writeOptionalValue(this.page, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.token, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
    }

    internal fun toByteArray(): ByteArray {
        val buffer = WireWriterPool.acquire(wireSize())
        val writer = buffer.writer
        try {
            writeTo(writer)
            return buffer.bytes()
        } finally {
            buffer.close()
        }
    }

    companion object {
        internal fun fromReader(reader: WireReader): DislikeListData {
            return DislikeListData(
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readSequence({ reader -> DislikeItem.fromReader(reader) }) }),
                reader.readOptionalValue({ reader -> reader.readSequence({ reader -> DislikeItem.fromReader(reader) }) }),
                reader.readOptionalValue({ reader -> reader.readSequence({ reader -> DislikeItem.fromReader(reader) }) }),
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readString() })
            )
        }

        internal fun fromByteArray(bytes: ByteArray): DislikeListData {
            val reader = WireReader(bytes)
            return fromReader(reader)
        }
    }
}


/**
 * 参考 `UserHomepageBaseInfo`：主页头部基础信息.
 */
data class UserHomepageBaseInfo(
    /**
     * 加密 UIN（上游键 `EncryptedUin`）.
     */
    val encryptedUin: String?,
    /**
     * 用户名.
     */
    val name: String?,
    /**
     * 头像地址.
     */
    val avatar: String?,
    /**
     * 背景图地址.
     */
    val backgroundImage: String?,
    /**
     * 用户类型标记.
     */
    val userType: Long?
) {
    internal fun wireSize(): Int {
        return 1 + (this.encryptedUin?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.name?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.avatar?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.backgroundImage?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.userType?.let { __boltffi_value_0 -> 8 } ?: 0)
    }

    internal fun writeTo(writer: WireWriter) {
        writer.writeOptionalValue(this.encryptedUin, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.name, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.avatar, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.backgroundImage, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.userType, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
    }

    internal fun toByteArray(): ByteArray {
        val buffer = WireWriterPool.acquire(wireSize())
        val writer = buffer.writer
        try {
            writeTo(writer)
            return buffer.bytes()
        } finally {
            buffer.close()
        }
    }

    companion object {
        internal fun fromReader(reader: WireReader): UserHomepageBaseInfo {
            return UserHomepageBaseInfo(
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readI64() })
            )
        }

        internal fun fromByteArray(bytes: ByteArray): UserHomepageBaseInfo {
            val reader = WireReader(bytes)
            return fromReader(reader)
        }
    }
}


/**
 * 参考 `UserHomepageResponse`：用户主页视图响应.
 *
 * `singer` 与 `tab_detail` 在参考里是 `dict[str, Any]`（原样透传的上游形状），
 * 这里存 JSON 文本：`#[data]` 认不出 `serde_json::Value`，而经 serde 进出时
 * 它们仍是真正的对象（见 `raw_json_serialize`）。
 */
data class UserHomepage(
    /**
     * 主页头部基础信息（`$.Info.BaseInfo`）.
     */
    val baseInfo: UserHomepageBaseInfo?,
    /**
     * 主页关联歌手信息（`$.Info.Singer`）.
     */
    val singer: String?,
    /**
     * 当前账号是否已关注（`$.Info.IsFollowed`）.
     */
    val isFollowed: Long?,
    /**
     * 主页标签页附加信息（上游键 `TabDetail`）.
     */
    val tabDetail: String?
) {
    internal fun wireSize(): Int {
        return 1 + (this.baseInfo?.let { __boltffi_value_0 -> __boltffi_value_0.wireSize() } ?: 0) + 1 + (this.singer?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.isFollowed?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.tabDetail?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0)
    }

    internal fun writeTo(writer: WireWriter) {
        writer.writeOptionalValue(this.baseInfo, { writer, __boltffi_value_0 -> __boltffi_value_0.writeTo(writer) })
        writer.writeOptionalValue(this.singer, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.isFollowed, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.tabDetail, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
    }

    internal fun toByteArray(): ByteArray {
        val buffer = WireWriterPool.acquire(wireSize())
        val writer = buffer.writer
        try {
            writeTo(writer)
            return buffer.bytes()
        } finally {
            buffer.close()
        }
    }

    companion object {
        internal fun fromReader(reader: WireReader): UserHomepage {
            return UserHomepage(
                reader.readOptionalValue({ reader -> UserHomepageBaseInfo.fromReader(reader) }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readString() })
            )
        }

        internal fun fromByteArray(bytes: ByteArray): UserHomepage {
            val reader = WireReader(bytes)
            return fromReader(reader)
        }
    }
}


/**
 * 参考 `VipIdentity`：VIP 信息响应中的会员身份明细块.
 */
data class VipIdentity(
    /**
     * 绿钻会员标志.
     */
    val vip: Long?,
    /**
     * 豪华绿钻会员标志（上游键 `HugeVip`）.
     */
    val hugeVip: Long?,
    /**
     * 豪华绿钻生效时间（上游键 `HugeVipStart`）.
     */
    val hugeVipStart: String?,
    /**
     * 豪华绿钻到期时间（上游键 `HugeVipEnd`）.
     */
    val hugeVipEnd: String?,
    /**
     * 年费会员标志（上游键 `yearflag`）.
     */
    val yearFlag: Long?,
    /**
     * 豪华年费会员标志（上游键 `HugeYearFlag`）.
     */
    val hugeYearFlag: Long?,
    /**
     * 十二平台会员标志.
     */
    val twelve: Long?,
    /**
     * 十二平台会员生效时间（上游键 `twelveStart`）.
     */
    val twelveStart: String?,
    /**
     * 十二平台会员到期时间（上游键 `twelveEnd`）.
     */
    val twelveEnd: String?,
    /**
     * 儿童会员标志（上游键 `ChildVip`）.
     */
    val childVip: Long?,
    /**
     * 体验会员标志（上游键 `ExpVip`）.
     */
    val expVip: Long?,
    /**
     * 家庭组会员标志（上游键 `GroupVipFlag`）.
     */
    val groupVipFlag: Long?,
    /**
     * 家庭组会员生效时间（上游键 `GroupVipStart`）.
     */
    val groupVipStart: String?,
    /**
     * 家庭组会员到期时间（上游键 `GroupVipEnd`）.
     */
    val groupVipEnd: String?,
    /**
     * 情侣会员标志（上游键 `CPLoverFlag`）.
     */
    val cpLoverFlag: Long?,
    /**
     * 情侣会员生效时间（上游键 `CPLoverStart`）.
     */
    val cpLoverStart: String?,
    /**
     * 情侣会员到期时间（上游键 `CPLoverEnd`）.
     */
    val cpLoverEnd: String?,
    /**
     * 广告会员标志（上游键 `AdVipFlag`）.
     */
    val adVipFlag: Long?,
    /**
     * 八平台会员标志.
     */
    val eight: Long?,
    /**
     * 八平台会员生效时间（上游键 `eightStart`）.
     */
    val eightStart: String?,
    /**
     * 八平台会员到期时间（上游键 `eightEnd`）.
     */
    val eightEnd: String?,
    /**
     * 会员等级.
     */
    val level: Long?,
    /**
     * 下一会员等级（上游键 `nextlevel`）.
     */
    val nextLevel: Long?,
    /**
     * 官方等级徽章图地址.
     */
    val icon: String?,
    /**
     * 会员购买页地址（上游键 `purchaseUrl`）.
     */
    val purchaseUrl: String?
) {
    internal fun wireSize(): Int {
        return 1 + (this.vip?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.hugeVip?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.hugeVipStart?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.hugeVipEnd?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.yearFlag?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.hugeYearFlag?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.twelve?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.twelveStart?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.twelveEnd?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.childVip?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.expVip?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.groupVipFlag?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.groupVipStart?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.groupVipEnd?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.cpLoverFlag?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.cpLoverStart?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.cpLoverEnd?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.adVipFlag?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.eight?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.eightStart?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.eightEnd?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.level?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.nextLevel?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.icon?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.purchaseUrl?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0)
    }

    internal fun writeTo(writer: WireWriter) {
        writer.writeOptionalValue(this.vip, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.hugeVip, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.hugeVipStart, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.hugeVipEnd, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.yearFlag, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.hugeYearFlag, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.twelve, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.twelveStart, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.twelveEnd, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.childVip, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.expVip, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.groupVipFlag, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.groupVipStart, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.groupVipEnd, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.cpLoverFlag, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.cpLoverStart, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.cpLoverEnd, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.adVipFlag, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.eight, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.eightStart, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.eightEnd, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.level, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.nextLevel, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.icon, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.purchaseUrl, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
    }

    internal fun toByteArray(): ByteArray {
        val buffer = WireWriterPool.acquire(wireSize())
        val writer = buffer.writer
        try {
            writeTo(writer)
            return buffer.bytes()
        } finally {
            buffer.close()
        }
    }

    companion object {
        internal fun fromReader(reader: WireReader): VipIdentity {
            return VipIdentity(
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() })
            )
        }

        internal fun fromByteArray(bytes: ByteArray): VipIdentity {
            val reader = WireReader(bytes)
            return fromReader(reader)
        }
    }
}


/**
 * 参考 `VipUserInfo`：VIP 信息响应中的用户权益摘要块.
 */
data class VipUserInfo(
    /**
     * 开通入口地址（参考同时接受 `buy_url` / `buyurl`）.
     */
    val buyUrl: String?,
    /**
     * 我的会员页地址（参考同时接受 `my_vip_url` / `myvipurl`）.
     */
    val myVipUrl: String?,
    /**
     * 会员积分.
     */
    val score: Long?,
    /**
     * 到期时间戳.
     */
    val expire: Long?,
    /**
     * 音乐等级（上游键 `music_level`）.
     */
    val musicLevel: Long?
) {
    internal fun wireSize(): Int {
        return 1 + (this.buyUrl?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.myVipUrl?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.score?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.expire?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.musicLevel?.let { __boltffi_value_0 -> 8 } ?: 0)
    }

    internal fun writeTo(writer: WireWriter) {
        writer.writeOptionalValue(this.buyUrl, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.myVipUrl, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.score, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.expire, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.musicLevel, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
    }

    internal fun toByteArray(): ByteArray {
        val buffer = WireWriterPool.acquire(wireSize())
        val writer = buffer.writer
        try {
            writeTo(writer)
            return buffer.bytes()
        } finally {
            buffer.close()
        }
    }

    companion object {
        internal fun fromReader(reader: WireReader): VipUserInfo {
            return VipUserInfo(
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readI64() })
            )
        }

        internal fun fromByteArray(bytes: ByteArray): VipUserInfo {
            val reader = WireReader(bytes)
            return fromReader(reader)
        }
    }
}


/**
 * 参考 `UserVipInfoResponse`：VIP 信息视图响应.
 */
data class UserVipInfo(
    /**
     * 自动下载开关状态（参考同时接受 `auto_down` / `autoDown` / `autodown`）.
     */
    val autoDown: Long?,
    /**
     * 是否可续费（上游键 `canRenew`）.
     */
    val canRenew: Long?,
    /**
     * 最大歌单数量（参考同时接受 `max_dir_num` / `maxDirNum` / `maxdirnum`）.
     */
    val maxDirNum: Long?,
    /**
     * 最大歌曲数量（参考同时接受 `max_song_num` / `maxSongNum` / `maxsongnum`）.
     */
    val maxSongNum: Long?,
    /**
     * 歌曲上限提示文案（参考同时接受 `song_limit_msg` / `songLimitMsg`）.
     */
    val songLimitMsg: String?,
    /**
     * 超级会员标志.
     */
    val svip: Long?,
    /**
     * 星级会员标志.
     */
    val star: Long?,
    /**
     * 星级会员生效时间（上游键 `starstart`）.
     */
    val starStart: String?,
    /**
     * 星级会员到期时间（上游键 `starend`）.
     */
    val starEnd: String?,
    /**
     * 年费星级会员标志.
     */
    val ystar: Long?,
    /**
     * 年费星级会员生效时间（上游键 `ystarstart`）.
     */
    val ystarStart: String?,
    /**
     * 年费星级会员到期时间（上游键 `ystarend`）.
     */
    val ystarEnd: String?,
    /**
     * 会员身份明细（参考里带 `default_factory`，缺了补空对象）.
     */
    val identity: VipIdentity?,
    /**
     * 用户权益摘要（参考里带 `default_factory`，缺了补空对象）.
     */
    val userinfo: VipUserInfo?
) {
    internal fun wireSize(): Int {
        return 1 + (this.autoDown?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.canRenew?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.maxDirNum?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.maxSongNum?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.songLimitMsg?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.svip?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.star?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.starStart?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.starEnd?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.ystar?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.ystarStart?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.ystarEnd?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.identity?.let { __boltffi_value_0 -> __boltffi_value_0.wireSize() } ?: 0) + 1 + (this.userinfo?.let { __boltffi_value_0 -> __boltffi_value_0.wireSize() } ?: 0)
    }

    internal fun writeTo(writer: WireWriter) {
        writer.writeOptionalValue(this.autoDown, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.canRenew, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.maxDirNum, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.maxSongNum, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.songLimitMsg, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.svip, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.star, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.starStart, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.starEnd, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.ystar, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.ystarStart, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.ystarEnd, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.identity, { writer, __boltffi_value_0 -> __boltffi_value_0.writeTo(writer) })
        writer.writeOptionalValue(this.userinfo, { writer, __boltffi_value_0 -> __boltffi_value_0.writeTo(writer) })
    }

    internal fun toByteArray(): ByteArray {
        val buffer = WireWriterPool.acquire(wireSize())
        val writer = buffer.writer
        try {
            writeTo(writer)
            return buffer.bytes()
        } finally {
            buffer.close()
        }
    }

    companion object {
        internal fun fromReader(reader: WireReader): UserVipInfo {
            return UserVipInfo(
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> VipIdentity.fromReader(reader) }),
                reader.readOptionalValue({ reader -> VipUserInfo.fromReader(reader) })
            )
        }

        internal fun fromByteArray(bytes: ByteArray): UserVipInfo {
            val reader = WireReader(bytes)
            return fromReader(reader)
        }
    }
}


/**
 * 参考 `RelationUser`：关注或粉丝列表中的单个用户条目.
 */
data class RelationUser(
    /**
     * 用户 MID（上游键 `MID`）.
     */
    val mid: String?,
    /**
     * 加密 UIN（上游键 `EncUin`）.
     */
    val encUin: String?,
    /**
     * 用户名称（上游键 `Name`）.
     */
    val name: String?,
    /**
     * 描述文案（上游键 `Desc`）.
     */
    val desc: String?,
    /**
     * 头像地址（上游键 `AvatarUrl`）.
     */
    val avatarUrl: String?,
    /**
     * 粉丝数（上游键 `FanNum`）.
     */
    val fanNum: Long?,
    /**
     * 当前账号是否已关注（上游键 `IsFollow`）.
     */
    val isFollow: Boolean?
) {
    internal fun wireSize(): Int {
        return 1 + (this.mid?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.encUin?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.name?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.desc?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.avatarUrl?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.fanNum?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.isFollow?.let { __boltffi_value_0 -> 1 } ?: 0)
    }

    internal fun writeTo(writer: WireWriter) {
        writer.writeOptionalValue(this.mid, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.encUin, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.name, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.desc, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.avatarUrl, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.fanNum, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.isFollow, { writer, __boltffi_value_0 -> writer.writeBool(__boltffi_value_0) })
    }

    internal fun toByteArray(): ByteArray {
        val buffer = WireWriterPool.acquire(wireSize())
        val writer = buffer.writer
        try {
            writeTo(writer)
            return buffer.bytes()
        } finally {
            buffer.close()
        }
    }

    companion object {
        internal fun fromReader(reader: WireReader): RelationUser {
            return RelationUser(
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readBool() })
            )
        }

        internal fun fromByteArray(bytes: ByteArray): RelationUser {
            val reader = WireReader(bytes)
            return fromReader(reader)
        }
    }
}


/**
 * 参考 `UserRelationListResponse`：关注关系分页列表响应.
 */
data class UserRelationList(
    /**
     * 总数量（上游键 `Total`）.
     */
    val total: Long?,
    /**
     * 当前页用户列表（上游键 `List`）.
     */
    val users: List<RelationUser>?,
    /**
     * 是否还有更多结果（上游键 `HasMore`）.
     */
    val hasMore: Boolean?,
    /**
     * 下一页游标（上游键 `LastPos`）.
     */
    val lastPos: String?,
    /**
     * 附加消息（上游键 `Msg`）.
     */
    val msg: String?,
    /**
     * 锁定状态标记（上游键 `LockFlag`）.
     */
    val lockFlag: Long?,
    /**
     * 锁定提示文案（上游键 `LockMsg`）.
     */
    val lockMsg: String?
) {
    internal fun wireSize(): Int {
        return 1 + (this.total?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.users?.let { __boltffi_value_0 -> 4 + __boltffi_value_0.sumOf { __boltffi_value_1 -> val __boltffi_size: kotlin.Int = __boltffi_value_1.wireSize(); __boltffi_size } } ?: 0) + 1 + (this.hasMore?.let { __boltffi_value_0 -> 1 } ?: 0) + 1 + (this.lastPos?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.msg?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.lockFlag?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.lockMsg?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0)
    }

    internal fun writeTo(writer: WireWriter) {
        writer.writeOptionalValue(this.total, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.users, { writer, __boltffi_value_0 -> writer.writeSequence(__boltffi_value_0, __boltffi_value_0.size, { writer, __boltffi_value_1 -> __boltffi_value_1.writeTo(writer) }) })
        writer.writeOptionalValue(this.hasMore, { writer, __boltffi_value_0 -> writer.writeBool(__boltffi_value_0) })
        writer.writeOptionalValue(this.lastPos, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.msg, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.lockFlag, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.lockMsg, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
    }

    internal fun toByteArray(): ByteArray {
        val buffer = WireWriterPool.acquire(wireSize())
        val writer = buffer.writer
        try {
            writeTo(writer)
            return buffer.bytes()
        } finally {
            buffer.close()
        }
    }

    companion object {
        internal fun fromReader(reader: WireReader): UserRelationList {
            return UserRelationList(
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readSequence({ reader -> RelationUser.fromReader(reader) }) }),
                reader.readOptionalValue({ reader -> reader.readBool() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readString() })
            )
        }

        internal fun fromByteArray(bytes: ByteArray): UserRelationList {
            val reader = WireReader(bytes)
            return fromReader(reader)
        }
    }
}


/**
 * 参考 `FriendEntry`：好友列表中的单个好友条目.
 */
data class FriendEntry(
    /**
     * 加密 UIN（上游键 `EncryptUin`）.
     */
    val encryptUin: String?,
    /**
     * 用户名（上游键 `UserName`）.
     */
    val userName: String?,
    /**
     * 头像地址（上游键 `AvatarUrl`）.
     */
    val avatarUrl: String?,
    /**
     * 当前账号是否已关注（上游键 `IsFollow`）.
     */
    val isFollow: Boolean?
) {
    internal fun wireSize(): Int {
        return 1 + (this.encryptUin?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.userName?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.avatarUrl?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.isFollow?.let { __boltffi_value_0 -> 1 } ?: 0)
    }

    internal fun writeTo(writer: WireWriter) {
        writer.writeOptionalValue(this.encryptUin, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.userName, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.avatarUrl, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.isFollow, { writer, __boltffi_value_0 -> writer.writeBool(__boltffi_value_0) })
    }

    internal fun toByteArray(): ByteArray {
        val buffer = WireWriterPool.acquire(wireSize())
        val writer = buffer.writer
        try {
            writeTo(writer)
            return buffer.bytes()
        } finally {
            buffer.close()
        }
    }

    companion object {
        internal fun fromReader(reader: WireReader): FriendEntry {
            return FriendEntry(
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readBool() })
            )
        }

        internal fun fromByteArray(bytes: ByteArray): FriendEntry {
            val reader = WireReader(bytes)
            return fromReader(reader)
        }
    }
}


/**
 * 参考 `UserFriendListResponse`：好友列表视图响应.
 */
data class UserFriendList(
    /**
     * 当前页好友列表（上游键 `Friends`）.
     */
    val friends: List<FriendEntry>?,
    /**
     * 是否还有更多结果（上游键 `HasMore`）.
     */
    val hasMore: Boolean?
) {
    internal fun wireSize(): Int {
        return 1 + (this.friends?.let { __boltffi_value_0 -> 4 + __boltffi_value_0.sumOf { __boltffi_value_1 -> val __boltffi_size: kotlin.Int = __boltffi_value_1.wireSize(); __boltffi_size } } ?: 0) + 1 + (this.hasMore?.let { __boltffi_value_0 -> 1 } ?: 0)
    }

    internal fun writeTo(writer: WireWriter) {
        writer.writeOptionalValue(this.friends, { writer, __boltffi_value_0 -> writer.writeSequence(__boltffi_value_0, __boltffi_value_0.size, { writer, __boltffi_value_1 -> __boltffi_value_1.writeTo(writer) }) })
        writer.writeOptionalValue(this.hasMore, { writer, __boltffi_value_0 -> writer.writeBool(__boltffi_value_0) })
    }

    internal fun toByteArray(): ByteArray {
        val buffer = WireWriterPool.acquire(wireSize())
        val writer = buffer.writer
        try {
            writeTo(writer)
            return buffer.bytes()
        } finally {
            buffer.close()
        }
    }

    companion object {
        internal fun fromReader(reader: WireReader): UserFriendList {
            return UserFriendList(
                reader.readOptionalValue({ reader -> reader.readSequence({ reader -> FriendEntry.fromReader(reader) }) }),
                reader.readOptionalValue({ reader -> reader.readBool() })
            )
        }

        internal fun fromByteArray(bytes: ByteArray): UserFriendList {
            val reader = WireReader(bytes)
            return fromReader(reader)
        }
    }
}


/**
 * 参考 `UserPlaylistSummary`（继承 `SongList`）：用户创建的歌单摘要.
 */
data class UserPlaylistSummary(
    /**
     * 歌单 ID（参考别名含 `tid` / `dissid`）.
     */
    val id: Long?,
    /**
     * 目录 ID.
     */
    val dirid: Long?,
    /**
     * 歌单标题.
     */
    val title: String?,
    /**
     * 歌单封面地址.
     */
    val picurl: String?,
    /**
     * 歌单简介.
     */
    val desc: String?,
    /**
     * 歌曲数量.
     */
    val songnum: Long?,
    /**
     * 播放量.
     */
    val listennum: Long?,
    /**
     * 创建时间戳（上游键 `createTime`）.
     */
    val createTime: Long?,
    /**
     * 更新时间戳（上游键 `updateTime`）.
     */
    val updateTime: Long?,
    /**
     * 创建者 UIN.
     */
    val uin: String?,
    /**
     * 创建者昵称.
     */
    val nick: String?,
    /**
     * 大图封面地址（上游键 `bigpicUrl`）.
     */
    val bigpicUrl: String?,
    /**
     * 专辑拼接封面地址（上游键 `albumPicUrl`）.
     */
    val albumPicUrl: String?,
    /**
     * 创建者头像.
     */
    val avatar: String?,
    /**
     * 身份图标地址（上游键 `identIcon`）.
     */
    val identIcon: String?,
    /**
     * 分层装饰地址（上游键 `layerUrl`）.
     */
    val layerUrl: String?,
    /**
     * 是否失效.
     */
    val invalid: Boolean?,
    /**
     * 目录展示标记（上游键 `dirShow`）.
     */
    val dirShow: Long?,
    /**
     * 创建者收藏量（上游键 `fav_cnt`）.
     */
    val createFavCnt: Long?,
    /**
     * 播放量.
     */
    val playCnt: Long?,
    /**
     * 评论数.
     */
    val commentCnt: Long?,
    /**
     * 操作类型标记（上游键 `opType`）.
     */
    val opType: Long?,
    /**
     * 排序权重（上游键 `sortWeight`）.
     */
    val sortWeight: Long?
) {
    internal fun wireSize(): Int {
        return 1 + (this.id?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.dirid?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.title?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.picurl?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.desc?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.songnum?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.listennum?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.createTime?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.updateTime?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.uin?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.nick?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.bigpicUrl?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.albumPicUrl?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.avatar?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.identIcon?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.layerUrl?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.invalid?.let { __boltffi_value_0 -> 1 } ?: 0) + 1 + (this.dirShow?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.createFavCnt?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.playCnt?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.commentCnt?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.opType?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.sortWeight?.let { __boltffi_value_0 -> 8 } ?: 0)
    }

    internal fun writeTo(writer: WireWriter) {
        writer.writeOptionalValue(this.id, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.dirid, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.title, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.picurl, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.desc, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.songnum, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.listennum, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.createTime, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.updateTime, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.uin, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.nick, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.bigpicUrl, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.albumPicUrl, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.avatar, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.identIcon, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.layerUrl, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.invalid, { writer, __boltffi_value_0 -> writer.writeBool(__boltffi_value_0) })
        writer.writeOptionalValue(this.dirShow, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.createFavCnt, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.playCnt, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.commentCnt, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.opType, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.sortWeight, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
    }

    internal fun toByteArray(): ByteArray {
        val buffer = WireWriterPool.acquire(wireSize())
        val writer = buffer.writer
        try {
            writeTo(writer)
            return buffer.bytes()
        } finally {
            buffer.close()
        }
    }

    companion object {
        internal fun fromReader(reader: WireReader): UserPlaylistSummary {
            return UserPlaylistSummary(
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readBool() }),
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readI64() })
            )
        }

        internal fun fromByteArray(bytes: ByteArray): UserPlaylistSummary {
            val reader = WireReader(bytes)
            return fromReader(reader)
        }
    }
}


/**
 * 参考 `UserCreatedSonglistResponse`：用户创建歌单列表页响应.
 */
data class UserCreatedSonglistResponse(
    /**
     * 歌单总数.
     */
    val total: Long?,
    /**
     * 当前页歌单摘要列表（上游键 `v_playlist`）.
     */
    val playlists: List<UserPlaylistSummary>?,
    /**
     * 上游返回的删除歌单 ID 标记（上游键 `v_delTid`）.
     */
    val deletedIds: LongArray?,
    /**
     * 是否已经拉取完成（上游键 `bFinish`）.
     */
    val finished: Boolean?
) {
    internal fun wireSize(): Int {
        return 1 + (this.total?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.playlists?.let { __boltffi_value_0 -> 4 + __boltffi_value_0.sumOf { __boltffi_value_1 -> val __boltffi_size: kotlin.Int = __boltffi_value_1.wireSize(); __boltffi_size } } ?: 0) + 1 + (this.deletedIds?.let { __boltffi_value_0 -> 4 + __boltffi_value_0.size * 8 } ?: 0) + 1 + (this.finished?.let { __boltffi_value_0 -> 1 } ?: 0)
    }

    internal fun writeTo(writer: WireWriter) {
        writer.writeOptionalValue(this.total, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.playlists, { writer, __boltffi_value_0 -> writer.writeSequence(__boltffi_value_0, __boltffi_value_0.size, { writer, __boltffi_value_1 -> __boltffi_value_1.writeTo(writer) }) })
        writer.writeOptionalValue(this.deletedIds, { writer, __boltffi_value_0 -> writer.writeLongArray(__boltffi_value_0) })
        writer.writeOptionalValue(this.finished, { writer, __boltffi_value_0 -> writer.writeBool(__boltffi_value_0) })
    }

    internal fun toByteArray(): ByteArray {
        val buffer = WireWriterPool.acquire(wireSize())
        val writer = buffer.writer
        try {
            writeTo(writer)
            return buffer.bytes()
        } finally {
            buffer.close()
        }
    }

    companion object {
        internal fun fromReader(reader: WireReader): UserCreatedSonglistResponse {
            return UserCreatedSonglistResponse(
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readSequence({ reader -> UserPlaylistSummary.fromReader(reader) }) }),
                reader.readOptionalValue({ reader -> reader.readLongArray() }),
                reader.readOptionalValue({ reader -> reader.readBool() })
            )
        }

        internal fun fromByteArray(bytes: ByteArray): UserCreatedSonglistResponse {
            val reader = WireReader(bytes)
            return fromReader(reader)
        }
    }
}


/**
 * One word, with the times the service gave it (milliseconds).
 */
data class QrcWord(
    val text: String,
    val startMs: Long,
    val durationMs: Long
) {
    internal fun wireSize(): Int {
        return 4 + Utf8Codec.maxBytes(this.text) + 8 + 8
    }

    internal fun writeTo(writer: WireWriter) {
        writer.writeString(this.text)
        writer.writeI64(this.startMs)
        writer.writeI64(this.durationMs)
    }

    internal fun toByteArray(): ByteArray {
        val buffer = WireWriterPool.acquire(wireSize())
        val writer = buffer.writer
        try {
            writeTo(writer)
            return buffer.bytes()
        } finally {
            buffer.close()
        }
    }

    companion object {
        internal fun fromReader(reader: WireReader): QrcWord {
            return QrcWord(
                reader.readString(),
                reader.readI64(),
                reader.readI64()
            )
        }

        internal fun fromByteArray(bytes: ByteArray): QrcWord {
            val reader = WireReader(bytes)
            return fromReader(reader)
        }
    }
}


/**
 * One lyric line.
 */
data class QrcLine(
    val startMs: Long,
    val durationMs: Long,
    val words: List<QrcWord>
) {
    internal fun wireSize(): Int {
        return 8 + 8 + 4 + this.words.sumOf { __boltffi_value_0 -> val __boltffi_size: kotlin.Int = __boltffi_value_0.wireSize(); __boltffi_size }
    }

    internal fun writeTo(writer: WireWriter) {
        writer.writeI64(this.startMs)
        writer.writeI64(this.durationMs)
        writer.writeSequence(this.words, this.words.size, { writer, __boltffi_value_0 -> __boltffi_value_0.writeTo(writer) })
    }

    internal fun toByteArray(): ByteArray {
        val buffer = WireWriterPool.acquire(wireSize())
        val writer = buffer.writer
        try {
            writeTo(writer)
            return buffer.bytes()
        } finally {
            buffer.close()
        }
    }

    companion object {
        internal fun fromReader(reader: WireReader): QrcLine {
            return QrcLine(
                reader.readI64(),
                reader.readI64(),
                reader.readSequence({ reader -> QrcWord.fromReader(reader) })
            )
        }

        internal fun fromByteArray(bytes: ByteArray): QrcLine {
            val reader = WireReader(bytes)
            return fromReader(reader)
        }
    }
}


/**
 * What the component is enforcing after a `set_rate_limit`.
 */
data class RateLimitConfigModel(
    val enabled: Boolean,
    val windowSeconds: Long,
    val maxRequests: Long
) {
    internal fun wireSize(): Int {
        return 1 + 8 + 8
    }

    internal fun writeTo(writer: WireWriter) {
        writer.writeBool(this.enabled)
        writer.writeI64(this.windowSeconds)
        writer.writeI64(this.maxRequests)
    }

    internal fun toByteArray(): ByteArray {
        val buffer = WireWriterPool.acquire(wireSize())
        val writer = buffer.writer
        try {
            writeTo(writer)
            return buffer.bytes()
        } finally {
            buffer.close()
        }
    }

    companion object {
        internal fun fromReader(reader: WireReader): RateLimitConfigModel {
            return RateLimitConfigModel(
                reader.readBool(),
                reader.readI64(),
                reader.readI64()
            )
        }

        internal fun fromByteArray(bytes: ByteArray): RateLimitConfigModel {
            val reader = WireReader(bytes)
            return fromReader(reader)
        }
    }
}


/**
 * What the component is enforcing after a `set_breaker`.
 */
data class BreakerConfigModel(
    val enabled: Boolean,
    val failureThreshold: Long,
    val failureWindowSeconds: Long,
    val openSeconds: Long
) {
    internal fun wireSize(): Int {
        return 1 + 8 + 8 + 8
    }

    internal fun writeTo(writer: WireWriter) {
        writer.writeBool(this.enabled)
        writer.writeI64(this.failureThreshold)
        writer.writeI64(this.failureWindowSeconds)
        writer.writeI64(this.openSeconds)
    }

    internal fun toByteArray(): ByteArray {
        val buffer = WireWriterPool.acquire(wireSize())
        val writer = buffer.writer
        try {
            writeTo(writer)
            return buffer.bytes()
        } finally {
            buffer.close()
        }
    }

    companion object {
        internal fun fromReader(reader: WireReader): BreakerConfigModel {
            return BreakerConfigModel(
                reader.readBool(),
                reader.readI64(),
                reader.readI64(),
                reader.readI64()
            )
        }

        internal fun fromByteArray(bytes: ByteArray): BreakerConfigModel {
            val reader = WireReader(bytes)
            return fromReader(reader)
        }
    }
}


/**
 * What the download engine is doing.
 */
data class Aria2Status(
    val installed: Boolean,
    val running: Boolean,
    val binary: String,
    val port: Long,
    val version: String?,
    val active: Long,
    val downloads: Long,
    val waiting: Long,
    val stopped: Long,
    val downloadSpeed: Long,
    val options: Aria2OptionsModel
) {
    internal fun wireSize(): Int {
        return 1 + 1 + 4 + Utf8Codec.maxBytes(this.binary) + 8 + 1 + (this.version?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 8 + 8 + 8 + 8 + 8 + this.options.wireSize()
    }

    internal fun writeTo(writer: WireWriter) {
        writer.writeBool(this.installed)
        writer.writeBool(this.running)
        writer.writeString(this.binary)
        writer.writeI64(this.port)
        writer.writeOptionalValue(this.version, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeI64(this.active)
        writer.writeI64(this.downloads)
        writer.writeI64(this.waiting)
        writer.writeI64(this.stopped)
        writer.writeI64(this.downloadSpeed)
        this.options.writeTo(writer)
    }

    internal fun toByteArray(): ByteArray {
        val buffer = WireWriterPool.acquire(wireSize())
        val writer = buffer.writer
        try {
            writeTo(writer)
            return buffer.bytes()
        } finally {
            buffer.close()
        }
    }

    companion object {
        internal fun fromReader(reader: WireReader): Aria2Status {
            return Aria2Status(
                reader.readBool(),
                reader.readBool(),
                reader.readString(),
                reader.readI64(),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readI64(),
                reader.readI64(),
                reader.readI64(),
                reader.readI64(),
                reader.readI64(),
                Aria2OptionsModel.fromReader(reader)
            )
        }

        internal fun fromByteArray(bytes: ByteArray): Aria2Status {
            val reader = WireReader(bytes)
            return fromReader(reader)
        }
    }
}


/**
 * The download engine's tunables, as the settings page shows them.
 */
data class Aria2OptionsModel(
    val split: Long,
    val maxConnectionPerServer: Long,
    val maxConcurrentDownloads: Long,
    val minSplitSizeMib: Long,
    val maxOverallDownloadLimitKib: Long,
    /**
     * The RPC port. Applied when the engine next starts.
     */
    val port: Long
) {
    internal fun wireSize(): Int {
        return 48
    }

    internal fun writeTo(writer: WireWriter) {
        writer.writeI64(split)
        writer.writeI64(maxConnectionPerServer)
        writer.writeI64(maxConcurrentDownloads)
        writer.writeI64(minSplitSizeMib)
        writer.writeI64(maxOverallDownloadLimitKib)
        writer.writeI64(port)
    }
    internal fun toByteArray(): ByteArray {
        val buffer = java.nio.ByteBuffer
            .allocate(STRUCT_SIZE)
            .order(java.nio.ByteOrder.nativeOrder())
        writeTo(buffer, 0)
        return buffer.array()
    }

    internal fun toDirectBuffer(): java.nio.ByteBuffer {
        val buffer = java.nio.ByteBuffer
            .allocateDirect(STRUCT_SIZE)
            .order(java.nio.ByteOrder.nativeOrder())
        writeTo(buffer, 0)
        return buffer
    }

    internal fun writeTo(buffer: java.nio.ByteBuffer, offset: Int) {
        buffer.putLong(offset, split)
        buffer.putLong(offset + 8, maxConnectionPerServer)
        buffer.putLong(offset + 16, maxConcurrentDownloads)
        buffer.putLong(offset + 24, minSplitSizeMib)
        buffer.putLong(offset + 32, maxOverallDownloadLimitKib)
        buffer.putLong(offset + 40, port)
    }

    companion object {
        internal const val STRUCT_SIZE: Int = 48
        internal fun fromReader(reader: WireReader): Aria2OptionsModel {
            return Aria2OptionsModel(
                reader.readI64(),
                reader.readI64(),
                reader.readI64(),
                reader.readI64(),
                reader.readI64(),
                reader.readI64()
            )
        }

        internal fun fromByteArray(bytes: ByteArray): Aria2OptionsModel {
            require(bytes.size == STRUCT_SIZE)
            val buffer = java.nio.ByteBuffer
                .wrap(bytes)
                .order(java.nio.ByteOrder.nativeOrder())
            return fromBuffer(buffer, 0)
        }

        internal fun fromBuffer(buffer: java.nio.ByteBuffer, offset: Int): Aria2OptionsModel {
            return Aria2OptionsModel(
                buffer.getLong(offset),
                buffer.getLong(offset + 8),
                buffer.getLong(offset + 16),
                buffer.getLong(offset + 24),
                buffer.getLong(offset + 32),
                buffer.getLong(offset + 40)
            )
        }
    }
}


/**
 * One queued file: the gid from `aria2_add`, or its live state from `aria2_tell`.
 */
data class Aria2Download(
    val gid: String,
    val status: String,
    val completed: Long,
    val total: Long,
    val speed: Long,
    val path: String,
    val error: String,
    val errorCode: Long
) {
    internal fun wireSize(): Int {
        return 4 + Utf8Codec.maxBytes(this.gid) + 4 + Utf8Codec.maxBytes(this.status) + 8 + 8 + 8 + 4 + Utf8Codec.maxBytes(this.path) + 4 + Utf8Codec.maxBytes(this.error) + 8
    }

    internal fun writeTo(writer: WireWriter) {
        writer.writeString(this.gid)
        writer.writeString(this.status)
        writer.writeI64(this.completed)
        writer.writeI64(this.total)
        writer.writeI64(this.speed)
        writer.writeString(this.path)
        writer.writeString(this.error)
        writer.writeI64(this.errorCode)
    }

    internal fun toByteArray(): ByteArray {
        val buffer = WireWriterPool.acquire(wireSize())
        val writer = buffer.writer
        try {
            writeTo(writer)
            return buffer.bytes()
        } finally {
            buffer.close()
        }
    }

    companion object {
        internal fun fromReader(reader: WireReader): Aria2Download {
            return Aria2Download(
                reader.readString(),
                reader.readString(),
                reader.readI64(),
                reader.readI64(),
                reader.readI64(),
                reader.readString(),
                reader.readString(),
                reader.readI64()
            )
        }

        internal fun fromByteArray(bytes: ByteArray): Aria2Download {
            val reader = WireReader(bytes)
            return fromReader(reader)
        }
    }
}


/**
 * The engine's task list, as the toolbar's download list shows it.
 */
data class Aria2TaskList(
    val downloads: List<Aria2Task>,
    val removed: List<String>
) {
    internal fun wireSize(): Int {
        return 4 + this.downloads.sumOf { __boltffi_value_0 -> val __boltffi_size: kotlin.Int = __boltffi_value_0.wireSize(); __boltffi_size } + 4 + this.removed.sumOf { __boltffi_value_0 -> val __boltffi_size: kotlin.Int = 4 + Utf8Codec.maxBytes(__boltffi_value_0); __boltffi_size }
    }

    internal fun writeTo(writer: WireWriter) {
        writer.writeSequence(this.downloads, this.downloads.size, { writer, __boltffi_value_0 -> __boltffi_value_0.writeTo(writer) })
        writer.writeSequence(this.removed, this.removed.size, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
    }

    internal fun toByteArray(): ByteArray {
        val buffer = WireWriterPool.acquire(wireSize())
        val writer = buffer.writer
        try {
            writeTo(writer)
            return buffer.bytes()
        } finally {
            buffer.close()
        }
    }

    companion object {
        internal fun fromReader(reader: WireReader): Aria2TaskList {
            return Aria2TaskList(
                reader.readSequence({ reader -> Aria2Task.fromReader(reader) }),
                reader.readSequence({ reader -> reader.readString() })
            )
        }

        internal fun fromByteArray(bytes: ByteArray): Aria2TaskList {
            val reader = WireReader(bytes)
            return fromReader(reader)
        }
    }
}


/**
 * One task.
 */
data class Aria2Task(
    val gid: String,
    /**
     * `active` / `waiting` / `paused` / `complete` / `error` / `removed`, in
     * aria2's own vocabulary.
     */
    val status: String,
    val completed: Long,
    val total: Long,
    val speed: Long,
    val name: String,
    val path: String,
    val error: String
) {
    internal fun wireSize(): Int {
        return 4 + Utf8Codec.maxBytes(this.gid) + 4 + Utf8Codec.maxBytes(this.status) + 8 + 8 + 8 + 4 + Utf8Codec.maxBytes(this.name) + 4 + Utf8Codec.maxBytes(this.path) + 4 + Utf8Codec.maxBytes(this.error)
    }

    internal fun writeTo(writer: WireWriter) {
        writer.writeString(this.gid)
        writer.writeString(this.status)
        writer.writeI64(this.completed)
        writer.writeI64(this.total)
        writer.writeI64(this.speed)
        writer.writeString(this.name)
        writer.writeString(this.path)
        writer.writeString(this.error)
    }

    internal fun toByteArray(): ByteArray {
        val buffer = WireWriterPool.acquire(wireSize())
        val writer = buffer.writer
        try {
            writeTo(writer)
            return buffer.bytes()
        } finally {
            buffer.close()
        }
    }

    companion object {
        internal fun fromReader(reader: WireReader): Aria2Task {
            return Aria2Task(
                reader.readString(),
                reader.readString(),
                reader.readI64(),
                reader.readI64(),
                reader.readI64(),
                reader.readString(),
                reader.readString(),
                reader.readString()
            )
        }

        internal fun fromByteArray(bytes: ByteArray): Aria2Task {
            val reader = WireReader(bytes)
            return fromReader(reader)
        }
    }
}


/**
 * A page of tracks, with the list's own size when the endpoint reports it.
 */
data class TrackPage(
    val tracks: List<Track>,
    /**
     * `None` when the endpoint reports no total, which is not the same as zero.
     */
    val total: Long?,
    /**
     * Next upstream row offset, including malformed rows omitted from `tracks`.
     */
    val nextOffset: Long?
) {
    internal fun wireSize(): Int {
        return 4 + this.tracks.sumOf { __boltffi_value_0 -> val __boltffi_size: kotlin.Int = __boltffi_value_0.wireSize(); __boltffi_size } + 1 + (this.total?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.nextOffset?.let { __boltffi_value_0 -> 8 } ?: 0)
    }

    internal fun writeTo(writer: WireWriter) {
        writer.writeSequence(this.tracks, this.tracks.size, { writer, __boltffi_value_0 -> __boltffi_value_0.writeTo(writer) })
        writer.writeOptionalValue(this.total, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.nextOffset, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
    }

    internal fun toByteArray(): ByteArray {
        val buffer = WireWriterPool.acquire(wireSize())
        val writer = buffer.writer
        try {
            writeTo(writer)
            return buffer.bytes()
        } finally {
            buffer.close()
        }
    }

    companion object {
        internal fun fromReader(reader: WireReader): TrackPage {
            return TrackPage(
                reader.readSequence({ reader -> Track.fromReader(reader) }),
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readI64() })
            )
        }

        internal fun fromByteArray(bytes: ByteArray): TrackPage {
            val reader = WireReader(bytes)
            return fromReader(reader)
        }
    }
}


/**
 * A page of an artist's albums, preserving its total.
 */
data class AlbumPage(
    val albums: List<Album>,
    val total: Long?,
    /**
     * Next upstream row offset, including rows omitted from `albums` — the
     * same semantics as [`TrackPage::next_offset`].
     */
    val nextOffset: Long?
) {
    internal fun wireSize(): Int {
        return 4 + this.albums.sumOf { __boltffi_value_0 -> val __boltffi_size: kotlin.Int = __boltffi_value_0.wireSize(); __boltffi_size } + 1 + (this.total?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.nextOffset?.let { __boltffi_value_0 -> 8 } ?: 0)
    }

    internal fun writeTo(writer: WireWriter) {
        writer.writeSequence(this.albums, this.albums.size, { writer, __boltffi_value_0 -> __boltffi_value_0.writeTo(writer) })
        writer.writeOptionalValue(this.total, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.nextOffset, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
    }

    internal fun toByteArray(): ByteArray {
        val buffer = WireWriterPool.acquire(wireSize())
        val writer = buffer.writer
        try {
            writeTo(writer)
            return buffer.bytes()
        } finally {
            buffer.close()
        }
    }

    companion object {
        internal fun fromReader(reader: WireReader): AlbumPage {
            return AlbumPage(
                reader.readSequence({ reader -> Album.fromReader(reader) }),
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readI64() })
            )
        }

        internal fun fromByteArray(bytes: ByteArray): AlbumPage {
            val reader = WireReader(bytes)
            return fromReader(reader)
        }
    }
}


/**
 * One credited singer of a track.
 */
data class Singer(
    val mid: String?,
    val name: String?
) {
    internal fun wireSize(): Int {
        return 1 + (this.mid?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.name?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0)
    }

    internal fun writeTo(writer: WireWriter) {
        writer.writeOptionalValue(this.mid, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.name, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
    }

    internal fun toByteArray(): ByteArray {
        val buffer = WireWriterPool.acquire(wireSize())
        val writer = buffer.writer
        try {
            writeTo(writer)
            return buffer.bytes()
        } finally {
            buffer.close()
        }
    }

    companion object {
        internal fun fromReader(reader: WireReader): Singer {
            return Singer(
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() })
            )
        }

        internal fun fromByteArray(bytes: ByteArray): Singer {
            val reader = WireReader(bytes)
            return fromReader(reader)
        }
    }
}


/**
 * A song, as the catalogue describes it.
 */
data class Track(
    val songId: Long?,
    /**
     * The song's mid — the handle every other call takes.
     */
    val songMid: String,
    val mediaMid: String?,
    val title: String,
    /**
     * All credited singers, joined with `", "`.
     */
    val artist: String,
    val album: String?,
    val albumMid: String?,
    /**
     * The numeric album id, which is what the album endpoints address.
     */
    val albumId: Long?,
    /**
     * The wire spells this `imageURL`, which `rename_all` alone would mangle.
     */
    val imageUrl: String?,
    val duration: Long?,
    /**
     * `1` marks a track the account likely cannot play (subscription).
     */
    val payPlay: Long?,
    val singerMid: String?,
    val singers: List<Singer>?,
    /**
     * Present when the source supplied it (artist "latest" ordering attaches it).
     */
    val releaseDate: String?,
    /**
     * Numeric QQ genre code; absent when the endpoint does not provide one.
     */
    val genre: Long?,
    /**
     * Available file variants. Vec keeps this model representable by BoltFFI.
     */
    val fileSizes: List<TrackFileSize>
) {
    internal fun wireSize(): Int {
        return 1 + (this.songId?.let { __boltffi_value_0 -> 8 } ?: 0) + 4 + Utf8Codec.maxBytes(this.songMid) + 1 + (this.mediaMid?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 4 + Utf8Codec.maxBytes(this.title) + 4 + Utf8Codec.maxBytes(this.artist) + 1 + (this.album?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.albumMid?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.albumId?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.imageUrl?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.duration?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.payPlay?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.singerMid?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.singers?.let { __boltffi_value_0 -> 4 + __boltffi_value_0.sumOf { __boltffi_value_1 -> val __boltffi_size: kotlin.Int = __boltffi_value_1.wireSize(); __boltffi_size } } ?: 0) + 1 + (this.releaseDate?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.genre?.let { __boltffi_value_0 -> 8 } ?: 0) + 4 + this.fileSizes.sumOf { __boltffi_value_0 -> val __boltffi_size: kotlin.Int = __boltffi_value_0.wireSize(); __boltffi_size }
    }

    internal fun writeTo(writer: WireWriter) {
        writer.writeOptionalValue(this.songId, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeString(this.songMid)
        writer.writeOptionalValue(this.mediaMid, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeString(this.title)
        writer.writeString(this.artist)
        writer.writeOptionalValue(this.album, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.albumMid, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.albumId, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.imageUrl, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.duration, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.payPlay, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.singerMid, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.singers, { writer, __boltffi_value_0 -> writer.writeSequence(__boltffi_value_0, __boltffi_value_0.size, { writer, __boltffi_value_1 -> __boltffi_value_1.writeTo(writer) }) })
        writer.writeOptionalValue(this.releaseDate, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.genre, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeSequence(this.fileSizes, this.fileSizes.size, { writer, __boltffi_value_0 -> __boltffi_value_0.writeTo(writer) })
    }

    internal fun toByteArray(): ByteArray {
        val buffer = WireWriterPool.acquire(wireSize())
        val writer = buffer.writer
        try {
            writeTo(writer)
            return buffer.bytes()
        } finally {
            buffer.close()
        }
    }

    companion object {
        internal fun fromReader(reader: WireReader): Track {
            return Track(
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readString(),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readString(),
                reader.readString(),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readSequence({ reader -> Singer.fromReader(reader) }) }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readSequence({ reader -> TrackFileSize.fromReader(reader) })
            )
        }

        internal fun fromByteArray(bytes: ByteArray): Track {
            val reader = WireReader(bytes)
            return fromReader(reader)
        }
    }
}


/**
 * A media file size advertised in a track's `file` object.
 */
data class TrackFileSize(
    val name: String,
    val bytes: Long
) {
    internal fun wireSize(): Int {
        return 4 + Utf8Codec.maxBytes(this.name) + 8
    }

    internal fun writeTo(writer: WireWriter) {
        writer.writeString(this.name)
        writer.writeI64(this.bytes)
    }

    internal fun toByteArray(): ByteArray {
        val buffer = WireWriterPool.acquire(wireSize())
        val writer = buffer.writer
        try {
            writeTo(writer)
            return buffer.bytes()
        } finally {
            buffer.close()
        }
    }

    companion object {
        internal fun fromReader(reader: WireReader): TrackFileSize {
            return TrackFileSize(
                reader.readString(),
                reader.readI64()
            )
        }

        internal fun fromByteArray(bytes: ByteArray): TrackFileSize {
            val reader = WireReader(bytes)
            return fromReader(reader)
        }
    }
}


/**
 * The account's "我喜欢", with the folder's own total.
 */
data class LikedSongs(
    val title: String,
    val total: Long,
    val tracks: List<Track>
) {
    internal fun wireSize(): Int {
        return 4 + Utf8Codec.maxBytes(this.title) + 8 + 4 + this.tracks.sumOf { __boltffi_value_0 -> val __boltffi_size: kotlin.Int = __boltffi_value_0.wireSize(); __boltffi_size }
    }

    internal fun writeTo(writer: WireWriter) {
        writer.writeString(this.title)
        writer.writeI64(this.total)
        writer.writeSequence(this.tracks, this.tracks.size, { writer, __boltffi_value_0 -> __boltffi_value_0.writeTo(writer) })
    }

    internal fun toByteArray(): ByteArray {
        val buffer = WireWriterPool.acquire(wireSize())
        val writer = buffer.writer
        try {
            writeTo(writer)
            return buffer.bytes()
        } finally {
            buffer.close()
        }
    }

    companion object {
        internal fun fromReader(reader: WireReader): LikedSongs {
            return LikedSongs(
                reader.readString(),
                reader.readI64(),
                reader.readSequence({ reader -> Track.fromReader(reader) })
            )
        }

        internal fun fromByteArray(bytes: ByteArray): LikedSongs {
            val reader = WireReader(bytes)
            return fromReader(reader)
        }
    }
}


/**
 * An album.
 */
data class Album(
    val id: Long,
    val title: String,
    val albumMid: String?,
    val coverUrl: String?,
    val artist: String?,
    val releaseDate: String?,
    val songCount: Long?
) {
    internal fun wireSize(): Int {
        return 8 + 4 + Utf8Codec.maxBytes(this.title) + 1 + (this.albumMid?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.coverUrl?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.artist?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.releaseDate?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.songCount?.let { __boltffi_value_0 -> 8 } ?: 0)
    }

    internal fun writeTo(writer: WireWriter) {
        writer.writeI64(this.id)
        writer.writeString(this.title)
        writer.writeOptionalValue(this.albumMid, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.coverUrl, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.artist, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.releaseDate, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.songCount, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
    }

    internal fun toByteArray(): ByteArray {
        val buffer = WireWriterPool.acquire(wireSize())
        val writer = buffer.writer
        try {
            writeTo(writer)
            return buffer.bytes()
        } finally {
            buffer.close()
        }
    }

    companion object {
        internal fun fromReader(reader: WireReader): Album {
            return Album(
                reader.readI64(),
                reader.readString(),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readI64() })
            )
        }

        internal fun fromByteArray(bytes: ByteArray): Album {
            val reader = WireReader(bytes)
            return fromReader(reader)
        }
    }
}


/**
 * A playlist (the account's own, or one found by search).
 */
data class Playlist(
    val id: Long,
    val title: String,
    val coverUrl: String?,
    val creator: String?,
    val songCount: Long?,
    val playCount: Long?
) {
    internal fun wireSize(): Int {
        return 8 + 4 + Utf8Codec.maxBytes(this.title) + 1 + (this.coverUrl?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.creator?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.songCount?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.playCount?.let { __boltffi_value_0 -> 8 } ?: 0)
    }

    internal fun writeTo(writer: WireWriter) {
        writer.writeI64(this.id)
        writer.writeString(this.title)
        writer.writeOptionalValue(this.coverUrl, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.creator, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.songCount, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.playCount, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
    }

    internal fun toByteArray(): ByteArray {
        val buffer = WireWriterPool.acquire(wireSize())
        val writer = buffer.writer
        try {
            writeTo(writer)
            return buffer.bytes()
        } finally {
            buffer.close()
        }
    }

    companion object {
        internal fun fromReader(reader: WireReader): Playlist {
            return Playlist(
                reader.readI64(),
                reader.readString(),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readI64() })
            )
        }

        internal fun fromByteArray(bytes: ByteArray): Playlist {
            val reader = WireReader(bytes)
            return fromReader(reader)
        }
    }
}


/**
 * A singer.
 */
data class Artist(
    val singerMid: String,
    val name: String,
    val coverUrl: String?,
    val songCount: Long?,
    val albumCount: Long?,
    val fanCount: Long?
) {
    internal fun wireSize(): Int {
        return 4 + Utf8Codec.maxBytes(this.singerMid) + 4 + Utf8Codec.maxBytes(this.name) + 1 + (this.coverUrl?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.songCount?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.albumCount?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.fanCount?.let { __boltffi_value_0 -> 8 } ?: 0)
    }

    internal fun writeTo(writer: WireWriter) {
        writer.writeString(this.singerMid)
        writer.writeString(this.name)
        writer.writeOptionalValue(this.coverUrl, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.songCount, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.albumCount, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.fanCount, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
    }

    internal fun toByteArray(): ByteArray {
        val buffer = WireWriterPool.acquire(wireSize())
        val writer = buffer.writer
        try {
            writeTo(writer)
            return buffer.bytes()
        } finally {
            buffer.close()
        }
    }

    companion object {
        internal fun fromReader(reader: WireReader): Artist {
            return Artist(
                reader.readString(),
                reader.readString(),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readI64() })
            )
        }

        internal fun fromByteArray(bytes: ByteArray): Artist {
            val reader = WireReader(bytes)
            return fromReader(reader)
        }
    }
}


/**
 * Who is logged in, as the upstream sees it.
 */
data class LoginStatus(
    val loggedIn: Boolean,
    val musicId: Long?,
    val nickname: String?,
    val vipType: Long?,
    val expired: Boolean?,
    /**
     * Whether the playback ticket (`qm_keyst`) is present. Without it the CDN
     * refuses even tracks the account may play.
     */
    val hasPlaybackKey: Boolean?
) {
    internal fun wireSize(): Int {
        return 1 + 1 + (this.musicId?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.nickname?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.vipType?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.expired?.let { __boltffi_value_0 -> 1 } ?: 0) + 1 + (this.hasPlaybackKey?.let { __boltffi_value_0 -> 1 } ?: 0)
    }

    internal fun writeTo(writer: WireWriter) {
        writer.writeBool(this.loggedIn)
        writer.writeOptionalValue(this.musicId, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.nickname, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.vipType, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.expired, { writer, __boltffi_value_0 -> writer.writeBool(__boltffi_value_0) })
        writer.writeOptionalValue(this.hasPlaybackKey, { writer, __boltffi_value_0 -> writer.writeBool(__boltffi_value_0) })
    }

    internal fun toByteArray(): ByteArray {
        val buffer = WireWriterPool.acquire(wireSize())
        val writer = buffer.writer
        try {
            writeTo(writer)
            return buffer.bytes()
        } finally {
            buffer.close()
        }
    }

    companion object {
        internal fun fromReader(reader: WireReader): LoginStatus {
            return LoginStatus(
                reader.readBool(),
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readBool() }),
                reader.readOptionalValue({ reader -> reader.readBool() })
            )
        }

        internal fun fromByteArray(bytes: ByteArray): LoginStatus {
            val reader = WireReader(bytes)
            return fromReader(reader)
        }
    }
}


/**
 * What this component is and what it serves.
 */
data class ComponentInfo(
    val helperVersion: String,
    val protocolVersion: Long,
    /**
     * Kept for hosts that display a "library version"; this component replaces
     * the library, so it names the protocol work it is built from.
     */
    val libraryVersion: String,
    val methods: List<String>
) {
    internal fun wireSize(): Int {
        return 4 + Utf8Codec.maxBytes(this.helperVersion) + 8 + 4 + Utf8Codec.maxBytes(this.libraryVersion) + 4 + this.methods.sumOf { __boltffi_value_0 -> val __boltffi_size: kotlin.Int = 4 + Utf8Codec.maxBytes(__boltffi_value_0); __boltffi_size }
    }

    internal fun writeTo(writer: WireWriter) {
        writer.writeString(this.helperVersion)
        writer.writeI64(this.protocolVersion)
        writer.writeString(this.libraryVersion)
        writer.writeSequence(this.methods, this.methods.size, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
    }

    internal fun toByteArray(): ByteArray {
        val buffer = WireWriterPool.acquire(wireSize())
        val writer = buffer.writer
        try {
            writeTo(writer)
            return buffer.bytes()
        } finally {
            buffer.close()
        }
    }

    companion object {
        internal fun fromReader(reader: WireReader): ComponentInfo {
            return ComponentInfo(
                reader.readString(),
                reader.readI64(),
                reader.readString(),
                reader.readSequence({ reader -> reader.readString() })
            )
        }

        internal fun fromByteArray(bytes: ByteArray): ComponentInfo {
            val reader = WireReader(bytes)
            return fromReader(reader)
        }
    }
}


/**
 * The state of the two politeness mechanisms, for the host to display.
 */
data class GuardStatus(
    /**
     * `"closed"`, `"half-open"` or `"open"`.
     */
    val breaker: String,
    val rateLimit: RateLimitUsage
) {
    internal fun wireSize(): Int {
        return 4 + Utf8Codec.maxBytes(this.breaker) + this.rateLimit.wireSize()
    }

    internal fun writeTo(writer: WireWriter) {
        writer.writeString(this.breaker)
        this.rateLimit.writeTo(writer)
    }

    internal fun toByteArray(): ByteArray {
        val buffer = WireWriterPool.acquire(wireSize())
        val writer = buffer.writer
        try {
            writeTo(writer)
            return buffer.bytes()
        } finally {
            buffer.close()
        }
    }

    companion object {
        internal fun fromReader(reader: WireReader): GuardStatus {
            return GuardStatus(
                reader.readString(),
                RateLimitUsage.fromReader(reader)
            )
        }

        internal fun fromByteArray(bytes: ByteArray): GuardStatus {
            val reader = WireReader(bytes)
            return fromReader(reader)
        }
    }
}


data class RateLimitUsage(
    val read: UInt,
    val interactive: UInt,
    val playback: UInt,
    val account: UInt,
    val write: UInt
) {
    internal fun wireSize(): Int {
        return 20
    }

    internal fun writeTo(writer: WireWriter) {
        writer.writeU32(read)
        writer.writeU32(interactive)
        writer.writeU32(playback)
        writer.writeU32(account)
        writer.writeU32(write)
    }
    internal fun toByteArray(): ByteArray {
        val buffer = java.nio.ByteBuffer
            .allocate(STRUCT_SIZE)
            .order(java.nio.ByteOrder.nativeOrder())
        writeTo(buffer, 0)
        return buffer.array()
    }

    internal fun toDirectBuffer(): java.nio.ByteBuffer {
        val buffer = java.nio.ByteBuffer
            .allocateDirect(STRUCT_SIZE)
            .order(java.nio.ByteOrder.nativeOrder())
        writeTo(buffer, 0)
        return buffer
    }

    internal fun writeTo(buffer: java.nio.ByteBuffer, offset: Int) {
        buffer.putInt(offset, read.toInt())
        buffer.putInt(offset + 4, interactive.toInt())
        buffer.putInt(offset + 8, playback.toInt())
        buffer.putInt(offset + 12, account.toInt())
        buffer.putInt(offset + 16, write.toInt())
    }

    companion object {
        internal const val STRUCT_SIZE: Int = 20
        internal fun fromReader(reader: WireReader): RateLimitUsage {
            return RateLimitUsage(
                reader.readU32(),
                reader.readU32(),
                reader.readU32(),
                reader.readU32(),
                reader.readU32()
            )
        }

        internal fun fromByteArray(bytes: ByteArray): RateLimitUsage {
            require(bytes.size == STRUCT_SIZE)
            val buffer = java.nio.ByteBuffer
                .wrap(bytes)
                .order(java.nio.ByteOrder.nativeOrder())
            return fromBuffer(buffer, 0)
        }

        internal fun fromBuffer(buffer: java.nio.ByteBuffer, offset: Int): RateLimitUsage {
            return RateLimitUsage(
                buffer.getInt(offset).toUInt(),
                buffer.getInt(offset + 4).toUInt(),
                buffer.getInt(offset + 8).toUInt(),
                buffer.getInt(offset + 12).toUInt(),
                buffer.getInt(offset + 16).toUInt()
            )
        }
    }
}


/**
 * A song's catalogue entry: the facts plus the prose the service publishes.
 */
data class SongDetail(
    val songMid: String,
    val songId: Long?,
    val title: String?,
    val artist: String?,
    val album: String?,
    val albumMid: String?,
    /**
     * The 简介. Empty for most songs, which is an answer and not a failure.
     */
    val description: String,
    val genre: List<String>,
    val language: String?,
    val company: String?,
    val releaseDate: String?,
    val duration: Long?
) {
    internal fun wireSize(): Int {
        return 4 + Utf8Codec.maxBytes(this.songMid) + 1 + (this.songId?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.title?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.artist?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.album?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.albumMid?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 4 + Utf8Codec.maxBytes(this.description) + 4 + this.genre.sumOf { __boltffi_value_0 -> val __boltffi_size: kotlin.Int = 4 + Utf8Codec.maxBytes(__boltffi_value_0); __boltffi_size } + 1 + (this.language?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.company?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.releaseDate?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.duration?.let { __boltffi_value_0 -> 8 } ?: 0)
    }

    internal fun writeTo(writer: WireWriter) {
        writer.writeString(this.songMid)
        writer.writeOptionalValue(this.songId, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.title, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.artist, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.album, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.albumMid, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeString(this.description)
        writer.writeSequence(this.genre, this.genre.size, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.language, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.company, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.releaseDate, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.duration, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
    }

    internal fun toByteArray(): ByteArray {
        val buffer = WireWriterPool.acquire(wireSize())
        val writer = buffer.writer
        try {
            writeTo(writer)
            return buffer.bytes()
        } finally {
            buffer.close()
        }
    }

    companion object {
        internal fun fromReader(reader: WireReader): SongDetail {
            return SongDetail(
                reader.readString(),
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readString(),
                reader.readSequence({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readI64() })
            )
        }

        internal fun fromByteArray(bytes: ByteArray): SongDetail {
            val reader = WireReader(bytes)
            return fromReader(reader)
        }
    }
}


/**
 * An album's catalogue entry.
 */
data class AlbumDetail(
    val id: Long?,
    val albumMid: String?,
    val title: String?,
    val artist: String?,
    val coverUrl: String?,
    val description: String?,
    val releaseDate: String?,
    val genre: String?,
    val language: String?,
    val company: String?,
    val songCount: Long?
) {
    internal fun wireSize(): Int {
        return 1 + (this.id?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.albumMid?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.title?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.artist?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.coverUrl?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.description?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.releaseDate?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.genre?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.language?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.company?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.songCount?.let { __boltffi_value_0 -> 8 } ?: 0)
    }

    internal fun writeTo(writer: WireWriter) {
        writer.writeOptionalValue(this.id, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.albumMid, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.title, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.artist, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.coverUrl, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.description, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.releaseDate, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.genre, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.language, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.company, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.songCount, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
    }

    internal fun toByteArray(): ByteArray {
        val buffer = WireWriterPool.acquire(wireSize())
        val writer = buffer.writer
        try {
            writeTo(writer)
            return buffer.bytes()
        } finally {
            buffer.close()
        }
    }

    companion object {
        internal fun fromReader(reader: WireReader): AlbumDetail {
            return AlbumDetail(
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readI64() })
            )
        }

        internal fun fromByteArray(bytes: ByteArray): AlbumDetail {
            val reader = WireReader(bytes)
            return fromReader(reader)
        }
    }
}


/**
 * An artist's profile.
 */
data class ArtistDetail(
    val singerMid: String,
    val name: String,
    val description: String,
    val coverUrl: String?,
    val foreignName: String?,
    val region: String?,
    val genre: List<String>,
    val songCount: Long?,
    val albumCount: Long?,
    val fanCount: Long?
) {
    internal fun wireSize(): Int {
        return 4 + Utf8Codec.maxBytes(this.singerMid) + 4 + Utf8Codec.maxBytes(this.name) + 4 + Utf8Codec.maxBytes(this.description) + 1 + (this.coverUrl?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.foreignName?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.region?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 4 + this.genre.sumOf { __boltffi_value_0 -> val __boltffi_size: kotlin.Int = 4 + Utf8Codec.maxBytes(__boltffi_value_0); __boltffi_size } + 1 + (this.songCount?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.albumCount?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.fanCount?.let { __boltffi_value_0 -> 8 } ?: 0)
    }

    internal fun writeTo(writer: WireWriter) {
        writer.writeString(this.singerMid)
        writer.writeString(this.name)
        writer.writeString(this.description)
        writer.writeOptionalValue(this.coverUrl, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.foreignName, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.region, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeSequence(this.genre, this.genre.size, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.songCount, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.albumCount, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.fanCount, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
    }

    internal fun toByteArray(): ByteArray {
        val buffer = WireWriterPool.acquire(wireSize())
        val writer = buffer.writer
        try {
            writeTo(writer)
            return buffer.bytes()
        } finally {
            buffer.close()
        }
    }

    companion object {
        internal fun fromReader(reader: WireReader): ArtistDetail {
            return ArtistDetail(
                reader.readString(),
                reader.readString(),
                reader.readString(),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readSequence({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readI64() })
            )
        }

        internal fun fromByteArray(bytes: ByteArray): ArtistDetail {
            val reader = WireReader(bytes)
            return fromReader(reader)
        }
    }
}


/**
 * One ranking inside a group.
 */
data class Toplist(
    val id: Long,
    val title: String,
    val coverUrl: String?,
    val updateTime: String?
) {
    internal fun wireSize(): Int {
        return 8 + 4 + Utf8Codec.maxBytes(this.title) + 1 + (this.coverUrl?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.updateTime?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0)
    }

    internal fun writeTo(writer: WireWriter) {
        writer.writeI64(this.id)
        writer.writeString(this.title)
        writer.writeOptionalValue(this.coverUrl, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.updateTime, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
    }

    internal fun toByteArray(): ByteArray {
        val buffer = WireWriterPool.acquire(wireSize())
        val writer = buffer.writer
        try {
            writeTo(writer)
            return buffer.bytes()
        } finally {
            buffer.close()
        }
    }

    companion object {
        internal fun fromReader(reader: WireReader): Toplist {
            return Toplist(
                reader.readI64(),
                reader.readString(),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() })
            )
        }

        internal fun fromByteArray(bytes: ByteArray): Toplist {
            val reader = WireReader(bytes)
            return fromReader(reader)
        }
    }
}


data class ToplistGroup(
    val title: String,
    val toplists: List<Toplist>
) {
    internal fun wireSize(): Int {
        return 4 + Utf8Codec.maxBytes(this.title) + 4 + this.toplists.sumOf { __boltffi_value_0 -> val __boltffi_size: kotlin.Int = __boltffi_value_0.wireSize(); __boltffi_size }
    }

    internal fun writeTo(writer: WireWriter) {
        writer.writeString(this.title)
        writer.writeSequence(this.toplists, this.toplists.size, { writer, __boltffi_value_0 -> __boltffi_value_0.writeTo(writer) })
    }

    internal fun toByteArray(): ByteArray {
        val buffer = WireWriterPool.acquire(wireSize())
        val writer = buffer.writer
        try {
            writeTo(writer)
            return buffer.bytes()
        } finally {
            buffer.close()
        }
    }

    companion object {
        internal fun fromReader(reader: WireReader): ToplistGroup {
            return ToplistGroup(
                reader.readString(),
                reader.readSequence({ reader -> Toplist.fromReader(reader) })
            )
        }

        internal fun fromByteArray(bytes: ByteArray): ToplistGroup {
            val reader = WireReader(bytes)
            return fromReader(reader)
        }
    }
}


data class RadioStation(
    val id: Long,
    val title: String,
    val coverUrl: String?
) {
    internal fun wireSize(): Int {
        return 8 + 4 + Utf8Codec.maxBytes(this.title) + 1 + (this.coverUrl?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0)
    }

    internal fun writeTo(writer: WireWriter) {
        writer.writeI64(this.id)
        writer.writeString(this.title)
        writer.writeOptionalValue(this.coverUrl, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
    }

    internal fun toByteArray(): ByteArray {
        val buffer = WireWriterPool.acquire(wireSize())
        val writer = buffer.writer
        try {
            writeTo(writer)
            return buffer.bytes()
        } finally {
            buffer.close()
        }
    }

    companion object {
        internal fun fromReader(reader: WireReader): RadioStation {
            return RadioStation(
                reader.readI64(),
                reader.readString(),
                reader.readOptionalValue({ reader -> reader.readString() })
            )
        }

        internal fun fromByteArray(bytes: ByteArray): RadioStation {
            val reader = WireReader(bytes)
            return fromReader(reader)
        }
    }
}


data class RadioGroup(
    val title: String,
    val stations: List<RadioStation>
) {
    internal fun wireSize(): Int {
        return 4 + Utf8Codec.maxBytes(this.title) + 4 + this.stations.sumOf { __boltffi_value_0 -> val __boltffi_size: kotlin.Int = __boltffi_value_0.wireSize(); __boltffi_size }
    }

    internal fun writeTo(writer: WireWriter) {
        writer.writeString(this.title)
        writer.writeSequence(this.stations, this.stations.size, { writer, __boltffi_value_0 -> __boltffi_value_0.writeTo(writer) })
    }

    internal fun toByteArray(): ByteArray {
        val buffer = WireWriterPool.acquire(wireSize())
        val writer = buffer.writer
        try {
            writeTo(writer)
            return buffer.bytes()
        } finally {
            buffer.close()
        }
    }

    companion object {
        internal fun fromReader(reader: WireReader): RadioGroup {
            return RadioGroup(
                reader.readString(),
                reader.readSequence({ reader -> RadioStation.fromReader(reader) })
            )
        }

        internal fun fromByteArray(bytes: ByteArray): RadioGroup {
            val reader = WireReader(bytes)
            return fromReader(reader)
        }
    }
}


/**
 * A lyric and the extra tracks the service offers with it. `word_lyric` is the
 * word-level one (the reason the helper channel ever existed).
 */
data class Lyric(
    val lyric: String?,
    val translation: String?,
    val romanization: String?,
    val wordLyric: String?,
    val qrcLines: List<QrcLine>?,
    val romanLines: List<QrcLine>?
) {
    internal fun wireSize(): Int {
        return 1 + (this.lyric?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.translation?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.romanization?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.wordLyric?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.qrcLines?.let { __boltffi_value_0 -> 4 + __boltffi_value_0.sumOf { __boltffi_value_1 -> val __boltffi_size: kotlin.Int = __boltffi_value_1.wireSize(); __boltffi_size } } ?: 0) + 1 + (this.romanLines?.let { __boltffi_value_0 -> 4 + __boltffi_value_0.sumOf { __boltffi_value_1 -> val __boltffi_size: kotlin.Int = __boltffi_value_1.wireSize(); __boltffi_size } } ?: 0)
    }

    internal fun writeTo(writer: WireWriter) {
        writer.writeOptionalValue(this.lyric, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.translation, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.romanization, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.wordLyric, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.qrcLines, { writer, __boltffi_value_0 -> writer.writeSequence(__boltffi_value_0, __boltffi_value_0.size, { writer, __boltffi_value_1 -> __boltffi_value_1.writeTo(writer) }) })
        writer.writeOptionalValue(this.romanLines, { writer, __boltffi_value_0 -> writer.writeSequence(__boltffi_value_0, __boltffi_value_0.size, { writer, __boltffi_value_1 -> __boltffi_value_1.writeTo(writer) }) })
    }

    internal fun toByteArray(): ByteArray {
        val buffer = WireWriterPool.acquire(wireSize())
        val writer = buffer.writer
        try {
            writeTo(writer)
            return buffer.bytes()
        } finally {
            buffer.close()
        }
    }

    companion object {
        internal fun fromReader(reader: WireReader): Lyric {
            return Lyric(
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readSequence({ reader -> QrcLine.fromReader(reader) }) }),
                reader.readOptionalValue({ reader -> reader.readSequence({ reader -> QrcLine.fromReader(reader) }) })
            )
        }

        internal fun fromByteArray(bytes: ByteArray): Lyric {
            val reader = WireReader(bytes)
            return fromReader(reader)
        }
    }
}


/**
 * A typed receipt for the `我喜欢` write, including upstream business codes.
 */
data class LikeReceipt(
    val success: Boolean,
    val code: Long,
    /**
     * `1000` is the account-write throttle code.
     */
    val throttled: Boolean
) {
    internal fun wireSize(): Int {
        return 1 + 8 + 1
    }

    internal fun writeTo(writer: WireWriter) {
        writer.writeBool(this.success)
        writer.writeI64(this.code)
        writer.writeBool(this.throttled)
    }

    internal fun toByteArray(): ByteArray {
        val buffer = WireWriterPool.acquire(wireSize())
        val writer = buffer.writer
        try {
            writeTo(writer)
            return buffer.bytes()
        } finally {
            buffer.close()
        }
    }

    companion object {
        internal fun fromReader(reader: WireReader): LikeReceipt {
            return LikeReceipt(
                reader.readBool(),
                reader.readI64(),
                reader.readBool()
            )
        }

        internal fun fromByteArray(bytes: ByteArray): LikeReceipt {
            val reader = WireReader(bytes)
            return fromReader(reader)
        }
    }
}


/**
 * A resolved playback url, or why there is none.
 */
data class StreamResolution(
    val songMid: String,
    /**
     * One of the six ladder labels — `flac` / `ogg320` / `320` / `ogg192` /
     * `128` / `aac` — when `playable`.
     */
    val quality: String?,
    val filename: String?,
    val url: String?,
    val playable: Boolean,
    /**
     * Present when nothing was granted: what each quality answered.
     */
    val reason: String?
) {
    internal fun wireSize(): Int {
        return 4 + Utf8Codec.maxBytes(this.songMid) + 1 + (this.quality?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.filename?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.url?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + 1 + (this.reason?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0)
    }

    internal fun writeTo(writer: WireWriter) {
        writer.writeString(this.songMid)
        writer.writeOptionalValue(this.quality, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.filename, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.url, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeBool(this.playable)
        writer.writeOptionalValue(this.reason, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
    }

    internal fun toByteArray(): ByteArray {
        val buffer = WireWriterPool.acquire(wireSize())
        val writer = buffer.writer
        try {
            writeTo(writer)
            return buffer.bytes()
        } finally {
            buffer.close()
        }
    }

    companion object {
        internal fun fromReader(reader: WireReader): StreamResolution {
            return StreamResolution(
                reader.readString(),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readBool(),
                reader.readOptionalValue({ reader -> reader.readString() })
            )
        }

        internal fun fromByteArray(bytes: ByteArray): StreamResolution {
            val reader = WireReader(bytes)
            return fromReader(reader)
        }
    }
}


/**
 * A login QR code: what to draw, and what to poll it with.
 */
data class LoginQrCode(
    /**
     * The `qrsig`: hand it back to `poll_login`.
     */
    val identifier: String,
    /**
     * `"qq"` for the scan-with-QQ flow.
     */
    val loginType: String,
    val mimetype: String,
    /**
     * A PNG, base64-encoded — the shape a host can draw directly.
     */
    val imageBase64: String
) {
    internal fun wireSize(): Int {
        return 4 + Utf8Codec.maxBytes(this.identifier) + 4 + Utf8Codec.maxBytes(this.loginType) + 4 + Utf8Codec.maxBytes(this.mimetype) + 4 + Utf8Codec.maxBytes(this.imageBase64)
    }

    internal fun writeTo(writer: WireWriter) {
        writer.writeString(this.identifier)
        writer.writeString(this.loginType)
        writer.writeString(this.mimetype)
        writer.writeString(this.imageBase64)
    }

    internal fun toByteArray(): ByteArray {
        val buffer = WireWriterPool.acquire(wireSize())
        val writer = buffer.writer
        try {
            writeTo(writer)
            return buffer.bytes()
        } finally {
            buffer.close()
        }
    }

    companion object {
        internal fun fromReader(reader: WireReader): LoginQrCode {
            return LoginQrCode(
                reader.readString(),
                reader.readString(),
                reader.readString(),
                reader.readString()
            )
        }

        internal fun fromByteArray(bytes: ByteArray): LoginQrCode {
            val reader = WireReader(bytes)
            return fromReader(reader)
        }
    }
}


/**
 * One poll of a login QR code.
 */
data class LoginPoll(
    /**
     * `SCAN` (not scanned yet), `CONF` (scanned, awaiting confirmation),
     * `DONE`, `TIMEOUT` or `REFUSE`.
     */
    val event: String,
    val loggedIn: Boolean,
    /**
     * Present once `logged_in`: who just logged in.
     */
    val login: LoginStatus?
) {
    internal fun wireSize(): Int {
        return 4 + Utf8Codec.maxBytes(this.event) + 1 + 1 + (this.login?.let { __boltffi_value_0 -> __boltffi_value_0.wireSize() } ?: 0)
    }

    internal fun writeTo(writer: WireWriter) {
        writer.writeString(this.event)
        writer.writeBool(this.loggedIn)
        writer.writeOptionalValue(this.login, { writer, __boltffi_value_0 -> __boltffi_value_0.writeTo(writer) })
    }

    internal fun toByteArray(): ByteArray {
        val buffer = WireWriterPool.acquire(wireSize())
        val writer = buffer.writer
        try {
            writeTo(writer)
            return buffer.bytes()
        } finally {
            buffer.close()
        }
    }

    companion object {
        internal fun fromReader(reader: WireReader): LoginPoll {
            return LoginPoll(
                reader.readString(),
                reader.readBool(),
                reader.readOptionalValue({ reader -> LoginStatus.fromReader(reader) })
            )
        }

        internal fun fromByteArray(bytes: ByteArray): LoginPoll {
            val reader = WireReader(bytes)
            return fromReader(reader)
        }
    }
}


/**
 * A search page: the rows plus the catalogue's own total for the query.
 */
data class TrackSearch(
    val total: Long,
    val tracks: List<Track>
) {
    internal fun wireSize(): Int {
        return 8 + 4 + this.tracks.sumOf { __boltffi_value_0 -> val __boltffi_size: kotlin.Int = __boltffi_value_0.wireSize(); __boltffi_size }
    }

    internal fun writeTo(writer: WireWriter) {
        writer.writeI64(this.total)
        writer.writeSequence(this.tracks, this.tracks.size, { writer, __boltffi_value_0 -> __boltffi_value_0.writeTo(writer) })
    }

    internal fun toByteArray(): ByteArray {
        val buffer = WireWriterPool.acquire(wireSize())
        val writer = buffer.writer
        try {
            writeTo(writer)
            return buffer.bytes()
        } finally {
            buffer.close()
        }
    }

    companion object {
        internal fun fromReader(reader: WireReader): TrackSearch {
            return TrackSearch(
                reader.readI64(),
                reader.readSequence({ reader -> Track.fromReader(reader) })
            )
        }

        internal fun fromByteArray(bytes: ByteArray): TrackSearch {
            val reader = WireReader(bytes)
            return fromReader(reader)
        }
    }
}


data class ArtistSearch(
    val total: Long,
    val artists: List<Artist>
) {
    internal fun wireSize(): Int {
        return 8 + 4 + this.artists.sumOf { __boltffi_value_0 -> val __boltffi_size: kotlin.Int = __boltffi_value_0.wireSize(); __boltffi_size }
    }

    internal fun writeTo(writer: WireWriter) {
        writer.writeI64(this.total)
        writer.writeSequence(this.artists, this.artists.size, { writer, __boltffi_value_0 -> __boltffi_value_0.writeTo(writer) })
    }

    internal fun toByteArray(): ByteArray {
        val buffer = WireWriterPool.acquire(wireSize())
        val writer = buffer.writer
        try {
            writeTo(writer)
            return buffer.bytes()
        } finally {
            buffer.close()
        }
    }

    companion object {
        internal fun fromReader(reader: WireReader): ArtistSearch {
            return ArtistSearch(
                reader.readI64(),
                reader.readSequence({ reader -> Artist.fromReader(reader) })
            )
        }

        internal fun fromByteArray(bytes: ByteArray): ArtistSearch {
            val reader = WireReader(bytes)
            return fromReader(reader)
        }
    }
}


data class AlbumSearch(
    val total: Long,
    val albums: List<Album>
) {
    internal fun wireSize(): Int {
        return 8 + 4 + this.albums.sumOf { __boltffi_value_0 -> val __boltffi_size: kotlin.Int = __boltffi_value_0.wireSize(); __boltffi_size }
    }

    internal fun writeTo(writer: WireWriter) {
        writer.writeI64(this.total)
        writer.writeSequence(this.albums, this.albums.size, { writer, __boltffi_value_0 -> __boltffi_value_0.writeTo(writer) })
    }

    internal fun toByteArray(): ByteArray {
        val buffer = WireWriterPool.acquire(wireSize())
        val writer = buffer.writer
        try {
            writeTo(writer)
            return buffer.bytes()
        } finally {
            buffer.close()
        }
    }

    companion object {
        internal fun fromReader(reader: WireReader): AlbumSearch {
            return AlbumSearch(
                reader.readI64(),
                reader.readSequence({ reader -> Album.fromReader(reader) })
            )
        }

        internal fun fromByteArray(bytes: ByteArray): AlbumSearch {
            val reader = WireReader(bytes)
            return fromReader(reader)
        }
    }
}


data class PlaylistSearch(
    val total: Long,
    val playlists: List<Playlist>
) {
    internal fun wireSize(): Int {
        return 8 + 4 + this.playlists.sumOf { __boltffi_value_0 -> val __boltffi_size: kotlin.Int = __boltffi_value_0.wireSize(); __boltffi_size }
    }

    internal fun writeTo(writer: WireWriter) {
        writer.writeI64(this.total)
        writer.writeSequence(this.playlists, this.playlists.size, { writer, __boltffi_value_0 -> __boltffi_value_0.writeTo(writer) })
    }

    internal fun toByteArray(): ByteArray {
        val buffer = WireWriterPool.acquire(wireSize())
        val writer = buffer.writer
        try {
            writeTo(writer)
            return buffer.bytes()
        } finally {
            buffer.close()
        }
    }

    companion object {
        internal fun fromReader(reader: WireReader): PlaylistSearch {
            return PlaylistSearch(
                reader.readI64(),
                reader.readSequence({ reader -> Playlist.fromReader(reader) })
            )
        }

        internal fun fromByteArray(bytes: ByteArray): PlaylistSearch {
            val reader = WireReader(bytes)
            return fromReader(reader)
        }
    }
}


/**
 * One candidate the catalogue offers for a local item's cover.
 *
 * The same shape serves a track, an artist and an album — the fields that do
 * not apply to a kind are simply absent, which is also how the app reads them.
 */
data class ArtworkCandidate(
    val title: String?,
    val artist: String?,
    val album: String?,
    val artistName: String?,
    val singerMid: String?,
    val songMid: String?,
    val albumMid: String?,
    val imageUrl: String?,
    val duration: Long?,
    val releaseDate: String?,
    /**
     * A rank hint, not a verdict: the host scores the candidates itself.
     */
    val confidence: Double?
) {
    internal fun wireSize(): Int {
        return 1 + (this.title?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.artist?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.album?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.artistName?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.singerMid?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.songMid?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.albumMid?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.imageUrl?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.duration?.let { __boltffi_value_0 -> 8 } ?: 0) + 1 + (this.releaseDate?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.confidence?.let { __boltffi_value_0 -> 8 } ?: 0)
    }

    internal fun writeTo(writer: WireWriter) {
        writer.writeOptionalValue(this.title, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.artist, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.album, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.artistName, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.singerMid, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.songMid, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.albumMid, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.imageUrl, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.duration, { writer, __boltffi_value_0 -> writer.writeI64(__boltffi_value_0) })
        writer.writeOptionalValue(this.releaseDate, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.confidence, { writer, __boltffi_value_0 -> writer.writeF64(__boltffi_value_0) })
    }

    internal fun toByteArray(): ByteArray {
        val buffer = WireWriterPool.acquire(wireSize())
        val writer = buffer.writer
        try {
            writeTo(writer)
            return buffer.bytes()
        } finally {
            buffer.close()
        }
    }

    companion object {
        internal fun fromReader(reader: WireReader): ArtworkCandidate {
            return ArtworkCandidate(
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readI64() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readF64() })
            )
        }

        internal fun fromByteArray(bytes: ByteArray): ArtworkCandidate {
            val reader = WireReader(bytes)
            return fromReader(reader)
        }
    }
}


/**
 * An artist's biography, as the local library's enrichment reads it.
 */
data class ArtistBiography(
    val artistName: String?,
    val singerMid: String?,
    val description: String?,
    val imageUrl: String?,
    val region: String?,
    val foreignName: String?,
    val confidence: Double?
) {
    internal fun wireSize(): Int {
        return 1 + (this.artistName?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.singerMid?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.description?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.imageUrl?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.region?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.foreignName?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0) + 1 + (this.confidence?.let { __boltffi_value_0 -> 8 } ?: 0)
    }

    internal fun writeTo(writer: WireWriter) {
        writer.writeOptionalValue(this.artistName, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.singerMid, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.description, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.imageUrl, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.region, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.foreignName, { writer, __boltffi_value_0 -> writer.writeString(__boltffi_value_0) })
        writer.writeOptionalValue(this.confidence, { writer, __boltffi_value_0 -> writer.writeF64(__boltffi_value_0) })
    }

    internal fun toByteArray(): ByteArray {
        val buffer = WireWriterPool.acquire(wireSize())
        val writer = buffer.writer
        try {
            writeTo(writer)
            return buffer.bytes()
        } finally {
            buffer.close()
        }
    }

    companion object {
        internal fun fromReader(reader: WireReader): ArtistBiography {
            return ArtistBiography(
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readString() }),
                reader.readOptionalValue({ reader -> reader.readF64() })
            )
        }

        internal fun fromByteArray(bytes: ByteArray): ArtistBiography {
            val reader = WireReader(bytes)
            return fromReader(reader)
        }
    }
}


/**
 * Failure of any call. One type for the whole surface: every call is "ask the
 * upstream and parse it", so the caller's options are always the same — report
 * it, or retry later.
 */
sealed class HelperError : Exception() {
    internal abstract fun wireSize(): Int

    internal abstract fun writeTo(writer: WireWriter)

    internal fun toByteArray(): ByteArray {
        val buffer = WireWriterPool.acquire(wireSize())
        val writer = buffer.writer
        try {
            writeTo(writer)
            return buffer.bytes()
        } finally {
            buffer.close()
        }
    }


    /**
     * No credential, or the upstream rejected it.
     */
    object NotLoggedIn : HelperError() {
        internal override fun wireSize(): Int {
            return 4
        }

        internal override fun writeTo(writer: WireWriter) {
            writer.writeU32(0.toUInt())
        }
    }
    /**
     * The component refused the call to protect the upstream (see the guard).
     */
    data class Throttled(
        val field0: String
    ) : HelperError() {
        internal override fun wireSize(): Int {
            return 4 + 4 + Utf8Codec.maxBytes(this.field0)
        }

        internal override fun writeTo(writer: WireWriter) {
            writer.writeU32(1.toUInt())
            writer.writeString(this.field0)
        }
    }
    /**
     * The network failed, or the response could not be parsed.
     */
    data class Upstream(
        val field0: String
    ) : HelperError() {
        internal override fun wireSize(): Int {
            return 4 + 4 + Utf8Codec.maxBytes(this.field0)
        }

        internal override fun writeTo(writer: WireWriter) {
            writer.writeU32(2.toUInt())
            writer.writeString(this.field0)
        }
    }
    /**
     * The caller asked for something this component does not serve.
     */
    data class Unsupported(
        val field0: String
    ) : HelperError() {
        internal override fun wireSize(): Int {
            return 4 + 4 + Utf8Codec.maxBytes(this.field0)
        }

        internal override fun writeTo(writer: WireWriter) {
            writer.writeU32(3.toUInt())
            writer.writeString(this.field0)
        }
    }
    /**
     * The host passed something unusable (a bad song mid, a missing directory).
     */
    data class InvalidRequest(
        val field0: String
    ) : HelperError() {
        internal override fun wireSize(): Int {
            return 4 + 4 + Utf8Codec.maxBytes(this.field0)
        }

        internal override fun writeTo(writer: WireWriter) {
            writer.writeU32(4.toUInt())
            writer.writeString(this.field0)
        }
    }

    companion object {
        internal fun fromReader(reader: WireReader): HelperError {
            val tag = reader.readU32()
            return when (tag) {
                0.toUInt() -> NotLoggedIn
                1.toUInt() -> Throttled(reader.readString())
                2.toUInt() -> Upstream(reader.readString())
                3.toUInt() -> Unsupported(reader.readString())
                4.toUInt() -> InvalidRequest(reader.readString())
                else -> throw IllegalArgumentException("unknown HelperError tag: $tag")
            }
        }

        internal fun fromByteArray(bytes: ByteArray): HelperError {
            val reader = WireReader(bytes)
            return fromReader(reader)
        }
    }
}

/**
 * 创建歌单。重名不会失败——上游自动加时间戳。
 */
fun createPlaylist(dirname: String): CreateDeleteSonglistResp {
    val __boltffi_dirname_wire = WireWriterPool.acquire(4 + Utf8Codec.maxBytes(dirname))
    val __boltffi_dirname_writer = __boltffi_dirname_wire.writer
    __boltffi_dirname_writer.writeString(dirname)
    try {
        val __boltffi_result = try { Native.boltffi_function_qqmusic_api_helper_next_port_collection_write_create_playlist(__boltffi_dirname_wire.directBuffer(), __boltffi_dirname_wire.size()) } catch (__boltffi_error: BoltFfiErrorBufferException) { run { val __boltffi_error_reader = WireReader(__boltffi_error.bytes); throw HelperError.fromReader(__boltffi_error_reader) } } ?: throw IllegalStateException("null buffer returned")
        val __boltffi_reader = WireReader(__boltffi_result)
        return CreateDeleteSonglistResp.fromReader(__boltffi_reader)
    } finally {
        __boltffi_dirname_wire.close()
    }
}

/**
 * 删除歌单；删除不存在的歌单时回值的 `dirid` 是 0。
 */
fun deletePlaylist(dirid: Long): CreateDeleteSonglistResp {
    val __boltffi_result = try { Native.boltffi_function_qqmusic_api_helper_next_port_collection_write_delete_playlist(dirid) } catch (__boltffi_error: BoltFfiErrorBufferException) { run { val __boltffi_error_reader = WireReader(__boltffi_error.bytes); throw HelperError.fromReader(__boltffi_error_reader) } } ?: throw IllegalStateException("null buffer returned")
    val __boltffi_reader = WireReader(__boltffi_result)
    return CreateDeleteSonglistResp.fromReader(__boltffi_reader)
}

/**
 * 添加歌曲到歌单；`tid` 缺省 0。
 *
 * 返回是否操作成功：歌曲已经在歌单里（80092）也算成功。
 */
fun addPlaylistSongs(dirid: Long, songInfo: List<SongInfoPair>, tid: Long?): Boolean {
    val __boltffi_tid_wire = WireWriterPool.acquire(if (tid == null) 1 else 9)
    val __boltffi_tid_writer = __boltffi_tid_wire.writer
    __boltffi_tid_writer.writeOptionalI64(tid)
    try {
        return try { Native.boltffi_function_qqmusic_api_helper_next_port_collection_write_add_playlist_songs(dirid, DirectVectorCodec.writeRecordList(songInfo, 16, { item, buffer, offset -> item.writeTo(buffer, offset) }), __boltffi_tid_wire.directBuffer(), __boltffi_tid_wire.size()) } catch (__boltffi_error: BoltFfiErrorBufferException) { run { val __boltffi_error_reader = WireReader(__boltffi_error.bytes); throw HelperError.fromReader(__boltffi_error_reader) } }
    } finally {
        __boltffi_tid_wire.close()
    }
}

/**
 * 删除歌单中的歌曲；`tid` 缺省 0。
 *
 * 返回是否操作成功：歌曲不在歌单里（80092）也算成功。
 */
fun removePlaylistSongs(dirid: Long, songInfo: List<SongInfoPair>, tid: Long?): Boolean {
    val __boltffi_tid_wire = WireWriterPool.acquire(if (tid == null) 1 else 9)
    val __boltffi_tid_writer = __boltffi_tid_wire.writer
    __boltffi_tid_writer.writeOptionalI64(tid)
    try {
        return try { Native.boltffi_function_qqmusic_api_helper_next_port_collection_write_remove_playlist_songs(dirid, DirectVectorCodec.writeRecordList(songInfo, 16, { item, buffer, offset -> item.writeTo(buffer, offset) }), __boltffi_tid_wire.directBuffer(), __boltffi_tid_wire.size()) } catch (__boltffi_error: BoltFfiErrorBufferException) { run { val __boltffi_error_reader = WireReader(__boltffi_error.bytes); throw HelperError.fromReader(__boltffi_error_reader) } }
    } finally {
        __boltffi_tid_wire.close()
    }
}

/**
 * 收藏专辑到当前登录用户；`album_ids` 是要收藏的专辑 ID 列表。
 *
 * 回值的 `success` 已经算好：`result == 0` 且没有失败项。
 */
fun favAlbum(albumIds: LongArray): AlbumFavWriteResponse {
    val __boltffi_result = try { Native.boltffi_function_qqmusic_api_helper_next_port_collection_write_fav_album(albumIds) } catch (__boltffi_error: BoltFfiErrorBufferException) { run { val __boltffi_error_reader = WireReader(__boltffi_error.bytes); throw HelperError.fromReader(__boltffi_error_reader) } } ?: throw IllegalStateException("null buffer returned")
    val __boltffi_reader = WireReader(__boltffi_result)
    return AlbumFavWriteResponse.fromReader(__boltffi_reader)
}

/**
 * 取消收藏专辑；`album_ids` 是要取消的专辑 ID 列表。
 */
fun unfavAlbum(albumIds: LongArray): AlbumFavWriteResponse {
    val __boltffi_result = try { Native.boltffi_function_qqmusic_api_helper_next_port_collection_write_unfav_album(albumIds) } catch (__boltffi_error: BoltFfiErrorBufferException) { run { val __boltffi_error_reader = WireReader(__boltffi_error.bytes); throw HelperError.fromReader(__boltffi_error_reader) } } ?: throw IllegalStateException("null buffer returned")
    val __boltffi_reader = WireReader(__boltffi_result)
    return AlbumFavWriteResponse.fromReader(__boltffi_reader)
}

/**
 * 收藏公开歌单（playlist_id 是 disstid/pid）。已收藏时也返回成功。
 */
fun favPlaylist(playlistId: Long): Boolean {
    return try { Native.boltffi_function_qqmusic_api_helper_next_port_collection_write_fav_playlist(playlistId) } catch (__boltffi_error: BoltFfiErrorBufferException) { run { val __boltffi_error_reader = WireReader(__boltffi_error.bytes); throw HelperError.fromReader(__boltffi_error_reader) } }
}

/**
 * 取消收藏公开歌单（playlist_id 是 disstid/pid）。未收藏时也返回成功。
 */
fun unfavPlaylist(playlistId: Long): Boolean {
    return try { Native.boltffi_function_qqmusic_api_helper_next_port_collection_write_unfav_playlist(playlistId) } catch (__boltffi_error: BoltFfiErrorBufferException) { run { val __boltffi_error_reader = WireReader(__boltffi_error.bytes); throw HelperError.fromReader(__boltffi_error_reader) } }
}

/**
 * 评论数量。`biz_type`: 1=歌曲 2=专辑 3=歌单 4=MV 15=长音频。
 */
fun fetchCommentCount(bizId: Long, bizType: Long?, bizSubType: Long?): CommentCount {
    val __boltffi_bizType_wire = WireWriterPool.acquire(if (bizType == null) 1 else 9)
    val __boltffi_bizType_writer = __boltffi_bizType_wire.writer
    __boltffi_bizType_writer.writeOptionalI64(bizType)
    val __boltffi_bizSubType_wire = WireWriterPool.acquire(if (bizSubType == null) 1 else 9)
    val __boltffi_bizSubType_writer = __boltffi_bizSubType_wire.writer
    __boltffi_bizSubType_writer.writeOptionalI64(bizSubType)
    try {
        val __boltffi_result = try { Native.boltffi_function_qqmusic_api_helper_next_port_comment_fetch_comment_count(bizId, __boltffi_bizType_wire.directBuffer(), __boltffi_bizType_wire.size(), __boltffi_bizSubType_wire.directBuffer(), __boltffi_bizSubType_wire.size()) } catch (__boltffi_error: BoltFfiErrorBufferException) { run { val __boltffi_error_reader = WireReader(__boltffi_error.bytes); throw HelperError.fromReader(__boltffi_error_reader) } } ?: throw IllegalStateException("null buffer returned")
        val __boltffi_reader = WireReader(__boltffi_result)
        return CommentCount.fromReader(__boltffi_reader)
    } finally {
        __boltffi_bizType_wire.close()
        __boltffi_bizSubType_wire.close()
    }
}

/**
 * 热评一页；`hasMore` 为 1 时把这一页最后一条的 `seqNo` 回填给下一页。
 */
fun fetchHotComments(bizId: Long, page: Long?, pageSize: Long?, lastCommentSeqNo: String?, bizType: Long?, bizSubType: Long?): CommentList {
    val __boltffi_page_wire = WireWriterPool.acquire(if (page == null) 1 else 9)
    val __boltffi_page_writer = __boltffi_page_wire.writer
    __boltffi_page_writer.writeOptionalI64(page)
    val __boltffi_pageSize_wire = WireWriterPool.acquire(if (pageSize == null) 1 else 9)
    val __boltffi_pageSize_writer = __boltffi_pageSize_wire.writer
    __boltffi_pageSize_writer.writeOptionalI64(pageSize)
    val __boltffi_lastCommentSeqNo_wire = WireWriterPool.acquire(1 + (lastCommentSeqNo?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0))
    val __boltffi_lastCommentSeqNo_writer = __boltffi_lastCommentSeqNo_wire.writer
    __boltffi_lastCommentSeqNo_writer.writeOptionalValue(lastCommentSeqNo, { __boltffi_lastCommentSeqNo_writer, __boltffi_value_0 -> __boltffi_lastCommentSeqNo_writer.writeString(__boltffi_value_0) })
    val __boltffi_bizType_wire = WireWriterPool.acquire(if (bizType == null) 1 else 9)
    val __boltffi_bizType_writer = __boltffi_bizType_wire.writer
    __boltffi_bizType_writer.writeOptionalI64(bizType)
    val __boltffi_bizSubType_wire = WireWriterPool.acquire(if (bizSubType == null) 1 else 9)
    val __boltffi_bizSubType_writer = __boltffi_bizSubType_wire.writer
    __boltffi_bizSubType_writer.writeOptionalI64(bizSubType)
    try {
        val __boltffi_result = try { Native.boltffi_function_qqmusic_api_helper_next_port_comment_fetch_hot_comments(bizId, __boltffi_page_wire.directBuffer(), __boltffi_page_wire.size(), __boltffi_pageSize_wire.directBuffer(), __boltffi_pageSize_wire.size(), __boltffi_lastCommentSeqNo_wire.directBuffer(), __boltffi_lastCommentSeqNo_wire.size(), __boltffi_bizType_wire.directBuffer(), __boltffi_bizType_wire.size(), __boltffi_bizSubType_wire.directBuffer(), __boltffi_bizSubType_wire.size()) } catch (__boltffi_error: BoltFfiErrorBufferException) { run { val __boltffi_error_reader = WireReader(__boltffi_error.bytes); throw HelperError.fromReader(__boltffi_error_reader) } } ?: throw IllegalStateException("null buffer returned")
        val __boltffi_reader = WireReader(__boltffi_result)
        return CommentList.fromReader(__boltffi_reader)
    } finally {
        __boltffi_page_wire.close()
        __boltffi_pageSize_wire.close()
        __boltffi_lastCommentSeqNo_wire.close()
        __boltffi_bizType_wire.close()
        __boltffi_bizSubType_wire.close()
    }
}

/**
 * 最新评论一页。
 */
fun fetchNewComments(bizId: Long, page: Long?, pageSize: Long?, lastCommentSeqNo: String?, bizType: Long?, bizSubType: Long?): CommentList {
    val __boltffi_page_wire = WireWriterPool.acquire(if (page == null) 1 else 9)
    val __boltffi_page_writer = __boltffi_page_wire.writer
    __boltffi_page_writer.writeOptionalI64(page)
    val __boltffi_pageSize_wire = WireWriterPool.acquire(if (pageSize == null) 1 else 9)
    val __boltffi_pageSize_writer = __boltffi_pageSize_wire.writer
    __boltffi_pageSize_writer.writeOptionalI64(pageSize)
    val __boltffi_lastCommentSeqNo_wire = WireWriterPool.acquire(1 + (lastCommentSeqNo?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0))
    val __boltffi_lastCommentSeqNo_writer = __boltffi_lastCommentSeqNo_wire.writer
    __boltffi_lastCommentSeqNo_writer.writeOptionalValue(lastCommentSeqNo, { __boltffi_lastCommentSeqNo_writer, __boltffi_value_0 -> __boltffi_lastCommentSeqNo_writer.writeString(__boltffi_value_0) })
    val __boltffi_bizType_wire = WireWriterPool.acquire(if (bizType == null) 1 else 9)
    val __boltffi_bizType_writer = __boltffi_bizType_wire.writer
    __boltffi_bizType_writer.writeOptionalI64(bizType)
    val __boltffi_bizSubType_wire = WireWriterPool.acquire(if (bizSubType == null) 1 else 9)
    val __boltffi_bizSubType_writer = __boltffi_bizSubType_wire.writer
    __boltffi_bizSubType_writer.writeOptionalI64(bizSubType)
    try {
        val __boltffi_result = try { Native.boltffi_function_qqmusic_api_helper_next_port_comment_fetch_new_comments(bizId, __boltffi_page_wire.directBuffer(), __boltffi_page_wire.size(), __boltffi_pageSize_wire.directBuffer(), __boltffi_pageSize_wire.size(), __boltffi_lastCommentSeqNo_wire.directBuffer(), __boltffi_lastCommentSeqNo_wire.size(), __boltffi_bizType_wire.directBuffer(), __boltffi_bizType_wire.size(), __boltffi_bizSubType_wire.directBuffer(), __boltffi_bizSubType_wire.size()) } catch (__boltffi_error: BoltFfiErrorBufferException) { run { val __boltffi_error_reader = WireReader(__boltffi_error.bytes); throw HelperError.fromReader(__boltffi_error_reader) } } ?: throw IllegalStateException("null buffer returned")
        val __boltffi_reader = WireReader(__boltffi_result)
        return CommentList.fromReader(__boltffi_reader)
    } finally {
        __boltffi_page_wire.close()
        __boltffi_pageSize_wire.close()
        __boltffi_lastCommentSeqNo_wire.close()
        __boltffi_bizType_wire.close()
        __boltffi_bizSubType_wire.close()
    }
}

/**
 * 推荐评论一页。
 */
fun fetchRecommendComments(bizId: Long, page: Long?, pageSize: Long?, lastCommentSeqNo: String?, bizType: Long?, bizSubType: Long?): CommentList {
    val __boltffi_page_wire = WireWriterPool.acquire(if (page == null) 1 else 9)
    val __boltffi_page_writer = __boltffi_page_wire.writer
    __boltffi_page_writer.writeOptionalI64(page)
    val __boltffi_pageSize_wire = WireWriterPool.acquire(if (pageSize == null) 1 else 9)
    val __boltffi_pageSize_writer = __boltffi_pageSize_wire.writer
    __boltffi_pageSize_writer.writeOptionalI64(pageSize)
    val __boltffi_lastCommentSeqNo_wire = WireWriterPool.acquire(1 + (lastCommentSeqNo?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0))
    val __boltffi_lastCommentSeqNo_writer = __boltffi_lastCommentSeqNo_wire.writer
    __boltffi_lastCommentSeqNo_writer.writeOptionalValue(lastCommentSeqNo, { __boltffi_lastCommentSeqNo_writer, __boltffi_value_0 -> __boltffi_lastCommentSeqNo_writer.writeString(__boltffi_value_0) })
    val __boltffi_bizType_wire = WireWriterPool.acquire(if (bizType == null) 1 else 9)
    val __boltffi_bizType_writer = __boltffi_bizType_wire.writer
    __boltffi_bizType_writer.writeOptionalI64(bizType)
    val __boltffi_bizSubType_wire = WireWriterPool.acquire(if (bizSubType == null) 1 else 9)
    val __boltffi_bizSubType_writer = __boltffi_bizSubType_wire.writer
    __boltffi_bizSubType_writer.writeOptionalI64(bizSubType)
    try {
        val __boltffi_result = try { Native.boltffi_function_qqmusic_api_helper_next_port_comment_fetch_recommend_comments(bizId, __boltffi_page_wire.directBuffer(), __boltffi_page_wire.size(), __boltffi_pageSize_wire.directBuffer(), __boltffi_pageSize_wire.size(), __boltffi_lastCommentSeqNo_wire.directBuffer(), __boltffi_lastCommentSeqNo_wire.size(), __boltffi_bizType_wire.directBuffer(), __boltffi_bizType_wire.size(), __boltffi_bizSubType_wire.directBuffer(), __boltffi_bizSubType_wire.size()) } catch (__boltffi_error: BoltFfiErrorBufferException) { run { val __boltffi_error_reader = WireReader(__boltffi_error.bytes); throw HelperError.fromReader(__boltffi_error_reader) } } ?: throw IllegalStateException("null buffer returned")
        val __boltffi_reader = WireReader(__boltffi_result)
        return CommentList.fromReader(__boltffi_reader)
    } finally {
        __boltffi_page_wire.close()
        __boltffi_pageSize_wire.close()
        __boltffi_lastCommentSeqNo_wire.close()
        __boltffi_bizType_wire.close()
        __boltffi_bizSubType_wire.close()
    }
}

/**
 * 时刻评论一页；游标回填 `last_comment_seq_no`（取自 `nextPos`）。
 */
fun fetchMomentComments(bizId: Long, pageSize: Long?, lastCommentSeqNo: String?, bizType: Long?, bizSubType: Long?): MomentCommentList {
    val __boltffi_pageSize_wire = WireWriterPool.acquire(if (pageSize == null) 1 else 9)
    val __boltffi_pageSize_writer = __boltffi_pageSize_wire.writer
    __boltffi_pageSize_writer.writeOptionalI64(pageSize)
    val __boltffi_lastCommentSeqNo_wire = WireWriterPool.acquire(1 + (lastCommentSeqNo?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0))
    val __boltffi_lastCommentSeqNo_writer = __boltffi_lastCommentSeqNo_wire.writer
    __boltffi_lastCommentSeqNo_writer.writeOptionalValue(lastCommentSeqNo, { __boltffi_lastCommentSeqNo_writer, __boltffi_value_0 -> __boltffi_lastCommentSeqNo_writer.writeString(__boltffi_value_0) })
    val __boltffi_bizType_wire = WireWriterPool.acquire(if (bizType == null) 1 else 9)
    val __boltffi_bizType_writer = __boltffi_bizType_wire.writer
    __boltffi_bizType_writer.writeOptionalI64(bizType)
    val __boltffi_bizSubType_wire = WireWriterPool.acquire(if (bizSubType == null) 1 else 9)
    val __boltffi_bizSubType_writer = __boltffi_bizSubType_wire.writer
    __boltffi_bizSubType_writer.writeOptionalI64(bizSubType)
    try {
        val __boltffi_result = try { Native.boltffi_function_qqmusic_api_helper_next_port_comment_fetch_moment_comments(bizId, __boltffi_pageSize_wire.directBuffer(), __boltffi_pageSize_wire.size(), __boltffi_lastCommentSeqNo_wire.directBuffer(), __boltffi_lastCommentSeqNo_wire.size(), __boltffi_bizType_wire.directBuffer(), __boltffi_bizType_wire.size(), __boltffi_bizSubType_wire.directBuffer(), __boltffi_bizSubType_wire.size()) } catch (__boltffi_error: BoltFfiErrorBufferException) { run { val __boltffi_error_reader = WireReader(__boltffi_error.bytes); throw HelperError.fromReader(__boltffi_error_reader) } } ?: throw IllegalStateException("null buffer returned")
        val __boltffi_reader = WireReader(__boltffi_result)
        return MomentCommentList.fromReader(__boltffi_reader)
    } finally {
        __boltffi_pageSize_wire.close()
        __boltffi_lastCommentSeqNo_wire.close()
        __boltffi_bizType_wire.close()
        __boltffi_bizSubType_wire.close()
    }
}

/**
 * 发表评论；`reply_cmt_id` 给了就是回复那条评论。
 */
fun addComment(bizId: Long, content: String, replyCmtId: String?, bizType: Long?, bizSubType: Long?): AddedComment {
    val __boltffi_content_wire = WireWriterPool.acquire(4 + Utf8Codec.maxBytes(content))
    val __boltffi_content_writer = __boltffi_content_wire.writer
    __boltffi_content_writer.writeString(content)
    val __boltffi_replyCmtId_wire = WireWriterPool.acquire(1 + (replyCmtId?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0))
    val __boltffi_replyCmtId_writer = __boltffi_replyCmtId_wire.writer
    __boltffi_replyCmtId_writer.writeOptionalValue(replyCmtId, { __boltffi_replyCmtId_writer, __boltffi_value_0 -> __boltffi_replyCmtId_writer.writeString(__boltffi_value_0) })
    val __boltffi_bizType_wire = WireWriterPool.acquire(if (bizType == null) 1 else 9)
    val __boltffi_bizType_writer = __boltffi_bizType_wire.writer
    __boltffi_bizType_writer.writeOptionalI64(bizType)
    val __boltffi_bizSubType_wire = WireWriterPool.acquire(if (bizSubType == null) 1 else 9)
    val __boltffi_bizSubType_writer = __boltffi_bizSubType_wire.writer
    __boltffi_bizSubType_writer.writeOptionalI64(bizSubType)
    try {
        val __boltffi_result = try { Native.boltffi_function_qqmusic_api_helper_next_port_comment_add_comment(bizId, __boltffi_content_wire.directBuffer(), __boltffi_content_wire.size(), __boltffi_replyCmtId_wire.directBuffer(), __boltffi_replyCmtId_wire.size(), __boltffi_bizType_wire.directBuffer(), __boltffi_bizType_wire.size(), __boltffi_bizSubType_wire.directBuffer(), __boltffi_bizSubType_wire.size()) } catch (__boltffi_error: BoltFfiErrorBufferException) { run { val __boltffi_error_reader = WireReader(__boltffi_error.bytes); throw HelperError.fromReader(__boltffi_error_reader) } } ?: throw IllegalStateException("null buffer returned")
        val __boltffi_reader = WireReader(__boltffi_result)
        return AddedComment.fromReader(__boltffi_reader)
    } finally {
        __boltffi_content_wire.close()
        __boltffi_replyCmtId_wire.close()
        __boltffi_bizType_wire.close()
        __boltffi_bizSubType_wire.close()
    }
}

/**
 * 删除评论。返回是否删除成功：评论不存在也是 `true`（参考的行为）。
 */
fun deleteComment(cmId: String): Boolean {
    val __boltffi_cmId_wire = WireWriterPool.acquire(4 + Utf8Codec.maxBytes(cmId))
    val __boltffi_cmId_writer = __boltffi_cmId_wire.writer
    __boltffi_cmId_writer.writeString(cmId)
    try {
        return try { Native.boltffi_function_qqmusic_api_helper_next_port_comment_delete_comment(__boltffi_cmId_wire.directBuffer(), __boltffi_cmId_wire.size()) } catch (__boltffi_error: BoltFfiErrorBufferException) { run { val __boltffi_error_reader = WireReader(__boltffi_error.bytes); throw HelperError.fromReader(__boltffi_error_reader) } }
    } finally {
        __boltffi_cmId_wire.close()
    }
}

/**
 * 新碟上架一页, 默认地区 1, 20 张, 第 1 页.
 */
fun fetchNewAlbums(area: Long?, num: Long?, page: Long?): NewAlbumResponse {
    val __boltffi_area_wire = WireWriterPool.acquire(if (area == null) 1 else 9)
    val __boltffi_area_writer = __boltffi_area_wire.writer
    __boltffi_area_writer.writeOptionalI64(area)
    val __boltffi_num_wire = WireWriterPool.acquire(if (num == null) 1 else 9)
    val __boltffi_num_writer = __boltffi_num_wire.writer
    __boltffi_num_writer.writeOptionalI64(num)
    val __boltffi_page_wire = WireWriterPool.acquire(if (page == null) 1 else 9)
    val __boltffi_page_writer = __boltffi_page_wire.writer
    __boltffi_page_writer.writeOptionalI64(page)
    try {
        val __boltffi_result = try { Native.boltffi_function_qqmusic_api_helper_next_port_library_extra_fetch_new_albums(__boltffi_area_wire.directBuffer(), __boltffi_area_wire.size(), __boltffi_num_wire.directBuffer(), __boltffi_num_wire.size(), __boltffi_page_wire.directBuffer(), __boltffi_page_wire.size()) } catch (__boltffi_error: BoltFfiErrorBufferException) { run { val __boltffi_error_reader = WireReader(__boltffi_error.bytes); throw HelperError.fromReader(__boltffi_error_reader) } } ?: throw IllegalStateException("null buffer returned")
        val __boltffi_reader = WireReader(__boltffi_result)
        return NewAlbumResponse.fromReader(__boltffi_reader)
    } finally {
        __boltffi_area_wire.close()
        __boltffi_num_wire.close()
        __boltffi_page_wire.close()
    }
}

/**
 * 检查歌曲是否提供助唱标注歌词.
 */
fun fetchSingingAnnotations(songId: Long): SingingAnnotationsResponse {
    val __boltffi_result = try { Native.boltffi_function_qqmusic_api_helper_next_port_library_extra_fetch_singing_annotations(songId) } catch (__boltffi_error: BoltFfiErrorBufferException) { run { val __boltffi_error_reader = WireReader(__boltffi_error.bytes); throw HelperError.fromReader(__boltffi_error_reader) } } ?: throw IllegalStateException("null buffer returned")
    val __boltffi_reader = WireReader(__boltffi_result)
    return SingingAnnotationsResponse.fromReader(__boltffi_reader)
}

/**
 * 获取歌曲的多风格翻译歌词.
 */
fun fetchMultiStyleLyrics(songId: Long): MultiStyleLyricsResponse {
    val __boltffi_result = try { Native.boltffi_function_qqmusic_api_helper_next_port_library_extra_fetch_multi_style_lyrics(songId) } catch (__boltffi_error: BoltFfiErrorBufferException) { run { val __boltffi_error_reader = WireReader(__boltffi_error.bytes); throw HelperError.fromReader(__boltffi_error_reader) } } ?: throw IllegalStateException("null buffer returned")
    val __boltffi_reader = WireReader(__boltffi_result)
    return MultiStyleLyricsResponse.fromReader(__boltffi_reader)
}

/**
 * 检查歌曲是否有 AI 词典.
 */
fun hasAiDictionary(songId: Long): AiDictionaryExistsResponse {
    val __boltffi_result = try { Native.boltffi_function_qqmusic_api_helper_next_port_library_extra_has_ai_dictionary(songId) } catch (__boltffi_error: BoltFfiErrorBufferException) { run { val __boltffi_error_reader = WireReader(__boltffi_error.bytes); throw HelperError.fromReader(__boltffi_error_reader) } } ?: throw IllegalStateException("null buffer returned")
    val __boltffi_reader = WireReader(__boltffi_result)
    return AiDictionaryExistsResponse.fromReader(__boltffi_reader)
}

/**
 * 获取歌曲的 AI 词典.
 */
fun fetchAiDictionary(songId: Long): AiDictionaryResponse {
    val __boltffi_result = try { Native.boltffi_function_qqmusic_api_helper_next_port_library_extra_fetch_ai_dictionary(songId) } catch (__boltffi_error: BoltFfiErrorBufferException) { run { val __boltffi_error_reader = WireReader(__boltffi_error.bytes); throw HelperError.fromReader(__boltffi_error_reader) } } ?: throw IllegalStateException("null buffer returned")
    val __boltffi_reader = WireReader(__boltffi_result)
    return AiDictionaryResponse.fromReader(__boltffi_reader)
}

/**
 * 获取指定加密 UIN 用户喜欢的歌曲, 默认第 1 页, 10 首.
 */
fun fetchUserLikedSongs(euin: String, page: Long?, num: Long?): UserLikedSongsResponse {
    val __boltffi_euin_wire = WireWriterPool.acquire(4 + Utf8Codec.maxBytes(euin))
    val __boltffi_euin_writer = __boltffi_euin_wire.writer
    __boltffi_euin_writer.writeString(euin)
    val __boltffi_page_wire = WireWriterPool.acquire(if (page == null) 1 else 9)
    val __boltffi_page_writer = __boltffi_page_wire.writer
    __boltffi_page_writer.writeOptionalI64(page)
    val __boltffi_num_wire = WireWriterPool.acquire(if (num == null) 1 else 9)
    val __boltffi_num_writer = __boltffi_num_wire.writer
    __boltffi_num_writer.writeOptionalI64(num)
    try {
        val __boltffi_result = try { Native.boltffi_function_qqmusic_api_helper_next_port_library_extra_fetch_user_liked_songs(__boltffi_euin_wire.directBuffer(), __boltffi_euin_wire.size(), __boltffi_page_wire.directBuffer(), __boltffi_page_wire.size(), __boltffi_num_wire.directBuffer(), __boltffi_num_wire.size()) } catch (__boltffi_error: BoltFfiErrorBufferException) { run { val __boltffi_error_reader = WireReader(__boltffi_error.bytes); throw HelperError.fromReader(__boltffi_error_reader) } } ?: throw IllegalStateException("null buffer returned")
        val __boltffi_reader = WireReader(__boltffi_result)
        return UserLikedSongsResponse.fromReader(__boltffi_reader)
    } finally {
        __boltffi_euin_wire.close()
        __boltffi_page_wire.close()
        __boltffi_num_wire.close()
    }
}

/**
 * 取微信登录二维码（只取码，不扫码）。`loginType` 是 `"wx"`。
 */
fun fetchWxQrcode(): LoginQrCode {
    val __boltffi_result = try { Native.boltffi_function_qqmusic_api_helper_next_port_login_extra_fetch_wx_qrcode() } catch (__boltffi_error: BoltFfiErrorBufferException) { run { val __boltffi_error_reader = WireReader(__boltffi_error.bytes); throw HelperError.fromReader(__boltffi_error_reader) } } ?: throw IllegalStateException("null buffer returned")
    val __boltffi_reader = WireReader(__boltffi_result)
    return LoginQrCode.fromReader(__boltffi_reader)
}

/**
 * 查一次微信扫码状态；返回 `SCAN`/`CONF`/`REFUSE`/`TIMEOUT`，
 * `DONE` 时凭据已写入组件存储，`login` 里的是刚登录的账号。
 */
fun checkWxQrcode(identifier: String): LoginPoll {
    val __boltffi_identifier_wire = WireWriterPool.acquire(4 + Utf8Codec.maxBytes(identifier))
    val __boltffi_identifier_writer = __boltffi_identifier_wire.writer
    __boltffi_identifier_writer.writeString(identifier)
    try {
        val __boltffi_result = try { Native.boltffi_function_qqmusic_api_helper_next_port_login_extra_check_wx_qrcode(__boltffi_identifier_wire.directBuffer(), __boltffi_identifier_wire.size()) } catch (__boltffi_error: BoltFfiErrorBufferException) { run { val __boltffi_error_reader = WireReader(__boltffi_error.bytes); throw HelperError.fromReader(__boltffi_error_reader) } } ?: throw IllegalStateException("null buffer returned")
        val __boltffi_reader = WireReader(__boltffi_result)
        return LoginPoll.fromReader(__boltffi_reader)
    } finally {
        __boltffi_identifier_wire.close()
    }
}

/**
 * 发手机验证码（固定 Android）。`phone` 是明文号码，`encrypted_phone` 是加密号码，
 * 两者只能给一个；`country_code` 缺省 86。
 */
fun sendPhoneAuthcode(phone: Long?, encryptedPhone: String?, countryCode: Long?): PhoneAuthCodeResult {
    val __boltffi_phone_wire = WireWriterPool.acquire(if (phone == null) 1 else 9)
    val __boltffi_phone_writer = __boltffi_phone_wire.writer
    __boltffi_phone_writer.writeOptionalI64(phone)
    val __boltffi_encryptedPhone_wire = WireWriterPool.acquire(1 + (encryptedPhone?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0))
    val __boltffi_encryptedPhone_writer = __boltffi_encryptedPhone_wire.writer
    __boltffi_encryptedPhone_writer.writeOptionalValue(encryptedPhone, { __boltffi_encryptedPhone_writer, __boltffi_value_0 -> __boltffi_encryptedPhone_writer.writeString(__boltffi_value_0) })
    val __boltffi_countryCode_wire = WireWriterPool.acquire(if (countryCode == null) 1 else 9)
    val __boltffi_countryCode_writer = __boltffi_countryCode_wire.writer
    __boltffi_countryCode_writer.writeOptionalI64(countryCode)
    try {
        val __boltffi_result = try { Native.boltffi_function_qqmusic_api_helper_next_port_login_extra_send_phone_authcode(__boltffi_phone_wire.directBuffer(), __boltffi_phone_wire.size(), __boltffi_encryptedPhone_wire.directBuffer(), __boltffi_encryptedPhone_wire.size(), __boltffi_countryCode_wire.directBuffer(), __boltffi_countryCode_wire.size()) } catch (__boltffi_error: BoltFfiErrorBufferException) { run { val __boltffi_error_reader = WireReader(__boltffi_error.bytes); throw HelperError.fromReader(__boltffi_error_reader) } } ?: throw IllegalStateException("null buffer returned")
        val __boltffi_reader = WireReader(__boltffi_result)
        return PhoneAuthCodeResult.fromReader(__boltffi_reader)
    } finally {
        __boltffi_phone_wire.close()
        __boltffi_encryptedPhone_wire.close()
        __boltffi_countryCode_wire.close()
    }
}

/**
 * 验证码登录（固定 Android）；成功后凭据已写入组件存储。
 */
fun phoneLogin(phone: Long?, encryptedPhone: String?, authCode: String): LoginStatus {
    val __boltffi_phone_wire = WireWriterPool.acquire(if (phone == null) 1 else 9)
    val __boltffi_phone_writer = __boltffi_phone_wire.writer
    __boltffi_phone_writer.writeOptionalI64(phone)
    val __boltffi_encryptedPhone_wire = WireWriterPool.acquire(1 + (encryptedPhone?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0))
    val __boltffi_encryptedPhone_writer = __boltffi_encryptedPhone_wire.writer
    __boltffi_encryptedPhone_writer.writeOptionalValue(encryptedPhone, { __boltffi_encryptedPhone_writer, __boltffi_value_0 -> __boltffi_encryptedPhone_writer.writeString(__boltffi_value_0) })
    val __boltffi_authCode_wire = WireWriterPool.acquire(4 + Utf8Codec.maxBytes(authCode))
    val __boltffi_authCode_writer = __boltffi_authCode_wire.writer
    __boltffi_authCode_writer.writeString(authCode)
    try {
        val __boltffi_result = try { Native.boltffi_function_qqmusic_api_helper_next_port_login_extra_phone_login(__boltffi_phone_wire.directBuffer(), __boltffi_phone_wire.size(), __boltffi_encryptedPhone_wire.directBuffer(), __boltffi_encryptedPhone_wire.size(), __boltffi_authCode_wire.directBuffer(), __boltffi_authCode_wire.size()) } catch (__boltffi_error: BoltFfiErrorBufferException) { run { val __boltffi_error_reader = WireReader(__boltffi_error.bytes); throw HelperError.fromReader(__boltffi_error_reader) } } ?: throw IllegalStateException("null buffer returned")
        val __boltffi_reader = WireReader(__boltffi_result)
        return LoginStatus.fromReader(__boltffi_reader)
    } finally {
        __boltffi_phone_wire.close()
        __boltffi_encryptedPhone_wire.close()
        __boltffi_authCode_wire.close()
    }
}

/**
 * 刷新当前凭据并写回存储，返回刷新后的账号状态。
 */
fun refreshCredential(): LoginStatus {
    val __boltffi_result = try { Native.boltffi_function_qqmusic_api_helper_next_port_login_extra_refresh_credential() } catch (__boltffi_error: BoltFfiErrorBufferException) { run { val __boltffi_error_reader = WireReader(__boltffi_error.bytes); throw HelperError.fromReader(__boltffi_error_reader) } } ?: throw IllegalStateException("null buffer returned")
    val __boltffi_reader = WireReader(__boltffi_result)
    return LoginStatus.fromReader(__boltffi_reader)
}

/**
 * 批量获取 MV 详情；回值 `data` 以 vid 为键。
 */
fun fetchMvDetail(vids: List<String>): MvDetailResponse {
    val __boltffi_vids_wire = WireWriterPool.acquire(4 + vids.sumOf { __boltffi_value_0 -> val __boltffi_size: kotlin.Int = 4 + Utf8Codec.maxBytes(__boltffi_value_0); __boltffi_size })
    val __boltffi_vids_writer = __boltffi_vids_wire.writer
    __boltffi_vids_writer.writeSequence(vids, vids.size, { __boltffi_vids_writer, __boltffi_value_0 -> __boltffi_vids_writer.writeString(__boltffi_value_0) })
    try {
        val __boltffi_result = try { Native.boltffi_function_qqmusic_api_helper_next_port_mv_fetch_mv_detail(__boltffi_vids_wire.directBuffer(), __boltffi_vids_wire.size()) } catch (__boltffi_error: BoltFfiErrorBufferException) { run { val __boltffi_error_reader = WireReader(__boltffi_error.bytes); throw HelperError.fromReader(__boltffi_error_reader) } } ?: throw IllegalStateException("null buffer returned")
        val __boltffi_reader = WireReader(__boltffi_result)
        return MvDetailResponse.fromReader(__boltffi_reader)
    } finally {
        __boltffi_vids_wire.close()
    }
}

/**
 * 批量获取 MV 播放地址；回值 `data` 以 vid 为键，每条含 `mp4`/`hls` 两组地址。
 */
fun resolveMvUrls(vids: List<String>): MvUrlResponse {
    val __boltffi_vids_wire = WireWriterPool.acquire(4 + vids.sumOf { __boltffi_value_0 -> val __boltffi_size: kotlin.Int = 4 + Utf8Codec.maxBytes(__boltffi_value_0); __boltffi_size })
    val __boltffi_vids_writer = __boltffi_vids_wire.writer
    __boltffi_vids_writer.writeSequence(vids, vids.size, { __boltffi_vids_writer, __boltffi_value_0 -> __boltffi_vids_writer.writeString(__boltffi_value_0) })
    try {
        val __boltffi_result = try { Native.boltffi_function_qqmusic_api_helper_next_port_mv_resolve_mv_urls(__boltffi_vids_wire.directBuffer(), __boltffi_vids_wire.size()) } catch (__boltffi_error: BoltFfiErrorBufferException) { run { val __boltffi_error_reader = WireReader(__boltffi_error.bytes); throw HelperError.fromReader(__boltffi_error_reader) } } ?: throw IllegalStateException("null buffer returned")
        val __boltffi_reader = WireReader(__boltffi_result)
        return MvUrlResponse.fromReader(__boltffi_reader)
    } finally {
        __boltffi_vids_wire.close()
    }
}

/**
 * MV 分类列表一页；省略的参数按参考的默认值走
 * （15=全部地区、7=全部类型、0=最新、10 条、第 1 页）。
 */
fun fetchMvList(area: Long?, version: Long?, order: Long?, num: Long?, page: Long?): MvListResponse {
    val __boltffi_area_wire = WireWriterPool.acquire(if (area == null) 1 else 9)
    val __boltffi_area_writer = __boltffi_area_wire.writer
    __boltffi_area_writer.writeOptionalI64(area)
    val __boltffi_version_wire = WireWriterPool.acquire(if (version == null) 1 else 9)
    val __boltffi_version_writer = __boltffi_version_wire.writer
    __boltffi_version_writer.writeOptionalI64(version)
    val __boltffi_order_wire = WireWriterPool.acquire(if (order == null) 1 else 9)
    val __boltffi_order_writer = __boltffi_order_wire.writer
    __boltffi_order_writer.writeOptionalI64(order)
    val __boltffi_num_wire = WireWriterPool.acquire(if (num == null) 1 else 9)
    val __boltffi_num_writer = __boltffi_num_wire.writer
    __boltffi_num_writer.writeOptionalI64(num)
    val __boltffi_page_wire = WireWriterPool.acquire(if (page == null) 1 else 9)
    val __boltffi_page_writer = __boltffi_page_wire.writer
    __boltffi_page_writer.writeOptionalI64(page)
    try {
        val __boltffi_result = try { Native.boltffi_function_qqmusic_api_helper_next_port_mv_fetch_mv_list(__boltffi_area_wire.directBuffer(), __boltffi_area_wire.size(), __boltffi_version_wire.directBuffer(), __boltffi_version_wire.size(), __boltffi_order_wire.directBuffer(), __boltffi_order_wire.size(), __boltffi_num_wire.directBuffer(), __boltffi_num_wire.size(), __boltffi_page_wire.directBuffer(), __boltffi_page_wire.size()) } catch (__boltffi_error: BoltFfiErrorBufferException) { run { val __boltffi_error_reader = WireReader(__boltffi_error.bytes); throw HelperError.fromReader(__boltffi_error_reader) } } ?: throw IllegalStateException("null buffer returned")
        val __boltffi_reader = WireReader(__boltffi_result)
        return MvListResponse.fromReader(__boltffi_reader)
    } finally {
        __boltffi_area_wire.close()
        __boltffi_version_wire.close()
        __boltffi_order_wire.close()
        __boltffi_num_wire.close()
        __boltffi_page_wire.close()
    }
}

/**
 * 首页信息流一页；`v_cache` 传已曝光的楼层 ID，防止重复推荐。
 */
fun fetchHomeFeed(page: Long?, direction: Long?, sNum: Long?, vCache: List<String>?): RecommendFeedCardResponse {
    val __boltffi_page_wire = WireWriterPool.acquire(if (page == null) 1 else 9)
    val __boltffi_page_writer = __boltffi_page_wire.writer
    __boltffi_page_writer.writeOptionalI64(page)
    val __boltffi_direction_wire = WireWriterPool.acquire(if (direction == null) 1 else 9)
    val __boltffi_direction_writer = __boltffi_direction_wire.writer
    __boltffi_direction_writer.writeOptionalI64(direction)
    val __boltffi_sNum_wire = WireWriterPool.acquire(if (sNum == null) 1 else 9)
    val __boltffi_sNum_writer = __boltffi_sNum_wire.writer
    __boltffi_sNum_writer.writeOptionalI64(sNum)
    val __boltffi_vCache_wire = WireWriterPool.acquire(1 + (vCache?.let { __boltffi_value_0 -> 4 + __boltffi_value_0.sumOf { __boltffi_value_1 -> val __boltffi_size: kotlin.Int = 4 + Utf8Codec.maxBytes(__boltffi_value_1); __boltffi_size } } ?: 0))
    val __boltffi_vCache_writer = __boltffi_vCache_wire.writer
    __boltffi_vCache_writer.writeOptionalValue(vCache, { __boltffi_vCache_writer, __boltffi_value_0 -> __boltffi_vCache_writer.writeSequence(__boltffi_value_0, __boltffi_value_0.size, { __boltffi_vCache_writer, __boltffi_value_1 -> __boltffi_vCache_writer.writeString(__boltffi_value_1) }) })
    try {
        val __boltffi_result = try { Native.boltffi_function_qqmusic_api_helper_next_port_recommend_extra_fetch_home_feed(__boltffi_page_wire.directBuffer(), __boltffi_page_wire.size(), __boltffi_direction_wire.directBuffer(), __boltffi_direction_wire.size(), __boltffi_sNum_wire.directBuffer(), __boltffi_sNum_wire.size(), __boltffi_vCache_wire.directBuffer(), __boltffi_vCache_wire.size()) } catch (__boltffi_error: BoltFfiErrorBufferException) { run { val __boltffi_error_reader = WireReader(__boltffi_error.bytes); throw HelperError.fromReader(__boltffi_error_reader) } } ?: throw IllegalStateException("null buffer returned")
        val __boltffi_reader = WireReader(__boltffi_result)
        return RecommendFeedCardResponse.fromReader(__boltffi_reader)
    } finally {
        __boltffi_page_wire.close()
        __boltffi_direction_wire.close()
        __boltffi_sNum_wire.close()
        __boltffi_vCache_wire.close()
    }
}

/**
 * 雷达推荐一页；`page` 省略时是第 1 页，回值 `hasMore` 为真时翻下一页。
 */
fun fetchRadarRecommend(page: Long?): RadarRecommendResponse {
    val __boltffi_page_wire = WireWriterPool.acquire(if (page == null) 1 else 9)
    val __boltffi_page_writer = __boltffi_page_wire.writer
    __boltffi_page_writer.writeOptionalI64(page)
    try {
        val __boltffi_result = try { Native.boltffi_function_qqmusic_api_helper_next_port_recommend_extra_fetch_radar_recommend(__boltffi_page_wire.directBuffer(), __boltffi_page_wire.size()) } catch (__boltffi_error: BoltFfiErrorBufferException) { run { val __boltffi_error_reader = WireReader(__boltffi_error.bytes); throw HelperError.fromReader(__boltffi_error_reader) } } ?: throw IllegalStateException("null buffer returned")
        val __boltffi_reader = WireReader(__boltffi_result)
        return RadarRecommendResponse.fromReader(__boltffi_reader)
    } finally {
        __boltffi_page_wire.close()
    }
}

/**
 * 推荐歌单一页；`num` 省略时是 25 条，回值 `fromLimit` 就是下一页的 `From`。
 */
fun fetchRecommendPlaylists(page: Long?, num: Long?): RecommendSonglistResponse {
    val __boltffi_page_wire = WireWriterPool.acquire(if (page == null) 1 else 9)
    val __boltffi_page_writer = __boltffi_page_wire.writer
    __boltffi_page_writer.writeOptionalI64(page)
    val __boltffi_num_wire = WireWriterPool.acquire(if (num == null) 1 else 9)
    val __boltffi_num_writer = __boltffi_num_wire.writer
    __boltffi_num_writer.writeOptionalI64(num)
    try {
        val __boltffi_result = try { Native.boltffi_function_qqmusic_api_helper_next_port_recommend_extra_fetch_recommend_playlists(__boltffi_page_wire.directBuffer(), __boltffi_page_wire.size(), __boltffi_num_wire.directBuffer(), __boltffi_num_wire.size()) } catch (__boltffi_error: BoltFfiErrorBufferException) { run { val __boltffi_error_reader = WireReader(__boltffi_error.bytes); throw HelperError.fromReader(__boltffi_error_reader) } } ?: throw IllegalStateException("null buffer returned")
        val __boltffi_reader = WireReader(__boltffi_result)
        return RecommendSonglistResponse.fromReader(__boltffi_reader)
    } finally {
        __boltffi_page_wire.close()
        __boltffi_num_wire.close()
    }
}

/**
 * 热搜词列表。
 */
fun fetchSearchHotkeys(): HotkeyResponse {
    val __boltffi_result = try { Native.boltffi_function_qqmusic_api_helper_next_port_search_extra_fetch_search_hotkeys() } catch (__boltffi_error: BoltFfiErrorBufferException) { run { val __boltffi_error_reader = WireReader(__boltffi_error.bytes); throw HelperError.fromReader(__boltffi_error_reader) } } ?: throw IllegalStateException("null buffer returned")
    val __boltffi_reader = WireReader(__boltffi_result)
    return HotkeyResponse.fromReader(__boltffi_reader)
}

/**
 * 搜索词补全建议；`keyword` 是输入到一半的词。
 */
fun completeSearch(keyword: String): CompleteResponse {
    val __boltffi_keyword_wire = WireWriterPool.acquire(4 + Utf8Codec.maxBytes(keyword))
    val __boltffi_keyword_writer = __boltffi_keyword_wire.writer
    __boltffi_keyword_writer.writeString(keyword)
    try {
        val __boltffi_result = try { Native.boltffi_function_qqmusic_api_helper_next_port_search_extra_complete_search(__boltffi_keyword_wire.directBuffer(), __boltffi_keyword_wire.size()) } catch (__boltffi_error: BoltFfiErrorBufferException) { run { val __boltffi_error_reader = WireReader(__boltffi_error.bytes); throw HelperError.fromReader(__boltffi_error_reader) } } ?: throw IllegalStateException("null buffer returned")
        val __boltffi_reader = WireReader(__boltffi_result)
        return CompleteResponse.fromReader(__boltffi_reader)
    } finally {
        __boltffi_keyword_wire.close()
    }
}

/**
 * 快速搜索；返回单曲/歌手/专辑/MV 四个分类的条目。
 */
fun quickSearch(keyword: String): QuickSearchResponse {
    val __boltffi_keyword_wire = WireWriterPool.acquire(4 + Utf8Codec.maxBytes(keyword))
    val __boltffi_keyword_writer = __boltffi_keyword_wire.writer
    __boltffi_keyword_writer.writeString(keyword)
    try {
        val __boltffi_result = try { Native.boltffi_function_qqmusic_api_helper_next_port_search_extra_quick_search(__boltffi_keyword_wire.directBuffer(), __boltffi_keyword_wire.size()) } catch (__boltffi_error: BoltFfiErrorBufferException) { run { val __boltffi_error_reader = WireReader(__boltffi_error.bytes); throw HelperError.fromReader(__boltffi_error_reader) } } ?: throw IllegalStateException("null buffer returned")
        val __boltffi_reader = WireReader(__boltffi_result)
        return QuickSearchResponse.fromReader(__boltffi_reader)
    } finally {
        __boltffi_keyword_wire.close()
    }
}

/**
 * 综合搜索；`page_start` 是上一页回带的续参（透传的对象），
 * `highlight` 给了就是参考的关键字参数（缺省 true）。
 */
fun generalSearch(keyword: String, page: Long?, num: Long?, searchid: String?, pageStart: String?, highlight: Boolean?): GeneralSearchResponse {
    val __boltffi_keyword_wire = WireWriterPool.acquire(4 + Utf8Codec.maxBytes(keyword))
    val __boltffi_keyword_writer = __boltffi_keyword_wire.writer
    __boltffi_keyword_writer.writeString(keyword)
    val __boltffi_page_wire = WireWriterPool.acquire(if (page == null) 1 else 9)
    val __boltffi_page_writer = __boltffi_page_wire.writer
    __boltffi_page_writer.writeOptionalI64(page)
    val __boltffi_num_wire = WireWriterPool.acquire(if (num == null) 1 else 9)
    val __boltffi_num_writer = __boltffi_num_wire.writer
    __boltffi_num_writer.writeOptionalI64(num)
    val __boltffi_searchid_wire = WireWriterPool.acquire(1 + (searchid?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0))
    val __boltffi_searchid_writer = __boltffi_searchid_wire.writer
    __boltffi_searchid_writer.writeOptionalValue(searchid, { __boltffi_searchid_writer, __boltffi_value_0 -> __boltffi_searchid_writer.writeString(__boltffi_value_0) })
    val __boltffi_pageStart_wire = WireWriterPool.acquire(1 + (pageStart?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0))
    val __boltffi_pageStart_writer = __boltffi_pageStart_wire.writer
    __boltffi_pageStart_writer.writeOptionalValue(pageStart, { __boltffi_pageStart_writer, __boltffi_value_0 -> __boltffi_pageStart_writer.writeString(__boltffi_value_0) })
    val __boltffi_highlight_wire = WireWriterPool.acquire(if (highlight == null) 1 else 2)
    val __boltffi_highlight_writer = __boltffi_highlight_wire.writer
    __boltffi_highlight_writer.writeOptionalBool(highlight)
    try {
        val __boltffi_result = try { Native.boltffi_function_qqmusic_api_helper_next_port_search_extra_general_search(__boltffi_keyword_wire.directBuffer(), __boltffi_keyword_wire.size(), __boltffi_page_wire.directBuffer(), __boltffi_page_wire.size(), __boltffi_num_wire.directBuffer(), __boltffi_num_wire.size(), __boltffi_searchid_wire.directBuffer(), __boltffi_searchid_wire.size(), __boltffi_pageStart_wire.directBuffer(), __boltffi_pageStart_wire.size(), __boltffi_highlight_wire.directBuffer(), __boltffi_highlight_wire.size()) } catch (__boltffi_error: BoltFfiErrorBufferException) { run { val __boltffi_error_reader = WireReader(__boltffi_error.bytes); throw HelperError.fromReader(__boltffi_error_reader) } } ?: throw IllegalStateException("null buffer returned")
        val __boltffi_reader = WireReader(__boltffi_result)
        return GeneralSearchResponse.fromReader(__boltffi_reader)
    } finally {
        __boltffi_keyword_wire.close()
        __boltffi_page_wire.close()
        __boltffi_num_wire.close()
        __boltffi_searchid_wire.close()
        __boltffi_pageStart_wire.close()
        __boltffi_highlight_wire.close()
    }
}

/**
 * 类型搜索；`search_type`: 0 歌曲 1 歌手 2 专辑 3 歌单 4 MV 7 歌词 8 用户
 * 10 彩铃 15 节目专辑 18 节目。`selectors` 是筛选器列表（参考 `SearchSelector`），
 * `highlight` 给了就是参考的关键字参数（缺省 true）。
 */
fun searchExtra(keyword: String, searchType: Long?, page: Long?, num: Long?, searchid: String?, selectors: List<SearchSelector>?, highlight: Boolean?): SearchByTypeResponse {
    val __boltffi_keyword_wire = WireWriterPool.acquire(4 + Utf8Codec.maxBytes(keyword))
    val __boltffi_keyword_writer = __boltffi_keyword_wire.writer
    __boltffi_keyword_writer.writeString(keyword)
    val __boltffi_searchType_wire = WireWriterPool.acquire(if (searchType == null) 1 else 9)
    val __boltffi_searchType_writer = __boltffi_searchType_wire.writer
    __boltffi_searchType_writer.writeOptionalI64(searchType)
    val __boltffi_page_wire = WireWriterPool.acquire(if (page == null) 1 else 9)
    val __boltffi_page_writer = __boltffi_page_wire.writer
    __boltffi_page_writer.writeOptionalI64(page)
    val __boltffi_num_wire = WireWriterPool.acquire(if (num == null) 1 else 9)
    val __boltffi_num_writer = __boltffi_num_wire.writer
    __boltffi_num_writer.writeOptionalI64(num)
    val __boltffi_searchid_wire = WireWriterPool.acquire(1 + (searchid?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0))
    val __boltffi_searchid_writer = __boltffi_searchid_wire.writer
    __boltffi_searchid_writer.writeOptionalValue(searchid, { __boltffi_searchid_writer, __boltffi_value_0 -> __boltffi_searchid_writer.writeString(__boltffi_value_0) })
    val __boltffi_selectors_wire = WireWriterPool.acquire(1 + (selectors?.let { __boltffi_value_0 -> 4 + __boltffi_value_0.sumOf { __boltffi_value_1 -> val __boltffi_size: kotlin.Int = __boltffi_value_1.wireSize(); __boltffi_size } } ?: 0))
    val __boltffi_selectors_writer = __boltffi_selectors_wire.writer
    __boltffi_selectors_writer.writeOptionalValue(selectors, { __boltffi_selectors_writer, __boltffi_value_0 -> __boltffi_selectors_writer.writeSequence(__boltffi_value_0, __boltffi_value_0.size, { __boltffi_selectors_writer, __boltffi_value_1 -> __boltffi_value_1.writeTo(__boltffi_selectors_writer) }) })
    val __boltffi_highlight_wire = WireWriterPool.acquire(if (highlight == null) 1 else 2)
    val __boltffi_highlight_writer = __boltffi_highlight_wire.writer
    __boltffi_highlight_writer.writeOptionalBool(highlight)
    try {
        val __boltffi_result = try { Native.boltffi_function_qqmusic_api_helper_next_port_search_extra_search_extra(__boltffi_keyword_wire.directBuffer(), __boltffi_keyword_wire.size(), __boltffi_searchType_wire.directBuffer(), __boltffi_searchType_wire.size(), __boltffi_page_wire.directBuffer(), __boltffi_page_wire.size(), __boltffi_num_wire.directBuffer(), __boltffi_num_wire.size(), __boltffi_searchid_wire.directBuffer(), __boltffi_searchid_wire.size(), __boltffi_selectors_wire.directBuffer(), __boltffi_selectors_wire.size(), __boltffi_highlight_wire.directBuffer(), __boltffi_highlight_wire.size()) } catch (__boltffi_error: BoltFfiErrorBufferException) { run { val __boltffi_error_reader = WireReader(__boltffi_error.bytes); throw HelperError.fromReader(__boltffi_error_reader) } } ?: throw IllegalStateException("null buffer returned")
        val __boltffi_reader = WireReader(__boltffi_result)
        return SearchByTypeResponse.fromReader(__boltffi_reader)
    } finally {
        __boltffi_keyword_wire.close()
        __boltffi_searchType_wire.close()
        __boltffi_page_wire.close()
        __boltffi_num_wire.close()
        __boltffi_searchid_wire.close()
        __boltffi_selectors_wire.close()
        __boltffi_highlight_wire.close()
    }
}

/**
 * 歌手列表；省略的筛选值按参考的缺省（都是 -100 = 全部）。
 */
fun fetchSingerList(area: Long?, sex: Long?, genre: Long?): SingerTypeList {
    val __boltffi_area_wire = WireWriterPool.acquire(if (area == null) 1 else 9)
    val __boltffi_area_writer = __boltffi_area_wire.writer
    __boltffi_area_writer.writeOptionalI64(area)
    val __boltffi_sex_wire = WireWriterPool.acquire(if (sex == null) 1 else 9)
    val __boltffi_sex_writer = __boltffi_sex_wire.writer
    __boltffi_sex_writer.writeOptionalI64(sex)
    val __boltffi_genre_wire = WireWriterPool.acquire(if (genre == null) 1 else 9)
    val __boltffi_genre_writer = __boltffi_genre_wire.writer
    __boltffi_genre_writer.writeOptionalI64(genre)
    try {
        val __boltffi_result = try { Native.boltffi_function_qqmusic_api_helper_next_port_singer_extra_fetch_singer_list(__boltffi_area_wire.directBuffer(), __boltffi_area_wire.size(), __boltffi_sex_wire.directBuffer(), __boltffi_sex_wire.size(), __boltffi_genre_wire.directBuffer(), __boltffi_genre_wire.size()) } catch (__boltffi_error: BoltFfiErrorBufferException) { run { val __boltffi_error_reader = WireReader(__boltffi_error.bytes); throw HelperError.fromReader(__boltffi_error_reader) } } ?: throw IllegalStateException("null buffer returned")
        val __boltffi_reader = WireReader(__boltffi_result)
        return SingerTypeList.fromReader(__boltffi_reader)
    } finally {
        __boltffi_area_wire.close()
        __boltffi_sex_wire.close()
        __boltffi_genre_wire.close()
    }
}

/**
 * 按首字母索引分页的歌手列表；`page` 缺省 1、`num` 缺省 80。
 * 翻页时把回值的 `total` 与当前 `sin`/`cur_page` 对上再取下一页。
 */
fun fetchSingerIndex(area: Long?, sex: Long?, genre: Long?, index: Long?, page: Long?, num: Long?): SingerIndexPage {
    val __boltffi_area_wire = WireWriterPool.acquire(if (area == null) 1 else 9)
    val __boltffi_area_writer = __boltffi_area_wire.writer
    __boltffi_area_writer.writeOptionalI64(area)
    val __boltffi_sex_wire = WireWriterPool.acquire(if (sex == null) 1 else 9)
    val __boltffi_sex_writer = __boltffi_sex_wire.writer
    __boltffi_sex_writer.writeOptionalI64(sex)
    val __boltffi_genre_wire = WireWriterPool.acquire(if (genre == null) 1 else 9)
    val __boltffi_genre_writer = __boltffi_genre_wire.writer
    __boltffi_genre_writer.writeOptionalI64(genre)
    val __boltffi_index_wire = WireWriterPool.acquire(if (index == null) 1 else 9)
    val __boltffi_index_writer = __boltffi_index_wire.writer
    __boltffi_index_writer.writeOptionalI64(index)
    val __boltffi_page_wire = WireWriterPool.acquire(if (page == null) 1 else 9)
    val __boltffi_page_writer = __boltffi_page_wire.writer
    __boltffi_page_writer.writeOptionalI64(page)
    val __boltffi_num_wire = WireWriterPool.acquire(if (num == null) 1 else 9)
    val __boltffi_num_writer = __boltffi_num_wire.writer
    __boltffi_num_writer.writeOptionalI64(num)
    try {
        val __boltffi_result = try { Native.boltffi_function_qqmusic_api_helper_next_port_singer_extra_fetch_singer_index(__boltffi_area_wire.directBuffer(), __boltffi_area_wire.size(), __boltffi_sex_wire.directBuffer(), __boltffi_sex_wire.size(), __boltffi_genre_wire.directBuffer(), __boltffi_genre_wire.size(), __boltffi_index_wire.directBuffer(), __boltffi_index_wire.size(), __boltffi_page_wire.directBuffer(), __boltffi_page_wire.size(), __boltffi_num_wire.directBuffer(), __boltffi_num_wire.size()) } catch (__boltffi_error: BoltFfiErrorBufferException) { run { val __boltffi_error_reader = WireReader(__boltffi_error.bytes); throw HelperError.fromReader(__boltffi_error_reader) } } ?: throw IllegalStateException("null buffer returned")
        val __boltffi_reader = WireReader(__boltffi_result)
        return SingerIndexPage.fromReader(__boltffi_reader)
    } finally {
        __boltffi_area_wire.close()
        __boltffi_sex_wire.close()
        __boltffi_genre_wire.close()
        __boltffi_index_wire.close()
        __boltffi_page_wire.close()
        __boltffi_num_wire.close()
    }
}

/**
 * 相似歌手；`number` 缺省 10。
 */
fun fetchSimilarArtists(singerMid: String, number: Long?): SimilarSingerList {
    val __boltffi_singerMid_wire = WireWriterPool.acquire(4 + Utf8Codec.maxBytes(singerMid))
    val __boltffi_singerMid_writer = __boltffi_singerMid_wire.writer
    __boltffi_singerMid_writer.writeString(singerMid)
    val __boltffi_number_wire = WireWriterPool.acquire(if (number == null) 1 else 9)
    val __boltffi_number_writer = __boltffi_number_wire.writer
    __boltffi_number_writer.writeOptionalI64(number)
    try {
        val __boltffi_result = try { Native.boltffi_function_qqmusic_api_helper_next_port_singer_extra_fetch_similar_artists(__boltffi_singerMid_wire.directBuffer(), __boltffi_singerMid_wire.size(), __boltffi_number_wire.directBuffer(), __boltffi_number_wire.size()) } catch (__boltffi_error: BoltFfiErrorBufferException) { run { val __boltffi_error_reader = WireReader(__boltffi_error.bytes); throw HelperError.fromReader(__boltffi_error_reader) } } ?: throw IllegalStateException("null buffer returned")
        val __boltffi_reader = WireReader(__boltffi_result)
        return SimilarSingerList.fromReader(__boltffi_reader)
    } finally {
        __boltffi_singerMid_wire.close()
        __boltffi_number_wire.close()
    }
}

/**
 * 歌手主页某个 Tab 的一页；`tab_type` 是参考 `TabType` 的字符串标识
 * （`wiki` / `album` / `song_sing` / `video` / `song_composing` / …）。
 */
fun fetchArtistTab(singerMid: String, tabType: String, page: Long?, num: Long?): HomepageTabDetail {
    val __boltffi_singerMid_wire = WireWriterPool.acquire(4 + Utf8Codec.maxBytes(singerMid))
    val __boltffi_singerMid_writer = __boltffi_singerMid_wire.writer
    __boltffi_singerMid_writer.writeString(singerMid)
    val __boltffi_tabType_wire = WireWriterPool.acquire(4 + Utf8Codec.maxBytes(tabType))
    val __boltffi_tabType_writer = __boltffi_tabType_wire.writer
    __boltffi_tabType_writer.writeString(tabType)
    val __boltffi_page_wire = WireWriterPool.acquire(if (page == null) 1 else 9)
    val __boltffi_page_writer = __boltffi_page_wire.writer
    __boltffi_page_writer.writeOptionalI64(page)
    val __boltffi_num_wire = WireWriterPool.acquire(if (num == null) 1 else 9)
    val __boltffi_num_writer = __boltffi_num_wire.writer
    __boltffi_num_writer.writeOptionalI64(num)
    try {
        val __boltffi_result = try { Native.boltffi_function_qqmusic_api_helper_next_port_singer_extra_fetch_artist_tab(__boltffi_singerMid_wire.directBuffer(), __boltffi_singerMid_wire.size(), __boltffi_tabType_wire.directBuffer(), __boltffi_tabType_wire.size(), __boltffi_page_wire.directBuffer(), __boltffi_page_wire.size(), __boltffi_num_wire.directBuffer(), __boltffi_num_wire.size()) } catch (__boltffi_error: BoltFfiErrorBufferException) { run { val __boltffi_error_reader = WireReader(__boltffi_error.bytes); throw HelperError.fromReader(__boltffi_error_reader) } } ?: throw IllegalStateException("null buffer returned")
        val __boltffi_reader = WireReader(__boltffi_result)
        return HomepageTabDetail.fromReader(__boltffi_reader)
    } finally {
        __boltffi_singerMid_wire.close()
        __boltffi_tabType_wire.close()
        __boltffi_page_wire.close()
        __boltffi_num_wire.close()
    }
}

/**
 * 歌手名称透明 PNG；无名称图片时 `displayType` 为 0、`picFile` 为空。
 */
fun fetchArtistDisplayName(singerMid: String): SingerNameSpecialDisplay {
    val __boltffi_singerMid_wire = WireWriterPool.acquire(4 + Utf8Codec.maxBytes(singerMid))
    val __boltffi_singerMid_writer = __boltffi_singerMid_wire.writer
    __boltffi_singerMid_writer.writeString(singerMid)
    try {
        val __boltffi_result = try { Native.boltffi_function_qqmusic_api_helper_next_port_singer_extra_fetch_artist_display_name(__boltffi_singerMid_wire.directBuffer(), __boltffi_singerMid_wire.size()) } catch (__boltffi_error: BoltFfiErrorBufferException) { run { val __boltffi_error_reader = WireReader(__boltffi_error.bytes); throw HelperError.fromReader(__boltffi_error_reader) } } ?: throw IllegalStateException("null buffer returned")
        val __boltffi_reader = WireReader(__boltffi_result)
        return SingerNameSpecialDisplay.fromReader(__boltffi_reader)
    } finally {
        __boltffi_singerMid_wire.close()
    }
}

/**
 * 歌手 MV 一页；`num` 缺省 10、`page` 缺省 1，总数在 `total`。
 */
fun fetchArtistMvs(singerMid: String, num: Long?, page: Long?): SingerMvList {
    val __boltffi_singerMid_wire = WireWriterPool.acquire(4 + Utf8Codec.maxBytes(singerMid))
    val __boltffi_singerMid_writer = __boltffi_singerMid_wire.writer
    __boltffi_singerMid_writer.writeString(singerMid)
    val __boltffi_num_wire = WireWriterPool.acquire(if (num == null) 1 else 9)
    val __boltffi_num_writer = __boltffi_num_wire.writer
    __boltffi_num_writer.writeOptionalI64(num)
    val __boltffi_page_wire = WireWriterPool.acquire(if (page == null) 1 else 9)
    val __boltffi_page_writer = __boltffi_page_wire.writer
    __boltffi_page_writer.writeOptionalI64(page)
    try {
        val __boltffi_result = try { Native.boltffi_function_qqmusic_api_helper_next_port_singer_extra_fetch_artist_mvs(__boltffi_singerMid_wire.directBuffer(), __boltffi_singerMid_wire.size(), __boltffi_num_wire.directBuffer(), __boltffi_num_wire.size(), __boltffi_page_wire.directBuffer(), __boltffi_page_wire.size()) } catch (__boltffi_error: BoltFfiErrorBufferException) { run { val __boltffi_error_reader = WireReader(__boltffi_error.bytes); throw HelperError.fromReader(__boltffi_error_reader) } } ?: throw IllegalStateException("null buffer returned")
        val __boltffi_reader = WireReader(__boltffi_result)
        return SingerMvList.fromReader(__boltffi_reader)
    } finally {
        __boltffi_singerMid_wire.close()
        __boltffi_num_wire.close()
        __boltffi_page_wire.close()
    }
}

/**
 * 批量获取歌曲信息；每项给 `id` 或 `mid` 之一，`songType` 缺省 0。
 */
fun querySongs(songs: List<SongQueryInfo>): QuerySongResponse {
    val __boltffi_songs_wire = WireWriterPool.acquire(4 + songs.sumOf { __boltffi_value_0 -> val __boltffi_size: kotlin.Int = __boltffi_value_0.wireSize(); __boltffi_size })
    val __boltffi_songs_writer = __boltffi_songs_wire.writer
    __boltffi_songs_writer.writeSequence(songs, songs.size, { __boltffi_songs_writer, __boltffi_value_0 -> __boltffi_value_0.writeTo(__boltffi_songs_writer) })
    try {
        val __boltffi_result = try { Native.boltffi_function_qqmusic_api_helper_next_port_song_asset_query_songs(__boltffi_songs_wire.directBuffer(), __boltffi_songs_wire.size()) } catch (__boltffi_error: BoltFfiErrorBufferException) { run { val __boltffi_error_reader = WireReader(__boltffi_error.bytes); throw HelperError.fromReader(__boltffi_error_reader) } } ?: throw IllegalStateException("null buffer returned")
        val __boltffi_reader = WireReader(__boltffi_result)
        return QuerySongResponse.fromReader(__boltffi_reader)
    } finally {
        __boltffi_songs_wire.close()
    }
}

/**
 * 取音频 CDN 调度信息；`sip` 里的根地址用来拼取流回值的 `purl`。
 */
fun fetchCdnDispatch(): GetCdnDispatchResponse {
    val __boltffi_result = try { Native.boltffi_function_qqmusic_api_helper_next_port_song_asset_fetch_cdn_dispatch() } catch (__boltffi_error: BoltFfiErrorBufferException) { run { val __boltffi_error_reader = WireReader(__boltffi_error.bytes); throw HelperError.fromReader(__boltffi_error_reader) } } ?: throw IllegalStateException("null buffer returned")
    val __boltffi_reader = WireReader(__boltffi_result)
    return GetCdnDispatchResponse.fromReader(__boltffi_reader)
}

/**
 * 批量取流；`file_type` 是参考枚举的成员名或 4 位前缀，缺省 `MP3_128`。
 * 一次最多 100 个 mid，超了在发请求前报错。
 */
fun resolveSongUrls(fileInfo: List<SongFileInfo>, fileType: String?): GetSongUrlsResponse {
    val __boltffi_fileInfo_wire = WireWriterPool.acquire(4 + fileInfo.sumOf { __boltffi_value_0 -> val __boltffi_size: kotlin.Int = __boltffi_value_0.wireSize(); __boltffi_size })
    val __boltffi_fileInfo_writer = __boltffi_fileInfo_wire.writer
    __boltffi_fileInfo_writer.writeSequence(fileInfo, fileInfo.size, { __boltffi_fileInfo_writer, __boltffi_value_0 -> __boltffi_value_0.writeTo(__boltffi_fileInfo_writer) })
    val __boltffi_fileType_wire = WireWriterPool.acquire(1 + (fileType?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0))
    val __boltffi_fileType_writer = __boltffi_fileType_wire.writer
    __boltffi_fileType_writer.writeOptionalValue(fileType, { __boltffi_fileType_writer, __boltffi_value_0 -> __boltffi_fileType_writer.writeString(__boltffi_value_0) })
    try {
        val __boltffi_result = try { Native.boltffi_function_qqmusic_api_helper_next_port_song_asset_resolve_song_urls(__boltffi_fileInfo_wire.directBuffer(), __boltffi_fileInfo_wire.size(), __boltffi_fileType_wire.directBuffer(), __boltffi_fileType_wire.size()) } catch (__boltffi_error: BoltFfiErrorBufferException) { run { val __boltffi_error_reader = WireReader(__boltffi_error.bytes); throw HelperError.fromReader(__boltffi_error_reader) } } ?: throw IllegalStateException("null buffer returned")
        val __boltffi_reader = WireReader(__boltffi_result)
        return GetSongUrlsResponse.fromReader(__boltffi_reader)
    } finally {
        __boltffi_fileInfo_wire.close()
        __boltffi_fileType_wire.close()
    }
}

/**
 * 歌曲其他版本；`value` 全为数字时按歌曲 ID 走，否则按 MID。
 */
fun fetchOtherVersions(value: String): GetOtherVersionResponse {
    val __boltffi_value_wire = WireWriterPool.acquire(4 + Utf8Codec.maxBytes(value))
    val __boltffi_value_writer = __boltffi_value_wire.writer
    __boltffi_value_writer.writeString(value)
    try {
        val __boltffi_result = try { Native.boltffi_function_qqmusic_api_helper_next_port_song_asset_fetch_other_versions(__boltffi_value_wire.directBuffer(), __boltffi_value_wire.size()) } catch (__boltffi_error: BoltFfiErrorBufferException) { run { val __boltffi_error_reader = WireReader(__boltffi_error.bytes); throw HelperError.fromReader(__boltffi_error_reader) } } ?: throw IllegalStateException("null buffer returned")
        val __boltffi_reader = WireReader(__boltffi_result)
        return GetOtherVersionResponse.fromReader(__boltffi_reader)
    } finally {
        __boltffi_value_wire.close()
    }
}

/**
 * 歌曲制作人；`value` 全为数字时按歌曲 ID 走，否则按 MID。
 */
fun fetchSongProducer(value: String): GetProducerResponse {
    val __boltffi_value_wire = WireWriterPool.acquire(4 + Utf8Codec.maxBytes(value))
    val __boltffi_value_writer = __boltffi_value_wire.writer
    __boltffi_value_writer.writeString(value)
    try {
        val __boltffi_result = try { Native.boltffi_function_qqmusic_api_helper_next_port_song_asset_fetch_song_producer(__boltffi_value_wire.directBuffer(), __boltffi_value_wire.size()) } catch (__boltffi_error: BoltFfiErrorBufferException) { run { val __boltffi_error_reader = WireReader(__boltffi_error.bytes); throw HelperError.fromReader(__boltffi_error_reader) } } ?: throw IllegalStateException("null buffer returned")
        val __boltffi_reader = WireReader(__boltffi_result)
        return GetProducerResponse.fromReader(__boltffi_reader)
    } finally {
        __boltffi_value_wire.close()
    }
}

/**
 * 歌曲收藏数；回值的 `numbers` 以歌曲 ID 为键。
 */
fun fetchSongFavCount(songIds: LongArray): GetFavNumResponse {
    val __boltffi_result = try { Native.boltffi_function_qqmusic_api_helper_next_port_song_asset_fetch_song_fav_count(songIds) } catch (__boltffi_error: BoltFfiErrorBufferException) { run { val __boltffi_error_reader = WireReader(__boltffi_error.bytes); throw HelperError.fromReader(__boltffi_error_reader) } } ?: throw IllegalStateException("null buffer returned")
    val __boltffi_reader = WireReader(__boltffi_result)
    return GetFavNumResponse.fromReader(__boltffi_reader)
}

/**
 * 相似歌曲；回值按推荐分组给出，每组一个标题与一组曲目。
 */
fun fetchSimilarSongs(songId: Long): GetSimilarSongResponse {
    val __boltffi_result = try { Native.boltffi_function_qqmusic_api_helper_next_port_song_related_fetch_similar_songs(songId) } catch (__boltffi_error: BoltFfiErrorBufferException) { run { val __boltffi_error_reader = WireReader(__boltffi_error.bytes); throw HelperError.fromReader(__boltffi_error_reader) } } ?: throw IllegalStateException("null buffer returned")
    val __boltffi_reader = WireReader(__boltffi_result)
    return GetSimilarSongResponse.fromReader(__boltffi_reader)
}

/**
 * 歌曲标签；空列表表示这首歌没有标签。
 */
fun fetchSongLabels(songId: Long): GetSongLabelsResponse {
    val __boltffi_result = try { Native.boltffi_function_qqmusic_api_helper_next_port_song_related_fetch_song_labels(songId) } catch (__boltffi_error: BoltFfiErrorBufferException) { run { val __boltffi_error_reader = WireReader(__boltffi_error.bytes); throw HelperError.fromReader(__boltffi_error_reader) } } ?: throw IllegalStateException("null buffer returned")
    val __boltffi_reader = WireReader(__boltffi_result)
    return GetSongLabelsResponse.fromReader(__boltffi_reader)
}

/**
 * 相关歌单；`last` 传上一批的歌单 ID 列表可以「换一批」，缺省是空列表。
 */
fun fetchRelatedPlaylists(songId: Long, last: LongArray?): GetRelatedSonglistResponse {
    val __boltffi_last_wire = WireWriterPool.acquire(1 + (last?.let { __boltffi_value_0 -> 4 + __boltffi_value_0.size * 8 } ?: 0))
    val __boltffi_last_writer = __boltffi_last_wire.writer
    __boltffi_last_writer.writeOptionalValue(last, { __boltffi_last_writer, __boltffi_value_0 -> __boltffi_last_writer.writeLongArray(__boltffi_value_0) })
    try {
        val __boltffi_result = try { Native.boltffi_function_qqmusic_api_helper_next_port_song_related_fetch_related_playlists(songId, __boltffi_last_wire.directBuffer(), __boltffi_last_wire.size()) } catch (__boltffi_error: BoltFfiErrorBufferException) { run { val __boltffi_error_reader = WireReader(__boltffi_error.bytes); throw HelperError.fromReader(__boltffi_error_reader) } } ?: throw IllegalStateException("null buffer returned")
        val __boltffi_reader = WireReader(__boltffi_result)
        return GetRelatedSonglistResponse.fromReader(__boltffi_reader)
    } finally {
        __boltffi_last_wire.close()
    }
}

/**
 * 相关 MV；`last_mvid` 传上一批最后一个 MV 的 VID 可以「换一批」，缺省发 0。
 */
fun fetchRelatedMvs(songId: Long, lastMvid: String?): GetRelatedMvResponse {
    val __boltffi_lastMvid_wire = WireWriterPool.acquire(1 + (lastMvid?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0))
    val __boltffi_lastMvid_writer = __boltffi_lastMvid_wire.writer
    __boltffi_lastMvid_writer.writeOptionalValue(lastMvid, { __boltffi_lastMvid_writer, __boltffi_value_0 -> __boltffi_lastMvid_writer.writeString(__boltffi_value_0) })
    try {
        val __boltffi_result = try { Native.boltffi_function_qqmusic_api_helper_next_port_song_related_fetch_related_mvs(songId, __boltffi_lastMvid_wire.directBuffer(), __boltffi_lastMvid_wire.size()) } catch (__boltffi_error: BoltFfiErrorBufferException) { run { val __boltffi_error_reader = WireReader(__boltffi_error.bytes); throw HelperError.fromReader(__boltffi_error_reader) } } ?: throw IllegalStateException("null buffer returned")
        val __boltffi_reader = WireReader(__boltffi_result)
        return GetRelatedMvResponse.fromReader(__boltffi_reader)
    } finally {
        __boltffi_lastMvid_wire.close()
    }
}

/**
 * 曲谱；`ttype` 0=用户上传、1=引擎/AI、2=虫虫钢琴，缺省 0。
 * 没有曲谱时 `result` 是空列表（10007 不是错误）。
 */
fun fetchSheetMusic(mid: String, ttype: Long?): GetSheetResponse {
    val __boltffi_mid_wire = WireWriterPool.acquire(4 + Utf8Codec.maxBytes(mid))
    val __boltffi_mid_writer = __boltffi_mid_wire.writer
    __boltffi_mid_writer.writeString(mid)
    val __boltffi_ttype_wire = WireWriterPool.acquire(if (ttype == null) 1 else 9)
    val __boltffi_ttype_writer = __boltffi_ttype_wire.writer
    __boltffi_ttype_writer.writeOptionalI64(ttype)
    try {
        val __boltffi_result = try { Native.boltffi_function_qqmusic_api_helper_next_port_song_related_fetch_sheet_music(__boltffi_mid_wire.directBuffer(), __boltffi_mid_wire.size(), __boltffi_ttype_wire.directBuffer(), __boltffi_ttype_wire.size()) } catch (__boltffi_error: BoltFfiErrorBufferException) { run { val __boltffi_error_reader = WireReader(__boltffi_error.bytes); throw HelperError.fromReader(__boltffi_error_reader) } } ?: throw IllegalStateException("null buffer returned")
        val __boltffi_reader = WireReader(__boltffi_result)
        return GetSheetResponse.fromReader(__boltffi_reader)
    } finally {
        __boltffi_mid_wire.close()
        __boltffi_ttype_wire.close()
    }
}

/**
 * 检查歌曲是否有曲谱；回值给出五种来源的布尔开关。
 */
fun hasSheetMusic(mid: String): HasSheetMusicResponse {
    val __boltffi_mid_wire = WireWriterPool.acquire(4 + Utf8Codec.maxBytes(mid))
    val __boltffi_mid_writer = __boltffi_mid_wire.writer
    __boltffi_mid_writer.writeString(mid)
    try {
        val __boltffi_result = try { Native.boltffi_function_qqmusic_api_helper_next_port_song_related_has_sheet_music(__boltffi_mid_wire.directBuffer(), __boltffi_mid_wire.size()) } catch (__boltffi_error: BoltFfiErrorBufferException) { run { val __boltffi_error_reader = WireReader(__boltffi_error.bytes); throw HelperError.fromReader(__boltffi_error_reader) } } ?: throw IllegalStateException("null buffer returned")
        val __boltffi_reader = WireReader(__boltffi_result)
        return HasSheetMusicResponse.fromReader(__boltffi_reader)
    } finally {
        __boltffi_mid_wire.close()
    }
}

/**
 * 收藏的外部歌单一页；空列表表示这个账号没有收藏歌单。
 */
fun fetchFavPlaylists(euin: String?, page: Long?, num: Long?): UserFavSonglistResponse {
    val __boltffi_euin_wire = WireWriterPool.acquire(1 + (euin?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0))
    val __boltffi_euin_writer = __boltffi_euin_wire.writer
    __boltffi_euin_writer.writeOptionalValue(euin, { __boltffi_euin_writer, __boltffi_value_0 -> __boltffi_euin_writer.writeString(__boltffi_value_0) })
    val __boltffi_page_wire = WireWriterPool.acquire(if (page == null) 1 else 9)
    val __boltffi_page_writer = __boltffi_page_wire.writer
    __boltffi_page_writer.writeOptionalI64(page)
    val __boltffi_num_wire = WireWriterPool.acquire(if (num == null) 1 else 9)
    val __boltffi_num_writer = __boltffi_num_wire.writer
    __boltffi_num_writer.writeOptionalI64(num)
    try {
        val __boltffi_result = try { Native.boltffi_function_qqmusic_api_helper_next_port_user_asset_fetch_fav_playlists(__boltffi_euin_wire.directBuffer(), __boltffi_euin_wire.size(), __boltffi_page_wire.directBuffer(), __boltffi_page_wire.size(), __boltffi_num_wire.directBuffer(), __boltffi_num_wire.size()) } catch (__boltffi_error: BoltFfiErrorBufferException) { run { val __boltffi_error_reader = WireReader(__boltffi_error.bytes); throw HelperError.fromReader(__boltffi_error_reader) } } ?: throw IllegalStateException("null buffer returned")
        val __boltffi_reader = WireReader(__boltffi_result)
        return UserFavSonglistResponse.fromReader(__boltffi_reader)
    } finally {
        __boltffi_euin_wire.close()
        __boltffi_page_wire.close()
        __boltffi_num_wire.close()
    }
}

/**
 * 收藏的专辑一页；`total` / `hasmore` 用来翻页。
 */
fun fetchFavAlbums(euin: String?, page: Long?, num: Long?): UserFavAlbumResponse {
    val __boltffi_euin_wire = WireWriterPool.acquire(1 + (euin?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0))
    val __boltffi_euin_writer = __boltffi_euin_wire.writer
    __boltffi_euin_writer.writeOptionalValue(euin, { __boltffi_euin_writer, __boltffi_value_0 -> __boltffi_euin_writer.writeString(__boltffi_value_0) })
    val __boltffi_page_wire = WireWriterPool.acquire(if (page == null) 1 else 9)
    val __boltffi_page_writer = __boltffi_page_wire.writer
    __boltffi_page_writer.writeOptionalI64(page)
    val __boltffi_num_wire = WireWriterPool.acquire(if (num == null) 1 else 9)
    val __boltffi_num_writer = __boltffi_num_wire.writer
    __boltffi_num_writer.writeOptionalI64(num)
    try {
        val __boltffi_result = try { Native.boltffi_function_qqmusic_api_helper_next_port_user_asset_fetch_fav_albums(__boltffi_euin_wire.directBuffer(), __boltffi_euin_wire.size(), __boltffi_page_wire.directBuffer(), __boltffi_page_wire.size(), __boltffi_num_wire.directBuffer(), __boltffi_num_wire.size()) } catch (__boltffi_error: BoltFfiErrorBufferException) { run { val __boltffi_error_reader = WireReader(__boltffi_error.bytes); throw HelperError.fromReader(__boltffi_error_reader) } } ?: throw IllegalStateException("null buffer returned")
        val __boltffi_reader = WireReader(__boltffi_result)
        return UserFavAlbumResponse.fromReader(__boltffi_reader)
    } finally {
        __boltffi_euin_wire.close()
        __boltffi_page_wire.close()
        __boltffi_num_wire.close()
    }
}

/**
 * 收藏的 MV 一页；需要登录，`num` 在协议上是页码减一。
 */
fun fetchFavMvs(euin: String?, page: Long?, num: Long?): UserFavMvResponse {
    val __boltffi_euin_wire = WireWriterPool.acquire(1 + (euin?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0))
    val __boltffi_euin_writer = __boltffi_euin_wire.writer
    __boltffi_euin_writer.writeOptionalValue(euin, { __boltffi_euin_writer, __boltffi_value_0 -> __boltffi_euin_writer.writeString(__boltffi_value_0) })
    val __boltffi_page_wire = WireWriterPool.acquire(if (page == null) 1 else 9)
    val __boltffi_page_writer = __boltffi_page_wire.writer
    __boltffi_page_writer.writeOptionalI64(page)
    val __boltffi_num_wire = WireWriterPool.acquire(if (num == null) 1 else 9)
    val __boltffi_num_writer = __boltffi_num_wire.writer
    __boltffi_num_writer.writeOptionalI64(num)
    try {
        val __boltffi_result = try { Native.boltffi_function_qqmusic_api_helper_next_port_user_asset_fetch_fav_mvs(__boltffi_euin_wire.directBuffer(), __boltffi_euin_wire.size(), __boltffi_page_wire.directBuffer(), __boltffi_page_wire.size(), __boltffi_num_wire.directBuffer(), __boltffi_num_wire.size()) } catch (__boltffi_error: BoltFfiErrorBufferException) { run { val __boltffi_error_reader = WireReader(__boltffi_error.bytes); throw HelperError.fromReader(__boltffi_error_reader) } } ?: throw IllegalStateException("null buffer returned")
        val __boltffi_reader = WireReader(__boltffi_result)
        return UserFavMvResponse.fromReader(__boltffi_reader)
    } finally {
        __boltffi_euin_wire.close()
        __boltffi_page_wire.close()
        __boltffi_num_wire.close()
    }
}

/**
 * 音乐基因；`euin` 指要看的账号，不给就是本账号。
 */
fun fetchMusicGene(euin: String?): UserMusicGeneResponse {
    val __boltffi_euin_wire = WireWriterPool.acquire(1 + (euin?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0))
    val __boltffi_euin_writer = __boltffi_euin_wire.writer
    __boltffi_euin_writer.writeOptionalValue(euin, { __boltffi_euin_writer, __boltffi_value_0 -> __boltffi_euin_writer.writeString(__boltffi_value_0) })
    try {
        val __boltffi_result = try { Native.boltffi_function_qqmusic_api_helper_next_port_user_asset_fetch_music_gene(__boltffi_euin_wire.directBuffer(), __boltffi_euin_wire.size()) } catch (__boltffi_error: BoltFfiErrorBufferException) { run { val __boltffi_error_reader = WireReader(__boltffi_error.bytes); throw HelperError.fromReader(__boltffi_error_reader) } } ?: throw IllegalStateException("null buffer returned")
        val __boltffi_reader = WireReader(__boltffi_result)
        return UserMusicGeneResponse.fromReader(__boltffi_reader)
    } finally {
        __boltffi_euin_wire.close()
    }
}

/**
 * 不喜欢列表一页；`cmd`: 2=歌手 3=歌曲 4=风格，`lastid` 是上一页最后一项的 ID。
 */
fun fetchDislikeList(cmd: Long?, page: Long?, lastid: Long?): DislikeListData {
    val __boltffi_cmd_wire = WireWriterPool.acquire(if (cmd == null) 1 else 9)
    val __boltffi_cmd_writer = __boltffi_cmd_wire.writer
    __boltffi_cmd_writer.writeOptionalI64(cmd)
    val __boltffi_page_wire = WireWriterPool.acquire(if (page == null) 1 else 9)
    val __boltffi_page_writer = __boltffi_page_wire.writer
    __boltffi_page_writer.writeOptionalI64(page)
    val __boltffi_lastid_wire = WireWriterPool.acquire(if (lastid == null) 1 else 9)
    val __boltffi_lastid_writer = __boltffi_lastid_wire.writer
    __boltffi_lastid_writer.writeOptionalI64(lastid)
    try {
        val __boltffi_result = try { Native.boltffi_function_qqmusic_api_helper_next_port_user_asset_fetch_dislike_list(__boltffi_cmd_wire.directBuffer(), __boltffi_cmd_wire.size(), __boltffi_page_wire.directBuffer(), __boltffi_page_wire.size(), __boltffi_lastid_wire.directBuffer(), __boltffi_lastid_wire.size()) } catch (__boltffi_error: BoltFfiErrorBufferException) { run { val __boltffi_error_reader = WireReader(__boltffi_error.bytes); throw HelperError.fromReader(__boltffi_error_reader) } } ?: throw IllegalStateException("null buffer returned")
        val __boltffi_reader = WireReader(__boltffi_result)
        return DislikeListData.fromReader(__boltffi_reader)
    } finally {
        __boltffi_cmd_wire.close()
        __boltffi_page_wire.close()
        __boltffi_lastid_wire.close()
    }
}

/**
 * 添加不喜欢；`id_type`: 1=歌曲 2=歌手 3=风格。返回是否操作成功。
 */
fun addDislike(idType: Long, values: LongArray): Boolean {
    return try { Native.boltffi_function_qqmusic_api_helper_next_port_user_asset_add_dislike(idType, values) } catch (__boltffi_error: BoltFfiErrorBufferException) { run { val __boltffi_error_reader = WireReader(__boltffi_error.bytes); throw HelperError.fromReader(__boltffi_error_reader) } }
}

/**
 * 取消不喜欢；`id_type`: 1=歌曲 2=歌手 3=风格。返回是否操作成功。
 */
fun cancelDislike(idType: Long, values: LongArray): Boolean {
    return try { Native.boltffi_function_qqmusic_api_helper_next_port_user_asset_cancel_dislike(idType, values) } catch (__boltffi_error: BoltFfiErrorBufferException) { run { val __boltffi_error_reader = WireReader(__boltffi_error.bytes); throw HelperError.fromReader(__boltffi_error_reader) } }
}

/**
 * 清空所有不喜欢歌曲（两步：先取 Token 再删除）。返回是否操作成功。
 */
fun clearDislikeSongs(): Boolean {
    return try { Native.boltffi_function_qqmusic_api_helper_next_port_user_asset_clear_dislike_songs() } catch (__boltffi_error: BoltFfiErrorBufferException) { run { val __boltffi_error_reader = WireReader(__boltffi_error.bytes); throw HelperError.fromReader(__boltffi_error_reader) } }
}

/**
 * 用户主页；`euin` 不给时退回凭据的加密 uin。
 */
fun fetchUserHomepage(euin: String?): UserHomepage {
    val __boltffi_euin_wire = WireWriterPool.acquire(1 + (euin?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0))
    val __boltffi_euin_writer = __boltffi_euin_wire.writer
    __boltffi_euin_writer.writeOptionalValue(euin, { __boltffi_euin_writer, __boltffi_value_0 -> __boltffi_euin_writer.writeString(__boltffi_value_0) })
    try {
        val __boltffi_result = try { Native.boltffi_function_qqmusic_api_helper_next_port_user_relation_fetch_user_homepage(__boltffi_euin_wire.directBuffer(), __boltffi_euin_wire.size()) } catch (__boltffi_error: BoltFfiErrorBufferException) { run { val __boltffi_error_reader = WireReader(__boltffi_error.bytes); throw HelperError.fromReader(__boltffi_error_reader) } } ?: throw IllegalStateException("null buffer returned")
        val __boltffi_reader = WireReader(__boltffi_result)
        return UserHomepage.fromReader(__boltffi_reader)
    } finally {
        __boltffi_euin_wire.close()
    }
}

/**
 * 当前登录账号的 VIP 信息；需要登录。
 */
fun fetchVipInfo(): UserVipInfo {
    val __boltffi_result = try { Native.boltffi_function_qqmusic_api_helper_next_port_user_relation_fetch_vip_info() } catch (__boltffi_error: BoltFfiErrorBufferException) { run { val __boltffi_error_reader = WireReader(__boltffi_error.bytes); throw HelperError.fromReader(__boltffi_error_reader) } } ?: throw IllegalStateException("null buffer returned")
    val __boltffi_reader = WireReader(__boltffi_result)
    return UserVipInfo.fromReader(__boltffi_reader)
}

/**
 * 某个账号关注的歌手一页；需要登录，`total` / `hasMore` 用来翻页。
 */
fun fetchFollowSingers(euin: String?, page: Long?, num: Long?): UserRelationList {
    val __boltffi_euin_wire = WireWriterPool.acquire(1 + (euin?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0))
    val __boltffi_euin_writer = __boltffi_euin_wire.writer
    __boltffi_euin_writer.writeOptionalValue(euin, { __boltffi_euin_writer, __boltffi_value_0 -> __boltffi_euin_writer.writeString(__boltffi_value_0) })
    val __boltffi_page_wire = WireWriterPool.acquire(if (page == null) 1 else 9)
    val __boltffi_page_writer = __boltffi_page_wire.writer
    __boltffi_page_writer.writeOptionalI64(page)
    val __boltffi_num_wire = WireWriterPool.acquire(if (num == null) 1 else 9)
    val __boltffi_num_writer = __boltffi_num_wire.writer
    __boltffi_num_writer.writeOptionalI64(num)
    try {
        val __boltffi_result = try { Native.boltffi_function_qqmusic_api_helper_next_port_user_relation_fetch_follow_singers(__boltffi_euin_wire.directBuffer(), __boltffi_euin_wire.size(), __boltffi_page_wire.directBuffer(), __boltffi_page_wire.size(), __boltffi_num_wire.directBuffer(), __boltffi_num_wire.size()) } catch (__boltffi_error: BoltFfiErrorBufferException) { run { val __boltffi_error_reader = WireReader(__boltffi_error.bytes); throw HelperError.fromReader(__boltffi_error_reader) } } ?: throw IllegalStateException("null buffer returned")
        val __boltffi_reader = WireReader(__boltffi_result)
        return UserRelationList.fromReader(__boltffi_reader)
    } finally {
        __boltffi_euin_wire.close()
        __boltffi_page_wire.close()
        __boltffi_num_wire.close()
    }
}

/**
 * 某个账号关注的歌手一页，直接按已加载行数发 `From`；保留总数用于继续翻页。
 */
fun fetchFollowSingersAtOffset(euin: String?, offset: Long, num: Long?): UserRelationList {
    val __boltffi_euin_wire = WireWriterPool.acquire(1 + (euin?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0))
    val __boltffi_euin_writer = __boltffi_euin_wire.writer
    __boltffi_euin_writer.writeOptionalValue(euin, { __boltffi_euin_writer, __boltffi_value_0 -> __boltffi_euin_writer.writeString(__boltffi_value_0) })
    val __boltffi_num_wire = WireWriterPool.acquire(if (num == null) 1 else 9)
    val __boltffi_num_writer = __boltffi_num_wire.writer
    __boltffi_num_writer.writeOptionalI64(num)
    try {
        val __boltffi_result = try { Native.boltffi_function_qqmusic_api_helper_next_port_user_relation_fetch_follow_singers_at_offset(__boltffi_euin_wire.directBuffer(), __boltffi_euin_wire.size(), offset, __boltffi_num_wire.directBuffer(), __boltffi_num_wire.size()) } catch (__boltffi_error: BoltFfiErrorBufferException) { run { val __boltffi_error_reader = WireReader(__boltffi_error.bytes); throw HelperError.fromReader(__boltffi_error_reader) } } ?: throw IllegalStateException("null buffer returned")
        val __boltffi_reader = WireReader(__boltffi_result)
        return UserRelationList.fromReader(__boltffi_reader)
    } finally {
        __boltffi_euin_wire.close()
        __boltffi_num_wire.close()
    }
}

/**
 * 某个账号的粉丝一页；需要登录。
 */
fun fetchFans(euin: String?, page: Long?, num: Long?): UserRelationList {
    val __boltffi_euin_wire = WireWriterPool.acquire(1 + (euin?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0))
    val __boltffi_euin_writer = __boltffi_euin_wire.writer
    __boltffi_euin_writer.writeOptionalValue(euin, { __boltffi_euin_writer, __boltffi_value_0 -> __boltffi_euin_writer.writeString(__boltffi_value_0) })
    val __boltffi_page_wire = WireWriterPool.acquire(if (page == null) 1 else 9)
    val __boltffi_page_writer = __boltffi_page_wire.writer
    __boltffi_page_writer.writeOptionalI64(page)
    val __boltffi_num_wire = WireWriterPool.acquire(if (num == null) 1 else 9)
    val __boltffi_num_writer = __boltffi_num_wire.writer
    __boltffi_num_writer.writeOptionalI64(num)
    try {
        val __boltffi_result = try { Native.boltffi_function_qqmusic_api_helper_next_port_user_relation_fetch_fans(__boltffi_euin_wire.directBuffer(), __boltffi_euin_wire.size(), __boltffi_page_wire.directBuffer(), __boltffi_page_wire.size(), __boltffi_num_wire.directBuffer(), __boltffi_num_wire.size()) } catch (__boltffi_error: BoltFfiErrorBufferException) { run { val __boltffi_error_reader = WireReader(__boltffi_error.bytes); throw HelperError.fromReader(__boltffi_error_reader) } } ?: throw IllegalStateException("null buffer returned")
        val __boltffi_reader = WireReader(__boltffi_result)
        return UserRelationList.fromReader(__boltffi_reader)
    } finally {
        __boltffi_euin_wire.close()
        __boltffi_page_wire.close()
        __boltffi_num_wire.close()
    }
}

/**
 * 好友一页；需要登录，`hasMore` 用来翻页。
 */
fun fetchFriends(page: Long?, num: Long?): UserFriendList {
    val __boltffi_page_wire = WireWriterPool.acquire(if (page == null) 1 else 9)
    val __boltffi_page_writer = __boltffi_page_wire.writer
    __boltffi_page_writer.writeOptionalI64(page)
    val __boltffi_num_wire = WireWriterPool.acquire(if (num == null) 1 else 9)
    val __boltffi_num_writer = __boltffi_num_wire.writer
    __boltffi_num_writer.writeOptionalI64(num)
    try {
        val __boltffi_result = try { Native.boltffi_function_qqmusic_api_helper_next_port_user_relation_fetch_friends(__boltffi_page_wire.directBuffer(), __boltffi_page_wire.size(), __boltffi_num_wire.directBuffer(), __boltffi_num_wire.size()) } catch (__boltffi_error: BoltFfiErrorBufferException) { run { val __boltffi_error_reader = WireReader(__boltffi_error.bytes); throw HelperError.fromReader(__boltffi_error_reader) } } ?: throw IllegalStateException("null buffer returned")
        val __boltffi_reader = WireReader(__boltffi_result)
        return UserFriendList.fromReader(__boltffi_reader)
    } finally {
        __boltffi_page_wire.close()
        __boltffi_num_wire.close()
    }
}

/**
 * 某个账号关注的人一页；需要登录。
 */
fun fetchFollowedUsers(euin: String?, page: Long?, num: Long?): UserRelationList {
    val __boltffi_euin_wire = WireWriterPool.acquire(1 + (euin?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0))
    val __boltffi_euin_writer = __boltffi_euin_wire.writer
    __boltffi_euin_writer.writeOptionalValue(euin, { __boltffi_euin_writer, __boltffi_value_0 -> __boltffi_euin_writer.writeString(__boltffi_value_0) })
    val __boltffi_page_wire = WireWriterPool.acquire(if (page == null) 1 else 9)
    val __boltffi_page_writer = __boltffi_page_wire.writer
    __boltffi_page_writer.writeOptionalI64(page)
    val __boltffi_num_wire = WireWriterPool.acquire(if (num == null) 1 else 9)
    val __boltffi_num_writer = __boltffi_num_wire.writer
    __boltffi_num_writer.writeOptionalI64(num)
    try {
        val __boltffi_result = try { Native.boltffi_function_qqmusic_api_helper_next_port_user_relation_fetch_followed_users(__boltffi_euin_wire.directBuffer(), __boltffi_euin_wire.size(), __boltffi_page_wire.directBuffer(), __boltffi_page_wire.size(), __boltffi_num_wire.directBuffer(), __boltffi_num_wire.size()) } catch (__boltffi_error: BoltFfiErrorBufferException) { run { val __boltffi_error_reader = WireReader(__boltffi_error.bytes); throw HelperError.fromReader(__boltffi_error_reader) } } ?: throw IllegalStateException("null buffer returned")
        val __boltffi_reader = WireReader(__boltffi_result)
        return UserRelationList.fromReader(__boltffi_reader)
    } finally {
        __boltffi_euin_wire.close()
        __boltffi_page_wire.close()
        __boltffi_num_wire.close()
    }
}

/**
 * 某个账号创建的歌单一览；`uin` 不给时读当前登录账号的。
 */
fun fetchCreatedPlaylists(uin: Long?): UserCreatedSonglistResponse {
    val __boltffi_uin_wire = WireWriterPool.acquire(if (uin == null) 1 else 9)
    val __boltffi_uin_writer = __boltffi_uin_wire.writer
    __boltffi_uin_writer.writeOptionalI64(uin)
    try {
        val __boltffi_result = try { Native.boltffi_function_qqmusic_api_helper_next_port_user_relation_fetch_created_playlists(__boltffi_uin_wire.directBuffer(), __boltffi_uin_wire.size()) } catch (__boltffi_error: BoltFfiErrorBufferException) { run { val __boltffi_error_reader = WireReader(__boltffi_error.bytes); throw HelperError.fromReader(__boltffi_error_reader) } } ?: throw IllegalStateException("null buffer returned")
        val __boltffi_reader = WireReader(__boltffi_result)
        return UserCreatedSonglistResponse.fromReader(__boltffi_reader)
    } finally {
        __boltffi_uin_wire.close()
    }
}

/**
 * Who is logged in. Answers from the upstream, so an expired session shows up
 * as `logged_in: false` rather than as a failure.
 */
fun loginStatus(): LoginStatus {
    val __boltffi_result = try { Native.boltffi_function_qqmusic_api_helper_next_api_login_status() } catch (__boltffi_error: BoltFfiErrorBufferException) { run { val __boltffi_error_reader = WireReader(__boltffi_error.bytes); throw HelperError.fromReader(__boltffi_error_reader) } } ?: throw IllegalStateException("null buffer returned")
    val __boltffi_reader = WireReader(__boltffi_result)
    return LoginStatus.fromReader(__boltffi_reader)
}

/**
 * Store a login from the two cookies a web login produces.
 *
 * `qm_keyst` is both the session ticket and the CDN playback ticket, so this
 * pair *is* a complete login — no other cookie matters, which is why the
 * signature takes these two rather than a cookie jar. (A `Vec<(String, String)>`
 * also happens to be the one shape the binding generator cannot render, so the
 * two-field form is what hosts on both platforms actually get.)
 */
fun importCredential(uin: String, qmKeyst: String) {
    val __boltffi_uin_wire = WireWriterPool.acquire(4 + Utf8Codec.maxBytes(uin))
    val __boltffi_uin_writer = __boltffi_uin_wire.writer
    __boltffi_uin_writer.writeString(uin)
    val __boltffi_qmKeyst_wire = WireWriterPool.acquire(4 + Utf8Codec.maxBytes(qmKeyst))
    val __boltffi_qmKeyst_writer = __boltffi_qmKeyst_wire.writer
    __boltffi_qmKeyst_writer.writeString(qmKeyst)
    try {
        try { Native.boltffi_function_qqmusic_api_helper_next_api_import_credential(__boltffi_uin_wire.directBuffer(), __boltffi_uin_wire.size(), __boltffi_qmKeyst_wire.directBuffer(), __boltffi_qmKeyst_wire.size()) } catch (__boltffi_error: BoltFfiErrorBufferException) { run { val __boltffi_error_reader = WireReader(__boltffi_error.bytes); throw HelperError.fromReader(__boltffi_error_reader) } }
    } finally {
        __boltffi_uin_wire.close()
        __boltffi_qmKeyst_wire.close()
    }
}

/**
 * Import the same login with an optional encrypted UIN for account relations.
 * Kept separate so the existing two-argument macOS API remains source-compatible.
 */
fun importCredentialWithEncryptUin(uin: String, qmKeyst: String, encryptUin: String?) {
    val __boltffi_uin_wire = WireWriterPool.acquire(4 + Utf8Codec.maxBytes(uin))
    val __boltffi_uin_writer = __boltffi_uin_wire.writer
    __boltffi_uin_writer.writeString(uin)
    val __boltffi_qmKeyst_wire = WireWriterPool.acquire(4 + Utf8Codec.maxBytes(qmKeyst))
    val __boltffi_qmKeyst_writer = __boltffi_qmKeyst_wire.writer
    __boltffi_qmKeyst_writer.writeString(qmKeyst)
    val __boltffi_encryptUin_wire = WireWriterPool.acquire(1 + (encryptUin?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0))
    val __boltffi_encryptUin_writer = __boltffi_encryptUin_wire.writer
    __boltffi_encryptUin_writer.writeOptionalValue(encryptUin, { __boltffi_encryptUin_writer, __boltffi_value_0 -> __boltffi_encryptUin_writer.writeString(__boltffi_value_0) })
    try {
        try { Native.boltffi_function_qqmusic_api_helper_next_api_import_credential_with_encrypt_uin(__boltffi_uin_wire.directBuffer(), __boltffi_uin_wire.size(), __boltffi_qmKeyst_wire.directBuffer(), __boltffi_qmKeyst_wire.size(), __boltffi_encryptUin_wire.directBuffer(), __boltffi_encryptUin_wire.size()) } catch (__boltffi_error: BoltFfiErrorBufferException) { run { val __boltffi_error_reader = WireReader(__boltffi_error.bytes); throw HelperError.fromReader(__boltffi_error_reader) } }
    } finally {
        __boltffi_uin_wire.close()
        __boltffi_qmKeyst_wire.close()
        __boltffi_encryptUin_wire.close()
    }
}

/**
 * Forget the stored login.
 */
fun logout() {
    try { Native.boltffi_function_qqmusic_api_helper_next_api_logout() } catch (__boltffi_error: BoltFfiErrorBufferException) { run { val __boltffi_error_reader = WireReader(__boltffi_error.bytes); throw HelperError.fromReader(__boltffi_error_reader) } }
}

/**
 * 我喜欢, one page at a time.
 */
fun likedSongs(page: UInt, limit: UInt): LikedSongs {
    val __boltffi_result = try { Native.boltffi_function_qqmusic_api_helper_next_api_liked_songs(page.toInt(), limit.toInt()) } catch (__boltffi_error: BoltFfiErrorBufferException) { run { val __boltffi_error_reader = WireReader(__boltffi_error.bytes); throw HelperError.fromReader(__boltffi_error_reader) } } ?: throw IllegalStateException("null buffer returned")
    val __boltffi_reader = WireReader(__boltffi_result)
    return LikedSongs.fromReader(__boltffi_reader)
}

/**
 * A playlist's (or ranking's) tracks. `offset` is a row offset, not a page.
 */
fun playlistTracks(listId: Long, offset: UInt, limit: UInt): List<Track> {
    val __boltffi_result = try { Native.boltffi_function_qqmusic_api_helper_next_api_playlist_tracks(listId, offset.toInt(), limit.toInt()) } catch (__boltffi_error: BoltFfiErrorBufferException) { run { val __boltffi_error_reader = WireReader(__boltffi_error.bytes); throw HelperError.fromReader(__boltffi_error_reader) } } ?: throw IllegalStateException("null buffer returned")
    val __boltffi_reader = WireReader(__boltffi_result)
    return __boltffi_reader.readSequence({ __boltffi_reader -> Track.fromReader(__boltffi_reader) })
}

/**
 * A row-offset page of playlist or liked-folder tracks, with the source total.
 * `dir_id=201` and `list_id=0` addresses the reserved 我喜欢 folder.
 */
fun playlistTracksPage(listId: Long, dirId: Long?, offset: UInt, limit: UInt): TrackPage {
    val __boltffi_dirId_wire = WireWriterPool.acquire(if (dirId == null) 1 else 9)
    val __boltffi_dirId_writer = __boltffi_dirId_wire.writer
    __boltffi_dirId_writer.writeOptionalI64(dirId)
    try {
        val __boltffi_result = try { Native.boltffi_function_qqmusic_api_helper_next_api_playlist_tracks_page(listId, __boltffi_dirId_wire.directBuffer(), __boltffi_dirId_wire.size(), offset.toInt(), limit.toInt()) } catch (__boltffi_error: BoltFfiErrorBufferException) { run { val __boltffi_error_reader = WireReader(__boltffi_error.bytes); throw HelperError.fromReader(__boltffi_error_reader) } } ?: throw IllegalStateException("null buffer returned")
        val __boltffi_reader = WireReader(__boltffi_result)
        return TrackPage.fromReader(__boltffi_reader)
    } finally {
        __boltffi_dirId_wire.close()
    }
}

/**
 * Add or remove a song by numeric id and retain the upstream result code.
 */
fun setLikedById(songId: Long, liked: Boolean): LikeReceipt {
    val __boltffi_result = try { Native.boltffi_function_qqmusic_api_helper_next_api_set_liked_by_id(songId, liked) } catch (__boltffi_error: BoltFfiErrorBufferException) { run { val __boltffi_error_reader = WireReader(__boltffi_error.bytes); throw HelperError.fromReader(__boltffi_error_reader) } } ?: throw IllegalStateException("null buffer returned")
    val __boltffi_reader = WireReader(__boltffi_result)
    return LikeReceipt.fromReader(__boltffi_reader)
}

/**
 * The account's own playlists.
 */
fun userPlaylists(limit: UInt): List<Playlist> {
    val __boltffi_result = try { Native.boltffi_function_qqmusic_api_helper_next_api_user_playlists(limit.toInt()) } catch (__boltffi_error: BoltFfiErrorBufferException) { run { val __boltffi_error_reader = WireReader(__boltffi_error.bytes); throw HelperError.fromReader(__boltffi_error_reader) } } ?: throw IllegalStateException("null buffer returned")
    val __boltffi_reader = WireReader(__boltffi_result)
    return __boltffi_reader.readSequence({ __boltffi_reader -> Playlist.fromReader(__boltffi_reader) })
}

/**
 * The account's favorited albums.
 */
fun likedAlbums(limit: UInt): List<Album> {
    val __boltffi_result = try { Native.boltffi_function_qqmusic_api_helper_next_api_liked_albums(limit.toInt()) } catch (__boltffi_error: BoltFfiErrorBufferException) { run { val __boltffi_error_reader = WireReader(__boltffi_error.bytes); throw HelperError.fromReader(__boltffi_error_reader) } } ?: throw IllegalStateException("null buffer returned")
    val __boltffi_reader = WireReader(__boltffi_result)
    return __boltffi_reader.readSequence({ __boltffi_reader -> Album.fromReader(__boltffi_reader) })
}

/**
 * The singers the account follows.
 */
fun followedArtists(page: UInt, limit: UInt): List<Artist> {
    val __boltffi_result = try { Native.boltffi_function_qqmusic_api_helper_next_api_followed_artists(page.toInt(), limit.toInt()) } catch (__boltffi_error: BoltFfiErrorBufferException) { run { val __boltffi_error_reader = WireReader(__boltffi_error.bytes); throw HelperError.fromReader(__boltffi_error_reader) } } ?: throw IllegalStateException("null buffer returned")
    val __boltffi_reader = WireReader(__boltffi_result)
    return __boltffi_reader.readSequence({ __boltffi_reader -> Artist.fromReader(__boltffi_reader) })
}

/**
 * What the component is, and every method it serves.
 */
fun componentInfo(): ComponentInfo {
    val __boltffi_result = try { Native.boltffi_function_qqmusic_api_helper_next_api_component_info() } catch (__boltffi_error: BoltFfiErrorBufferException) { run { val __boltffi_error_reader = WireReader(__boltffi_error.bytes); throw HelperError.fromReader(__boltffi_error_reader) } } ?: throw IllegalStateException("null buffer returned")
    val __boltffi_reader = WireReader(__boltffi_result)
    return ComponentInfo.fromReader(__boltffi_reader)
}

/**
 * The rate limiter's usage and the breaker's state.
 */
fun guardStatus(): GuardStatus {
    val __boltffi_result = try { Native.boltffi_function_qqmusic_api_helper_next_api_guard_status() } catch (__boltffi_error: BoltFfiErrorBufferException) { run { val __boltffi_error_reader = WireReader(__boltffi_error.bytes); throw HelperError.fromReader(__boltffi_error_reader) } } ?: throw IllegalStateException("null buffer returned")
    val __boltffi_reader = WireReader(__boltffi_result)
    return GuardStatus.fromReader(__boltffi_reader)
}

/**
 * A song's catalogue entry, including its 简介 (empty when it has none).
 */
fun songDetail(songMid: String): SongDetail {
    val __boltffi_songMid_wire = WireWriterPool.acquire(4 + Utf8Codec.maxBytes(songMid))
    val __boltffi_songMid_writer = __boltffi_songMid_wire.writer
    __boltffi_songMid_writer.writeString(songMid)
    try {
        val __boltffi_result = try { Native.boltffi_function_qqmusic_api_helper_next_api_song_detail(__boltffi_songMid_wire.directBuffer(), __boltffi_songMid_wire.size()) } catch (__boltffi_error: BoltFfiErrorBufferException) { run { val __boltffi_error_reader = WireReader(__boltffi_error.bytes); throw HelperError.fromReader(__boltffi_error_reader) } } ?: throw IllegalStateException("null buffer returned")
        val __boltffi_reader = WireReader(__boltffi_result)
        return SongDetail.fromReader(__boltffi_reader)
    } finally {
        __boltffi_songMid_wire.close()
    }
}

/**
 * An album's catalogue entry. Address it by mid or by its numeric id.
 */
fun albumDetail(albumMid: String?, albumId: Long?): AlbumDetail {
    val __boltffi_albumMid_wire = WireWriterPool.acquire(1 + (albumMid?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0))
    val __boltffi_albumMid_writer = __boltffi_albumMid_wire.writer
    __boltffi_albumMid_writer.writeOptionalValue(albumMid, { __boltffi_albumMid_writer, __boltffi_value_0 -> __boltffi_albumMid_writer.writeString(__boltffi_value_0) })
    val __boltffi_albumId_wire = WireWriterPool.acquire(if (albumId == null) 1 else 9)
    val __boltffi_albumId_writer = __boltffi_albumId_wire.writer
    __boltffi_albumId_writer.writeOptionalI64(albumId)
    try {
        val __boltffi_result = try { Native.boltffi_function_qqmusic_api_helper_next_api_album_detail(__boltffi_albumMid_wire.directBuffer(), __boltffi_albumMid_wire.size(), __boltffi_albumId_wire.directBuffer(), __boltffi_albumId_wire.size()) } catch (__boltffi_error: BoltFfiErrorBufferException) { run { val __boltffi_error_reader = WireReader(__boltffi_error.bytes); throw HelperError.fromReader(__boltffi_error_reader) } } ?: throw IllegalStateException("null buffer returned")
        val __boltffi_reader = WireReader(__boltffi_result)
        return AlbumDetail.fromReader(__boltffi_reader)
    } finally {
        __boltffi_albumMid_wire.close()
        __boltffi_albumId_wire.close()
    }
}

/**
 * An album's tracks.
 */
fun albumTracks(albumMid: String?, albumId: Long?, offset: Long, limit: Long): TrackPage {
    val __boltffi_albumMid_wire = WireWriterPool.acquire(1 + (albumMid?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0))
    val __boltffi_albumMid_writer = __boltffi_albumMid_wire.writer
    __boltffi_albumMid_writer.writeOptionalValue(albumMid, { __boltffi_albumMid_writer, __boltffi_value_0 -> __boltffi_albumMid_writer.writeString(__boltffi_value_0) })
    val __boltffi_albumId_wire = WireWriterPool.acquire(if (albumId == null) 1 else 9)
    val __boltffi_albumId_writer = __boltffi_albumId_wire.writer
    __boltffi_albumId_writer.writeOptionalI64(albumId)
    try {
        val __boltffi_result = try { Native.boltffi_function_qqmusic_api_helper_next_api_album_tracks(__boltffi_albumMid_wire.directBuffer(), __boltffi_albumMid_wire.size(), __boltffi_albumId_wire.directBuffer(), __boltffi_albumId_wire.size(), offset, limit) } catch (__boltffi_error: BoltFfiErrorBufferException) { run { val __boltffi_error_reader = WireReader(__boltffi_error.bytes); throw HelperError.fromReader(__boltffi_error_reader) } } ?: throw IllegalStateException("null buffer returned")
        val __boltffi_reader = WireReader(__boltffi_result)
        return TrackPage.fromReader(__boltffi_reader)
    } finally {
        __boltffi_albumMid_wire.close()
        __boltffi_albumId_wire.close()
    }
}

/**
 * An artist's songs. `sort` is `hot` or `latest` — both go to the upstream's
 * own global ordering (`order=1` / `order=2`); nothing is sorted locally.
 */
fun artistSongs(singerMid: String, sort: String, page: Long, limit: Long): List<Track> {
    val __boltffi_singerMid_wire = WireWriterPool.acquire(4 + Utf8Codec.maxBytes(singerMid))
    val __boltffi_singerMid_writer = __boltffi_singerMid_wire.writer
    __boltffi_singerMid_writer.writeString(singerMid)
    val __boltffi_sort_wire = WireWriterPool.acquire(4 + Utf8Codec.maxBytes(sort))
    val __boltffi_sort_writer = __boltffi_sort_wire.writer
    __boltffi_sort_writer.writeString(sort)
    try {
        val __boltffi_result = try { Native.boltffi_function_qqmusic_api_helper_next_api_artist_songs(__boltffi_singerMid_wire.directBuffer(), __boltffi_singerMid_wire.size(), __boltffi_sort_wire.directBuffer(), __boltffi_sort_wire.size(), page, limit) } catch (__boltffi_error: BoltFfiErrorBufferException) { run { val __boltffi_error_reader = WireReader(__boltffi_error.bytes); throw HelperError.fromReader(__boltffi_error_reader) } } ?: throw IllegalStateException("null buffer returned")
        val __boltffi_reader = WireReader(__boltffi_result)
        return __boltffi_reader.readSequence({ __boltffi_reader -> Track.fromReader(__boltffi_reader) })
    } finally {
        __boltffi_singerMid_wire.close()
        __boltffi_sort_wire.close()
    }
}

/**
 * An artist's globally ordered, row-offset song page with the source total.
 */
fun artistSongsPage(singerMid: String, sort: String, offset: UInt, limit: UInt): TrackPage {
    val __boltffi_singerMid_wire = WireWriterPool.acquire(4 + Utf8Codec.maxBytes(singerMid))
    val __boltffi_singerMid_writer = __boltffi_singerMid_wire.writer
    __boltffi_singerMid_writer.writeString(singerMid)
    val __boltffi_sort_wire = WireWriterPool.acquire(4 + Utf8Codec.maxBytes(sort))
    val __boltffi_sort_writer = __boltffi_sort_wire.writer
    __boltffi_sort_writer.writeString(sort)
    try {
        val __boltffi_result = try { Native.boltffi_function_qqmusic_api_helper_next_api_artist_songs_page(__boltffi_singerMid_wire.directBuffer(), __boltffi_singerMid_wire.size(), __boltffi_sort_wire.directBuffer(), __boltffi_sort_wire.size(), offset.toInt(), limit.toInt()) } catch (__boltffi_error: BoltFfiErrorBufferException) { run { val __boltffi_error_reader = WireReader(__boltffi_error.bytes); throw HelperError.fromReader(__boltffi_error_reader) } } ?: throw IllegalStateException("null buffer returned")
        val __boltffi_reader = WireReader(__boltffi_result)
        return TrackPage.fromReader(__boltffi_reader)
    } finally {
        __boltffi_singerMid_wire.close()
        __boltffi_sort_wire.close()
    }
}

/**
 * An artist's albums, same two sorts.
 */
fun artistAlbums(singerMid: String, sort: String, page: Long, limit: Long): List<Album> {
    val __boltffi_singerMid_wire = WireWriterPool.acquire(4 + Utf8Codec.maxBytes(singerMid))
    val __boltffi_singerMid_writer = __boltffi_singerMid_wire.writer
    __boltffi_singerMid_writer.writeString(singerMid)
    val __boltffi_sort_wire = WireWriterPool.acquire(4 + Utf8Codec.maxBytes(sort))
    val __boltffi_sort_writer = __boltffi_sort_wire.writer
    __boltffi_sort_writer.writeString(sort)
    try {
        val __boltffi_result = try { Native.boltffi_function_qqmusic_api_helper_next_api_artist_albums(__boltffi_singerMid_wire.directBuffer(), __boltffi_singerMid_wire.size(), __boltffi_sort_wire.directBuffer(), __boltffi_sort_wire.size(), page, limit) } catch (__boltffi_error: BoltFfiErrorBufferException) { run { val __boltffi_error_reader = WireReader(__boltffi_error.bytes); throw HelperError.fromReader(__boltffi_error_reader) } } ?: throw IllegalStateException("null buffer returned")
        val __boltffi_reader = WireReader(__boltffi_result)
        return __boltffi_reader.readSequence({ __boltffi_reader -> Album.fromReader(__boltffi_reader) })
    } finally {
        __boltffi_singerMid_wire.close()
        __boltffi_sort_wire.close()
    }
}

/**
 * An artist's globally ordered album page, with total and song counts.
 */
fun artistAlbumsPage(singerMid: String, sort: String, offset: UInt, limit: UInt): AlbumPage {
    val __boltffi_singerMid_wire = WireWriterPool.acquire(4 + Utf8Codec.maxBytes(singerMid))
    val __boltffi_singerMid_writer = __boltffi_singerMid_wire.writer
    __boltffi_singerMid_writer.writeString(singerMid)
    val __boltffi_sort_wire = WireWriterPool.acquire(4 + Utf8Codec.maxBytes(sort))
    val __boltffi_sort_writer = __boltffi_sort_wire.writer
    __boltffi_sort_writer.writeString(sort)
    try {
        val __boltffi_result = try { Native.boltffi_function_qqmusic_api_helper_next_api_artist_albums_page(__boltffi_singerMid_wire.directBuffer(), __boltffi_singerMid_wire.size(), __boltffi_sort_wire.directBuffer(), __boltffi_sort_wire.size(), offset.toInt(), limit.toInt()) } catch (__boltffi_error: BoltFfiErrorBufferException) { run { val __boltffi_error_reader = WireReader(__boltffi_error.bytes); throw HelperError.fromReader(__boltffi_error_reader) } } ?: throw IllegalStateException("null buffer returned")
        val __boltffi_reader = WireReader(__boltffi_result)
        return AlbumPage.fromReader(__boltffi_reader)
    } finally {
        __boltffi_singerMid_wire.close()
        __boltffi_sort_wire.close()
    }
}

/**
 * An artist's profile and biography.
 */
fun artistDetail(singerMid: String): ArtistDetail {
    val __boltffi_singerMid_wire = WireWriterPool.acquire(4 + Utf8Codec.maxBytes(singerMid))
    val __boltffi_singerMid_writer = __boltffi_singerMid_wire.writer
    __boltffi_singerMid_writer.writeString(singerMid)
    try {
        val __boltffi_result = try { Native.boltffi_function_qqmusic_api_helper_next_api_artist_detail(__boltffi_singerMid_wire.directBuffer(), __boltffi_singerMid_wire.size()) } catch (__boltffi_error: BoltFfiErrorBufferException) { run { val __boltffi_error_reader = WireReader(__boltffi_error.bytes); throw HelperError.fromReader(__boltffi_error_reader) } } ?: throw IllegalStateException("null buffer returned")
        val __boltffi_reader = WireReader(__boltffi_result)
        return ArtistDetail.fromReader(__boltffi_reader)
    } finally {
        __boltffi_singerMid_wire.close()
    }
}

/**
 * The ranking groups, each with the rankings it contains.
 */
fun toplistCategories(): List<ToplistGroup> {
    val __boltffi_result = try { Native.boltffi_function_qqmusic_api_helper_next_api_toplist_categories() } catch (__boltffi_error: BoltFfiErrorBufferException) { run { val __boltffi_error_reader = WireReader(__boltffi_error.bytes); throw HelperError.fromReader(__boltffi_error_reader) } } ?: throw IllegalStateException("null buffer returned")
    val __boltffi_reader = WireReader(__boltffi_result)
    return __boltffi_reader.readSequence({ __boltffi_reader -> ToplistGroup.fromReader(__boltffi_reader) })
}

/**
 * One ranking's tracks.
 */
fun toplistTracks(topId: Long, offset: Long, limit: Long): List<Track> {
    val __boltffi_result = try { Native.boltffi_function_qqmusic_api_helper_next_api_toplist_tracks(topId, offset, limit) } catch (__boltffi_error: BoltFfiErrorBufferException) { run { val __boltffi_error_reader = WireReader(__boltffi_error.bytes); throw HelperError.fromReader(__boltffi_error_reader) } } ?: throw IllegalStateException("null buffer returned")
    val __boltffi_reader = WireReader(__boltffi_result)
    return __boltffi_reader.readSequence({ __boltffi_reader -> Track.fromReader(__boltffi_reader) })
}

/**
 * The radio groups, each with its stations.
 */
fun radioStations(): List<RadioGroup> {
    val __boltffi_result = try { Native.boltffi_function_qqmusic_api_helper_next_api_radio_stations() } catch (__boltffi_error: BoltFfiErrorBufferException) { run { val __boltffi_error_reader = WireReader(__boltffi_error.bytes); throw HelperError.fromReader(__boltffi_error_reader) } } ?: throw IllegalStateException("null buffer returned")
    val __boltffi_reader = WireReader(__boltffi_result)
    return __boltffi_reader.readSequence({ __boltffi_reader -> RadioGroup.fromReader(__boltffi_reader) })
}

/**
 * A station's next songs (a fresh rotation on every call).
 */
fun radioTracks(stationId: Long, limit: Long, firstPlay: Boolean): List<Track> {
    val __boltffi_result = try { Native.boltffi_function_qqmusic_api_helper_next_api_radio_tracks(stationId, limit, firstPlay) } catch (__boltffi_error: BoltFfiErrorBufferException) { run { val __boltffi_error_reader = WireReader(__boltffi_error.bytes); throw HelperError.fromReader(__boltffi_error_reader) } } ?: throw IllegalStateException("null buffer returned")
    val __boltffi_reader = WireReader(__boltffi_result)
    return __boltffi_reader.readSequence({ __boltffi_reader -> Track.fromReader(__boltffi_reader) })
}

/**
 * Fetch one fresh batch from a QQ Music infinite radio rotation.
 */
fun radioTrackBatch(stationId: Long, firstPlay: Boolean): TrackPage {
    val __boltffi_result = try { Native.boltffi_function_qqmusic_api_helper_next_api_radio_track_batch(stationId, firstPlay) } catch (__boltffi_error: BoltFfiErrorBufferException) { run { val __boltffi_error_reader = WireReader(__boltffi_error.bytes); throw HelperError.fromReader(__boltffi_error_reader) } } ?: throw IllegalStateException("null buffer returned")
    val __boltffi_reader = WireReader(__boltffi_result)
    return TrackPage.fromReader(__boltffi_reader)
}

/**
 * New songs for one region: 0 最新, 1 内地, 2 港台, 3 欧美, 4 日本, 5 韩国.
 */
fun newSongs(regionType: Long): List<Track> {
    val __boltffi_result = try { Native.boltffi_function_qqmusic_api_helper_next_api_new_songs(regionType) } catch (__boltffi_error: BoltFfiErrorBufferException) { run { val __boltffi_error_reader = WireReader(__boltffi_error.bytes); throw HelperError.fromReader(__boltffi_error_reader) } } ?: throw IllegalStateException("null buffer returned")
    val __boltffi_reader = WireReader(__boltffi_result)
    return __boltffi_reader.readSequence({ __boltffi_reader -> Track.fromReader(__boltffi_reader) })
}

/**
 * "Guess you like" — five fresh tracks per call.
 */
fun recommendFeed(): List<Track> {
    val __boltffi_result = try { Native.boltffi_function_qqmusic_api_helper_next_api_recommend_feed() } catch (__boltffi_error: BoltFfiErrorBufferException) { run { val __boltffi_error_reader = WireReader(__boltffi_error.bytes); throw HelperError.fromReader(__boltffi_error_reader) } } ?: throw IllegalStateException("null buffer returned")
    val __boltffi_reader = WireReader(__boltffi_result)
    return __boltffi_reader.readSequence({ __boltffi_reader -> Track.fromReader(__boltffi_reader) })
}

/**
 * A song's lyric. `word_timing` asks for the word-level track; `translation`
 * asks for the translation and romanisation when the service has them.
 */
fun lyric(songMid: String, songId: Long?, wordTiming: Boolean, translation: Boolean): Lyric {
    val __boltffi_songMid_wire = WireWriterPool.acquire(4 + Utf8Codec.maxBytes(songMid))
    val __boltffi_songMid_writer = __boltffi_songMid_wire.writer
    __boltffi_songMid_writer.writeString(songMid)
    val __boltffi_songId_wire = WireWriterPool.acquire(if (songId == null) 1 else 9)
    val __boltffi_songId_writer = __boltffi_songId_wire.writer
    __boltffi_songId_writer.writeOptionalI64(songId)
    try {
        val __boltffi_result = try { Native.boltffi_function_qqmusic_api_helper_next_api_lyric(__boltffi_songMid_wire.directBuffer(), __boltffi_songMid_wire.size(), __boltffi_songId_wire.directBuffer(), __boltffi_songId_wire.size(), wordTiming, translation) } catch (__boltffi_error: BoltFfiErrorBufferException) { run { val __boltffi_error_reader = WireReader(__boltffi_error.bytes); throw HelperError.fromReader(__boltffi_error_reader) } } ?: throw IllegalStateException("null buffer returned")
        val __boltffi_reader = WireReader(__boltffi_result)
        return Lyric.fromReader(__boltffi_reader)
    } finally {
        __boltffi_songMid_wire.close()
        __boltffi_songId_wire.close()
    }
}

/**
 * A playable url for one track, probing the quality ladder best-first.
 *
 * `preferred_quality` narrows the probe to one rung; leaving it unset walks the
 * ladder and returns the first grant. A track the account may not play comes
 * back with `playable: false` and the reason — not as an error.
 */
fun resolveSongUrl(songMid: String, mediaMid: String?, songType: Long, preferredQuality: String?): StreamResolution {
    val __boltffi_songMid_wire = WireWriterPool.acquire(4 + Utf8Codec.maxBytes(songMid))
    val __boltffi_songMid_writer = __boltffi_songMid_wire.writer
    __boltffi_songMid_writer.writeString(songMid)
    val __boltffi_mediaMid_wire = WireWriterPool.acquire(1 + (mediaMid?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0))
    val __boltffi_mediaMid_writer = __boltffi_mediaMid_wire.writer
    __boltffi_mediaMid_writer.writeOptionalValue(mediaMid, { __boltffi_mediaMid_writer, __boltffi_value_0 -> __boltffi_mediaMid_writer.writeString(__boltffi_value_0) })
    val __boltffi_preferredQuality_wire = WireWriterPool.acquire(1 + (preferredQuality?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0))
    val __boltffi_preferredQuality_writer = __boltffi_preferredQuality_wire.writer
    __boltffi_preferredQuality_writer.writeOptionalValue(preferredQuality, { __boltffi_preferredQuality_writer, __boltffi_value_0 -> __boltffi_preferredQuality_writer.writeString(__boltffi_value_0) })
    try {
        val __boltffi_result = try { Native.boltffi_function_qqmusic_api_helper_next_api_resolve_song_url(__boltffi_songMid_wire.directBuffer(), __boltffi_songMid_wire.size(), __boltffi_mediaMid_wire.directBuffer(), __boltffi_mediaMid_wire.size(), songType, __boltffi_preferredQuality_wire.directBuffer(), __boltffi_preferredQuality_wire.size()) } catch (__boltffi_error: BoltFfiErrorBufferException) { run { val __boltffi_error_reader = WireReader(__boltffi_error.bytes); throw HelperError.fromReader(__boltffi_error_reader) } } ?: throw IllegalStateException("null buffer returned")
        val __boltffi_reader = WireReader(__boltffi_result)
        return StreamResolution.fromReader(__boltffi_reader)
    } finally {
        __boltffi_songMid_wire.close()
        __boltffi_mediaMid_wire.close()
        __boltffi_preferredQuality_wire.close()
    }
}

/**
 * Send a request under an explicit platform profile.
 *
 * The typed wrappers above use the host's configured default; this is the
 * documented way to reach the other profile for a single interface without
 * rebuilding. `params_json` is the same object the protocol layer takes, and
 * the result is the raw payload as JSON.
 */
fun callWithPlatform(method: String, paramsJson: String, platform: String): String {
    val __boltffi_method_wire = WireWriterPool.acquire(4 + Utf8Codec.maxBytes(method))
    val __boltffi_method_writer = __boltffi_method_wire.writer
    __boltffi_method_writer.writeString(method)
    val __boltffi_paramsJson_wire = WireWriterPool.acquire(4 + Utf8Codec.maxBytes(paramsJson))
    val __boltffi_paramsJson_writer = __boltffi_paramsJson_wire.writer
    __boltffi_paramsJson_writer.writeString(paramsJson)
    val __boltffi_platform_wire = WireWriterPool.acquire(4 + Utf8Codec.maxBytes(platform))
    val __boltffi_platform_writer = __boltffi_platform_wire.writer
    __boltffi_platform_writer.writeString(platform)
    try {
        val __boltffi_result = try { Native.boltffi_function_qqmusic_api_helper_next_api_call_with_platform(__boltffi_method_wire.directBuffer(), __boltffi_method_wire.size(), __boltffi_paramsJson_wire.directBuffer(), __boltffi_paramsJson_wire.size(), __boltffi_platform_wire.directBuffer(), __boltffi_platform_wire.size()) } catch (__boltffi_error: BoltFfiErrorBufferException) { run { val __boltffi_error_reader = WireReader(__boltffi_error.bytes); throw HelperError.fromReader(__boltffi_error_reader) } } ?: throw IllegalStateException("null buffer returned")
        val __boltffi_reader = WireReader(__boltffi_result)
        return __boltffi_reader.readString()
    } finally {
        __boltffi_method_wire.close()
        __boltffi_paramsJson_wire.close()
        __boltffi_platform_wire.close()
    }
}

/**
 * Add to, or remove from, "我喜欢". The only write this component performs.
 */
fun setLiked(songMid: String, songType: Long, liked: Boolean) {
    val __boltffi_songMid_wire = WireWriterPool.acquire(4 + Utf8Codec.maxBytes(songMid))
    val __boltffi_songMid_writer = __boltffi_songMid_wire.writer
    __boltffi_songMid_writer.writeString(songMid)
    try {
        try { Native.boltffi_function_qqmusic_api_helper_next_api_set_liked(__boltffi_songMid_wire.directBuffer(), __boltffi_songMid_wire.size(), songType, liked) } catch (__boltffi_error: BoltFfiErrorBufferException) { run { val __boltffi_error_reader = WireReader(__boltffi_error.bytes); throw HelperError.fromReader(__boltffi_error_reader) } }
    } finally {
        __boltffi_songMid_wire.close()
    }
}

/**
 * Start a QQ scan-to-login: returns the code to draw and the identifier to
 * poll with.
 */
fun startLogin(): LoginQrCode {
    val __boltffi_result = try { Native.boltffi_function_qqmusic_api_helper_next_api_start_login() } catch (__boltffi_error: BoltFfiErrorBufferException) { run { val __boltffi_error_reader = WireReader(__boltffi_error.bytes); throw HelperError.fromReader(__boltffi_error_reader) } } ?: throw IllegalStateException("null buffer returned")
    val __boltffi_reader = WireReader(__boltffi_result)
    return LoginQrCode.fromReader(__boltffi_reader)
}

/**
 * Ask what the scan has done; on the last step it stores the credential and
 * reports `logged_in`.
 */
fun pollLogin(identifier: String): LoginPoll {
    val __boltffi_identifier_wire = WireWriterPool.acquire(4 + Utf8Codec.maxBytes(identifier))
    val __boltffi_identifier_writer = __boltffi_identifier_wire.writer
    __boltffi_identifier_writer.writeString(identifier)
    try {
        val __boltffi_result = try { Native.boltffi_function_qqmusic_api_helper_next_api_poll_login(__boltffi_identifier_wire.directBuffer(), __boltffi_identifier_wire.size()) } catch (__boltffi_error: BoltFfiErrorBufferException) { run { val __boltffi_error_reader = WireReader(__boltffi_error.bytes); throw HelperError.fromReader(__boltffi_error_reader) } } ?: throw IllegalStateException("null buffer returned")
        val __boltffi_reader = WireReader(__boltffi_result)
        return LoginPoll.fromReader(__boltffi_reader)
    } finally {
        __boltffi_identifier_wire.close()
    }
}

fun searchSongs(keyword: String, page: Long, limit: Long): TrackSearch {
    val __boltffi_keyword_wire = WireWriterPool.acquire(4 + Utf8Codec.maxBytes(keyword))
    val __boltffi_keyword_writer = __boltffi_keyword_wire.writer
    __boltffi_keyword_writer.writeString(keyword)
    try {
        val __boltffi_result = try { Native.boltffi_function_qqmusic_api_helper_next_api_search_songs(__boltffi_keyword_wire.directBuffer(), __boltffi_keyword_wire.size(), page, limit) } catch (__boltffi_error: BoltFfiErrorBufferException) { run { val __boltffi_error_reader = WireReader(__boltffi_error.bytes); throw HelperError.fromReader(__boltffi_error_reader) } } ?: throw IllegalStateException("null buffer returned")
        val __boltffi_reader = WireReader(__boltffi_result)
        return TrackSearch.fromReader(__boltffi_reader)
    } finally {
        __boltffi_keyword_wire.close()
    }
}

fun searchArtists(keyword: String, page: Long, limit: Long): ArtistSearch {
    val __boltffi_keyword_wire = WireWriterPool.acquire(4 + Utf8Codec.maxBytes(keyword))
    val __boltffi_keyword_writer = __boltffi_keyword_wire.writer
    __boltffi_keyword_writer.writeString(keyword)
    try {
        val __boltffi_result = try { Native.boltffi_function_qqmusic_api_helper_next_api_search_artists(__boltffi_keyword_wire.directBuffer(), __boltffi_keyword_wire.size(), page, limit) } catch (__boltffi_error: BoltFfiErrorBufferException) { run { val __boltffi_error_reader = WireReader(__boltffi_error.bytes); throw HelperError.fromReader(__boltffi_error_reader) } } ?: throw IllegalStateException("null buffer returned")
        val __boltffi_reader = WireReader(__boltffi_result)
        return ArtistSearch.fromReader(__boltffi_reader)
    } finally {
        __boltffi_keyword_wire.close()
    }
}

fun searchAlbums(keyword: String, page: Long, limit: Long): AlbumSearch {
    val __boltffi_keyword_wire = WireWriterPool.acquire(4 + Utf8Codec.maxBytes(keyword))
    val __boltffi_keyword_writer = __boltffi_keyword_wire.writer
    __boltffi_keyword_writer.writeString(keyword)
    try {
        val __boltffi_result = try { Native.boltffi_function_qqmusic_api_helper_next_api_search_albums(__boltffi_keyword_wire.directBuffer(), __boltffi_keyword_wire.size(), page, limit) } catch (__boltffi_error: BoltFfiErrorBufferException) { run { val __boltffi_error_reader = WireReader(__boltffi_error.bytes); throw HelperError.fromReader(__boltffi_error_reader) } } ?: throw IllegalStateException("null buffer returned")
        val __boltffi_reader = WireReader(__boltffi_result)
        return AlbumSearch.fromReader(__boltffi_reader)
    } finally {
        __boltffi_keyword_wire.close()
    }
}

fun searchPlaylists(keyword: String, page: Long, limit: Long): PlaylistSearch {
    val __boltffi_keyword_wire = WireWriterPool.acquire(4 + Utf8Codec.maxBytes(keyword))
    val __boltffi_keyword_writer = __boltffi_keyword_wire.writer
    __boltffi_keyword_writer.writeString(keyword)
    try {
        val __boltffi_result = try { Native.boltffi_function_qqmusic_api_helper_next_api_search_playlists(__boltffi_keyword_wire.directBuffer(), __boltffi_keyword_wire.size(), page, limit) } catch (__boltffi_error: BoltFfiErrorBufferException) { run { val __boltffi_error_reader = WireReader(__boltffi_error.bytes); throw HelperError.fromReader(__boltffi_error_reader) } } ?: throw IllegalStateException("null buffer returned")
        val __boltffi_reader = WireReader(__boltffi_result)
        return PlaylistSearch.fromReader(__boltffi_reader)
    } finally {
        __boltffi_keyword_wire.close()
    }
}

/**
 * Cover candidates for a local track (the host scores them).
 */
fun searchTrackArtwork(title: String, artist: String, album: String, limit: Long): List<ArtworkCandidate> {
    val __boltffi_title_wire = WireWriterPool.acquire(4 + Utf8Codec.maxBytes(title))
    val __boltffi_title_writer = __boltffi_title_wire.writer
    __boltffi_title_writer.writeString(title)
    val __boltffi_artist_wire = WireWriterPool.acquire(4 + Utf8Codec.maxBytes(artist))
    val __boltffi_artist_writer = __boltffi_artist_wire.writer
    __boltffi_artist_writer.writeString(artist)
    val __boltffi_album_wire = WireWriterPool.acquire(4 + Utf8Codec.maxBytes(album))
    val __boltffi_album_writer = __boltffi_album_wire.writer
    __boltffi_album_writer.writeString(album)
    try {
        val __boltffi_result = try { Native.boltffi_function_qqmusic_api_helper_next_api_search_track_artwork(__boltffi_title_wire.directBuffer(), __boltffi_title_wire.size(), __boltffi_artist_wire.directBuffer(), __boltffi_artist_wire.size(), __boltffi_album_wire.directBuffer(), __boltffi_album_wire.size(), limit) } catch (__boltffi_error: BoltFfiErrorBufferException) { run { val __boltffi_error_reader = WireReader(__boltffi_error.bytes); throw HelperError.fromReader(__boltffi_error_reader) } } ?: throw IllegalStateException("null buffer returned")
        val __boltffi_reader = WireReader(__boltffi_result)
        return __boltffi_reader.readSequence({ __boltffi_reader -> ArtworkCandidate.fromReader(__boltffi_reader) })
    } finally {
        __boltffi_title_wire.close()
        __boltffi_artist_wire.close()
        __boltffi_album_wire.close()
    }
}

/**
 * Cover candidates for a local artist.
 */
fun searchArtistArtwork(name: String, limit: Long): List<ArtworkCandidate> {
    val __boltffi_name_wire = WireWriterPool.acquire(4 + Utf8Codec.maxBytes(name))
    val __boltffi_name_writer = __boltffi_name_wire.writer
    __boltffi_name_writer.writeString(name)
    try {
        val __boltffi_result = try { Native.boltffi_function_qqmusic_api_helper_next_api_search_artist_artwork(__boltffi_name_wire.directBuffer(), __boltffi_name_wire.size(), limit) } catch (__boltffi_error: BoltFfiErrorBufferException) { run { val __boltffi_error_reader = WireReader(__boltffi_error.bytes); throw HelperError.fromReader(__boltffi_error_reader) } } ?: throw IllegalStateException("null buffer returned")
        val __boltffi_reader = WireReader(__boltffi_result)
        return __boltffi_reader.readSequence({ __boltffi_reader -> ArtworkCandidate.fromReader(__boltffi_reader) })
    } finally {
        __boltffi_name_wire.close()
    }
}

/**
 * Cover candidates for a local album.
 */
fun searchAlbumArtwork(album: String, artist: String, limit: Long): List<ArtworkCandidate> {
    val __boltffi_album_wire = WireWriterPool.acquire(4 + Utf8Codec.maxBytes(album))
    val __boltffi_album_writer = __boltffi_album_wire.writer
    __boltffi_album_writer.writeString(album)
    val __boltffi_artist_wire = WireWriterPool.acquire(4 + Utf8Codec.maxBytes(artist))
    val __boltffi_artist_writer = __boltffi_artist_wire.writer
    __boltffi_artist_writer.writeString(artist)
    try {
        val __boltffi_result = try { Native.boltffi_function_qqmusic_api_helper_next_api_search_album_artwork(__boltffi_album_wire.directBuffer(), __boltffi_album_wire.size(), __boltffi_artist_wire.directBuffer(), __boltffi_artist_wire.size(), limit) } catch (__boltffi_error: BoltFfiErrorBufferException) { run { val __boltffi_error_reader = WireReader(__boltffi_error.bytes); throw HelperError.fromReader(__boltffi_error_reader) } } ?: throw IllegalStateException("null buffer returned")
        val __boltffi_reader = WireReader(__boltffi_result)
        return __boltffi_reader.readSequence({ __boltffi_reader -> ArtworkCandidate.fromReader(__boltffi_reader) })
    } finally {
        __boltffi_album_wire.close()
        __boltffi_artist_wire.close()
    }
}

/**
 * Apply the user's request-rate ceiling.
 *
 * A configuration push rather than a read: the app sends it on startup and
 * whenever the settings change, so the numbers survive a component restart.
 */
fun setRateLimit(enabled: Boolean, windowSeconds: Long, maxRequests: Long): RateLimitConfigModel {
    val __boltffi_result = try { Native.boltffi_function_qqmusic_api_helper_next_api_set_rate_limit(enabled, windowSeconds, maxRequests) } catch (__boltffi_error: BoltFfiErrorBufferException) { run { val __boltffi_error_reader = WireReader(__boltffi_error.bytes); throw HelperError.fromReader(__boltffi_error_reader) } } ?: throw IllegalStateException("null buffer returned")
    val __boltffi_reader = WireReader(__boltffi_result)
    return RateLimitConfigModel.fromReader(__boltffi_reader)
}

/**
 * Apply the user's circuit-breaker numbers.
 */
fun setBreaker(enabled: Boolean, failureThreshold: Long, failureWindowSeconds: Long, openSeconds: Long): BreakerConfigModel {
    val __boltffi_result = try { Native.boltffi_function_qqmusic_api_helper_next_api_set_breaker(enabled, failureThreshold, failureWindowSeconds, openSeconds) } catch (__boltffi_error: BoltFfiErrorBufferException) { run { val __boltffi_error_reader = WireReader(__boltffi_error.bytes); throw HelperError.fromReader(__boltffi_error_reader) } } ?: throw IllegalStateException("null buffer returned")
    val __boltffi_reader = WireReader(__boltffi_result)
    return BreakerConfigModel.fromReader(__boltffi_reader)
}

/**
 * The download engine's state. `ensure` starts it when it is not up.
 */
fun aria2Status(ensure: Boolean): Aria2Status {
    val __boltffi_result = try { Native.boltffi_function_qqmusic_api_helper_next_api_aria2_status(ensure) } catch (__boltffi_error: BoltFfiErrorBufferException) { run { val __boltffi_error_reader = WireReader(__boltffi_error.bytes); throw HelperError.fromReader(__boltffi_error_reader) } } ?: throw IllegalStateException("null buffer returned")
    val __boltffi_reader = WireReader(__boltffi_result)
    return Aria2Status.fromReader(__boltffi_reader)
}

/**
 * Restart the download engine.
 */
fun aria2Restart(): Aria2Status {
    val __boltffi_result = try { Native.boltffi_function_qqmusic_api_helper_next_api_aria2_restart() } catch (__boltffi_error: BoltFfiErrorBufferException) { run { val __boltffi_error_reader = WireReader(__boltffi_error.bytes); throw HelperError.fromReader(__boltffi_error_reader) } } ?: throw IllegalStateException("null buffer returned")
    val __boltffi_reader = WireReader(__boltffi_result)
    return Aria2Status.fromReader(__boltffi_reader)
}

/**
 * Apply the download engine's numbers.
 */
fun aria2Configure(split: Long, maxConnectionPerServer: Long, maxConcurrentDownloads: Long, minSplitSizeMib: Long, maxOverallDownloadLimitKib: Long, port: Long): Aria2Status {
    val __boltffi_result = try { Native.boltffi_function_qqmusic_api_helper_next_api_aria2_configure(split, maxConnectionPerServer, maxConcurrentDownloads, minSplitSizeMib, maxOverallDownloadLimitKib, port) } catch (__boltffi_error: BoltFfiErrorBufferException) { run { val __boltffi_error_reader = WireReader(__boltffi_error.bytes); throw HelperError.fromReader(__boltffi_error_reader) } } ?: throw IllegalStateException("null buffer returned")
    val __boltffi_reader = WireReader(__boltffi_result)
    return Aria2Status.fromReader(__boltffi_reader)
}

/**
 * Queue one file.
 */
fun aria2Add(url: String, `out`: String): Aria2Download {
    val __boltffi_url_wire = WireWriterPool.acquire(4 + Utf8Codec.maxBytes(url))
    val __boltffi_url_writer = __boltffi_url_wire.writer
    __boltffi_url_writer.writeString(url)
    val __boltffi_out_wire = WireWriterPool.acquire(4 + Utf8Codec.maxBytes(`out`))
    val __boltffi_out_writer = __boltffi_out_wire.writer
    __boltffi_out_writer.writeString(`out`)
    try {
        val __boltffi_result = try { Native.boltffi_function_qqmusic_api_helper_next_api_aria2_add(__boltffi_url_wire.directBuffer(), __boltffi_url_wire.size(), __boltffi_out_wire.directBuffer(), __boltffi_out_wire.size()) } catch (__boltffi_error: BoltFfiErrorBufferException) { run { val __boltffi_error_reader = WireReader(__boltffi_error.bytes); throw HelperError.fromReader(__boltffi_error_reader) } } ?: throw IllegalStateException("null buffer returned")
        val __boltffi_reader = WireReader(__boltffi_result)
        return Aria2Download.fromReader(__boltffi_reader)
    } finally {
        __boltffi_url_wire.close()
        __boltffi_out_wire.close()
    }
}

/**
 * One download's progress.
 */
fun aria2Tell(gid: String): Aria2Download {
    val __boltffi_gid_wire = WireWriterPool.acquire(4 + Utf8Codec.maxBytes(gid))
    val __boltffi_gid_writer = __boltffi_gid_wire.writer
    __boltffi_gid_writer.writeString(gid)
    try {
        val __boltffi_result = try { Native.boltffi_function_qqmusic_api_helper_next_api_aria2_tell(__boltffi_gid_wire.directBuffer(), __boltffi_gid_wire.size()) } catch (__boltffi_error: BoltFfiErrorBufferException) { run { val __boltffi_error_reader = WireReader(__boltffi_error.bytes); throw HelperError.fromReader(__boltffi_error_reader) } } ?: throw IllegalStateException("null buffer returned")
        val __boltffi_reader = WireReader(__boltffi_result)
        return Aria2Download.fromReader(__boltffi_reader)
    } finally {
        __boltffi_gid_wire.close()
    }
}

/**
 * Every task the engine holds.
 */
fun aria2List(): Aria2TaskList {
    val __boltffi_result = try { Native.boltffi_function_qqmusic_api_helper_next_api_aria2_list() } catch (__boltffi_error: BoltFfiErrorBufferException) { run { val __boltffi_error_reader = WireReader(__boltffi_error.bytes); throw HelperError.fromReader(__boltffi_error_reader) } } ?: throw IllegalStateException("null buffer returned")
    val __boltffi_reader = WireReader(__boltffi_result)
    return Aria2TaskList.fromReader(__boltffi_reader)
}

/**
 * Pause one task (or all when `gid` is empty).
 */
fun aria2Pause(gid: String?): Aria2TaskList {
    val __boltffi_gid_wire = WireWriterPool.acquire(1 + (gid?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0))
    val __boltffi_gid_writer = __boltffi_gid_wire.writer
    __boltffi_gid_writer.writeOptionalValue(gid, { __boltffi_gid_writer, __boltffi_value_0 -> __boltffi_gid_writer.writeString(__boltffi_value_0) })
    try {
        val __boltffi_result = try { Native.boltffi_function_qqmusic_api_helper_next_api_aria2_pause(__boltffi_gid_wire.directBuffer(), __boltffi_gid_wire.size()) } catch (__boltffi_error: BoltFfiErrorBufferException) { run { val __boltffi_error_reader = WireReader(__boltffi_error.bytes); throw HelperError.fromReader(__boltffi_error_reader) } } ?: throw IllegalStateException("null buffer returned")
        val __boltffi_reader = WireReader(__boltffi_result)
        return Aria2TaskList.fromReader(__boltffi_reader)
    } finally {
        __boltffi_gid_wire.close()
    }
}

/**
 * Resume one task (or all).
 */
fun aria2Unpause(gid: String?): Aria2TaskList {
    val __boltffi_gid_wire = WireWriterPool.acquire(1 + (gid?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0))
    val __boltffi_gid_writer = __boltffi_gid_wire.writer
    __boltffi_gid_writer.writeOptionalValue(gid, { __boltffi_gid_writer, __boltffi_value_0 -> __boltffi_gid_writer.writeString(__boltffi_value_0) })
    try {
        val __boltffi_result = try { Native.boltffi_function_qqmusic_api_helper_next_api_aria2_unpause(__boltffi_gid_wire.directBuffer(), __boltffi_gid_wire.size()) } catch (__boltffi_error: BoltFfiErrorBufferException) { run { val __boltffi_error_reader = WireReader(__boltffi_error.bytes); throw HelperError.fromReader(__boltffi_error_reader) } } ?: throw IllegalStateException("null buffer returned")
        val __boltffi_reader = WireReader(__boltffi_result)
        return Aria2TaskList.fromReader(__boltffi_reader)
    } finally {
        __boltffi_gid_wire.close()
    }
}

/**
 * Cancel one task (or all), deleting the partial files.
 */
fun aria2Cancel(gid: String?): Aria2TaskList {
    val __boltffi_gid_wire = WireWriterPool.acquire(1 + (gid?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0))
    val __boltffi_gid_writer = __boltffi_gid_wire.writer
    __boltffi_gid_writer.writeOptionalValue(gid, { __boltffi_gid_writer, __boltffi_value_0 -> __boltffi_gid_writer.writeString(__boltffi_value_0) })
    try {
        val __boltffi_result = try { Native.boltffi_function_qqmusic_api_helper_next_api_aria2_cancel(__boltffi_gid_wire.directBuffer(), __boltffi_gid_wire.size()) } catch (__boltffi_error: BoltFfiErrorBufferException) { run { val __boltffi_error_reader = WireReader(__boltffi_error.bytes); throw HelperError.fromReader(__boltffi_error_reader) } } ?: throw IllegalStateException("null buffer returned")
        val __boltffi_reader = WireReader(__boltffi_result)
        return Aria2TaskList.fromReader(__boltffi_reader)
    } finally {
        __boltffi_gid_wire.close()
    }
}

/**
 * An artist's biography.
 */
fun fetchArtistBiography(name: String, singerMid: String?): ArtistBiography {
    val __boltffi_name_wire = WireWriterPool.acquire(4 + Utf8Codec.maxBytes(name))
    val __boltffi_name_writer = __boltffi_name_wire.writer
    __boltffi_name_writer.writeString(name)
    val __boltffi_singerMid_wire = WireWriterPool.acquire(1 + (singerMid?.let { __boltffi_value_0 -> 4 + Utf8Codec.maxBytes(__boltffi_value_0) } ?: 0))
    val __boltffi_singerMid_writer = __boltffi_singerMid_wire.writer
    __boltffi_singerMid_writer.writeOptionalValue(singerMid, { __boltffi_singerMid_writer, __boltffi_value_0 -> __boltffi_singerMid_writer.writeString(__boltffi_value_0) })
    try {
        val __boltffi_result = try { Native.boltffi_function_qqmusic_api_helper_next_api_fetch_artist_biography(__boltffi_name_wire.directBuffer(), __boltffi_name_wire.size(), __boltffi_singerMid_wire.directBuffer(), __boltffi_singerMid_wire.size()) } catch (__boltffi_error: BoltFfiErrorBufferException) { run { val __boltffi_error_reader = WireReader(__boltffi_error.bytes); throw HelperError.fromReader(__boltffi_error_reader) } } ?: throw IllegalStateException("null buffer returned")
        val __boltffi_reader = WireReader(__boltffi_result)
        return ArtistBiography.fromReader(__boltffi_reader)
    } finally {
        __boltffi_name_wire.close()
        __boltffi_singerMid_wire.close()
    }
}

/**
 * Initialize the component for a host before its first API call.
 *
 * Repeating the same configuration is safe. A conflicting configuration, or
 * first-time configuration after any component API has started, is an explicit
 * error because its stores may already have captured the original directory.
 */
fun initialize(dataDir: String, platform: String) {
    val __boltffi_dataDir_wire = WireWriterPool.acquire(4 + Utf8Codec.maxBytes(dataDir))
    val __boltffi_dataDir_writer = __boltffi_dataDir_wire.writer
    __boltffi_dataDir_writer.writeString(dataDir)
    val __boltffi_platform_wire = WireWriterPool.acquire(4 + Utf8Codec.maxBytes(platform))
    val __boltffi_platform_writer = __boltffi_platform_wire.writer
    __boltffi_platform_writer.writeString(platform)
    try {
        try { Native.boltffi_function_qqmusic_api_helper_next_initialize(__boltffi_dataDir_wire.directBuffer(), __boltffi_dataDir_wire.size(), __boltffi_platform_wire.directBuffer(), __boltffi_platform_wire.size()) } catch (__boltffi_error: BoltFfiErrorBufferException) { run { val __boltffi_error_reader = WireReader(__boltffi_error.bytes); throw HelperError.fromReader(__boltffi_error_reader) } }
    } finally {
        __boltffi_dataDir_wire.close()
        __boltffi_platform_wire.close()
    }
}