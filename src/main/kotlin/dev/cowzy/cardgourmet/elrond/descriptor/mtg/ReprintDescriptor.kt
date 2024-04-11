package dev.cowzy.cardgourmet.elrond.descriptor.mtg

import dev.cowzy.cardgourmet.commons.i18n.LocalizationService
import dev.cowzy.cardgourmet.commons.i18n.Strings
import dev.cowzy.cardgourmet.commons.i18n.UserLanguage
import dev.cowzy.cardgourmet.elrond.descriptor.PropertyDescriptor
import dev.cowzy.cardgourmet.elrond.query.PropertyQueryExpression

open class ReprintDescriptor(private val mode: Mode) : PropertyDescriptor(Strings.Query.Property.CARD) {

    enum class Mode(val key: String, val negatedKey: String) {
        REPRINT_IN(Strings.Query.Mtg.Comparison.ReprintIn.TRUE, Strings.Query.Mtg.Comparison.ReprintIn.FALSE),
        REPRINT_NEW(Strings.Query.Mtg.Comparison.ReprintNew.TRUE, Strings.Query.Mtg.Comparison.ReprintNew.FALSE),
    }

    override suspend fun describe(
        expression: PropertyQueryExpression,
        negate: Boolean,
        locale: UserLanguage,
        i18n: LocalizationService
    ): String {
        val negated = if (negate) !expression.negate else expression.negate

        val key = when {
            negated -> mode.negatedKey
            else -> mode.key
        }

        return i18n.translate(locale, key, getProperty(locale, i18n), getValue(expression, locale, i18n))
    }

}