/**
 * Grammar interpreter and loading entry points. Mirrors `parser/YcParser.kt`.
 *
 * Loading order matches the Kotlin original: defaults -> option statements
 * from the grammar -> user options -> definitions -> root parser. User options
 * therefore win over `@...` statements, which is what the Kotlin `Settings`
 * callback does.
 */

import { isJavaWhitespace } from "./data.js"
import { GrammarError, NodeError } from "./errors.js"
import { concatValue, Internal, Leaf, nodePosition, type Node } from "./tree.js"
import type { Parser } from "./parser.js"
import { CombinedParser } from "./combined-parser.js"
import * as Yc from "./grammar-parser.js"

export interface LocaleInit {
  language?: string
  entries?: Record<string, string>
}

export interface GrammarOptions {
  caseSensitive?: boolean
  splitWord?: boolean
  leftRecurDetection?: boolean
  useMemorizedParser?: boolean
  locale?: LocaleInit
  /** Sink for the `?[...]` / `??[...]` debug constructs; defaults to console.log. */
  debug?: (s: string) => void
}

function matchError(node: Node): NodeError {
  return new NodeError(
    `match error: unknown type "${node.type}" for node ${node.toSExp().toPrettyString()}`,
    node,
  )
}

function childrenOf(node: Node): readonly Node[] {
  if (node instanceof Internal) return node.children
  throw matchError(node)
}

function matchChildren(node: Node, expected: string): readonly Node[] {
  if (node.type === expected && node instanceof Internal) return node.children
  throw matchError(node)
}

/** Kotlin `internalChildren`: the grammar AST sections must be internal nodes. */
function internalChildren(node: Node, what: string): readonly Node[] {
  if (node instanceof Internal) return node.children
  throw new GrammarError(`${what} must be a node`)
}

function isBlank(s: string): boolean {
  for (let i = 0; i < s.length; i++) {
    if (!isJavaWhitespace(s.charAt(i))) return false
  }
  return true
}

/**
 * Kotlin splits words with `Regex("\\s+")`, which on the JVM matches ASCII
 * whitespace only (unlike the Unicode-aware `isBlank` above); keep that
 * asymmetry so `@split-word` behaves the same.
 */
function splitAsciiWhitespace(s: string): string[] {
  return s.split(/[ \t\n\u000B\f\r]+/)
}

function toIntOrNull(s: string): number | null {
  if (!/^[+-]?\d+$/.test(s)) return null
  const n = Number(s)
  if (!Number.isInteger(n) || n < -2147483648 || n > 2147483647) return null
  return n
}

export class Grammar extends CombinedParser {
  private caseSensitive = true
  private splitWord = true

  constructor(grammarAst: Node, options?: GrammarOptions) {
    super()

    // defaults
    this.caseSensitive = true
    this.splitWord = true
    this.leftRecurDetection = true
    this.useMemorizedParser = true
    this.spacingParser = this.cGlob(this.cStar(this.pSpace()))
    // ===============

    const nodes = internalChildren(grammarAst, "grammar AST root")
    const rootStm = nodes[0]!
    const optionStms = internalChildren(nodes[1]!, "grammar AST option-section")
    const defStms = internalChildren(nodes[2]!, "grammar AST def-section")
    const rootName = rootStm.stringValue()

    for (const stm of optionStms) {
      const es = matchChildren(stm, Yc.OPTION_STM)
      this.setOption(es[0]!, es.slice(1))
    }

    if (options !== undefined) this.applyOptions(options)

    for (const stm of defStms) {
      const es = matchChildren(stm, Yc.DEF_STM)
      const name = es[0]!.stringValue()
      const ps = this.buildExpList(es.slice(1))
      this.defParser(name, this.cSeq(...ps))
    }

    this.setRootParser(this.get(rootName))
  }

  private applyOptions(options: GrammarOptions): void {
    if (options.caseSensitive !== undefined) this.caseSensitive = options.caseSensitive
    if (options.splitWord !== undefined) this.splitWord = options.splitWord
    if (options.leftRecurDetection !== undefined) this.leftRecurDetection = options.leftRecurDetection
    if (options.useMemorizedParser !== undefined) this.useMemorizedParser = options.useMemorizedParser
    if (options.locale !== undefined) {
      const language = options.locale.language
      if (language !== undefined) {
        this.locale.language = language
        if (options.locale.entries !== undefined) this.locale.set(language, options.locale.entries)
      }
    }
    if (options.debug !== undefined) this.debugSink = options.debug
  }

  private setOption(cmd: Node, args: readonly Node[]): void {
    switch (cmd.type) {
      case Yc.CASE_SENSITIVE:
        this.caseSensitive = this.onOrOffValue(args[0]!)
        break
      case Yc.LEX_IGNORE:
        this.spacingParser = this.get(args[0]!.stringValue())
        break
      case Yc.LEX_MODE:
        this.spacingParser = null
        break
      case Yc.SPLIT_WORD:
        this.splitWord = this.onOrOffValue(args[0]!)
        break
      case Yc.INCLUDE_BUILTIN:
        this.loadBuildinParsers()
        break
      case Yc.SET_MESSAGE_LOCALE_START:
        this.messageLocaleStart = args[0]!.stringValue()
        break
      case Yc.SET_MESSAGE_LOCALE_END:
        this.messageLocaleEnd = args[0]!.stringValue()
        break
      default:
        throw matchError(cmd)
    }
  }

