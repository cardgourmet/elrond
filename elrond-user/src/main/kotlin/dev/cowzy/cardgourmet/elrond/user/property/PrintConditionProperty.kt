package dev.cowzy.cardgourmet.elrond.user.property

import dev.cowzy.kuery.query.WhereQueryBuilder
import dev.cowzy.kuery.query.orWhere
import dev.cowzy.cardgourmet.commons.database.card.CardCondition
import dev.cowzy.cardgourmet.commons.user.UserCard
import dev.cowzy.cardgourmet.commons.getSerialName
import dev.cowzy.cardgourmet.commons.i18n.Strings
import dev.cowzy.cardgourmet.elrond.*
import dev.cowzy.cardgourmet.elrond.descriptor.NumericDescriptor
import dev.cowzy.cardgourmet.elrond.property.SearchQueryProperty

class PrintConditionProperty : SearchQueryProperty<CardCondition>(
    supportedOperators = numericQueryOperators,
    affectedTables = arrayOf(UserCard::class),
    descriptor = NumericDescriptor(Strings.Query.Collection.Property.CONDITION)
) {

    override val valueDefinition = QueryValueDefinition {
        formatValue { (it as CardCondition).shorthand }

        StringValue::class {
            transform { value ->
                CardCondition.values().find {
                    it.shorthand.equals(value.value, ignoreCase = true)
                            || it.getSerialName().equals(value.value, ignoreCase = true)
                }
            }
        }
    }

    override suspend fun <T : WhereQueryBuilder<T>> applyCondition(
        builder: T,
        operator: SearchQueryOperator,
        value: CardCondition
    ) {
        val matchConditions = when (operator) {
            SearchQueryOperator.CONTAINS, SearchQueryOperator.EQUALS -> listOf(value)
            SearchQueryOperator.GREATER_THAN_OR_EQUALS -> CardCondition.values().filter { it.value <= value.value }
            SearchQueryOperator.GREATER_THAN -> CardCondition.values().filter { it.value < value.value }
            SearchQueryOperator.LESS_THAN_OR_EQUALS -> CardCondition.values().filter { it.value >= value.value }
            SearchQueryOperator.LESS_THAN -> CardCondition.values().filter { it.value > value.value }
        }

        builder.where { inner ->
            matchConditions.forEach {
                inner.orWhere(UserCard::condition, it)
            }
        }
    }

}
