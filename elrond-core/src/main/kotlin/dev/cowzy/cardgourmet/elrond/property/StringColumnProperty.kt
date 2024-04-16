package dev.cowzy.cardgourmet.elrond.property

import dev.cowzy.kuery.query.SelectQueryBuilder
import dev.cowzy.kuery.reflection.columnName
import dev.cowzy.kuery.reflection.table
import dev.cowzy.cardgourmet.commons.toSimpleString
import dev.cowzy.cardgourmet.elrond.*
import dev.cowzy.cardgourmet.elrond.descriptor.PropertyDescriptor
import dev.cowzy.cardgourmet.elrond.values.ValueProvider
import kotlin.reflect.KProperty1

open class StringColumnProperty(
    protected val column: KProperty1<*, *>,
    private val valueProvider: ValueProvider<String>? = null,
    private val mappings: Map<String, String> = emptyMap(),
    private val simpleColumn: KProperty1<*, *>? = null,
    mapContainsToEquals: Boolean = false,
    descriptor: PropertyDescriptor,
) : StringSearchQueryProperty(
    simplify = simpleColumn != null,
    affectedTables = arrayOf(column.table()),
    descriptor = descriptor,
    mapContainsToEquals = mapContainsToEquals
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
                    simpleColumn == null || value.exact -> value
                    else -> StringValue(value.value.toSimpleString())
                }
            }

            match { value ->
                valueProvider?.getValues()?.any { it.equals(value.value.toString(), ignoreCase = true) } ?: true
            }

            display { value, _, _ ->
                val displayValue = valueProvider?.getValues()?.first {
                    it.equals(value.value.toString(), ignoreCase = true)
                } ?: value.value.toString()

                return@display "\"$displayValue\""
            }
        }

        RegexValue::class {
            transform { it }
        }
    }

    override fun applyProperty(builder: SelectQueryBuilder) = Unit

    override fun getRawSql(value: QueryValue<*>) = when {
        simpleColumn != null && value is StringValue && !value.exact -> simpleColumn.columnName()
        else -> column.columnName()
    }

}