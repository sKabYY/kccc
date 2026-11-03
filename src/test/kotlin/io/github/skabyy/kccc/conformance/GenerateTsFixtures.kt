package io.github.skabyy.kccc.conformance

import io.github.skabyy.kccc.common.ResourceUtils
import io.github.skabyy.kccc.parser.YcGrammarParser
import io.github.skabyy.kccc.parser.YcParser
import java.io.File

/**
 * Generates the TypeScript conformance fixtures under `ts/`:
 *
 *  - `ts/conformance/cases.ts`      — the case table (grammar/input/options)
 *  - `ts/conformance/expected/<name>.txt` — one expected output per case
 *  - `ts/src/yccc-grammar.ts`       — the bootstrap grammar as a TS constant
 *
 * Run with `gradlew generateTsFixtures`. The generated files are committed so
 * that every Kotlin change that alters observable behavior shows up as a diff
 * in the TypeScript package too (see `ts/README.md`).
 */
private const val ROOT_PREFIX = """::root
@include-builtin
spacing = ~(@* \space)
"""

private data class LocaleSpec(val language: String, val entries: Map<String, String>? = null)

private data class Options(
    val caseSensitive: Boolean? = null,
    val splitWord: Boolean? = null,
    val leftRecurDetection: Boolean? = null,
    val useMemorizedParser: Boolean? = null,
    val locale: LocaleSpec? = null,
)

private data class Case(
    val name: String,
    val kind: String,
    val mode: String,
    val astDepth: Int = 1,
    val grammar: String = "",
    val parseByName: String? = null,
    val input: String,
    val options: Options? = null,
)

private fun sample(
    name: String,
    body: String,
    input: String,
    parseByName: String? = null,
): Case = Case(
    name = "samples-$name",
    kind = "parse",
    mode = "text",
    grammar = ROOT_PREFIX + body,
    parseByName = parseByName,
    input = input,
)

