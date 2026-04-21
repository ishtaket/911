package com.searchaid.domain.usecase

import com.searchaid.domain.model.HistoricalPlace
import com.searchaid.domain.repository.HistoricalPlaceRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetHistoricalPlacesUseCase @Inject constructor(
    private val repository: HistoricalPlaceRepository,
) {
    operator fun invoke(personId: Long): Flow<List<HistoricalPlace>> =
        repository.observeByPerson(personId)
}
