package com.neumusic.player.data.api

/**
 * QRC 逐字歌词解密与解析。
 *
 * ## 解密（非标准类 DES，三重）
 * QQ 的 QRC 密文用「私有类 DES」分组密码（S/P/E 盒为 QQ 自定义、密钥位序有特有怪癖），
 * 三重组合 D(K3)→E(K2)→D(K1)，密钥为连续 24 字节 `!@#)(*$%123ZXC!@!@#)(NHL`；
 * 解密后是 zlib（带 UTF-8 BOM），解压得到带 XML 壳的 QRC 明文。
 * 输入是 **hex 文本**（`GetPlayLyricInfo` 在 `qrc:1` 时 `lyric` 字段即 hex 密文）。
 *
 * 算法实现自 [qrc-decoder](https://github.com/apoint123/qrc-decoder)（MIT License,
 * Copyright (c) apoint123）的规格；已用 AMLL 官方测试向量验证一致。Kotlin 移植保留
 * 原 MIT 版权声明。
 */
object QrcCodec {

    private val KEY = "!@#)(*$%123ZXC!@!@#)(NHL".toByteArray(Charsets.UTF_8)

    private val KEY_COMPRESSION = intArrayOf(13,16,10,23,0,4,2,27,14,5,20,9,22,18,11,3,25,7,15,6,26,19,12,1,40,51,30,36,46,54,29,39,50,44,32,47,43,48,38,55,33,52,45,41,49,35,28,31)
    private val KEY_PERM_C = intArrayOf(56,48,40,32,24,16,8,0,57,49,41,33,25,17,9,1,58,50,42,34,26,18,10,2,59,51,43,35)
    private val KEY_PERM_D = intArrayOf(62,54,46,38,30,22,14,6,61,53,45,37,29,21,13,5,60,52,44,36,28,20,12,4,27,19,11,3)
    private val KEY_RND_SHIFT = intArrayOf(1,1,2,2,2,2,2,2,1,2,2,2,2,2,2,1)
    private val IP_RULE = intArrayOf(34,42,50,58,2,10,18,26,36,44,52,60,4,12,20,28,38,46,54,62,6,14,22,30,40,48,56,64,8,16,24,32,33,41,49,57,1,9,17,25,35,43,51,59,3,11,19,27,37,45,53,61,5,13,21,29,39,47,55,63,7,15,23,31)
    private val INV_IP_RULE = intArrayOf(37,5,45,13,53,21,61,29,38,6,46,14,54,22,62,30,39,7,47,15,55,23,63,31,40,8,48,16,56,24,64,32,33,1,41,9,49,17,57,25,34,2,42,10,50,18,58,26,35,3,43,11,51,19,59,27,36,4,44,12,52,20,60,28)
    private val P_BOX = intArrayOf(16,7,20,21,29,12,28,17,1,15,23,26,5,18,31,10,2,8,24,14,32,27,3,9,19,13,30,6,22,11,4,25)
    private val E_BOX = intArrayOf(32,1,2,3,4,5,4,5,6,7,8,9,8,9,10,11,12,13,12,13,14,15,16,17,16,17,18,19,20,21,20,21,22,23,24,25,24,25,26,27,28,29,28,29,30,31,32,1)
    private val S_BOXES = listOf(
        intArrayOf(14,4,13,1,2,15,11,8,3,10,6,12,5,9,0,7,0,15,7,4,14,2,13,1,10,6,12,11,9,5,3,8,4,1,14,8,13,6,2,11,15,12,9,7,3,10,5,0,15,12,8,2,4,9,1,7,5,11,3,14,10,0,6,13),
        intArrayOf(15,1,8,14,6,11,3,4,9,7,2,13,12,0,5,10,3,13,4,7,15,2,8,15,12,0,1,10,6,9,11,5,0,14,7,11,10,4,13,1,5,8,12,6,9,3,2,15,13,8,10,1,3,15,4,2,11,6,7,12,0,5,14,9),
        intArrayOf(10,0,9,14,6,3,15,5,1,13,12,7,11,4,2,8,13,7,0,9,3,4,6,10,2,8,5,14,12,11,15,1,13,6,4,9,8,15,3,0,11,1,2,12,5,10,14,7,1,10,13,0,6,9,8,7,4,15,14,3,11,5,2,12),
        intArrayOf(7,13,14,3,0,6,9,10,1,2,8,5,11,12,4,15,13,8,11,5,6,15,0,3,4,7,2,12,1,10,14,9,10,6,9,0,12,11,7,13,15,1,3,14,5,2,8,4,3,15,0,6,10,10,13,8,9,4,5,11,12,7,2,14),
        intArrayOf(2,12,4,1,7,10,11,6,8,5,3,15,13,0,14,9,14,11,2,12,4,7,13,1,5,0,15,10,3,9,8,6,4,2,1,11,10,13,7,8,15,9,12,5,6,3,0,14,11,8,12,7,1,14,2,13,6,15,0,9,10,4,5,3),
        intArrayOf(12,1,10,15,9,2,6,8,0,13,3,4,14,7,5,11,10,15,4,2,7,12,9,5,6,1,13,14,0,11,3,8,9,14,15,5,2,8,12,3,7,0,4,10,1,13,11,6,4,3,2,12,9,5,15,10,11,14,1,7,6,0,8,13),
        intArrayOf(4,11,2,14,15,0,8,13,3,12,9,7,5,10,6,1,13,0,11,7,4,9,1,10,14,3,5,12,2,15,8,6,1,4,11,13,12,3,7,14,10,15,6,8,0,5,9,2,6,11,13,8,1,4,10,7,9,5,0,15,14,2,3,12),
        intArrayOf(13,2,8,4,6,15,11,1,10,9,3,14,5,0,12,7,1,15,13,8,10,3,7,4,12,5,6,11,0,14,9,2,7,11,4,1,9,12,14,2,0,6,10,13,15,3,5,8,2,1,14,7,4,10,8,13,15,12,9,0,3,5,6,11),
    )

