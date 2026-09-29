package io.github.skabyy.kccc.tests.bootstrapping

import io.github.skabyy.kccc.common.ResourceUtils
import io.github.skabyy.kccc.parser.NcGrammerParser
import io.github.skabyy.kccc.parser.NcParser
import io.github.skabyy.kccc.tests.Utils
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BootstrappingTests {

    private fun p2p(ncParser: NcParser, grammer: String, init: ((NcParser.Settings) -> Unit)? = null): NcParser {
        val ast = ncParser.parse(grammer).nodes!!.first()
        return NcParser.load(ast, init)
    }

    @Test
    fun testSelf() {
        val ncccParser0 = NcGrammerParser()
        val ncGrammer = NcGrammerParser.getNcGrammerSource()
        val ncccParser1 = NcParser.load(ncGrammer)
        val ncccParser2 = p2p(ncccParser1, ncGrammer)
        val pr0 = ncccParser0.parse(ncGrammer)
        assertTrue(pr0.isSuccess())
        val pr1 = ncccParser1.parse(ncGrammer)
        assertTrue(pr1.isSuccess())
        assertEquals(0, Utils.diffAndShow(pr0, pr1).size)
        val pr2 = ncccParser2.parse(ncGrammer)
        assertTrue(pr2.isSuccess())
        assertEquals(0, Utils.diffAndShow(pr0, pr2).size)
    }

    @Test
    fun testErr() {
        val ncccParser0 = NcGrammerParser()
        val ncGrammer = NcGrammerParser.getNcGrammerSource()
        val ncccParser1 = NcParser.load(ncGrammer)
        val ncccParser2 = p2p(ncccParser1, ncGrammer)
        val errGrammer = "::dummy @hehe '123'"
        val pr0 = ncccParser0.parse(errGrammer)
        val pr1 = ncccParser1.parse(errGrammer)
        val pr2 = ncccParser2.parse(errGrammer)
        assertTrue(!pr0.isSuccess())
        assertTrue(!pr1.isSuccess())
        assertTrue(!pr2.isSuccess())
        assertEquals(0, Utils.diffAndShow(pr0, pr1).size)
        assertEquals(0, Utils.diffAndShow(pr0, pr2).size)
    }

    private fun bootstrap3Test(grammerPath: String, samplePath: String) {
        val ncGrammer = NcGrammerParser.getNcGrammerSource()
        val ncccParser1 = NcParser.load(ncGrammer)
        val ncccParser2 = p2p(ncccParser1, ncGrammer)
        val testGrammer = ResourceUtils.readString(grammerPath)
        val jsonParser0 = NcParser.load(testGrammer)
        val jsonParser1 = p2p(ncccParser1, testGrammer)
        val jsonParser2 = p2p(ncccParser2, testGrammer)
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
