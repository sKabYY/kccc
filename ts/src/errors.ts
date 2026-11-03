import type { Position } from "./data.js"

/**
 * Base class of every error thrown by this library. The Kotlin original splits
 * these into `ParseException` / `NodeMethodException` / `ReachEofException`;
 * the TypeScript port keeps the same three roles as subclasses so that callers
 * can still tell a grammar problem from a tree-manipulation problem.
 */
export class KcccError extends Error {
  constructor(message: string) {
    super(message)
    this.name = new.target.name
  }
}

/** Grammar could not be loaded (syntax error or invalid grammar construct). */
export class GrammarError extends KcccError {
  /** Position reported by the failing parse result, when one is available. */
  readonly position?: Position

  constructor(message: string, position?: Position) {
    super(message)
    if (position !== undefined) this.position = position
  }
}

/** A node was used in a way its shape does not allow. */
export class NodeError extends KcccError {
  readonly node?: unknown

  constructor(message: string, node?: unknown) {
    super(message)
    if (node !== undefined) this.node = node
  }
}

/**
 * `Token.value` was read on an end-of-input token. The Kotlin original throws
 * `ReachEofException` without a message; the empty message is kept on purpose.
 */
export class EndOfInputError extends KcccError {
  constructor() {
    super("")
  }
}
