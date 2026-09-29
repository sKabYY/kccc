package io.github.skabyy.nccc.parser

import io.github.skabyy.nccc.common.Locale
import io.github.skabyy.nccc.common.ResourceUtils
import io.github.skabyy.nccc.exceptions.ParseException

class NcParser(grammerAst: Node, init: ((Settings) -> Unit)?) : CombinedParser() {

    private var caseSensitive: Boolean
    private var splitWord: Boolean

    /**
     * NOTE: the C# Settings properties are write-only; Kotlin cannot express
     * a private getter on a public var, so these are read/write instead.
     */
    class Settings internal constructor(private val _parser: NcParser) {

        val locale: Locale
            get() = _parser.`_`

        var caseSensitive: Boolean
            get() = _parser.caseSensitive
            set(value) { _parser.caseSensitive = value }

        var splitWord: Boolean
            get() = _parser.splitWord
            set(value) { _parser.splitWord = value }

        var leftRecurDetection: Boolean
            get() = _parser.leftRecurDetection
            set(value) { _parser.leftRecurDetection = value }

        var useMemorizedParser: Boolean
            get() = _parser.useMemorizedParser
            set(value) { _parser.useMemorizedParser = value }
    }

    init {
        // default options
        caseSensitive = true
        splitWord = true
        leftRecurDetection = true
        useMemorizedParser = true
        spacingParser = cGlob(cStar(pSpace()))
        // ===============

        val nodes = grammerAst.children!!
        val rootStm = nodes.first()
        val optionStms = nodes[1].children!!
        val defStms = nodes[2].children!!
        val rootName = rootStm.stringValue()

        Node.match(optionStms) { type ->
            type(NcGrammerParser.OPTION_STM) { es ->
                setOption(es.first(), es.drop(1))
            }
        }

        init?.invoke(Settings(this))

        Node.match(defStms) { type ->
            type(NcGrammerParser.DEF_STM) { es ->
                val name = es.first().stringValue()
                val ps = valueOfList(es.drop(1))
                defParser(name, cSeq(*ps))
            }
        }

        setRootParser(get(rootName))
    }

    private fun setOption(cmd: Node, args: List<Node>) {
        cmd.match<Unit> { type ->
            type(NcGrammerParser.CASE_SENSITIVE) { _ ->
                caseSensitive = valueOfOnOrOff(args.first())
            }
            type(NcGrammerParser.LEX_IGNORE) { _ ->
                spacingParser = get(args.first().stringValue())
            }
            type(NcGrammerParser.LEX_MODE) { _ ->
                spacingParser = null
            }
            type(NcGrammerParser.SPLIT_WORD) { _ ->
                splitWord = valueOfOnOrOff(args.first())
            }
            type(NcGrammerParser.INCLUDE_BUILTIN) { _ ->
                loadBuildinParsers()
            }
            type(NcGrammerParser.SET_MESSAGE_LOCALE_START) { _ ->
                messageLocaleStart = args.first().stringValue()
            }
            type(NcGrammerParser.SET_MESSAGE_LOCALE_END) { _ ->
                messageLocaleEnd = args.first().stringValue()
            }
        }
    }

    private fun valueOfOnOrOff(node: Node): Boolean {
        return node.match { type ->
            type(NcGrammerParser.OPTION_ON) { _ -> true }
            type(NcGrammerParser.OPTION_OFF) { _ -> false }
        }
    }

