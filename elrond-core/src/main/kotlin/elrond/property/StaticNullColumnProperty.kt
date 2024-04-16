package elrond.property

import dev.cowzy.cardgourmet.elrond.descriptor.SimplePropertyDescriptor
import dev.cowzy.kuery.query.WhereQueryBuilder
import dev.cowzy.kuery.query.whereNotNull
import dev.cowzy.kuery.reflection.table
import kotlin.reflect.KProperty1

class StaticNullColumnProperty(
    private val column: KProperty1<*, *>,
    private val inverted: Boolean = false,
    descriptor: SimplePropertyDescriptor,
) : StaticSearchQueryProperty(arrayOf(column.table()), descriptor) {

    override suspend fun <T : WhereQueryBuilder<T>> applyCondition(builder: T) {
        if (inverted) {
            builder.whereNotNull(column)
        } else {
            builder.whereNull(column)
        }
    }

}