package io.github.skabyy.kccc.common

class TextPosition(val offset: Int, val linenum: Int, val colnum: Int) {

    fun back(): TextPosition {
        if (offset == 0) {
            // FIX: the C# original constructed an InvalidOperationException
            // but forgot to `throw` it. The port restores the intended behavior.
            throw IllegalStateException("could not Back() when Offset==0")
        }
        return if (colnum == 1) {
            TextPosition(offset - 1, linenum - 1, 1)
        } else {
            TextPosition(offset - 1, linenum, colnum - 1)
        }
    }

    fun shift(prefix: String): TextPosition {
        if (prefix.isEmpty()) return this
        val lines = prefix.split('\n')
        return if (lines.size == 1) {
            TextPosition(offset + prefix.length, linenum, colnum + lines[0].length)
        } else {
            TextPosition(offset + prefix.length, linenum + lines.size - 1, lines[0].length + 1)
        }
    }

    fun shift(c: Char): TextPosition {
        return if (c == '\n') {
            TextPosition(offset + 1, linenum + 1, 1)
        } else {
            TextPosition(offset + 1, linenum, colnum + 1)
        }
    }

    fun shiftToEnd(str: String): TextPosition {
        return shift(str.substring(offset))
    }

    companion object {
        @JvmField
        val startPos = TextPosition(0, 1, 1)
    }
}
