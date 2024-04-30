package me.cfpq.pointsto.miner

import java.net.URL
import kotlin.test.assertEquals

data class GraphPaths(
    val graphURL: URL,
    val vertexMappingsURL: URL,
    val fieldMappingsURL: URL,
    val typesMappingsURL: URL,
    val typesURL: URL,
    val supTypesURL: URL,
) {
    constructor(graphName: String, urlResolver: (name: String) -> URL): this(
        graphURL = urlResolver("$graphName.g"),
        vertexMappingsURL = urlResolver(VERTEX_MAPPINGS_FILE_NAME),
        fieldMappingsURL = urlResolver(FIELD_MAPPINGS_FILE_NAME),
        typesMappingsURL = urlResolver(TYPE_MAPPINGS_FILE_NAME),
        typesURL = urlResolver(TYPES_FILE_NAME),
        supTypesURL = urlResolver(SUP_TYPES_FILE_NAME),
    )
}

data class DeserializedGraph(
    val edges: Set<TestEdge>,
    val vertexToType: Map<String, String>,
    val vertexToSupTypes: Map<String, Set<String>>
)

data class TestEdge(
    val source: String,
    val target: String,
    val label: String,
    val field: String?
)

/**
 * Reads graph representation without ids, that can be used in tests
 * with [assertEquals] without failing every time [IdGenerator] or JacoDB changes.
 */
fun readGraph(paths: GraphPaths): DeserializedGraph {
    val vertexMappings = readMappings(paths.vertexMappingsURL)
    val fieldMappings = readMappings(paths.fieldMappingsURL)
    val typeMappings = readMappings(paths.typesMappingsURL)
    return DeserializedGraph(
        edges = paths.graphURL.openStream().bufferedReader().useLines { lines ->
            lines.map { line ->
                val parts = line.splitWords()
                val (source, target, label) = parts
                val field = parts.getOrNull(3)
                TestEdge(
                    vertexMappings.getValue(source.toInt()).withoutJacoDbIds(),
                    vertexMappings.getValue(target.toInt()).withoutJacoDbIds(),
                    label,
                    field?.let { fieldMappings.getValue(it.toInt()) }?.withoutJacoDbIds()
                )
            }.toSet()
        },
        vertexToType = paths.typesURL.openStream().bufferedReader().useLines { lines ->
            lines.associate { line ->
                val (vertex, type) = line.splitWords()
                vertexMappings.getValue(vertex.toInt()).withoutJacoDbIds() to typeMappings.getValue(type.toInt())
            }
        },
        vertexToSupTypes = paths.typesURL.openStream().bufferedReader().useLines { lines ->
            lines.map { line ->
                val (vertex, type) = line.splitWords()
                vertexMappings.getValue(vertex.toInt()).withoutJacoDbIds() to typeMappings.getValue(type.toInt())
            }
                .groupBy(keySelector = { it.first }, valueTransform = { it.second })
                .mapValues { it.value.toSet() }
        }
    )
}

// JacoDB generates JRE-dependent ID for every method and field (e.g. `(id:7)java.lang.Object#<init>()`)
private fun String.withoutJacoDbIds(): String =
    replace("""\(id:\d+\)""".toRegex(), "(id:jre-dependent)")

private fun String.splitWords() = split("\\s+".toRegex())
