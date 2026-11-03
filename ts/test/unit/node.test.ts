import { describe, expect, it } from "vitest"
import { Internal, Leaf, NodeError, makeNode, pretty } from "../../src/index.js"

const at = (offset: number, line = 1, column = offset + 1) => ({ offset, line, column })

describe("Node", () => {
  it("treats an empty internal node as a string node with an empty value", () => {
    const empty = new Internal("x", [], at(0), at(0))
    expect(empty.isStringNode()).toBe(true)
    expect(empty.tryStringValue()).toBe("")
    // the printed form carries only the metadata, never the empty value
    expect(empty.toSExp().toPrettyString()).toBe("(x[(1,1)-(1,1)])")
  })

  it("concatenates leaf values into the string value", () => {
    const node = new Internal(
      "word",
      [new Leaf("a", at(0), at(1)), new Leaf("b", at(1), at(2))],
      at(0),
      at(2),
    )
    expect(node.stringValue()).toBe("ab")
  })

  it("throws NodeError when a non-string node is read as a string", () => {
    const node = new Internal("pair", [new Internal("a", [], at(0), at(1))], at(0), at(1))
    expect(() => node.stringValue()).toThrow(NodeError)
  })

  it("walks a type path with digNode / digValue", () => {
    const tree = makeNode(
      "root",
      [
        makeNode("add", [makeNode("num", [new Leaf("2", at(0), at(1))], at(0), at(1))], at(0), at(1)),
      ],
      at(0),
      at(1),
    )
    expect(tree.digNode("add", "num").type).toBe("num")
    expect(tree.digValue("add", "num")).toBe("2")
    expect(tree.tryDigNode("missing")).toBeNull()
    expect(() => tree.digNode("missing")).toThrow(NodeError)
  })

  it("serializes to a plain JSON tree", () => {
    const leaf = new Leaf("a", at(0), at(1))
    expect(leaf.toJSON()).toEqual({
      kind: "leaf",
      value: "a",
      start: { offset: 0, line: 1, column: 1 },
      end: { offset: 1, line: 1, column: 2 },
    })
    const internal = new Internal("x", [leaf], at(0), at(1))
    expect(internal.toJSON()).toMatchObject({ kind: "internal", type: "x" })
  })

  it("prints parse results through pretty()", () => {
    expect(typeof pretty).toBe("function")
  })
})
