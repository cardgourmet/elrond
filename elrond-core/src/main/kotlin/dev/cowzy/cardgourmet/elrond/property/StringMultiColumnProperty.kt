package dev.cowzy.cardgourmet.elrond.property

import dev.cowzy.kuery.query.WhereQueryBuilder
import dev.cowzy.kuery.reflection.columnName
import dev.cowzy.kuery.reflection.table
import dev.cowzy.cardgourmet.commons.toSimpleString
import dev.cowzy.cardgourmet.elrond.*
import dev.cowzy.cardgourmet.elrond.descriptor.PropertyDescriptor
import kotlin.reflect.KProperty1

class StringMultiColumnProperty(
    private vararg val columns: KProperty1<*, *>,
    private val supportedValues: Array<String>? = null,
    private val mappings: Map<String, String> = emptyMap(),
    private val mapContainsToEquals: Boolean = false,
    descriptor: PropertyDescriptor,
) : SearchQueryProperty<QueryValue<*>>(
    supportedOperators = stringQueryOperators,
    affectedTables = columns.map { it.table() }.toTypedArray(),
    descriptor = descriptor
) {

    override val valueDefinition = QueryValueDefinition<QueryValue<*>> {
        StringValue::class {
            transform {
                val value = mappings.entries.find { (key, _) ->
                    key.equals(it.value, ignoreCase = true)
                }?.let { match ->
                    StringValue(match.value)
                } ?: it

                return@transform when {
                    value.exact -> value
                    else -> StringValue(value.value.toSimpleString())
                }
            }

            match { value ->
                supportedValues?.any { it.equals(value.value.toString(), ignoreCase = true) } ?: true
            }

            display { value, _, _ ->
                val displayValue = supportedValues?.first {
                    it.equals(value.value.toString(), ignoreCase = true)
                } ?: value.value.toString()

                return@display "\"$displayValue\""
            }
        }

        RegexValue::class {
            transform { it }
        }
    }

    override suspend fun <T : WhereQueryBuilder<T>> applyCondition(
        builder: T,
        operator: SearchQueryOperator,
        value: QueryValue<*>
    ) {
        val mappedOperator = when {
            mapContainsToEquals && operator == SearchQueryOperator.CONTAINS -> SearchQueryOperator.EQUALS
            else -> operator
        }

        columns.forEach { column ->
            builder.orWhere { inner ->
                when (value) {
                    is StringValue -> when (mappedOperator) {
                        SearchQueryOperator.EQUALS -> inner.where("UPPER(${column.columnName()})", "=", value = value.value.uppercase())
                        SearchQueryOperator.CONTAINS -> inner.where(column, "ILIKE", value = "%${value.value}%")
                        else -> throw IllegalStateException("Unsupported operator: $mappedOperator")
                    }

                    is RegexValue -> {
                        val pattern = when (operator) {
                            SearchQueryOperator.EQUALS -> value.value.pattern.toFullMatchRegex()
                            SearchQueryOperator.CONTAINS -> value.value.pattern
                            else -> throw IllegalStateException("Unsupported operator: $operator")
                        }

                        inner.where(column, "~*", value = pattern)
                    }

                    else -> throw IllegalStateException("Unsupported value type: ${value::class.simpleName}")
                }
            }
        }
    }

}