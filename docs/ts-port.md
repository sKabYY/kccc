# Kccc —— TypeScript 移植说明

> 本文件记录 `ts/` 下 TypeScript 包与 Kotlin 版 Kccc 的对应关系、必须保持的语义
> 约束，以及一致性测试（conformance fixture）的工作方式。一般使用者请先读
> [`ts/README.md`](../ts/README.md)。

## 1. 目标与策略

TypeScript 版是 Kotlin 版 `src/main` 的 **1:1 语义移植**：给定同一份 `.grammar`
和同一段输入，两边必须产出逐字符相同的 `toSExp().toPrettyString()`、相同的失败
消息与失败位置。

为了避免“读代码照着写”带来的静默偏差，等价性由 **Kotlin 生成 fixture、TS 逐字节
比对** 保证，而不是靠人工审查：

```
src/test/kotlin/.../conformance/GenerateTsFixtures.kt
        │  gradlew generateTsFixtures
        ▼
ts/conformance/cases.ts + ts/conformance/expected/<name>.txt
ts/src/yccc-grammar.ts
        │  pnpm --dir ts test
        ▼
test/conformance.test.ts   （54 条用例，逐字符全等）
```

fixture 覆盖自举（手写解析器 / 解释器 / 两级 p2p）、JSON、SQL、定宽 lex-mode、
计算器、消息本地化、`@err`、组合子最小样例句、选项开关，以及加载期与解析期的
异常消息。

## 2. 文件映射

| Kotlin | TypeScript | 说明 |
|---|---|---|
| `common/TextPosition.kt` | `ts/src/data.ts` | `Position` + `shiftChar`；`back()` / `shift(String)` / `shiftToEnd` 未移植（`src/` 内无调用点） |
| `common/SExp.kt` | `ts/src/data.ts` | `ListSExp` / `ValueSExp`，`toPrettyString` 的 80 列与引号规则逐字符保留 |
| `common/Locale.kt` | `ts/src/data.ts` | 内置 `zh-cn` 词典原样搬入 |
| `common/ResourceUtils.kt` | — | 浏览器没有 classpath：文法由调用方直接传字符串，`yccc.grammar` 由生成器内联为 `YCCC_GRAMMAR` |
| `scanner/CharScanner.kt` | `ts/src/data.ts` | |
| `scanner/Token.kt` | `ts/src/data.ts` | `toString()` 逐字符一致（会进入 fixture） |
| `scanner/TokenStream.kt` | `ts/src/data.ts` | `equals` 改为普通方法 `equals()`；TS 不能重载 `==` |
| `parser/IParser.kt` | `ts/src/parser.ts` | `ParseFunction` 为函数类型 |
| `parser/IParseStack.kt` | `ts/src/parser.ts` | |
| `parser/ParseStack.kt` | `ts/src/parser.ts` | `ParseStack.Item` → `ParseStackItem` |
| `parser/FakeParseStack.kt` | `ts/src/parser.ts` | 单例 `FAKE_STACK` |
| `parser/ParserImpl.kt` | `ts/src/parser.ts` | 左递归检测 + `parserName` 回填 |
| `parser/MemorizedParser.kt` | `ts/src/parser.ts` | |
| `parser/ParseResult.kt` | `ts/src/tree.ts` | sealed class → 以 `ok` 判别的联合类型 |
| `parser/Node.kt` | `ts/src/tree.ts` | 保留类与方法（`digNode`/`stringValue`/`toSExp`/`toJSON`）；`match` 未移植 |
| `parser/CombinedParser.kt` | `ts/src/combined-parser.ts` | `messageLocaleString` 改用 `indexOf` 扫描，行为不变 |
| `parser/YcGrammarParser.kt` | `ts/src/grammar-parser.ts` | 节点类型常量改为具名导出 |
| `parser/YcParser.kt` | `ts/src/grammar.ts` | |
| `src/main/resources/kccc/yccc.grammar` | `ts/src/yccc-grammar.ts` | **生成文件**，`export const YCCC_GRAMMAR` |

依赖方向严格单向，避免 ESM 循环导入：

```
data → tree → parser → combined-parser → grammar-parser → grammar → index
```

## 3. 命名对照

| Kotlin | TypeScript |
|---|---|
| `YcParser` | `Grammar` |
| `YcParser.load(src)` | `loadGrammar(src, options?)` |
| `YcParser.load(grammarAst)` | `loadGrammarFromAst(ast, options?)` |
| `YcParser.Settings` | `GrammarOptions` |
| `YcGrammarParser` | `GrammarParser` |
| `IParser` / `ParseFunction` | `Parser` / `ParseFunction` |
| `TextPosition.linenum/colnum` | `Position.line/column` |
| `ParseResult.isSuccess()` | `result.ok` |
| `ParseResult.toSExp().toPrettyString()` | `pretty(result)` |
| `Node.concatValue(nodes)` | `concatValue(nodes)` |
| `Node.digNode(nodes, path)` | `digNode(nodes, ...path)` |
| `ParseException` / `NodeMethodException` / `ReachEofException` | `GrammarError` / `NodeError` / `EndOfInputError` |
| `getYcGrammarSource()` | `YCCC_GRAMMAR` 常量 |

## 4. 必须保持的语义

