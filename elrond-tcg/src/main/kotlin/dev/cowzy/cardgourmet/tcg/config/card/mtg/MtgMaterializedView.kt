package dev.cowzy.cardgourmet.tcg.config.card.mtg

import dev.cowzy.cardgourmet.chef.commons.model.card.mtg.*
import dev.cowzy.cardgourmet.elrond.config.*
import dev.cowzy.kuery.reflection.table
import dev.cowzy.kuery.reflection.tableName
import kotlin.reflect.*

private val tables = setOf(
    MtgCard::class,
    MtgCardFace::class,
    MtgCardFaceTranslation::class,
    MtgPrint::class,
    MtgPrintFace::class,
    MtgPrintFaceTranslation::class,
)

val MtgMaterializedView = MaterializedView(
    table = "mtg.mv_absolute_unit",
    coveredTables = tables,
    columnMappings = generateColumnMappings()
)

private fun generateColumnMappings(): Map<KProperty1<*, *>, String> {
    val columns = tables.flatMap { table -> table.table().columns }
    return columns.associate { it.property to "${it.table.tableName(false)}_${it.name}" }
}