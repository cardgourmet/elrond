package dev.cowzy.cardgourmet.elrond.property.mtg

import dev.cowzy.kuery.query.*
import dev.cowzy.kuery.reflection.columnName
import dev.cowzy.cardgourmet.commons.database.card.mtg.*
import dev.cowzy.cardgourmet.commons.i18n.Strings
import dev.cowzy.cardgourmet.commons.toSimpleString
import dev.cowzy.cardgourmet.elrond.*
import dev.cowzy.cardgourmet.elrond.descriptor.StringDescriptor
import dev.cowzy.cardgourmet.elrond.property.SearchQueryProperty

class MtgNameProperty : SearchQueryProperty<QueryValue<*>>(
    supportedOperators = stringQueryOperators,
    affectedTables = arrayOf(MtgCardFaceTranslation::class),
    descriptor = StringDescriptor(Strings.Query.Property.NAME)
) {

    private val alias = createSqlAlias()

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

//    override fun applyProperty(builder: SelectQueryBuilder) {
//        builder.joinRaw("INNER JOIN mtg.search_names AS $alias ON $alias.id = ${MtgCardFaceTranslation::id.columnName()}")
//        builder.orderBy("$alias.priority")
//    }

    override suspend fun <T : WhereQueryBuilder<T>> applyCondition(
        builder: T,
        operator: SearchQueryOperator,
        value: QueryValue<*>
    ) {
        val innerBuilder = QueryBuilder.selectBuilder("mtg.search_names")
            .select("id")
            .orderBy("mtg.search_names.priority")

        when (value) {
            is StringValue -> {
                val nameColumn = when {
                    value.exact && operator == SearchQueryOperator.CONTAINS -> "$alias.name"
                    value.exact -> "UPPER($alias.name)"
                    else -> "$alias.simple_name"
                }

                when (operator) {
                    SearchQueryOperator.EQUALS -> innerBuilder.where(nameColumn, "=", value = if (value.exact) value.value.uppercase() else value.value)
                    SearchQueryOperator.CONTAINS -> innerBuilder.where(nameColumn, "ILIKE", value = "%${value.value}%")
                    else -> throw IllegalStateException("Unsupported operator: $operator")
                }
            }

            is RegexValue -> {
                val pattern = value.value.pattern
                innerBuilder.where("$alias.name", "~*", value = pattern)
            }

            else -> throw IllegalStateException("Unsupported value type: ${value::class.simpleName}")
        }

        builder.whereIn(MtgCardFaceTranslation::id.columnName(), innerBuilder)

//        if (value is StringValue) {
//            val nameColumn = when {
//                value.exact && operator == SearchQueryOperator.CONTAINS -> "$alias.name"
//                value.exact -> "UPPER($alias.name)"
//                else -> "$alias.simple_name"
//            }
//
//            when (operator) {
//                SearchQueryOperator.EQUALS -> builder.where(nameColumn, "=", value = if (value.exact) value.value.uppercase() else value.value)
//                SearchQueryOperator.CONTAINS -> builder.where(nameColumn, "ILIKE", value = "%${value.value}%")
//                else -> throw IllegalStateException("Unsupported operator: $operator")
//            }
//        } else if (value is RegexValue) {
//            val pattern = value.value.pattern
//            builder.where("$alias.name", "~*", value = pattern)
//        }
    }

}