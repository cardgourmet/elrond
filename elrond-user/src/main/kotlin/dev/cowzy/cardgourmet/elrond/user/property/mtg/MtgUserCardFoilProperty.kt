package dev.cowzy.cardgourmet.elrond.user.property.mtg

import dev.cowzy.cardgourmet.chef.commons.model.card.mtg.MtgFinish
import dev.cowzy.kuery.query.WhereQueryBuilder
import dev.cowzy.kuery.query.orWhereRaw
import dev.cowzy.cardgourmet.commons.user.UserCard
import dev.cowzy.cardgourmet.commons.getSerialName
import dev.cowzy.cardgourmet.commons.i18n.Strings
import dev.cowzy.cardgourmet.elrond.descriptor.SimplePropertyDescriptor
import dev.cowzy.cardgourmet.elrond.property.StaticSearchQueryProperty

class MtgUserCardFoilProperty(private val inverted: Boolean = false) : StaticSearchQueryProperty(
    arrayOf(UserCard::class),
    SimplePropertyDescriptor(Strings.Query.Mtg.Comparison.IsFoil.KEY, Strings.Query.Property.PRINT),
    key = "is_foil"
) {
    override suspend fun <T : WhereQueryBuilder<T>> applyCondition(builder: T) {
        val foilTypes = MtgFinish.values().filter { it.isFoil() }

        if (inverted) {
            builder.whereNot { inner ->
                foilTypes.forEach {
                    inner.orWhereRaw(UserCard::finishes, "@>", "ARRAY[?]::text[]") { stmt, index ->
                        stmt.setString(index.getAndIncrement(), it.getSerialName())
                    }
                }
            }
        } else {
            builder.where { inner ->
                foilTypes.forEach {
                    inner.orWhereRaw(UserCard::finishes, "@>", "ARRAY[?]::text[]") { stmt, index ->
                        stmt.setString(index.getAndIncrement(), it.getSerialName())
                    }
                }
            }
        }
    }
}
