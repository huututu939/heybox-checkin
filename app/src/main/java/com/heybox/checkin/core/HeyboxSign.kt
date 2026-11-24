package com.heybox.checkin.core

import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.time.ZoneOffset
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * 小黑盒 APP 签名（对应 APP 1.3.398 / build 1142 / shader 14）。
 * 纯计算，无原生库依赖。黄金向量：24 组 native + 3 组 web。
 */
object HeyboxSign {

    const val APP_VERSION = "1.3.398"
    const val APP_BUILD = "1142"
    const val SHADER_VERSION = "14"

    private val K = longArrayOf(
        0x428a2f98d728ae22uL.toLong(),
        0x7137449123ef65cduL.toLong(),
        0xb5c0fbcfec4d3b2fuL.toLong(),
        0xe9b5dba58189dbbcuL.toLong(),
        0x3956c25bf348b538uL.toLong(),
        0x59f111f1b605d019uL.toLong(),
        0x923f82a4af194f9buL.toLong(),
        0xab1c5ed5da6d8118uL.toLong(),
        0xd807aa98a3030242uL.toLong(),
        0x12835b0145706fbeuL.toLong(),
        0x243185be4ee4b28cuL.toLong(),
        0x550c7dc3d5ffb4e2uL.toLong(),
        0x72be5d74f27b896fuL.toLong(),
        0x80deb1fe3b1696b1uL.toLong(),
        0x9bdc06a725c71235uL.toLong(),
        0xc19bf174cf692694uL.toLong(),
        0xe49b69c19ef14ad2uL.toLong(),
        0xefbe4786384f25e3uL.toLong(),
        0xfc19dc68b8cd5b5uL.toLong(),
        0x240ca1cc77ac9c65uL.toLong(),
        0x2de92c6f592b0275uL.toLong(),
        0x4a7484aa6ea6e483uL.toLong(),
        0x5cb0a9dcbd41fbd4uL.toLong(),
        0x76f988da831153b5uL.toLong(),
        0x983e5152ee66dfabuL.toLong(),
        0xa831c66d2db43210uL.toLong(),
        0xb00327c898fb213fuL.toLong(),
        0xbf597fc7beef0ee4uL.toLong(),
        0xc6e00bf33da88fc2uL.toLong(),
        0xd5a79147930aa725uL.toLong(),
        0x6ca6351e003826fuL.toLong(),
        0x142929670a0e6e70uL.toLong(),
        0x27b70a8546d22ffcuL.toLong(),
        0x2e1b21385c26c926uL.toLong(),
        0x4d2c6dfc5ac42aeduL.toLong(),
        0x53380d139d95b3dfuL.toLong(),
        0x650a73548baf63deuL.toLong(),
        0x766a0abb3c77b2a8uL.toLong(),
        0x81c2c92e47edaee6uL.toLong(),
        0x92722c851482353buL.toLong(),
        0xa2bfe8a14cf10364uL.toLong(),
        0xa81a664bbc423001uL.toLong(),
        0xc24b8b70d0f89791uL.toLong(),
        0xc76c51a30654be30uL.toLong(),
        0xd192e819d6ef5218uL.toLong(),
        0xd69906245565a910uL.toLong(),
        0xf40e35855771202auL.toLong(),
        0x106aa07032bbd1b8uL.toLong(),
        0x19a4c116b8d2d0c8uL.toLong(),
        0x1e376c085141ab53uL.toLong(),
        0x2748774cdf8eeb99uL.toLong(),
        0x34b0bcb5e19b48a8uL.toLong(),
        0x391c0cb3c5c95a63uL.toLong(),
        0x4ed8aa4ae3418acbuL.toLong(),
        0x5b9cca4f7763e373uL.toLong(),
        0x682e6ff3d6b2b8a3uL.toLong(),
        0x748f82ee5defb2fcuL.toLong(),
        0x78a5636f43172f60uL.toLong(),
        0x84c87814a1f0ab72uL.toLong(),
        0x8cc702081a6439ecuL.toLong(),
        0x7ba0ea2d98160007uL.toLong(),
        0x7eabf2d0c21f964auL.toLong(),
        0x8dbe8d038b409545uL.toLong(),
        0x90bb1721582e8285uL.toLong(),
        0x99a2ad45936d4e61uL.toLong(),
        0x9f86e289fe03e73auL.toLong(),
        0xa84c4472faa9a82fuL.toLong(),
        0xb3df34fce89e0532uL.toLong(),
        0xb99bb8d7b173534fuL.toLong(),
        0xbc76cbab1aea1f9cuL.toLong(),
        0xc226a69a780f3cc3uL.toLong(),
        0xd304f19aa233957duL.toLong(),
        0xde1be20a212129dduL.toLong(),
        0xe39bb43755141950uL.toLong(),
        0xee84927cea48ddd2uL.toLong(),
        0xf3edd2773c523b67uL.toLong(),
        0xfbfdfe53a8d32f2auL.toLong(),
        0xbee2c7ab77e9e25uL.toLong(),
        0xe90181cf1b09e56uL.toLong(),
        0x25f57204c725bed8uL.toLong(),
    )