以下每一条都在 Kotlin 里有对应实现，改动任何一条都会改变 fixture 输出：

1. **UTF-16 码元**：一律 `charAt(i)` / `charCodeAt(i)`，禁止 `Array.from`、`[...str]`、
   `codePointAt`。代理对算两个字符，与 JVM 的 `Char` 一致。
2. **`isJavaWhitespace`**：等价于 `Character.isWhitespace`，**不含** U+00A0 /
   U+2007 / U+202F，含 U+001C–U+001F。JS 的 `\s` 与之不同；它同时决定 `space`
   内建解析器和 `ValueSExp` 是否需要加引号。
3. **`TokenStream.equals`**：scanner 引用相等、文本值相等、`Position` **引用相等**。
   `cStar` 的空匹配检测和左递归栈都依赖它，因此 TS 里是显式方法而不是结构比较。
4. **空 `Internal` 是字符串节点**：`children.every(isLeaf)` 在空数组上为真，
   `tryStringValue()` 返回 `""`，而 `toSExp()` 只输出 meta、不 push 空串。
5. **memo 只按 offset 缓存**，每次 `parse()` 开头清空；不要改成 `(offset, stack)` 键。
6. **`deeper`**：失败位置相同时保留先到的（`<=`）。
7. **`get(name)` 惰性查表**：递归文法（`add → mul → pow → primary → add`）靠这一点，
   在构建期求值会栈溢出。
8. **`messageLocaleString`**：按出现顺序收集 `start…end` 片段，按完整匹配去重，
   再逐段全局替换；不再构建正则。
9. **`toString()` 家族**：`Token`、`TokenStream`、`Parser` 的输出会进入 fixture
   （`rest <Stream: car=<Token ...>>`），逐字符保留。
10. **`\r` 不推进行号**：只有 `\n` 让 `line + 1`；`pNewline` 仍匹配 `\r\n`、`\n`、`\r`。
11. **`lowercaseChar()`**：只接受单码元的小写结果，否则退回原字符（避免 `İ` 之类
    的大小写扩展）。
12. **`@split-word`**：`isBlank` 用 Unicode 空白判断，切分却按 JVM `\s`（ASCII）
    来切——Kotlin 本身就是这个组合，移植时保留了这个不对称。
13. **加载顺序**：默认值 → 文法内 `@...` 选项 → 用户 `options` → 定义 → 根解析器。
    因此 `@include-builtin` 生成的消息不会被 `options.locale` 影响（与 Kotlin 一致）。
14. **`fatal()` 消息格式**：`message\nparser: ...\nrest: ...\nstack trace: ...`。
15. **`ParseResult` 携带 `TokenStream` 而不是 `Position`**：`toSExp()` 会把
    `<Stream: car=...>` 写进输出，若改成位置就无法与 Kotlin 对齐；取位置请用
    `result.rest.position()`。
16. **`Int` 范围**：`[N]exp` 的 N 走 `toIntOrNull` 语义（`^[+-]?\d+$` 且 32 位有符号）。

## 5. 未移植 / 差异

| 项 | 原因 |
|---|---|
| `Node.match { type -> ... }` | Kotlin lambda DSL 在 TS 里是负担；用 `switch (node.type)` 或 `digNode` |
| `TextPosition.back()` / `shift(String)` / `shiftToEnd()` | Kotlin 侧也无调用点（死代码），只保留 `shiftChar` |
| `ResourceUtils` / `loadFromResource` | 浏览器没有 classpath；调用方用 fetch / `?raw` 自行读取 |
| 例外：`cDebug` 的 `println` | 改为 `GrammarOptions.debug` 注入，默认 `console.log` |
| `System.identityHashCode` / `hashCode` | `TokenStream` 从未作为哈希键使用 |

## 6. 修改 Kotlin 语义之后

1. `gradlew.bat generateTsFixtures`（Windows）或 `./gradlew generateTsFixtures`。
2. 查看 `git diff -- ts/conformance ts/src/yccc-grammar.ts`，确认改动符合预期。
3. `pnpm --dir ts test`：TS 侧会以同样的方式失败/通过。
4. 提交 fixture 与实现（fixture 的 diff 就是这次行为变更的契约）。

新增用例只需改 `src/test/kotlin/io/github/skabyy/kccc/conformance/GenerateTsFixtures.kt`
的 `cases()`，重新生成即可；TS 测试自动遍历 `CASES`，无需改动。

建议在 CI 中把第 1 步之后的 `git diff --exit-code -- ts/conformance ts/src/yccc-grammar.ts`
作为检查项，防止 Kotlin 改了而 TS 没跟。

## 7. 已知限制

- **递归深度**：组合子递归使用 JS 调用栈，深度约 1 万帧；深层嵌套输入会抛
  `RangeError` 而不是解析失败。大输入建议放 Web Worker 或先限制嵌套深度
  （`test/depth.test.ts` 记录了 200 层可用）。
- **实例非线程安全**：与 Kotlin 一致，`MemorizedParser` 的记忆表按 offset 缓存、
  每次 `parse()` 前清空。多线程场景（如多个 Worker）请每个 Worker 各持一份实例。
- **仅 ESM**：包以 `"type": "module"` 发布，面向现代 bundler。
