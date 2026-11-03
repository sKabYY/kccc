/**
 * Parse tree and parse results. Mirrors `parser/Node.kt` and
 * `parser/ParseResult.kt`.
 *
 * `ParseResult` is a discriminated union on `ok` instead of Kotlin's sealed
 * class; `Node` stays class-based because consumers call methods on it
 * (`digNode`, `stringValue`, `toSExp`).
 */

import { SExp, ListSExp, type Position, type Token, type TokenStream } from "./data.js"
import { NodeError } from "./errors.js"

/** `row N column M` for a node, `unknown position` when it has none. */
export function nodePosition(node: Node): string {
  return node.start === null ? "unknown position" : `row ${node.start.line} column ${node.start.column}`
}

export type NodeJson =
  | { kind: "leaf"; value: string; start: Position; end: Position }
  | {
      kind: "internal"
      type: string | null
      start: Position | null
      end: Position | null
      children: NodeJson[]
    }

export abstract class Node {
  abstract readonly type: string | null
  abstract readonly start: Position | null
  abstract readonly end: Position | null

  isLeaf(): this is Leaf {
    return this instanceof Leaf
  }

  isStringNode(): boolean {
    if (!(this instanceof Internal)) return false
    return this.children.every((c) => c instanceof Leaf)
  }

  /**
   * String value of a string node, or null when this is not one.
   * Note that an `Internal` with no children counts as a string node whose
   * value is "" (Kotlin's `all {}` is true on an empty list).
   */
  tryStringValue(): string | null {
    if (!(this instanceof Internal)) return null
    const children = this.children
    if (children.every((c) => c instanceof Leaf)) {
      return children.map((c) => (c as Leaf).value).join("")
    }
    return null
  }

  stringValue(): string {
    const value = this.tryStringValue()
    if (value !== null) return value
    throw new NodeError(
      `can't get StringValue of node ${this.toSExp().toPrettyString()} at ${nodePosition(this)}`,
      this,
    )
  }

  toSExp(): SExp {
    if (this instanceof Leaf) {
      return SExp.value(this.value)
    }
    const internal = this as unknown as Internal
    const strValue = internal.tryStringValue()
    if (strValue !== null) {
      const lst = SExp.list(internal.getNodeMeta())
      if (strValue.length > 0) lst.push(strValue)
      return lst
    }
    const lst = SExp.list(...internal.children.map((c) => c.toSExp()))
    lst.pushFront(internal.getNodeMeta())
    return lst
  }

  toJSON(): NodeJson {
    if (this instanceof Leaf) {
      return { kind: "leaf", value: this.value, start: this.start, end: this.end }
    }
    const internal = this as unknown as Internal
    return {
      kind: "internal",
      type: internal.type,
      start: internal.start,
      end: internal.end,
      children: internal.children.map((c) => c.toJSON()),
    }
  }

  toString(): string {
    return this.toSExp().toPrettyString()
  }

  /**
   * Structural lookup used by the `dig*` helpers. Mirrors the Kotlin
   * `digSomething`: at every level exactly one child must carry the type.
   */
  private digSomething<T>(
    foundOne: (node: Node) => T,
    notFound: (type: string, node: Node) => T,
    foundMore: (type: string, node: Node) => T,
    path: readonly string[],
  ): T {
    let node: Node = this
    for (let i = 0; i < path.length; i++) {
      const wanted = path[i]!
      const children = node instanceof Internal ? node.children : null
      if (children === null) return notFound(wanted, node)
      const found = children.filter((c) => c.type === wanted)
      if (found.length === 0) return notFound(wanted, node)
      if (found.length === 1) {
        node = found[0]!
      } else {
        return foundMore(wanted, node)
      }
    }
    return foundOne(node)
  }

  digNode(...path: string[]): Node {
    return this.digSomething(
      (node) => node,
      (type, node) => {
        throw new NodeError(
          `type "${type}" not found in $${node.toSExp().toPrettyString()}`,
          this,
        )
      },
      (type, node) => {
        throw new NodeError(
          `two or more "${type}" found in $${node.toSExp().toPrettyString()}`,
          this,
        )
      },
      path,
    )
  }

  tryDigNode(...path: string[]): Node | null {
    return this.digSomething(
      (node) => node,
      () => null,
      () => null,
      path,
    )
  }

  digNodeOrNull(...path: string[]): Node | null {
    return this.tryDigNode(...path)
  }

  digValue(...path: string[]): string {
    return this.digNode(...path).stringValue()
  }

  digValueOrNull(...path: string[]): string | null {
    return this.tryDigNode(...path)?.stringValue() ?? null
  }

  private getNodeMeta(): string {
    return `${this.type}[(${this.start?.line},${this.start?.column})-(${this.end?.line},${this.end?.column})]`
  }
}

export class Leaf extends Node {
  readonly value: string
  override readonly start: Position
  override readonly end: Position
  override readonly type: string | null = null

  constructor(value: string, start: Position, end: Position) {
    super()
    this.value = value
    this.start = start
    this.end = end
  }
}

export class Internal extends Node {
  override readonly type: string | null
  readonly children: readonly Node[]
  override readonly start: Position | null
  override readonly end: Position | null

  constructor(
    type: string | null,
    children: readonly Node[] | null,
    start: Position | null,
    end: Position | null,
  ) {
    super()
    this.type = type
    this.children = children ?? []
    this.start = start
    this.end = end
  }
}

