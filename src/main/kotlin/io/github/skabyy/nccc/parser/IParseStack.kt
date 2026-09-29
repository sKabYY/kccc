package io.github.skabyy.nccc.parser

import io.github.skabyy.nccc.common.ListSExp
import io.github.skabyy.nccc.scanner.TokenStream

interface IParseStack {
    fun isEmpty(): Boolean
    fun has(parser: IParser, toks: TokenStream): Boolean
    fun extend(parser: IParser, toks: TokenStream): IParseStack
    fun toSExp(): ListSExp
}