  private onOrOffValue(node: Node): boolean {
    switch (node.type) {
      case Yc.OPTION_ON:
        return true
      case Yc.OPTION_OFF:
        return false
      default:
        throw matchError(node)
    }
  }

  private buildExp(exp: Node): Parser {
    const es = childrenOf(exp)
    switch (exp.type) {
      case Yc.NAMED_EXP:
        return this.cIs(es[0]!.stringValue(), this.buildExp(es[1]!))
      case Yc.GLOB_EXP:
        return this.cGlob(this.buildExp(es[0]!))
      case Yc.OP_EXP:
        return this.applyOp(es[0]!, ...this.buildExpList(es.slice(1)))
      case Yc.ERR_EXP:
        return this.cIfFail(es[0]!.stringValue(), this.cSeq(...this.buildExpList(es.slice(1))))
      case Yc.SEQ_EXP:
        return this.cSeq(...this.buildExpList(es))
      case Yc.ANY_EXP:
        return this.pAny()
      case Yc.DBG_EXP:
        return this.cDebug(this.cSeq(...this.buildExpList(es)))
      case Yc.DBG_1EXP:
        return this.cDebug(this.buildExp(es[0]!))
      case Yc.CHAR_EXP:
        return this.buildCharExp(es[0]!)
      case Yc.GLOB_CHAR_EXP:
        return this.cGlob(this.buildCharExp(es[0]!))
      case Yc.WORD_EXP:
        return this.buildWordExp(es)
      case Yc.GLOB_WORD_EXP:
        return this.cGlob(this.buildWordExp(es))
      case Yc.VAR_EXP:
        return this.get(concatValue(es))
      case Yc.ARRAY_EXP: {
        const parser = this.buildExp(es[1]!)
        const size = toIntOrNull(es[0]!.stringValue())
        if (size !== null) return this.cArray(parser, size)
        throw new GrammarError(`expect an integer at ${nodePosition(es[0]!)}`)
      }
      default:
        throw matchError(exp)
    }
  }

  private buildExpList(es: readonly Node[]): Parser[] {
    return es.map((e) => this.buildExp(e))
  }

  private buildCharExp(node: Node): Parser {
    switch (node.type) {
      case Yc.NORMAL_CHAR: {
        const es = childrenOf(node)
        if (es.length === 1) {
          return this.pChar((es[0] as Leaf).value)
        }
        throw new GrammarError(`not a char (${nodePosition(es[0]!)})`)
      }
      case Yc.SPECIAL_CHAR: {
        const es = childrenOf(node)
        const charName = concatValue(es)
        switch (charName) {
          case Yc.SPECIAL_CHAR_EOF:
            return this.pEof()
          case Yc.SPECIAL_CHAR_NEWLINE:
            return this.pNewline()
          case Yc.SPECIAL_CHAR_SPACE:
            return this.pSpace()
          default:
            throw new GrammarError(`unknown char '${charName}' at ${nodePosition(es[0]!)}`)
        }
      }
      default:
        throw matchError(node)
    }
  }

  private buildWordExp(es: readonly Node[]): Parser {
    const word = concatValue(es)
    if (!isBlank(word) && this.splitWord) {
      const parts = splitAsciiWhitespace(word).filter((s) => s.length > 0)
      return this.cSeq(...parts.map((p) => this.pEqAndRetain(p, this.caseSensitive)))
    }
    return this.pEqAndRetain(word, this.caseSensitive)
  }

  private applyOp(op: Node, ...ps: Parser[]): Parser {
    const opName = op.stringValue()
    switch (opName) {
      case Yc.PLUS_CMB:
        return this.cPlus(...ps)
      case Yc.STAR_CMB:
        return this.cStar(...ps)
      case Yc.OR_CMB:
        return this.cOr(...ps)
      case Yc.SEQ_CMB:
        return this.cSeq(...ps)
      case Yc.JOIN_CMB:
        return this.cJoin(ps[0]!, ...ps.slice(1))
      case Yc.JOIN_PLUS_CMB:
        return this.cJoinPlus(ps[0]!, ...ps.slice(1))
      case Yc.NOT_CMB:
        return this.cNot(...ps)
      case Yc.MAYBE_CMB:
        return this.cMaybe(...ps)
      default:
        throw new GrammarError(`unknown op '${opName}' at ${nodePosition(op)}`)
    }
  }
}

/**
 * Parses a yccc grammar and returns the parser it describes.
 * Throws `GrammarError` when the grammar itself does not parse or is invalid.
 */
export function loadGrammar(source: string, options?: GrammarOptions): Grammar {
  const parser = new Yc.GrammarParser()
  const result = parser.parse(source)
  if (!result.ok) {
    throw new GrammarError(`parsing grammar failed: (message: ${result.message}, rest: ${result.failRest})`)
  }
  return new Grammar(result.nodes[0]!, options)
}

/** `loadGrammar` without the exception, for editors and untrusted input. */
export function tryLoadGrammar(
  source: string,
  options?: GrammarOptions,
): { ok: true; grammar: Grammar } | { ok: false; error: GrammarError } {
  try {
    return { ok: true, grammar: loadGrammar(source, options) }
  } catch (e) {
    if (e instanceof GrammarError) return { ok: false, error: e }
    throw e
  }
}

/** Builds a parser from an already parsed grammar AST (Kotlin `load(grammarAst)`). */
export function loadGrammarFromAst(ast: Node, options?: GrammarOptions): Grammar {
  return new Grammar(ast, options)
}
