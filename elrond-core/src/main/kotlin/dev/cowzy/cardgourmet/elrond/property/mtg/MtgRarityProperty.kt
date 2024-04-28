package dev.cowzy.cardgourmet.elrond.property.mtg

import dev.cowzy.cardgourmet.commons.catalogue.MtgRarity
import dev.cowzy.kuery.query.WhereQueryBuilder
import dev.cowzy.cardgourmet.commons.database.card.mtg.MtgPrint
import dev.cowzy.cardgourmet.commons.getSerialName
import dev.cowzy.cardgourmet.commons.i18n.Strings
import dev.cowzy.cardgourmet.elrond.*
import dev.cowzy.cardgourmet.elrond.descriptor.NumericDescriptor
import dev.cowzy.cardgourmet.elrond.property.SearchQueryProperty

class MtgRarityProperty : SearchQueryProperty<MtgRarity>(
    supportedOperators = numericQueryOperators,
    affectedTables = arrayOf(MtgPrint::class),
    descriptor = NumericDescriptor(Strings.Query.Property.RARITY)
) {

    override val valueDefinition = QueryValueDefinition<MtgRarity> {
        StringValue::class {
            mappings(enumToMappings<MtgRarity> { it.keywords.toList() })
            values(MtgRarity.values().map { it.getSerialName() })

            transform { value ->
                MtgRarity.values().find { it.keywords.any { keyword -> keyword.equals(value.value, ignoreCase = true) } }
            }

            display { rarity, _, _ -> "`${rarity.keywords.first()}`" }
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
