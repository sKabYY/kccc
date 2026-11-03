/**
 * Built-in parsers and combinators. Mirrors `parser/CombinedParser.kt`.
 *
 * Every method keeps the exact failure/position behavior of the Kotlin
 * implementation: which failure is reported, how results merge, when spacing
 * is consumed. The conformance fixtures compare the printed result tree, so a
 * deviation here shows up as a byte diff rather than a subtle bug.
 */

import { CharScanner, Locale, isJavaWhitespace, lowerChar, type Position, type Token } from "./data.js"
import { TokenStream } from "./data.js"
import { GrammarError } from "./errors.js"
import { deeper, failure, makeLeaf, makeNode, resultToSExp, success } from "./tree.js"
import type { Node, ParseResult, ParseSuccess } from "./tree.js"
import { EMPTY_STACK, FAKE_STACK, MemorizedParser, ParserImpl } from "./parser.js"
import type { Parser } from "./parser.js"

export class CombinedParser {
  readonly locale: Locale = new Locale()
  protected readonly scanner: CharScanner = new CharScanner()
  protected leftRecurDetection = true
  protected useMemorizedParser = true
  protected messageLocaleStart: string | null = null
  protected messageLocaleEnd: string | null = null
  protected spacingParser: Parser | null = null
  /** Sink used by the `?[...]` / `??[...]` debug grammar constructs. */
  protected debugSink: (s: string) => void = (s) => {
    console.log(s)
  }

  private rootParser: Parser | null = null
  private readonly env = new Map<string, Parser>()
  private readonly memorizedParsers: MemorizedParser[] = []

  protected constructor() {}

  protected setRootParser(parser: Parser): void {
    this.rootParser = parser
  }

  parse(src: string): ParseResult {
    const root = this.rootParser
    if (root === null) throw new GrammarError("RootParser is uninitialized")
    return this.parseByParser(root, src)
  }

  parseBy(name: string, src: string): ParseResult {
    return this.parseByParser(this.get(name), src)
  }

  private parseByParser(parser: Parser, src: string): ParseResult {
    const toks = this.scanner.scan(src)
    this.resetMemorizedParsers()
    return this.wrapperEof(parser).parse(toks, this.leftRecurDetection ? EMPTY_STACK : FAKE_STACK)
  }

  private wrapperEof(parser: Parser): Parser {
    return this.cSeq(this.pSpacing(), parser, this.pEof())
  }

  private makeMemorizedParser(parser: Parser): MemorizedParser {
    const memorized = new MemorizedParser(parser)
    this.memorizedParsers.push(memorized)
    return memorized
  }

  private resetMemorizedParsers(): void {
    for (const p of this.memorizedParsers) p.clear()
  }

  protected defParser(name: string, parser: Parser): Parser {
    const registered = this.useMemorizedParser ? this.makeMemorizedParser(parser) : parser
    this.envSet(name, registered)
    return this.get(name)
  }

  protected get(name: string): Parser {
    return new ParserImpl((toks, stk) => this.envGet(name).parse(toks, stk), name)
  }

  // =========================================================================
  // builtin parsers
  // =========================================================================

  protected pEof(): Parser {
    return this.cIfFail("expect EOF", this.cNot(this.pAny()))
  }

  protected pAlpha(): Parser {
    return this.cIfFail(
      `${this.locale.l("expect")} alpha`,
      this.cOr(this.pRange("a", "z"), this.pRange("A", "Z")),
    )
  }

  protected pDigit(): Parser {
    return this.pRange("0", "9")
  }

  protected pSpace(): Parser {
    return this.pTokenPred(`${this.locale.l("expect")} whitespace`, (tok) => isJavaWhitespace(tok.value))
  }

  protected pNewline(): Parser {
    return this.cOr(this.pCharArray("\r\n"), this.pChar("\n"), this.pChar("\r"))
  }

