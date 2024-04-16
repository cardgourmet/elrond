package dev.cowzy.cardgourmet.elrond.descriptor

import dev.cowzy.cardgourmet.commons.i18n.LocalizationService
import dev.cowzy.cardgourmet.commons.i18n.Strings
import dev.cowzy.cardgourmet.commons.i18n.UserLanguage
import dev.cowzy.cardgourmet.elrond.RegexValue
import dev.cowzy.cardgourmet.elrond.SearchQueryOperator
import dev.cowzy.cardgourmet.elrond.StringValue
import dev.cowzy.cardgourmet.elrond.query.PropertyQueryExpression
import dev.cowzy.cardgourmet.elrond.query.ValueLeafQueryExpression

class StringDescriptor(
    subjectKey: String,
) : PropertyDescriptor(subjectKey) {

    override suspend fun describe(
        expression: PropertyQueryExpression,
        negate: Boolean,
        locale: UserLanguage,
        i18n: LocalizationService
    ): String {
        if (expression !is ValueLeafQueryExpression) {
            throw IllegalArgumentException("Unsupported expression $expression")
        }

        val negated = if (negate) !expression.negate else expression.negate

        val propertyKey = when (expression.value) {
            is StringValue -> Strings.Query.Comparison.String.let {
                when (expression.operator) {
                    SearchQueryOperator.CONTAINS -> if (!negated) it.CONTAINS else it.NOT_CONTAINS
                    SearchQueryOperator.EQUALS -> if (!negated) it.EQUALS else it.NOT_EQUALS
                    else -> throw IllegalArgumentException("Unsupported operator ${expression.operator}")
                }
            }

            is RegexValue -> when {
                !negated -> Strings.Query.Comparison.MatchesRegex.TRUE
                else -> Strings.Query.Comparison.MatchesRegex.FALSE
            }

            else -> throw IllegalArgumentException("Unsupported value ${expression.value}")
        }

        return i18n.translate(locale, propertyKey, getProperty(locale, i18n), getValue(expression, locale, i18n))
    }

}