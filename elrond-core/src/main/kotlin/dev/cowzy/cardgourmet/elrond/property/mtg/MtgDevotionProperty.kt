package dev.cowzy.cardgourmet.elrond.property.mtg

import dev.cowzy.cardgourmet.commons.MtgManaType
import dev.cowzy.kuery.ColumnIndex
import dev.cowzy.kuery.query.WhereQueryBuilder
import dev.cowzy.kuery.query.orWhere
import dev.cowzy.kuery.reflection.columnName
import dev.cowzy.cardgourmet.commons.database.card.mtg.MtgCard
import dev.cowzy.cardgourmet.commons.database.card.mtg.MtgCardFace
import dev.cowzy.cardgourmet.commons.i18n.Strings
import dev.cowzy.cardgourmet.commons.toManaDisplays
import dev.cowzy.cardgourmet.elrond.*
import dev.cowzy.cardgourmet.elrond.descriptor.NumericDescriptor
import dev.cowzy.cardgourmet.elrond.property.SearchQueryProperty
import dev.cowzy.kuery.setNumber
import java.sql.PreparedStatement

class MtgDevotionProperty: SearchQueryProperty<Map<MtgManaType, Int>>(
    numericQueryOperators,
    emptyArray(),
    arrayOf(MtgCardFace::class, MtgCard::class),
    NumericDescriptor(Strings.Query.Mtg.Property.DEVOTION, mapContainsTo = SearchQueryOperator.GREATER_THAN_OR_EQUALS),
) {

    override val valueDefinition = QueryValueDefinition<Map<MtgManaType, Int>> {
        StringValue::class {
            format = "mtg_mana"

            transform { value -> value.value.toManaDisplays()?.map { display -> display.values.map { it.type } }?.flatten()?.groupBy { it }?.mapValues { it.value.size } }

            match { it.values.distinct().size == 1 && it.values.sum() % it.entries.size == 0 }

            display { value, _, _ ->
                val types = value.keys.sortedBy { it.ordinal }
                val targetDevotion = value.values.sum() / value.entries.size

                val displayValue = (0 until targetDevotion).joinToString("") {
                    val content = types.joinToString("/") { type -> type.symbol }
                    "{$content}"
                }

                return@display "`$displayValue`"
            }
        }
    }

    override suspend fun <T : WhereQueryBuilder<T>> applyCondition(
        builder: T,
        operator: SearchQueryOperator,
        value: Map<MtgManaType, Int>
    ) {
        val targetDevotion = value.values.sum() / value.entries.size

        val sqlSum = value.entries.joinToString(" + ") { "cardinality(array_positions(${MtgCardFace::colorDevotion.columnName()}, ?))" }
        val fillSum: (PreparedStatement, ColumnIndex) -> Unit = { stmt, index ->
            value.forEach { stmt.setNumber(index.getAndIncrement(), it.key.ordinal) }
            stmt.setNumber(index.getAndIncrement(), targetDevotion)
        }

        builder.where { it
            .where(MtgCard::layout, "!=", "transform")
            .orWhere(MtgCardFace::index, 0)
        }

        builder.where { inner ->
            when (operator) {
                SearchQueryOperator.CONTAINS, SearchQueryOperator.GREATER_THAN_OR_EQUALS -> inner
                    .whereRaw("$sqlSum >= ?", fillSum)
                    .where(MtgCardFace::totalDevotion, ">=", targetDevotion)
                SearchQueryOperator.GREATER_THAN -> inner
                    .whereRaw("$sqlSum > ?", fillSum)
                    .where(MtgCardFace::totalDevotion, ">", targetDevotion)
                SearchQueryOperator.LESS_THAN_OR_EQUALS -> inner.whereRaw("$sqlSum <= ?", fillSum)
                SearchQueryOperator.LESS_THAN -> inner.whereRaw("$sqlSum < ?", fillSum)
                SearchQueryOperator.EQUALS -> inner
                    .whereRaw("$sqlSum = ?", fillSum)
                    .where(MtgCardFace::totalDevotion, ">=", targetDevotion)
            }
        }
    }
}