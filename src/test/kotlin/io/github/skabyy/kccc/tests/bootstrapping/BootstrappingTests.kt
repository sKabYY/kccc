package io.github.skabyy.kccc.tests.bootstrapping

import io.github.skabyy.kccc.common.ResourceUtils
import io.github.skabyy.kccc.parser.NcGrammarParser
import io.github.skabyy.kccc.parser.NcParser
import io.github.skabyy.kccc.tests.Utils
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BootstrappingTests {

    private fun p2p(ncParser: NcParser, grammar: String, init: ((NcParser.Settings) -> Unit)? = null): NcParser {
        val ast = ncParser.parse(grammar).nodes!!.first()
        return NcParser.load(ast, init)
    }

    @Test
    fun testSelf() {
        val ncccParser0 = NcGrammarParser()
        val ncGrammar = NcGrammarParser.getNcGrammarSource()
        val ncccParser1 = NcParser.load(ncGrammar)
        val ncccParser2 = p2p(ncccParser1, ncGrammar)
        val pr0 = ncccParser0.parse(ncGrammar)
        assertTrue(pr0.isSuccess())
        val pr1 = ncccParser1.parse(ncGrammar)
        assertTrue(pr1.isSuccess())
        assertEquals(0, Utils.diffAndShow(pr0, pr1).size)
        val pr2 = ncccParser2.parse(ncGrammar)
        assertTrue(pr2.isSuccess())
        assertEquals(0, Utils.diffAndShow(pr0, pr2).size)
    }

    @Test
    fun testErr() {
        val ncccParser0 = NcGrammarParser()
        val ncGrammar = NcGrammarParser.getNcGrammarSource()
        val ncccParser1 = NcParser.load(ncGrammar)
        val ncccParser2 = p2p(ncccParser1, ncGrammar)
        val errGrammar = "::dummy @hehe '123'"
        val pr0 = ncccParser0.parse(errGrammar)
        val pr1 = ncccParser1.parse(errGrammar)
        val pr2 = ncccParser2.parse(errGrammar)
        assertTrue(!pr0.isSuccess())
        assertTrue(!pr1.isSuccess())
        assertTrue(!pr2.isSuccess())
        assertEquals(0, Utils.diffAndShow(pr0, pr1).size)
        assertEquals(0, Utils.diffAndShow(pr0, pr2).size)
    }

    private fun bootstrap3Test(grammarPath: String, samplePath: String) {
        val ncGrammar = NcGrammarParser.getNcGrammarSource()
        val ncccParser1 = NcParser.load(ncGrammar)
        val ncccParser2 = p2p(ncccParser1, ncGrammar)
        val testGrammar = ResourceUtils.readString(grammarPath)
        val jsonParser0 = NcParser.load(testGrammar)
        val jsonParser1 = p2p(ncccParser1, testGrammar)
        val jsonParser2 = p2p(ncccParser2, testGrammar)
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
