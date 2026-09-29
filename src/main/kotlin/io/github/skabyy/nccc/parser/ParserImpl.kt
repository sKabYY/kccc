package io.github.skabyy.nccc.parser

import io.github.skabyy.nccc.scanner.TokenStream

internal class ParserImpl(private val _name: String?, private val _parse: ParseFunction) : IParser {

    constructor(parse: ParseFunction) : this(null, parse)

    override fun parse(toks: TokenStream, stk: IParseStack): ParseResult {
        if (stk.has(this, toks)) {
            fatal("left-recursion detected", this, toks, stk)
        }
        val result = _parse(toks, stk.extend(this, toks))
        result.parserName = _name
        return result
    }

    override fun toString(): String {
        return if (_name == null) super.toString() else "<Parser:$_name>"
    }
}
