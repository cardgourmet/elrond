package dev.cowzy.cardgourmet.elrond.descriptor.mtg

import dev.cowzy.cardgourmet.commons.i18n.LocalizationService
import dev.cowzy.cardgourmet.commons.i18n.Strings
import dev.cowzy.cardgourmet.commons.i18n.UserLanguage
import dev.cowzy.cardgourmet.elrond.descriptor.PropertyDescriptor
import dev.cowzy.cardgourmet.elrond.query.PropertyQueryExpression

class FormatDescriptor(private val type: Type) : PropertyDescriptor(Strings.Query.Property.CARD) {

    enum class Type(val key: String, val negatedKey: String) {
        LEGAL(Strings.Query.Mtg.Comparison.FormatLegal.TRUE, Strings.Query.Mtg.Comparison.FormatLegal.FALSE),
        RESTRICTED(Strings.Query.Mtg.Comparison.FormatRestricted.TRUE, Strings.Query.Mtg.Comparison.FormatRestricted.FALSE),
        BANNED(Strings.Query.Mtg.Comparison.FormatBanned.TRUE, Strings.Query.Mtg.Comparison.FormatBanned.FALSE),
    }

    override suspend fun describe(
        expression: PropertyQueryExpression,
        negate: Boolean,
        locale: UserLanguage,
        i18n: LocalizationService
    ): String {
        val negated = if (negate) !expression.negate else expression.negate

        val key = when {
            negated -> type.negatedKey
            else -> type.key
        }

        return i18n.translate(locale, key, getProperty(locale, i18n), getValue(expression, locale, i18n))
    }

}