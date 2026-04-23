package com.searchaid.domain.usecase

import com.searchaid.domain.identity.NameNormalizer
import com.searchaid.domain.model.IdentityPack
import com.searchaid.domain.model.PersonProfile
import com.searchaid.domain.model.SocialSource
import javax.inject.Inject

class BuildIdentityPackUseCase @Inject constructor(
    private val normalizer: NameNormalizer,
) {
    operator fun invoke(
        profile: PersonProfile,
        sources: List<SocialSource> = emptyList(),
    ): IdentityPack {
        val nameVariants = mutableSetOf<String>()

        // Normalize primary name
        nameVariants.addAll(normalizer.normalize(profile.name))

        // Normalize each alias
        profile.aliases.forEach { nameVariants.addAll(normalizer.normalize(it)) }

        // Normalize each nickname
        profile.nicknames.forEach { nameVariants.addAll(normalizer.normalize(it)) }

        // Collect handles from social sources
        val handles = sources
            .filter { it.enabled }
            .mapNotNull { it.handleOrAlias }
            .filter { it.isNotBlank() }
            .distinct()

        // Collect region from sources
        val region = sources
            .mapNotNull { it.region }
            .firstOrNull { it.isNotBlank() }

        return IdentityPack(
            personId = profile.id,
            primaryName = profile.name.trim(),
            nameVariants = nameVariants.toList(),
            aliases = (profile.aliases + profile.nicknames).filter { it.isNotBlank() },
            handles = handles,
            emails = profile.emails.filter { it.isNotBlank() },
            phones = profile.phones.filter { it.isNotBlank() },
            age = profile.age,
            region = region,
        )
    }
}
