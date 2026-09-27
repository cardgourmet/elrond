package dev.cowzy.cardgourmet.elrond.user.property.list

import dev.cowzy.cardgourmet.commons.i18n.*
import dev.cowzy.cardgourmet.commons.user.*
import dev.cowzy.cardgourmet.elrond.*
import dev.cowzy.cardgourmet.elrond.property.*
import dev.cowzy.cardgourmet.tcg.config.card.*
import dev.cowzy.kuery.query.*
import dev.cowzy.kuery.reflection.*
import java.util.*
import kotlin.reflect.*

class ListIdProperty(
    private val printIdColumn: KProperty1<*, *>,
    private val printCardIdColumn: KProperty1<*, *>,
) : SearchQueryProperty<UUID>(
    supportedOperators = stringQueryOperators,
    affectedTables = arrayOf(printIdColumn.table()),
    descriptor = ListDescriptor(Strings.Query.Collection.Property.BINDER_ID) // TODO
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
        val useCardId = ctx.searchQuery.distinctMode != TcgCardSearchQueryDistinctMode.UNIQUE_PRINTS && ctx.searchQuery.distinctMode != TcgCardSearchQueryDistinctMode.UNIQUE_PRINT_FACES

        if (useCardId) {
            val cardIds = UserList::class.selectBuilder()
                .select(printCardIdColumn)
                .innerJoin(UserListResource::class) { it.whereColumn(UserList::id, UserListResource::listId) }
                .innerJoin(printIdColumn.table()) { it.whereColumn(UserListResource::resourceId, printIdColumn) }
                .applyListCondition(value, ctx)

            builder.whereIn(ctx.resolve(printCardIdColumn), cardIds)
        } else {
            val printIds = UserList::class.selectBuilder()
                .innerJoin(UserListResource::class) { it.whereColumn(UserList::id, UserListResource::listId) }
                .select(UserListResource::resourceId)
                .applyListCondition(value, ctx)

            builder.whereIn(ctx.resolve(printIdColumn), printIds)
        }
    }

    private fun SelectQueryBuilder.applyListCondition(value: UUID, ctx: ExecutionContext): SelectQueryBuilder = this.apply {
        this.where(UserListResource::resourceType, ListResourceType.CARD)
        this.where(UserList::id, value)
        this.where {
            if (ctx.principal is User) {
                it.where(UserList::userId, (ctx.principal as User).id)
            }

            it.orWhere(UserList::visibility, UserListVisibility.PUBLIC) // TODO: add unlisted once available
        }
    }
}