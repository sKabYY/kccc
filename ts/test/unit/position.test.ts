import { describe, expect, it } from "vitest"
import { START_POSITION, isJavaWhitespace, lowerChar, shiftChar } from "../../src/index.js"

describe("position", () => {
  it("starts at offset 0 / line 1 / column 1", () => {
    expect(START_POSITION).toEqual({ offset: 0, line: 1, column: 1 })
  })

  it("advances the column on ordinary characters", () => {
    expect(shiftChar(START_POSITION, "a")).toEqual({ offset: 1, line: 1, column: 2 })
  })

  it("starts a new line only on \\n, like the Kotlin TextPosition", () => {
    expect(shiftChar(START_POSITION, "\n")).toEqual({ offset: 1, line: 2, column: 1 })
    // a bare \r is a newline token but does not move the line counter
    expect(shiftChar(START_POSITION, "\r")).toEqual({ offset: 1, line: 1, column: 2 })
  })
})

describe("isJavaWhitespace", () => {
  it("matches Character.isWhitespace for the common cases", () => {
    for (const ch of [" ", "\t", "\n", "\r", "\u000b", "\f", "\u001c", "\u3000", "\u2028"]) {
      expect(isJavaWhitespace(ch), JSON.stringify(ch)).toBe(true)
    }
  })

  it("excludes the non-breaking spaces that JavaScript's \\s would match", () => {
    for (const ch of ["\u00a0", "\u2007", "\u202f", "\ufeff"]) {
      expect(isJavaWhitespace(ch), JSON.stringify(ch)).toBe(false)
    }
  })
})

describe("lowerChar", () => {
  it("lowercases a single character", () => {
    expect(lowerChar("A")).toBe("a")
  })

  it("keeps characters whose lowercase form is not a single unit", () => {
    expect(lowerChar("İ")).toBe("İ")
  })
})
