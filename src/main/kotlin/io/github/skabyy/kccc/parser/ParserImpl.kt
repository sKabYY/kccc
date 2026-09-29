package io.github.skabyy.kccc.parser

import io.github.skabyy.kccc.scanner.TokenStream

internal class ParserImpl(private val name: String?, private val parseFn: ParseFunction) : IParser {

    constructor(parse: ParseFunction) : this(null, parse)

    override fun parse(toks: TokenStream, stk: IParseStack): ParseResult {
        if (stk.has(this, toks)) {
            fatal("left-recursion detected", this, toks, stk)
        }
        val result = parseFn(toks, stk.extend(this, toks))
        result.parserName = name
        return result
    }

    override fun toString(): String {
        return if (name == null) super.toString() else "<Parser:$name>"
    }
}
