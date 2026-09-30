# Kccc (Kotlin/JVM)

**Kccc**（**K**otlin **C**ompiler **C**ompiler **C**ombinator）是一个 Parsec 风格的
解析器组合子运行时库：用 `.grammar` 文法文件（语法语言 **yccc**）描述解析器，
运行时将其编译成可执行的解析器树。它是原 C# 项目
[Nccc](https://github.com/sKabYY/Nccc) 的 **1:1 直译**移植，Kotlin 编写，**零第三方
运行时依赖**。

- **字符级 PEG**：一个 Token = 一个字符，文法直接在字符流上工作，无需单独的词法层。
- **文法即代码**：`YcParser.load("""...""")` 或 `YcParser.loadFromResource(...)`
  一行加载，返回可直接使用的解析器。
- **自举**：`kccc/yccc.grammar` 用 yccc 描述 yccc 自身语法，并通过"手写解析器 →
  一级解释 → 二级 P2P 生成"三级自举测试（三份解析结果 SExp 输出 diff 为零）。

## 快速开始

要求 **JDK 17+**，构建用仓库自带 Gradle wrapper（无需本机安装）。

```kotlin
import io.github.skabyy.kccc.parser.YcParser

// 文法即解析器：只接受"一个或多个字母，且无尾随内容"
val parser = YcParser.load("""
    :: root
    @include-builtin
    root = (@+ alpha) \eof
""")

val pr = parser.parse("hello")
if (pr.isSuccess()) {
    println(pr.nodes!!.first().toSExp().toPrettyString())
} else {
    println(pr.message)      // 失败原因
    println(pr.failRest)     // 失败位置
}
```

也可以从 classpath 资源加载（对应原版 `LoadFromAssembly`），并按需设置选项：

```kotlin
val parser = YcParser.loadFromResource("json/json.grammar") { settings ->
    settings.caseSensitive = true
    settings.splitWord = true
    settings.leftRecurDetection = true
    settings.useMemorizedParser = true
    settings.locale.language = "zh-cn"
}
```

## 使用示例：计算器

文法（`src/test/resources/calculator/calculator.grammar`）：

```yacc
:: add

@include-builtin
@lex-ignore spacing

spacing = ~(@* \space)

float = (@? #\+ #\-) (@+ digit) (@? #\. (@+ digit)) spacing

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

解析并求值（测试 `Calculator.kt` 的消费方式）：

```kotlin
val parser = YcParser.loadFromResource("calculator/calculator.grammar")

fun calc(exp: String): Double {
    val pr = parser.parse(exp)
    if (!pr.isSuccess()) throw IllegalArgumentException("parse fail: ${pr.toSExp()}")
    return valueOf(pr.nodes!!.first())
}

fun valueOf(node: Node): Double = node.match { type ->
    type("par") { es -> valueOf(es[0]) }
    type("add") { es -> valueOf(es[0]) + valueOf(es[1]) }
    type("sub") { es -> valueOf(es[0]) - valueOf(es[1]) }
    type("mul") { es -> valueOf(es[0]) * valueOf(es[1]) }
    type("div") { es -> valueOf(es[0]) / valueOf(es[1]) }
    type("pow") { es -> valueOf(es[0]).pow(valueOf(es[1])) }
    type("neg") { es -> -valueOf(es[0]) }
    type("num") { es -> Node.concatValue(es).toDouble() }
}

calc("(5.1+2)*3+-2^3^2")   // → -490.7 = 7.1×3 − 2^(3^2)
```

更多完整示例（JSON、SQL、定宽词法、错误本地化）见
[`src/test/resources/`](src/test/resources/)。

## 语法参考

- 如何编写 `.grammar` 文法文件（文件结构、表达式、节点树消费、常见陷阱）：
  [`docs/yccc-syntax.md`](docs/yccc-syntax.md)。
- yccc 最完整的语法范例：`src/main/resources/kccc/yccc.grammar`（自带注释，92 行）。

## 项目结构

```
io.github.skabyy.kccc
├── common          TextPosition / SExp / Locale / ResourceUtils
├── exceptions      ParseException / ReachEofException / NodeMethodException
├── scanner         CharScanner / Token / TokenStream
└── parser          IParser / Node / ParseResult / CombinedParser（组合子）/
                    MemorizedParser（packrat 记忆化）/ ParserImpl（左递归检测）/
                    YcGrammarParser（手写 bootstrap 文法）/ YcParser（文法解释器）
```

- 组合子：`cOr` / `cSeq` / `cStar` / `cPlus` / `cJoin` / `cNot` / `cIs` / `cGlob` /
  `cArray` 等；内建字符解析器 `pEof` / `pAlpha` / `pDigit` / `pSpace` / `pNewline` /
  `pNumber` 等。
- 加载文法：`YcParser.load(src)`、`YcParser.load(grammarAst)`、
  `YcParser.loadFromResource("path/in/classpath")`。

## 构建 / 测试

```bash
gradlew build        # 编译 + 全部测试（Windows 用 gradlew.bat build）
gradlew test         # 仅测试
```

全部测试通过，覆盖 8 组：Bootstrap（三级自举）、Calculator、Errs、Json、LexMode、
SExp、Samples、SQL。工具链选型细节见
[`docs/porting-notes.md`](docs/porting-notes.md)。

## 许可

MIT（见 [LICENSE](LICENSE)），原作者 sKabYY © 2019，JVM 移植版 © 2026。
