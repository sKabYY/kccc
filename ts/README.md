# kccc (TypeScript)

**Kccc** 的 TypeScript 移植：Parsec 风格的解析器组合子运行时，加上用 `.grammar`
文件（文法语言 **yccc**）描述解析器的能力。零运行时依赖、ESM，gzip 后约 16 KB。

行为与 [Kotlin/JVM 版](../README.md) 逐字符一致：同一份文法、同一段输入，得到的
语法树、错误消息和失败位置完全相同。等价性由 Kotlin 侧生成的 conformance fixture
保证，详见 [`docs/ts-port.md`](../docs/ts-port.md)。

```ts
import { loadGrammar, pretty } from "kccc"

const json = loadGrammar(`
:: json
@include-builtin
spacing = ~(@* \\space)
str = \\" (@* (@! \\") <*>) \\" spacing
json = (@or array:array object:object value)
array = '[' (@,* ',' json) ']'
object = '{' (@,* ',' pair:(key:str ':' value:json)) '}'
value = (@or string:str number:number bool:(@or #'true' #'false') null:'null')
`)

const result = json.parse('{"a": [1, true]}')
if (result.ok) {
  console.log(pretty(result))
} else {
  console.error(result.message, result.failRest.position())
}
```

## 安装

```bash
pnpm add kccc       # 或 npm i kccc / yarn add kccc
```

需要 Node 18+（或任意现代 bundler）。本包只发 ESM。

## 文法从哪里来

浏览器里没有 classpath，文法文本由调用方提供，三种常见来源：

```ts
// 1. Vite / webpack 的 raw 导入
import grammar from "./json.grammar?raw"

// 2. 运行时 fetch
const grammar = await fetch("/grammars/json.grammar").then((r) => r.text())

// 3. 内置：yccc 自身的文法（可以用它来解析 .grammar 文件）
import { YCCC_GRAMMAR } from "kccc"
const grammarParser = loadGrammar(YCCC_GRAMMAR)
```

`.grammar` 文件本身是普通文本，可以直接当静态资源发布，也可以在构建期内联成常量。

## API

### 加载文法

```ts
import { loadGrammar, tryLoadGrammar, loadGrammarFromAst, GrammarError } from "kccc"

const parser = loadGrammar(source)              // 失败时抛 GrammarError
const maybe = tryLoadGrammar(source)            // { ok: true, grammar } | { ok: false, error }
if (!maybe.ok) console.error(maybe.error.message, maybe.error.position)

const fromAst = loadGrammarFromAst(grammarParser.parse(source).nodes[0])
```

选项（与 Kotlin `Settings` 对应）：

| 选项 | 默认 | 说明 |
|---|---|---|
| `caseSensitive` | `true` | 词法比较是否区分大小写 |
| `splitWord` | `true` | 关键字按空白切成多个词 |
| `leftRecurDetection` | `true` | 打开左递归检测（关闭可省去每帧 O(depth) 的扫栈） |
| `useMemorizedParser` | `true` | 打开 packrat 记忆化 |
| `locale` | — | `{ language: "zh-cn" }`，可附 `entries` 自定义词条 |
| `debug` | `console.log` | `?[...]` / `??[...]` 调试构造的输出 |

> 选项在文法内 `@...` 语句之后生效，因此会覆盖 `@case-sensitive` 之类的设置。
> 注意 `@include-builtin` 在选项之前执行，所以内置解析器的消息不受 `locale` 影响
> ——这一点与 Kotlin 版一致。

### 解析

```ts
const r = parser.parse(text)
if (r.ok) {
  r.nodes            // Node[]，根规则产出的节点
  r.rest.position()  // 结束位置 { offset, line, column }
} else {
  r.message          // 已按 locale 翻译的失败消息
  r.failRest.position()  // 最深的失败位置
}
```

`parseBy(name, text)` 从指定规则开始解析。

### 消费节点树

```ts
const node = r.nodes[0]

node.type                 // "add" | "num" | null（Leaf）
node.stringValue()        // 字符串节点的值，失败抛 NodeError
node.digNode("add", "mul")        // 按类型路径查找
node.digValue("add", "neg", "pow", "num")
node.toJSON()             // { kind: "internal", type, start, end, children }
node.toSExp().toPrettyString()    // 调试用，与 Kotlin 输出一致
```

`match` 没有移植：TS 里直接 `switch (node.type)` 更自然。

## 开发

```bash
pnpm install
pnpm run typecheck
pnpm test
pnpm run build        # 输出 dist/（ESM + .d.ts）
```

### 一致性 fixture

`conformance/cases.ts`、`conformance/expected/*.txt` 和 `src/yccc-grammar.ts` 都由
Kotlin 侧生成，**不要手改**：

```bash
# 仓库根目录
gradlew.bat generateTsFixtures     # Windows
./gradlew generateTsFixtures       # macOS / Linux
```

改了 Kotlin 行为之后重新生成并提交，`git diff` 就是这次语义变更的契约。新增用例
只需修改 `src/test/kotlin/io/github/skabyy/kccc/conformance/GenerateTsFixtures.kt`。

## 已知限制

- **递归深度**：组合子递归用 JS 调用栈（约 1 万帧）。深层嵌套输入会抛 `RangeError`，
  不是解析失败；大输入建议放 Worker 或先限制嵌套深度。
- **实例非线程安全**：每次 `parse()` 前会清空记忆表；并发场景请每个 Worker 一个实例。
- **仅 ESM**，不提供 CJS 产物。

## 许可

MIT。