    private val IV = longArrayOf(
        0x6a09e667f3bcc908uL.toLong(),
        0xbb67ae8584caa73buL.toLong(),
        0x3c6ef372fe94f82buL.toLong(),
        0xa54ff53a5f1d36f1uL.toLong(),
        0x510e527fade682d1uL.toLong(),
        0x9b05688c2b3e6c1fuL.toLong(),
        0x1f83d9abfb41bd6buL.toLong(),
        0x5be0cd19137e2179uL.toLong(),
    )

    private const val ALPHABET = "AB45STUVWZEFGJ6CH01D237IXYPQRKLMN89"
    private const val DAILY_ALPHABET = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
    private const val HKEY_KEY = "LFEJGJJOKCEHODNMNFIKKONFBKIHJFKB"

    private val POLY_HKEY: Int = 0x88a4b5a0.toInt()
    private val POLY_RND: Int = 0xad350290.toInt()

    private val random = SecureRandom()

    data class NativeSignature(val time: String, val nonce: String, val hkey: String, val rnd: String)
    data class WebSignature(val time: String, val nonce: String, val hkey: String)

    fun nativeSignature(
        path: String,
        timeSeconds: Long,
        debugInfo: String,
        osVersion: String,
        version: String = APP_VERSION,
    ): NativeSignature {
        // 与上游一致：本函数不做路径归一化，调用方先用 normalizePath 处理
        val normalized = path
        val time = timeSeconds.toString()

        val stdDigest = hmacSha512(
            HKEY_KEY.toByteArray(Charsets.US_ASCII),
            (normalized + time + debugInfo).toByteArray(Charsets.UTF_8),
        )
        val customDigest = customHmacSha512(
            eE2(normalized + time),
            eE3(debugInfo + osVersion + version),
        ).toHexLower()

        return NativeSignature(
            time = time,
            nonce = makeNativeNonce(timeSeconds),
            hkey = hex(crc32Reflected(stdDigest, POLY_HKEY)),
            rnd = SHADER_VERSION + ":" + hex(crc32Reflected(customDigest.toByteArray(Charsets.US_ASCII), POLY_RND)),
        )
    }

    fun makeNativeNonce(timeSeconds: Long): String {
        val date = Instant.ofEpochSecond(timeSeconds).atZone(ZoneOffset.UTC).toLocalDate()
        val day = date.year * 10000 + date.monthValue * 100 + date.dayOfMonth
        val alphabet = buildString(32) {
            repeat(32) { append(DAILY_ALPHABET[(day + 705 + it * 7) % 62]) }
        }
        return buildString(32) { repeat(32) { append(alphabet[random.nextInt(alphabet.length)]) } }
    }

    fun webSignature(
        path: String,
        timeSeconds: Long = System.currentTimeMillis() / 1000,
        nonce: String = randomNonce(),
    ): WebSignature {
        val timePart = mapChars((timeSeconds + 1).toString(), ALPHABET.dropLast(2))
        val pathPart = mapChars(normalizePath(path), ALPHABET)
        val noncePart = mapChars(nonce, ALPHABET)

        val maxLen = maxOf(timePart.length, pathPart.length, noncePart.length)
        val mixed = buildString {
            for (i in 0 until maxLen) {
                if (i < timePart.length) append(timePart[i])
                if (i < pathPart.length) append(pathPart[i])
                if (i < noncePart.length) append(noncePart[i])
            }
        }

        val digest = md5Hex(mixed.take(20))
        val input = digest.takeLast(6).map { it.code }
        val transforms = listOf(::sigmaS, ::sigmaC, ::sigmaK, ::triple)
        var total = input[4] + input[5]
        for (row in 0 until 4) {
            var acc = 0
            for (col in 0 until 4) acc = acc xor transforms[(col - row + 4) % 4](input[col])
            total += acc
        }

        val hkey = mapChars(digest.take(5), ALPHABET.dropLast(4)) + (total % 100).toString().padStart(2, '0')
        return WebSignature(time = timeSeconds.toString(), nonce = nonce, hkey = hkey)
    }

    fun normalizePath(path: String): String =
        "/" + path.split('/').filter { it.isNotEmpty() }.joinToString("/") + "/"

    private fun randomNonce(): String = ByteArray(16).also { random.nextBytes(it) }.toHexUpper()

    private fun md5Hex(value: String): String =
        MessageDigest.getInstance("MD5").digest(value.toByteArray(Charsets.UTF_8)).toHexLower()

    private fun ror(x: Long, bits: Int): Long = (x ushr bits) or (x shl (64 - bits))
    private fun bigSigma0(x: Long) = ror(x, 21) xor ror(x, 31) xor ror(x, 39)
    private fun bigSigma1(x: Long) = ror(x, 14) xor ror(x, 18) xor ror(x, 41)
    private fun smallSigma0(x: Long) = ror(x, 1) xor ror(x, 8) xor (x ushr 7)
    private fun smallSigma1(x: Long) = ror(x, 38) xor ror(x, 55) xor (x ushr 6)
    private fun ch(x: Long, y: Long, z: Long) = (x and (y xor z)) xor y
    private fun maj(x: Long, y: Long, z: Long) = (x and (y or z)) or (y and z)

