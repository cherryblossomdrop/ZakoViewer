package moe.zako.viewer.core.search

import moe.zako.viewer.data.source.cosav.CosVideoItem

data class ParsedSearchQuery(
    val rawQuery: String,
    val mainKeyword: String,
    val andTerms: List<String>,
    val notTerms: List<String>,
    val orTerms: List<String>
) {
    val isCompound: Boolean
        get() = andTerms.isNotEmpty() || notTerms.isNotEmpty() || orTerms.size > 1

    fun matches(video: CosVideoItem): Boolean {
        val searchableText = buildString {
            append(video.title).append(' ')
            append(video.barcode ?: "").append(' ')
            append(video.id).append(' ')
            append(video.tagList.joinToString(" ")).append(' ')
            append(video.company ?: "").append(' ')
            append(video.series ?: "").append(' ')
            append(video.cosRole ?: "").append(' ')
            append(video.author ?: "").append(' ')
            append(video.cosWorks ?: "").append(' ')
            append(video.channelName ?: "")
        }

        // 1. Check NOT terms: must NOT match any
        for (notTerm in notTerms) {
            if (searchableText.contains(notTerm, ignoreCase = true)) {
                return false
            }
        }

        // 2. Check AND terms: must match ALL
        for (andTerm in andTerms) {
            if (!searchableText.contains(andTerm, ignoreCase = true)) {
                return false
            }
        }

        // 3. Check OR terms: if non-empty, must match at least ONE
        if (orTerms.isNotEmpty()) {
            val matchedAny = orTerms.any { searchableText.contains(it, ignoreCase = true) }
            if (!matchedAny) {
                return false
            }
        }

        return true
    }
}

object SearchQueryParser {
    /**
     * Parse query string by whitespace tokens.
     * Leading '+' indicates AND (must match)
     * Leading '-' indicates NOT (must exclude)
     * Words without +/- indicate OR (match any)
     */
    fun parse(query: String): ParsedSearchQuery {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            return ParsedSearchQuery(
                rawQuery = "",
                mainKeyword = "",
                andTerms = emptyList(),
                notTerms = emptyList(),
                orTerms = emptyList()
            )
        }

        val tokens = trimmed.split(Regex("\\s+")).filter { it.isNotEmpty() }
        val andList = mutableListOf<String>()
        val notList = mutableListOf<String>()
        val orList = mutableListOf<String>()

        for (token in tokens) {
            when {
                token.startsWith("+") && token.length > 1 -> {
                    andList.add(token.substring(1))
                }
                token.startsWith("-") && token.length > 1 -> {
                    notList.add(token.substring(1))
                }
                else -> {
                    orList.add(token.removePrefix("+"))
                }
            }
        }

        // Determine main keyword to send to remote API
        val mainKeyword = orList.firstOrNull() ?: andList.firstOrNull() ?: ""

        return ParsedSearchQuery(
            rawQuery = trimmed,
            mainKeyword = mainKeyword,
            andTerms = andList,
            notTerms = notList,
            orTerms = orList
        )
    }

    /**
     * 校验搜索输入是否为当前版本对应的 Base64 数据源设置解锁暗号
     * 动态计算 BuildConfig.VERSION_NAME 的标准 Base64 编码 (NO_WRAP)
     */
    fun isDataSourceUnlockCipher(raw: String): Boolean {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return false
        val expectedCipher = android.util.Base64.encodeToString(
            moe.zako.viewer.BuildConfig.VERSION_NAME.toByteArray(Charsets.UTF_8),
            android.util.Base64.NO_WRAP
        ).trim()
        return trimmed == expectedCipher
    }
}
