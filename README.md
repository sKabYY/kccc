# Kccc (Kotlin/JVM)

Kccc（**K**otlin **C**ompiler **C**ompiler **C**ombinator；原名 Nccc = **.Net**
Compiler Compiler Combinator，随平台更名）的 JVM 移植版：一个 Parsec 风格的解析器
组合子运行时库。本仓库是原 C# 项目
[Nccc](https://github.com/sKabYY/Nccc)（.NET 版 2.0.3）的 **1:1 直译**移植，使用
Kotlin 编写，零第三方运行时依赖。

- 原理：字符级扫描（一个 Token = 一个字符），`.grammar` 语法文件（语言名 yccc）由
  手写的 `YcGrammarParser`（bootstrap 解析器）解析为 AST，再由 `YcParser` 解释成
  解析器树。
- 自举：`kccc/yccc.grammar`（资源文件）描述了语法文件本身的语法，因此 Kccc 可以
  解析自己的语法描述（见 `BootstrappingTests`：手写解析器 → 一级解释 → 二级 P2P
  生成，三份解析结果 SExp 输出要求 **diff 为零**）。

## 版本

**2.1.0** = .NET 版 2.0.3 语义 + 下列修正（见「与原版的差异」）。

## 构建 / 测试

- JDK 17+，Gradle 9.8.0（仓库自带 wrapper，无需本机安装）。
- `gradlew.bat build`（Windows）／`./gradlew build`（*nix）。

工具链说明：Kotlin 插件取 2.1.20，与 IntelliJ IDEA 2025.1 内置的 Kotlin 插件版本
一致（保证 `build.gradle.kts` 的 IDE 脚本模型可用）；Gradle 9.8.0 略新于该版
IDEA 的官方支持列表，Tooling API 兼容，导入时的 "untested Gradle version"
提示属正常。不降到 Gradle 8.x 的原因：8.x 的构建逻辑插桩 transform 在部分
Windows 环境存在
[gradle/gradle#31392](https://github.com/gradle/gradle/issues/31392)
（"Could not move temporary workspace to immutable location"）问题，本机实测
8.13/8.14.3 均可复现。

```
gradlew build        # 编译 + 全部测试
gradlew test         # 仅测试
```

全部 29 个测试通过，覆盖原仓库全部 8 组测试（Bootstrap 三级自举、Calculator、
Errs（错误消息本地化）、Json、LexMode（定宽词法模式）、Samples、SExp、SQL）。

## 结构

```
io.github.skabyy.kccc
├── common          TextPosition / SExp / Locale / ResourceUtils
├── exceptions      ParseException / ReachEofException / NodeMethodException
├── scanner         CharScanner / Token / TokenStream
└── parser          IParser / ParseFunction / ParseStack / FakeParseStack /
                    MemorizedParser（packrat 记忆化）/ ParserImpl（左递归检测）/
                    ParseResult / Node / CombinedParser（P*/C* 组合子）/
                    YcGrammarParser（手写 bootstrap 文法）/ YcParser（文法解释器）
```

- `CombinedParser`：内建字符解析器（`pEof`/`pAlpha`/`pDigit`/`pSpace`/`pNewline`/
  `pNumber`…）、token 解析器（`pEq`/`pEqAndRetain`…）与组合子
  （`cOr`/`cSeq`/`cStar`/`cPlus`/`cJoin`/`cNot`/`cIs`/`cGlob`/`cArray`…）。
- `YcParser.Settings`：`locale`、`caseSensitive`、`splitWord`、
  `leftRecurDetection`、`useMemorizedParser`。
- 加载文法：`YcParser.load(grammarSource) { settings -> ... }` 或
  `YcParser.loadFromResource("path/in/classpath")`（对应原版的
  `LoadFromAssembly`）。

## 语法文件示例

```
:: root

@include-builtin

root = (@+ alpha) \eof
```

详细语法见 `src/main/resources/kccc/yccc.grammar` 自身（92 行，带注释），以及
`src/test/resources/` 下的 calculator / json / lexMode / sql 文法与样例。

## 与原版的差异（除语言平台外，仅以下几处）

1. **`TextPosition.back()` 补上 throw**：原 C# 代码构造了
   `InvalidOperationException` 但忘了 `throw`（`new InvalidOperationException(...)`
   语句无效果）。移植版恢复本意：`offset == 0` 时抛出 `IllegalStateException`。
2. **拼写修正 `grammer` → `grammar`**：原版的 `grammer` 拼写错误贯穿扩展名与
   标识符——文件扩展名 `.grammer`、类名 `NcGrammerParser`、方法
   `GetNcGrammerSource`——移植版全部修正为 `.grammar`、`YcGrammarParser`、
   `getYcGrammarSource`。
3. **`sample.json` 为移植版补写**：原仓库 `Nccc.Tests.csproj` 声明了内嵌资源
   `Nccc.Tests.Json.sample.json`，但该文件从未被提交，原 `JsonTests.TestSample`
   在 .NET 上也无法通过。移植版按 json.grammar 编写了一份覆盖全部产生式的
   样例（对象/数组/字符串/数字/布尔/null/嵌套/空容器）。
4. **`YcParser.Settings` 属性**：原版为只写属性（write-only）；Kotlin 无法表达
   公开 var + 私有 getter，改为可读写（行为超集，不影响测试）。
5. **`TokenStream.hashCode()`**：原版把 `_cur`（Token 引用）混入哈希，与其自定义
   `Equals`（scanner 引用 + 字符串值 + 位置引用）不一致；移植版改用稳定 offset。
   该类型从未作为哈希键使用，无行为影响。
6. **`MessageLocaleString` 的正则**：原版直接拼接
   `$"{Start}([^{End}])*{End}"`；`java.util.regex` 不允许裸 `{`（无法构成合法
   量词时直接抛 `PatternSyntaxException`，.NET 则回退为字面量），移植版对定界符
   使用 `Pattern.quote`，语义等价。
7. **`Locale.set` 复制入参 Map**：原版保留调用方字典引用；移植版拷贝一份
   （避免外部不可变 Map 混入可变存储的矛盾）。
8. **测试 diff 工具**：原版测试用 DiffPlex 做行内 diff；移植版以零依赖的 LCS
   行 diff 替代（控制台着色简化为 `+ `/`- ` 前缀），断言语义不变。
9. **线程安全**：与原版一致，`MemorizedParser` 的记忆表按偏移缓存、不区分解析
   栈，**解析器实例非线程安全**（每次 parse 前会清空缓存）。
10. **项目改名 Nccc → Kccc**：原名的 N 指 .Net，随平台更名；当时类名
    `NcParser`/`NcGrammarParser` 暂予保留（见第 11 条）；引导文法资源路径由
    `nccc/nccc.grammar` 改为 `kccc/kccc.grammar`（文法文件格式与内容零改动）。
11. **类名 `Nc*` → `Yc*`，语法语言命名 `yccc`**：类名前缀的 N 随 .Net，且
    "nc 语法"之名易被误读为绑定技术栈；改为作者标识前缀 **Yc**（Y = sKabYY），
    语法语言正式命名 **yccc**（Y + ccc，与库名 Kccc 同构）——技术栈中立，
    `.grammar` 为可移植格式。类名 `NcParser`/`NcGrammarParser` →
    `YcParser`/`YcGrammarParser`；meta 文法资源 `kccc/kccc.grammar` →
    `kccc/yccc.grammar`（文法文件格式与内容零改动）。
12. **错误消息拼写修正 `colum`/`unkown` → `column`/`unknown`**：原版错误消息
    里的两个错别字（"colum"、"unkown op"），移植版随错误消息统一格式化顺带
    修正（测试无断言）。

其余实现——包括引用相等语义（`TokenStream.Equals` 中 Position 按引用比较、
`ParseStack.Has` 中 parser 按引用比较、`CStar` 空匹配检测）——均按原版直译保留。

## 许可

MIT（见 [LICENSE](LICENSE)），原作者 sKabYY © 2019，JVM 移植版 © 2025。