    private fun valueOf(exp: Node): IParser {
        return exp.match { type ->
            type(NcGrammerParser.NAMED_EXP) { es -> cIs(es.first().stringValue(), valueOf(es[1])) }
            type(NcGrammerParser.GLOB_EXP) { es -> cGlob(valueOf(es.first())) }
            type(NcGrammerParser.OP_EXP) { es -> applyOp(es.first(), *valueOfList(es.drop(1))) }
            type(NcGrammerParser.ERR_EXP) { es -> cIfFail(es.first().stringValue(), cSeq(*valueOfList(es.drop(1)))) }
            type(NcGrammerParser.SEQ_EXP) { es -> cSeq(*valueOfList(es)) }
            type(NcGrammerParser.ANY_EXP) { _ -> pAny() }
            type(NcGrammerParser.DBG_EXP) { es -> cDebug(cSeq(*valueOfList(es))) }
            type(NcGrammerParser.DBG_1EXP) { es -> cDebug(valueOf(es.first())) }
            type(NcGrammerParser.CHAR_EXP) { es -> valueOfCharExp(es.first()) }
            type(NcGrammerParser.GLOB_CHAR_EXP) { es -> cGlob(valueOfCharExp(es.first())) }
            type(NcGrammerParser.WORD_EXP) { es -> valueOfWordExp(es) }
            type(NcGrammerParser.GLOB_WORD_EXP) { es -> cGlob(valueOfWordExp(es)) }
            type(NcGrammerParser.VAR_EXP) { es -> get(Node.concatValue(es)) }
            type(NcGrammerParser.ARRAY_EXP) { es ->
                val parser = valueOf(es[1])
                val size = es[0].stringValue().toIntOrNull()
                if (size != null) {
                    cArray(parser, size)
                } else {
                    val pos = es[0].start!!
                    throw ParseException("expect an integer at row ${pos.linenum} column ${pos.colnum}")
                }
            }
        }
    }

    private fun valueOfList(es: List<Node>): Array<IParser> {
        return es.map { valueOf(it) }.toTypedArray()
    }

    private fun valueOfCharExp(node: Node): IParser {
        return node.match { type ->
            type(NcGrammerParser.NORMAL_CHAR) { es ->
                if (es.size == 1) {
                    pChar(es.first().value)
                } else {
                    val pos = es.first().start!!
                    throw ParseException("not a char (row ${pos.linenum}, column ${pos.colnum})")
                }
            }
            type(NcGrammerParser.SPECIAL_CHAR) { es ->
                val charName = Node.concatValue(es)
                when (charName) {
                    NcGrammerParser.SPECIAL_CHAR_EOF -> pEof()
                    NcGrammerParser.SPECIAL_CHAR_NEWLINE -> pNewline()
                    NcGrammerParser.SPECIAL_CHAR_SPACE -> pSpace()
                    else -> {
                        val pos = es.first().start!!
                        throw ParseException("unknown char '$charName' at row ${pos.linenum} column ${pos.colnum}")
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
        val pos = op.start!!
        return when (opName) {
            NcGrammerParser.PLUS_CMB -> cPlus(*ps)
            NcGrammerParser.STAR_CMB -> cStar(*ps)
            NcGrammerParser.OR_CMB -> cOr(*ps)
            NcGrammerParser.SEQ_CMB -> cSeq(*ps)
            NcGrammerParser.JOIN_CMB -> cJoin(ps.first(), *ps.drop(1).toTypedArray())
            NcGrammerParser.JOIN_PLUS_CMB -> cJoinPlus(ps.first(), *ps.drop(1).toTypedArray())
            NcGrammerParser.NOT_CMB -> cNot(*ps)
            NcGrammerParser.MAYBE_CMB -> cMaybe(*ps)
            else -> throw ParseException("unkown op '$opName' at row ${pos.linenum} column ${pos.colnum}")
        }
    }

    companion object {

        @JvmStatic
        @JvmOverloads
        fun load(src: String, init: ((Settings) -> Unit)? = null): NcParser {
            val ncgp = NcGrammerParser()
            val parseResult = ncgp.parse(src)
            if (!parseResult.isSuccess()) {
                throw ParseException("parsing grammar failed: (message: ${parseResult.message}, rest: ${parseResult.failRest})")
            }
            return load(parseResult.nodes!!.first(), init)
        }

        @JvmStatic
        @JvmOverloads
        fun load(grammerAst: Node, init: ((Settings) -> Unit)? = null): NcParser {
            return NcParser(grammerAst, init)
        }

        /**
         * JVM counterpart of `LoadFromAssembly`: reads a grammar resource
         * from the classpath.
         */
        @JvmStatic
        @JvmOverloads
        fun loadFromResource(path: String, init: ((Settings) -> Unit)? = null): NcParser {
            val src = ResourceUtils.readString(path)
            return load(src, init)
        }
    }
}
