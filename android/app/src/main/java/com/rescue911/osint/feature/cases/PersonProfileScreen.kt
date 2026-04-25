package com.rescue911.osint.feature.cases

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.navigation.NavHostController
import com.rescue911.osint.R
import com.rescue911.osint.data.repository.Rescue911Repository
import com.rescue911.osint.domain.model.Person
import com.rescue911.osint.ui.components.EmptyState
import com.rescue911.osint.ui.components.InfoCard
import com.rescue911.osint.ui.components.ScreenScaffold
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.first

@HiltViewModel
class PersonProfileViewModel @Inject constructor(
    private val repository: Rescue911Repository,
) : ViewModel() {
    suspend fun loadByPersonId(personId: String): Person? = runCatching {
        repository.cases().first().firstOrNull { it.person.id == personId }?.person
    }.getOrNull()
}

@Composable
fun PersonProfileScreen(
    navController: NavHostController,
    padding: PaddingValues,
    personId: String,
    vm: PersonProfileViewModel = hiltViewModel(),
) {
    var person by remember { mutableStateOf<Person?>(null) }
    var loaded by remember { mutableStateOf(false) }
    LaunchedEffect(personId) {
        person = vm.loadByPersonId(personId)
        loaded = true
    }

    ScreenScaffold(stringResource(R.string.person_profile), padding) {
        when {
            !loaded -> EmptyState(stringResource(R.string.state_loading))
            person == null -> EmptyState(stringResource(R.string.state_empty_person))
            else -> {
                val p = person!!
                InfoCard(title = p.fullName, body = p.nameVariants.joinToString())
                InfoCard(
                    title = stringResource(R.string.person_age_label),
                    body = p.age?.toString() ?: "—",
                )
                if (p.socialHandles.isNotEmpty()) {
                    InfoCard(
                        title = stringResource(R.string.person_social_handles),
                        body = p.socialHandles.joinToString(),
                    )
                }
            }
        }
    }
}
