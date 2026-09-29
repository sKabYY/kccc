package io.github.skabyy.kccc.parser

import io.github.skabyy.kccc.common.ListSExp
import io.github.skabyy.kccc.common.SExp
import io.github.skabyy.kccc.common.TextPosition
import io.github.skabyy.kccc.scanner.TokenStream

class ParseResult {

    var nodes: MutableList<Node>? = null
    var success: Boolean = false
    var rest: TokenStream? = null
    var message: String? = null
    var failRest: TokenStream? = null
    var start: TextPosition? = null
    var end: TextPosition? = null
    var parserName: String? = null

    fun isSuccess(): Boolean {
        return nodes != null && success
    }

    fun deeper(r: ParseResult?): ParseResult {
        if (r == null || r.failRest!!.position().offset <= failRest!!.position().offset) {
            return this
        }
        return r
    }

    fun failResult(): ParseResult {
        val r = ParseResult()
        r.success = false
        r.nodes = nodes
        r.rest = rest
        r.message = message
        r.failRest = failRest
        r.start = start
        r.end = end
        return r
    }

    fun toSExp(): ListSExp {
        val list = SExp.list(SExp.list("success?", isSuccess()))
        if (isSuccess()) {
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
}
