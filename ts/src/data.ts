/**
 * Data layer: positions, s-expressions, messages, tokens and token streams.
 * Mirrors `common/TextPosition.kt`, `common/SExp.kt`, `common/Locale.kt` and
 * `scanner/*.kt`.
 *
 * Everything works on UTF-16 code units, exactly like Kotlin's `Char`: a lone
 * surrogate is a character of its own and never a code point. That is what
 * keeps offsets, positions and error messages identical to the JVM.
 */

import { EndOfInputError } from "./errors.js"

// ---------------------------------------------------------------------------
// Position (`TextPosition`)
// ---------------------------------------------------------------------------

export interface Position {
  readonly offset: number
  readonly line: number
  readonly column: number
}

/** Shared initial position, same role as `TextPosition.startPos`. */
export const START_POSITION: Position = Object.freeze({ offset: 0, line: 1, column: 1 })

export function shiftChar(pos: Position, c: string): Position {
  if (c === "\n") {
    return { offset: pos.offset + 1, line: pos.line + 1, column: 1 }
  }
  return { offset: pos.offset + 1, line: pos.line, column: pos.column + 1 }
}

// ---------------------------------------------------------------------------
// Character classification
// ---------------------------------------------------------------------------

/**
 * `Character.isWhitespace`, which is *not* JavaScript's `\s`:
 * it includes U+0009..U+000D and U+001C..U+001F but excludes the
 * non-breaking spaces U+00A0, U+2007 and U+202F that `\s` would match.
 *
 * Used both by the `space` builtin and by `ValueSExp` to decide whether a
 * value needs quoting, so a deviation here changes the pretty output.
 */
export function isJavaWhitespace(ch: string): boolean {
  const code = ch.charCodeAt(0)
  if (code >= 0x09 && code <= 0x0d) return true
  if (code >= 0x1c && code <= 0x1f) return true
  if (code === 0x20 || code === 0x1680) return true
  if (code >= 0x2000 && code <= 0x2006) return true
  if (code >= 0x2008 && code <= 0x200a) return true
  if (code === 0x2028 || code === 0x2029 || code === 0x205f || code === 0x3000) return true
  return false
}

function containsWhitespace(s: string): boolean {
  for (let i = 0; i < s.length; i++) {
    if (isJavaWhitespace(s[i] as string)) return true
  }
  return false
}

/** `Char.lowercaseChar()` without the multi-character expansions of `toLowerCase`. */
export function lowerChar(ch: string): string {
  const lowered = ch.toLowerCase()
  return lowered.length === 1 ? lowered : ch
}

// ---------------------------------------------------------------------------
// S-expressions (`SExp` / `ListSExp` / `ValueSExp`)
// ---------------------------------------------------------------------------

export abstract class SExp {
  abstract toPrettyString(firstOfList?: boolean, indent?: number): string

  static list(...os: unknown[]): ListSExp {
    return new ListSExp(...os)
  }

  static value(obj: unknown): ValueSExp {
    return new ValueSExp(obj)
  }
}

function ensureSExp(obj: unknown): SExp {
  return obj instanceof SExp ? obj : new ValueSExp(obj)
}

export class ListSExp extends SExp {
  private readonly sexps: SExp[]

  constructor(...os: unknown[]) {
    super()
    this.sexps = os.map(ensureSExp)
  }

  push(...os: unknown[]): void {
    for (const o of os) this.sexps.push(ensureSExp(o))
  }

  pushFront(o: unknown): void {
    this.sexps.unshift(ensureSExp(o))
  }

  append(e: ListSExp): void {
    this.sexps.push(...e.sexps)
  }

  override toPrettyString(firstOfList = false, indent = 0): string {
    const sameLine = firstOfList
    const sb: string[] = []
    if (!sameLine) sb.push(" ".repeat(indent))
    sb.push("(")
    if (this.sexps.length > 0) {
      const newIndent = indent + 1
      const first = this.sexps[0]!.toPrettyString(true, newIndent)
      sb.push(first)
      const allValues = this.sexps.every((s) => s instanceof ValueSExp)
      if (allValues) {
        const maxLineLength = 80
        let curLength = first.length
        for (const sexp of this.sexps.slice(1)) {
          if (curLength < maxLineLength) {
            const str = sexp.toPrettyString(true, newIndent)
            sb.push(" ")
            sb.push(str)
            curLength += str.length + 1
          } else {
            const str = sexp.toPrettyString(false, newIndent)
            sb.push("\n")
            sb.push(str)
            curLength = str.length
          }
        }
      } else {
        for (const sexp of this.sexps.slice(1)) {
          sb.push("\n")
          sb.push(sexp.toPrettyString(false, newIndent))
        }
      }
    }
    sb.push(")")
    return sb.join("")
  }
}

