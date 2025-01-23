package dev.cowzy.cardgourmet.tcg.property.dlc

import dev.cowzy.cardgourmet.commons.catalogue.dlc.DlcRarity
import dev.cowzy.cardgourmet.commons.database.card.dlc.DlcPrint
import dev.cowzy.kuery.query.WhereQueryBuilder
import dev.cowzy.cardgourmet.commons.i18n.Strings
import dev.cowzy.cardgourmet.elrond.*
import dev.cowzy.cardgourmet.elrond.descriptor.NumericDescriptor
import dev.cowzy.cardgourmet.elrond.property.SearchQueryProperty
import dev.cowzy.cardgourmet.elrond.values.ValueProviderPool

class DlcRarityProperty(
    valueProviderPool: ValueProviderPool
) : SearchQueryProperty<DlcRarity>(
    supportedOperators = numericQueryOperators,
    affectedTables = arrayOf(DlcPrint::class),
    descriptor = NumericDescriptor(Strings.Query.Property.RARITY)
) {

    override val valueDefinition = QueryValueDefinition<DlcRarity> {
        formatValue { rarity -> rarity.keys.first() }

        provider("dlc_rarity", valueProviderPool) {
            strict(true)
            enumValues<DlcRarity>("rarity", findKeywords = { it.keys.toList() })
        }
    }

    override suspend fun <T : WhereQueryBuilder<T>> applyCondition(
        builder: T,
        operator: SearchQueryOperator,
        value: DlcRarity
    ) {
        when (operator) {
            SearchQueryOperator.CONTAINS, SearchQueryOperator.EQUALS -> builder.where(DlcPrint::rarity, value)
            else -> {
                val rarities = when (operator) {
                    SearchQueryOperator.GREATER_THAN_OR_EQUALS -> DlcRarity.values().filter { it.index >= value.index }
                    SearchQueryOperator.GREATER_THAN -> DlcRarity.values().filter { it.index > value.index }
                    SearchQueryOperator.LESS_THAN_OR_EQUALS -> DlcRarity.values().filter { it.index <= value.index }
                    SearchQueryOperator.LESS_THAN -> DlcRarity.values().filter { it.index < value.index }
                    else -> throw IllegalArgumentException("Unsupported operator $operator")
                }

                builder.whereIn(DlcPrint::rarity, rarities)
            }
        }
    }

}
