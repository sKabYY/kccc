package io.github.skabyy.nccc.tests.lexmode

import io.github.skabyy.nccc.common.ResourceUtils
import io.github.skabyy.nccc.parser.NcParser
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LexModeTests {

    @Test
    fun testSample() {
        val src = ResourceUtils.readString("lexmode/sample.txt")
        val parser = NcParser.loadFromResource("lexmode/lexMode.grammar")
        val parseResult = parser.parse(src)
        println(parseResult.toSExp().toPrettyString())
        assertTrue(parseResult.isSuccess())
    }
}
