package me.cfpq.pointsto.miner

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.nio.file.Files
import kotlin.test.assertEquals

class GraphMinerTest {
    @ParameterizedTest
    @ValueSource(strings = ["simple", "inheritance"])
    fun `graph miner should work correctly on sample classes`(testCase: String) = runBlocking {
        val actualFolder = Files.createTempDirectory("pt_graph_out").toFile()
        actualFolder.deleteOnExit()
        useJacoDb { cp ->
            minePtGraph(
                cp = cp,
                classesPrefix = "me.cfpq.pointsto.miner.sample.$testCase.",
                outFolder = actualFolder,
                ptSimplifier = SlowPtSimplifier
            )
        }
        val actualGraph = readGraph(GraphPaths(
            graphName = actualFolder.name,
            urlResolver = { actualFolder.resolve(it).toURI().toURL() }
        ))

        val expectedGraph = readGraph(GraphPaths(
            graphName = testCase,
            urlResolver = { this.javaClass.classLoader.getResource("expected/graphs/$testCase/$it")!! }
        ))

        assertEquals(actualGraph, expectedGraph)
    }

    @Test
    fun `graph miner should not throw on mockito even though JacoDB fails to parse some mockito classes`() {
        assertDoesNotThrow {
            runBlocking {
                val outFolder = Files.createTempDirectory("pt_graph_out").toFile()
                outFolder.deleteOnExit()
                useJacoDb { cp ->
                    minePtGraph(cp, "org.mockito.", outFolder)
                }
            }
        }
    }
}
