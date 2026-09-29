package io.github.skabyy.kccc.tests

import io.github.skabyy.kccc.parser.NcParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ErrsTests {

    companion object {
        const val grammer = """
:: root

@set-message-locale-start 'L{'
@set-message-locale-end '}'

root = (@err'L{expect} A L{or} B' oo:(@or 'A' 'B'))
"""
    }

    @Test
    fun testMessageLocale() {
        val parser = NcParser.load(grammer) { settings ->
            settings.locale.language = "zh-cn"
            settings.locale.set(
                "zh-cn", mapOf(
                    "expect" to "盼望着",
                    "or" to "或"
                )
            )
        }
        val source = "C"
        val result = parser.parse(source)
        println(result.toSExp().toPrettyString())
        assertFalse(result.isSuccess())
        assertEquals("盼望着 A 或 B", result.message)
    }

    @Test
    fun testCIfFail() {
        val grmr = """
::root
@include-builtin
root = (@+ alpha) \eof
"""
        val parser = NcParser.load(grmr)
        val result = parser.parse("a1")
        println(result.toSExp().toPrettyString())
        assertFalse(result.isSuccess())
    }
}
