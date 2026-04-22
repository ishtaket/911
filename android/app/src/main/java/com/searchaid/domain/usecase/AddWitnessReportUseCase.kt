package com.searchaid.domain.usecase

import com.searchaid.domain.model.WitnessReport
import com.searchaid.domain.repository.WitnessReportRepository
import javax.inject.Inject

class AddWitnessReportUseCase @Inject constructor(
    private val repository: WitnessReportRepository,
) {
    suspend operator fun invoke(report: WitnessReport): Long = repository.add(report)
}
