package com.searchaid.domain.usecase

import com.searchaid.domain.repository.HistoricalPlaceRepository
import javax.inject.Inject

class DeleteHistoricalPlaceUseCase @Inject constructor(
    private val repository: HistoricalPlaceRepository,
) {
    suspend operator fun invoke(id: Long) = repository.delete(id)
}
