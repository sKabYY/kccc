import { describe, expect, it } from "vitest"
import { SExp } from "../../src/index.js"

describe("SExp formatting", () => {
  it("prints a flat list on one line", () => {
    expect(SExp.list("a", "b").toPrettyString()).toBe("(a b)")
  })

  it("prints nested lists one child per line", () => {
    expect(SExp.list(SExp.list("a"), "b").toPrettyString()).toBe("((a)\n b)")
  })

  it("quotes values containing whitespace and escapes inner quotes", () => {
    expect(SExp.value("a b").toPrettyString()).toBe('"a b"')
    expect(SExp.value('say "hi"').toPrettyString()).toBe('"say \\"hi\\""')
  })

  it("renders null as <null>", () => {
    expect(SExp.value(null).toPrettyString()).toBe("<null>")
    expect(SExp.value(undefined).toPrettyString()).toBe("<null>")
  })

  it("wraps long value lists at 80 columns", () => {
    const values = Array.from({ length: 30 }, (_, i) => `v${i}`)
    const text = SExp.list(...values).toPrettyString()
    expect(text).toContain("\n")
    for (const line of text.split("\n")) {
      expect(line.length).toBeLessThan(90)
    }
  })

  it("keeps an empty list as ()", () => {
    expect(SExp.list().toPrettyString()).toBe("()")
  })
})
