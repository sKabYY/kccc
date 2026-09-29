package io.github.skabyy.nccc.parser

import io.github.skabyy.nccc.common.SExp
import io.github.skabyy.nccc.common.TextPosition
import io.github.skabyy.nccc.exceptions.NodeMethodException
import io.github.skabyy.nccc.scanner.Token

class Node {

    var type: String? = null
    var value: Char = '\u0000'
    var children: MutableList<Node>? = null
    var start: TextPosition? = null
    var end: TextPosition? = null

    fun isLeaf(): Boolean {
        return children == null
    }

    fun isStringNode(): Boolean {
        return children != null && children!!.all { it.isLeaf() }
    }

    /**
     * Returns the string value of a string node, or null if this node is not
     * a string node (replacement for the C# `TryGetStringValue(out ...)`).
     */
    fun tryGetStringValue(): String? {
        if (isStringNode()) {
            return nodesToString(children!!)
        }
        return null
    }

    fun stringValue(): String {
        val value = tryGetStringValue()
        if (value != null) {
            return value
        }
        val pos = start!!
        throw NodeMethodException(this, "can't get StringValue of node ${toSExp().toPrettyString()} at row ${pos.linenum} colum ${pos.colnum}")
    }

    fun toSExp(): SExp {
        if (isLeaf()) {
            return SExp.value(value)
        }
        val strValue = tryGetStringValue()
        if (strValue != null) {
            val lst = SExp.list(getNodeMeta())
            if (!strValue.isNullOrEmpty()) {
                lst.push(strValue)
            }
            return lst
        }
        val lst = SExp.list(*children!!.map { it.toSExp() }.toTypedArray())
        lst.pushFront(getNodeMeta())
        return lst
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
    fun <T> match(block: ((String, (MutableList<Node>) -> T) -> Unit) -> Unit): T {
        var value: Any? = null
        var hit = false
        block { type, proc ->
            if (this.type == type) {
                value = proc(children!!)
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
            if (node.isLeaf()) {
                return notFound(path[i], node)
            }
            val found = node.children!!.filter { it.type == path[i] }
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

    companion object {

        @JvmStatic
        fun concatValue(nodes: MutableList<Node>): String {
            val node = nodes.firstOrNull { !it.isLeaf() }
            if (node != null) {
                val pos = node.start
                throw NodeMethodException(node, "expect a leaf but got ${node.toSExp().toPrettyString()} at row ${pos!!.linenum} column ${pos.colnum}")
            }
            return nodesToString(nodes)
        }

        @JvmStatic
        fun makeLeaf(tok: Token): Node {
            val node = Node()
            node.value = tok.value
            node.start = tok.start
            node.end = tok.end
            return node
        }

        @JvmStatic
        fun makeNode(type: String?, children: MutableList<Node>?, start: TextPosition?, end: TextPosition?): Node {
            val node = Node()
            node.type = type
            node.children = children
            node.start = start
            node.end = end
            return node
        }

        @JvmStatic
        fun match(nodes: MutableList<Node>, block: ((String, (MutableList<Node>) -> Unit) -> Unit) -> Unit) {
            for (node in nodes) {
                node.match(block)
            }
        }

        @JvmStatic
        fun digNode(nodes: MutableList<Node>, vararg path: String): Node {
            val node = Node()
            node.children = nodes
            return node.digNode(*path)
        }

        @JvmStatic
        fun tryDigNode(nodes: MutableList<Node>, vararg path: String): Node? {
            val node = Node()
            node.children = nodes
            return node.tryDigNode(*path)
        }

        @JvmStatic
        fun digNodeOrNull(nodes: MutableList<Node>, vararg path: String): Node? {
            val node = Node()
            node.children = nodes
            return node.digNodeOrNull(*path)
        }

        @JvmStatic
        fun digValue(nodes: MutableList<Node>, vararg path: String): String {
            val node = Node()
            node.children = nodes
            return node.digValue(*path)
        }

        private fun nodesToString(nodes: MutableList<Node>): String {
            return String(nodes.map { it.value }.toCharArray())
        }
    }
}
