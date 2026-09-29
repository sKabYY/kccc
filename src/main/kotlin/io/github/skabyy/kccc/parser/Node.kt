package io.github.skabyy.kccc.parser

import io.github.skabyy.kccc.common.SExp
import io.github.skabyy.kccc.common.TextPosition
import io.github.skabyy.kccc.exceptions.NodeMethodException
import io.github.skabyy.kccc.scanner.Token

/**
 * Renders a node's start position for error messages ("unknown position"
 * when the node carries none), so message construction keeps the intended
 * exception type instead of NPE-ing on a missing position.
 */
internal fun nodePosition(node: Node): String {
    return node.start?.let { "row ${it.linenum} column ${it.colnum}" } ?: "unknown position"
}

/**
 * Parse-tree node, sealed into [Leaf] (a single token's char value) and
 * [Internal] (a typed node with children) so the two shapes are distinguished
 * by the type system instead of a nullable `children` flag. Both shapes are
 * immutable after construction: [Leaf] always carries a position, [Internal]
 * keeps a nullable type/start/end (the internal dig-wrapper nodes carry none).
 */
sealed class Node {

    abstract val type: String?
    abstract val start: TextPosition?
    abstract val end: TextPosition?

    fun isLeaf(): Boolean {
        return this is Leaf
    }

    fun isStringNode(): Boolean {
        return this is Internal && children.all { it is Leaf }
    }

    /**
     * Returns the string value of a string node, or null if this node is not
     * a string node (replacement for the C# `TryGetStringValue(out ...)`).
     */
    fun tryGetStringValue(): String? {
        if (this is Internal && children.all { it is Leaf }) {
            return nodesToString(children)
        }
        return null
    }

    fun stringValue(): String {
        val value = tryGetStringValue()
        if (value != null) {
            return value
        }
        throw NodeMethodException(this, "can't get StringValue of node ${toSExp().toPrettyString()} at ${nodePosition(this)}")
    }

    fun toSExp(): SExp {
        return when (this) {
            is Leaf -> SExp.value(value)
            is Internal -> {
                val strValue = tryGetStringValue()
                if (strValue != null) {
                    val lst = SExp.list(getNodeMeta())
                    if (strValue.isNotEmpty()) {
                        lst.push(strValue)
                    }
                    lst
                } else {
                    val lst = SExp.list(*children.map { it.toSExp() }.toTypedArray())
                    lst.pushFront(getNodeMeta())
                    lst
                }
            }
        }
    }

    override fun toString(): String {
        return toSExp().toPrettyString()
    }

    private fun getNodeMeta(): String {
        return "${type}[(${start?.linenum},${start?.colnum})-(${end?.linenum},${end?.colnum})]"
    }

    /**
     * Structural `match`: the block receives a registrar function that maps a
     * node type to a handler over the node's children. Exactly mirrors the C#
     * `Match(Action<Action<string, Action<IList<Node>>>>)` / `Match<T>(...)`
     * pair — the generic form alone subsumes the void form (T = Unit).
     */
    fun <T> match(block: ((String, (List<Node>) -> T) -> Unit) -> Unit): T {
        var value: Any? = null
        var hit = false
        block { type, proc ->
            if (this.type == type && this is Internal) {
                value = proc(children)
                hit = true
            }
        }
        if (!hit) {
            throw NodeMethodException(this, "match error: unknown type \"${this.type}\" for node ${toSExp().toPrettyString()}")
        }
        @Suppress("UNCHECKED_CAST")
        return value as T
    }

    fun <T> digSomething(
        foundOne: (Node) -> T,
        notFound: (String, Node) -> T,
        foundMore: (String, Node) -> T,
        vararg path: String
    ): T {
        var node = this
        for (i in path.indices) {
            val c = when (node) {
                is Internal -> node.children
                is Leaf -> return notFound(path[i], node)
            }
            val found = c.filter { it.type == path[i] }
            when (found.size) {
                0 -> return notFound(path[i], node)
                1 -> node = found[0]
                else -> return foundMore(path[i], node)
            }
        }
        return foundOne(node)
    }

    fun digNode(vararg path: String): Node {
        return digSomething(
            { node -> node },
            // NOTE: the `$` before the interpolation is literal in the C# string
            // (`$"... in ${node...}"`), not a typo — kept verbatim.
            { type, node -> throw NodeMethodException(this, "type \"$type\" not found in \$${node.toSExp().toPrettyString()}") },
            { type, node -> throw NodeMethodException(this, "two or more \"$type\" found in \$${node.toSExp().toPrettyString()}") },
            *path
        )
    }

    /**
     * Returns the dug node, or null when not found (replacement for the C#
     * `TryDigNode(out ...)` — a null result carries the same information).
     */
    fun tryDigNode(vararg path: String): Node? {
        return digSomething(
            { node -> node },
            { _, _ -> null },
            { _, _ -> null },
            *path
        )
    }

    fun digNodeOrNull(vararg path: String): Node? {
        return tryDigNode(*path)
    }

    fun digValue(vararg path: String): String {
        return digNode(*path).stringValue()
    }

    fun digValueOrNull(vararg path: String): String? {
        return tryDigNode(*path)?.stringValue()
    }

    class Leaf(
        val value: Char,
        override val start: TextPosition,
        override val end: TextPosition
    ) : Node() {
        override val type: String? = null
    }

    class Internal(
        override val type: String?,
        val children: List<Node>,
        override val start: TextPosition?,
        override val end: TextPosition?
    ) : Node()

    companion object {

        @JvmStatic
        fun concatValue(nodes: List<Node>): String {
            val node = nodes.firstOrNull { it !is Leaf }
            if (node != null) {
                throw NodeMethodException(node, "expect a leaf but got ${node.toSExp().toPrettyString()} at ${nodePosition(node)}")
            }
            return nodesToString(nodes)
        }

        @JvmStatic
        fun makeLeaf(tok: Token): Leaf {
            return Leaf(tok.value, tok.start, tok.end)
        }

        @JvmStatic
        fun makeNode(type: String?, children: List<Node>?, start: TextPosition?, end: TextPosition?): Internal {
            return Internal(type, children ?: emptyList(), start, end)
        }

        @JvmStatic
        fun match(nodes: List<Node>, block: ((String, (List<Node>) -> Unit) -> Unit) -> Unit) {
            for (node in nodes) {
                node.match(block)
            }
        }

        @JvmStatic
        fun digNode(nodes: List<Node>, vararg path: String): Node {
            return Internal(null, nodes, null, null).digNode(*path)
        }

        @JvmStatic
        fun tryDigNode(nodes: List<Node>, vararg path: String): Node? {
            return Internal(null, nodes, null, null).tryDigNode(*path)
        }

        @JvmStatic
        fun digNodeOrNull(nodes: List<Node>, vararg path: String): Node? {
            return Internal(null, nodes, null, null).digNodeOrNull(*path)
        }

        @JvmStatic
        fun digValue(nodes: List<Node>, vararg path: String): String {
            return Internal(null, nodes, null, null).digValue(*path)
        }

        private fun nodesToString(nodes: List<Node>): String {
            return String(nodes.map { (it as Leaf).value }.toCharArray())
        }
    }
}
