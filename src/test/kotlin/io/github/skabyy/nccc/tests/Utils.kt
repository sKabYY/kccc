package io.github.skabyy.nccc.tests

import io.github.skabyy.nccc.parser.ParseResult

/**
 * Test helper replacing the C# original's DiffPlex-based `DiffAndShow`:
 * computes a line diff (LCS) between two strings, prints it with "+ "/"- "
 * prefixes (the C# version used console colors, which the JVM console does
 * not support portably), and returns the changed lines.
 */
object Utils {

    fun diffAndShow(before: ParseResult, after: ParseResult): List<String> {
        return diffAndShow(before.toSExp().toPrettyString(), after.toSExp().toPrettyString())
    }

    fun diffAndShow(before: String, after: String): List<String> {
        if (before == after) return emptyList()
        val a = before.split("\n")
        val b = after.split("\n")
        val n = a.size
        val m = b.size
        // lcs[i][j] = length of the LCS of a[i..n) and b[j..m)
        val lcs = Array(n + 1) { IntArray(m + 1) }
        for (i in n - 1 downTo 0) {
            for (j in m - 1 downTo 0) {
                lcs[i][j] = if (a[i] == b[j]) {
                    lcs[i + 1][j + 1] + 1
                } else {
                    maxOf(lcs[i + 1][j], lcs[i][j + 1])
                }
            }
        }
        val changed = ArrayList<String>()
        var i = 0
        var j = 0
        while (i < n && j < m) {
            when {
                a[i] == b[j] -> {
                    println("  ${a[i]}")
                    i++
                    j++
                }
                lcs[i + 1][j] >= lcs[i][j + 1] -> {
                    println("- ${a[i]}")
                    changed.add("- ${a[i]}")
                    i++
                }
                else -> {
                    println("+ ${b[j]}")
                    changed.add("+ ${b[j]}")
                    j++
                }
            }
        }
        while (i < n) {
            println("- ${a[i]}")
            changed.add("- ${a[i]}")
            i++
        }
        while (j < m) {
            println("+ ${b[j]}")
            changed.add("+ ${b[j]}")
            j++
        }
        return changed
    }
}