private fun cases(): List<Case> {
    val yccc = YcGrammarParser.getYcGrammarSource()
    val jsonGrammar = ResourceUtils.readString("json/json.grammar")
    val sqlGrammar = ResourceUtils.readString("sql/sql.grammar")
    val calculatorGrammar = ResourceUtils.readString("calculator/calculator.grammar")
    val lexModeGrammar = ResourceUtils.readString("lexmode/lexMode.grammar")
    val jsonSample = ResourceUtils.readString("json/sample.json")
    val sqlSample = ResourceUtils.readString("sql/sample.sql")
    val sqlSampleErr = ResourceUtils.readString("sql/sample-err.sql")
    val lexModeSample = ResourceUtils.readString("lexmode/sample.txt")

    val errGrammar = """
:: root

@set-message-locale-start 'L{'
@set-message-locale-end '}'

root = (@err'L{expect} A L{or} B' oo:(@or 'A' 'B'))
"""

    val sexpGrammar = """
:: sexps

@include-builtin

sexps = (@+ sexp)
sexp = (@or o:(open sexps close) i:item)

open = (@or '[' '(')
close = (@or ']' ')')
item = (@+ (@! (@or open close \space)) <*>) spacing

spacing = ~(@* \space)
"""

    val sexpCode = """
(define (double x) (+ x x))
(define (gcd a b) (if (= a 0) b (gcd (remainder b a) a)))
"""

    val parseByGrammar = """
root = header footer
header = (@+ num:number)
footer = (@+ var:(@+ alpha) spacing)
"""

    val list = mutableListOf<Case>()

    // ---- bootstrap: hand-written parser vs interpreter vs two p2p levels ----
    list += Case("bootstrap-yccc-handwritten", "parse", "grammarParser", input = yccc)
    list += Case("bootstrap-yccc-interpreted", "parse", "text", grammar = yccc, input = yccc)
    list += Case("bootstrap-yccc-p2p1", "parse", "ast", astDepth = 1, grammar = yccc, input = yccc)
    list += Case("bootstrap-yccc-p2p2", "parse", "ast", astDepth = 2, grammar = yccc, input = yccc)

    val badGrammarSrc = "::dummy @hehe '123'"
    list += Case("bootstrap-err-handwritten", "parse", "grammarParser", input = badGrammarSrc)
    list += Case("bootstrap-err-interpreted", "parse", "text", grammar = yccc, input = badGrammarSrc)
    list += Case("bootstrap-err-p2p1", "parse", "ast", astDepth = 1, grammar = yccc, input = badGrammarSrc)

    // ---- json ----
    list += Case("json-sample", "parse", "text", grammar = jsonGrammar, input = jsonSample)
    list += Case("json-sample-p2p1", "parse", "ast", astDepth = 1, grammar = jsonGrammar, input = jsonSample)
    list += Case("json-sample-p2p2", "parse", "ast", astDepth = 2, grammar = jsonGrammar, input = jsonSample)
    list += Case(
        "json-error-zh", "parse", "text", grammar = jsonGrammar, input = "[1,2,3",
        options = Options(locale = LocaleSpec("zh-cn", mapOf("expect" to "哈哈"))),
    )

    // ---- sql (case-insensitive) ----
    val sqlOptions = Options(caseSensitive = false)
    list += Case("sql-sample", "parse", "text", grammar = sqlGrammar, input = sqlSample, options = sqlOptions)
    list += Case("sql-error", "parse", "text", grammar = sqlGrammar, input = sqlSampleErr, options = sqlOptions)

    // ---- lex mode ----
    list += Case("lexmode-sample", "parse", "text", grammar = lexModeGrammar, input = lexModeSample)
    list += Case("lexmode-sample-p2p1", "parse", "ast", astDepth = 1, grammar = lexModeGrammar, input = lexModeSample)

    // ---- calculator ----
    list += Case("calculator-parse", "parse", "text", grammar = calculatorGrammar, input = "(5.1+2)*3+-2^3^2")

    // ---- message localization / @err ----
    list += Case(
        "errs-message-locale", "parse", "text", grammar = errGrammar, input = "C",
        options = Options(
            locale = LocaleSpec("zh-cn", mapOf("expect" to "盼望着", "or" to "或")),
        ),
    )
    list += Case(
        "errs-c-if-fail", "parse", "text",
        grammar = """::root
@include-builtin
root = (@+ alpha) \eof
""",
        input = "a1",
    )

    // ---- s-expression sample ----
    list += Case("sexp-grammar-handwritten", "parse", "grammarParser", input = sexpGrammar)
    list += Case("sexp-parse", "parse", "text", grammar = sexpGrammar, input = sexpCode)

    // ---- minimal combinator samples (mirror SamplesTests) ----
    list += sample("string", "root = 'A'\n", "A")
    list += sample("seq-op", "root = (@.. 'A' 'B')\n", "A B")
    list += sample("seq-group", "root = ('A' 'B')\n", "A B")
    list += sample("seq-plain", "root = 'A' 'B'\n", "A B")
    list += sample("or-a", "root = (@or 'A' 'B')\n", "A")
    list += sample("or-b", "root = (@or 'A' 'B')\n", "B")
    list += sample("maybe-empty", "root = (@? 'A')\n", "")
    list += sample("maybe-a", "root = (@? 'A')\n", "A")
    list += sample("star-empty", "root = (@* 'A')\n", "")
    list += sample("star-four", "root = (@* 'A')\n", "A A A A")
    val starOr = "root = (@* (@or 'A' 'B'))\n"
    list += sample("star-or-bb", starOr, "B B")
    list += sample("star-or-aa", starOr, "A A")
    list += sample("star-or-ab", starOr, "A B")
    list += sample("star-or-aba", starOr, "A B A A")
    list += sample("number", "root = number\n", "1.1")
    val numberOr = "root = (@or number 'A')\n"
    list += sample("number-or-number", numberOr, "1.1")
    list += sample("number-or-a", numberOr, "A")
    val starNumberOr = "root = (@* (@or number #'A'))\n"
    list += sample("star-number-or-1", starNumberOr, "1.1")
    list += sample("star-number-or-a", starNumberOr, "A")
    list += sample("star-number-or-aa", starNumberOr, "A A")
    list += sample("star-number-or-mixed", starNumberOr, "1.1 2")
    list += sample("star-number-or-a23", starNumberOr, "A 23")
    list += sample("star-number-or-multi", starNumberOr, "A 1.2 A A")
    list += sample("parseby-root", parseByGrammar, "1.1 1.2 abc ef")
    list += sample("parseby-header", parseByGrammar, "1.1 1.2", parseByName = "header")
    list += sample("parseby-footer", parseByGrammar, "abc ef", parseByName = "footer")
    list += sample("default-spacing-globbed", parseByGrammar, "1.1 1.2 abc ef")
    list += sample("eof-fail", "root = (@+ num:number)\n", "1.1 1.2 abc ef")

    // ---- option flags ----
    list += Case(
        "flags-left-recur-off", "parse", "text", grammar = calculatorGrammar,
        input = "(5.1+2)*3+-2^3^2", options = Options(leftRecurDetection = false),
    )
    list += Case(
        "flags-no-memo", "parse", "text", grammar = jsonGrammar,
        input = jsonSample, options = Options(useMemorizedParser = false),
    )

    // ---- load-time failures: lock the exception message and position ----
    list += Case(
        "loaderr-unknown-op", "loadError", "text",
        grammar = """:: root
@include-builtin
root = (@bogus 'a')
""",
        input = "",
    )
    list += Case(
        "loaderr-array-size", "loadError", "text",
        grammar = """:: root
root = [99999999999] 'a'
""",
        input = "",
    )
    list += Case(
        "loaderr-syntax", "loadError", "text",
        grammar = """:: root
@include-builtin
root = (@+ alpha 12345
""",
        input = "",
    )
    // ---- parse-time failures: undefined grammar name is resolved lazily ----
    list += Case(
        "parseerr-undefined-var", "parseError", "text",
        grammar = """:: root
root = foo
""",
        input = "x",
    )

    return list
}

