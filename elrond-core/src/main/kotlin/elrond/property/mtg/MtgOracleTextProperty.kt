package elrond.property.mtg

import dev.cowzy.kuery.query.SelectQueryBuilder
import dev.cowzy.kuery.query.WhereQueryBuilder
import dev.cowzy.kuery.reflection.columnName
import dev.cowzy.cardgourmet.commons.database.card.mtg.MtgCardFaceTranslation
import dev.cowzy.cardgourmet.commons.database.card.mtg.MtgPrintFaceTranslation
import dev.cowzy.cardgourmet.elrond.*
import dev.cowzy.cardgourmet.elrond.descriptor.PropertyDescriptor
import dev.cowzy.cardgourmet.elrond.property.StringSearchQueryProperty
import kotlin.reflect.KProperty1

class MtgOracleTextProperty(
    private val column: KProperty1<*, *>,
    private val simpleColumn: KProperty1<*, *>,
    descriptor: PropertyDescriptor
) : StringSearchQueryProperty(
    simplify = true,
    affectedTables = arrayOf(MtgCardFaceTranslation::class, MtgPrintFaceTranslation::class),
    descriptor = descriptor,
) {

    override fun applyProperty(builder: SelectQueryBuilder) = Unit

    override suspend fun <T : WhereQueryBuilder<T>> applyCondition(
        builder: T,
        operator: SearchQueryOperator,
        value: QueryValue<*>
    ) {
        when (value) {
            is StringValue -> {
                val containsPlaceholder = value.value.contains("~")

                when (operator) {
                    SearchQueryOperator.EQUALS -> builder.where(getColumnName(containsPlaceholder, true, value), "=", value = value.value.uppercase())
                    SearchQueryOperator.CONTAINS -> builder.where(getColumnName(containsPlaceholder, false, value), "ILIKE", value = "%${value.value}%")
                    else -> throw IllegalStateException("Unsupported operator: $operator")
                }
            }

            is RegexValue -> {
                val pattern = when (operator) {
                    SearchQueryOperator.EQUALS -> value.value.pattern.toFullMatchRegex()
                    SearchQueryOperator.CONTAINS -> value.value.pattern
                    else -> throw IllegalStateException("Unsupported operator: $operator")
                }

                val containsPlaceholder = pattern.contains("~")

                builder.where(getColumnName(containsPlaceholder, false, value), "~*", value = pattern)
            }

            else -> throw IllegalStateException("Unsupported value type: ${value::class.simpleName}")
        }
    }

    private fun getColumnName(containsPlaceholder: Boolean, uppercase: Boolean, value: QueryValue<*>): String {
        if (containsPlaceholder) {
            val transformedColumn = "UPPER(${column.columnName()})"
            val cardName = "UPPER(COALESCE(${MtgPrintFaceTranslation::flavorName.columnName()}, ${MtgCardFaceTranslation::name.columnName()}))"
            return "REPLACE($transformedColumn, $cardName, '~')"
        }

        return if (uppercase) "UPPER(${getRawSql(value)})" else getRawSql(value)
    }

    override fun getRawSql(value: QueryValue<*>) = when {
        value is StringValue && !value.exact -> simpleColumn.columnName()
        else -> column.columnName()
    }

}