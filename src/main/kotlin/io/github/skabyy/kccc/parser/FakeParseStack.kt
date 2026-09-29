package io.github.skabyy.kccc.parser

import io.github.skabyy.kccc.common.ListSExp
import io.github.skabyy.kccc.common.SExp
import io.github.skabyy.kccc.scanner.TokenStream

internal class FakeParseStack private constructor() : IParseStack {

    override fun extend(parser: IParser, toks: TokenStream): IParseStack {
        return this
    }

    override fun has(parser: IParser, toks: TokenStream): Boolean {
        return false
    }

    override fun isEmpty(): Boolean {
        return true
    }

    override fun toSExp(): ListSExp {
        return SExp.list()
    }

    companion object {
        val Empty: IParseStack = FakeParseStack()
    }
}