export function makeLeaf(tok: Token): Leaf {
  return new Leaf(tok.value, tok.start, tok.end)
}

export function makeNode(
  type: string | null,
  children: readonly Node[] | null,
  start: Position | null,
  end: Position | null,
): Internal {
  return new Internal(type, children, start, end)
}

/** `Node.concatValue`: concatenates a list that must consist of leaves only. */
export function concatValue(nodes: readonly Node[]): string {
  const bad = nodes.find((n) => !(n instanceof Leaf))
  if (bad !== undefined) {
    throw new NodeError(
      `expect a leaf but got ${bad.toSExp().toPrettyString()} at ${nodePosition(bad)}`,
      bad,
    )
  }
  return nodes.map((n) => (n as Leaf).value).join("")
}

/** `Node.digNode(nodes, path)`: looks a type path up in a flat node list. */
export function digNode(nodes: readonly Node[], ...path: string[]): Node {
  return new Internal(null, nodes, null, null).digNode(...path)
}

export function tryDigNode(nodes: readonly Node[], ...path: string[]): Node | null {
  return new Internal(null, nodes, null, null).tryDigNode(...path)
}

export function digValue(nodes: readonly Node[], ...path: string[]): string {
  return new Internal(null, nodes, null, null).digValue(...path)
}

// ---------------------------------------------------------------------------
// Parse results
// ---------------------------------------------------------------------------

export interface ParseSuccess {
  readonly ok: true
  nodes: Node[]
  readonly rest: TokenStream
  readonly failRest: TokenStream
  readonly message: string | null
  readonly start: Position | null
  readonly end: Position | null
  parserName: string | null
}

export interface ParseFailure {
  readonly ok: false
  readonly nodes: Node[] | null
  readonly rest: TokenStream
  readonly failRest: TokenStream
  readonly message: string
  readonly start: Position | null
  readonly end: Position | null
  parserName: string | null
}

export type ParseResult = ParseSuccess | ParseFailure

class ParseSuccessImpl implements ParseSuccess {
  readonly ok = true as const
  nodes: Node[]
  readonly rest: TokenStream
  readonly failRest: TokenStream
  readonly message: string | null
  readonly start: Position | null
  readonly end: Position | null
  parserName: string | null = null

  constructor(
    nodes: Node[],
    rest: TokenStream,
    message: string | null,
    failRest: TokenStream,
    start: Position | null,
    end: Position | null,
  ) {
    this.nodes = nodes
    this.rest = rest
    this.message = message
    this.failRest = failRest
    this.start = start
    this.end = end
  }
}

class ParseFailureImpl implements ParseFailure {
  readonly ok = false as const
  readonly nodes: Node[] | null
  readonly rest: TokenStream
  readonly failRest: TokenStream
  readonly message: string
  readonly start: Position | null
  readonly end: Position | null
  parserName: string | null = null

  constructor(
    nodes: Node[] | null,
    rest: TokenStream,
    message: string,
    failRest: TokenStream,
    start: Position | null,
    end: Position | null,
  ) {
    this.nodes = nodes
    this.rest = rest
    this.message = message
    this.failRest = failRest
    this.start = start
    this.end = end
  }
}

export function success(
  nodes: Node[],
  rest: TokenStream,
  message: string | null,
  failRest: TokenStream,
  start: Position | null,
  end: Position | null,
): ParseSuccess {
  return new ParseSuccessImpl(nodes, rest, message, failRest, start, end)
}

export function failure(
  nodes: Node[] | null,
  rest: TokenStream,
  message: string,
  failRest: TokenStream,
  start: Position | null = null,
  end: Position | null = null,
): ParseFailure {
  return new ParseFailureImpl(nodes, rest, message, failRest, start, end)
}

/** Keeps the result whose failure is furthest into the input (ties keep the left one). */
export function deeper(r: ParseResult, other: ParseResult | null): ParseResult {
  if (other === null) return r
  return other.failRest.position().offset <= r.failRest.position().offset ? r : other
}

/** Turns a success into the equivalent failure (Kotlin `failResult`). */
export function failResult(r: ParseResult): ParseFailure {
  if (!r.ok) return r
  return failure(r.nodes, r.rest, r.message ?? "parse failed", r.failRest, r.start, r.end)
}

function nodesSExp(r: ParseResult): ListSExp | null {
  return r.nodes === null ? null : SExp.list(...r.nodes.map((n) => n.toSExp()))
}

export function resultToSExp(r: ParseResult): ListSExp {
  const list = SExp.list(SExp.list("success?", r.ok))
  if (r.ok) {
    list.push(
      SExp.list("nodes", nodesSExp(r)),
      SExp.list("rest", r.rest),
      SExp.list("message", r.message),
      SExp.list("fail_rest", r.failRest),
    )
  } else {
    list.push(
      SExp.list("message", r.message),
      SExp.list("fail_rest", r.failRest),
      SExp.list("rest", r.rest),
      SExp.list("nodes", nodesSExp(r)),
    )
  }
  if (r.parserName !== null && r.parserName.length > 0) {
    list.pushFront(SExp.list(SExp.list("parser", r.parserName)))
  }
  return list
}

/** Pretty-printed parse result, byte-identical to Kotlin's `toString()`. */
export function pretty(r: ParseResult): string {
  return resultToSExp(r).toPrettyString()
}
