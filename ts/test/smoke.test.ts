import { describe, expect, it } from "vitest"
import { readFileSync, readdirSync } from "node:fs"
import { CASES } from "../conformance/cases.js"

describe("conformance fixtures", () => {
  it("ships a case table and one expected file per case", () => {
    expect(CASES.length).toBeGreaterThan(40)
    const files = readdirSync(new URL("../conformance/expected/", import.meta.url))
    for (const c of CASES) {
      expect(files, `missing expected file for ${c.name}`).toContain(`${c.name}.txt`)
    }
  })

  it("expected files are non-empty", () => {
    for (const c of CASES) {
      const text = readFileSync(new URL(`../conformance/expected/${c.name}.txt`, import.meta.url), "utf8")
      expect(text.length, c.name).toBeGreaterThan(0)
    }
  })
})
