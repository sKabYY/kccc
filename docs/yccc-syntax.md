# yccc 语法参考 —— 用 yccc 定义解析文法

> 本文档面向需要在 Kccc（Kotlin/JVM）或同类运行时上编写 `.grammar` 文法文件的开发者，
> 可直接复制给其他项目使用。
>
> **yccc**（Y + ccc，与库名 Kccc 同构）是 Kccc 的语法描述语言：用一套**字符级 PEG**
> 表达式定义解析器，运行时把文法文件解析成可执行的解析器树。Kccc 是原 C# 项目
> [Nccc](https://github.com/sKabYY/Nccc) 的 1:1 移植，因此本文对 Nccc 同样适用。

---

## 目录

1. [总览](#1-总览)
2. [文法文件结构](#2-文法文件结构)
3. [书写约定（词法层）](#3-书写约定词法层)
4. [表达式参考](#4-表达式参考)
5. [PEG 语义要点](#5-peg-语义要点)
6. [节点树与结果消费](#6-节点树与结果消费)
7. [选项参考与内建解析器](#7-选项参考与内建解析器)
8. [完整示例](#8-完整示例)
9. [常见陷阱与最佳实践](#9-常见陷阱与最佳实践)
10. [附录：yccc 自举](#10-附录yccc-自举)

---

## 1. 总览

- 一份 `.grammar` 文件即一份文法（grammar），描述一个解析器。
- **字符级扫描**：输入被切分为"一个字符一个 Token"的流，文法在字符流上工作。
- 文法文件由 bootstrap 解析器 `YcGrammarParser` 解析为 AST，再由 `YcParser`
  解释成解析器树。
- **自举**：`kccc/yccc.grammar` 是用 yccc 描述 yccc 自身语法的元文法（见附录）。

### 1.1 在代码中加载与使用

```kotlin
import io.github.skabyy.kccc.parser.YcParser

// 从字符串加载
val parser = YcParser.load("""
    :: root
    @include-builtin
    root = (@+ alpha) \eof
""")

// 从 classpath 资源加载
val parser2 = YcParser.loadFromResource("calculator/calculator.grammar")

// 带设置加载
val parser3 = YcParser.loadFromResource("json/json.grammar") { settings ->
    settings.caseSensitive = true
    settings.splitWord = true
    settings.leftRecurDetection = true
    settings.useMemorizedParser = true
    settings.locale.language = "zh-cn"
}

// 解析
val pr = parser.parse("abc")
if (pr.isSuccess()) {
    // pr.nodes 为根节点列表
    println(pr.nodes!!.first().toSExp().toPrettyString())
} else {
    // 失败：pr.message / pr.failRest / pr.rest
    println(pr.message)
}
// S-表达式调试输出（成功与失败均可）
println(pr.toSExp().toPrettyString())
```

`parse(src)` 等价于 `cSeq(pSpacing(), root, pEof())`：开头自动消费空白，末尾必须到达
EOF。

### 1.2 Settings（代码内设置，与文法内 `@` 选项对应）

| 设置 | 默认 | 对应 `@` 选项 | 说明 |
| --- | --- | --- | --- |
| `caseSensitive` | `true` | `@case-sensitive` | 单词匹配是否区分大小写 |
| `splitWord` | `true` | `@split-word` | 单词是否按空白拆分 |
| `leftRecurDetection` | `true` | — | 左递归检测（关闭则用假栈） |
| `useMemorizedParser` | `true` | — | packrat 记忆化 |
| `locale` | 空 | `@set-message-locale-*` | 错误消息本地化 |

---

## 2. 文法文件结构

```
:: root名               ← 必填：root 解析器（def-root）
@选项1                  ← 可选：选项段（option-section），可多条
@选项2
名字 = 表达式           ← 可选：定义段（def-section），可多条
```

- `::` 后跟 root 解析器名字；最终生成以它为入口的解析器。
- 每条定义 `lhs = exp`：`=` 两侧空白不限，RHS 是一个或多个表达式（默认按空白
  分隔构成序列）。
- 示例（README 的最小文法）：

```
:: root

@include-builtin

root = (@+ alpha) \eof
```

---

## 3. 书写约定（词法层）

本节讲的是"怎么写 `.grammar` 文件"本身的词法：注释、空白、字符与单词的表示。

### 3.1 注释

- `;` 到行尾：行注释。
- `#| ... |#`：块注释（可跨行，遇第一个 `|#` 结束，不嵌套）。

```
; 这是行注释
#| 这是
   块注释 |#
```

### 3.2 空白（spacing）

- **默认**：自动吞并任意数量的空白字符（空格、Tab、换行等），不产生节点，等价于
  `~(@* \space)`。
- `@lex-ignore 解析器名`：把自动空白换成自定义解析器（通常是"吞空白、不产节点"的
  定义，如 `spacing = ~(@* \space)`）。
- `@lex-mode`：**完全关闭**自动空白（空白由文法自行处理；适合定宽/无空白语言）。

**关键规则**：只有"单词" token（`'...'` / `#'...'`）在匹配后会自动吞尾随空白；
**字符与组合子不吞空白**。因此字符级规则需要在末尾显式写 `spacing`（见 4 与 8）。

### 3.3 字符表示

| 写法 | 名称 | 匹配 | 是否产节点 |
| --- | --- | --- | --- |
| `\x` | glob char | 单字符 `x` | 否 |
| `#\x` | char | 单字符 `x` | 是（叶子节点） |
| `\eof` `\newline` `\space` | glob 特殊字符 | EOF / 换行 / 空白 | 否 |
| `#\eof` `#\newline` `#\space` | 特殊字符 | 同上 | 见下 |

- `x` 为任意**非空白**单字符（`\` 后跟空格不合法）。于是 `\#` 匹配字面 `#`、
  `\\` 匹配字面 `\`、`\"` 匹配 `"`、`\'` 匹配 `'`、`\|` 匹配 `|`。
- 要匹配两字符前缀 `#\`，写作 `\# \\`（元文法 `hchar = \# \\ char-iden` 即如此）。
- 特殊字符：`\space` 匹配任意空白字符（Unicode `isWhitespace`）；`\newline` 匹配
  `\r\n` / `\n` / `\r`；`\eof` 匹配输入结束（零宽判定，不消费字符，故 `#\eof`
  不产生节点）。

### 3.4 单词表示

| 写法 | 名称 | 匹配 | 是否产节点 |
| --- | --- | --- | --- |
| `'text'` | glob word | 字面单词 | 否 |
| `#'text'` | word | 字面单词 | 是（字符串节点） |

- 字符串内容为**除定界引号外**的任意字符（无转义，内部不能再出现同名引号；
  `"` 可以出现在 `'...'` 内，反之亦然）。
- `@split-word on`（默认）：`'CREATE TABLE'` 按空白拆成 `CREATE`、`TABLE` 两个词，
  逐词匹配、各自吞尾随空白。
- `@split-word off`：整串（含内部空白）作为一个整体逐字符匹配，末尾吞一次空白。
- 单词匹配受 `@case-sensitive` 控制（默认区分大小写）。

### 3.5 转义小结

在文法文件里写特殊字面量时，在前面加一个 glob char 反斜杠：

`\` → `\\`、`#` → `\#`、`"` → `\"`、`'` → `\'`、`|` → `\|`、`(` → `\(`、`)` → `\)`。

---

## 4. 表达式参考

表达式（exp）构成定义的 RHS。下表列出全部形式。

### 4.1 速查表

| 表达式 | 名称 | 语义 | 产节点 |
| --- | --- | --- | --- |
| `name` | var-exp | 引用已定义解析器 | 透传被引用者 |
| `name:exp` | named-exp | 把 exp 的节点包成类型为 `name` 的节点 | ✅ |
| `~exp` | glob-exp | 匹配 exp 但丢弃其全部节点 | 否 |
| `(@+ e1 e2 …)` | `@+` | 一个或多个序列 `(e1 e2 …)` | 合并 |
| `(@* e1 e2 …)` | `@*` | 零或多个序列 `(e1 e2 …)` | 合并 |
| `(@,+ sep e1 …)` | `@,+` | 一或多个，`sep` 分隔 | 合并 |
| `(@,* sep e1 …)` | `@,*` | 零或多个，`sep` 分隔 | 合并 |
| `(@or a b …)` | `@or` | PEG 有序选择，取首个成功分支 | 选中者 |
| `(@.. a b …)` | `@..` | 显式序列 | 合并 |
| `(@! e)` | `@!` | 否定前瞻：e 能匹配则整体失败；否则成功且不消费输入 | 否 |
| `(@? e)` | `@?` | 可选（e 匹配或空） | 匹配时 |
| `<*>` | any-exp | 匹配任意单个字符 | ✅ 叶子 |
| `[n]exp` | array-exp | exp 恰好重复 n 次 | 合并 |
| `(@err 'msg' e …)` | err-exp | 序列 `(e …)` 失败时改用 `msg` 作为错误消息 | 匹配时 |
| `?[e …]` / `??e` | dbg-exp | 调试：把解析结果 S-表达式打印到 stdout 后透传 | 透传 |
| `#\x` / `\x` / `#'w'` / `'w'` | 字符/单词 | 见 3.3 / 3.4 | 见 3.3 / 3.4 |

### 4.2 各形式详解

- **变量引用 `name`**：引用 `def-section` 或内建解析器。语法上要求后面不能直接跟
  `:`（否则会被解析为 named-exp）。
- **命名 `name:exp`**：`cIs(name, exp)`。唯一给节点附加类型名的机制，是构造节点树
  的主手段。若 exp 产生多个节点，它们都收在 `name` 节点之下。
- **丢弃 `~exp`**：`cGlob`，匹配但吞掉所有节点（常用于 spacing、纯词法 token）。
- **`@+` / `@*`**：`cPlus` / `cStar`，作用于其后的**整个序列** `(e1 e2 …)`。
  `@*` 遇到"空匹配"（不消费输入却成功）会报 `empty-star detected` 致命错误
  （死循环保护）。
- **`@,+` / `@,*`**：`cJoinPlus` / `cJoin`。第一个参数是分隔符，其后是被重复的
  整个序列：`(@,* sep e1 e2)` = `(e1 e2) (sep (e1 e2))*`。常见用法是单个元素：
  `(@,* ',' item)` = `item (',' item)*`。
- **`@or`**：PEG **有序选择**——按书写顺序尝试，取第一个成功分支；失败分支从
  分支起点完全回退，后续分支从原始位置重新开始。
- **`@..`**：与隐式序列 `(a b)` 等价（`cSeq`）。
- **`@!`**：零宽否定前瞻，常用于词边界。例如匹配单词后再检查 `(@! id-char)`。
- **`@?`**：可选。`(@? e)` = `(e)` 或空；`(@? a b)` = `(a b)` 或空（注意是整段
  序列可选，见 8.2 的注）。
- **`<*>`**：任意单字符（`pAny`），产叶子节点；输入已到 EOF 时失败（消息
  "reach eof"）。
- **`[n]exp`**：恰好 n 次（`cArray`）。`[5]char` 即精确 5 个字符。
- **`@err`**：错误消息覆盖。`(@err 'msg' e …)` 中 `(e …)` 失败时，失败消息替换为
  `msg`，可配合 `@set-message-locale-*` 做本地化。
- **调试**：`?[e …]` 与 `??e` 打印 `===[Debug]===` 前缀的 S-表达式到 stdout，结果
  原样透传（不会失败，仅用于调试）。

---

## 5. PEG 语义要点

1. **有序选择与回溯**：`@or` 按顺序尝试，首个成功分支胜出；失败分支的节点与位置
   全部回退，后续分支从原始位置重新开始。
2. **左递归检测**：默认开启 `leftRecurDetection`。同一解析器在**同一输入位置**重入
   立即报 `left-recursion detected`，防止无限递归。注意：这是"同位置重入"保护，
   **不是禁止递归文法**——只要每次递归消费了输入（位置后移）就合法。
3. **packrat 记忆化**：默认开启 `useMemorizedParser`，同一解析器在同一位置的解析
   结果被缓存。每次 `parse` 前清空缓存，**解析器实例非线程安全**，多线程请各自
   new 一个实例。
4. **空匹配死循环**：`(@* e)` 的 e 若能空匹配成功（不消费输入）即触发
   `empty-star detected`。
5. **EOF 校验**：`parse` 在 root 之后强制 `\eof`。root 未消费完输入（含尾随空白）
   即失败，因此 root 规则通常以吞空白的 token/规则收尾（见 9.3）。

### 5.1 递归与优先级示例（calculator）

```yacc
add = (@or add:(mul '+' add)
           sub:(mul '-' add)
           mul)
mul = (@or mul:(pow '*' mul)
           div:(pow '/' mul)
           pow)
pow = (@or pow:(primary '^' pow)
           primary)
```

- 每个规则先尝试"运算符"分支、失败再回退到"单元素"分支，由此构成优先级：
  `^` > 一元 `-` / 括号 > `*` / `/` > `+` / `-`。
- `pow` 的 `pow:(primary '^' pow)` 是右结合：`2^3^2` 解析为 `2^(3^2)`
  （Calculator 测试验证）。
- 递归发生在已消费输入之后的位置，不触发左递归检测。

---

## 6. 节点树与结果消费

### 6.1 节点形态

- `Node.Leaf`：单字符叶子，无类型。
- `Node.Internal`：type + children。
- **字符串节点**：children 全为 Leaf 的 Internal（type 通常为 null），
  `stringValue()` 把叶子拼回字符串。

### 6.2 哪些表达式产节点

| 表达式 | 节点 |
| --- | --- |
| `name:exp` | Internal 类型节点（type=`name`，含 exp 子节点） |
| `#'text'` | 字符串节点 |
| `#\x` / `#\newline` / `#\space` | 叶子节点（`#\eof` 不产节点，见 3.3） |
| `<*>` | 叶子节点 |
| 组合子（序列 / `@+` / `@*` / `@?` 等） | 合并子节点（展平，不包层） |
| `~` / `'…'` / `\x` / `@!` | 无 |

### 6.3 消费 API

```kotlin
val pr = parser.parse("(5.1+2)*3+-2^3^2")
val node = pr.nodes!!.first()

// 1) 结构匹配（Calculator 测试的求值方式）
val value = node.match { type ->
    type("add") { es -> valueOf(es[0]) + valueOf(es[1]) }
    type("num") { es -> Node.concatValue(es).toDouble() }
    // ...
}

// 2) 路径挖掘
val mulNode = Node.digNode(pr.nodes!!, "add", "mul")                    // 沿 type 路径取节点
val numStr  = Node.digValue(pr.nodes!!, "add", "neg", "pow", "num")     // 取字符串值
val opt     = Node.tryDigNode(pr.nodes!!, "add", "missing")             // 不存在返回 null

// 3) 字符串值
node.stringValue()          // 字符串节点 → 文本
node.tryGetStringValue()    // 非字符串节点返回 null

// 4) S-表达式调试输出
println(pr.toSExp().toPrettyString())
```

失败时：`pr.message`（最深失败点的消息）、`pr.failRest`（失败位置处的流）、
`pr.rest`（当前余流）。

---

## 7. 选项参考与内建解析器

### 7.1 选项

| 选项 | 参数 | 说明 |
| --- | --- | --- |
| `@include-builtin` | — | 载入内建解析器 `alpha` / `digit` / `number` |
| `@case-sensitive` | `on` / `off` | 单词匹配区分大小写（默认 `on`） |
| `@split-word` | `on` / `off` | 单词按空白拆分（默认 `on`） |
| `@lex-ignore` | 解析器名 | 自定义自动空白解析器（替代默认 `~(@* \space)`） |
| `@lex-mode` | — | 关闭自动空白 |
| `@set-message-locale-start` | 字符串 | 错误消息本地化定界符（如 `L{`） |
| `@set-message-locale-end` | 字符串 | 同上（如 `}`） |

### 7.2 内建解析器（`@include-builtin` 后可用）

| 名字 | 匹配 |
| --- | --- |
| `alpha` | `[a-zA-Z]` |
| `digit` | `[0-9]` |
| `number` | 可选符号 + 整数 + 可选小数 + 可选指数，末尾自动吞空白 |

---

## 8. 完整示例

以下示例均来自本仓库 `src/test/resources/`，由测试覆盖验证。

### 8.1 最小示例

```
:: root

@include-builtin

root = (@+ alpha) \eof
```

严格匹配：仅接受"全为字母且无尾随空白"的输入（`alpha` 不吞空白，root 后紧跟
`\eof`）。适合理解结构，实际使用时按 8.2 处理空白。

### 8.2 计算器（`calculator/calculator.grammar`）

```yacc
; 定义add为root parser，即最终生成的parser
:: add

; 导入内置parser
@include-builtin

; 指定词间需要过滤的空白字符
@lex-ignore spacing

;;; 词法分析

; 匹配空白字符
spacing = ~(@* \space)

; 匹配浮点数，每个词末尾需要跟上spacing来过滤空白字符。
; buitlin中已有匹配数字的parser，这里出于演示的目的另外定义了float。
float = (@? #\+ #\-) (@+ digit) (@? #\. (@+ digit)) spacing

;;; 语法分析 - 组合parser

add = (@or add:(mul '+' add)
           sub:(mul '-' add)
           mul)

mul = (@or mul:(pow '*' mul)
           div:(pow '/' mul)
           pow)

pow = (@or pow:(primary '^' pow)
           primary)

primary = (@or par:('(' add ')')
               par:('[' add ']')
               par:('{' add '}')
               neg:('-' pow)
               num:float)
```

讲解：

- `spacing = ~(@* \space)`：匹配任意空白、不产节点；`@lex-ignore spacing` 把它设为
  自动空白。
- `float`：`(@? #\+ #\-)` 可选序列、`(@+ digit)` 整数、`(@? #\. (@+ digit))` 可选
  小数、末尾显式 `spacing` 吞尾随空白（字符级规则必须手动处理空白）。
  > 注：`(@? #\+ #\-)` 是**可选序列 `+-`**（`cMaybe(cSeq('+','-'))`），并非单个
  > 正负号。真正的负号由 `primary` 的 `neg:('-' pow)` 处理；测试输入即如此解析。
- `add` / `mul` / `pow` 的 `@or` 分支顺序即优先级，递归分支后置（见 5.1）。
- `primary` 用 `par:` / `neg:` / `num:` 给节点命名，供消费方 `node.match` 使用
  （见 6.3）。

### 8.3 JSON（`json/json.grammar`）

```yacc
:: json

@include-builtin

@set-message-locale-start 'L{'
@set-message-locale-end '}'

spacing = ~(@* \space)

str = \" (@* (@! \") <*>) \" spacing

json = (@or array:array object:object value)

array = '[' (@,* ',' json) ']'

object = '{' (@,* ',' pair:(key:str ':' value:json)) '}'

value = (@err'L{invalid value type}'
             (@or string:str
                  number:number
                  bool:(@or #'true' #'false')
                  null:'null'))
```

讲解：

- `(@,* ',' json)` = `json (',' json)*`，分隔列表；`object` 用 `pair:` 给
  `key:str ':' value:json` 命名。
- `str = \" (@* (@! \") <*>) \" spacing`：`\"` 是字面双引号（glob char）；
  循环体 `(@! \") <*>` = "下一个字符不是 `"` 就消费一个字符"。
- `@err 'L{invalid value type}'`：值类型非法时报自定义消息；`L{...}` 定界符内的
  消息可用 locale 翻译（本地化用法，来自 `JsonTests`）：

```kotlin
val parser = YcParser.loadFromResource("json/json.grammar") { settings ->
    settings.locale.language = "zh-cn"
    settings.locale.set("zh-cn", mapOf("expect" to "应为"))
}
```

### 8.4 定宽词法（`lexmode/lexMode.grammar`）

```yacc
:: persons

@lex-mode

persons = (@,* nl person) (@* nl)

person = persion:(name:name age:age sex:sex)

name = [5]char
age = [3]char
sex = [1]char

char = (@! nl) <*>
nl = ~\newline
```

- `@lex-mode` 关闭自动空白，一切空白（这里只有换行）由文法自行处理。
- `[5]char` / `[3]char` / `[1]char`：定宽字段，恰好 n 个字符。
- `char = (@! nl) <*>`：任意非换行单字符；`nl = ~\newline`：行分隔。
- 样例 `sample.txt`（`XMING 77M` / `XHONG166F`）：name=5 字符、age=3 字符、
  sex=1 字符，换行分隔两条记录。

### 8.5 SQL 摘录（`sql/sql.grammar`）

```yacc
:: stms

@include-builtin
@lex-ignore spacing

spacing = ~(@* \space)

string = \' (@* (@! \') <*>) \' spacing
identifier = (@! 'CONSTRAINT') (@+ (@or #\_ alpha digit)) spacing

stms = (@+ stm ';')

create-table-stm =
  'CREATE TABLE' table '('
    table-items:(@,+ ',' create-table-item)
  ')'
```

- 多词 token：`'CREATE TABLE'` 在 `@split-word on` 下拆成两个词、逐词吞空白。
- 词边界 `(@! 'CONSTRAINT')`：`identifier` 不以 `CONSTRAINT` 开头（负向前瞻），
  避免关键字被吞成标识符。
- `(@,+ ',' item)`：一或多个、逗号分隔的列表。

---

## 9. 常见陷阱与最佳实践

1. **关键字前缀匹配**：单词 token 按前缀匹配，`'let'` 会吞掉 `letter` 的前 3 个
   字符。对策：关键字前后留空白；或先定义 `id-char = (@or alpha #\_ digit)`，在
   关键字单词后加词边界 `(@! id-char)`（即 `#'true' (@! id-char)`），确保 `truex`
   不会把 `true` 当作布尔值。
2. **`@or` 顺序**：字面量分支要排在变量分支之前——`true` 本身是合法标识符，
   `exp = (@or bool:(...true/false...) var:identifier)` 才能让 `true` 优先成布尔。
3. **字符级规则不吞空白**：`pChar` / `pDigit` 等只匹配字符。规则内用到字符序列时
   在末尾显式 `spacing`（如 8.2 的 `float`）；否则 root 收尾时可能因 EOF 检查失败。
4. **`@lex-mode` 下空白全自理**：没有自动空白，每个需要空白的点都要自己匹配。
5. **空匹配死循环**：`(@* e)` 的 e 若可空匹配（例如 e 内部套了 `(@? x)` 且能整体
   空成功）会触发 `empty-star detected`。
6. **实例非线程安全**：记忆化缓存每次 parse 前清空；并发解析请各自创建实例。
7. **字符串内不能含定界引号**：`'...'` 内不能再有 `'`，`"..."` 内不能再有 `"`；
   需要包含引号的值请换定界符或用字符级构造。
8. **递归规则的位置约束**：递归引用必须发生在已消费输入之后，否则触发左递归检测；
   优先级文法请仿照 5.1 的"先运算符分支、后元素分支"结构。
9. **root 必须消费到尾**：`parse` 强制 EOF。若 root 不消费尾随空白，带尾随空白的
   合法输入会失败。

---

## 10. 附录：yccc 自举

`kccc/yccc.grammar`（92 行）用 yccc 描述 yccc 自身的语法。自举验证
（`BootstrappingTests`）：手写解析器解析 → 解释生成解析器 → 用生成的解析器再解析
（P2P 二级生成），三份解析结果的 S-表达式输出要求 **diff 为零**。它是 yccc 最完整
的语法范例，本文第 3、4 节即归纳自该文件。

关键片段：

```yacc
:: program

@include-builtin

@case-sensitive on
@split-word on
@lex-ignore spacing

comment-start = \# \|
comment-end = \| \#
comment = (@or (\; (@* (@! \newline) <*>))
               (comment-start (@* (@! comment-end) <*>) comment-end))
spacing = ~(@* (@or \space comment))

hchar = \# \\ char-iden
char = \\ char-iden
string = \' (@* (@! \') <*>) \' spacing
hstring = \# \' (@* (@! \') <*>) \' spacing
```

- `comment-start = \# \|`：glob char 拼出字面 `#|`；`comment-end = \| \#` 拼出
  `|#`。
- `hchar = \# \\ char-iden`：glob char 拼出字面 `#\` 前缀，后接
  `char-iden`（特殊字符名或普通字符）。
- `string = \' (@* (@! \') <*>) \' spacing`：引号包裹、内容为"非引号字符"。
