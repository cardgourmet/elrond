package elrond

import dev.cowzy.kuery.Order
import kotlin.reflect.KProperty1

data class ElrondSortColumn(val property: KProperty1<*, *>, val order: Order, val flipped: Boolean = false) {
    var sortName = "sort_${elrond.createSqlAlias(4)}"
}

fun elrond.ElrondSortColumn.flipped() = this.copy(
    order = when (this.order) {
        Order.ASCENDING -> Order.DESCENDING
        Order.DESCENDING -> Order.ASCENDING
    },
    flipped = !this.flipped
).also {
    it.sortName = this.sortName
}

fun Array<out elrond.SortColumnDefinition>.parseSortColumns(value: String): List<elrond.ElrondSortColumn> {
    return value.split(",").map {
        val parts = it.split(".")

        val order = when {
            parts.size == 1 || parts[1].equals("asc", ignoreCase = true) -> Order.ASCENDING
            parts.size == 2 && parts[1].equals("desc", ignoreCase = true) -> Order.DESCENDING
            else -> throw elrond.ElrondParseException("Invalid sort order: ${parts[1]}. Valid orders are: [asc, desc]")
        }

        val entry = this.find { entry ->
            val ignoreRegex = Regex("[_-]")
            entry.keyword.replace(ignoreRegex, "").equals(parts[0].replace(ignoreRegex, ""), ignoreCase = true)
        } ?: throw elrond.ElrondParseException("Invalid sort column: ${parts[0]}. Valid columns are: [${this.joinToString { entry -> entry.keyword }}]")

        entry.properties.map { property -> elrond.ElrondSortColumn(property, order) }
    }.flatten()
}

interface SortColumnDefinition {
    val keyword: String
    val properties: Array<KProperty1<*, *>>
}

class ElrondParseException(message: String) : Exception(message)
