package io.github.skabyy.kccc.common

abstract class SExp {

    abstract fun toPrettyString(firstOfList: Boolean = false, indent: Int = 0): String

    companion object {
        /**
         * NOTE: the C# original returns `null` when called with an explicit
         * `null` argument array (`SExp.List(null)`). Kotlin varargs can never
         * be a null array, so the single call site relying on that behavior
         * (`ParseResult.toSExp`) reproduces the null case manually.
         */
        @JvmStatic
        fun list(vararg os: Any?): ListSExp {
            return ListSExp(*os)
        }

        @JvmStatic
        fun value(obj: Any?): ValueSExp {
            return ValueSExp(obj)
        }
    }
}

// file-private replacement of the C# `protected static SExp.EnsureSExp`
private fun ensureSExp(obj: Any?): SExp {
    return obj as? SExp ?: ValueSExp(obj)
}

class ListSExp(vararg os: Any?) : SExp() {

    private val sexps: MutableList<SExp> = os.map { ensureSExp(it) }.toMutableList()

    fun push(vararg os: Any?) {
        for (o in os) {
            sexps.add(ensureSExp(o))
        }
    }

    fun pushFront(o: Any?) {
        sexps.add(0, ensureSExp(o))
    }

    fun append(e: ListSExp) {
        sexps.addAll(e.sexps)
    }

    override fun toPrettyString(firstOfList: Boolean, indent: Int): String {
        val sameLine = firstOfList
        val sb = StringBuilder()
        if (!sameLine) {
            sb.append(" ".repeat(indent))
        }
        sb.append('(')
        if (sexps.isNotEmpty()) {
            val newIndent = indent + 1
            val first = sexps.first().toPrettyString(true, newIndent)
            sb.append(first)
            if (sexps.all { it is ValueSExp }) {
                val maxLineLength = 80
                var curLength = first.length
                for (sexp in sexps.drop(1)) {
                    if (curLength < maxLineLength) {
                        val str = sexp.toPrettyString(true, newIndent)
                        sb.append(' ')
                        sb.append(str)
                        curLength += str.length + 1
                    } else {
                        val str = sexp.toPrettyString(false, newIndent)
                        sb.append('\n')
                        sb.append(str)
                        curLength = str.length
                    }
                }
            } else {
                for (sexp in sexps.drop(1)) {
                    sb.append('\n')
                    sb.append(sexp.toPrettyString(false, newIndent))
                }
            }
        }
        sb.append(')')
        return sb.toString()
    }
}

class ValueSExp(private val value: Any?) : SExp() {

    override fun toPrettyString(firstOfList: Boolean, indent: Int): String {
        var s = value?.toString() ?: "<null>"
        if (value is String && s.any { it.isWhitespace() }) {
            s = "\"" + s.replace("\"", "\\\"") + "\""
        }
        if (firstOfList || indent == 0) return s
        return " ".repeat(indent) + s
    }
}