private fun applyOptions(settings: YcParser.Settings, options: Options?) {
    if (options == null) return
    options.caseSensitive?.let { settings.caseSensitive = it }
    options.splitWord?.let { settings.splitWord = it }
    options.leftRecurDetection?.let { settings.leftRecurDetection = it }
    options.useMemorizedParser?.let { settings.useMemorizedParser = it }
    options.locale?.let { spec ->
        settings.locale.language = spec.language
        spec.entries?.let { settings.locale.set(spec.language, it) }
    }
}

private fun buildParser(c: Case): YcParser {
    return when (c.mode) {
        "grammarParser" -> throw IllegalStateException("grammarParser mode has no parser")
        "text" -> YcParser.load(c.grammar) { settings -> applyOptions(settings, c.options) }
        "ast" -> {
            val yccc = YcGrammarParser.getYcGrammarSource()
            val p1 = YcParser.load(yccc)
            val base = if (c.astDepth >= 2) {
                YcParser.load(p1.parse(yccc).nodes!!.first())
            } else {
                p1
            }
            val ast = base.parse(c.grammar).nodes!!.first()
            YcParser.load(ast) { settings -> applyOptions(settings, c.options) }
        }
        else -> throw IllegalStateException("unknown mode ${c.mode}")
    }
}

private fun runParse(c: Case): io.github.skabyy.kccc.parser.ParseResult {
    return if (c.mode == "grammarParser") {
        YcGrammarParser().parse(c.input)
    } else {
        val parser = buildParser(c)
        if (c.parseByName != null) parser.parseBy(c.parseByName, c.input) else parser.parse(c.input)
    }
}

/** Runs [block], which must throw, and returns the exception message. */
private inline fun captureMessage(what: String, block: () -> Unit): String {
    var message: String? = null
    try {
        block()
    } catch (e: Exception) {
        message = e.message ?: e.toString()
    }
    return message ?: throw IllegalStateException("$what: expected loading/parsing to fail")
}

private fun runCase(c: Case): String = when (c.kind) {
    "parse" -> runParse(c).toSExp().toPrettyString()
    "loadError" -> captureMessage(c.name) { buildParser(c) }
    "parseError" -> captureMessage(c.name) { runParse(c) }
    else -> throw IllegalStateException("unknown kind ${c.kind}")
}

/** Renders a TS string literal; everything else in the generated file is plain text. */
private fun tsString(s: String): String {
    val sb = StringBuilder("\"")
    for (ch in s) {
        when (ch) {
            '\\' -> sb.append("\\\\")
            '"' -> sb.append("\\\"")
            '\n' -> sb.append("\\n")
            '\r' -> sb.append("\\r")
            '\t' -> sb.append("\\t")
            else -> if (ch.code < 0x20) {
                sb.append("\\u")
                sb.append(ch.code.toString(16).padStart(4, '0'))
            } else {
                sb.append(ch)
            }
        }
    }
    sb.append('"')
    return sb.toString()
}

