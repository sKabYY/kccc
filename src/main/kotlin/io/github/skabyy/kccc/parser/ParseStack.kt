package io.github.skabyy.kccc.parser

import io.github.skabyy.kccc.common.ListSExp
import io.github.skabyy.kccc.common.SExp
import io.github.skabyy.kccc.scanner.TokenStream

/**
 * Left-recursion detection stack.
 *
 * Sealed into a data-carrying [Item] and the [Empty] singleton so that
 * parser/toks/prev are structurally non-null (the C# original kept them
 * nullable with a static `Empty` instance; splitting the cases moves the
 * nullability out of the type).
 */
internal sealed class ParseStack : IParseStack {

    override fun isEmpty(): Boolean {
        return this === Empty
    }

    internal class Item(
        val parser: IParser,
        val toks: TokenStream,
        val prev: IParseStack
    ) : ParseStack() {

        override fun has(parser: IParser, toks: TokenStream): Boolean {
            // `this.parser === parser` is reference equality (as in C#);
            // `this.toks == toks` calls the custom TokenStream.Equals
            // (see TokenStream for its semantics).
            if (this.parser === parser && this.toks == toks) return true
            return prev.has(parser, toks)
        }

        override fun extend(parser: IParser, toks: TokenStream): IParseStack {
            return Item(parser, toks, this)
        }

        override fun toSExp(): ListSExp {
            val lst = SExp.list(
                SExp.list(
                    SExp.list("parser", parser.toString()),
                    SExp.list("toks", toks.toString())
                )
            )
            lst.append(prev.toSExp())
            return lst
        }
    }

    object Empty : ParseStack() {

        override fun has(parser: IParser, toks: TokenStream): Boolean {
            return false
        }

        override fun extend(parser: IParser, toks: TokenStream): IParseStack {
            return Item(parser, toks, this)
        }

        override fun toSExp(): ListSExp {
            return SExp.list()
        }
    }
}
