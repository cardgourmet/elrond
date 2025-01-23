package dev.cowzy.cardgourmet.tcg.property.pcg

import dev.cowzy.cardgourmet.commons.database.card.pcg.PcgPrint
import dev.cowzy.cardgourmet.commons.database.card.pcg.PcgRarity
import dev.cowzy.cardgourmet.commons.i18n.Strings
import dev.cowzy.cardgourmet.elrond.QueryValueDefinition
import dev.cowzy.cardgourmet.elrond.SearchQueryOperator
import dev.cowzy.cardgourmet.elrond.descriptor.NumericDescriptor
import dev.cowzy.cardgourmet.elrond.numericQueryOperators
import dev.cowzy.cardgourmet.elrond.property.SearchQueryProperty
import dev.cowzy.cardgourmet.elrond.toNumericSqlOperator
import dev.cowzy.cardgourmet.elrond.values.ValueProviderPool
import dev.cowzy.kuery.query.WhereQueryBuilder

class PcgRarityProperty(
    valueProviderPool: ValueProviderPool
) : SearchQueryProperty<PcgRarity>(
    supportedOperators = numericQueryOperators,
    affectedTables = arrayOf(PcgPrint::class),
    descriptor = NumericDescriptor(Strings.Query.Property.RARITY)
) {

    override val valueDefinition = QueryValueDefinition<PcgRarity> {
        formatValue { rarity -> rarity.keys.first() }

        provider("pcg_rarity", valueProviderPool) {
            strict(true)
            enumValues<PcgRarity>("rarity", findKeywords = { it.keys.toList() })
        }
    }

    override suspend fun <T : WhereQueryBuilder<T>> applyCondition(
        builder: T,
        operator: SearchQueryOperator,
        value: PcgRarity
    ) {
        when (operator) {
            SearchQueryOperator.CONTAINS, SearchQueryOperator.EQUALS -> builder.where(PcgPrint::rarity, value)
            else -> builder.where(PcgPrint::rarityValue, operator.toNumericSqlOperator(), value.value)
        }
    }

}
