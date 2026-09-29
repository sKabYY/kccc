package io.github.skabyy.kccc.parser

import io.github.skabyy.kccc.common.ListSExp
import io.github.skabyy.kccc.common.SExp
import io.github.skabyy.kccc.scanner.TokenStream

internal class ParseStack private constructor(
    private val parser: IParser?,
    private val toks: TokenStream?,
    private val prev: IParseStack?
) : IParseStack {

    override fun isEmpty(): Boolean {
        return this === Empty
    }

    override fun has(parser: IParser, toks: TokenStream): Boolean {
        if (isEmpty()) return false
        // `parser === this.parser` is reference equality (as in C#);
        // `toks == this.toks` calls the custom TokenStream.Equals
        // (see TokenStream for its semantics).
        if (parser === this.parser && toks == this.toks) return true
        return prev!!.has(parser, toks)
    }

    override fun extend(parser: IParser, toks: TokenStream): IParseStack {
        return ParseStack(parser, toks, this)
    }

    override fun toSExp(): ListSExp {
        if (!isEmpty()) {
            val lst = SExp.list(
                SExp.list(
                    SExp.list("parser", parser!!.toString()),
                    SExp.list("toks", toks!!.toString())
                )
            )
            lst.append(prev!!.toSExp())
            return lst
        }
        return SExp.list()
    }

    companion object {
        val Empty: IParseStack = ParseStack(null, null, null)
    }
}
