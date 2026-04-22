package com.searchaid.domain.usecase

import com.searchaid.domain.model.ReportStatus
import com.searchaid.domain.repository.WitnessReportRepository
import javax.inject.Inject

class UpdateWitnessReportStatusUseCase @Inject constructor(
    private val repository: WitnessReportRepository,
) {
    suspend operator fun invoke(id: Long, status: ReportStatus) =
        repository.updateStatus(id, status)
}
