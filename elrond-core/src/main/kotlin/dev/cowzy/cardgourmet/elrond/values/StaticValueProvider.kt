package dev.cowzy.cardgourmet.elrond.values

class StaticValueProvider<T>(private val values: Set<T>) : ValueProvider<T> {

    constructor(values: Array<T>) : this(values.toSet())

    override suspend fun getValues() = values

}