package com.rescue911.osint.data.remote

import com.rescue911.osint.data.remote.dto.ArchiveStartResponseDto
import com.rescue911.osint.data.remote.dto.AuditEntryDto
import com.rescue911.osint.data.remote.dto.CaseDto
import com.rescue911.osint.data.remote.dto.CreateCaseRequestDto
import com.rescue911.osint.data.remote.dto.EvidenceDto
import com.rescue911.osint.data.remote.dto.GeoIntStartResponseDto
import com.rescue911.osint.data.remote.dto.HealthDto
import com.rescue911.osint.data.remote.dto.HypothesisDto
import com.rescue911.osint.data.remote.dto.ProviderListResponseDto
import com.rescue911.osint.data.remote.dto.ProviderStatusResponseDto
import com.rescue911.osint.data.remote.dto.ReviewBodyDto
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Backend API contract — Android only ever talks to *our* backend.
 * Provider keys, OSINT/GeoINT orchestration, and validation live server-side.
 */
interface Rescue911Api {

    @GET("v1/health")
    suspend fun health(): HealthDto

    @GET("v1/provider-status")
    suspend fun providerStatus(): ProviderStatusResponseDto

    @GET("v1/cases")
    suspend fun listCases(): List<CaseDto>

    @POST("v1/cases")
    suspend fun createCase(@Body body: CreateCaseRequestDto): CaseDto

    @GET("v1/cases/{id}")
    suspend fun getCase(@Path("id") id: String): CaseDto

    @GET("v1/evidence")
    suspend fun listEvidence(@Query("case_id") caseId: String? = null): List<EvidenceDto>

    @GET("v1/hypotheses")
    suspend fun listHypotheses(@Query("case_id") caseId: String? = null): List<HypothesisDto>

    @GET("v1/audit")
    suspend fun listAudit(@Query("limit") limit: Int = 200): List<AuditEntryDto>

    @POST("v1/review/{id}")
    suspend fun reviewEvidence(
        @Path("id") evidenceId: String,
        @Body body: ReviewBodyDto,
    ): EvidenceDto

    /** Compatibility: dispatches all channels (web + social + archive). */
    @POST("v1/search/start/{caseId}")
    suspend fun startSearch(@Path("caseId") caseId: String): List<EvidenceDto>

    @POST("v1/search/web/start/{caseId}")
    suspend fun startWebSearch(@Path("caseId") caseId: String): List<EvidenceDto>

    @POST("v1/search/social/start/{caseId}")
    suspend fun startSocialSearch(@Path("caseId") caseId: String): List<EvidenceDto>

    @POST("v1/search/archive/start/{caseId}")
    suspend fun startArchiveSearch(@Path("caseId") caseId: String): ArchiveStartResponseDto

    @POST("v1/geoint/start/{caseId}")
    suspend fun startGeoint(@Path("caseId") caseId: String): GeoIntStartResponseDto

    /** New rich provider registry. Replaces /v1/provider-status in the UI. */
    @GET("v1/providers")
    suspend fun listProviders(): ProviderListResponseDto
}
