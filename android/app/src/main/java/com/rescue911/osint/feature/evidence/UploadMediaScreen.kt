package com.rescue911.osint.feature.evidence

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
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
import com.rescue911.osint.ui.components.InfoCard
import com.rescue911.osint.ui.components.ScreenScaffold

@Composable
fun UploadMediaScreen(
    navController: NavHostController,
    padding: PaddingValues,
    caseId: String,
) {
    var lastAction by remember { mutableStateOf<String?>(null) }
    val notImpl = stringResource(R.string.upload_not_implemented)

    ScreenScaffold(stringResource(R.string.action_upload_media), padding) {
        InfoCard(
            title = stringResource(R.string.upload_pick_source),
            body = stringResource(R.string.upload_explainer),
        )
        lastAction?.let { ActionResultBanner(message = it, kind = BannerKind.WARNING) }
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Button(onClick = { lastAction = "Camera: $notImpl" }, modifier = Modifier.weight(1f)) {
                Icon(Icons.Filled.CameraAlt, contentDescription = null)
                Text(text = "  " + stringResource(R.string.upload_take_photo))
            }
            OutlinedButton(onClick = { lastAction = "Gallery: $notImpl" }, modifier = Modifier.weight(1f)) {
                Icon(Icons.Filled.PhotoLibrary, contentDescription = null)
                Text(text = "  " + stringResource(R.string.upload_pick_from_gallery))
            }
        }
    }
}
