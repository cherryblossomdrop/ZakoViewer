package moe.zako.viewer.core.share

object ShareCipherUtil {
    /**
     * 暗号专用识别标记（日常输入绝不会包含的特定汉字组合）
     */
    const val CIPHER_MARKER = "鹤鸣九皋声闻野"

    /**
     * 16个无敏感含义的清雅常见汉字映射表（对应 0..15 的十六进制半字节）
     */
    private val CIPHER_ALPHABET = charArrayOf(
        '松', '竹', '梅', '兰', '菊', '荷', '柏', '枫',
        '云', '月', '星', '泉', '石', '溪', '风', '雨'
    )

    private val REVERSE_MAP: Map<Char, Int> = CIPHER_ALPHABET.mapIndexed { index, char -> char to index }.toMap()

    /**
     * 将视频 ID 编码为完全 SFW 的中文诗意短句暗号
     */
    fun encode(videoId: String): String {
        val trimmedId = videoId.trim()
        val bytes = trimmedId.toByteArray(Charsets.UTF_8)
        val encodedPayload = StringBuilder(bytes.size * 2)
        for (b in bytes) {
            val unsigned = b.toInt() and 0xFF
            val high = unsigned shr 4
            val low = unsigned and 0x0F
            encodedPayload.append(CIPHER_ALPHABET[high])
            encodedPayload.append(CIPHER_ALPHABET[low])
        }
        // 外层包装为自然、正常、完全 SFW 的中文短句
        return "春水碧于天，画船听雨眠。${CIPHER_MARKER}【$encodedPayload】明月松间照，清泉石上流。"
    }

    /**
     * 检测文本中是否包含暗号专用识别标记
     */
    fun containsCipherMarker(text: String): Boolean {
        return text.contains(CIPHER_MARKER)
    }

    /**
     * 从暗号文本中解码提取出原始视频 ID
     * @return 成功返回视频 ID，解码失败返回 null
     */
    fun decode(text: String): String? {
        if (!containsCipherMarker(text)) return null

        try {
            // 优先提取【】或[]中的暗号字符；若无括号则从标记之后查找连续的暗号汉字
            val markerIndex = text.indexOf(CIPHER_MARKER)
            val subAfterMarker = text.substring(markerIndex + CIPHER_MARKER.length)

            val rawChars = if (subAfterMarker.contains("【") && subAfterMarker.contains("】")) {
                val start = subAfterMarker.indexOf("【") + 1
                val end = subAfterMarker.indexOf("】", start)
                if (end > start) subAfterMarker.substring(start, end) else ""
            } else if (subAfterMarker.contains("[") && subAfterMarker.contains("]")) {
                val start = subAfterMarker.indexOf("[") + 1
                val end = subAfterMarker.indexOf("]", start)
                if (end > start) subAfterMarker.substring(start, end) else ""
            } else {
                subAfterMarker
            }

            // 过滤提取出所有属于映射表的汉字
            val cipherChars = rawChars.filter { REVERSE_MAP.containsKey(it) }
            if (cipherChars.isEmpty() || cipherChars.length % 2 != 0) {
                return null
            }

            val byteArray = ByteArray(cipherChars.length / 2)
            for (i in byteArray.indices) {
                val highChar = cipherChars[i * 2]
                val lowChar = cipherChars[i * 2 + 1]
                val high = REVERSE_MAP[highChar] ?: return null
                val low = REVERSE_MAP[lowChar] ?: return null
                byteArray[i] = ((high shl 4) or low).toByte()
            }

            val result = String(byteArray, Charsets.UTF_8).trim()
            return if (result.isNotEmpty()) result else null
        } catch (_: Exception) {
            return null
        }
    }
}
