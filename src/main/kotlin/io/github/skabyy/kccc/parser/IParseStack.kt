package io.github.skabyy.kccc.parser

import io.github.skabyy.kccc.common.ListSExp
import io.github.skabyy.kccc.scanner.TokenStream

interface IParseStack {
    fun isEmpty(): Boolean
    fun has(parser: IParser, toks: TokenStream): Boolean
    fun extend(parser: IParser, toks: TokenStream): IParseStack
    fun toSExp(): ListSExp
}
