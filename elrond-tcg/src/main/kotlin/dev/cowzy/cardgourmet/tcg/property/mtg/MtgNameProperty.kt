package dev.cowzy.cardgourmet.tcg.property.mtg

import dev.cowzy.cardgourmet.chef.commons.model.card.mtg.*
import dev.cowzy.cardgourmet.commons.*
import dev.cowzy.cardgourmet.commons.i18n.*
import dev.cowzy.cardgourmet.elrond.*
import dev.cowzy.cardgourmet.elrond.descriptor.*
import dev.cowzy.cardgourmet.elrond.property.*
import dev.cowzy.cardgourmet.elrond.tokenizer.*
import dev.cowzy.kuery.query.*
import kotlin.reflect.KProperty1

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
        value: QueryValue<*>,
        ctx: ColumnContext
    ) {
        builder.applyNameSearchTerm(operator, value, ctx)
    }

    override suspend fun <T : WhereQueryBuilder<T>> applyMultipleConditions(
        builder: T,
        operator: LogicalOperator,
        conditions: List<Pair<SearchQueryOperator, QueryValue<*>>>,
        ctx: ColumnContext
    ) {
        conditions.forEach { (op, value) ->
            when (operator) {
                LogicalOperator.AND -> builder.where { it.applyNameSearchTerm(op, value, ctx) }
                else -> builder.orWhere { it.applyNameSearchTerm(op, value, ctx) }
            }
        }
    }

    private fun <T : WhereQueryBuilder<T>> T.applyNameSearchTerm(
        operator: SearchQueryOperator,
        value: QueryValue<*>,
        ctx: ColumnContext
    ): T {
        return this
            .where { applyNameCondition(it, printFaceTranslationColumn(value), operator, value, ctx) }
            .orWhere { applyNameCondition(it, cardFaceTranslationColumn(value), operator, value, ctx) }
            .orWhere { applyNameCondition(it, MtgCard::name, operator, value, ctx) }
    }

    private fun <T : WhereQueryBuilder<T>> applyNameCondition(
        builder: T,
        column: KProperty1<*, *>,
        operator: SearchQueryOperator,
        value: QueryValue<*>,
        ctx: ColumnContext
    ) {
        when (value) {
            is StringValue -> when (operator) {
                SearchQueryOperator.EQUALS -> builder.where(ctx.resolve(column), "ILIKE", value = value.value)
                SearchQueryOperator.CONTAINS -> builder.where(ctx.resolve(column), "ILIKE", value = "%${value.value}%")
                else -> throw IllegalStateException("Unsupported operator: $operator")
            }

            is RegexValue -> builder.where(ctx.resolve(column), "~*", value = value.value.pattern)

            else -> throw IllegalStateException("Unsupported value type: ${value::class.simpleName}")
        }
    }

    private fun cardFaceTranslationColumn(value: QueryValue<*>): KProperty1<*, *> = when {
        value is StringValue && value.exact -> MtgCardFaceTranslation::name
        value is RegexValue -> MtgCardFaceTranslation::name
        else -> MtgCardFaceTranslation::simpleName
    }

    private fun printFaceTranslationColumn(value: QueryValue<*>): KProperty1<*, *> = when {
        value is StringValue && value.exact -> MtgPrintFaceTranslation::flavorName
        value is RegexValue -> MtgPrintFaceTranslation::flavorName
        else -> MtgPrintFaceTranslation::simpleFlavorName
    }
}