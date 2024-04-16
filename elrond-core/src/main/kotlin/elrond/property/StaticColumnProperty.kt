package elrond.property

import dev.cowzy.kuery.query.WhereQueryBuilder
import dev.cowzy.kuery.query.whereNotRaw
import dev.cowzy.kuery.reflection.columnName
import dev.cowzy.kuery.reflection.table
import dev.cowzy.cardgourmet.elrond.descriptor.SimplePropertyDescriptor
import kotlin.reflect.KProperty1

class StaticColumnProperty(
    private val column: KProperty1<*, Boolean?>,
    private val inverted: Boolean = false,
    descriptor: SimplePropertyDescriptor,
) : StaticSearchQueryProperty(arrayOf(column.table()), descriptor) {

    override suspend fun <T : WhereQueryBuilder<T>> applyCondition(builder: T) {
        if (inverted) {
            builder.whereNotRaw(column.columnName())
        } else {
            builder.whereRaw(column.columnName())
        }
    }

}