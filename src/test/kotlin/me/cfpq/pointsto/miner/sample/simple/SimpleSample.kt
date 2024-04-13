@file:Suppress("unused", "MayBeConstant")

package me.cfpq.pointsto.miner.sample.simple

val PI = 3.14

class ExpressionsSample {
    lateinit var fieldWithAccessors: StringBuilder

    fun <T> choose(returnFirst: Boolean, first: T, second: T): T =
        if (returnFirst) first else second

    fun asString(obj: CharSequence): String = obj as String

    fun accessArray(array: Array<Any>, index: Int): Any = array[index]

    fun accessPrimitiveArray(array: IntArray, index: Int): Int = array[index]
}
