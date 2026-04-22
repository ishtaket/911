package com.searchaid.domain.usecase

import com.searchaid.domain.model.SearchZone
import com.searchaid.domain.repository.SearchZoneRepository
import javax.inject.Inject

class AddSearchZoneUseCase @Inject constructor(
    private val repository: SearchZoneRepository,
) {
    suspend operator fun invoke(zone: SearchZone): Long = repository.add(zone)
}
