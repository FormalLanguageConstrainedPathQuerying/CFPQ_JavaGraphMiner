package me.pointsto.graph.me.pointsto.graph

import me.pointsto.graph.allRawSuperHierarchySequence
import me.pointsto.graph.toRaw
import me.pointsto.sample.Sample
import org.jacodb.api.*
import org.jacodb.api.cfg.*
import org.jacodb.api.ext.*
import org.jacodb.impl.features.classpaths.JcUnknownMethod
import org.jacodb.impl.features.classpaths.UnknownClassMethodsAndFields
import org.jacodb.impl.features.classpaths.UnknownClasses
import org.jacodb.impl.jacodb
import java.io.File
import java.util.stream.Stream
import kotlin.time.measureTime

interface IdGenerator<in T> {
    fun generateId(value: T): Int
}

class NonConcurrentIdGenerator<T> : IdGenerator<T> {
    val idCache = mutableMapOf<T, Int>()

    override fun generateId(value: T): Int = idCache.getOrPut(value) { idCache.size }
    fun getIdOrNull(value: T): Int? = idCache[value]
}

inline fun <T> NonConcurrentIdGenerator<T>.writeMappings(path: String, map: (T) -> Any? = { it }) =
    File(path).printWriter().buffered().use { writer ->
        idCache.entries
            .sortedBy { it.value }
            .forEach {
                writer.append(it.value.toString())
                writer.append(": ")
                writer.append(map(it.key).toString().replace("\n", "\\n"))
                writer.newLine()
            }
}

