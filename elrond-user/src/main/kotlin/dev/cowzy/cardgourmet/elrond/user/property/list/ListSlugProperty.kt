package dev.cowzy.cardgourmet.elrond.user.property.list

import dev.cowzy.cardgourmet.commons.i18n.*
import dev.cowzy.cardgourmet.commons.user.*
import dev.cowzy.cardgourmet.elrond.*
import dev.cowzy.cardgourmet.elrond.property.*
import dev.cowzy.cardgourmet.elrond.user.UserKey
import dev.cowzy.cardgourmet.tcg.config.card.TcgCardSearchQueryDistinctMode
import dev.cowzy.kuery.query.*
import dev.cowzy.kuery.reflection.table
import java.util.*
import kotlin.reflect.KProperty1

data class UserListKey(val slug: String, val username: String?)

class ListSlugProperty(
    private val printIdColumn: KProperty1<*, *>,
    private val printCardIdColumn: KProperty1<*, *>,
) : SearchQueryProperty<ListDetails>(
    supportedOperators = stringQueryOperators,
    affectedTables = arrayOf(printIdColumn.table(), printCardIdColumn.table()),
    descriptor = ListDescriptor(Strings.Query.Collection.Property.BINDER) // TODO
) {

    override val valueDefinition = QueryValueDefinition<ListDetails> {
        StringValue::class()
    }

    private fun SelectQueryBuilder.applyListCondition(value: ListDetails, ctx: ExecutionContext): SelectQueryBuilder = this.apply {
        this.where(UserListResource::resourceType, ListResourceType.CARD)
        this.where(UserList::id, value.id)
        this.where { inner ->
            inner.whereIn(UserList::visibility, values = listOf(Visibility.PUBLIC)) // TODO: add unlisted once available

            val user = ctx.attributes[UserKey]
            if (user != null) {
                inner.orWhere(UserList::userId, user.id)
            }
        }
    }

    override suspend fun <T : WhereQueryBuilder<T>> applyCondition(
        builder: T,
        operator: SearchQueryOperator,
        value: ListDetails,
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
}