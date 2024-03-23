package me.cfpq.pointsto.miner

import java.io.File
import java.net.URL

interface IdGenerator<in T> {
    fun generateId(value: T): Int
}

class NonConcurrentIdGenerator<T> : IdGenerator<T> {
    val idCache = mutableMapOf<T, Int>()

    override fun generateId(value: T): Int = idCache.getOrPut(value) { idCache.size }
    fun getIdOrNull(value: T): Int? = idCache[value]
}

fun <T> NonConcurrentIdGenerator<T>.writeMappings(file: File, map: (T) -> Any? = { it }) =
    file.printWriter().buffered().use { writer ->
        idCache.entries
            .sortedBy { it.value }
            .forEach {
                writer.append(it.value.toString())
                writer.append(": ")
                writer.append(map(it.key).toString().replace("\n", "\\n"))
                writer.newLine()
            }
    }

fun readMappings(url: URL): Map<Int, String> = url.openStream().bufferedReader().useLines { lines ->
    lines.associate { line ->
        val (key, value) = line.split(": ", limit = 2)
        key.toInt() to value
    }
}
