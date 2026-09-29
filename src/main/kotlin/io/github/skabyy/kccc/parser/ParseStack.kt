package io.github.skabyy.kccc.parser

import io.github.skabyy.kccc.common.ListSExp
import io.github.skabyy.kccc.common.SExp
import io.github.skabyy.kccc.scanner.TokenStream

internal class ParseStack private constructor(
    private val _parser: IParser?,
    private val _toks: TokenStream?,
    private val _prev: IParseStack?
) : IParseStack {

    override fun isEmpty(): Boolean {
        return this === Empty
    }

    override fun has(parser: IParser, toks: TokenStream): Boolean {
        if (isEmpty()) return false
        // `parser == _parser` is reference equality in C#; `toks.Equals(_toks)`
        // calls the custom TokenStream.Equals (see TokenStream for its semantics).
        if (parser === _parser && toks == _toks) return true
        return _prev!!.has(parser, toks)
    }

    override fun extend(parser: IParser, toks: TokenStream): IParseStack {
        return ParseStack(parser, toks, this)
    }

    override fun toSExp(): ListSExp {
        if (!isEmpty()) {
            val lst = SExp.list(
                SExp.list(
                    SExp.list("parser", _parser!!.toString()),
                    SExp.list("toks", _toks!!.toString())
                )
            )
            lst.append(_prev!!.toSExp())
            return lst
        }
        return SExp.list()
    }

    companion object {
        val Empty: IParseStack = ParseStack(null, null, null)
    }
}
