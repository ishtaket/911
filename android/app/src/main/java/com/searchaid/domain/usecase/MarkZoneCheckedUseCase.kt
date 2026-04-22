package com.searchaid.domain.usecase

import com.searchaid.domain.repository.SearchZoneRepository
import javax.inject.Inject

class MarkZoneCheckedUseCase @Inject constructor(
    private val repository: SearchZoneRepository,
) {
    suspend operator fun invoke(zoneId: Long) = repository.markChecked(zoneId)
}