private fun renderOptions(options: Options): String {
    val parts = mutableListOf<String>()
    options.caseSensitive?.let { parts += "caseSensitive: $it" }
    options.splitWord?.let { parts += "splitWord: $it" }
    options.leftRecurDetection?.let { parts += "leftRecurDetection: $it" }
    options.useMemorizedParser?.let { parts += "useMemorizedParser: $it" }
    options.locale?.let { spec ->
        val entries = spec.entries?.entries?.sortedBy { it.key }?.joinToString(", ") { (k, v) ->
            "${tsString(k)}: ${tsString(v)}"
        }
        val locale = if (entries.isNullOrEmpty()) {
            "{ language: ${tsString(spec.language)} }"
        } else {
            "{ language: ${tsString(spec.language)}, entries: { $entries } }"
        }
        parts += "locale: $locale"
    }
    return "{ ${parts.joinToString(", ")} }"
}

private fun renderCase(c: Case): String {
    val sb = StringBuilder()
    sb.append("  {\n")
    sb.append("    name: ").append(tsString(c.name)).append(",\n")
    sb.append("    kind: ").append(tsString(c.kind)).append(",\n")
    sb.append("    mode: ").append(tsString(c.mode)).append(",\n")
    if (c.mode == "ast") sb.append("    astDepth: ").append(c.astDepth).append(",\n")
    if (c.grammar.isNotEmpty()) sb.append("    grammar: ").append(tsString(c.grammar)).append(",\n")
    c.parseByName?.let { sb.append("    parseByName: ").append(tsString(it)).append(",\n") }
    sb.append("    input: ").append(tsString(c.input)).append(",\n")
    c.options?.let { sb.append("    options: ").append(renderOptions(it)).append(",\n") }
    sb.append("  },\n")
    return sb.toString()
}

private fun writeText(file: File, text: String) {
    file.parentFile.mkdirs()
    file.writeText(text, Charsets.UTF_8)
}

fun main() {
    val projectDir = File(".").absoluteFile.normalize()
    val tsDir = File(projectDir, "ts")
    val expectedDir = File(tsDir, "conformance/expected")

    if (!tsDir.isDirectory) {
        throw IllegalStateException("expected the TypeScript package at $tsDir")
    }
    expectedDir.mkdirs()
    expectedDir.listFiles()?.forEach { it.delete() }

    val all = cases()
    val duplicated = all.groupBy { it.name }.filterValues { it.size > 1 }.keys
    if (duplicated.isNotEmpty()) throw IllegalStateException("duplicated case names: $duplicated")

    val sorted = all.sortedBy { it.name }
    val casesFile = StringBuilder()
    casesFile.append("// AUTO-GENERATED by `gradlew generateTsFixtures` from the Kotlin implementation.\n")
    casesFile.append("// Do not edit by hand; change src/test/kotlin/.../GenerateTsFixtures.kt instead.\n")
    casesFile.append("import type { CaseSpec } from \"./types.js\"\n\n")
    casesFile.append("export const CASES: CaseSpec[] = [\n")

    for (c in sorted) {
        val expected = runCase(c)
        writeText(File(expectedDir, "${c.name}.txt"), expected + "\n")
        casesFile.append(renderCase(c))
    }
    casesFile.append("]\n")
    writeText(File(tsDir, "conformance/cases.ts"), casesFile.toString())

    val yccc = YcGrammarParser.getYcGrammarSource()
    val ycccFile = StringBuilder()
    ycccFile.append("// AUTO-GENERATED by `gradlew generateTsFixtures` from src/main/resources/kccc/yccc.grammar.\n")
    ycccFile.append("// Do not edit by hand.\n\n")
    ycccFile.append("/** The yccc grammar describing yccc itself (the bootstrap grammar). */\n")
    ycccFile.append("export const YCCC_GRAMMAR: string = ").append(tsString(yccc)).append("\n")
    writeText(File(tsDir, "src/yccc-grammar.ts"), ycccFile.toString())

    println("generated ${sorted.size} conformance cases into ${expectedDir.relativeTo(projectDir)}")
    println("generated ${File(tsDir, "conformance/cases.ts").relativeTo(projectDir)}")
    println("generated ${File(tsDir, "src/yccc-grammar.ts").relativeTo(projectDir)}")
}
