package com.dshx.game.she

import android.util.Base64
import java.io.ByteArrayOutputStream
import java.util.zip.DeflaterOutputStream
import java.util.zip.InflaterInputStream

/**
 * 城市分享：种子码 / 单张二维码 / 多张二维码分片。
 *
 * 数据流：CityCodec 二进制 → deflate 压缩 → Base64。
 * 二进制编码让每格只占 4~6 字节，比 JSON 省 5 倍，一张二维码能装更多。
 * 城市太大时自动切成多片，UI 轮播显示，扫码端逐张收集后拼回。
 *
 * 分片格式：CS1.<序号>.<总数>.<数据>
 */
object ShareCode {

    private const val PREFIX = "CS1."

    /**
     * 单张二维码最多承载的字符数。
     * QR 版本 40 + 纠错 L 上限 2953 字节，Base64 膨胀 4/3 → 约 3900 字符。
     * 留出余量取 2600，保证绝大多数城市一张码就够。
     */
    const val CHUNK_CHARS = 2600

    // ------------------------------------------------------------------
    // 种子码（短数字，只含地形）
    // ------------------------------------------------------------------

    fun seedCode(): String? = GameData.current?.let { GameData.seed.toString() }

    fun parseSeedCode(text: String): Int? {
        val t = text.trim()
        if (t.isEmpty() || t.length > 9) return null
        val v = t.toIntOrNull() ?: return null
        return if (v in 1..999999999) v else null
    }

    fun looksLikeCode(text: String): Boolean = text.trim().startsWith(PREFIX)

    // ------------------------------------------------------------------
    // 导出
    // ------------------------------------------------------------------

    /** 完整城市数据（二进制→压缩→Base64），失败返回 null */
    private fun packedData(): String? {
        val raw = CityCodec.encode() ?: return null
        val bos = ByteArrayOutputStream()
        DeflaterOutputStream(bos, java.util.zip.Deflater(9)).use { it.write(raw) }
        return Base64.encodeToString(
            bos.toByteArray(),
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
        )
    }

    /**
     * 导出为分片列表。城市越大片数越多。
     * 每片都是独立二维码，扫齐所有片才能还原。
     */
    fun exportChunks(): List<String>? {
        val data = packedData() ?: return null
        if (data.length <= CHUNK_CHARS) return listOf("$PREFIX" + "1.1." + data)
        val total = (data.length + CHUNK_CHARS - 1) / CHUNK_CHARS
        val out = ArrayList<String>(total)
        var i = 0
        var idx = 1
        while (i < data.length) {
            val end = minOf(i + CHUNK_CHARS, data.length)
            out.add("$PREFIX" + idx + "." + total + "." + data.substring(i, end))
            i = end
            idx++
        }
        return out
    }

    /** 兼容旧调用：只取第一片 */
    fun export(): String? = exportChunks()?.firstOrNull()

    // ------------------------------------------------------------------
    // 导入
    // ------------------------------------------------------------------

    /** 从单段文本解析出 (序号, 总数, 数据)；不是分片格式返回 null */
    private fun splitChunk(text: String): Triple<Int, Int, String>? {
        val t = text.trim()
        if (!t.startsWith(PREFIX)) return null
        val rest = t.removePrefix(PREFIX)
        val p1 = rest.indexOf('.')
        if (p1 <= 0) return null
        val p2 = rest.indexOf('.', p1 + 1)
        if (p2 <= 0) return null
        val idx = rest.substring(0, p1).toIntOrNull() ?: return null
        val total = rest.substring(p1 + 1, p2).toIntOrNull() ?: return null
        val body = rest.substring(p2 + 1)
        if (idx < 1 || total < 1 || body.isEmpty()) return null
        return Triple(idx, total, body)
    }

    /** 分片收集器：扫码/粘贴多片时逐片喂进来，收齐后返回完整数据 */
    private val chunks = HashMap<Int, String>()
    private var expectTotal = -1

    fun resetCollect() {
        chunks.clear()
        expectTotal = -1
    }

    /** 当前已收集进度，返回 (已收, 总数) */
    fun progress(): Pair<Int, Int> = chunks.size to maxOf(expectTotal, chunks.size)

    /**
     * 喂入一片。收齐返回 true，否则 false。
     * 单张（总数=1）也是走这条路，收齐即 true。
     */
    fun feed(text: String): Boolean {
        val (idx, total, body) = splitChunk(text) ?: return false
        if (expectTotal != total) {
            // 换了一批数据，重新收集
            if (expectTotal != -1 && total != expectTotal) chunks.clear()
            expectTotal = total
        }
        chunks[idx] = body
        return chunks.size >= expectTotal
    }

    /** 收齐后把数据还原进存档槽 */
    fun finishInto(slot: Int): Boolean {
        if (expectTotal < 1 || chunks.size < expectTotal) return false
        val sb = StringBuilder()
        for (i in 1..expectTotal) {
            sb.append(chunks[i] ?: return false)
        }
        resetCollect()
        return importPacked(sb.toString(), slot)
    }

    /** 直接把一整段（未分片或已拼好）导入 */
    fun import(text: String, slot: Int): Boolean {
        val t = text.trim()
        if (!looksLikeCode(t)) return false
        val sp = splitChunk(t)
        if (sp != null && sp.second > 1) {
            // 是分片：喂进收集器，收齐才导入
            if (feed(t)) return finishInto(slot)
            return false
        }
        val body = if (sp != null) sp.third else t.removePrefix(PREFIX)
        return importPacked(body, slot)
    }

    private fun importPacked(packed: String, slot: Int): Boolean {
        val bytes = try {
            val b = Base64.decode(packed, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
            val out = ByteArrayOutputStream()
            InflaterInputStream(b.inputStream()).use { it.copyTo(out) }
            out.toByteArray()
        } catch (t: Throwable) {
            return false
        }
        val save = CityCodec.decodeToSave(bytes) ?: return false
        return try {
            SaveManager.writeRaw(slot, save)
            true
        } catch (t: Throwable) {
            false
        }
    }

    /** 估算当前城市需要几张二维码 */
    fun chunkCount(): Int = exportChunks()?.size ?: 0
}
