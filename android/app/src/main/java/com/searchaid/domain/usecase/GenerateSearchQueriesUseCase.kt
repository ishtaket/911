package com.searchaid.domain.usecase

import com.searchaid.domain.model.IdentityPack
import javax.inject.Inject

/**
 * Generates ranked search query strings from an IdentityPack.
 * Queries are ordered by expected relevance: exact name + region first,
 * then name variants, aliases, handles, emails.
 */
class GenerateSearchQueriesUseCase @Inject constructor() {

    operator fun invoke(pack: IdentityPack): List<String> {
        val queries = mutableListOf<String>()

        // 1. Primary name + region (highest value)
        if (pack.region != null) {
            queries.add("\"${pack.primaryName}\" ${pack.region}")
        }

        // 2. Primary name alone
        queries.add("\"${pack.primaryName}\"")

        // 3. Primary name + age context
        if (pack.age != null) {
            queries.add("${pack.primaryName} ${pack.age} лет")
        }

        // 4. Name variants (transliterated, reordered) — skip primary to avoid dupes
        pack.nameVariants
            .filter { it != pack.primaryName && it.contains(" ") }
            .forEach { variant ->
                queries.add("\"$variant\"")
            }

        // 5. Aliases/nicknames as quoted search
        pack.aliases.forEach { alias ->
            queries.add("\"$alias\"")
        }

        // 6. Handles (social usernames)
        pack.handles.forEach { handle ->
            queries.add(handle)
        }

        // 7. Emails
        pack.emails.forEach { email ->
            queries.add("\"$email\"")
        }

        // 8. Phones
        pack.phones.forEach { phone ->
            queries.add("\"$phone\"")
        }

        return queries.distinct()
    }
}