    private const val M32 = -0x1L   // 32 位全 1

    private fun permuteFromKeyBytes(key: ByteArray, table: IntArray): Long {
        var out = 0L
        var mask = 1L shl (table.size - 1)
        for (pos in table) {
            val word = pos ushr 5
            val bitInWord = pos and 31
            val byteInWord = bitInWord ushr 3
            val bitInByte = bitInWord and 7
            val byteIndex = word * 4 + 3 - byteInWord
            if ((key[byteIndex].toInt() ushr (7 - bitInByte)) and 1 == 1) out = out or mask
            mask = mask ushr 1
        }
        return out
    }

    private fun rotl28(v: Long, n: Int): Long {
        val bits = 0xFFFFFFF0L
        val v28 = v and bits
        return ((v28 shl n) or (v28 ushr (28 - n))) and bits
    }

    private fun keySchedule(key: ByteArray, mode: Int): IntArray {
        val sched = IntArray(32)
        var c = permuteFromKeyBytes(key, KEY_PERM_C) shl 4
        var d = permuteFromKeyBytes(key, KEY_PERM_D) shl 4
        for (i in 0 until 16) {
            val sh = KEY_RND_SHIFT[i]
            c = rotl28(c, sh); d = rotl28(d, sh)
            val toGen = if (mode == 1) 15 - i else i
            var sub = 0L
            for (k in KEY_COMPRESSION.indices) {
                val pos = KEY_COMPRESSION[k]
                val bit = if (pos < 28) (c ushr (31 - pos)) and 1L
                else (d ushr (31 - (pos - 27))) and 1L
                if (bit == 1L) sub = sub or (1L shl (47 - k))
            }
            val b5 = ((sub ushr 40) and 0xFF).toInt()
            val b4 = ((sub ushr 32) and 0xFF).toInt()
            val b3 = ((sub ushr 24) and 0xFF).toInt()
            val b2 = ((sub ushr 16) and 0xFF).toInt()
            val b1 = ((sub ushr 8) and 0xFF).toInt()
            val b0 = (sub and 0xFF).toInt()
            sched[toGen * 2] = (b5 shl 16) or (b4 shl 8) or b3
            sched[toGen * 2 + 1] = (b2 shl 16) or (b1 shl 8) or b0
        }
        return sched
    }

    private val IP_LEFT = IntArray(2048); private val IP_RIGHT = IntArray(2048)
    private val INV_LEFT = IntArray(2048); private val INV_RIGHT = IntArray(2048)
    private val SP = IntArray(512)
    private val EB_HIGH = IntArray(1024); private val EB_LOW = IntArray(1024)

    private fun applyPerm64(input: Long, rule: IntArray): Long {
        var out = 0L
        for (i in 0 until 64) {
            if ((input ushr (64 - rule[i])) and 1L == 1L) out = out or (1L shl (63 - i))
        }
        return out
    }

    private fun sboxIndex(a: Int): Int = (a and 0x20) or ((a and 0x1f) ushr 1) or ((a and 0x01) shl 4)

    private fun qqPbox(v: Int): Int {
        var out = 0
        for (i in 0 until 32) {
            val src = P_BOX[i]
            if (v and (1 shl (32 - src)) != 0) out = out or (1 shl (31 - i))
        }
        return out
    }

    init {
        for (bp in 0 until 8) {
            for (bv in 0 until 256) {
                val inp = bv.toLong() shl (56 - bp * 8)
                val p = applyPerm64(inp, IP_RULE)
                IP_LEFT[(bp shl 8) or bv] = ((p ushr 32) and M32).toInt()
                IP_RIGHT[(bp shl 8) or bv] = (p and M32).toInt()
                val p2 = applyPerm64(inp, INV_IP_RULE)
                INV_LEFT[(bp shl 8) or bv] = ((p2 ushr 32) and M32).toInt()
                INV_RIGHT[(bp shl 8) or bv] = (p2 and M32).toInt()
            }
        }
        for (si in 0 until 8) {
            for (inp in 0 until 64) {
                val idx = sboxIndex(inp)
                val value = S_BOXES[si][idx]
                SP[(si shl 6) or inp] = qqPbox(value shl (28 - si * 4))
            }
        }
        for (ch in 0 until 4) {
            val sh = (3 - ch) * 8
            for (bv in 0 until 256) {
                val inp = bv.toLong() shl sh
                var hi = 0; var lo = 0
                for (i in 0 until 24) if ((inp ushr (32 - E_BOX[i])) and 1L == 1L) hi = hi or (1 shl (23 - i))
                for (i in 24 until 48) if ((inp ushr (32 - E_BOX[i])) and 1L == 1L) lo = lo or (1 shl (47 - i))
                EB_HIGH[(ch shl 8) or bv] = hi
                EB_LOW[(ch shl 8) or bv] = lo
            }
        }
    }

