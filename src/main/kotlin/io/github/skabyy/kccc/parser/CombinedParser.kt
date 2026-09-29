package io.github.skabyy.kccc.parser

import io.github.skabyy.kccc.common.Locale
import io.github.skabyy.kccc.common.TextPosition
import io.github.skabyy.kccc.exceptions.ParseException
import io.github.skabyy.kccc.scanner.CharScanner
import io.github.skabyy.kccc.scanner.Token
import io.github.skabyy.kccc.scanner.TokenStream
import java.util.regex.Pattern

open class CombinedParser protected constructor() {

    protected val locale: Locale = Locale()
    protected val scanner: CharScanner = CharScanner()

    protected var leftRecurDetection: Boolean = true
    protected var useMemorizedParser: Boolean = true
    protected var messageLocaleStart: String? = null
    protected var messageLocaleEnd: String? = null

    protected var spacingParser: IParser? = null

    private var rootParser: IParser? = null
    private val env = HashMap<String, IParser>()

    protected fun setRootParser(parser: IParser) {
        rootParser = parser
    }

    fun parse(src: String): ParseResult {
        val root = rootParser ?: throw ParseException("RootParser is uninitialized")
        return parseBy(root, src)
    }

    fun parseBy(parser: String, src: String): ParseResult {
        return parseBy(get(parser), src)
    }

    private fun parseBy(parser: IParser, src: String): ParseResult {
        val toks = scanner.scan(src)
        resetMemorizedParsers()
        return wrapperEof(parser).parse(toks, if (leftRecurDetection) ParseStack.Empty else FakeParseStack.Empty)
    }

    private fun wrapperEof(parser: IParser): IParser {
        return cSeq(pSpacing(), parser, pEof())
    }

    private val memorizedParsers = ArrayList<MemorizedParser>()

    private fun makeMemorizedParser(parser: IParser): MemorizedParser {
        val memorizedParser = MemorizedParser(parser)
        memorizedParsers.add(memorizedParser)
        return memorizedParser
    }

    private fun resetMemorizedParsers() {
        for (p in memorizedParsers) {
            p.clear()
        }
    }

    protected fun defParser(name: String, parser: IParser): IParser {
        val registered = if (useMemorizedParser) makeMemorizedParser(parser) else parser
        envSet(name, registered)
        return get(name)
    }

    protected fun get(name: String): IParser {
        return ParserImpl(name) { toks, stk ->
            val parser = envGet(name)
            parser.parse(toks, stk)
        }
    }

    // =====================================================================
    // builtin parsers
    // =====================================================================

    protected fun pEof(): IParser {
        return cIfFail("expect EOF", cNot(pAny()))
    }

    protected fun pAlpha(): IParser {
        return cIfFail("${locale.l("expect")} alpha", cOr(pRange('a', 'z'), pRange('A', 'Z')))
    }

    protected fun pDigit(): IParser {
        return pRange('0', '9')
    }

    protected fun pSpace(): IParser {
        return pTokenPred("${locale.l("expect")} whitespace") { tok -> tok.value.isWhitespace() }
    }

    protected fun pNewline(): IParser {
        return cOr(pCharArray("\r\n"), pChar('\n'), pChar('\r'))
    }

    protected fun pNumber(): IParser {
        val digits = cPlus(pDigit())
        return cSeq(
            cMaybe(cOr(pChar('+'), pChar('-'))),  /* 符号 */
            digits,                               /* 整数部分 */
            cMaybe(cSeq(pChar('.'), digits)),     /* 小数部分 */
            cMaybe(                               /* 指数部分 */
                cOr(pChar('E'), pChar('e')),
                cMaybe(pChar('-')),
                digits),
            pSpacing()
        )
    }

    protected fun loadBuildinParsers() {
        defParser("alpha", pAlpha())
        defParser("digit", pDigit())
        defParser("number", pNumber())
    }

    // =====================================================================
    // char
    // =====================================================================

    protected fun pTokenPred(failMessage: String, pred: (Token) -> Boolean): IParser {
        return ParserImpl { toks, _ ->
            if (toks.isEndOfStream()) {
                outputFail(failMessage, toks)
            } else {
                val tok = toks.car()
                if (pred(tok)) {
                    outputNode(Node.makeLeaf(tok), toks.cdr())
                } else {
                    outputFail(failMessage, toks)
                }
            }
        }
    }

    protected fun pChar(c: Char, caseSensitive: Boolean = true): IParser {
        return pTokenPred("${locale.l("expect")} \"$c\"") { tok ->
            if (caseSensitive) {
                tok.value == c
            } else {
                tok.value.lowercaseChar() == c.lowercaseChar()
            }
        }
    }

