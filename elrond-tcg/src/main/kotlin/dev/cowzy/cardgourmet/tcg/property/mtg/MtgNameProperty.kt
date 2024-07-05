package dev.cowzy.cardgourmet.tcg.property.mtg

import dev.cowzy.kuery.query.*
import dev.cowzy.kuery.reflection.columnName
import dev.cowzy.cardgourmet.commons.database.card.mtg.*
import dev.cowzy.cardgourmet.commons.i18n.Strings
import dev.cowzy.cardgourmet.commons.toSimpleString
import dev.cowzy.cardgourmet.elrond.*
import dev.cowzy.cardgourmet.elrond.descriptor.StringDescriptor
import dev.cowzy.cardgourmet.elrond.property.SearchQueryProperty
import dev.cowzy.cardgourmet.elrond.query.ValueLeafQueryExpression
import dev.cowzy.cardgourmet.elrond.tokenizer.LogicalOperator

class MtgNameProperty : SearchQueryProperty<QueryValue<*>>(
    supportedOperators = stringQueryOperators,
    affectedTables = arrayOf(MtgCardFaceTranslation::class),
    descriptor = StringDescriptor(Strings.Query.Property.NAME)
) {

    init {
        handleJoinedAnd = true
        handleJoinedOr = true
    }

    override val valueDefinition = QueryValueDefinition<QueryValue<*>> {
        StringValue::class {
            transform {
                when {
                    !it.exact -> StringValue(it.value.toSimpleString())
                    else -> it
                }
            }
        }

        RegexValue::class {
            transformWithOperator { value, operator ->
                when (operator) {
                    SearchQueryOperator.EQUALS -> RegexValue(value.value.pattern.toFullMatchRegex().toRegex())
                    else -> value
                } to operator
            }
        }
    }

    override suspend fun <T : WhereQueryBuilder<T>> applyCondition(
        builder: T,
        operator: SearchQueryOperator,
        value: QueryValue<*>
    ) {
        val innerBuilder = QueryBuilder.selectBuilder("mtg.search_names")
            .select("id")
            .apply(operator, value)
            .orderBy("mtg.search_names.priority")

        builder.whereIn(MtgCardFaceTranslation::id.columnName(), innerBuilder)
    }

    private fun <T : WhereQueryBuilder<T>> T.apply(
        operator: SearchQueryOperator,
        value: QueryValue<*>
    ): T {
        when (value) {
            is StringValue -> {
                val nameColumn = when {
                    value.exact && operator == SearchQueryOperator.CONTAINS -> "mtg.search_names.name"
                    value.exact -> "mtg.search_names.name" //"UPPER(mtg.search_names.name)"
                    else -> "mtg.search_names.simple_name"
                }

                when (operator) {
//                    SearchQueryOperator.EQUALS -> this.where(nameColumn, "=", value = if (value.exact) value.value.uppercase() else value.value)
                    SearchQueryOperator.EQUALS -> this.where(nameColumn, "ILIKE", value = value.value)
                    SearchQueryOperator.CONTAINS -> this.where(nameColumn, "ILIKE", value = "%${value.value}%")
                    else -> throw IllegalStateException("Unsupported operator: $operator")
                }
            }

            is RegexValue -> {
                val pattern = value.value.pattern
                this.where("mtg.search_names.name", "~*", value = pattern)
            }

            else -> throw IllegalStateException("Unsupported value type: ${value::class.simpleName}")
        }

        return this
    }

    override suspend fun <T : WhereQueryBuilder<T>> applyMultipleConditions(
        builder: T,
        operator: LogicalOperator,
        conditions: List<Pair<SearchQueryOperator, QueryValue<*>>>
    ) {
        val innerBuilder = QueryBuilder.selectBuilder("mtg.search_names").select("id")

        if (operator == LogicalOperator.AND) {
            conditions.forEach { (operator, value) -> innerBuilder.apply(operator, value) }
            builder.whereIn(MtgCardFaceTranslation::id.columnName(), innerBuilder)
        } else {
            val builders = conditions.map { (operator, value) -> innerBuilder.clone().apply(operator, value) }
            val unionBuilder = builders.first().union(builders[1])
            builders.drop(2).forEach { unionBuilder.union(it) }
            builder.whereIn(MtgCardFaceTranslation::id.columnName(), unionBuilder)
        }
    }

}