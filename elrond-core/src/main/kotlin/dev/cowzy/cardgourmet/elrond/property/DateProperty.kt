package dev.cowzy.cardgourmet.elrond.property

import dev.cowzy.cardgourmet.elrond.QueryValueDefinition
import dev.cowzy.cardgourmet.elrond.SearchQueryOperator
import dev.cowzy.cardgourmet.elrond.StringValue
import dev.cowzy.cardgourmet.elrond.descriptor.NumericDescriptor
import dev.cowzy.cardgourmet.elrond.numericQueryOperators
import dev.cowzy.kuery.query.WhereQueryBuilder
import dev.cowzy.kuery.reflection.table
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.reflect.KProperty1

class DateProperty(
    private val column: KProperty1<*, *>,
    propertyKey: String,
) : SearchQueryProperty<String>(
    supportedOperators = numericQueryOperators,
    affectedTables = arrayOf(column.table()),
    descriptor = NumericDescriptor(propertyKey)
) {

    private val dateRegex = Regex("^(\\d{4})(?:-(\\d{1,2}))?(?:-(\\d{1,2}))?$")

    override val valueDefinition = QueryValueDefinition<String> {
        StringValue::class {
            transform { it.value }
            match { it.matches(dateRegex) }
            display { it, _, _ -> "`$it`" }
        }
    }

    override suspend fun <T : WhereQueryBuilder<T>> applyCondition(
        builder: T,
        operator: SearchQueryOperator,
        value: String
    ) {
        val match = dateRegex.find(value) ?: throw IllegalStateException("Invalid date")
        val day = match.groupValues[3].ifEmpty { null }?.toInt()

        val condition: (WhereQueryBuilder<*>) -> Unit = { inner ->
            when (operator) {
                SearchQueryOperator.CONTAINS, SearchQueryOperator.EQUALS -> when {
                    day != null -> inner.where(column, value)
                    else -> inner
                        .where(column, ">=", getLowerBound(operator, value))
                        .whereRaw(column, "<", "'${getUpperBound(operator, value)}'")
                }

                SearchQueryOperator.GREATER_THAN_OR_EQUALS -> inner.whereRaw(column, ">=", "'${getLowerBound(operator, value)}'")

                SearchQueryOperator.GREATER_THAN -> when {
                    day != null -> inner.where(column, ">", value)
                    else -> inner.whereRaw(column, ">=", "'${getLowerBound(operator, value)}'")
                }

                SearchQueryOperator.LESS_THAN_OR_EQUALS -> when {
                    day != null -> inner.where(column, "<=", value)
                    else -> inner.whereRaw(column, "<", "'${getUpperBound(operator, value)}'")
                }

                SearchQueryOperator.LESS_THAN -> inner.whereRaw(column, "<", "'${getUpperBound(operator, value)}'")
            }
        }

        builder.where(condition)
    }

    private fun getLowerBound(operator: SearchQueryOperator, value: String): String {
        val match = dateRegex.find(value)!!
        val year = match.groupValues[1].toInt()
        val month = match.groupValues[2].ifEmpty { null }?.toInt()
        val date = match.groupValues[3].ifEmpty { null }?.toInt()

        return if (date != null) {
            value
        } else {
            when (operator) {
                SearchQueryOperator.CONTAINS, SearchQueryOperator.EQUALS -> when {
                    month != null -> LocalDate.of(year, month, 1).format(DateTimeFormatter.ISO_DATE)
                    else -> LocalDate.of(year, 1, 1).format(DateTimeFormatter.ISO_DATE)
                }

                SearchQueryOperator.LESS_THAN, SearchQueryOperator.GREATER_THAN_OR_EQUALS -> when {
                    month != null -> LocalDate.of(year, month, 1).format(DateTimeFormatter.ISO_DATE)
                    else -> LocalDate.of(year, 1, 1).format(DateTimeFormatter.ISO_DATE)
                }

                SearchQueryOperator.GREATER_THAN, SearchQueryOperator.LESS_THAN_OR_EQUALS -> when {
                    month != null -> LocalDate.of(year, month + 1, 1).format(DateTimeFormatter.ISO_DATE)
                    else -> LocalDate.of(year + 1, 1, 1).format(DateTimeFormatter.ISO_DATE)
                }
            }
        }
    }

    private fun getUpperBound(operator: SearchQueryOperator, value: String): String {
        val match = dateRegex.find(value)!!
        val year = match.groupValues[1].toInt()
        val month = match.groupValues[2].ifEmpty { null }?.toInt()
        val date = match.groupValues[3].ifEmpty { null }?.toInt()

        return if (date != null) {
            value
        } else {
            when (operator) {
                SearchQueryOperator.CONTAINS, SearchQueryOperator.EQUALS -> when {
                    month != null -> LocalDate.of(year, month + 1, 1).format(DateTimeFormatter.ISO_DATE)
                    else -> LocalDate.of(year + 1, 1, 1).format(DateTimeFormatter.ISO_DATE)
                }

                SearchQueryOperator.LESS_THAN, SearchQueryOperator.GREATER_THAN_OR_EQUALS -> when {
                    month != null -> LocalDate.of(year, month, 1).format(DateTimeFormatter.ISO_DATE)
                    else -> LocalDate.of(year, 1, 1).format(DateTimeFormatter.ISO_DATE)
                }

                SearchQueryOperator.GREATER_THAN, SearchQueryOperator.LESS_THAN_OR_EQUALS -> when {
                    month != null -> LocalDate.of(year, month + 1, 1).format(DateTimeFormatter.ISO_DATE)
                    else -> LocalDate.of(year + 1, 1, 1).format(DateTimeFormatter.ISO_DATE)
                }

                else -> throw IllegalStateException("Unsupported operation: $operator")
            }
        }
    }

}