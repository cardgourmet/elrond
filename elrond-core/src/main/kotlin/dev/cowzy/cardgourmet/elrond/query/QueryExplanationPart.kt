package dev.cowzy.cardgourmet.elrond.query

import dev.cowzy.cardgourmet.elrond.SearchQueryOperator
import dev.cowzy.cardgourmet.elrond.tokenizer.LogicalOperator
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class QueryExplanationPart(
    val type: QueryExplanationType,
    val negate: Boolean,

    val groupOperator: LogicalOperator? = null,
    val children: List<QueryExplanationPart>? = null,

    val filter: String? = null,
    val filterOperator: SearchQueryOperator? = null,
    val property: String? = null,

    val value: String? = null,
    val valueType: QueryExplanationValueType? = null,
    val valueProperty: String? = null,
)

@Serializable
enum class QueryExplanationValueType {
    @SerialName("string") STRING,
    @SerialName("number") NUMBER,
    @SerialName("regex") REGEX,
    @SerialName("filter") FILTER,
}

@Serializable
enum class QueryExplanationType {
    @SerialName("group") GROUP,
    @SerialName("filter") FILTER,
}
