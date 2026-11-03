/**
 * Hand-written parser for the yccc grammar language. Mirrors
 * `parser/YcGrammarParser.kt` statement by statement; this is the parser that
 * has to accept `yccc.grammar` itself.
 */

import { CombinedParser } from "./combined-parser.js"
import type { Parser } from "./parser.js"

export const CASE_SENSITIVE = "case-sensitive"
export const SPLIT_WORD = "split-word"
export const LEX_IGNORE = "lex-ignore"
export const LEX_MODE = "lex-mode"
export const INCLUDE_BUILTIN = "include-builtin"

export const OPTION_ON = "on"
export const OPTION_OFF = "off"

export const SET_MESSAGE_LOCALE_START = "set-message-locale-start"
export const SET_MESSAGE_LOCALE_END = "set-message-locale-end"

export const NORMAL_CHAR = "normal-char"
export const SPECIAL_CHAR = "special-char"
export const SPECIAL_CHAR_EOF = "eof"
export const SPECIAL_CHAR_NEWLINE = "newline"
export const SPECIAL_CHAR_SPACE = "space"

export const PLUS_CMB = "@+"
export const STAR_CMB = "@*"
export const JOIN_CMB = "@,*"
export const JOIN_PLUS_CMB = "@,+"
export const OR_CMB = "@or"
export const SEQ_CMB = "@.."
export const NOT_CMB = "@!"
export const MAYBE_CMB = "@?"

export const EXP = "exp"
export const NAMED_EXP = "named-exp"
export const GLOB_EXP = "glob-exp"
export const OP_EXP = "op-exp"
export const SEQ_EXP = "seq-exp"
export const ERR_EXP = "err-exp"
export const ANY_EXP = "any-exp"
export const DBG_EXP = "dbg-exp"
export const DBG_1EXP = "dbg-1exp"
export const CHAR_EXP = "char-exp"
export const GLOB_CHAR_EXP = "glob-char-exp"
export const WORD_EXP = "word-exp"
export const GLOB_WORD_EXP = "glob-word-exp"
export const VAR_EXP = "var-exp"
export const PRIMARY_EXP = "primary-exp"
export const ARRAY_EXP = "array-exp"

export const OPTION_STM = "option-stm"
export const DEF_STM = "def-stm"

export const OPTION_SECTION = "option-section"
export const DEF_SECTION = "def-section"

export const DEF_ROOT = "def-root"
export const PROGRAM = "program"

