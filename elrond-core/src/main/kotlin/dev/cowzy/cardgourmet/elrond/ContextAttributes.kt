package dev.cowzy.cardgourmet.elrond

class ContextKey<T : Any>(val name: String) {
    override fun toString(): String = name
}

class ContextAttributes private constructor(private val values: Map<ContextKey<*>, Any>) {

    @Suppress("UNCHECKED_CAST")
    operator fun <T : Any> get(key: ContextKey<T>): T? = values[key] as T?

    operator fun contains(key: ContextKey<*>): Boolean = values.containsKey(key)

    fun <T : Any> with(key: ContextKey<T>, value: T?): ContextAttributes {
        val newValues = values.toMutableMap()
        if (value == null) newValues.remove(key) else newValues[key] = value
        return ContextAttributes(newValues)
    }

    companion object {
        val EMPTY = ContextAttributes(emptyMap())

        fun <T : Any> of(key: ContextKey<T>, value: T?): ContextAttributes = EMPTY.with(key, value)
    }
}
