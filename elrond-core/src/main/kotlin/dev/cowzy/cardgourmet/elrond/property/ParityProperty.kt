package dev.cowzy.cardgourmet.elrond.property

import dev.cowzy.cardgourmet.elrond.QueryValueDefinition
import dev.cowzy.cardgourmet.elrond.SearchQueryOperator
import dev.cowzy.cardgourmet.elrond.StringValue
import dev.cowzy.cardgourmet.elrond.descriptor.ParityDescriptor
import dev.cowzy.kuery.find
import dev.cowzy.kuery.query.WhereQueryBuilder
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class Parity {
    @SerialName("even") EVEN,
    @SerialName("odd") ODD
}

class ParityProperty(val property: NumericSearchQueryProperty) : SearchQueryProperty<Parity>(
    supportedOperators = arrayOf(SearchQueryOperator.EQUALS, SearchQueryOperator.CONTAINS),
    comparableTo = emptyArray(),
    affectedTables = property.affectedTables,
    descriptor = ParityDescriptor(property.descriptor.propertyKey),
    key = "${property.key}_parity"
) {

    override val valueDefinition = QueryValueDefinition {
        StringValue::class {
            transform { Parity.values().find(it.value) }
        }
    }

    override suspend fun <T : WhereQueryBuilder<T>> applyCondition(
        builder: T,
        operator: SearchQueryOperator,
        value: Parity
    ) {
        when (value) {
            Parity.EVEN -> builder.where("abs(${property.getRawSql()})::integer % 2", 0)
            Parity.ODD -> builder.where("abs(${property.getRawSql()})::integer % 2", 1)
        }
    }

}
