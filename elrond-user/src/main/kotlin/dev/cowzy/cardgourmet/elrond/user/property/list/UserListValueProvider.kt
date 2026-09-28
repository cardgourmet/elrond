package dev.cowzy.cardgourmet.elrond.user.property.list

import dev.cowzy.cardgourmet.commons.user.User
import dev.cowzy.cardgourmet.elrond.ContextAttributes
import dev.cowzy.cardgourmet.elrond.user.UserKey
import dev.cowzy.cardgourmet.elrond.values.ProvidedValue
import dev.cowzy.cardgourmet.elrond.values.ResolvedValue
import dev.cowzy.cardgourmet.elrond.values.ValueProvider

class UserListValueProvider(
    private val findListBySlugAndUsername: suspend (User?, String, String?) -> ListDetails?,
    private val getUserLists: suspend (User) -> List<ListDetails>
) : ValueProvider<ListDetails>(true) {

    override suspend fun getValues(
        attributes: ContextAttributes,
        language: String?
    ): Iterable<ProvidedValue<ListDetails>> {
        val user = attributes[UserKey] ?: return emptyList()

        return getUserLists(user).map {
            ProvidedValue(
                input = it.slug,
                aliases = mutableSetOf(it.name),
                resolvesTo = ResolvedValue(
                    display = it.name,
                    value = it,
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
    ): ProvidedValue<ListDetails>? {
        val user = attributes[UserKey]

        val parts = value.split("@")
        val slug = parts.first()
        val username = parts.getOrNull(1)

        return findListBySlugAndUsername(user, slug, username)?.let {
            ProvidedValue(
                input = it.slug,
                aliases = mutableSetOf(it.name),
                resolvesTo = ResolvedValue(
                    display = it.name,
                    value = it.copy(username = if (it.username == user?.username) null else it.username),
                    operator = null
                ),
                type = "list_slug",
                languages = mutableSetOf()
            )
        }
    }

}