    private fun f(state: Int, kh: Int, kl: Int): Int {
        val b0 = (state ushr 24) and 0xFF
        val b1 = (state ushr 16) and 0xFF
        val b2 = (state ushr 8) and 0xFF
        val b3 = state and 0xFF
        val eh = EB_HIGH[b0] or EB_HIGH[256 or b1] or EB_HIGH[512 or b2] or EB_HIGH[768 or b3]
        val el = EB_LOW[b0] or EB_LOW[256 or b1] or EB_LOW[512 or b2] or EB_LOW[768 or b3]
        val xh = eh xor kh
        val xl = el xor kl
        return (SP[(xh ushr 18) and 0x3F] or SP[64 or ((xh ushr 12) and 0x3F)] or
                SP[128 or ((xh ushr 6) and 0x3F)] or SP[192 or (xh and 0x3F)] or
                SP[256 or ((xl ushr 18) and 0x3F)] or SP[320 or ((xl ushr 12) and 0x3F)] or
                SP[384 or ((xl ushr 6) and 0x3F)] or SP[448 or (xl and 0x3F)])
    }

    /** 解密单个 8 字节块（in 起始处），结果写回 out 的对应段。 */
    private fun desBlock(input: ByteArray, inOff: Int, out: ByteArray, outOff: Int, sched: IntArray) {
        var left = 0; var right = 0
        for (i in 0 until 8) {
            val idx = (i shl 8) or (input[inOff + i].toInt() and 0xFF)
            left = left or IP_LEFT[idx]
            right = right or IP_RIGHT[idx]
        }
        for (i in 0 until 15) {
            val tmp = right
            right = left xor f(right, sched[i * 2], sched[i * 2 + 1])
            left = tmp
        }
        val l = left xor f(right, sched[30], sched[31])
        val r = right
        var outL = 0; var outR = 0
        for (b in 0 until 4) {
            val idxL = (b shl 8) or ((l ushr (24 - b * 8)) and 0xFF)
            outL = outL or INV_LEFT[idxL]; outR = outR or INV_RIGHT[idxL]
            val idxR = ((b + 4) shl 8) or ((r ushr (24 - b * 8)) and 0xFF)
            outL = outL or INV_LEFT[idxR]; outR = outR or INV_RIGHT[idxR]
        }
        out[outOff] = ((outL ushr 24) and 0xFF).toByte()
        out[outOff + 1] = ((outL ushr 16) and 0xFF).toByte()
        out[outOff + 2] = ((outL ushr 8) and 0xFF).toByte()
        out[outOff + 3] = (outL and 0xFF).toByte()
        out[outOff + 4] = ((outR ushr 24) and 0xFF).toByte()
        out[outOff + 5] = ((outR ushr 16) and 0xFF).toByte()
        out[outOff + 6] = ((outR ushr 8) and 0xFF).toByte()
        out[outOff + 7] = (outR and 0xFF).toByte()
    }

    private val KD = arrayOf(
        keySchedule(KEY.copyOfRange(16, 24), 1),   // D(K3)
        keySchedule(KEY.copyOfRange(8, 16), 0),    // E(K2)
        keySchedule(KEY.copyOfRange(0, 8), 1),     // D(K1)
    )

    /**
     * 解密 hex 文本形式的 QRC 密文，返回解压后的 QRC XML 明文。
     * 格式不符（非 hex / 非 8 对齐）抛 [IllegalArgumentException]。
     */
    fun decryptHex(hex: String): String {
        require(hex.length % 2 == 0) { "hex 长度须为偶数" }
        val enc = hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        require(enc.size % 8 == 0) { "密文长度不是 8 的倍数" }
        val out = ByteArray(enc.size)
        for (i in 0 until enc.size step 8) {
            desBlock(enc, i, out, i, KD[0])
            desBlock(out, i, out, i, KD[1])
            desBlock(out, i, out, i, KD[2])
        }
        val inflater = java.util.zip.Inflater()
        inflater.setInput(out)
        val res = ByteArray(256 * 1024)
        var n = 0
        while (!inflater.finished() && n < res.size) {
            val got = inflater.inflate(res, n, res.size - n)
            if (got == 0 && inflater.needsInput()) break
            n += got
        }
        inflater.end()
        var txt = res.copyOf(n)
        if (txt.size >= 3 && txt[0] == 0xEF.toByte() && txt[1] == 0xBB.toByte() && txt[2] == 0xBF.toByte()) {
            txt = txt.copyOfRange(3, txt.size)
        }
        return String(txt, Charsets.UTF_8)
    }
}
