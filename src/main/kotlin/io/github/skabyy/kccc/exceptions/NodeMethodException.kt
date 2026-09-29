package io.github.skabyy.kccc.exceptions

import io.github.skabyy.kccc.parser.Node

class NodeMethodException(val node: Node?, message: String) : RuntimeException(message)
