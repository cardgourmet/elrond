package dev.cowzy.cardgourmet.elrond.user.property.list

import dev.cowzy.cardgourmet.commons.user.User
import dev.cowzy.cardgourmet.elrond.ContextAttributes
import dev.cowzy.cardgourmet.elrond.user.UserKey
import dev.cowzy.cardgourmet.elrond.values.ProvidedValue
import dev.cowzy.cardgourmet.elrond.values.ResolvedValue
import dev.cowzy.cardgourmet.elrond.values.ValueProvider

class UserListValueProvider(
    private val findListBySlug: suspend (User?, String) -> ListDetails?,
    private val getUserLists: suspend (User) -> List<ListDetails>
) : ValueProvider<UserListKey>(false) {

    override suspend fun getValues(
        attributes: ContextAttributes,
        language: String?
    ): Iterable<ProvidedValue<UserListKey>> {
        val user = attributes[UserKey] ?: return emptyList()
        return getUserLists(user).map {
            ProvidedValue(
                input = it.slug,
                aliases = mutableSetOf(it.name),
                resolvesTo = ResolvedValue(
                    display = it.name,
                    value = UserListKey(it.slug, user.username),
                    operator = null
                ),
                type = "list_slug",
                languages = mutableSetOf()
            )
        }
    }

    override suspend fun findValue(
        attributes: ContextAttributes,
        value: String
    ): ProvidedValue<UserListKey>? {
        return findListBySlug(attributes[UserKey], value)?.let {
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