  protected pNumber(): Parser {
    const digits = this.cPlus(this.pDigit())
    return this.cSeq(
      this.cMaybe(this.cOr(this.pChar("+"), this.pChar("-"))), // sign
      digits, // integer part
      this.cMaybe(this.cSeq(this.pChar("."), digits)), // fraction
      // exponent
      this.cMaybe(this.cOr(this.pChar("E"), this.pChar("e")), this.cMaybe(this.pChar("-")), digits),
      this.pSpacing(),
    )
  }

  protected loadBuildinParsers(): void {
    this.defParser("alpha", this.pAlpha())
    this.defParser("digit", this.pDigit())
    this.defParser("number", this.pNumber())
  }

  // =========================================================================
  // char
  // =========================================================================

  protected pTokenPred(failMessage: string, pred: (tok: Token) => boolean): Parser {
    return new ParserImpl((toks) => {
      if (toks.isEndOfStream()) {
        return this.outputFail(failMessage, toks)
      }
      const tok = toks.car()
      if (pred(tok)) {
        return this.outputNode(makeLeaf(tok), toks.cdr())
      }
      return this.outputFail(failMessage, toks)
    })
  }

  protected pChar(c: string, caseSensitive = true): Parser {
    return this.pTokenPred(`${this.locale.l("expect")} "${c}"`, (tok) => {
      if (caseSensitive) return tok.value === c
      return lowerChar(tok.value) === lowerChar(c)
    })
  }

  protected pCharArray(s: string, caseSensitive = true): Parser {
    const chars: Parser[] = []
    for (let i = 0; i < s.length; i++) chars.push(this.pChar(s.charAt(i), caseSensitive))
    return this.cIfFail(`${this.locale.l("expect")} "${s}"`, this.cSeq(...chars))
  }

  protected pRange(c1: string, c2: string): Parser {
    return this.pTokenPred(`expect char between '${c1}' and '${c2}'`, (tok) => c1 <= tok.value && tok.value <= c2)
  }

  protected pEmpty(): Parser {
    return new ParserImpl((toks) => this.outputEmpty(toks, toks.position(), toks.position()))
  }

  protected pAny(): Parser {
    return new ParserImpl((toks) => {
      if (toks.isEndOfStream()) {
        return this.outputFail(`ANY fail: ${this.locale.l("reach eof")}`, toks)
      }
      return this.outputNode(makeLeaf(toks.car()), toks.cdr())
    })
  }

  protected pSpacing(): Parser {
    return new ParserImpl((toks, stk) => {
      const sp = this.spacingParser
      if (sp === null) {
        return this.outputEmpty(toks, toks.position(), toks.position())
      }
      return sp.parse(toks, stk)
    })
  }

  // =========================================================================
  // token
  // =========================================================================

  protected pEqAndRetain(s: string, caseSensitive = true): Parser {
    return this.cSeq(this.pCharArray(s, caseSensitive), this.pSpacing())
  }

  protected pEq(s: string, caseSensitive = true): Parser {
    return this.cGlob(this.pEqAndRetain(s, caseSensitive))
  }

  // =========================================================================
  // combinators
  // =========================================================================

  protected cSeq(...ps: Parser[]): Parser {
    if (ps.length === 0) throw new GrammarError("empty CSeq")
    if (ps.length === 1) return ps[0]!
    return new ParserImpl((toks, stk) => {
      let curToks = toks
      let deepest: ParseResult | null = null
      const results: ParseResult[] = []
      for (const p of ps) {
        const r = p.parse(curToks, stk)
        if (!r.ok) {
          const d = deeper(r, deepest)
          deepest = d
          results.push(d)
          break
        }
        curToks = r.rest
        results.push(r)
      }
      const message = deepest === null ? null : deepest.message
      const failRest = deepest === null ? null : deepest.failRest
      return this.mergeResults(results, curToks, message, failRest)
    })
  }

