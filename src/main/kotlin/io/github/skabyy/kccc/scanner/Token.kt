package io.github.skabyy.kccc.scanner

import io.github.skabyy.kccc.common.TextPosition
import io.github.skabyy.kccc.exceptions.ReachEofException

class Token private constructor(private val _value: Char?, val start: TextPosition) {

    val end: TextPosition = if (_value != null) start.shift(_value) else start

    val value: Char
        get() {
            if (_value != null) return _value
            throw ReachEofException()
        }

    fun isEof(): Boolean {
        return _value == null
    }

    override fun toString(): String {
        return if (isEof()) {
            "<EOF at=(${start.linenum}, ${start.colnum})>"
        } else {
            "<Token char='$value', from=(${start.linenum}, ${start.colnum}), to=(${end.linenum}, ${end.colnum})>"
        }
    }

    companion object {

        @JvmStatic
        fun make(value: Char, start: TextPosition): Token {
            return Token(value, start)
        }

        @JvmStatic
        fun eof(start: TextPosition): Token {
            return Token(null, start)
        }
    }
}
