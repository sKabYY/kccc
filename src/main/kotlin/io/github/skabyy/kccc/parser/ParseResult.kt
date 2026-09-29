package io.github.skabyy.kccc.parser

import io.github.skabyy.kccc.common.ListSExp
import io.github.skabyy.kccc.common.SExp
import io.github.skabyy.kccc.common.TextPosition
import io.github.skabyy.kccc.scanner.TokenStream

/**
 * Result of a parse, sealed into [Success] and [Failure] so the machinery can
 * branch on the outcome and the established invariants become types:
 * `rest`/`failRest` are always set, a [Failure] always carries a message, and
 * a [Success] always carries its (possibly empty) nodes.
 */
sealed class ParseResult {

    abstract val nodes: List<Node>?
    abstract val rest: TokenStream
    abstract val message: String?
    abstract val failRest: TokenStream
    abstract val start: TextPosition?
    abstract val end: TextPosition?
    abstract var parserName: String?

    fun isSuccess(): Boolean {
        return this is Success
    }

    fun deeper(r: ParseResult?): ParseResult {
        if (r == null) return this
        return if (r.failRest.position().offset <= failRest.position().offset) this else r
    }

    fun failResult(): Failure {
        return when (this) {
            is Failure -> this
            is Success -> Failure(nodes, rest, message ?: "parse failed", failRest, start, end, parserName)
        }
    }

    fun toSExp(): ListSExp {
        val list = SExp.list(SExp.list("success?", isSuccess()))
        if (this is Success) {
            list.push(
                SExp.list("nodes", nodesSExp()),
                SExp.list("rest", rest),
                SExp.list("message", message),
                SExp.list("fail_rest", failRest)
            )
        } else {
            list.push(
                SExp.list("message", message),
                SExp.list("fail_rest", failRest),
                SExp.list("rest", rest),
                SExp.list("nodes", nodesSExp())
            )
        }
        if (!parserName.isNullOrEmpty()) {
            list.pushFront(SExp.list(SExp.list("parser", parserName)))
        }
        return list
    }

    override fun toString(): String {
        return toSExp().toPrettyString()
    }

    /**
     * Mirrors the C# `SExp.List(Nodes?.Select(n => n.ToSExp()).ToArray())`:
     * when Nodes is null the C# call passes a null array, `SExp.List`
     * returns null, and the element is rendered as `<null>`.
     */
    private fun nodesSExp(): Any? {
        return nodes?.map { it.toSExp() }?.toTypedArray()?.let { SExp.list(*it) }
    }

    class Success(
        override val nodes: List<Node>,
        override val rest: TokenStream,
        override val message: String?,
        override val failRest: TokenStream,
        override val start: TextPosition?,
        override val end: TextPosition?,
        override var parserName: String? = null
    ) : ParseResult()

    class Failure(
        override val nodes: List<Node>?,
        override val rest: TokenStream,
        override val message: String,
        override val failRest: TokenStream,
        override val start: TextPosition? = null,
        override val end: TextPosition? = null,
        override var parserName: String? = null
    ) : ParseResult()
}
