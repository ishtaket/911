package com.searchaid.domain.usecase

import com.searchaid.domain.model.HistoricalPlace
import com.searchaid.domain.repository.HistoricalPlaceRepository
import javax.inject.Inject

class AddHistoricalPlaceUseCase @Inject constructor(
    private val repository: HistoricalPlaceRepository,
) {
    suspend operator fun invoke(place: HistoricalPlace): Long = repository.add(place)
}
