package io.github.skabyy.kccc.parser

import io.github.skabyy.kccc.common.Locale
import io.github.skabyy.kccc.common.ResourceUtils
import io.github.skabyy.kccc.exceptions.ParseException

class YcParser(grammarAst: Node, init: ((Settings) -> Unit)?) : CombinedParser() {

    private var caseSensitive: Boolean
    private var splitWord: Boolean

    /**
     * NOTE: the C# Settings properties are write-only; Kotlin cannot express
     * a private getter on a public var, so these are read/write instead.
     */
    class Settings internal constructor(private val parser: YcParser) {

        val locale: Locale
            get() = parser.locale

        var caseSensitive: Boolean
            get() = parser.caseSensitive
            set(value) { parser.caseSensitive = value }

        var splitWord: Boolean
            get() = parser.splitWord
            set(value) { parser.splitWord = value }

        var leftRecurDetection: Boolean
            get() = parser.leftRecurDetection
            set(value) { parser.leftRecurDetection = value }

        var useMemorizedParser: Boolean
            get() = parser.useMemorizedParser
            set(value) { parser.useMemorizedParser = value }
    }

    private fun internalChildren(node: Node, what: String): MutableList<Node> {
        return when (node) {
            is Node.Internal -> node.children
            is Node.Leaf -> throw ParseException("$what must be a node")
        }
    }

    init {
        // default options
        caseSensitive = true
        splitWord = true
        leftRecurDetection = true
        useMemorizedParser = true
        spacingParser = cGlob(cStar(pSpace()))
        // ===============

        val nodes = internalChildren(grammarAst, "grammar AST root")
        val rootStm = nodes.first()
        val optionStms = internalChildren(nodes[1], "grammar AST option-section")
        val defStms = internalChildren(nodes[2], "grammar AST def-section")
        val rootName = rootStm.stringValue()

        Node.match(optionStms) { type ->
            type(YcGrammarParser.OPTION_STM) { es ->
                setOption(es.first(), es.drop(1))
            }
        }

        init?.invoke(Settings(this))

        Node.match(defStms) { type ->
            type(YcGrammarParser.DEF_STM) { es ->
                val name = es.first().stringValue()
                val ps = valueOfList(es.drop(1))
                defParser(name, cSeq(*ps))
            }
        }

        setRootParser(get(rootName))
    }

    private fun setOption(cmd: Node, args: List<Node>) {
        cmd.match<Unit> { type ->
            type(YcGrammarParser.CASE_SENSITIVE) { _ ->
                caseSensitive = valueOfOnOrOff(args.first())
            }
            type(YcGrammarParser.LEX_IGNORE) { _ ->
                spacingParser = get(args.first().stringValue())
            }
            type(YcGrammarParser.LEX_MODE) { _ ->
                spacingParser = null
            }
            type(YcGrammarParser.SPLIT_WORD) { _ ->
                splitWord = valueOfOnOrOff(args.first())
            }
            type(YcGrammarParser.INCLUDE_BUILTIN) { _ ->
                loadBuildinParsers()
            }
            type(YcGrammarParser.SET_MESSAGE_LOCALE_START) { _ ->
                messageLocaleStart = args.first().stringValue()
            }
            type(YcGrammarParser.SET_MESSAGE_LOCALE_END) { _ ->
                messageLocaleEnd = args.first().stringValue()
            }
        }
    }

    private fun valueOfOnOrOff(node: Node): Boolean {
        return node.match { type ->
            type(YcGrammarParser.OPTION_ON) { _ -> true }
            type(YcGrammarParser.OPTION_OFF) { _ -> false }
        }
    }

