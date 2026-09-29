package io.github.skabyy.nccc.common

import java.io.FileNotFoundException

/**
 * JVM replacement of the .NET `AssemblyUtils` helper: reads a text resource
 * from the classpath and decodes it as UTF-8.
 *
 * Unlike .NET's `StreamReader`, plain JVM readers do not strip the UTF-8 BOM,
 * and the original grammar/sample resources are stored with a BOM. The BOM is
 * stripped here so that the decoded string matches what
 * `File.ReadAllText` / `StreamReader.ReadToEnd` would have produced on .NET.
 */
object ResourceUtils {

    fun readString(path: String): String {
        val stream = ResourceUtils::class.java.classLoader.getResourceAsStream(path)
            ?: throw FileNotFoundException("resource not found: $path")
        return stream.use {
            it.readBytes().toString(Charsets.UTF_8).removePrefix("\uFEFF")
        }
    }
}
