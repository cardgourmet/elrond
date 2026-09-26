package dev.cowzy.cardgourmet.elrond.user.property.list

import dev.cowzy.cardgourmet.commons.i18n.*
import dev.cowzy.cardgourmet.commons.user.*
import dev.cowzy.cardgourmet.elrond.*
import dev.cowzy.cardgourmet.elrond.descriptor.*
import dev.cowzy.cardgourmet.elrond.property.*
import dev.cowzy.kuery.query.*
import java.util.UUID

data class UserListKey(val slug: String, val username: String?)

class ListSlugProperty(
) : SearchQueryProperty<UserListKey>(
    supportedOperators = stringQueryOperators,
    affectedTables = arrayOf(UserList::class, User::class),
    descriptor = StringDescriptor(Strings.Query.Collection.Property.BINDER) // TODO
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

    override suspend fun <T : WhereQueryBuilder<T>> applyCondition(
        builder: T,
        operator: SearchQueryOperator,
        value: UserListKey,
        ctx: ExecutionContext
    ) {
        if (value.username != null) {
            try {
                val id = UUID.fromString(value.username)
                builder.where(ctx.resolve(UserList::userId), id)
            } catch (_: IllegalArgumentException) {
                builder.where(ctx.resolve(User::username), operator = "ILIKE", value.username)
            }

            builder.whereIn(ctx.resolve(UserList::visibility), values = listOf(Visibility.PUBLIC)) // TODO: add unlisted once available
        } else {
            builder.where(ctx.resolve(UserList::userId), (ctx.principal as User).id)
        }

        builder.where(ctx.resolve(UserList::slug), value.slug.lowercase())
    }
}