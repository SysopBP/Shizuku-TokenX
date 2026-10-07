package moe.shizuku.manager.ui.screen

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.Route
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import moe.shizuku.manager.R
import moe.shizuku.manager.ui.Detail
import moe.shizuku.manager.ui.theme.LocalAmoledTheme
import moe.shizuku.manager.ui.theme.LocalTokenXGlass

/**
 * The things that are gone to rather than lived in.
 *
 * Every one of these was a tab of its own before, which is most of the bar's width spent on
 * screens that are largely empty when you land on them: you open the app-ops list to look one
 * app up, and you open the shell to run something and leave. A tab is for a place the app keeps
 * you in, so this is a grid of the others instead, and the next one to earn a place here costs a
 * line rather than a fifth of the bar.
 *
 * A grid and a name, nothing more: these are features, not decisions, so each one is its icon
 * and what it is called. A name that needs a sentence under it to be understood is a name to
 * change, and the grid is what makes room for many of them.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LabsScreen(bottomPadding: Dp, onOpenDetail: (Detail) -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(stringResource(R.string.tab_labs)) },
            windowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp)
        )

        LazyVerticalGrid(
            // A width per tile rather than a count, so a phone shows two and a tablet shows
            // however many fit without either being told to.
            columns = GridCells.Adaptive(minSize = 150.dp),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                top = 16.dp,
                end = 16.dp,
                bottom = bottomPadding
            ),
            horizontalArrangement = Arrangement.spacedBy(13.dp),
            verticalArrangement = Arrangement.spacedBy(13.dp)
        ) {
            item {
                LabTile(
                    icon = Icons.Outlined.AdminPanelSettings,
                    label = stringResource(R.string.tab_manage),
                    onClick = { onOpenDetail(Detail.APP_OPS) }
                )
            }

            item {
                LabTile(
                    icon = Icons.Outlined.Terminal,
                    label = stringResource(R.string.tab_shell),
                    onClick = { onOpenDetail(Detail.SHELL) }
                )
            }

            item {
                LabTile(
                    icon = AppToggleFeature.FIREWALL.icon,
                    label = stringResource(AppToggleFeature.FIREWALL.titleRes),
                    onClick = { onOpenDetail(Detail.FIREWALL) }
                )
            }

            item {
                LabTile(
                    icon = AppToggleFeature.AUTOSTART.icon,
                    label = stringResource(AppToggleFeature.AUTOSTART.titleRes),
                    onClick = { onOpenDetail(Detail.AUTOSTART) }
                )
            }

            item {
                LabTile(
                    icon = Icons.Outlined.Route,
                    label = "TokenX Control Center",
                    badge = "ROOT · D2 · SYSTEM RPC",
                    onClick = { onOpenDetail(Detail.TOKENX) }
                )
            }
            item {
                LabTile(
                    icon = Icons.Outlined.Key,
                    label = "Root Console",
                    badge = "UID 0",
                    onClick = { onOpenDetail(Detail.TERMINAL) }
                )
            }
            item {
                LabTile(
                    icon = Icons.Outlined.BugReport,
                    label = "Secure Chain Monitor",
                    badge = "LIVE · D2 · SYSTEM RPC",
                    onClick = { onOpenDetail(Detail.TOKENX) }
                )
            }
        }
    }
}

/**
 * One feature: its icon on a tinted plate, its name under it, and nothing else.
 *
 * The plate is what makes the grid scannable, the way a launcher's icons do: at this size the
 * outline of the icon alone is not enough to tell one tile from another at a glance, and the
 * colour is what the eye lands on first.
 */
@Composable
private fun LabTile(icon: ImageVector, label: String, badge: String? = null, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 124.dp)
            .clickable(onClick = onClick)
            .then(
                if (LocalAmoledTheme.current) {
                    Modifier.border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant,
                        shape = MaterialTheme.shapes.large
                    )
                } else {
                    Modifier
                }
            ),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = if (LocalTokenXGlass.current.enabled) LocalTokenXGlass.current.opacity else 1f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Surface(
                modifier = Modifier.size(48.dp),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }

            Text(
                text = label,
                modifier = Modifier.padding(top = 12.dp),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                // Two lines, so a longer name wraps rather than being cut, and centred,
                // because it is a tile and not a list row.
                maxLines = 2,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface
            )
            badge?.let {
                Text(
                    text = it,
                    modifier = Modifier.padding(top = 5.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
