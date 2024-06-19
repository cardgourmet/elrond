package dev.cowzy.cardgourmet.elrond.values

import dev.cowzy.cardgourmet.commons.database.SqlDatabasePool
import java.sql.Connection

class ValueProvider<T : Any>(
    val strictValues: Boolean,
    ttl: Long = 3600,
    applyValues: suspend (ValueGroup<T>) -> Unit
) {

    private val cache = ValueCache(ttl) {
        val valueGroup = ValueGroup<T>()
        applyValues(valueGroup)
        valueGroup.getValues()
    }

    constructor(
        dbPool: SqlDatabasePool,
        applyValues: List<(Connection, ValueGroup<T>, (T) -> String) -> Unit>,
        displayTransform: (T) -> String,
        strictValues: Boolean,
        ttl: Long = 3600
    ) : this(
        strictValues,
        ttl,
        { valueGroup -> dbPool.use { connection -> applyValues.forEach { it(connection, valueGroup, displayTransform) } } }
    )

    suspend fun getValues(language: String? = null): Iterable<ProvidedValue<T>> = cache.getAll().apply {
        this.filter { language == null || it.language == language }
    }

    suspend fun getValues(filter: String, language: String?): Iterable<ProvidedValue<T>> {
        return getValues()
            .filter { language == null || it.language == language }
            .filter { it.input.contains(filter, ignoreCase = true) || it.aliases.any { alias -> alias.contains(filter, ignoreCase = true) } }
    }

    suspend fun findValue(value: String) = cache.find(value.trim())

}

fun <Input : Any, Output : Any> ValueProvider<Input>.withTransform(transform: (Input) -> Output): ValueProvider<Output> {
    return ValueProvider(
        this.strictValues,
        -1L,
    ) { valueGroup ->
        this.getValues().map {
            ProvidedValue(
                input = it.input,
                aliases = it.aliases,
                type = it.type,
                resolvesTo = ResolvedValue(
                    value = transform(it.resolvesTo.value),
                    display = it.resolvesTo.display,
                    operator = it.resolvesTo.operator
                ),
                language = it.language
            )
        }.forEach(valueGroup::add)
    }
}
