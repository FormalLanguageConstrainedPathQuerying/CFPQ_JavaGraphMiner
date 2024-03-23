package me.cfpq.pointsto.miner

import mu.KotlinLogging
import org.jacodb.api.JcClasspath
import org.jacodb.api.JcType
import java.io.File
import java.util.stream.Collectors.toSet

const val VERTEX_MAPPINGS_FILE_NAME = "vertex_mappings.txt"
const val FIELD_MAPPINGS_FILE_NAME = "field_mappings.txt"
const val TYPE_MAPPINGS_FILE_NAME = "type_mappings.txt"
const val TYPES_FILE_NAME = "types.txt"
const val SUP_TYPES_FILE_NAME = "sup_types.txt"

private val logger = KotlinLogging.logger {}

fun minePtGraph(
    cp: JcClasspath,
    classesPrefix: String,
    outFolder: File,
    ptSimplifier: PtSimplifier = NoopPtSimplifier,
) {
    // Do not refactor it to use Kotlin Sequences instead of Java Streams.
    // Java Streams are used intentionally, so latter they can be made parallel.
    val edges = cp
        .allClasses()
        .filter { it.name.startsWith(classesPrefix) }
        .flatMap { it.declaredMethods.stream() }
        .flatMap { method ->
            val edges = mutableListOf<PtEdge>()
            method.instList.forEach { inst ->
                resolveJcInst(method, inst, edges)
            }
            method.overriddenMethods.forEach { overriddenMethod ->
                edges.add(PtAssignEdge(lhs = PtThis(method), rhs = PtThis(overriddenMethod)))
                method.parameters.indices
                    .forEach { i ->
                        edges.add(PtAssignEdge(lhs = PtArg(method, i), rhs = PtArg(overriddenMethod, i)))
                    }
                edges.add(PtAssignEdge(lhs = PtReturn(overriddenMethod), rhs = PtReturn(method)))
            }
            edges.stream()
        }
        .distinct()
        .let { edges -> ptSimplifier.simplify(edges.collect(toSet())) }
        .toList()

    logStats(edges)

    val vertexIdGenerator = NonConcurrentIdGenerator<PtVertex>()
    val fieldIdGenerator = NonConcurrentIdGenerator<PtField>()

    vertexIdGenerator.generateId(PtStaticContextVertex(cp))

    outFolder.mkdirs()

    dumpPtGraph(
        graphFile = outFolder.resolve("${outFolder.name}.g"),
        edges = edges,
        vertexIdGenerator = vertexIdGenerator,
        fieldIdGenerator = fieldIdGenerator
    )

    val typeIdGenerator = NonConcurrentIdGenerator<JcType>()
    dumpTypesData(outFolder, vertexIdGenerator, typeIdGenerator)
    vertexIdGenerator.writeMappings(outFolder.resolve(VERTEX_MAPPINGS_FILE_NAME))
    fieldIdGenerator.writeMappings(outFolder.resolve(FIELD_MAPPINGS_FILE_NAME))
    typeIdGenerator.writeMappings(outFolder.resolve(TYPE_MAPPINGS_FILE_NAME)) { it.typeName }
}

private fun dumpPtGraph(
    graphFile: File,
    edges: List<PtEdge>,
    vertexIdGenerator: NonConcurrentIdGenerator<PtVertex>,
    fieldIdGenerator: NonConcurrentIdGenerator<PtField>,
) {
    graphFile.printWriter().buffered().use { writer ->
        edges
            .forEach { edge ->
                // the fact that `lhs` is a source vertex, while `rhs` is a target vertex
                // may look a little bit reversed, but that is how it's defined in a
                // corresponding CFPQ_Data grammar
                writer.append(vertexIdGenerator.generateId(edge.lhs).toString())
                writer.append('\t')
                writer.append(vertexIdGenerator.generateId(edge.rhs).toString())
                writer.append('\t')
                writer.append(
                    when (edge) {
                        is PtAllocEdge -> "alloc"
                        is PtAssignEdge -> "assign"
                        is PtLoadEdge -> "load_i ${fieldIdGenerator.generateId(edge.field)}"
                        is PtStoreEdge -> "store_i ${fieldIdGenerator.generateId(edge.field)}"
                    }
                )
                writer.newLine()
                writer.append(vertexIdGenerator.generateId(edge.rhs).toString())
                writer.append('\t')
                writer.append(vertexIdGenerator.generateId(edge.lhs).toString())
                writer.append('\t')
                writer.append(
                    when (edge) {
                        is PtAllocEdge -> "alloc_r"
                        is PtAssignEdge -> "assign_r"
                        is PtLoadEdge -> "load_r_i ${fieldIdGenerator.generateId(edge.field)}"
                        is PtStoreEdge -> "store_r_i ${fieldIdGenerator.generateId(edge.field)}"
                    }
                )
                writer.newLine()
            }
    }
}

private fun dumpTypesData(
    outFolder: File,
    vertexIdGenerator: NonConcurrentIdGenerator<PtVertex>,
    typeIdGenerator: NonConcurrentIdGenerator<JcType>
) {
    outFolder.resolve(TYPES_FILE_NAME).printWriter().buffered().use { writer ->
        vertexIdGenerator.idCache.entries
            .forEach {
                writer.append(it.value.toString())
                writer.append(" ")
                writer.append(typeIdGenerator.generateId(it.key.type.toRaw()).toString())
                writer.newLine()
            }
    }
    outFolder.resolve(SUP_TYPES_FILE_NAME).printWriter().buffered().use { writer ->
        vertexIdGenerator.idCache.entries
            .forEach {
                it.key.type.allRawSuperHierarchySequence().forEach { supType ->
                    typeIdGenerator.getIdOrNull(supType)?.let { supTypeId ->
                        writer.append(it.value.toString())
                        writer.append(" ")
                        writer.append(supTypeId.toString())
                        writer.newLine()
                    }
                }
            }
    }
}

private fun logStats(edges: List<PtEdge>) {
    logger.info {
        buildString {
            appendLine()
            appendLine("Total edges: ${edges.size}")
            appendLine("Edges distribution by type:")
            val edgeGroups = edges.groupBy { it::class.java }
            edgeGroups.forEach { (k, v) -> appendLine("$k: ${v.size} (${v.size * 100 / edges.size}%)") }
            appendLine()
        }
    }
}