suspend fun main() {
    println(measureTime {
//        val classpath = "/home/ilya/IdeaProjects/UTBotJava/utbot-junit-contest/build/utbot-usvm-runtool/utbot-usvm-tool.jar"
        val classpath = "/media/ilya/4fb735c1-d7d9-4b24-bf80-a1bab387b053/home/ilya/IdeaProjects/UTBotJava/utbot-junit-contest/build/utbot-usvm-runtool/utbot-usvm-tool.jar"
//        val classpath = System.getProperty("java.class.path")
//        val classpath = ""
        val classpathFiles = classpath.split(File.pathSeparator)
            .filter { it.isNotEmpty() }
            .map { File(it) }
        jacodb {
            loadByteCode(classpathFiles)
        }.use { db ->
            db.classpath(classpathFiles, listOf(UnknownClassMethodsAndFields, UnknownClasses)).use { cp ->
//                var edges = cp.sampleClasses()
//                var edges = cp.allClasses()
//                var edges = cp.nonSunClasses()
//                var edges = cp.utBotClasses()
                var edges = cp.utBotAndLibClasses()
                    .flatMap { it.declaredMethods.stream() }
//                  var edges = Stream.of(cp.findClass("org.utbot.python.PythonTestGenerationProcessor").declaredMethods.first { it.name == "collectImports" })
                    .flatMap { method ->
                        val edges = mutableListOf<PtEdge>()
                        method.instList.forEach { inst ->
                            resolveJcInst(method, inst, edges)
                        }
                        if (!method.isPrivate && !method.isStatic) {
                            method.overriddenMethods.forEach { overriddenMethod ->
                                edges.add(PtAssignEdge(PtThis(method, -1), PtThis(overriddenMethod, -1)))
                                method.parameters.zip(overriddenMethod.parameters).forEachIndexed { i, (param, overriddenParam) ->
                                    edges.add(PtAssignEdge(
                                        PtArg(method, -1, i, cp.findType(param.type.typeName)),
                                        PtArg(overriddenMethod, -1, i, cp.findType(overriddenParam.type.typeName))
                                    ))
                                }
                                edges.add(PtAssignEdge(PtReturn(overriddenMethod, -1), PtReturn(method, -1)))
                            }
                        }
                        edges.stream()
                    }
                    .distinct()
                    .toList()

                println("original")
                printStats(edges)

                edges = edges.filterNot { it is PtAllocEdge && (it.rhs as? PtAllocVertex)?.expr is JcConstant }
//                edges = simplifyOutgoing(edges)

                println("simplified")
                printStats(edges)

                val vertexIdGenerator = NonConcurrentIdGenerator<PtVertex>()
                val fieldIdGenerator = NonConcurrentIdGenerator<PtField>()

                vertexIdGenerator.generateId(PtStaticContextVertex(cp))

                File("mined_graph.txt").printWriter().buffered().use { writer ->
                    edges
                        .forEach { edge ->
                            writer.append(vertexIdGenerator.generateId(edge.lhs).toString())
                            writer.append(' ')
                            writer.append(
                                when (edge) {
                                    is PtAllocEdge -> "alloc"
                                    is PtAssignEdge -> "assign"
                                    is PtLoadEdge -> "load_${fieldIdGenerator.generateId(edge.field)}"
                                    is PtStoreEdge -> "store_${fieldIdGenerator.generateId(edge.field)}"
                                }
                            )
                            writer.append(' ')
                            writer.append(vertexIdGenerator.generateId(edge.rhs).toString())
                            writer.newLine()
                        }
                }

                val typeIdGenerator = NonConcurrentIdGenerator<JcType>()
                File("types.txt").printWriter().buffered().use { writer ->
                    vertexIdGenerator.idCache.entries
                        .forEach {
                            writer.append(it.value.toString())
                            writer.append(" ")
                            writer.append(typeIdGenerator.generateId(it.key.type.toRaw()).toString())
                            writer.newLine()
                        }
                }
                File("sup_types.txt").printWriter().buffered().use { writer ->
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

                vertexIdGenerator.writeMappings("vertex_mappings.txt")
                fieldIdGenerator.writeMappings("field_mappings.txt")
                typeIdGenerator.writeMappings("type_mappings.txt") { it.typeName }
            }
        }
    })
}

private fun simplifyOutgoing(edges: List<PtEdge>): List<PtEdge> {
    val outgoingEdges = getOutgoingEdgesMap(edges)

    val vertexReplacements: MutableMap<PtVertex, PtVertex?> = outgoingEdges.mapNotNull { (vertex, outEdges) ->
        when (val outEdge = outEdges.singleOrNull()) {
            is PtAssignEdge -> vertex to outEdge.rhs
            else -> null
        }
    }.toMap().toMutableMap()

    vertexReplacements.forEach { (vertex, replacement) ->
        val path = mutableSetOf(vertex)
        var cur = replacement
        while (cur != null && cur in vertexReplacements) {
            path.add(cur)
            cur = vertexReplacements[cur]
            if (cur in path)
                cur = null // loop
        }
        val compositeVertex = PtCompositeVertex(path) // FIXME there are no outgoing edges from composite vertex, in Python version last path element is used in place of composite vertex
        path.forEach { vertexReplacements[it] = compositeVertex }
    }

    val newEdges = mutableListOf<PtEdge>()
    outgoingEdges.forEach { (vertex, outEdges) ->
        if (vertex !in vertexReplacements) {
            outEdges.forEach { edge ->
                newEdges.add(
                    vertexReplacements[edge.rhs]?.let { replacement -> edge.copy(rhs = replacement) } ?: edge
                )
            }
        }
    }
    return newEdges
}

private fun getOutgoingEdgesMap(edges: List<PtEdge>): Map<PtVertex, List<PtEdge>> {
    val outgoingEdges = mutableMapOf<PtVertex, MutableList<PtEdge>>()
    edges.forEach {
        outgoingEdges.getOrPut(it.lhs) { mutableListOf() }.add(it)
    }
    return outgoingEdges
}

private fun printStats(edges: MutableList<PtEdge>) {
    println(edges.size)
    println()
    val edgeGroups = edges.groupBy { it::class.java }
    edgeGroups.forEach { (k, v) -> println("$k: ${v.size} (${v.size * 100 / edges.size}%)") }
    println()
    val allocEdges = edgeGroups[PtAllocEdge::class.java]
    allocEdges
        ?.groupBy { ((it as PtAllocEdge).rhs as? PtAllocVertex)?.let { v -> v.expr::class.java } }
        ?.forEach { (k, v) -> println("$k: ${v.size} (${v.size * 100 / allocEdges.size}%)") }
    println()
}

val JcMethod.overriddenMethods get() =
    if (isPrivate) emptyList()
    else (enclosingClass.interfaces + listOfNotNull(enclosingClass.superClass)).mapNotNull { superType ->
        superType.findMethodOrNull(name, description)?.takeIf { it !is JcUnknownMethod }
    }

private fun JcClasspath.sampleClasses(): Stream<JcClassOrInterface> =
    Stream.of(findClass<Sample>())

private fun JcClasspath.sampleJdkClasses(): Stream<JcClassOrInterface> =
    allClasses().filter { it.name.startsWith("java.util.stream") }

private fun JcClasspath.utBotClasses(): Stream<JcClassOrInterface> =
    allClasses().filter { it.name.startsWith("org.utbot.framework") }

private fun JcClasspath.utBotAndLibClasses(): Stream<JcClassOrInterface> =
    allClasses().filter { clazz ->
        listOf("org.utbot.", "java.lang.", "java.util.", "kotlin.", "kotlinx.").any {
            clazz.name.startsWith(it)
        }
    }

private fun JcClasspath.nonSunClasses(): Stream<JcClassOrInterface> =
    allClasses().filter { !it.name.startsWith("com.sun.") && !it.name.startsWith("sun.") }

private fun JcClasspath.allClasses(): Stream<JcClassOrInterface> =
    locations.stream()
        .parallel()
        .flatMap { it.classNames.orEmpty().stream() }
        .flatMap { findClasses(it).stream() }

private fun JcClasspath.javaPackageClasses(): Stream<JcClassOrInterface> =
    allClasses()
        .filter {
            (it.name.startsWith("java.lang") || it.name.startsWith("java.util"))
                    && it.name != "java.lang.Object"
        }

fun resolveJcInst(method: JcMethod, inst: JcInst, edges: MutableList<PtEdge>) {
    when (inst) {
        is JcAssignInst -> edges.add(
            PtAssignEdge(
                lhs = resolveJcExprToPtVertex(method, inst.lineNumber, inst.lhv, edges, handSide = HandSide.LEFT),
                rhs = resolveJcExprToPtVertex(method, inst.lineNumber, inst.rhv, edges, handSide = HandSide.RIGHT),
            )
        )

        is JcCallInst -> resolveJcExprToPtVertex(method, inst.lineNumber, inst.callExpr, edges, handSide = HandSide.RIGHT)
        is JcReturnInst -> inst.returnValue?.let { returnValue ->
            edges.add(PtAssignEdge(
                lhs = PtReturn(method, inst.lineNumber),
                rhs = resolveJcExprToPtVertex(method, inst.lineNumber, returnValue, edges, HandSide.RIGHT)
            ))
        }
    }
}

enum class HandSide {
    LEFT, RIGHT
}

fun resolveJcExprToPtVertex(
    method: JcMethod,
    lineNumber: Int,
    expr: JcExpr,
    edges: MutableList<PtEdge>,
    handSide: HandSide,
): PtVertex = when (expr) {
    // TODO handle casts
    is JcCastExpr -> resolveJcExprToPtVertex(method, lineNumber, expr.operand, edges, handSide)
    is JcArgument -> PtArg(method, lineNumber, expr.index, expr.type)
    is JcLocalVar -> PtLocalVar(method, lineNumber, expr.name, expr.type)
    is JcThis -> PtThis(method, lineNumber)
    is JcComplexValue -> {
        val (instance, field) = when (expr) {
            is JcFieldRef -> expr.instance?.let {
                resolveJcExprToPtVertex(
                    method,
                    lineNumber,
                    it,
                    edges,
                    handSide
                )
            } to PtSimpleField(expr.field.field)

            is JcArrayAccess -> resolveJcExprToPtVertex(method, lineNumber, expr.array, edges, handSide) to PtArrayElementField
            else -> error("Unexpected expression type ${expr::class.java}")
        }
        PtTempVertex(expr.type, lineNumber).also { tempVertex ->
            edges.add(
                when (handSide) {
                    HandSide.RIGHT -> PtLoadEdge(tempVertex, instance, field)
                    HandSide.LEFT -> PtStoreEdge(instance, field, tempVertex)
                }
            )
        }
    }

    is JcCallExpr -> {
        require(handSide == HandSide.RIGHT)
        expr.args.forEachIndexed { i, arg ->
            edges.add(
                PtAssignEdge(
                    lhs = PtArg(
                        expr.method.method,
                        lineNumber,
                        i,
                        runCatching { expr.method.parameters.getOrNull(i)?.type }.getOrElse {
                            println(it) // TODO logger
                            null
                        } ?: return@forEachIndexed
                    ),
                    rhs = resolveJcExprToPtVertex(method, lineNumber, arg, edges, handSide)
                )
            )
        }
        if (expr is JcInstanceCallExpr) {
            edges.add(
                PtAssignEdge(
                    lhs = PtThis(expr.method.method, lineNumber),
                    rhs = resolveJcExprToPtVertex(method, lineNumber, expr.instance, edges, handSide)
                )
            )
        }
        PtReturn(expr.method.method, lineNumber)
    }

    else -> {
        require(handSide == HandSide.RIGHT)
        PtTempVertex(expr.type, lineNumber).also { tempVertex ->
            edges.add(PtAllocEdge(tempVertex, PtAllocVertex(expr, lineNumber, method, expr.type)))
        }
    }
}

sealed interface PtVertex {
    val type: JcType
}

sealed interface PtLocal : PtVertex

data class PtLocalVar(val method: JcMethod, val lineNumber: Int, val name: String, override val type: JcType) : PtLocal {
    override fun toString(): String {
        return "PtLocalVar(method=$method, name='$name', type=${type.typeName}, lineNumber=$lineNumber)"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as PtLocalVar

        if (method != other.method) return false
        if (name != other.name) return false

        return true
    }

    override fun hashCode(): Int {
        var result = method.hashCode()
        result = 31 * result + name.hashCode()
        return result
    }
}

data class PtArg(val method: JcMethod, val lineNumber: Int, val index: Int, override val type: JcType) : PtLocal {
    override fun toString(): String {
        return "PtArg(method=$method, index=$index, type=${type.typeName}, lineNumber=$lineNumber)"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as PtArg

        if (method != other.method) return false
        if (index != other.index) return false

        return true
    }

    override fun hashCode(): Int {
        var result = method.hashCode()
        result = 31 * result + index
        return result
    }
}

data class PtThis(val method: JcMethod, val lineNumber: Int) : PtLocal {
    override val type: JcType get() = method.enclosingClass.toType()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as PtThis

        return method == other.method
    }

    override fun hashCode(): Int {
        return method.hashCode()
    }
}

