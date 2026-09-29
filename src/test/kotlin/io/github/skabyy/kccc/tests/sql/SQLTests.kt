package io.github.skabyy.kccc.tests.sql

import io.github.skabyy.kccc.common.ResourceUtils
import io.github.skabyy.kccc.parser.Node
import io.github.skabyy.kccc.parser.YcParser
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SQLTests {

    private val sql = ResourceUtils.readString("sql/sample.sql")
    private val errSql = ResourceUtils.readString("sql/sample-err.sql")

    companion object {
        private fun getSqlParser(): YcParser {
            return YcParser.loadFromResource("sql/sql.grammar") { settings ->
                settings.caseSensitive = false
            }
        }
    }

    @Test
    fun test() {
        val parser = getSqlParser()
        val parseResult = parser.parse(sql)
        println(parseResult.toSExp().toPrettyString())
        assertTrue(parseResult.isSuccess())
    }

    @Test
    fun testErr() {
        val parser = getSqlParser()
        val parseResult = parser.parse(errSql)
        println(parseResult.toSExp().toPrettyString())
        Node.digNode(parseResult.nodes!!, "create_table")
        assertTrue(parseResult.nodes!!.count { it.type == "comment" } > 0)
        assertFalse(parseResult.isSuccess())
    }

    @Test
    fun test100Times() {
        val times = 100
        val parser = getSqlParser()
        for (i in 0 until times) {
            val parseResult = parser.parse(sql)
            assertTrue(parseResult.isSuccess())
        }
    }
}