  protected cDebug(...ps: Parser[]): Parser {
    const parser = this.cSeq(...ps)
    return new ParserImpl((toks, stk) => {
      const r = parser.parse(toks, stk)
      this.debugSink(`===[Debug]===\n${resultToSExp(r).toPrettyString()}\n`)
      return r
    })
  }

  protected cIs(type: string, ...ps: Parser[]): Parser {
    const parser = this.cSeq(...ps)
    return new ParserImpl((toks, stk) => {
      const r = parser.parse(toks, stk)
      if (!r.ok) return r
      return this.outputNode(makeNode(type, r.nodes, r.start, r.end), r.rest, r.message, r.failRest)
    })
  }

  protected cGlob(...ps: Parser[]): Parser {
    const parser = this.cSeq(...ps)
    return new ParserImpl((toks, stk) => {
      const r = parser.parse(toks, stk)
      if (!r.ok) return r
      const start = r.start
      const end = r.end
      if (start === null) throw new GrammarError("parse result missing start")
      if (end === null) throw new GrammarError("parse result missing end")
      return this.outputEmpty(r.rest, start, end, r.message, r.failRest)
    })
  }

  protected cIfFail(failMessage: string, parser: Parser): Parser {
    return new ParserImpl((toks, stk) => {
      const r = parser.parse(toks, stk)
      if (r.ok) return r
      return this.outputFail(failMessage, r.rest)
    })
  }

  protected cOr(...ps: Parser[]): Parser {
    return new ParserImpl((toks, stk) => {
      let result: ParseSuccess | null = null
      let deepest: ParseResult | null = null
      for (const p of ps) {
        const r = p.parse(toks, stk)
        if (r.ok) {
          result = r
          break
        }
        deepest = deeper(r, deepest)
      }
      if (result !== null) return result
      if (deepest !== null) return deepest
      return this.outputFail("empty OR", toks)
    })
  }

  protected cNot(...ps: Parser[]): Parser {
    const parser = this.cSeq(...ps)
    return new ParserImpl((toks, stk) => {
      if (!toks.isEndOfStream() && parser.parse(toks, stk).ok) {
        return this.outputFail("NOT fail", toks)
      }
      return this.outputEmpty(toks, toks.position(), toks.position())
    })
  }

  protected cStar(...ps: Parser[]): Parser {
    const parser = this.cSeq(...ps)
    return new ParserImpl((toks, stk) => {
      let curToks = toks
      const results: ParseResult[] = []
      let deepest: ParseResult | null = null
      for (;;) {
        const r = parser.parse(curToks, stk)
        deepest = deeper(r, deepest)
        if (!r.ok) break
        // `toks == r.Rest` is TokenStream's custom equality: same scanner, same
        // text, same position object.
        if (curToks.equals(r.rest)) {
          this.fatal("empty-star detected", parser, curToks, stk)
        }
        curToks = r.rest
        results.push(r)
      }
      const d = deepest as ParseResult
      return this.mergeResults(results, curToks, d.message, d.failRest)
    })
  }

  protected cMaybe(...ps: Parser[]): Parser {
    return this.cOr(this.cSeq(...ps), this.pEmpty())
  }

  protected cPlus(...ps: Parser[]): Parser {
    const parser = this.cSeq(...ps)
    return this.cSeq(parser, this.cStar(parser))
  }

  protected cJoin(sepParser: Parser, ...ps: Parser[]): Parser {
    const parser = this.cSeq(...ps)
    return this.cMaybe(this.cJoinPlus(sepParser, parser))
  }

  protected cJoinPlus(sepParser: Parser, ...ps: Parser[]): Parser {
    const parser = this.cSeq(...ps)
    return this.cSeq(parser, this.cStar(sepParser, parser))
  }

  protected cArray(parser: Parser, n: number): Parser {
    return this.cSeq(...Array.from({ length: n }, () => parser))
  }

  // =========================================================================
  // internals
  // =========================================================================

