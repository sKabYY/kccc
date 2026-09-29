package io.github.skabyy.nccc.tests.calculator

import io.github.skabyy.nccc.common.ResourceUtils
import io.github.skabyy.nccc.parser.Node
import io.github.skabyy.nccc.parser.NcParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class Calculator {

    private val parser = NcParser.loadFromResource("calculator/calculator.grammar")

    @Test
    fun testParser() {
        val pr = parser.parse("(5.1+2)*3+-2^3^2")
        println(pr.toSExp().toPrettyString())
    }

    private fun calc(exp: String): Double {
        val pr = parser.parse(exp)
        if (!pr.isSuccess()) {
            throw IllegalArgumentException("Parsing fail: ${pr.toSExp().toPrettyString()}")
        }
        return valueOf(pr.nodes!!.first())
    }

    private fun valueOf(node: Node): Double = node.match { type ->
        type("par") { es -> valueOf(es[0]) }
        type("add") { es -> valueOf(es[0]) + valueOf(es[1]) }
        type("sub") { es -> valueOf(es[0]) - valueOf(es[1]) }
        type("mul") { es -> valueOf(es[0]) * valueOf(es[1]) }
        type("div") { es -> valueOf(es[0]) / valueOf(es[1]) }
        type("pow") { es -> Math.pow(valueOf(es[0]), valueOf(es[1])) }
        type("neg") { es -> -valueOf(es[0]) }
        type("num") { es -> Node.concatValue(es).toDouble() }
    }

    @Test
    fun testCalc() {
        val x = calc("(5.1 +2)* 3+-2 ^3^2")
        val x0 = (5.1 + 2) * 3 - Math.pow(2.0, Math.pow(3.0, 2.0))
        println(x)
        assertEquals(x0, x, 0.0)
    }

    @Test
    fun testDig() {
        val pr = parser.parse("(5.1+2)*3+-2^3^2")
        println(pr)
        val node = Node.digNode(pr.nodes!!, "add", "mul")
        assertEquals("mul", node.type)
        val value = Node.digValue(pr.nodes!!, "add", "neg", "pow", "num")
        assertEquals("2", value)
    }
}
