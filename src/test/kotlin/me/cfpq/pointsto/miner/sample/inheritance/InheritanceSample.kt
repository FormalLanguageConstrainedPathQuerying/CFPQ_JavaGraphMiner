package me.cfpq.pointsto.miner.sample.inheritance

abstract class Animal(
    protected val name: String
) {
    abstract fun copy(name: String): Animal
}

class Cat(name: String) : Animal(name) {
    // NOTE: A synthetic bridge method returning `Animal` is automatically created,
    // leading to the generated graph containing pairs of vertices that look identical but differ in types.
    override fun copy(name: String): Cat = Cat(name)
}
