package com.searchaid.domain.usecase

import com.searchaid.domain.model.MissingCase
import com.searchaid.domain.repository.MissingCaseRepository
import javax.inject.Inject

class StartMissingCaseUseCase @Inject constructor(
    private val repository: MissingCaseRepository,
) {
    suspend operator fun invoke(case_: MissingCase): Long = repository.create(case_)
}
