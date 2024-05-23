package dev.cowzy.cardgourmet.elrond.property

import dev.cowzy.cardgourmet.commons.toSimpleString
import dev.cowzy.cardgourmet.elrond.*
import dev.cowzy.cardgourmet.elrond.descriptor.PropertyDescriptor
import dev.cowzy.kuery.query.WhereQueryBuilder
import kotlin.reflect.KClass

abstract class StringSearchQueryProperty(
    affectedTables: Array<KClass<*>>,
    private val simplify: Boolean = false,
    private val mapContainsToEquals: Boolean = false,
    descriptor: PropertyDescriptor,
) : SearchQueryProperty<QueryValue<*>>(
    supportedOperators = stringQueryOperators,
    affectedTables = affectedTables,
    descriptor = descriptor
) {

    override val valueDefinition = QueryValueDefinition<QueryValue<*>> {
        StringValue::class {
            transform {
                when {
                    simplify && !it.exact -> StringValue(it.value.toSimpleString())
                    else -> it
                }
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

        when (value) {
            is StringValue -> when (mappedOperator) {
                SearchQueryOperator.EQUALS -> builder.where("UPPER(${getRawSql(value)})", "=", value = value.value.uppercase())
                SearchQueryOperator.CONTAINS -> builder.where(getRawSql(value), "ILIKE", value = "%${value.value}%")
                else -> throw IllegalStateException("Unsupported operator: $mappedOperator")
            }

            is RegexValue -> {
                val pattern = when (operator) {
                    SearchQueryOperator.EQUALS -> value.value.pattern.toFullMatchRegex()
                    SearchQueryOperator.CONTAINS -> value.value.pattern
                    else -> throw IllegalStateException("Unsupported operator: $operator")
                }

                builder.where(getRawSql(value), "~*", value = pattern)
            }

            else -> throw IllegalStateException("Unsupported value type: ${value::class.simpleName}")
        }
    }

    abstract fun getRawSql(value: QueryValue<*>): String

}
