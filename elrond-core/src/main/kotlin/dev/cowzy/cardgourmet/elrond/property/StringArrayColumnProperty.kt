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
    override val valueProvider: ValueProvider<String>? = null,
    override val allowAnyValue: Boolean = false,
    override val mappings: Map<String, String> = emptyMap(),
    private val inverted: Boolean = false,
    descriptor: PropertyDescriptor
) : SearchQueryProperty<String>(
    supportedOperators = arrayOf(SearchQueryOperator.CONTAINS),
    affectedTables = columns.map { it.table() }.distinct().toTypedArray(),
    descriptor = descriptor
), ValueProvided, Mappable<String> {

    override val valueDefinition = QueryValueDefinition {
        StringValue::class {
            transform { value ->
                val mappedValue = mappings.entries.find {
                    it.key.equals(value.value, ignoreCase = true)
                }?.value ?: value.value

                matchValue(mappedValue)
            }
        }
    }

    override suspend fun <T : WhereQueryBuilder<T>> applyCondition(
        builder: T,
        operator: SearchQueryOperator,
        value: String
    ) {
        val matchingValue = matchValue(value) ?: throw IllegalStateException("Unsupported value: $value")

        val apply: (ConcreteWhereQueryBuilder) -> Unit = {
            columns.forEach { column ->
                it.orWhereRaw(column, "@>", "ARRAY[${column.placeholder()}]::text[]") { stmt, index ->
                    stmt.setString(index.getAndIncrement(), matchingValue)
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