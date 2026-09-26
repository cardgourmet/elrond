package dev.cowzy.cardgourmet.elrond.user.config

import dev.cowzy.cardgourmet.chef.commons.model.*
import dev.cowzy.cardgourmet.commons.user.*
import dev.cowzy.cardgourmet.elrond.config.*
import dev.cowzy.cardgourmet.elrond.user.property.list.*
import dev.cowzy.kuery.query.*
import dev.cowzy.kuery.reflection.*
import kotlin.reflect.*

fun SearchQueryFilterBuilder.configurePrincipalSearchQueryFilters(
    findListBySlug: suspend (User?, String) -> ListDetails?,
    getUserLists: suspend (User) -> List<ListDetails>
) {
    val listSlugValueProvider = UserListValueProvider(findListBySlug, getUserLists)
    val listSlugProperty = ListSlugProperty()
    val listIdProperty = ListIdProperty()

    filter("list") {
        property(listSlugProperty, listSlugValueProvider)
        property(listIdProperty)
    }

    filter("listid") {
        property(listIdProperty)
    }

    filter("listslug") {
        property(listSlugProperty, listSlugValueProvider)
    }
}

fun SearchQuerySqlConfig.withPrincipalContext(game: GameType, printIdColumn: KProperty1<*, *>): SearchQuerySqlConfig {
    return this.copy(
        tableDependencies = this.tableDependencies + mapOf(
            UserListResource::class to TableDependency(printIdColumn.table()) { builder, ctx ->
                builder.innerJoin(UserListResource::class) {
                    it.where(ctx.resolve(UserListResource::game), game)
                    it.where(ctx.resolve(UserListResource::resourceType), ListResourceType.CARD)
                    it.whereColumn(ctx.resolve(UserListResource::resourceId), ctx.resolve(printIdColumn))
                }
            },
            UserList::class to TableDependency(UserListResource::class) { builder, ctx ->
                builder.innerJoin(UserList::class) {
                    it.whereColumn(ctx.resolve(UserListResource::listId), ctx.resolve(UserList::id))
                }
            },
            User::class to TableDependency(UserList::class) { builder, ctx ->
                builder.innerJoin(User::class) {
                    it.whereColumn(ctx.resolve(UserList::userId), ctx.resolve(User::id))
                }
            },
        )
    )
}
