package dev.cowzy.cardgourmet.elrond.user.config

import dev.cowzy.cardgourmet.chef.commons.model.*
import dev.cowzy.cardgourmet.commons.user.*
import dev.cowzy.cardgourmet.elrond.config.*
import dev.cowzy.cardgourmet.elrond.user.property.list.*
import dev.cowzy.kuery.query.*
import dev.cowzy.kuery.reflection.*
import kotlin.reflect.*

fun SearchQueryFilterBuilder.configurePrincipalSearchQueryFilters(
    printIdColumn: KProperty1<*, *>,
    printCardIdColumn: KProperty1<*, *>,
    findListBySlug: suspend (User?, String) -> ListDetails?,
    getUserLists: suspend (User) -> List<ListDetails>
) {
    val listSlugValueProvider = UserListValueProvider(findListBySlug, getUserLists)
    val listSlugProperty = ListSlugProperty(printIdColumn, printCardIdColumn)
    val listIdProperty = ListIdProperty(printIdColumn, printCardIdColumn)

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
                builder.leftJoin(UserListResource::class) {
                    it.where(UserListResource::game, game)
                    it.where(UserListResource::resourceType, ListResourceType.CARD)
                    it.whereColumn(UserListResource::resourceId, ctx.resolve(printIdColumn))
                }
            },
            UserList::class to TableDependency(UserListResource::class) { builder, ctx ->
                builder.leftJoin(UserList::class) {
                    it.whereColumn(UserListResource::listId, UserList::id)
                }
            },
            User::class to TableDependency(UserList::class) { builder, ctx ->
                builder.leftJoin(User::class) {
                    it.whereColumn(UserList::userId, User::id)
                }
            },
        )
    )
}
