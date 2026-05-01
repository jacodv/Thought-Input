package com.thoughtinput.capture.ui.destinations

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.thoughtinput.capture.data.destinations.DestinationType

@Composable
fun DestinationTypeIcon(type: DestinationType, modifier: Modifier = Modifier.size(24.dp)) {
    val (vector, label) = when (type) {
        is DestinationType.Supabase -> Icons.Filled.Cloud to "Supabase"
        is DestinationType.RestNoAuth -> Icons.Filled.Public to "REST"
        is DestinationType.RestApiKey -> Icons.Filled.Key to "API Key"
        is DestinationType.RestOAuthPassword -> Icons.Filled.Person to "OAuth Password"
        is DestinationType.RestOAuthClientCredentials -> Icons.Filled.Lock to "OAuth Client"
    }
    Icon(
        imageVector = vector,
        contentDescription = label,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
    )
}