export class GrammarParser extends CombinedParser {
  constructor() {
    super()

    const space = this.pSpace()
    const comment = this.cOr(
      this.cSeq(this.pChar(";"), this.cStar(this.cNot(this.pNewline()), this.pAny())),
      this.cWrapperAnyBy(this.pCharArray("#|"), this.pCharArray("|#")),
    )
    const spacing = this.cGlob(this.cStar(this.cOr(space, comment)))
    this.spacingParser = spacing

    const idenAlpha = this.cOr(this.pChar("_"), this.pChar("-"), this.pAlpha())
    const variable = this.cIfFail(
      "invalid var",
      this.cSeq(idenAlpha, this.cStar(this.cOr(idenAlpha, this.pDigit())), spacing),
    )
    const charIdentifier = this.cOr(
      this.cIs(
        SPECIAL_CHAR,
        this.cOr(
          this.pEqAndRetain(SPECIAL_CHAR_EOF),
          this.pEqAndRetain(SPECIAL_CHAR_NEWLINE),
          this.pEqAndRetain(SPECIAL_CHAR_SPACE),
        ),
      ),
      this.cIs(NORMAL_CHAR, this.cIfFail("expect a char", this.cNot(space)), this.pAny(), spacing),
    )
    const hchr = this.cSeq(this.cGlob(this.pCharArray("#\\")), charIdentifier)
    const chr = this.cSeq(this.cGlob(this.pChar("\\")), charIdentifier)
    const str = this.cSeq(this.cWrapperAnyBy(this.pChar("'"), this.pChar("'")), spacing)
    const hstr = this.cSeq(this.cWrapperAnyBy(this.pCharArray("#'"), this.pChar("'")), spacing)
    const integer = this.cSeq(this.cPlus(this.pDigit()), spacing)

    const onOrOff = this.cOr(
      this.cIs(OPTION_ON, this.pEq(OPTION_ON)),
      this.cIs(OPTION_OFF, this.pEq(OPTION_OFF)),
    )

    const caseSensitiveStm = this.cSeq(this.cIs(CASE_SENSITIVE, this.pEq("@case-sensitive")), onOrOff)
    const splitWordStm = this.cSeq(this.cIs(SPLIT_WORD, this.pEq("@split-word")), onOrOff)
    const lexIgnoreStm = this.cSeq(
      this.cIs(LEX_IGNORE, this.pEq("@lex-ignore")),
      this.cIs("spacing-parser", variable),
    )
    const lexModeStm = this.cSeq(this.cIs(LEX_MODE, this.pEq("@lex-mode")))
    const includeBuiltinStm = this.cSeq(this.cIs(INCLUDE_BUILTIN, this.pEq("@include-builtin")))
    const setMessageLocaleStart = this.cSeq(
      this.cIs(SET_MESSAGE_LOCALE_START, this.pEq("@set-message-locale-start")),
      this.cIs("locale-start", str),
    )
    const setMessageLocaleEnd = this.cSeq(
      this.cIs(SET_MESSAGE_LOCALE_END, this.pEq("@set-message-locale-end")),
      this.cIs("locale-end", str),
    )
    const optionStm = this.cIs(
      OPTION_STM,
      this.cOr(
        caseSensitiveStm,
        splitWordStm,
        lexIgnoreStm,
        lexModeStm,
        includeBuiltinStm,
        setMessageLocaleStart,
        setMessageLocaleEnd,
      ),
    )

    const lparen = this.pEq("(")
    const rparen = this.pEq(")")
    const eq = this.pEq("=")
    const cmbOp = this.cIfFail(
      "unknown op",
      this.cIs(
        "op",
        this.cOr(
          this.pEqAndRetain(PLUS_CMB),
          this.pEqAndRetain(STAR_CMB),
          this.pEqAndRetain(JOIN_CMB),
          this.pEqAndRetain(JOIN_PLUS_CMB),
          this.pEqAndRetain(OR_CMB),
          this.pEqAndRetain(SEQ_CMB),
          this.pEqAndRetain(NOT_CMB),
          this.pEqAndRetain(MAYBE_CMB),
        ),
      ),
    )

    const namedExp = this.cSeq(this.cIs("name", variable), this.pEq(":"), this.get(EXP))
    const globExp = this.cSeq(this.pEq("~"), this.get(EXP))
    const opExp = this.cSeq(lparen, cmbOp, this.cStar(this.get(EXP)), rparen)
    const errExp = this.cSeq(lparen, this.pEq("@err"), this.cIs("message", str), this.cPlus(this.get(EXP)), rparen)
    const seqExp = this.cSeq(lparen, this.cPlus(this.get(EXP)), rparen)
    const anyExp = this.pEq("<*>")
    const arrayExp = this.cSeq(this.pEq("["), this.cIs("size", integer), this.pEq("]"), this.get(EXP))
    const dbgExp = this.cSeq(this.pEq("?["), this.cPlus(this.get(EXP)), this.pEq("]"))
    const dbg1Exp = this.cSeq(this.pEq("??"), this.get(EXP))
    const exp = this.defParser(
      EXP,
      this.cOr(
        this.cIs(NAMED_EXP, namedExp),
        this.cIs(GLOB_EXP, globExp),
        this.cIs(OP_EXP, opExp),
        this.cIs(ERR_EXP, errExp),
        this.cIs(SEQ_EXP, seqExp),
        this.cIs(ANY_EXP, anyExp),
        this.cIs(ARRAY_EXP, arrayExp),
        this.cIs(DBG_EXP, dbgExp),
        this.cIs(DBG_1EXP, dbg1Exp),
        this.cIs(CHAR_EXP, hchr),
        this.cIs(GLOB_CHAR_EXP, chr),
        this.cIs(WORD_EXP, hstr),
        this.cIs(GLOB_WORD_EXP, str),
        this.cIs(VAR_EXP, this.cSeq(variable, this.cNot(this.pEq(":")))),
      ),
    )

    const defStm = this.cIs(
      DEF_STM,
      this.cSeq(this.cIs("lhs", variable), eq, this.cPlus(exp, this.cNot(eq))),
    )
    const rootStm = this.cIs(DEF_ROOT, this.cSeq(this.pEq("::"), variable))

    this.setRootParser(
      this.defParser(
        PROGRAM,
        this.cIs(
          PROGRAM,
          this.cSeq(
            rootStm,
            this.cIs(OPTION_SECTION, this.cStar(optionStm)),
            this.cIs(DEF_SECTION, this.cStar(defStm)),
          ),
        ),
      ),
    )
  }

  private cWrapperAnyBy(start: Parser, end: Parser): Parser {
    return this.cSeq(this.cGlob(start), this.cStar(this.cNot(end), this.pAny()), this.cGlob(end))
  }
}