    private fun valueOf(exp: Node): IParser {
        return exp.match { type ->
            type(YcGrammarParser.NAMED_EXP) { es -> cIs(es.first().stringValue(), valueOf(es[1])) }
            type(YcGrammarParser.GLOB_EXP) { es -> cGlob(valueOf(es.first())) }
            type(YcGrammarParser.OP_EXP) { es -> applyOp(es.first(), *valueOfList(es.drop(1))) }
            type(YcGrammarParser.ERR_EXP) { es -> cIfFail(es.first().stringValue(), cSeq(*valueOfList(es.drop(1)))) }
            type(YcGrammarParser.SEQ_EXP) { es -> cSeq(*valueOfList(es)) }
            type(YcGrammarParser.ANY_EXP) { _ -> pAny() }
            type(YcGrammarParser.DBG_EXP) { es -> cDebug(cSeq(*valueOfList(es))) }
            type(YcGrammarParser.DBG_1EXP) { es -> cDebug(valueOf(es.first())) }
            type(YcGrammarParser.CHAR_EXP) { es -> valueOfCharExp(es.first()) }
            type(YcGrammarParser.GLOB_CHAR_EXP) { es -> cGlob(valueOfCharExp(es.first())) }
            type(YcGrammarParser.WORD_EXP) { es -> valueOfWordExp(es) }
            type(YcGrammarParser.GLOB_WORD_EXP) { es -> cGlob(valueOfWordExp(es)) }
            type(YcGrammarParser.VAR_EXP) { es -> get(Node.concatValue(es)) }
            type(YcGrammarParser.ARRAY_EXP) { es ->
                val parser = valueOf(es[1])
                val size = es[0].stringValue().toIntOrNull()
                if (size != null) {
                    cArray(parser, size)
                } else {
                    throw ParseException("expect an integer at ${nodePosition(es[0])}")
                }
            }
        }
    }

    private fun valueOfList(es: List<Node>): Array<IParser> {
        return es.map { valueOf(it) }.toTypedArray()
    }

    private fun valueOfCharExp(node: Node): IParser {
        return node.match { type ->
            type(YcGrammarParser.NORMAL_CHAR) { es ->
                if (es.size == 1) {
                    pChar((es.first() as Node.Leaf).value)
                } else {
                    throw ParseException("not a char (${nodePosition(es.first())})")
                }
            }
            type(YcGrammarParser.SPECIAL_CHAR) { es ->
                val charName = Node.concatValue(es)
                when (charName) {
                    YcGrammarParser.SPECIAL_CHAR_EOF -> pEof()
                    YcGrammarParser.SPECIAL_CHAR_NEWLINE -> pNewline()
                    YcGrammarParser.SPECIAL_CHAR_SPACE -> pSpace()
                    else -> {
                        throw ParseException("unknown char '$charName' at ${nodePosition(es.first())}")
                    }
                }
            }
        }
    }

    private fun valueOfWordExp(es: List<Node>): IParser {
        val word = Node.concatValue(es.toMutableList())
        if (!word.isBlank() && splitWord) {
            // C# `word.Split()` splits on runs of whitespace and drops empty
            // entries; the filter reproduces that after a regex split.
            return cSeq(*word.split(Regex("\\s+"))
                .filter { it.isNotEmpty() }
                .map { pEqAndRetain(it, caseSensitive) }
                .toTypedArray())
        } else {
            return pEqAndRetain(word, caseSensitive)
        }
    }

    private fun applyOp(op: Node, vararg ps: IParser): IParser {
        val opName = op.stringValue()
        return when (opName) {
            YcGrammarParser.PLUS_CMB -> cPlus(*ps)
            YcGrammarParser.STAR_CMB -> cStar(*ps)
            YcGrammarParser.OR_CMB -> cOr(*ps)
            YcGrammarParser.SEQ_CMB -> cSeq(*ps)
            YcGrammarParser.JOIN_CMB -> cJoin(ps.first(), *ps.drop(1).toTypedArray())
            YcGrammarParser.JOIN_PLUS_CMB -> cJoinPlus(ps.first(), *ps.drop(1).toTypedArray())
            YcGrammarParser.NOT_CMB -> cNot(*ps)
            YcGrammarParser.MAYBE_CMB -> cMaybe(*ps)
            else -> throw ParseException("unknown op '$opName' at ${nodePosition(op)}")
        }
    }

    companion object {

        @JvmStatic
        @JvmOverloads
        fun load(src: String, init: ((Settings) -> Unit)? = null): YcParser {
            val ycgp = YcGrammarParser()
            val parseResult = ycgp.parse(src)
            return when (parseResult) {
                is ParseResult.Failure -> throw ParseException(
                    "parsing grammar failed: (message: ${parseResult.message}, rest: ${parseResult.failRest})")
                is ParseResult.Success -> load(parseResult.nodes.first(), init)
            }
        }

        @JvmStatic
        @JvmOverloads
        fun load(grammarAst: Node, init: ((Settings) -> Unit)? = null): YcParser {
            return YcParser(grammarAst, init)
        }

        /**
         * JVM counterpart of `LoadFromAssembly`: reads a grammar resource
         * from the classpath.
         */
        @JvmStatic
        @JvmOverloads
        fun loadFromResource(path: String, init: ((Settings) -> Unit)? = null): YcParser {
            val src = ResourceUtils.readString(path)
            return load(src, init)
        }
    }
}
