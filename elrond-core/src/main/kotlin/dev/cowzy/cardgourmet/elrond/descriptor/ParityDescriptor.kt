package dev.cowzy.cardgourmet.elrond.descriptor

import dev.cowzy.cardgourmet.commons.i18n.LocalizationService
import dev.cowzy.cardgourmet.commons.i18n.Strings
import dev.cowzy.cardgourmet.commons.i18n.UserLanguage
import dev.cowzy.cardgourmet.elrond.property.Parity
import dev.cowzy.cardgourmet.elrond.query.PropertyQueryExpression
import dev.cowzy.cardgourmet.elrond.query.ValueLeafQueryExpression

class ParityDescriptor(propertyKey: String) : PropertyDescriptor(propertyKey) {

    override suspend fun describe(
        expression: PropertyQueryExpression,
        negate: Boolean,
        locale: UserLanguage,
        i18n: LocalizationService
    ): String {
        if (expression !is ValueLeafQueryExpression) throw IllegalArgumentException("Unsupported expression type: ${expression::class.simpleName}")

        val negated = if (negate) !expression.negate else expression.negate

        val propertyKey = when {
            expression.value == Parity.EVEN && !negate -> Strings.Query.Comparison.Numeric.EVEN
            expression.value == Parity.EVEN && negated -> Strings.Query.Comparison.Numeric.ODD
            expression.value == Parity.ODD && !negate -> Strings.Query.Comparison.Numeric.ODD
            expression.value == Parity.ODD && negated -> Strings.Query.Comparison.Numeric.EVEN
            else -> throw IllegalStateException("Unsupported value: ${expression.value}")
        }

        return i18n.translate(locale, propertyKey, getProperty(locale, i18n))
    }

}
