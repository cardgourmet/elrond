package dev.cowzy.cardgourmet.elrond.property.mtg

import dev.cowzy.kuery.query.*
import dev.cowzy.kuery.reflection.columnName
import dev.cowzy.cardgourmet.commons.database.card.mtg.*
import dev.cowzy.cardgourmet.commons.i18n.Strings
import dev.cowzy.cardgourmet.commons.toSimpleString
import dev.cowzy.cardgourmet.elrond.*
import dev.cowzy.cardgourmet.elrond.descriptor.StringDescriptor
import dev.cowzy.cardgourmet.elrond.property.SearchQueryProperty
import dev.cowzy.cardgourmet.elrond.values.ValueProvider

class MtgUserNameProperty(
    valueProvider: ValueProvider<String>
) : SearchQueryProperty<QueryValue<*>>(
    supportedOperators = stringQueryOperators,
    affectedTables = arrayOf(MtgCardFaceTranslation::class, MtgPrintFaceTranslation::class),
    descriptor = StringDescriptor(Strings.Query.Property.NAME)
) {

    override val valueDefinition = QueryValueDefinition<QueryValue<*>> {
        StringValue::class {
            values(valueProvider)

            transform {
                when {
                    !it.exact -> StringValue(it.value.toSimpleString())
                    else -> it
                }
            }
        }

        RegexValue::class {
            transformWithOperator { value, operator ->
                when (operator) {
                    SearchQueryOperator.EQUALS -> RegexValue(value.value.pattern.toFullMatchRegex().toRegex())
                    else -> value
                } to operator
            }
        }
    }

    override suspend fun <T : WhereQueryBuilder<T>> applyCondition(
        builder: T,
        operator: SearchQueryOperator,
        value: QueryValue<*>
    ) {
        if (value is StringValue) {
            builder.where { inner ->
                inner.where { query ->
                    val nameColumn = when {
                        value.exact && operator == SearchQueryOperator.CONTAINS -> MtgCardFaceTranslation::name.columnName()
                        value.exact -> "UPPER(${MtgCardFaceTranslation::name.columnName()})"
                        else -> MtgCardFaceTranslation::simpleName.columnName()
                    }

                    when (operator) {
                        SearchQueryOperator.EQUALS -> query.where(nameColumn, "=", value = if (value.exact) value.value.uppercase() else value.value)
                        SearchQueryOperator.CONTAINS -> query.where(nameColumn, "ILIKE", value = "%${value.value}%")
                        else -> throw IllegalStateException("Unsupported operator: $operator")
                    }
                }

                inner.orWhere { query ->
                    val nameColumn = when {
                        operator == SearchQueryOperator.CONTAINS -> MtgCard::name.columnName()
                        else -> "UPPER(${MtgCard::name.columnName()})"
                    }

                    when (operator) {
                        SearchQueryOperator.EQUALS -> query.where(nameColumn, "=", value = value.value.uppercase())
                        SearchQueryOperator.CONTAINS -> query.where(nameColumn, "ILIKE", value = "%${value.value}%")
                        else -> throw IllegalStateException("Unsupported operator: $operator")
                    }
                }

                inner.orWhere { query ->
                    val flavorNameColumn = when {
                        value.exact && operator == SearchQueryOperator.CONTAINS -> MtgPrintFaceTranslation::flavorName.columnName()
                        value.exact -> "UPPER(${MtgPrintFaceTranslation::flavorName.columnName()})"
                        else -> MtgPrintFaceTranslation::simpleFlavorName.columnName()
                    }

                    query.whereNotNull(if (value.exact) MtgPrintFaceTranslation::flavorName else MtgPrintFaceTranslation::simpleFlavorName)

                    when (operator) {
                        SearchQueryOperator.EQUALS -> query.where(flavorNameColumn, "=", value = if (value.exact) value.value.uppercase() else value.value)
                        SearchQueryOperator.CONTAINS -> query.where(flavorNameColumn, "ILIKE", value = "%${value.value}%")
                        else -> throw IllegalStateException("Unsupported operator: $operator")
                    }
                }
            }
        } else if (value is RegexValue) {
            val pattern = value.value.pattern
            builder.where { inner ->
                inner.where { it.where(MtgCard::name, "~*", value = pattern) }
                inner.orWhere(MtgCardFaceTranslation::name, "~*", value = pattern)
            }
        }
    }

}