package io.github.skabyy.kccc.tests

import io.github.skabyy.kccc.parser.NcParser
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SamplesTests {

    private fun parseAndPrint(grammer: String, source: String) {
        val postProcessedGrammer = """
::root
@include-builtin
spacing = ~(@* \space)
""" + grammer
        val parser = NcParser.load(postProcessedGrammer)
        println("source: \"$source\"")
        val result = parser.parse(source)
        println(result.toSExp().toPrettyString())
        assertTrue(result.isSuccess())
    }

    @Test
    fun parseString() {
        parseAndPrint(grammer = "root = 'A'", source = "A")
    }

    @Test
    fun parseSeq() {
        parseAndPrint(grammer = "root = (@.. 'A' 'B')", source = "A B")
        parseAndPrint(grammer = "root = ('A' 'B')", source = "A B")
        parseAndPrint(grammer = "root = 'A' 'B'", source = "A B")
    }

    @Test
    fun parseOr() {
        val grammer = "root = (@or 'A' 'B')"
        parseAndPrint(grammer = grammer, source = "A")
        parseAndPrint(grammer = grammer, source = "B")
    }

    @Test
    fun parseMaybe() {
        val grammer = "root = (@? 'A')"
        parseAndPrint(grammer = grammer, source = "")
        parseAndPrint(grammer = grammer, source = "A")
    }

    @Test
    fun parseStar() {
        val grammer = "root = (@* 'A')"
        parseAndPrint(grammer = grammer, source = "")
        parseAndPrint(grammer = grammer, source = "A A A A")
    }

    @Test
    fun parseStarAOrB() {
        val grammer = "root = (@* (@or 'A' 'B'))"
        parseAndPrint(grammer = grammer, source = "B B")
        parseAndPrint(grammer = grammer, source = "A A")
        parseAndPrint(grammer = grammer, source = "A B")
        parseAndPrint(grammer = grammer, source = "A B A A")
    }

    @Test
    fun parseNumber() {
        parseAndPrint(grammer = "root = number", source = "1.1")
    }

    @Test
    fun parseNumberOrStringA() {
        val grammer = "root = (@or number 'A')"
        parseAndPrint(grammer = grammer, source = "1.1")
        parseAndPrint(grammer = grammer, source = "A")
    }

    @Test
    fun parseStarNumberOrStringA() {
        val grammer = "root = (@* (@or number #'A'))"
        parseAndPrint(grammer = grammer, source = "1.1")
        parseAndPrint(grammer = grammer, source = "A")
        parseAndPrint(grammer = grammer, source = "A A")
        parseAndPrint(grammer = grammer, source = "1.1 2")
        parseAndPrint(grammer = grammer, source = "A 23")
        parseAndPrint(grammer = grammer, source = "A 1.2 A A")
    }

    private fun parseByAndPrint(grammer: String, parserName: String, source: String) {
        val postProcessedGrammer = """
::root
@include-builtin
spacing = ~(@* \space)
""" + grammer
        val parser = NcParser.load(postProcessedGrammer)
        println("source: \"$source\"")
        val result = parser.parseBy(parserName, source)
        println(result.toSExp().toPrettyString())
        assertTrue(result.isSuccess())
    }

    @Test
    fun parseBy() {
        val grammer = """
root = header footer
header = (@+ num:number)
footer = (@+ var:(@+ alpha) spacing)
"""
        parseAndPrint(grammer = grammer, source = "1.1 1.2 abc ef")
        parseByAndPrint(grammer = grammer, parserName = "header", source = "1.1 1.2")
        parseByAndPrint(grammer = grammer, parserName = "footer", source = "abc ef")
    }

    @Test
    fun defaultSpacingShouldBeGlobbed() {
        val grammer = """
root = header footer
header = (@+ num:number)
footer = (@+ var:(@+ alpha) spacing)
"""
        val postProcessedGrammer = """
::root
@include-builtin
spacing = ~(@* \space)
""" + grammer
        val parser = NcParser.load(postProcessedGrammer)
        val source = "1.1 1.2 abc ef"
        println("source: \"$source\"")
        val result = parser.parse(source)
        println(result.toSExp().toPrettyString())
        assertTrue(result.isSuccess())
        assertFalse(result.nodes!!.first().stringValue().endsWith(' '))
    }

    @Test
    fun eofTests() {
        val grammer = """
root = (@+ num:number)
"""
        val postProcessedGrammer = """
::root
@include-builtin
spacing = ~(@* \space)
""" + grammer
        val parser = NcParser.load(postProcessedGrammer)
        val source = "1.1 1.2 abc ef"
        println("source: \"$source\"")
        val result = parser.parse(source)
        println(result.toSExp().toPrettyString())
        assertFalse(result.isSuccess())
        assertTrue(result.message!!.contains("EOF"))
    }
}
