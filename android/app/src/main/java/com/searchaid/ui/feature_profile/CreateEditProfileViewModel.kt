package com.searchaid.ui.feature_profile

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.searchaid.domain.model.PersonProfile
import com.searchaid.domain.usecase.CreatePersonProfileUseCase
import com.searchaid.domain.usecase.GetPersonProfileUseCase
import com.searchaid.domain.usecase.LogActionUseCase
import com.searchaid.domain.usecase.UpdatePersonProfileUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProfileFormState(
    val name: String = "",
    val age: String = "",
    val condition: String = "",
    val distinguishingFeatures: String = "",
    val habits: String = "",
    val knownLocations: String = "",
    val aliases: String = "",
    val nicknames: String = "",
    val emails: String = "",
    val phones: String = "",
    val familyNotes: String = "",
    val isEdit: Boolean = false,
    val loading: Boolean = false,
    val saved: Boolean = false,
)

@HiltViewModel
class CreateEditProfileViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getProfile: GetPersonProfileUseCase,
    private val createProfile: CreatePersonProfileUseCase,
    private val updateProfile: UpdatePersonProfileUseCase,
    private val logAction: LogActionUseCase,
) : ViewModel() {

    private val profileId: Long = savedStateHandle["profileId"] ?: -1L
    private var existingProfile: PersonProfile? = null

    private val _state = MutableStateFlow(ProfileFormState())
    val state: StateFlow<ProfileFormState> = _state

    init {
        if (profileId > 0) {
            _state.update { it.copy(loading = true, isEdit = true) }
            viewModelScope.launch {
                val profile = getProfile(profileId)
                existingProfile = profile
                if (profile != null) {
                    _state.update {
                        it.copy(
                            name = profile.name,
                            age = profile.age?.toString() ?: "",
                            condition = profile.condition ?: "",
                            distinguishingFeatures = profile.distinguishingFeatures ?: "",
                            habits = profile.habits ?: "",
                            knownLocations = profile.knownLocations ?: "",
                            aliases = profile.aliases.joinToString(", "),
                            nicknames = profile.nicknames.joinToString(", "),
                            emails = profile.emails.joinToString(", "),
                            phones = profile.phones.joinToString(", "),
                            familyNotes = profile.familyNotes ?: "",
                            loading = false,
                        )
                    }
                } else {
                    _state.update { it.copy(loading = false) }
                }
            }
        }
    }

    fun onNameChange(v: String) { _state.update { it.copy(name = v) } }
    fun onAgeChange(v: String) { _state.update { it.copy(age = v) } }
    fun onConditionChange(v: String) { _state.update { it.copy(condition = v) } }
    fun onFeaturesChange(v: String) { _state.update { it.copy(distinguishingFeatures = v) } }
    fun onHabitsChange(v: String) { _state.update { it.copy(habits = v) } }
    fun onLocationsChange(v: String) { _state.update { it.copy(knownLocations = v) } }
    fun onAliasesChange(v: String) { _state.update { it.copy(aliases = v) } }
    fun onNicknamesChange(v: String) { _state.update { it.copy(nicknames = v) } }
    fun onEmailsChange(v: String) { _state.update { it.copy(emails = v) } }
    fun onPhonesChange(v: String) { _state.update { it.copy(phones = v) } }
    fun onFamilyNotesChange(v: String) { _state.update { it.copy(familyNotes = v) } }

    fun save() {
        val s = _state.value
        if (s.name.isBlank()) return

        viewModelScope.launch {
            _state.update { it.copy(loading = true) }

            val profile = PersonProfile(
                id = existingProfile?.id ?: 0,
                name = s.name.trim(),
                age = s.age.toIntOrNull(),
                photoUri = existingProfile?.photoUri,
                condition = s.condition.trimToNull(),
                distinguishingFeatures = s.distinguishingFeatures.trimToNull(),
                habits = s.habits.trimToNull(),
                knownLocations = s.knownLocations.trimToNull(),
                aliases = s.aliases.splitComma(),
                nicknames = s.nicknames.splitComma(),
                emails = s.emails.splitComma(),
                phones = s.phones.splitComma(),
                familyNotes = s.familyNotes.trimToNull(),
                createdAt = existingProfile?.createdAt ?: 0,
                updatedAt = 0,
            )

            if (existingProfile != null) {
                updateProfile(profile)
                logAction("PROFILE_UPDATED", details = "Updated profile: ${profile.name}")
            } else {
                val id = createProfile(profile)
                logAction("PROFILE_CREATED", details = "Created profile: ${profile.name} (id=$id)")
            }

            _state.update { it.copy(loading = false, saved = true) }
        }
    }

    private fun String.trimToNull(): String? = trim().ifBlank { null }
    private fun String.splitComma(): List<String> =
        split(",").map { it.trim() }.filter { it.isNotEmpty() }
}
