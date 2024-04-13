package me.cfpq.pointsto.miner

import org.jacodb.api.*
import org.jacodb.api.ext.*
import org.jacodb.impl.features.classpaths.JcUnknownMethod
import java.util.stream.Stream

fun JcType.toRaw(): JcType {
    return when (this) {
        is JcArrayType -> this.classpath.arrayTypeOf(elementType.toRaw())
        is JcRefType -> this.jcClass.toType()
        else -> this
    }
}

fun JcType.allRawSuperHierarchySequence(): Sequence<JcType> = toRaw().run {
    return when (this) {
        is JcRefType -> (sequenceOf(this, classpath.objectType)
                + this.jcClass.allSuperHierarchySequence.distinct().map { it.toType() }).distinct()
        else -> sequenceOf(this)
    }
}

val JcMethod.overriddenMethods
    get() =
        if (isPrivate || isStatic || isConstructor) emptyList()
        // NOTE: deep hierarch isn't needed here, because `findMethodOrNull` already searches across entire hierarchy
        else (enclosingClass.interfaces + listOfNotNull(enclosingClass.superClass)).mapNotNull { superType ->
            superType.findMethodOrNull(name, description)?.takeIf { it !is JcUnknownMethod }
        }

fun JcClasspath.allClasses(): Stream<JcClassOrInterface> =
    locations.stream()
        .sequential() // TODO make parallel when better performance is needed, requires `ConcurrentIdGenerator`
        .flatMap { it.classNames.orEmpty().stream() }
        .flatMap { findClasses(it).stream() }
