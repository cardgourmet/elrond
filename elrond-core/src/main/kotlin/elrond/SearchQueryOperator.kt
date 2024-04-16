package elrond

enum class SearchQueryOperator(val value: String) {
    CONTAINS(":"),
    GREATER_THAN_OR_EQUALS(">="),
    GREATER_THAN(">"),
    LESS_THAN_OR_EQUALS("<="),
    LESS_THAN("<"),
    EQUALS("=");

    companion object {
        fun tryParse(value: String) = SearchQueryOperator.values().find { it.value == value }
    }
}

val numericQueryOperators = SearchQueryOperator.values()

val stringQueryOperators = arrayOf(
    SearchQueryOperator.CONTAINS,
    SearchQueryOperator.EQUALS,
)

fun SearchQueryOperator.toNumericSqlOperator() = when (this) {
    SearchQueryOperator.CONTAINS, SearchQueryOperator.EQUALS -> "="
    SearchQueryOperator.GREATER_THAN_OR_EQUALS -> ">="
    SearchQueryOperator.GREATER_THAN -> ">"
    SearchQueryOperator.LESS_THAN_OR_EQUALS -> "<="
    SearchQueryOperator.LESS_THAN -> "<"
}
