package dev.cowzy.cardgourmet.elrond.user.property.list

import dev.cowzy.cardgourmet.commons.user.User
import dev.cowzy.cardgourmet.elrond.values.ProvidedValue
import dev.cowzy.cardgourmet.elrond.values.ResolvedValue
import dev.cowzy.cardgourmet.elrond.values.ValueProvider

class UserListValueProvider(
    private val findListBySlug: suspend (User?, String) -> ListDetails?,
    private val getUserLists: suspend (User) -> List<ListDetails>
) : ValueProvider<UserListKey, User>(false) {

    override suspend fun getValues(
        principal: User?,
        language: String?
    ): Iterable<ProvidedValue<UserListKey>> {
        if (principal == null) return emptyList()
        return getUserLists(principal).map {
            ProvidedValue(
                input = it.slug,
                aliases = mutableSetOf(it.name),
                resolvesTo = ResolvedValue(
                    display = it.name,
                    value = UserListKey(it.slug, principal.username),
                    operator = null
                ),
                type = "list_slug",
                languages = mutableSetOf()
            )
        }
    }

    override suspend fun findValue(
        principal: User?,
        value: String
    ): ProvidedValue<UserListKey>? {
        return findListBySlug(principal, value)?.let {
            ProvidedValue(
                input = it.slug,
                aliases = mutableSetOf(it.name),
                resolvesTo = ResolvedValue(
                    display = it.name,
                    value = UserListKey(it.slug, it.username),
                    operator = null
                ),
                type = "list_slug",
                languages = mutableSetOf()
            )
        }
    }

}