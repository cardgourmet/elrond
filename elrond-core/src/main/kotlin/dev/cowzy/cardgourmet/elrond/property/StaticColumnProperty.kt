package dev.cowzy.cardgourmet.elrond.property

import dev.cowzy.cardgourmet.elrond.ColumnContext
import dev.cowzy.kuery.query.WhereQueryBuilder
import dev.cowzy.kuery.reflection.columnName
import dev.cowzy.kuery.reflection.table
import dev.cowzy.cardgourmet.elrond.descriptor.SimplePropertyDescriptor
import dev.cowzy.kuery.query.orWhereNotRaw
import dev.cowzy.kuery.query.whereNotNull
import kotlin.reflect.KProperty1

class StaticColumnProperty(
    private val column: KProperty1<*, Boolean?>,
    private val inverted: Boolean = false,
    descriptor: SimplePropertyDescriptor,
    key: String? = null
) : StaticSearchQueryProperty(arrayOf(column.table()), descriptor, key) {

    override suspend fun <T : WhereQueryBuilder<T>> applyCondition(builder: T, ctx: ColumnContext) {
        if (inverted) {
            builder.whereNull(ctx.resolve(column))
            builder.orWhereNotRaw(ctx.resolve(column))
        } else {
            builder.whereNotNull(ctx.resolve(column))
            builder.whereRaw(ctx.resolve(column))
        }
    }

}