package com.rescue911.osint.data.remote

import com.rescue911.osint.domain.model.AuditEntry
import com.rescue911.osint.domain.model.Evidence
import com.rescue911.osint.domain.model.Hypothesis
import com.rescue911.osint.domain.model.MissingCase
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/** Backend API contract — Android only ever talks to *our* backend. */
interface Rescue911Api {
    @GET("v1/health")
    suspend fun health(): Map<String, Any>

    @GET("v1/cases")
    suspend fun listCases(): List<MissingCase>

    @GET("v1/cases/{id}")
    suspend fun getCase(@Path("id") id: String): MissingCase

    @POST("v1/search/start/{caseId}")
    suspend fun startSearch(@Path("caseId") caseId: String): List<Evidence>

    @GET("v1/evidence")
    suspend fun listEvidence(@Query("case_id") caseId: String? = null): List<Evidence>

    @GET("v1/hypotheses")
    suspend fun listHypotheses(@Query("case_id") caseId: String? = null): List<Hypothesis>

    @GET("v1/audit")
    suspend fun audit(@Query("limit") limit: Int = 200): List<AuditEntry>
}
