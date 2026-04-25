package com.rescue911.osint.feature.cases

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.rescue911.osint.R
import com.rescue911.osint.ui.components.ActionResultBanner
import com.rescue911.osint.ui.components.BannerKind
import com.rescue911.osint.ui.components.ScreenScaffold

@Composable
fun CreateCaseScreen(navController: NavHostController, padding: PaddingValues) {
    var title by remember { mutableStateOf("") }
    var personName by remember { mutableStateOf("") }
    var lastSeen by remember { mutableStateOf("") }
    var banner by remember { mutableStateOf<String?>(null) }
    val notImpl = stringResource(R.string.create_case_not_implemented)

    ScreenScaffold(stringResource(R.string.case_create), padding) {
        banner?.let { ActionResultBanner(message = it, kind = BannerKind.WARNING) }
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text(stringResource(R.string.case_title)) },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = personName,
                onValueChange = { personName = it },
                label = { Text(stringResource(R.string.person_full_name)) },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = lastSeen,
                onValueChange = { lastSeen = it },
                label = { Text(stringResource(R.string.case_last_seen)) },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = { banner = notImpl },
                enabled = title.isNotBlank() && personName.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.action_create)) }
        }
    }
}
