package io.github.skabyy.kccc.tests.bootstrapping

import io.github.skabyy.kccc.common.ResourceUtils
import io.github.skabyy.kccc.parser.YcGrammarParser
import io.github.skabyy.kccc.parser.YcParser
import io.github.skabyy.kccc.tests.Utils
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BootstrappingTests {

    private fun p2p(ycParser: YcParser, grammar: String, init: ((YcParser.Settings) -> Unit)? = null): YcParser {
        val ast = ycParser.parse(grammar).nodes!!.first()
        return YcParser.load(ast, init)
    }

    @Test
    fun testSelf() {
        val ycParser0 = YcGrammarParser()
        val ycGrammar = YcGrammarParser.getYcGrammarSource()
        val ycParser1 = YcParser.load(ycGrammar)
        val ycParser2 = p2p(ycParser1, ycGrammar)
        val pr0 = ycParser0.parse(ycGrammar)
        assertTrue(pr0.isSuccess())
        val pr1 = ycParser1.parse(ycGrammar)
        assertTrue(pr1.isSuccess())
        assertEquals(0, Utils.diffAndShow(pr0, pr1).size)
        val pr2 = ycParser2.parse(ycGrammar)
        assertTrue(pr2.isSuccess())
        assertEquals(0, Utils.diffAndShow(pr0, pr2).size)
    }

    @Test
    fun testErr() {
        val ycParser0 = YcGrammarParser()
        val ycGrammar = YcGrammarParser.getYcGrammarSource()
        val ycParser1 = YcParser.load(ycGrammar)
        val ycParser2 = p2p(ycParser1, ycGrammar)
        val errGrammar = "::dummy @hehe '123'"
        val pr0 = ycParser0.parse(errGrammar)
        val pr1 = ycParser1.parse(errGrammar)
        val pr2 = ycParser2.parse(errGrammar)
        assertTrue(!pr0.isSuccess())
        assertTrue(!pr1.isSuccess())
        assertTrue(!pr2.isSuccess())
        assertEquals(0, Utils.diffAndShow(pr0, pr1).size)
        assertEquals(0, Utils.diffAndShow(pr0, pr2).size)
    }

    private fun bootstrap3Test(grammarPath: String, samplePath: String) {
        val ycGrammar = YcGrammarParser.getYcGrammarSource()
        val ycParser1 = YcParser.load(ycGrammar)
        val ycParser2 = p2p(ycParser1, ycGrammar)
        val testGrammar = ResourceUtils.readString(grammarPath)
        val jsonParser0 = YcParser.load(testGrammar)
        val jsonParser1 = p2p(ycParser1, testGrammar)
        val jsonParser2 = p2p(ycParser2, testGrammar)
        val testSample = ResourceUtils.readString(samplePath)
        val pr0 = jsonParser0.parse(testSample)
        val pr1 = jsonParser1.parse(testSample)
        val pr2 = jsonParser2.parse(testSample)
        assertTrue(pr0.isSuccess())
        assertTrue(pr1.isSuccess())
        assertTrue(pr2.isSuccess())
        assertEquals(0, Utils.diffAndShow(pr0, pr1).size)
        assertEquals(0, Utils.diffAndShow(pr0, pr2).size)
    }

    @Test
    fun testJson() {
        bootstrap3Test("json/json.grammar", "json/sample.json")
    }

    @Test
    fun testLexMode() {
        bootstrap3Test("lexmode/lexMode.grammar", "lexmode/sample.txt")
    }
}
