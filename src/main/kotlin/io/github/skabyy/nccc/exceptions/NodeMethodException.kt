package io.github.skabyy.nccc.exceptions

import io.github.skabyy.nccc.parser.Node

class NodeMethodException(val node: Node?, message: String) : RuntimeException(message)
