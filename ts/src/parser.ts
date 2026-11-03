/**
 * Parser runtime: the `Parser` interface, its parser-function wrapper, the
 * left-recursion stack and the memoizing parser.
 * Mirrors `parser/IParser.kt`, `IParseStack.kt`, `ParseStack.kt`,
 * `FakeParseStack.kt`, `ParserImpl.kt` and `MemorizedParser.kt`.
 */

import { SExp, type ListSExp, type TokenStream } from "./data.js"
import { GrammarError } from "./errors.js"
import type { ParseResult } from "./tree.js"

export type ParseFunction = (toks: TokenStream, stk: ParseStack) => ParseResult

export interface Parser {
  readonly name?: string
  parse(toks: TokenStream, stk: ParseStack): ParseResult
  toString(): string
}

export interface ParseStack {
  isEmpty(): boolean
  has(parser: Parser, toks: TokenStream): boolean
  extend(parser: Parser, toks: TokenStream): ParseStack
  toSExp(): ListSExp
}

/** Throws the Kotlin `ParseException` message used for internal parser faults. */
export function fatal(
  message: string,
  parser: Parser,
  toks: TokenStream,
  stk: ParseStack,
): never {
  throw new GrammarError(
    `${message}\nparser: ${parser}\nrest: ${toks}\nstack trace: ${stk.toSExp().toPrettyString()}`,
  )
}

/**
 * A parser built from a closure. Adds the left-recursion check and stamps the
 * result with the parser name, exactly like Kotlin's `ParserImpl`.
 */
export class ParserImpl implements Parser {
  readonly name?: string
  private readonly parseFn: ParseFunction

  constructor(parseFn: ParseFunction, name?: string) {
    this.parseFn = parseFn
    this.name = name
  }

  parse(toks: TokenStream, stk: ParseStack): ParseResult {
    if (stk.has(this, toks)) {
      fatal("left-recursion detected", this, toks, stk)
    }
    const result = this.parseFn(toks, stk.extend(this, toks))
    result.parserName = this.name ?? null
    return result
  }

  toString(): string {
    return this.name === undefined ? Object.prototype.toString.call(this) : `<Parser:${this.name}>`
  }
}

/** Caches parse results per input offset, like the Kotlin `MemorizedParser`. */
export class MemorizedParser implements Parser {
  private readonly parser: Parser
  private readonly memo = new Map<number, ParseResult>()

  constructor(parser: Parser) {
    this.parser = parser
  }

  parse(toks: TokenStream, stk: ParseStack): ParseResult {
    const offset = toks.car().start.offset
    let r = this.memo.get(offset)
    if (r === undefined) {
      r = this.parser.parse(toks, stk)
      this.memo.set(offset, r)
    }
    return r
  }

  clear(): void {
    this.memo.clear()
  }

  toString(): string {
    return this.parser.toString()
  }
}

class ParseStackItem implements ParseStack {
  private readonly parser: Parser
  private readonly toks: TokenStream
  private readonly prev: ParseStack

  constructor(parser: Parser, toks: TokenStream, prev: ParseStack) {
    this.parser = parser
    this.toks = toks
    this.prev = prev
  }

  isEmpty(): boolean {
    return false
  }

  has(parser: Parser, toks: TokenStream): boolean {
    if (this.parser === parser && this.toks.equals(toks)) return true
    return this.prev.has(parser, toks)
  }

  extend(parser: Parser, toks: TokenStream): ParseStack {
    return new ParseStackItem(parser, toks, this)
  }

  toSExp(): ListSExp {
    const lst = SExp.list(
      SExp.list(SExp.list("parser", this.parser.toString()), SExp.list("toks", this.toks.toString())),
    )
    lst.append(this.prev.toSExp())
    return lst
  }
}

/** The real stack: detects left recursion, one frame per parser invocation. */
export const EMPTY_STACK: ParseStack = {
  isEmpty: () => true,
  has: () => false,
  extend: (parser, toks) => new ParseStackItem(parser, toks, EMPTY_STACK),
  toSExp: () => SExp.list(),
}

/** Stand-in used when `leftRecurDetection` is off; never grows. */
export const FAKE_STACK: ParseStack = {
  isEmpty: () => true,
  has: () => false,
  extend: () => FAKE_STACK,
  toSExp: () => SExp.list(),
}
