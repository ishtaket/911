package com.rescue911.osint.feature.cases

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavHostController
import com.rescue911.osint.R
import com.rescue911.osint.data.mock.MockData
import com.rescue911.osint.ui.components.InfoCard
import com.rescue911.osint.ui.components.ScreenScaffold

@Composable
fun PersonProfileScreen(navController: NavHostController, padding: PaddingValues, personId: String) {
    val person = MockData.cases.firstOrNull { it.person.id == personId }?.person
    ScreenScaffold(stringResource(R.string.person_profile), padding) {
        person?.let {
            InfoCard(title = it.fullName, body = it.nameVariants.joinToString())
            InfoCard(
                title = stringResource(R.string.person_age_label),
                body = it.age?.toString() ?: "—",
            )
            if (it.socialHandles.isNotEmpty()) {
                InfoCard(title = stringResource(R.string.person_social_handles), body = it.socialHandles.joinToString())
            }
        }
    }
}
