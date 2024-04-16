package elrond.descriptor.mtg

import dev.cowzy.cardgourmet.commons.i18n.LocalizationService
import dev.cowzy.cardgourmet.commons.i18n.Strings
import dev.cowzy.cardgourmet.commons.i18n.UserLanguage
import dev.cowzy.cardgourmet.elrond.query.PropertyQueryExpression
import dev.cowzy.cardgourmet.elrond.query.ValueLeafQueryExpression

class ReprintNewDescriptor : ReprintDescriptor(Mode.REPRINT_NEW) {

    private val special = mapOf(
        "rarity" to Strings.Query.Mtg.Comparison.ReprintNew.Rarity.KEY,
        "set" to Strings.Query.Mtg.Comparison.ReprintNew.Set.KEY,
        "border" to Strings.Query.Mtg.Comparison.ReprintNew.Border.KEY,
        "artist" to Strings.Query.Mtg.Comparison.ReprintNew.Artist.KEY,
        "flavor" to Strings.Query.Mtg.Comparison.ReprintNew.Flavor.KEY,
        "frame" to Strings.Query.Mtg.Comparison.ReprintNew.Frame.KEY,
        "language" to Strings.Query.Mtg.Comparison.ReprintNew.Language.KEY,
        "finish" to Strings.Query.Mtg.Comparison.ReprintNew.Finish.KEY,
        "game" to Strings.Query.Mtg.Comparison.ReprintNew.Game.KEY,
        "stamp" to Strings.Query.Mtg.Comparison.ReprintNew.Stamp.KEY,
        "watermark" to Strings.Query.Mtg.Comparison.ReprintNew.Watermark.KEY,
        "frame_effect" to Strings.Query.Mtg.Comparison.ReprintNew.FrameEffect.KEY,
    )

    override suspend fun describe(
        expression: PropertyQueryExpression,
        negate: Boolean,
        locale: UserLanguage,
        i18n: LocalizationService
    ): String {
        if (expression !is ValueLeafQueryExpression || !special.containsKey(expression.value)) {
            return super.describe(expression, negate, locale, i18n)
        }

        val negated = if (negate) !expression.negate else expression.negate

        val baseKey = special[expression.value]!!
        val key = when {
            negated -> "$baseKey.false"
            else -> "$baseKey.true"
        }

        return i18n.translate(locale, key, getProperty(locale, i18n), getValue(expression, locale, i18n))
    }

}