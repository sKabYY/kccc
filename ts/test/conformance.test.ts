import { describe, expect, it } from "vitest"
import { readFileSync } from "node:fs"
import { CASES } from "../conformance/cases.js"
import type { CaseSpec } from "../conformance/types.js"
import {
  GrammarParser,
  YCCC_GRAMMAR,
  loadGrammar,
  loadGrammarFromAst,
  pretty,
  type Grammar,
  type GrammarOptions,
  type ParseResult,
} from "../src/index.js"

function expectedText(name: string): string {
  const raw = readFileSync(new URL(`../conformance/expected/${name}.txt`, import.meta.url), "utf8")
  return raw.replace(/\r\n/g, "\n").replace(/\n$/, "")
}

function optionsOf(c: CaseSpec): GrammarOptions | undefined {
  const o = c.options
  if (o === undefined) return undefined
  const options: GrammarOptions = {}
  if (o.caseSensitive !== undefined) options.caseSensitive = o.caseSensitive
  if (o.splitWord !== undefined) options.splitWord = o.splitWord
  if (o.leftRecurDetection !== undefined) options.leftRecurDetection = o.leftRecurDetection
  if (o.useMemorizedParser !== undefined) options.useMemorizedParser = o.useMemorizedParser
  if (o.locale !== undefined) options.locale = { language: o.locale.language, entries: o.locale.entries }
  return options
}

function parseAst(parser: Grammar, grammar: string) {
  const r = parser.parse(grammar)
  if (!r.ok) throw new Error(`grammar did not parse: ${r.message}`)
  return r.nodes[0]!
}

function buildParser(c: CaseSpec): Grammar {
  const grammar = c.grammar ?? ""
  switch (c.mode) {
    case "text":
      return loadGrammar(grammar, optionsOf(c))
    case "ast": {
      const first = loadGrammar(YCCC_GRAMMAR)
      const base =
        (c.astDepth ?? 1) >= 2 ? loadGrammarFromAst(parseAst(first, YCCC_GRAMMAR)) : first
      return loadGrammarFromAst(parseAst(base, grammar), optionsOf(c))
    }
    default:
      throw new Error(`mode "${c.mode}" does not build a Grammar`)
  }
}

function runParse(c: CaseSpec): ParseResult {
  if (c.mode === "grammarParser") return new GrammarParser().parse(c.input)
  const parser = buildParser(c)
  return c.parseByName !== undefined ? parser.parseBy(c.parseByName, c.input) : parser.parse(c.input)
}

function messageOf(run: () => unknown): string {
  try {
    run()
  } catch (e) {
    return (e as Error).message
  }
  throw new Error("expected the operation to fail")
}

describe("conformance (oracle: Kotlin implementation)", () => {
  for (const c of CASES) {
    it(c.name, () => {
      switch (c.kind) {
        case "parse":
          expect(pretty(runParse(c))).toBe(expectedText(c.name))
          break
        case "loadError":
          expect(messageOf(() => buildParser(c))).toBe(expectedText(c.name))
          break
        case "parseError":
          expect(messageOf(() => runParse(c))).toBe(expectedText(c.name))
          break
        default:
          throw new Error(`unknown case kind ${String(c.kind)}`)
      }
    })
  }
})
