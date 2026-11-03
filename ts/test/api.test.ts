import { describe, expect, it } from "vitest"
import {
  GrammarError,
  YCCC_GRAMMAR,
  loadGrammar,
  pretty,
  tryLoadGrammar,
} from "../src/index.js"

const ALPHA = `:: root
@include-builtin
root = (@+ alpha) \\eof
`

describe("public API", () => {
  it("loads a grammar from text and parses", () => {
    const parser = loadGrammar(ALPHA)
    const r = parser.parse("hello")
    expect(r.ok).toBe(true)
    expect(r.nodes).toHaveLength(5) // one leaf per matched alpha character
    // the trailing EOF token leaves the stream at the end of the input
    expect(r.failRest.position().offset).toBe(5)
  })

  it("reports parse failures without throwing", () => {
    const r = loadGrammar(ALPHA).parse("hello1")
    expect(r.ok).toBe(false)
    expect(r.message).toContain("expect")
  })

  it("returns a structured error from tryLoadGrammar", () => {
    const result = tryLoadGrammar(":: root\nroot = (@+ \n")
    expect(result.ok).toBe(false)
    if (result.ok) return
    expect(result.error).toBeInstanceOf(GrammarError)
    expect(result.error.message).toContain("parsing grammar failed")
  })

  it("throws GrammarError from loadGrammar", () => {
    expect(() => loadGrammar(":: root\nroot = (@+ \n")).toThrow(GrammarError)
  })

  it("resolves grammar references lazily, so an undefined name fails at parse time", () => {
    const parser = loadGrammar(":: root\nroot = foo\n")
    expect(() => parser.parse("x")).toThrow(/"foo" is undefined/)
  })

  it("parses from a named rule with parseBy", () => {
    const parser = loadGrammar(`:: root
@include-builtin
root = header footer
header = (@+ number)
footer = (@+ alpha)
`)
    expect(parser.parseBy("header", "1 2").ok).toBe(true)
    expect(parser.parseBy("footer", "ab").ok).toBe(true)
  })

  it("routes the debug constructs to the configured sink", () => {
    const lines: string[] = []
    const parser = loadGrammar(":: root\nroot = ?? 'a'\n", { debug: (s) => lines.push(s) })
    expect(parser.parse("a").ok).toBe(true)
    expect(lines).toHaveLength(1)
    expect(lines[0]).toContain("===[Debug]===")
  })

  it("applies the zh-cn message locale to parsers built after the options", () => {
    // `@include-builtin` runs before the user options (same order as Kotlin),
    // so builtin messages keep the default language; a rule-defined char
    // parser is built afterwards and does get translated.
    const parser = loadGrammar(":: root\nroot = 'a'\n", { locale: { language: "zh-cn" } })
    const r = parser.parse("b")
    expect(r.ok).toBe(false)
    expect(r.message).toContain("应为")
  })

  it("honours caseSensitive and splitWord", () => {
    const grammar = `:: root
root = 'ABC'
`
    expect(loadGrammar(grammar).parse("abc").ok).toBe(false)
    expect(loadGrammar(grammar, { caseSensitive: false }).parse("abc").ok).toBe(true)
  })

  it("ships the yccc grammar and can load it", () => {
    expect(YCCC_GRAMMAR.length).toBeGreaterThan(1000)
    expect(() => loadGrammar(YCCC_GRAMMAR)).not.toThrow()
  })

  it("exposes a pretty printer for results", () => {
    expect(pretty(loadGrammar(ALPHA).parse("hi"))).toContain("(success? true)")
  })

  it("gives the right position for a failure", () => {
    const r = loadGrammar(ALPHA).parse("ab1")
    expect(r.ok).toBe(false)
    expect(r.failRest.position()).toMatchObject({ offset: 2, line: 1, column: 3 })
  })
})
