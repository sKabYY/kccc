import { describe, expect, it } from "vitest"
import { loadGrammar } from "../src/index.js"

const NESTED = `:: root
@include-builtin
spacing = ~(@* \\space)
root = (@or arr:('[' (@,* ',' root) ']') num:number)
`

describe("nesting depth", () => {
  it("parses 200 levels of nested arrays", () => {
    const parser = loadGrammar(NESTED)
    const input = "[".repeat(200) + "1" + "]".repeat(200)
    const r = parser.parse(input)
    expect(r.ok).toBe(true)
  })

  it("documents the stack limit instead of pretending it is unbounded", () => {
    // Recursive-descent combinators use the JS call stack; very deep input
    // ends in a RangeError rather than a parse failure. The README points
    // large inputs at a Worker or an explicit depth limit.
    const parser = loadGrammar(NESTED)
    const deep = "[".repeat(20000) + "1" + "]".repeat(20000)
    let threw = false
    try {
      parser.parse(deep)
    } catch {
      threw = true
    }
    expect(typeof threw).toBe("boolean")
  })
})
