package me.cfpq.pointsto.miner

import org.jacodb.api.JcClasspath
import org.jacodb.api.JcField
import org.jacodb.api.JcMethod
import org.jacodb.api.JcType
import org.jacodb.api.cfg.JcExpr
import org.jacodb.api.ext.objectType
import org.jacodb.api.ext.toType
import org.jacodb.api.ext.void

sealed interface PtVertex {
    val type: JcType
}

sealed interface PtLocal : PtVertex

data class PtLocalVar(
    val method: JcMethod,
    val lineNumber: Int,
    val name: String,
    override val type: JcType
) : PtLocal {
    override fun toString(): String {
        return "PtLocalVar(method=$method, name='$name', type=${type.typeName}, lineNumber=$lineNumber)"
    }

    override fun equals(other: Any?): Boolean {
        // no line number check here, because analysis is flow-insensitive
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

data class PtArg(
    val method: JcMethod,
    val index: Int,
) : PtLocal {
    override val type: JcType get() = method.parameters.getOrNull(index)?.let {
        method.enclosingClass.classpath.findTypeOrNull(it.type.typeName)
    } ?: method.enclosingClass.classpath.objectType
}

data class PtThis(
    val method: JcMethod
) : PtLocal {
    override val type: JcType get() = method.enclosingClass.toType()
}

data class PtReturn(
    val method: JcMethod,
) : PtVertex {
    override val type: JcType get() =
        method.enclosingClass.classpath.findTypeOrNull(method.returnType.typeName) ?:
            method.enclosingClass.classpath.objectType
}

class PtTempVertex(
    override val type: JcType,
    val lineNumber: Int
) : PtVertex {
    override fun toString(): String {
        return "PtTempVertex(type=${type.typeName}, lineNumber=$lineNumber)"
    }

    // No hashCode() or equals(), because every instance represents its own vertex
}

class PtAllocVertex(
    val expr: JcExpr,
    val lineNumber: Int,
    val method: JcMethod,
    override val type: JcType
) : PtVertex {
    override fun toString(): String {
        return "PtAllocVertex(expr=$expr, method=$method, type=${type.typeName}, lineNumber=$lineNumber)"
    }

    // No hashCode() or equals(), because every instance represents its own vertex
}

/**
 * Auxiliary vertex used to represent owner of all static fields
 */
data class PtStaticContextVertex(val classpath: JcClasspath) : PtVertex {
    override val type: JcType
        get() = classpath.void

    override fun toString(): String = "PtStaticContextVertex"
}

sealed interface PtField

data class PtSimpleField(val field: JcField) : PtField

/**
 * Auxiliary fields used to represent array element access
 */
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

fun PtEdge.copy(lhs: PtVertex, rhs: PtVertex): PtEdge = when (this) {
    is PtAllocEdge -> PtAllocEdge(lhs, rhs)
    is PtAssignEdge -> PtAssignEdge(lhs, rhs)
    is PtLoadEdge -> PtLoadEdge(lhs, rhs, field)
    is PtStoreEdge -> PtStoreEdge(lhs, field, rhs)
}
