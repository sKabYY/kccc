package io.github.skabyy.kccc.parser

import io.github.skabyy.kccc.common.ResourceUtils

class YcGrammarParser : CombinedParser() {

    init {
        val space = pSpace()
        val comment = cOr(
            cSeq(pChar(';'), cStar(cNot(pNewline()), pAny())),
            cWrapperAnyBy(pCharArray("#|"), pCharArray("|#")))
        val spacing = cGlob(cStar(cOr(space, comment)))
        spacingParser = spacing

        val idenAlpha = cOr(pChar('_'), pChar('-'), pAlpha())
        val variable = cIfFail("invalid var", cSeq(idenAlpha, cStar(cOr(idenAlpha, pDigit())), spacing))
        val charIdentifier = cOr(
            cIs(SPECIAL_CHAR, cOr(
                pEqAndRetain(SPECIAL_CHAR_EOF),
                pEqAndRetain(SPECIAL_CHAR_NEWLINE),
                pEqAndRetain(SPECIAL_CHAR_SPACE))),
            cIs(NORMAL_CHAR, cIfFail("expect a char", cNot(space)), pAny(), spacing))
        val hchr = cSeq(cGlob(pCharArray("#\\")), charIdentifier)
        val chr = cSeq(cGlob(pChar('\\')), charIdentifier)
        val str = cSeq(cWrapperAnyBy(pChar('\''), pChar('\'')), spacing)
        val hstr = cSeq(cWrapperAnyBy(pCharArray("#'"), pChar('\'')), spacing)
        val integer = cSeq(cPlus(pDigit()), spacing)

        val onOrOff = cOr(cIs(OPTION_ON, pEq(OPTION_ON)), cIs(OPTION_OFF, pEq(OPTION_OFF)))

        val caseSensitiveStm = cSeq(cIs(CASE_SENSITIVE, pEq("@case-sensitive")), onOrOff)
        val splitWordStm = cSeq(cIs(SPLIT_WORD, pEq("@split-word")), onOrOff)
        val lexIgnoreStm = cSeq(cIs(LEX_IGNORE, pEq("@lex-ignore")), cIs("spacing-parser", variable))
        val lexModeStm = cSeq(cIs(LEX_MODE, pEq("@lex-mode")))
        val includeBuiltinStm = cSeq(cIs(INCLUDE_BUILTIN, pEq("@include-builtin")))
        val setMessageLocaleStart = cSeq(cIs(SET_MESSAGE_LOCALE_START, pEq("@set-message-locale-start")), cIs("locale-start", str))
        val setMessageLocaleEnd = cSeq(cIs(SET_MESSAGE_LOCALE_END, pEq("@set-message-locale-end")), cIs("locale-end", str))
        val optionStm = cIs(OPTION_STM, cOr(
            caseSensitiveStm,
            splitWordStm,
            lexIgnoreStm,
            lexModeStm,
            includeBuiltinStm,
            setMessageLocaleStart,
            setMessageLocaleEnd))

        val lparen = pEq("(")
        val rparen = pEq(")")
        val eq = pEq("=")
        val cmbOp = cIfFail("unknown op", cIs("op", cOr(
            pEqAndRetain(PLUS_CMB),
            pEqAndRetain(STAR_CMB),
            pEqAndRetain(JOIN_CMB),
            pEqAndRetain(JOIN_PLUS_CMB),
            pEqAndRetain(OR_CMB),
            pEqAndRetain(SEQ_CMB),
            pEqAndRetain(NOT_CMB),
            pEqAndRetain(MAYBE_CMB))))

        val namedExp = cSeq(cIs("name", variable), pEq(":"), get(EXP))
        val globExp = cSeq(pEq("~"), get(EXP))
        val opExp = cSeq(lparen, cmbOp, cStar(get(EXP)), rparen)
        val errExp = cSeq(lparen, pEq("@err"), cIs("message", str), cPlus(get(EXP)), rparen)
        val seqExp = cSeq(lparen, cPlus(get(EXP)), rparen)
        val anyExp = pEq("<*>")
        val arrayExp = cSeq(pEq("["), cIs("size", integer), pEq("]"), get(EXP))
        val dbgExp = cSeq(pEq("?["), cPlus(get(EXP)), pEq("]"))
        val dbg1Exp = cSeq(pEq("??"), get(EXP))
        val exp = defParser(EXP, cOr(
            cIs(NAMED_EXP, namedExp),
            cIs(GLOB_EXP, globExp),
            cIs(OP_EXP, opExp),
            cIs(ERR_EXP, errExp),
            cIs(SEQ_EXP, seqExp),
            cIs(ANY_EXP, anyExp),
            cIs(ARRAY_EXP, arrayExp),
            cIs(DBG_EXP, dbgExp),
            cIs(DBG_1EXP, dbg1Exp),
            cIs(CHAR_EXP, hchr),
            cIs(GLOB_CHAR_EXP, chr),
            cIs(WORD_EXP, hstr),
            cIs(GLOB_WORD_EXP, str),
            cIs(VAR_EXP, cSeq(variable, cNot(pEq(":"))))))

        val defStm = cIs(DEF_STM, cSeq(cIs("lhs", variable), eq, cPlus(exp, cNot(eq))))
        val rootStm = cIs(DEF_ROOT, cSeq(pEq("::"), variable))

        setRootParser(defParser(PROGRAM, cIs(PROGRAM, cSeq(
            rootStm,
            cIs(OPTION_SECTION, cStar(optionStm)),
            cIs(DEF_SECTION, cStar(defStm))))))
    }

