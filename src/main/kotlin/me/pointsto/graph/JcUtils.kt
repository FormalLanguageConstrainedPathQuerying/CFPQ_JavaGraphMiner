package me.pointsto.graph

import org.jacodb.api.*
import org.jacodb.api.ext.*

fun JcType.toRaw(): JcType {
    return when (this) {
        is JcArrayType -> this.classpath.arrayTypeOf(elementType.toRaw())
        is JcRefType -> this.jcClass.toType()
        else -> this
    }
}

fun JcType.allRawSuperHierarchySequence(): Sequence<JcType> = toRaw().run {
    return when (this) {
        is JcRefType -> sequenceOf(this) + this.jcClass.allSuperHierarchySequence.distinct().map { it.toType() }
        else -> sequenceOf(this)
    }
}
