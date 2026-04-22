package com.searchaid.domain.usecase

import com.searchaid.domain.model.WitnessReport
import com.searchaid.domain.repository.WitnessReportRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetWitnessReportsByCaseUseCase @Inject constructor(
    private val repository: WitnessReportRepository,
) {
    operator fun invoke(caseId: Long): Flow<List<WitnessReport>> =
        repository.observeByCase(caseId)
}