    fun customSha512(input: ByteArray): ByteArray {
        val paddedSize = ((input.size + 1 + 16 + 127) / 128) * 128
        val padded = ByteArray(paddedSize)
        input.copyInto(padded)
        padded[input.size] = 0x80.toByte()
        val bits = input.size.toLong() * 8
        writeLongBE(padded, paddedSize - 16, 0L)
        writeLongBE(padded, paddedSize - 8, bits)

        val state = IV.copyOf()
        val w = LongArray(80)
        var offset = 0
        while (offset < paddedSize) {
            for (i in 0 until 16) w[i] = readLongBE(padded, offset + i * 8)
            for (i in 16 until 80) w[i] = smallSigma1(w[i - 2]) + w[i - 7] + smallSigma0(w[i - 15]) + w[i - 16]

            var a = state[0]
            var b = state[1]
            var c2 = state[2]
            var d = state[3]
            var e = state[4]
            var f = state[5]
            var g = state[6]
            var h = state[7]

            for (i in 0 until 80) {
                val t1 = h + bigSigma1(e) + ch(e, f, g) + K[i] + w[i]
                val t2 = bigSigma0(a) + maj(a, b, c2)
                val newE = d + t1
                h = g; g = f; f = e; e = newE; d = c2; c2 = b; b = a; a = t1 + t2
            }

            state[0] += a; state[1] += b; state[2] += c2; state[3] += d
            state[4] += e; state[5] += f; state[6] += g; state[7] += h
            offset += 128
        }

        val out = ByteArray(64)
        for (i in 0 until 8) writeLongBE(out, i * 8, state[i])
        return out
    }

    /** 自定义 HMAC：ipad/opad 为 0x52 / 0x6c。 */
    fun customHmacSha512(keyInput: ByteArray, data: ByteArray): ByteArray {
        var key = keyInput
        if (key.size > 128) key = customSha512(key)
        val ipad = ByteArray(128) { 0x52 }
        val opad = ByteArray(128) { 0x6c }
        for (i in key.indices) {
            ipad[i] = (ipad[i].toInt() xor key[i].toInt()).toByte()
            opad[i] = (opad[i].toInt() xor key[i].toInt()).toByte()
        }
        return customSha512(opad + customSha512(ipad + data))
    }

    private fun hmacSha512(key: ByteArray, data: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA512")
        mac.init(SecretKeySpec(key, "HmacSHA512"))
        return mac.doFinal(data)
    }

    fun eE2(input: String): ByteArray {
        val bytes = input.toByteArray(Charsets.UTF_8)
        val nul = bytes.indexOfFirst { it.toInt() == 0 }
        val end = minOf(127, if (nul < 0) bytes.size else nul)
        val out = bytes.copyOfRange(0, end)
        for (i in out.indices) {
            val c = out[i].toInt() and 0xff
            val x = (c and 0x7f) xor 0x37
            out[i] = (if (x == 0 || (i + 1) % 3 == 0) ((c and 7) shl 4) xor 0x37 else x).toByte()
        }
        return out
    }

    fun eE3(input: String): ByteArray {
        val out = eE2(input)
        for (i in out.indices) {
            val c = out[i].toInt() and 0xff
            val x = (c and 0x7f) xor 0x46
            out[i] = (if (x == 0 || (i + 1) % 5 == 0) ((c and 31) shl 2) xor 0x46 else x).toByte()
        }
        return out
    }

    private fun crc32Reflected(bytes: ByteArray, polynomial: Int): Int {
        var crc = -1
        for (b in bytes) {
            crc = crc xor (b.toInt() and 0xff)
            repeat(8) { crc = (crc ushr 1) xor (if (crc and 1 != 0) polynomial else 0) }
        }
        return crc xor -1
    }

    private fun hex(value: Int): String = Integer.toHexString(value).uppercase()

    private fun mapChars(text: String, chars: String): String = buildString(text.length) {
        for (ch in text) append(chars[ch.code % chars.length])
    }

    private fun doubleByte(n: Int): Int = if (n and 128 != 0) ((n shl 1) xor 27) and 255 else n shl 1
    private fun triple(n: Int): Int = doubleByte(n) xor n
    private fun sigmaK(n: Int): Int = triple(doubleByte(n))
    private fun sigmaC(n: Int): Int = sigmaK(triple(doubleByte(n)))
    private fun sigmaS(n: Int): Int = sigmaC(n) xor sigmaK(n) xor triple(n)

    private fun readLongBE(b: ByteArray, offset: Int): Long {
        var value = 0L
        for (i in 0 until 8) value = (value shl 8) or (b[offset + i].toLong() and 0xff)
        return value
    }

    private fun writeLongBE(b: ByteArray, offset: Int, value: Long) {
        for (i in 0 until 8) b[offset + i] = ((value ushr (56 - 8 * i)) and 0xff).toByte()
    }

    private fun ByteArray.toHexLower(): String = joinToString("") { "%02x".format(it) }
    private fun ByteArray.toHexUpper(): String = joinToString("") { "%02X".format(it) }
}
