package dev.cowzy.cardgourmet.elrond.query

import dev.cowzy.cardgourmet.commons.database.query.SearchQueryStatistics
import dev.cowzy.cardgourmet.commons.getSerialName
import java.time.OffsetDateTime
import java.util.*

data class MeasureResult<T>(
    val result: T,
    val duration: Long
)

private suspend fun <T> measure(execute: suspend () -> T): MeasureResult<T> {
    val start = System.currentTimeMillis()
    val result = execute()
    val end = System.currentTimeMillis()
    return MeasureResult(result, end - start)
}

suspend fun <T : Enum<T>> SearchQuery<T>.toStatistics(
    queryId: UUID,
    mode: SearchQueryMode,
    cached: Boolean,
    attempt: Int,
    client: String,
    userId: UUID?,
    userAgent: String?,
    execute: suspend (SearchQuery<T>) -> Pair<T, Int>
): SearchQueryStatistics {
    val result = measure { execute(this) }

    return SearchQueryStatistics(
        id = UUID.randomUUID(),
        queryId = queryId,
        userId = userId,
        rawQuery = this.expression.toExpressionString(),
        searchMode = mode.getSerialName(),
        results = result.result.second,
        executionTime = result.duration,
        language = this.preferredLanguage,
        sortMode = (this.sorting.mode as Enum<*>).getSerialName(),
        sortDirection = this.sorting.order,
        flags = this.flags.map { it.getSerialName() },
        distinctMode = this.distinctMode.getSerialName(),
        attempt = attempt,
        createdAt = OffsetDateTime.now(),
        client = client,
        userAgent = userAgent,
        cached = cached
    )
}
