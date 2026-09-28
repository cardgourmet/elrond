package dev.cowzy.cardgourmet.elrond.values

import dev.cowzy.cardgourmet.commons.database.SqlDatabasePool
import dev.cowzy.cardgourmet.elrond.ContextAttributes
import java.sql.Connection

class StaticValueProvider<T : Any>(
    strictValues: Boolean,
    ttl: Long = 3600,
    applyValues: suspend (ValueGroup<T>) -> Unit
) : ValueProvider<T>(strictValues) {

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

    override suspend fun getValues(attributes: ContextAttributes, language: String?): Iterable<ProvidedValue<T>> {
        return cache.getAll().filter { language == null || it.languages.isEmpty() || it.languages.contains(language) }
    }

    override suspend fun findValue(attributes: ContextAttributes, value: String): ProvidedValue<T>? = cache.find(value.trim())

}