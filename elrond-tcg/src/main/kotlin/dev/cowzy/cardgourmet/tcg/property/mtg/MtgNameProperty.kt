package dev.cowzy.cardgourmet.tcg.property.mtg

import dev.cowzy.cardgourmet.chef.commons.model.card.mtg.*
import dev.cowzy.cardgourmet.commons.*
import dev.cowzy.cardgourmet.commons.i18n.*
import dev.cowzy.cardgourmet.elrond.*
import dev.cowzy.cardgourmet.elrond.descriptor.*
import dev.cowzy.cardgourmet.elrond.property.*
import dev.cowzy.cardgourmet.elrond.tokenizer.*
import dev.cowzy.kuery.query.*
import dev.cowzy.kuery.reflection.*

class MtgNameProperty : SearchQueryProperty<QueryValue<*>>(
    supportedOperators = stringQueryOperators,
    affectedTables = arrayOf(MtgCardFaceTranslation::class, MtgPrintFaceTranslation::class),
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
        builder.applyNameSearchTerm(operator, value)
    }

    override suspend fun <T : WhereQueryBuilder<T>> applyMultipleConditions(
        builder: T,
        operator: LogicalOperator,
        conditions: List<Pair<SearchQueryOperator, QueryValue<*>>>
    ) {
        conditions.forEach { (op, value) ->
            when (operator) {
                LogicalOperator.AND -> builder.where { it.applyNameSearchTerm(op, value) }
                else -> builder.orWhere { it.applyNameSearchTerm(op, value) }
            }
        }
    }

    private fun <T : WhereQueryBuilder<T>> T.applyNameSearchTerm(
        operator: SearchQueryOperator,
        value: QueryValue<*>
    ): T {
        return this.where {
            it.where { inner -> applyNameCondition(inner, printFaceTranslationColumn(value), operator, value) }
            it.orWhere { inner ->
                val existsQuery = QueryBuilder.selectBuilder(
                    "mtg.search_names AS other_search_names",
                    tableAlias = "other_search_names"
                )
                    .selectRaw("1")
                    .whereColumn("other_search_names.card_id", MtgCard::id.columnName())
                    .whereNotNull("other_search_names.print_face_translation_id")
                    .apply {
                        applyNameCondition(
                            inner,
                            "other_search_names.${searchNameColumn(value)}",
                            operator,
                            value
                        )
                    }.toSqlExpression()

                inner.whereRaw("NOT EXISTS (${existsQuery.sql})", existsQuery.fill)

                val innerBuilder = QueryBuilder.selectBuilder("mtg.search_names")
                    .select("id")
                    .whereNull("mtg.search_names.print_face_translation_id")
                    .apply { applyNameCondition(inner, "mtg.search_names.${searchNameColumn(value)}", operator, value) }

                inner.whereIn(MtgCardFaceTranslation::id.columnName(), innerBuilder)
            }
        }
    }

    private fun searchNameColumn(value: QueryValue<*>): String = when {
        value is StringValue && value.exact -> "name"
        value is RegexValue -> "name"
        else -> "simple_name"
    }

    private fun printFaceTranslationColumn(value: QueryValue<*>): String = when {
        value is StringValue && value.exact -> MtgPrintFaceTranslation::flavorName.columnName()
        value is RegexValue -> MtgPrintFaceTranslation::flavorName.columnName()
        else -> MtgPrintFaceTranslation::simpleFlavorName.columnName()
    }

    private fun <T : WhereQueryBuilder<T>> applyNameCondition(
        builder: T,
        column: String,
        operator: SearchQueryOperator,
        value: QueryValue<*>
    ) {
        when (value) {
            is StringValue -> when (operator) {
                SearchQueryOperator.EQUALS -> builder.where(column, "ILIKE", value = value.value)
                SearchQueryOperator.CONTAINS -> builder.where(column, "ILIKE", value = "%${value.value}%")
                else -> throw IllegalStateException("Unsupported operator: $operator")
            }

            is RegexValue -> builder.where(column, "~*", value = value.value.pattern)

            else -> throw IllegalStateException("Unsupported value type: ${value::class.simpleName}")
        }
    }
}