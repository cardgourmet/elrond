package dev.cowzy.cardgourmet.elrond.descriptor

import dev.cowzy.cardgourmet.commons.i18n.LocalizationService
import dev.cowzy.cardgourmet.commons.i18n.Strings
import dev.cowzy.cardgourmet.commons.i18n.UserLanguage
import dev.cowzy.cardgourmet.elrond.SearchQueryOperator
import dev.cowzy.cardgourmet.elrond.query.PropertyQueryExpression

class NumericDescriptor(
    propertyKey: String,
    private val mapContainsTo: SearchQueryOperator = SearchQueryOperator.EQUALS,
) : PropertyDescriptor(propertyKey) {

    override suspend fun describe(
        expression: PropertyQueryExpression,
        negate: Boolean,
        locale: UserLanguage,
        i18n: LocalizationService
    ): String {
        val negated = if (negate) !expression.negate else expression.negate

        val operator = if (expression.operator == SearchQueryOperator.CONTAINS) mapContainsTo else expression.operator

        val propertyKey = Strings.Query.Comparison.Numeric.let {
            when (operator) {
                SearchQueryOperator.EQUALS -> if (!negated) it.EQUALS else it.NOT_EQUALS
                SearchQueryOperator.GREATER_THAN_OR_EQUALS -> if (!negated) it.GREATER_THAN_OR_EQUAL_TO else it.LESS_THAN
                SearchQueryOperator.GREATER_THAN -> if (!negated) it.GREATER_THAN else it.LESS_THAN_OR_EQUAL_TO
                SearchQueryOperator.LESS_THAN_OR_EQUALS -> if (!negated) it.LESS_THAN_OR_EQUAL_TO else it.GREATER_THAN
                SearchQueryOperator.LESS_THAN -> if (!negated) it.LESS_THAN else it.GREATER_THAN_OR_EQUAL_TO
                else -> throw IllegalStateException("Unsupported operator: $operator")
            }
        }

        return i18n.translate(locale, propertyKey, getProperty(locale, i18n), getValue(expression, locale, i18n))
    }

}