export class ValueSExp extends SExp {
  private readonly value: unknown

  constructor(value: unknown) {
    super()
    this.value = value
  }

  override toPrettyString(firstOfList = false, indent = 0): string {
    let s = this.value === null || this.value === undefined ? "<null>" : String(this.value)
    if (typeof this.value === "string" && containsWhitespace(s)) {
      s = `"${s.replace(/"/g, '\\"')}"`
    }
    if (firstOfList || indent === 0) return s
    return " ".repeat(indent) + s
  }
}

// ---------------------------------------------------------------------------
// Messages (`Locale`)
// ---------------------------------------------------------------------------

const GLOBAL_LOCALES: ReadonlyMap<string, ReadonlyMap<string, string>> = new Map([
  [
    "zh-cn",
    new Map([
      ["expect <<EOF>>", "分析已结束，但文本还有内容"],
      ["expect", "应为"],
      ["not match regex", "无法匹配正则"],
      ["reach eof", "文本已读到末尾"],
      ["is undefined", "未定义"],
      ["block comment match error", "注释结束标记缺失"],
      ["string match error", "字符串结束标记缺失"],
      ["regex match error", "正则结束标记缺失"],
    ]),
  ],
])

export class Locale {
  language: string | null = null

  private readonly locales = new Map<string, Map<string, string>>()

  l(s: string): string {
    return this.lookup(this.locales, s) ?? this.lookup(GLOBAL_LOCALES, s) ?? s
  }

  set(lang: string, entries: Record<string, string> | ReadonlyMap<string, string>): void {
    const dict = this.locales.get(lang)
    const incoming = entries instanceof Map ? entries : new Map(Object.entries(entries))
    if (dict !== undefined) {
      for (const [k, v] of incoming) dict.set(k, v)
    } else {
      this.locales.set(lang, new Map(incoming))
    }
  }

  private lookup(
    locales: ReadonlyMap<string, ReadonlyMap<string, string>>,
    s: string,
  ): string | null {
    const lang = this.language
    if (lang === null) return null
    return locales.get(lang)?.get(s) ?? null
  }
}

// ---------------------------------------------------------------------------
// Token (`Token`)
// ---------------------------------------------------------------------------

export class Token {
  private readonly _value: string | null
  readonly start: Position
  readonly end: Position

  private constructor(value: string | null, start: Position) {
    this._value = value
    this.start = start
    this.end = value !== null ? shiftChar(start, value) : start
  }

  get value(): string {
    if (this._value !== null) return this._value
    throw new EndOfInputError()
  }

  isEof(): boolean {
    return this._value === null
  }

  toString(): string {
    if (this.isEof()) {
      return `<EOF at=(${this.start.line}, ${this.start.column})>`
    }
    return `<Token char='${this.value}', from=(${this.start.line}, ${this.start.column}), to=(${this.end.line}, ${this.end.column})>`
  }

  static make(value: string, start: Position): Token {
    return new Token(value, start)
  }

  static eof(start: Position): Token {
    return new Token(null, start)
  }
}

// ---------------------------------------------------------------------------
// Scanner and stream (`CharScanner` / `TokenStream`)
// ---------------------------------------------------------------------------

export class CharScanner {
  scan(str: string): TokenStream {
    return new TokenStream(this, str, START_POSITION)
  }

  scan1(str: string, start: Position): Token {
    if (str.length === start.offset) {
      return Token.eof(start)
    }
    return Token.make(str.charAt(start.offset), start)
  }
}

export class TokenStream {
  private readonly scanner: CharScanner
  readonly str: string
  private readonly cur: Token

  constructor(scanner: CharScanner, str: string, pos: Position) {
    this.scanner = scanner
    this.str = str
    this.cur = scanner.scan1(str, pos)
  }

  car(): Token {
    return this.cur
  }

  cdr(): TokenStream {
    return new TokenStream(this.scanner, this.str, this.cur.end)
  }

  isEndOfStream(): boolean {
    return this.cur.isEof()
  }

  position(): Position {
    return this.cur.start
  }

  toString(): string {
    return `<Stream: car=${this.car()}>`
  }

  /**
   * The Kotlin `equals` override: scanner by reference, text by value, and
   * position by *reference*. `cStar`'s empty-match guard and the left
   * recursion stack both depend on this exact rule, so it is a named method
   * instead of a structural comparison.
   */
  equals(other: TokenStream): boolean {
    return this.scanner === other.scanner && this.str === other.str && this.position() === other.position()
  }
}