    private fun cWrapperAnyBy(start: IParser, end: IParser): IParser {
        return cSeq(cGlob(start), cStar(cNot(end), pAny()), cGlob(end))
    }

    companion object {
        const val CASE_SENSITIVE = "case-sensitive"
        const val SPLIT_WORD = "split-word"
        const val LEX_IGNORE = "lex-ignore"
        const val LEX_MODE = "lex-mode"
        const val INCLUDE_BUILTIN = "include-builtin"

        const val OPTION_ON = "on"
        const val OPTION_OFF = "off"

        const val SET_MESSAGE_LOCALE_START = "set-message-locale-start"
        const val SET_MESSAGE_LOCALE_END = "set-message-locale-end"

        const val NORMAL_CHAR = "normal-char"
        const val SPECIAL_CHAR = "special-char"
        const val SPECIAL_CHAR_EOF = "eof"
        const val SPECIAL_CHAR_NEWLINE = "newline"
        const val SPECIAL_CHAR_SPACE = "space"

        const val PLUS_CMB = "@+"
        const val STAR_CMB = "@*"
        const val JOIN_CMB = "@,*"
        const val JOIN_PLUS_CMB = "@,+"
        const val OR_CMB = "@or"
        const val SEQ_CMB = "@.."
        const val NOT_CMB = "@!"
        const val MAYBE_CMB = "@?"

        const val EXP = "exp"
        const val NAMED_EXP = "named-exp"
        const val GLOB_EXP = "glob-exp"
        const val OP_EXP = "op-exp"
        const val SEQ_EXP = "seq-exp"
        const val ERR_EXP = "err-exp"
        const val ANY_EXP = "any-exp"
        const val DBG_EXP = "dbg-exp"
        const val DBG_1EXP = "dbg-1exp"
        const val CHAR_EXP = "char-exp"
        const val GLOB_CHAR_EXP = "glob-char-exp"
        const val WORD_EXP = "word-exp"
        const val GLOB_WORD_EXP = "glob-word-exp"
        const val VAR_EXP = "var-exp"
        const val PRIMARY_EXP = "primary-exp"
        const val ARRAY_EXP = "array-exp"

        const val OPTION_STM = "option-stm"
        const val DEF_STM = "def-stm"

        const val OPTION_SECTION = "option-section"
        const val DEF_SECTION = "def-section"

        const val DEF_ROOT = "def-root"
        const val PROGRAM = "program"

        /**
         * Reads the bootstrapping grammar resource.
         */
        @JvmStatic
        fun getYcGrammarSource(): String {
            return ResourceUtils.readString("kccc/yccc.grammar")
        }
    }
}