    protected fun pCharArray(s: String, caseSensitive: Boolean = true): IParser {
        return cIfFail("${locale.l("expect")} \"$s\"", cSeq(*s.map { pChar(it, caseSensitive) }.toTypedArray()))
    }

    protected fun pRange(c1: Char, c2: Char): IParser {
        // TODO: locale
        return pTokenPred("expect char between '$c1' and '$c2'") { tok ->
            c1 <= tok.value && tok.value <= c2
        }
    }

    protected fun pEmpty(): IParser {
        return ParserImpl { toks, _ -> outputEmpty(toks, toks.position(), toks.position()) }
    }

    protected fun pAny(): IParser {
        return ParserImpl { toks, _ ->
            if (toks.isEndOfStream()) {
                outputFail("ANY fail: ${locale.l("reach eof")}", toks)
            } else {
                outputNode(Node.makeLeaf(toks.car()), toks.cdr())
            }
        }
    }

    protected fun pSpacing(): IParser {
        return ParserImpl { toks, stk ->
            val sp = spacingParser
            if (sp == null) {
                outputEmpty(toks, toks.position(), toks.position())
            } else {
                sp.parse(toks, stk)
            }
        }
    }

    // =====================================================================
    // token
    // =====================================================================

    protected fun pEqAndRetain(s: String, caseSensitive: Boolean = true): IParser {
        return cSeq(pCharArray(s, caseSensitive), pSpacing())
    }

    protected fun pEq(s: String, caseSensitive: Boolean = true): IParser {
        return cGlob(pEqAndRetain(s, caseSensitive))
    }

    // =====================================================================
    // combinators
    // =====================================================================

    protected fun cSeq(vararg ps: IParser): IParser {
        // TODO: 使用SeqParserImpl简化parser复杂度
        if (ps.isEmpty()) throw ParseException("empty CSeq")
        if (ps.size == 1) return ps.first()
        return ParserImpl { toks, stk ->
            var curToks = toks
            var deepest: ParseResult? = null
            val results = ArrayList<ParseResult>()
            for (p in ps) {
                val r = p.parse(curToks, stk)
                if (r.message != null) {
                    deepest = r.deeper(deepest)
                }
                if (!r.isSuccess()) {
                    val failResult = deepest!!.failResult()
                    results.add(failResult)
                    break
                }
                curToks = r.rest!!
                results.add(r)
            }
            val message = deepest?.message
            val failRest = deepest?.failRest
            mergeResults(results, curToks, message, failRest)
        }
    }

    protected fun cDebug(vararg ps: IParser): IParser {
        val parser = cSeq(*ps)
        return ParserImpl { toks, stk ->
            val r = parser.parse(toks, stk)
            println("===[Debug]===\n${r.toSExp().toPrettyString()}\n")
            r
        }
    }

    protected fun cIs(type: String, vararg ps: IParser): IParser {
        val parser = cSeq(*ps)
        return ParserImpl { toks, stk ->
            val r = parser.parse(toks, stk)
            if (!r.isSuccess()) {
                r
            } else {
                outputNode(Node.makeNode(type, r.nodes, r.start, r.end), r.rest!!, r.message, r.failRest)
            }
        }
    }

    protected fun cGlob(vararg ps: IParser): IParser {
        val parser = cSeq(*ps)
        return ParserImpl { toks, stk ->
            val r = parser.parse(toks, stk)
            if (r.isSuccess()) {
                outputEmpty(r.rest!!, r.start!!, r.end!!, r.message, r.failRest)
            } else {
                r
            }
        }
    }

    protected fun cIfFail(failMessage: String, parser: IParser): IParser {
        return ParserImpl { toks, stk ->
            val r = parser.parse(toks, stk)
            if (r.isSuccess()) {
                r
            } else {
                outputFail(failMessage, r.rest!!)
            }
        }
    }

    protected fun cOr(vararg ps: IParser): IParser {
        return ParserImpl { toks, stk ->
            var result: ParseResult? = null
            var deepest: ParseResult? = null
            for (p in ps) {
                val r = p.parse(toks, stk)
                if (r.isSuccess()) {
                    result = r
                    break
                }
                deepest = r.deeper(deepest)
            }
            result ?: deepest ?: outputFail("empty OR", toks)
        }
    }

    protected fun cNot(vararg ps: IParser): IParser {
        val parser = cSeq(*ps)
        return ParserImpl { toks, stk ->
            if (!toks.isEndOfStream() && parser.parse(toks, stk).isSuccess()) {
                outputFail("NOT fail", toks)
            } else {
                outputEmpty(toks, toks.position(), toks.position())
            }
        }
    }

