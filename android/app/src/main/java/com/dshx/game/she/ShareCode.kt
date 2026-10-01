package com.dshx.game.she

import android.util.Base64
import java.io.ByteArrayOutputStream
import java.util.zip.DeflaterOutputStream
import java.util.zip.InflaterInputStream
import org.json.JSONObject

/**
 * 分享码：把当前城市压成一串可复制的文本，别人粘贴即可进入同一座城市
 * （地形、建筑、道路、车马线路、资金状态全部还原）。
 *
 * 实现要点：直接复用 [SaveManager.buildJson] 的完整存档 JSON，
 * 保证分享码与存档字段永远一致，不会漏字段。
 * 编码：JSON → deflate 压缩 → Base64(URL_SAFE) → 前缀 "CS1."
 *
 * 合规：分享码只承载地图数据；城市名/玩家名在导入后仍会过屏蔽词校验。
 */
object ShareCode {

    private const val PREFIX = "CS1."   // 完整城市码（含建筑）前缀

    /**
     * 短种子码：纯数字，只分享地形。
     * 与开局「地图种子」完全同源——同一个数字必然生成同一张地图。
     * 优点：短、好记、好发论坛；缺点：不含玩家已建成的建筑。
     */
    fun seedCode(): String? = GameData.current?.let { GameData.seed.toString() }

    /** 解析短种子码（纯数字，1~9 位） */
    fun parseSeedCode(text: String): Int? {
        val t = text.trim()
        if (t.isEmpty() || t.length > 9) return null
        val v = t.toIntOrNull() ?: return null
        return if (v in 1..999999999) v else null
    }

    fun looksLikeCode(text: String): Boolean = text.trim().startsWith(PREFIX)

    /** 导出当前城市为分享码；无存档返回 null */
    fun export(): String? {
        val json = SaveManager.buildJson() ?: return null
        val raw = json.toString().toByteArray(Charsets.UTF_8)
        val bos = ByteArrayOutputStream()
        DeflaterOutputStream(bos).use { it.write(raw) }
        val packed = Base64.encodeToString(
            bos.toByteArray(),
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
        )
        return PREFIX + packed
    }

    /** 把分享码写入指定槽位（写完后用 SaveManager.load(slot) 载入即可） */
    fun import(code: String, slot: Int): Boolean {
        val text = code.trim()
        if (!looksLikeCode(text)) return false
        val json = try {
            val bytes = Base64.decode(
                text.removePrefix(PREFIX),
                Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
            )
            val out = ByteArrayOutputStream()
            InflaterInputStream(bytes.inputStream()).use { it.copyTo(out) }
            JSONObject(out.toString("UTF-8"))
        } catch (t: Throwable) {
            return false
        }
        return try {
            SaveManager.writeRaw(slot, json)
            true
        } catch (t: Throwable) {
            false
        }
    }
}
