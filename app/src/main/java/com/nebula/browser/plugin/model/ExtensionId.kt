package com.nebula.browser.plugin.model

import java.security.MessageDigest

/**
 * Chrome 扩展 ID 生成算法：取公钥 SHA-1 第 0-15 字节，每字节映射到 'a'-'p'。
 */
object ExtensionId {
    fun fromPublicKey(publicKey: ByteArray): String {
        val sha1 = MessageDigest.getInstance("SHA-1").digest(publicKey)
        val sb = StringBuilder()
        for (i in 0 until 16) {
            val v = (sha1[i].toInt() and 0xFF)
            sb.append(('a' + (v and 0x0F)).toChar())
            // 高 4 位再追加以满足 32 位（与 Chrome 算法一致：4 字节⇒8 位 chars 共 16 元素⇒32 chars）
            sb.append(('a' + ((v ushr 4) and 0x0F)).toChar())
        }
        return sb.toString()
    }

    /** 由任意种子字符串生成稳定 ID，便于本地侧载（无 CRX 公钥时） */
    fun fromSeed(seed: String): String =
        fromPublicKey(MessageDigest.getInstance("SHA-256").digest(seed.toByteArray()))
}
