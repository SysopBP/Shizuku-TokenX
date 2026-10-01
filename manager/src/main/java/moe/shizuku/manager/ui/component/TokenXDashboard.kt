package moe.shizuku.manager.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.Token
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun TokenXDashboard(
    running: Boolean,
    uid: Int,
    rootAvailable: Boolean,
    modifier: Modifier = Modifier,
) {
    TokenXGlassCard(modifier) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Token, contentDescription = null)
                Column(Modifier.padding(start = 12.dp).weight(1f)) {
                    Text(
                        "TokenX Control Center",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        if (running) "Privilege engine active" else "Privilege engine stopped",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    if (running) "ACTIVE" else "OFFLINE",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (running) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.error
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TokenXBackendChip(
                    label = "System",
                    value = if (uid == 1000) "UID 1000" else "Standby",
                    active = uid == 1000,
                    modifier = Modifier.weight(1f)
                )
                TokenXBackendChip(
                    label = "Root",
                    value = if (uid == 0) "UID 0" else if (rootAvailable) "Ready" else "Unavailable",
                    active = uid == 0,
                    modifier = Modifier.weight(1f)
                )
                TokenXBackendChip(
                    label = "Shell",
                    value = if (uid == 2000) "UID 2000" else "Fallback",
                    active = uid == 2000,
                    modifier = Modifier.weight(1f)
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatusLine(Icons.Rounded.Token, "Boot Token", "Foundation ready", Modifier.weight(1f))
                StatusLine(Icons.Rounded.Security, "Watchdog", "Existing engine", Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatusLine(Icons.Rounded.AdminPanelSettings, "Xposed", "Planned", Modifier.weight(1f))
                StatusLine(Icons.Rounded.Terminal, "Router", "Multi-backend", Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun TokenXBackendChip(
    label: String,
    value: String,
    active: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = if (active) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun StatusLine(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Column(Modifier.padding(start = 8.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            Text(value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
