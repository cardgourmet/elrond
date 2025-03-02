package dev.cowzy.cardgourmet.tcg.property.pcg

import dev.cowzy.cardgourmet.chef.commons.model.card.pcg.PcgCard
import dev.cowzy.cardgourmet.chef.commons.model.card.pcg.PcgEvolutionStage
import dev.cowzy.cardgourmet.commons.i18n.Strings.Query.Pcg.Property.EVOLUTION_STAGE
import dev.cowzy.cardgourmet.elrond.QueryValueDefinition
import dev.cowzy.cardgourmet.elrond.SearchQueryOperator
import dev.cowzy.cardgourmet.elrond.descriptor.NumericDescriptor
import dev.cowzy.cardgourmet.elrond.numericQueryOperators
import dev.cowzy.cardgourmet.elrond.property.SearchQueryProperty
import dev.cowzy.cardgourmet.elrond.toNumericSqlOperator
import dev.cowzy.cardgourmet.elrond.values.ValueProviderPool
import dev.cowzy.kuery.query.WhereQueryBuilder

class PcgStageProperty(
    valueProviderPool: ValueProviderPool
) : SearchQueryProperty<PcgEvolutionStage>(
    supportedOperators = numericQueryOperators,
    affectedTables = arrayOf(PcgCard::class),
    descriptor = NumericDescriptor(EVOLUTION_STAGE)
) {

    override val valueDefinition = QueryValueDefinition<PcgEvolutionStage> {
        formatValue { rarity -> rarity.keys.first() }

        provider("pcg_evolution_stage", valueProviderPool) {
            strict(true)
            enumValues<PcgEvolutionStage>("evolution_stage", findKeywords = { it.keys.toList() })
        }
    }

    override suspend fun <T : WhereQueryBuilder<T>> applyCondition(
        builder: T,
        operator: SearchQueryOperator,
        value: PcgEvolutionStage
    ) {
        when (operator) {
            SearchQueryOperator.CONTAINS, SearchQueryOperator.EQUALS -> builder.where(PcgCard::evolutionStage, value)
            else -> builder.where(PcgCard::evolutionStageValue, operator.toNumericSqlOperator(), value.value)
        }
    }

}
