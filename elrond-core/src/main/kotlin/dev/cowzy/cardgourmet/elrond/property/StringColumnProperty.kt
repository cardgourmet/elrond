package dev.cowzy.cardgourmet.elrond.property

import dev.cowzy.cardgourmet.commons.toSimpleString
import dev.cowzy.cardgourmet.elrond.QueryValue
import dev.cowzy.cardgourmet.elrond.QueryValueDefinition
import dev.cowzy.cardgourmet.elrond.RegexValue
import dev.cowzy.cardgourmet.elrond.StringValue
import dev.cowzy.cardgourmet.elrond.descriptor.PropertyDescriptor
import dev.cowzy.cardgourmet.elrond.values.MappingProvider
import dev.cowzy.cardgourmet.elrond.values.ValueProvider
import dev.cowzy.kuery.query.SelectQueryBuilder
import dev.cowzy.kuery.reflection.columnName
import dev.cowzy.kuery.reflection.table
import kotlin.reflect.KProperty1

open class StringColumnProperty(
    protected val column: KProperty1<*, *>,
    valueProvider: ValueProvider<String>? = null,
    useStrictValues: Boolean = false,
    mappingProvider: MappingProvider<String, String>? = null,
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
            mappingsWithOperator(mappingProvider, ::StringValue)
            values(valueProvider, useStrictValues)

            transform {
                return@transform when {
                    simpleColumn == null || it.exact -> it
                    else -> StringValue(it.value.toSimpleString())
                }
            }

            display { value, _, _ -> "\"${value.value}\"" }
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