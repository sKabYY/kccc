/**
 * Public API of the TypeScript port of Kccc.
 *
 * The Kotlin API is mirrored where it is language-neutral; see `ts-port.md`
 * for the naming map and for the two deliberate omissions (`Node.match` and
 * the unused `TextPosition.back`/`shift(String)`/`shiftToEnd` helpers).
 */

export {
  CharScanner,
  Locale,
  START_POSITION,
  SExp,
  ListSExp,
  ValueSExp,
  Token,
  TokenStream,
  isJavaWhitespace,
  lowerChar,
  shiftChar,
} from "./data.js"
export type { Position } from "./data.js"

export {
  KcccError,
  GrammarError,
  NodeError,
  EndOfInputError,
} from "./errors.js"

export {
  Internal,
  Leaf,
  Node,
  concatValue,
  deeper,
  digNode,
  digValue,
  failResult,
  makeLeaf,
  makeNode,
  nodePosition,
  pretty,
  resultToSExp,
  success,
  failure,
  tryDigNode,
} from "./tree.js"
export type { NodeJson, ParseFailure, ParseResult, ParseSuccess } from "./tree.js"

export type { Parser, ParseStack } from "./parser.js"

export { Grammar, loadGrammar, loadGrammarFromAst, tryLoadGrammar } from "./grammar.js"
export type { GrammarOptions, LocaleInit } from "./grammar.js"

export { GrammarParser } from "./grammar-parser.js"

export { YCCC_GRAMMAR } from "./yccc-grammar.js"
