package io.github.skabyy.kccc.tests

import io.github.skabyy.kccc.parser.NcGrammarParser
import io.github.skabyy.kccc.parser.NcParser
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SExpTests {

    companion object {
        const val grammar = """
:: sexps

@include-builtin

sexps = (@+ sexp)
sexp = (@or o:(open sexps close) i:item)

open = (@or '[' '(')
close = (@or ']' ')')
item = (@+ (@! (@or open close \space)) <*>) spacing

spacing = ~(@* \space)
"""
    }

    @Test
    fun testNcPGP() {
        val parser = NcGrammarParser()
        val parseResult = parser.parse(grammar)
        println(parseResult.toSExp().toPrettyString())
        assertTrue(parseResult.isSuccess())
    }

    @Test
    fun testParse() {
        val parser = NcParser.load(grammar)
        val sexpCode = """
(define (double x) (+ x x))
(define (gcd a b) (if (= a 0) b (gcd (remainder b a) a)))
"""
        val parseResult = parser.parse(sexpCode)
        println(parseResult.toSExp().toPrettyString())
        assertTrue(parseResult.isSuccess())
    }
}
