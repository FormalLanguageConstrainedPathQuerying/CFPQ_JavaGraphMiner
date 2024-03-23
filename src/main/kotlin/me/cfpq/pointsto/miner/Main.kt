package me.cfpq.pointsto.miner

import mu.KotlinLogging
import org.jacodb.api.JcClasspath
import org.jacodb.impl.features.classpaths.UnknownClassMethodsAndFields
import org.jacodb.impl.features.classpaths.UnknownClasses
import org.jacodb.impl.jacodb
import java.io.File

private val logger = KotlinLogging.logger {}

private val libs = listOf(
    "junit5" to "org.junit",
    "guava" to "com.google.common",
    "mockito" to "org.mockito",
    "jackson" to "com.fasterxml.jackson",
    "commons_lang3" to "org.apache.commons.lang3",
    "commons_io" to "org.apache.commons.io",
    "gson" to "com.google.gson",
)

suspend fun main() {
    useJacoDb { cp ->
        val outFolder = File("graphs")
        libs.forEach { (name, prefix) ->
            logger.info { "Processing $name..." }
            minePtGraph(cp, "$prefix.", outFolder.resolve(name))
        }
    }
}

suspend fun useJacoDb(block: (JcClasspath) -> Unit) = jacodb {}.use { db ->
    db.classpath(getRuntimeClasspath(), listOf(UnknownClassMethodsAndFields, UnknownClasses)).use(block)
}

private fun getRuntimeClasspath(): List<File> {
    val classpath = System.getProperty("java.class.path")
    val classpathFiles = classpath.split(File.pathSeparator)
        .filter { it.isNotEmpty() }
        .map { File(it) }
    return classpathFiles
}