    protected fun cStar(vararg ps: IParser): IParser {
        val parser = cSeq(*ps)
        return ParserImpl { toks, stk ->
            var curToks = toks
            val results = ArrayList<ParseResult>()
            var deepest: ParseResult? = null
            while (true) {
                val r = parser.parse(curToks, stk)
                deepest = r.deeper(deepest)
                if (!r.isSuccess()) {
                    break
                }
                // `toks == r.Rest` in C# is TokenStream's *custom* Equals
                // (same scanner, same string, same position object).
                if (curToks == r.rest) {
                    fatal("empty-star detected", parser, curToks, stk)
                }
                curToks = r.rest!!
                results.add(r)
            }
            mergeResults(results, curToks, deepest.message, deepest.failRest)
        }
    }

    protected fun cMaybe(vararg ps: IParser): IParser {
        return cOr(cSeq(*ps), pEmpty())
    }

    protected fun cPlus(vararg ps: IParser): IParser {
        val parser = cSeq(*ps)
        return cSeq(parser, cStar(parser))
    }

    protected fun cJoin(sepParser: IParser, vararg ps: IParser): IParser {
        val parser = cSeq(*ps)
        return cMaybe(cJoinPlus(sepParser, parser))
    }

    protected fun cJoinPlus(sepParser: IParser, vararg ps: IParser): IParser {
        val parser = cSeq(*ps)
        return cSeq(parser, cStar(sepParser, parser))
    }

    protected fun cArray(parser: IParser, n: Int): IParser {
        return cSeq(*Array(n) { parser })
    }

    private fun envGet(name: String): IParser {
        val p = env[name]
        if (p != null) {
            return p
        }
        throw ParseException("\"$name\" ${locale.l("is undefined")}")
    }

    private fun envSet(name: String, p: IParser) {
        env[name] = p
    }

    private fun outputNodes(
        success: Boolean,
        nodes: List<Node>?,
        rest: TokenStream?,
        start: TextPosition?,
        end: TextPosition?,
        message: String? = null,
        failRest: TokenStream? = null
    ): ParseResult {
        val r = ParseResult()
        r.success = success
        r.nodes = nodes?.toMutableList()
        r.rest = rest
        r.message = message
        r.failRest = failRest ?: rest
        r.start = start
        r.end = end
        return r
    }

    private fun outputNode(node: Node, rest: TokenStream, message: String? = null, failRest: TokenStream? = null): ParseResult {
        return outputNodes(true, listOf(node), rest, node.start, node.end, message, failRest)
    }

    private fun outputEmpty(rest: TokenStream, start: TextPosition, end: TextPosition, message: String? = null, failRest: TokenStream? = null): ParseResult {
        return outputNodes(true, emptyList(), rest, start, end, message, failRest)
    }

    private fun outputFail(message: String, rest: TokenStream): ParseResult {
        val r = ParseResult()
        r.success = false
        r.message = messageLocaleString(message)
        r.rest = rest
        r.failRest = rest
        return r
    }

    // Assert results[0...-1] succeed
    private fun mergeResults(results: List<ParseResult>, rest: TokenStream, messageIn: String?, failRestIn: TokenStream?): ParseResult {
        var message = messageIn
        var failRest = failRestIn
        val nodes = results.filter { it.isSuccess() }.flatMap { it.nodes!! }.toMutableList()
        var success = true
        if (results.isNotEmpty()) {
            val lastR = results.last()
            if (message == null) {
                message = lastR.message
                failRest = lastR.failRest
            }
            success = lastR.isSuccess()
        }
        val startIdx = if (results.isEmpty()) rest.position() else results.first().start
        val endIdx = if (results.isEmpty()) rest.position() else results.last().end
        return outputNodes(success, nodes, rest, startIdx, endIdx, message, failRest)
    }

    private fun messageLocaleString(s: String): String {
        val start = messageLocaleStart
        val end = messageLocaleEnd
        if (start.isNullOrEmpty() || end.isNullOrEmpty()) {
            return s
        }
        // C# builds `${Start}([^${End}])*${End}`; java.util.regex rejects a
        // bare `{` that does not form a valid quantifier, so the delimiters
        // are Pattern.quote-d (semantically identical for the char class).
        val pattern = Pattern.quote(start) + "([^" + Pattern.quote(end) + "])*" + Pattern.quote(end)
        val mc = Pattern.compile(pattern).matcher(s)
        val items = LinkedHashSet<String>()
        while (mc.find()) {
            items.add(mc.group())
        }
        var result = s
        for (item in items) {
            // C# `Substring(start.Length, item.Length - start.Length - end.Length)`
            // takes a *length* as second argument; Kotlin's substring takes an
            // end index, hence `item.length - end.length`.
            val key = item.substring(start.length, item.length - end.length)
            result = result.replace(item, locale.l(key))
        }
        return result
    }
}