data class PtReturn(
    val method: JcMethod,
    val lineNumber: Int
) : PtVertex {
    override val type: JcType get() = method.enclosingClass.classpath.findType(method.returnType.typeName)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as PtReturn

        return method == other.method
    }

    override fun hashCode(): Int {
        return method.hashCode()
    }


}

class PtTempVertex(override val type: JcType, val lineNumber: Int) : PtVertex {
    override fun equals(other: Any?): Boolean {
        return this === other
    }

    override fun hashCode(): Int {
        return System.identityHashCode(this)
    }

    override fun toString(): String {
        return "PtTempVertex(type=${type.typeName}, lineNumber=$lineNumber)"
    }
}

class PtAllocVertex(val expr: JcExpr, val lineNumber: Int, val method: JcMethod, override val type: JcType) : PtVertex {
    override fun equals(other: Any?): Boolean {
        return this === other
    }

    override fun hashCode(): Int {
        return System.identityHashCode(this)
    }

    override fun toString(): String {
        return "PtAllocVertex(expr=$expr, method=$method, type=${type.typeName}, lineNumber=$lineNumber)"
    }
}
data class PtStaticContextVertex(val classpath: JcClasspath) : PtVertex {
    override val type: JcType
        get() = classpath.void
}

data class PtCompositeVertex(
    val vertices: Set<PtVertex>
) : PtVertex {
    override val type: JcType
        get() = vertices
            .map { it.type.toRaw() }
            .maxBy { it.allRawSuperHierarchySequence().count() }

    override fun toString(): String {
        return "PtCompositeVertex(vertices=$vertices, type=${type.typeName})"
    }
}