  private envGet(name: string): Parser {
    const p = this.env.get(name)
    if (p !== undefined) return p
    throw new GrammarError(`"${name}" ${this.locale.l("is undefined")}`)
  }

  private envSet(name: string, p: Parser): void {
    this.env.set(name, p)
  }

  private fatal(message: string, parser: Parser, toks: TokenStream, stk: Parameters<Parser["parse"]>[1]): never {
    throw new GrammarError(
      `${message}\nparser: ${parser}\nrest: ${toks}\nstack trace: ${stk.toSExp().toPrettyString()}`,
    )
  }

  private makeResult(
    ok: boolean,
    nodes: Node[] | null,
    rest: TokenStream,
    start: Position | null,
    end: Position | null,
    message: string | null = null,
    failRest: TokenStream | null = null,
  ): ParseResult {
    const fr = failRest ?? rest
    if (ok) {
      return success(nodes ?? [], rest, message, fr, start, end)
    }
    if (message === null) throw new GrammarError("failure result without a message")
    return failure(nodes, rest, message, fr, start, end)
  }

  private outputNode(
    node: Node,
    rest: TokenStream,
    message: string | null = null,
    failRest: TokenStream | null = null,
  ): ParseResult {
    return this.makeResult(true, [node], rest, node.start, node.end, message, failRest)
  }

  private outputEmpty(
    rest: TokenStream,
    start: Position,
    end: Position,
    message: string | null = null,
    failRest: TokenStream | null = null,
  ): ParseResult {
    return this.makeResult(true, [], rest, start, end, message, failRest)
  }

  private outputFail(message: string, rest: TokenStream): ParseResult {
    return failure(null, rest, this.messageLocaleString(message), rest)
  }

  /** Collects the results of a sequence: nodes from the successes, state from the last one. */
  private mergeResults(
    results: ParseResult[],
    rest: TokenStream,
    messageIn: string | null,
    failRestIn: TokenStream | null,
  ): ParseResult {
    let message = messageIn
    let failRest = failRestIn
    const nodes: Node[] = []
    for (const r of results) {
      if (r.ok && r.nodes !== null) nodes.push(...r.nodes)
    }
    let ok = true
    if (results.length > 0) {
      const lastR = results[results.length - 1]!
      if (message === null) {
        message = lastR.message
        failRest = lastR.failRest
      }
      ok = lastR.ok
    }
    const startIdx = results.length === 0 ? rest.position() : results[0]!.start
    const endIdx = results.length === 0 ? rest.position() : results[results.length - 1]!.end
    return this.makeResult(ok, nodes, rest, startIdx, endIdx, message, failRest)
  }

  /**
   * Replaces `start...end` delimited message keys through `Locale`. The Kotlin
   * original builds a regex per call; this scans the string directly with the
   * same observable behavior (left-to-right, deduplicated by the full match,
   * then every occurrence replaced).
   */
  private messageLocaleString(s: string): string {
    const start = this.messageLocaleStart
    const end = this.messageLocaleEnd
    if (start === null || start.length === 0 || end === null || end.length === 0) {
      return s
    }
    const endChars = new Set<string>()
    for (let i = 0; i < end.length; i++) endChars.add(end.charAt(i))

    const items: string[] = []
    const seen = new Set<string>()
    let from = 0
    while (from <= s.length - start.length) {
      const idx = s.indexOf(start, from)
      if (idx < 0) break
      let p = idx + start.length
      while (p < s.length && !endChars.has(s.charAt(p))) p++
      if (p + end.length <= s.length && s.startsWith(end, p)) {
        const item = s.slice(idx, p + end.length)
        if (!seen.has(item)) {
          seen.add(item)
          items.push(item)
        }
        from = p + end.length
      } else {
        from = idx + 1
      }
    }

    let result = s
    for (const item of items) {
      const key = item.slice(start.length, item.length - end.length)
      result = result.split(item).join(this.locale.l(key))
    }
    return result
  }
}
