package com.dshx.game.she

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.CRC32
import java.util.zip.DeflaterOutputStream
import java.util.zip.InflaterInputStream

/**
 * 新区存档文件（.citymap）：发微信/QQ 给好友，对方点开即可载入。
 *
 * 文件结构：
 *   魔数 "CMAP" + 版本 + CRC32 + 压缩后的项目数据
 * 自带 CRC 校验，传输损坏时打开就报错，不需要额外校验手段。
 *
 * 接收：manifest 里注册了 .citymap 的 VIEW intent-filter，
 * 好友在微信里点文件 → 选「用其他应用打开」→ 选本作 → 自动载入。
 */
object CityFile {

    const val EXT = "citymap"
    private const val MAGIC = "CMAP"
    private const val VERSION = 1

    /** 导出到应用私有目录，返回文件（用于分享） */
    fun exportToFile(context: Context, cityName: String): File? {
        val data = CityCodec.encode() ?: return null
        val bos = ByteArrayOutputStream()
        DeflaterOutputStream(bos, java.util.zip.Deflater(9)).use { it.write(data) }
        val payload = bos.toByteArray()

        val crc = CRC32()
        crc.update(payload)

        val out = ByteArrayOutputStream()
        out.write(MAGIC.toByteArray(Charsets.US_ASCII))
        out.write(VERSION)
        writeInt(out, payload.size)
        writeInt(out, crc.value.toInt())
        out.write(payload)

        val dir = File(context.filesDir, "share")
        if (!dir.exists()) dir.mkdirs()
        val safe = cityName.replace(Regex("[^\\w\\u4e00-\\u9fa5]"), "_").take(20)
        val f = File(dir, "新区_${safe}_${System.currentTimeMillis()}.$EXT")
        return try {
            f.writeBytes(out.toByteArray())
            f
        } catch (t: Throwable) {
            null
        }
    }

    /** 弹出系统分享面板，把存档发给好友（微信/QQ 等） */
    fun shareIntent(context: Context, file: File): Intent? {
        return try {
            val uri: Uri = FileProvider.getUriForFile(
                context, "${context.packageName}.citymap.provider", file
            )
            val it = Intent(Intent.ACTION_SEND).apply {
                type = "application/octet-stream"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "新区存档")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            Intent.createChooser(it, "把新区发给好友")
        } catch (t: Throwable) {
            null
        }
    }

    /** 从外部 URI 读入存档并写入槽位 */
    fun importFromUri(context: Context, uri: Uri, slot: Int): Boolean {
        val j = parseFromUri(context, uri) ?: return false
        return try {
            SaveManager.writeRaw(slot, j)
            true
        } catch (t: Throwable) {
            false
        }
    }

    /** 从外部 URI 只解析，不落盘（交给玩家选槽位后再写） */
    fun parseFromUri(context: Context, uri: Uri): org.json.JSONObject? {
        val bytes = try {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        } catch (t: Throwable) {
            null
        } ?: return null
        return parseBytes(bytes)
    }

    /** 解析字节为存档 JSON（含 CRC 校验），失败返回 null */
    fun parseBytes(bytes: ByteArray): org.json.JSONObject? {
        return try {
            if (bytes.size < 13) return null
            val magic = String(bytes, 0, 4, Charsets.US_ASCII)
            if (magic != MAGIC) return null
            var p = 4
            val ver = bytes[p++].toInt() and 0xFF
            if (ver != VERSION) return null
            val len = readInt(bytes, p); p += 4
            val crcWant = readInt(bytes, p); p += 4
            if (len <= 0 || p + len > bytes.size) return null
            val payload = bytes.copyOfRange(p, p + len)
            val crc = CRC32()
            crc.update(payload)
            if (crc.value.toInt() != crcWant) return null
            val out = ByteArrayOutputStream()
            InflaterInputStream(payload.inputStream()).use { it.copyTo(out) }
            CityCodec.decodeToSave(out.toByteArray())
        } catch (t: Throwable) {
            null
        }
    }

    /** 从字节解析并写入槽位 */
    fun importBytes(bytes: ByteArray, slot: Int): Boolean {
        return try {
            if (bytes.size < 13) return false
            val magic = String(bytes, 0, 4, Charsets.US_ASCII)
            if (magic != MAGIC) return false
            var p = 4
            val ver = bytes[p++].toInt() and 0xFF
            if (ver != VERSION) return false
            val len = readInt(bytes, p); p += 4
            val crcWant = readInt(bytes, p); p += 4
            if (len <= 0 || p + len > bytes.size) return false
            val payload = bytes.copyOfRange(p, p + len)
            val crc = CRC32()
            crc.update(payload)
            if (crc.value.toInt() != crcWant) return false   // 校验失败：文件损坏

            val out = ByteArrayOutputStream()
            InflaterInputStream(payload.inputStream()).use { it.copyTo(out) }
            val save = CityCodec.decodeToSave(out.toByteArray()) ?: return false
            SaveManager.writeRaw(slot, save)
            true
        } catch (t: Throwable) {
            false
        }
    }

    /** 判断某个 intent 是否是「打开新区存档」 */
    fun isCityMapIntent(intent: Intent?): Boolean {
        if (intent == null) return false
        val action = intent.action ?: return false
        if (action != Intent.ACTION_VIEW) return false
        val data = intent.data ?: return false
        val s = data.toString().lowercase()
        return s.endsWith(".$EXT") || s.contains(".$EXT")
    }

    private fun writeInt(out: ByteArrayOutputStream, v: Int) {
        out.write(v and 0xFF)
        out.write((v shr 8) and 0xFF)
        out.write((v shr 16) and 0xFF)
        out.write((v shr 24) and 0xFF)
    }

    private fun readInt(b: ByteArray, p: Int): Int =
        (b[p].toInt() and 0xFF) or
            ((b[p + 1].toInt() and 0xFF) shl 8) or
            ((b[p + 2].toInt() and 0xFF) shl 16) or
            ((b[p + 3].toInt() and 0xFF) shl 24)
}