sealed interface PtField

data class PtSimpleField(val field: JcField) : PtField
data object PtArrayElementField : PtField

sealed interface PtEdge {
    val lhs: PtVertex
    val rhs: PtVertex
}

data class PtAssignEdge(
    override val lhs: PtVertex,
    override val rhs: PtVertex,
) : PtEdge

data class PtLoadEdge(
    override val lhs: PtVertex,
    val rhsInstance: PtVertex?,
    val field: PtField,
) : PtEdge {
    override val rhs: PtVertex
        get() = rhsInstance ?: PtStaticContextVertex(lhs.type.classpath)
}

data class PtStoreEdge(
    val lhsInstance: PtVertex?,
    val field: PtField,
    override val rhs: PtVertex,
) : PtEdge {
    override val lhs: PtVertex
        get() = lhsInstance ?: PtStaticContextVertex(rhs.type.classpath)
}

data class PtAllocEdge(
    override val lhs: PtVertex,
    override val rhs: PtVertex,
) : PtEdge

fun PtEdge.copy(lhs: PtVertex = this.lhs, rhs: PtVertex = this.rhs) = when (this) {
    is PtAllocEdge -> PtAllocEdge(lhs, rhs)
    is PtAssignEdge -> PtAssignEdge(lhs, rhs)
    is PtLoadEdge -> PtLoadEdge(lhs, rhs, field)
    is PtStoreEdge -> PtStoreEdge(lhs, field, rhs)
}