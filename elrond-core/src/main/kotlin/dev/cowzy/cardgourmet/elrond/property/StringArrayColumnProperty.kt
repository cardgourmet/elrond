package dev.cowzy.cardgourmet.elrond.property

import dev.cowzy.kuery.query.ConcreteWhereQueryBuilder
import dev.cowzy.kuery.query.WhereQueryBuilder
import dev.cowzy.kuery.query.orWhereRaw
import dev.cowzy.kuery.reflection.placeholder
import dev.cowzy.kuery.reflection.table
import dev.cowzy.cardgourmet.elrond.*
import dev.cowzy.cardgourmet.elrond.descriptor.PropertyDescriptor
import dev.cowzy.cardgourmet.elrond.values.ValueProvider
import kotlin.reflect.KProperty1

class StringArrayColumnProperty(
    private vararg val columns: KProperty1<*, *>,
    valueProvider: ValueProvider<String>? = null,
    mappings: Map<String, String>? = null,
    private val inverted: Boolean = false,
    descriptor: PropertyDescriptor,
    key: String? = null,
) : SearchQueryProperty<String>(
    supportedOperators = arrayOf(SearchQueryOperator.CONTAINS),
    affectedTables = columns.map { it.table() }.distinct().toTypedArray(),
    descriptor = descriptor,
    key = key
) {

    override val valueDefinition = QueryValueDefinition {
        StringValue::class {
            mappings(mappings)
            values(valueProvider)
        }
    }

    override suspend fun <T : WhereQueryBuilder<T>> applyCondition(
        builder: T,
        operator: SearchQueryOperator,
        value: String
    ) {
        val apply: (ConcreteWhereQueryBuilder) -> Unit = {
            columns.forEach { column ->
                it.orWhereRaw(column, "@>", "ARRAY[${column.placeholder()}]::text[]") { stmt, index ->
                    stmt.setString(index.getAndIncrement(), value)
                }
            }
        }

        if (inverted) {
            builder.whereNot(apply)
        } else {
            builder.where(apply)
        }
    }

}