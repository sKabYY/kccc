package io.github.skabyy.kccc.scanner

import io.github.skabyy.kccc.common.TextPosition

class TokenStream(private val _scanner: CharScanner, val str: String, pos: TextPosition) {

    private val _cur: Token = _scanner.scan1(str, pos)

    constructor(charScanner: CharScanner, str: String) : this(charScanner, str, TextPosition.startPos)

    fun car(): Token {
        return _cur
    }

    fun cdr(): TokenStream {
        return TokenStream(_scanner, str, _cur.end)
    }

    fun isEndOfStream(): Boolean {
        return _cur.isEof()
    }

    fun position(): TextPosition {
        return _cur.start
    }

    override fun toString(): String {
        return "<Stream: car=${car()}>"
    }

    override fun equals(other: Any?): Boolean {
        if (other !is TokenStream) return false
        // Mirrors the C# `Equals` override: the scanner is compared by
        // reference, the string by value, and the position by *reference*
        // (TextPosition never overrides Equals in the original either).
        return _scanner === other._scanner && str == other.str && position() === other.position()
    }

    override fun hashCode(): Int {
        // The C# original mixes the Token *reference* into its hash code,
        // which is inconsistent with its Equals. TokenStream is never used
        // as a hash key; hashing by the stable offset keeps the general
        // contract without changing observable behavior.
        var hashCode = 1851490821
        hashCode = hashCode * -1521134295 + System.identityHashCode(_scanner)
        hashCode = hashCode * -1521134295 + str.hashCode()
        hashCode = hashCode * -1521134295 + _cur.start.offset
        return hashCode
    }
}
