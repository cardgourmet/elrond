package dev.cowzy.cardgourmet.elrond.user.property.list

import dev.cowzy.cardgourmet.commons.i18n.*
import dev.cowzy.cardgourmet.commons.user.*
import dev.cowzy.cardgourmet.elrond.*
import dev.cowzy.cardgourmet.elrond.property.*
import dev.cowzy.cardgourmet.tcg.config.card.TcgCardSearchQueryDistinctMode
import dev.cowzy.kuery.query.*
import dev.cowzy.kuery.reflection.table
import java.util.*
import kotlin.reflect.KProperty1

data class UserListKey(val slug: String, val username: String?)

class ListSlugProperty(
    private val printIdColumn: KProperty1<*, *>,
    private val printCardIdColumn: KProperty1<*, *>,
) : SearchQueryProperty<UserListKey>(
    supportedOperators = stringQueryOperators,
    affectedTables = arrayOf(printIdColumn.table(), printCardIdColumn.table()),
    descriptor = ListDescriptor(Strings.Query.Collection.Property.BINDER) // TODO
) {

    override val valueDefinition = QueryValueDefinition {
        StringValue::class {
            format = "slug_with_optional_username"

            transform {
                val parts = it.value.split("@")
                if (parts.size > 2) return@transform null
                val slug = parts[0]
                val username = if (parts.size == 2) parts[1] else null
                UserListKey(slug.lowercase(), username)
            }
        }
    }

    private fun SelectQueryBuilder.applyListCondition(value: UserListKey, ctx: ExecutionContext): SelectQueryBuilder = this.apply {
        this.where(UserListResource::resourceType, ListResourceType.CARD)

        if (value.username != null) {
            try {
                val id = UUID.fromString(value.username)
                this.where(UserList::userId, id)
            } catch (_: IllegalArgumentException) {
                this.innerJoin(User::class) { it.whereColumn(UserList::userId, User::id) }
                this.where(User::username, operator = "ILIKE", value.username)
            }

            this.whereIn(UserList::visibility, values = listOf(Visibility.PUBLIC)) // TODO: add unlisted once available
        } else if (ctx.principal is User) {
            this.where(UserList::userId, (ctx.principal as User).id)
        } else {
            this.whereRaw("FALSE") // No username provided and not authenticated, so no results
        }

        this.where(UserList::slug, value.slug.lowercase())
    }

    override suspend fun <T : WhereQueryBuilder<T>> applyCondition(
        builder: T,
        operator: SearchQueryOperator,
        value: UserListKey,
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