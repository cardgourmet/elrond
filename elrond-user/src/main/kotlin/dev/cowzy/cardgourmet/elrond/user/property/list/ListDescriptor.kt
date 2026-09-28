package dev.cowzy.cardgourmet.elrond.user.property.list

import dev.cowzy.cardgourmet.commons.i18n.LocalizationService
import dev.cowzy.cardgourmet.commons.i18n.Strings
import dev.cowzy.cardgourmet.commons.i18n.UserLanguage
import dev.cowzy.cardgourmet.elrond.descriptor.PropertyDescriptor
import dev.cowzy.cardgourmet.elrond.query.PropertyQueryExpression
import dev.cowzy.cardgourmet.elrond.query.SearchQueryDistinctMode
import dev.cowzy.cardgourmet.elrond.query.ValueLeafQueryExpression
import dev.cowzy.cardgourmet.tcg.config.card.TcgCardSearchQueryDistinctMode

class ListDescriptor(propertyKey: String) : PropertyDescriptor(propertyKey) {

    override suspend fun describe(
        expression: PropertyQueryExpression,
        negate: Boolean,
        locale: UserLanguage,
        i18n: LocalizationService,
        distinctMode: SearchQueryDistinctMode
    ): String {
        if (expression !is ValueLeafQueryExpression) throw IllegalArgumentException("Unsupported expression type: ${expression::class.simpleName}")

        val negated = if (negate) !expression.negate else expression.negate

        val value = when {
            expression.value is ListDetails && (expression.value as ListDetails).username != null -> {
                i18n.translate(
                    locale,
                    "query.user.list.with_user",
                    "`${(expression.value as ListDetails).name}`",
                    "`@${(expression.value as ListDetails).username}`"
                )
            }
            else -> i18n.translate(
                locale,
                "query.user.list.without_user",
                "`${(expression.value as? ListDetails)?.name ?: expression.value.toString()}`"
            )
        }

        val subjectKey = when (distinctMode) {
            TcgCardSearchQueryDistinctMode.UNIQUE_CARDS, TcgCardSearchQueryDistinctMode.UNIQUE_FACES -> Strings.Query.Property.CARD
            else -> Strings.Query.Property.PRINT
        }

        return when (negated) {
            true -> i18n.translate(locale, "query.comparison.contained_in.false", i18n.translate(locale, subjectKey), value)
            false -> i18n.translate(locale, "query.comparison.contained_in.true", i18n.translate(locale, subjectKey), value)
        }
    }

}