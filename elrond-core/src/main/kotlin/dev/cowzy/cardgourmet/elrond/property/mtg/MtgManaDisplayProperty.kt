package dev.cowzy.cardgourmet.elrond.property.mtg

import dev.cowzy.cardgourmet.commons.GenericManaValue
import dev.cowzy.cardgourmet.commons.ManaDisplay
import dev.cowzy.kuery.ColumnIndex
import dev.cowzy.kuery.query.WhereQueryBuilder
import dev.cowzy.kuery.query.orWhere
import dev.cowzy.kuery.reflection.columnName
import dev.cowzy.cardgourmet.commons.database.card.mtg.MtgCard
import dev.cowzy.cardgourmet.commons.database.card.mtg.MtgCardFace
import dev.cowzy.cardgourmet.commons.i18n.Strings
import dev.cowzy.cardgourmet.commons.toManaDisplays
import dev.cowzy.cardgourmet.elrond.*
import dev.cowzy.cardgourmet.elrond.descriptor.NumericDescriptor
import dev.cowzy.cardgourmet.elrond.property.SearchQueryProperty
import dev.cowzy.kuery.setNumber
import java.sql.PreparedStatement

class MtgManaDisplayProperty : SearchQueryProperty<List<ManaDisplay>>(
    numericQueryOperators,
    emptyArray(),
    arrayOf(MtgCardFace::class, MtgCard::class),
    NumericDescriptor(Strings.Query.Mtg.Property.MANA_DISPLAY, mapContainsTo = SearchQueryOperator.GREATER_THAN_OR_EQUALS),
) {

    override val valueDefinition = QueryValueDefinition<List<ManaDisplay>> {
        StringValue::class {
            format = "mtg_mana"
            transform { value -> value.value.toManaDisplays() }
            display { displays, _, _ -> "`${displays.joinToString("") { it.toString() }}`" }
        }
    }

    override suspend fun <T : WhereQueryBuilder<T>> applyCondition(
        builder: T,
        operator: SearchQueryOperator,
        value: List<ManaDisplay>
    ) {
        var generic = 0
        val specific = mutableMapOf<ManaDisplay, Int>()

        value.forEach {
            if (it.values.size == 1 && it.values.first() is GenericManaValue) {
                generic += (it.values.first() as GenericManaValue).amount
            } else {
                val key = it.copy(values = it.values.sortedBy { value ->
                    if (value is GenericManaValue) "${value.amount}" else value.type.symbol
                })

                specific[key] = specific.getOrPut(key) { 0 } + 1
            }
        }

        builder.where { it
            .where(MtgCard::layout, "!=", "transform")
            .orWhere(MtgCardFace::index, 0)
        }

        val specificEntries = specific.entries.sortedBy { it.key.simpleString }

        val sqlArray = "ARRAY[${specificEntries.map { (0 until it.value).map { "?::text" } }.flatten().joinToString()}]::text[]"
        val fillArray: (PreparedStatement, ColumnIndex) -> Unit = { stmt, index ->
            specificEntries.forEach { (key, value) ->
                repeat(value) { stmt.setString(index.getAndIncrement(), key.simpleString) }
            }
        }

        builder.where { inner ->
            when (operator) {
                SearchQueryOperator.CONTAINS, SearchQueryOperator.GREATER_THAN_OR_EQUALS -> {
                    inner
                        .whereRaw(MtgCardFace::manaDisplayParts, "@>", sqlArray, fill = fillArray)
                        .where(MtgCardFace::manaDisplayGeneric, ">=", generic)

                    specificEntries.forEach {
                        inner.whereRaw("cardinality(array_positions(${MtgCardFace::manaDisplayParts.columnName()}, ?)) >= ?") { stmt, index ->
                            stmt.setString(index.getAndIncrement(), it.key.simpleString)
                            stmt.setNumber(index.getAndIncrement(), it.value)
                        }
                    }
                }

                SearchQueryOperator.GREATER_THAN -> {
                    inner
                        .whereRaw(MtgCardFace::manaDisplayParts, "@>", sqlArray, fill = fillArray)
                        .where(MtgCardFace::manaDisplayGeneric, ">=", generic)

                    specificEntries.forEach {
                        inner.whereRaw("cardinality(array_positions(${MtgCardFace::manaDisplayParts.columnName()}, ?)) >= ?") { stmt, index ->
                            stmt.setString(index.getAndIncrement(), it.key.simpleString)
                            stmt.setNumber(index.getAndIncrement(), it.value)
                        }
                    }

                    inner.where { it
                        .where("cardinality(${MtgCardFace::manaDisplayParts.columnName()})", ">", specific.size)
                        .orWhere(MtgCardFace::manaDisplayGeneric, ">", generic)
                    }
                }

                SearchQueryOperator.LESS_THAN_OR_EQUALS -> {
                    inner
                        .whereRaw(MtgCardFace::manaDisplayParts, "<@", sqlArray, fill = fillArray)
                        .where(MtgCardFace::manaDisplayGeneric, "<=", generic)

                    specificEntries.forEach {
                        inner.whereRaw("cardinality(array_positions(${MtgCardFace::manaDisplayParts.columnName()}, ?)) <= ?") { stmt, index ->
                            stmt.setString(index.getAndIncrement(), it.key.simpleString)
                            stmt.setNumber(index.getAndIncrement(), it.value)
                        }
                    }
                }

                SearchQueryOperator.LESS_THAN -> {
                    inner
                        .whereRaw(MtgCardFace::manaDisplayParts, "<@", sqlArray, fill = fillArray)
                        .where(MtgCardFace::manaDisplayGeneric, "<=", generic)

                    specificEntries.forEach {
                        inner.whereRaw("cardinality(array_positions(${MtgCardFace::manaDisplayParts.columnName()}, ?)) <= ?") { stmt, index ->
                            stmt.setString(index.getAndIncrement(), it.key.simpleString)
                            stmt.setNumber(index.getAndIncrement(), it.value)
                        }
                    }

                    inner.where { it
                        .where("cardinality(${MtgCardFace::manaDisplayParts.columnName()})", "<", specific.size)
                        .orWhere(MtgCardFace::manaDisplayGeneric, "<", generic)
                    }
                }

                SearchQueryOperator.EQUALS -> inner
                    .whereRaw(MtgCardFace::manaDisplayParts, "=", sqlArray, fill = fillArray)
                    .where(MtgCardFace::manaDisplayGeneric, generic)
            }
        }
    }

}