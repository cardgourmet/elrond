package dev.cowzy.cardgourmet.tcg.property.mtg

import dev.cowzy.kuery.query.WhereQueryBuilder
import dev.cowzy.cardgourmet.chef.commons.model.card.mtg.MtgPrint
import dev.cowzy.cardgourmet.chef.commons.model.card.mtg.MtgRarity
import dev.cowzy.cardgourmet.commons.getSerialName
import dev.cowzy.cardgourmet.commons.i18n.Strings
import dev.cowzy.cardgourmet.elrond.*
import dev.cowzy.cardgourmet.elrond.descriptor.NumericDescriptor
import dev.cowzy.cardgourmet.elrond.property.SearchQueryProperty
import dev.cowzy.cardgourmet.elrond.values.ValueProviderPool

class MtgRarityProperty(
    valueProviderPool: ValueProviderPool
) : SearchQueryProperty<MtgRarity>(
    supportedOperators = numericQueryOperators,
    affectedTables = arrayOf(MtgPrint::class),
    descriptor = NumericDescriptor(Strings.Query.Property.RARITY)
) {

    override val valueDefinition = QueryValueDefinition<MtgRarity> {
        formatValue { rarity -> rarity.keywords.first() }

        provider("mtg_rarity", valueProviderPool) {
            strict(true)
            enumValues<MtgRarity>("rarity", findKeywords = { it.keywords.toList() })
        }
    }

    override suspend fun <T : WhereQueryBuilder<T>> applyCondition(
        builder: T,
        operator: SearchQueryOperator,
        value: MtgRarity
    ) {
        builder.where(MtgPrint::rarity, operator.toNumericSqlOperator(), value.index)
    }

}
