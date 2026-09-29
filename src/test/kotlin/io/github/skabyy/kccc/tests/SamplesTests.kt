package io.github.skabyy.kccc.tests

import io.github.skabyy.kccc.parser.YcParser
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SamplesTests {

    private fun parseAndPrint(grammar: String, source: String) {
        val postProcessedGrammar = """
::root
@include-builtin
spacing = ~(@* \space)
""" + grammar
        val parser = YcParser.load(postProcessedGrammar)
        println("source: \"$source\"")
        val result = parser.parse(source)
        println(result.toSExp().toPrettyString())
        assertTrue(result.isSuccess())
    }

    @Test
    fun parseString() {
        parseAndPrint(grammar = "root = 'A'", source = "A")
    }

    @Test
    fun parseSeq() {
        parseAndPrint(grammar = "root = (@.. 'A' 'B')", source = "A B")
        parseAndPrint(grammar = "root = ('A' 'B')", source = "A B")
        parseAndPrint(grammar = "root = 'A' 'B'", source = "A B")
    }

    @Test
    fun parseOr() {
        val grammar = "root = (@or 'A' 'B')"
        parseAndPrint(grammar = grammar, source = "A")
        parseAndPrint(grammar = grammar, source = "B")
    }

    @Test
    fun parseMaybe() {
        val grammar = "root = (@? 'A')"
        parseAndPrint(grammar = grammar, source = "")
        parseAndPrint(grammar = grammar, source = "A")
    }

    @Test
    fun parseStar() {
        val grammar = "root = (@* 'A')"
        parseAndPrint(grammar = grammar, source = "")
        parseAndPrint(grammar = grammar, source = "A A A A")
    }

    @Test
    fun parseStarAOrB() {
        val grammar = "root = (@* (@or 'A' 'B'))"
        parseAndPrint(grammar = grammar, source = "B B")
        parseAndPrint(grammar = grammar, source = "A A")
        parseAndPrint(grammar = grammar, source = "A B")
        parseAndPrint(grammar = grammar, source = "A B A A")
    }

    @Test
    fun parseNumber() {
        parseAndPrint(grammar = "root = number", source = "1.1")
    }

    @Test
    fun parseNumberOrStringA() {
        val grammar = "root = (@or number 'A')"
        parseAndPrint(grammar = grammar, source = "1.1")
        parseAndPrint(grammar = grammar, source = "A")
    }

    @Test
    fun parseStarNumberOrStringA() {
        val grammar = "root = (@* (@or number #'A'))"
        parseAndPrint(grammar = grammar, source = "1.1")
        parseAndPrint(grammar = grammar, source = "A")
        parseAndPrint(grammar = grammar, source = "A A")
        parseAndPrint(grammar = grammar, source = "1.1 2")
        parseAndPrint(grammar = grammar, source = "A 23")
        parseAndPrint(grammar = grammar, source = "A 1.2 A A")
    }

    private fun parseByAndPrint(grammar: String, parserName: String, source: String) {
        val postProcessedGrammar = """
::root
@include-builtin
spacing = ~(@* \space)
""" + grammar
        val parser = YcParser.load(postProcessedGrammar)
        println("source: \"$source\"")
        val result = parser.parseBy(parserName, source)
        println(result.toSExp().toPrettyString())
        assertTrue(result.isSuccess())
    }

    @Test
    fun parseBy() {
        val grammar = """
root = header footer
header = (@+ num:number)
footer = (@+ var:(@+ alpha) spacing)
"""
        parseAndPrint(grammar = grammar, source = "1.1 1.2 abc ef")
        parseByAndPrint(grammar = grammar, parserName = "header", source = "1.1 1.2")
        parseByAndPrint(grammar = grammar, parserName = "footer", source = "abc ef")
    }

    @Test
    fun defaultSpacingShouldBeGlobbed() {
        val grammar = """
root = header footer
header = (@+ num:number)
footer = (@+ var:(@+ alpha) spacing)
"""
        val postProcessedGrammar = """
::root
@include-builtin
spacing = ~(@* \space)
""" + grammar
        val parser = YcParser.load(postProcessedGrammar)
        val source = "1.1 1.2 abc ef"
        println("source: \"$source\"")
        val result = parser.parse(source)
        println(result.toSExp().toPrettyString())
        assertTrue(result.isSuccess())
        assertFalse(result.nodes!!.first().stringValue().endsWith(' '))
    }

    @Test
    fun eofTests() {
        val grammar = """
root = (@+ num:number)
"""
        val postProcessedGrammar = """
::root
@include-builtin
spacing = ~(@* \space)
""" + grammar
        val parser = YcParser.load(postProcessedGrammar)
        val source = "1.1 1.2 abc ef"
        println("source: \"$source\"")
        val result = parser.parse(source)
        println(result.toSExp().toPrettyString())
        assertFalse(result.isSuccess())
        assertTrue(result.message!!.contains("EOF"))
    }
}
