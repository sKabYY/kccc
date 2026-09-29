package io.github.skabyy.kccc.common

class Locale {

    var language: String? = null

    fun l(s: String): String {
        tryGetFromLocales(locales, s)?.let { return it }
        tryGetFromLocales(globalLocales, s)?.let { return it }
        return s
    }

    fun set(lang: String, entries: Map<String, String>) {
        val dict = locales[lang]
        if (dict != null) {
            dict.putAll(entries)
        } else {
            locales[lang] = HashMap(entries)
        }
    }

    private fun tryGetFromLocales(locales: Map<String, Map<String, String>>, s: String): String? {
        val lang = language ?: return null
        val dict = locales[lang] ?: return null
        return dict[s]
    }

    private val locales = HashMap<String, MutableMap<String, String>>()

    companion object {
        private val globalLocales: Map<String, Map<String, String>> = mapOf(
            "zh-cn" to mapOf(
                "expect <<EOF>>" to "分析已结束，但文本还有内容",
                "expect" to "应为",
                "not match regex" to "无法匹配正则",
                "reach eof" to "文本已读到末尾",
                "is undefined" to "未定义",
                "block comment match error" to "注释结束标记缺失",
                "string match error" to "字符串结束标记缺失",
                "regex match error" to "正则结束标记缺失"
            )
        )
    }
}
