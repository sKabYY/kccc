package io.github.skabyy.kccc.scanner

import io.github.skabyy.kccc.common.TextPosition

class CharScanner {

    fun scan(str: String): TokenStream {
        return TokenStream(this, str)
    }

    fun scan1(str: String, start: TextPosition): Token {
        if (str.length == start.offset) {
            return Token.eof(start)
        }
        return Token.make(str[start.offset], start)
    }
}
