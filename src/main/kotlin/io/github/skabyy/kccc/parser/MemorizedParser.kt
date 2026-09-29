package io.github.skabyy.kccc.parser

import io.github.skabyy.kccc.scanner.TokenStream

internal class MemorizedParser(private val _parser: IParser) : IParser {

    // NOTE: like the C# original, the memo is keyed by offset only and is NOT
    // keyed by the parse stack, so parser instances are not thread-safe.
    private val _memo = HashMap<Int, ParseResult>()

    override fun parse(toks: TokenStream, stk: IParseStack): ParseResult {
        val offset = toks.car().start.offset
        var r = _memo[offset]
        if (r == null) {
            r = _parser.parse(toks, stk)
            _memo[offset] = r
        }
        return r
    }

    fun clear() {
        _memo.clear()
    }

    override fun toString(): String {
        return _parser.toString()
    }
}
