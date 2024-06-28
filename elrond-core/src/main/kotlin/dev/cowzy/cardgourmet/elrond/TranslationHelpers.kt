package dev.cowzy.cardgourmet.elrond

import dev.cowzy.cardgourmet.commons.i18n.LocalizationService
import dev.cowzy.cardgourmet.commons.i18n.Strings
import dev.cowzy.cardgourmet.commons.i18n.UserLanguage
import dev.cowzy.cardgourmet.elrond.query.*
import dev.cowzy.cardgourmet.elrond.tokenizer.LogicalOperator
import kotlinx.serialization.Serializable
import java.text.NumberFormat
import java.util.Locale

@Serializable
data class ExplainResult(
    val description: String,
    val filters: List<String>
)

suspend fun QueryExpression.explainCondition(
    i18n: LocalizationService,
    locale: UserLanguage,
    isTopLevel: Boolean = true,
    negate: Boolean = false
): ExplainResult? {
    return when (this) {
        is PropertyQueryExpression -> {
            ExplainResult(
                description = this.property.descriptor.describe(this, negate, locale, i18n),
                filters = listOf(this.property.descriptor.propertyKey.split(".").last())
            )
        }
        is QueryExpressionGroup -> {
            val negated = if (negate) !this.negate else this.negate

            val explanations = this.children.mapNotNull {
                it.explainCondition(
                    i18n,
                    locale,
                    false,
                    negated
                )
            }

            val operator = when {
                this.operator == LogicalOperator.AND && !negated -> Strings.Query.Operator.AND
                this.operator == LogicalOperator.AND && negated -> Strings.Query.Operator.OR
                this.operator == LogicalOperator.OR && !negated -> Strings.Query.Operator.OR
                else -> Strings.Query.Operator.AND
            }

            var combined = explanations.firstOrNull() ?: return null
            explanations.drop(1).forEach {
                combined = ExplainResult(
                    description = i18n.translate(locale, operator, combined.description, it.description),
                    filters = combined.filters + it.filters
                )
            }

            return when {
                !isTopLevel && explanations.size > 1 -> combined.copy(description = "(${combined.description})")
                else -> combined
            }
        }

        else -> null
    }?.let { it.copy(description = it.description.trim()) }
}

suspend fun QueryExpression.explain(
    i18n: LocalizationService,
    locale: UserLanguage,
    subjectKey: String,
    amount: Int?, estimate: Boolean,
    withExtras: Boolean,
    preferredLanguageKey: String?,
): ExplainResult {
    val condition = this.explainCondition(i18n, locale)

    val numberFormat = NumberFormat.getInstance(locale.toLocale())
    val formattedAmount = amount?.let { numberFormat.format(it) }?.let { if (estimate) "~$it" else it }

    val mappedSubject = i18n.translate(
        locale,
        if (amount == 1) "$subjectKey.singular" else "$subjectKey.plural",
        formattedAmount ?: ""
    )

    val subjectWithExtras = when {
        withExtras -> i18n.translate(locale, Strings.Query.Skeleton.WITH_EXTRAS, mappedSubject)
        else -> i18n.translate(locale, Strings.Query.Skeleton.WITHOUT_EXTRAS, mappedSubject)
    }.trim().replaceFirstChar { it.uppercase() }

    val subjectWithLanguage = when {
        preferredLanguageKey == null -> subjectWithExtras
        else -> {
            val language = i18n.translate(locale, preferredLanguageKey)
            i18n.translate(locale, Strings.Query.Skeleton.WITH_PREFERRED_LANGUAGE, subjectWithExtras, language)
        }
    }

    return ExplainResult(
        description = when {
            condition != null -> i18n.translate(
                locale,
                if (amount == 1) "$subjectKey.with_condition_singular" else "$subjectKey.with_condition_plural",
                subjectWithLanguage,
                condition.description
            )

            else -> i18n.translate(locale, "$subjectKey.without_condition", subjectWithLanguage)
        },
        filters = condition?.filters ?: emptyList()
    )
}

private fun UserLanguage.toLocale() = when (this) {
    UserLanguage.ENGLISH -> Locale.ENGLISH
    UserLanguage.GERMAN -> Locale.GERMAN
    else -> Locale.ENGLISH
}
