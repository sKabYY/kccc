package io.github.skabyy.nccc.tests.json

import io.github.skabyy.nccc.common.ResourceUtils
import io.github.skabyy.nccc.parser.NcParser
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class JsonTests {

    @Test
    fun testSample() {
        val src = ResourceUtils.readString("json/sample.json")
        val parser = NcParser.loadFromResource("json/json.grammar")
        val parseResult = parser.parse(src)
        println(parseResult.toSExp().toPrettyString())
        assertTrue(parseResult.isSuccess())
    }

    @Test
    fun testError() {
        val expectMessage = "哈哈"
        val src = "[1,2,3"
        val parser = NcParser.loadFromResource("json/json.grammar") { settings ->
            settings.locale.language = "zh-cn"
            settings.locale.set("zh-cn", mapOf("expect" to expectMessage))
        }
        val parseResult = parser.parse(src)
        println(parseResult.toSExp().toPrettyString())
        assertFalse(parseResult.isSuccess())
        assertTrue(parseResult.message!!.contains(expectMessage))
    }
}
