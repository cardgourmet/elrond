package dev.cowzy.cardgourmet.elrond.user.property.list

import dev.cowzy.cardgourmet.commons.i18n.*
import dev.cowzy.cardgourmet.commons.user.*
import dev.cowzy.cardgourmet.elrond.*
import dev.cowzy.cardgourmet.elrond.descriptor.*
import dev.cowzy.cardgourmet.elrond.property.*
import dev.cowzy.kuery.query.*
import java.util.*

class ListIdProperty : SearchQueryProperty<UUID>(
    supportedOperators = stringQueryOperators,
    affectedTables = arrayOf(UserList::class),
    descriptor = StringDescriptor(Strings.Query.Collection.Property.BINDER_ID) // TODO
) {

    override val valueDefinition = QueryValueDefinition {
        StringValue::class {
            format = "uuid"
            transform { value ->
                runCatching { UUID.fromString(value.value) }.getOrNull()
            }
        }
    }

    override suspend fun <T : WhereQueryBuilder<T>> applyCondition(
        builder: T,
        operator: SearchQueryOperator,
        value: UUID,
        ctx: ExecutionContext
    ) {
        builder.where(UserList::id, value)
        builder.where {
            it.where(UserList::userId, (ctx.principal as User).id)
            it.orWhere(UserList::visibility, UserListVisibility.PUBLIC) // TODO: add unlisted once available
        }
    }
}