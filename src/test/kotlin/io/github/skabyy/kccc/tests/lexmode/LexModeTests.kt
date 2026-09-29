package io.github.skabyy.kccc.tests.lexmode

import io.github.skabyy.kccc.common.ResourceUtils
import io.github.skabyy.kccc.parser.YcParser
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LexModeTests {

    @Test
    fun testSample() {
        val src = ResourceUtils.readString("lexmode/sample.txt")
        val parser = YcParser.loadFromResource("lexmode/lexMode.grammar")
        val parseResult = parser.parse(src)
        println(parseResult.toSExp().toPrettyString())
        assertTrue(parseResult.isSuccess())
    }
}
