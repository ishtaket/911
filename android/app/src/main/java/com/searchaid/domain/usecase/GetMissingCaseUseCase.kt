package com.searchaid.domain.usecase

import com.searchaid.domain.model.MissingCase
import com.searchaid.domain.repository.MissingCaseRepository
import javax.inject.Inject

class GetMissingCaseUseCase @Inject constructor(
    private val repository: MissingCaseRepository,
) {
    suspend operator fun invoke(id: Long): MissingCase? = repository.getById(id)
}
