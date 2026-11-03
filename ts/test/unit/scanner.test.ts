import { describe, expect, it } from "vitest"
import { CharScanner, EndOfInputError, START_POSITION, TokenStream } from "../../src/index.js"

describe("scanner", () => {
  const scanner = new CharScanner()

  it("scans one UTF-16 unit at a time", () => {
    const stream = scanner.scan("ab")
    expect(stream.car().value).toBe("a")
    expect(stream.car().start).toEqual({ offset: 0, line: 1, column: 1 })
    expect(stream.car().end).toEqual({ offset: 1, line: 1, column: 2 })
  })

  it("advances through cdr and ends with an EOF token", () => {
    const stream = scanner.scan("ab").cdr()
    expect(stream.car().value).toBe("b")
    expect(stream.isEndOfStream()).toBe(false)
    const eof = stream.cdr()
    expect(eof.isEndOfStream()).toBe(true)
    expect(() => eof.car().value).toThrow(EndOfInputError)
  })

  it("renders tokens and streams like the Kotlin toString()", () => {
    const stream = scanner.scan("a")
    expect(stream.toString()).toBe(
      "<Stream: car=<Token char='a', from=(1, 1), to=(1, 2)>>",
    )
    expect(scanner.scan("").toString()).toBe("<Stream: car=<EOF at=(1, 1)>>")
  })

  it("treats an astral character as two units, matching JVM offsets", () => {
    const stream = scanner.scan("\u{1F600}")
    expect(stream.car().value.length).toBe(1)
    expect(stream.cdr().car().end.offset).toBe(2)
  })
})

describe("TokenStream equality", () => {
  const scanner = new CharScanner()

  it("compares scanner by reference, text by value and position by reference", () => {
    const a = new TokenStream(scanner, "ab", START_POSITION)
    const b = new TokenStream(scanner, "ab", START_POSITION)
    expect(a.equals(b)).toBe(true)

    // same offset but a fresh position object: not equal, exactly like Kotlin
    const c = new TokenStream(scanner, "ab", { offset: 0, line: 1, column: 1 })
    expect(a.equals(c)).toBe(false)

    // different scanner instance
    const other = new CharScanner()
    expect(a.equals(new TokenStream(other, "ab", START_POSITION))).toBe(false)
  })
})
