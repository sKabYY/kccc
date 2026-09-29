package io.github.skabyy.kccc.parser

import io.github.skabyy.kccc.scanner.TokenStream

internal class MemorizedParser(private val parser: IParser) : IParser {

    // NOTE: like the C# original, the memo is keyed by offset only and is NOT
    // keyed by the parse stack, so parser instances are not thread-safe.
    private val memo = HashMap<Int, ParseResult>()

    override fun parse(toks: TokenStream, stk: IParseStack): ParseResult {
        val offset = toks.car().start.offset
        var r = memo[offset]
        if (r == null) {
            r = parser.parse(toks, stk)
            memo[offset] = r
        }
        return r
    }

    fun clear() {
        memo.clear()
    }

    override fun toString(): String {
        return parser.toString()
    }
}
