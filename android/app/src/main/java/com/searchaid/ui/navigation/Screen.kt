package com.searchaid.ui.navigation

sealed class Screen(val route: String) {
    data object ProfilesList : Screen("profiles_list")
    data object PersonProfile : Screen("person_profile/{profileId}") {
        fun withId(id: Long) = "person_profile/$id"
    }
    data object CreateEditProfile : Screen("create_edit_profile?profileId={profileId}") {
        fun create() = "create_edit_profile"
        fun edit(id: Long) = "create_edit_profile?profileId=$id"
    }
    data object StartMissingCase : Screen("start_missing_case/{profileId}") {
        fun withId(id: Long) = "start_missing_case/$id"
    }
    data object ActiveCaseDashboard : Screen("active_case/{caseId}") {
        fun withId(id: Long) = "active_case/$id"
    }
    data object SearchMap : Screen("search_map/{caseId}") {
        fun withId(id: Long) = "search_map/$id"
    }
    data object LeadsList : Screen("leads_list/{caseId}") {
        fun withId(id: Long) = "leads_list/$id"
    }
    data object WitnessReports : Screen("witness_reports/{caseId}") {
        fun withId(id: Long) = "witness_reports/$id"
    }
    data object Outreach : Screen("outreach/{caseId}") {
        fun withId(id: Long) = "outreach/$id"
    }
    data object WebSearch : Screen("web_search/{caseId}") {
        fun withId(id: Long) = "web_search/$id"
    }
    data object SocialSearch : Screen("social_search/{caseId}") {
        fun withId(id: Long) = "social_search/$id"
    }
    data object AuditLog : Screen("audit_log?caseId={caseId}") {
        fun forCase(id: Long) = "audit_log?caseId=$id"
        fun all() = "audit_log"
    }
}
