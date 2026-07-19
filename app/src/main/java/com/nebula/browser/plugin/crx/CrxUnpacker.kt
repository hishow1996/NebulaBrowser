package com.nebula.browser.plugin.crx

import com.nebula.browser.common.toast
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.PublicKey
import java.util.zip.ZipInputStream

/**
 * Chrome 扩展 .crx 文件解包器。
 *
 * CRX3 格式：
 *   magic     "Cr24"        4 bytes
 *   version   uint32 LE      4 bytes
 *   header_size uint32 LE    4 bytes
 *   header (proto CrxFileHeader)  header_size bytes
 *   zip payload                ...
 *
 * 解包后返回解压目录、公钥字节（用于 ID 生成）、与 SHA-1 哈希。
 */
object CrxUnpacker {

    data class Unpacked(val rootDir: File, val publicKey: ByteArray, val id: String)

    /**
     * @param input .crx 文件，或内容为 zip 但扩展名为 .crx 的伪 CRX
     * @param destRoot 解压根目录（每个扩展会创建一个子目录）
     */
    fun unpack(input: File, destRoot: File): Unpacked? {
        if (!input.exists()) return null
        destRoot.mkdirs()

        // 检测 magic
        val magic = ByteArray(4)
        FileInputStream(input).use { it.read(magic) }
        val isCrx = String(magic, Charsets.US_ASCII) == "Cr24"

        // 推导扩展 ID
        val publicKey: ByteArray = if (isCrx) {
            // 读 version + header_size
            val fb = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
            FileInputStream(input).use { fis ->
                fis.skip(4); fis.read(fb.array())
            }
            @Suppress(" UNUSED_VARIABLE ") val version = fb.getInt(0)
            val headerSize = fb.getInt(4)
            // 头部以 raw bytes 0x30 0x12 ... 形式前缀（proto AsymmetricKeyProof.SHA256_RSA）
            // 公钥可由 header 中提取 AsymmetricKeyProof.publicKey (field 1, bytes)
            val headerBytes = ByteArray(headerSize)
            FileInputStream(input).use { fis -> fis.skip(12); fis.read(headerBytes) }
            extractPublicKeyFromHeader(headerBytes)
        } else {
            // zip 文件没有公钥：使用文件名/路径的哈希作为伪公钥种子
            input.name.toByteArray()
        }
        val id = com.nebula.browser.plugin.model.ExtensionId.fromPublicKey(publicKey)

        val extDir = File(destRoot, id)
        if (extDir.exists()) extDir.deleteRecursively()
        extDir.mkdirs()

        // 提取 zip payload
        val zipOffset = if (isCrx) {
            val fb = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
            FileInputStream(input).use { fis -> fis.skip(4); fis.read(fb.array()) }
            12 + fb.getInt(4)
        } else 0

        try {
            FileInputStream(input).use { fis ->
                fis.skip(zipOffset.toLong())
                ZipInputStream(fis).use { zis ->
                    var entry = zis.nextEntry
                    val buf = ByteArray(8 * 1024)
                    while (entry != null) {
                        if (!entry.isDirectory) {
                            val outFile = File(extDir, entry.name)
                            outFile.parentFile?.mkdirs()
                            FileOutputStream(outFile).use { out ->
                                var n: Int
                                while (true) { n = zis.read(buf); if (n < 0) break; out.write(buf, 0, n) }
                            }
                        }
                        zis.closeEntry()
                        entry = zis.nextEntry
                    }
                }
            }
        } catch (e: Exception) {
            toast("解包失败：${e.message}")
            return null
        }
        return Unpacked(extDir, publicKey, id)
    }

    /**
     * 从 CRX3 proto 头部提取 AsymmetricKeyProof.publicKey（field=1, type=bytes）。
     * 极简实现：扫描 header 字节，遇到 0x0A（field=1, wire=2 length-delimited）后接长度 varint + bytes，认为是 public key。
     */
    private fun extractPublicKeyFromHeader(header: ByteArray): ByteArray {
        try {
            for (i in header.indices) {
                if (header[i] == 0x0A.toByte()) {
                    val len = header[i + 1].toInt() and 0xFF
                    if (len in 1..2048 && i + 2 + len <= header.size) {
                        return header.copyOfRange(i + 2, i + 2 + len)
                    }
                }
            }
        } catch (_: Exception) {}
        // fallback：使用整个 header 作为种子
        return header
    }
}
