package moe.shizuku.manager.ui.screen

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import moe.shizuku.manager.ui.component.TokenXGlassCard

private const val FLAG_SECURE_PACKAGE = "com.varuns2002.disable_flag_secure"

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun FlagSecureLabsScreen(onBack: () -> Unit) {
 val context=LocalContext.current
 val installed=runCatching{context.packageManager.getPackageInfo(FLAG_SECURE_PACKAGE,0)}.isSuccess
 Column(Modifier.fillMaxSize()){
  TopAppBar(title={Text("Disable FLAG_SECURE")},navigationIcon={IconButton(onClick=onBack){Icon(Icons.AutoMirrored.Filled.ArrowBack,"Back")}},windowInsets=WindowInsets(0.dp))
  Column(Modifier.fillMaxSize().padding(16.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){
   TokenXGlassCard{Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){Icon(Icons.Outlined.Shield,null);Text("Disable FLAG_SECURE",style=MaterialTheme.typography.titleLarge);Text("2.0.0 reference • Xposed / LSPosed",color=MaterialTheme.colorScheme.onSurfaceVariant);AssistChip(onClick={},label={Text(if(installed)"INSTALLED" else "NOT DETECTED")});if(installed){context.packageManager.getLaunchIntentForPackage(FLAG_SECURE_PACKAGE)?.let{launch->Button(onClick={launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);context.startActivity(launch)}){Icon(Icons.Outlined.OpenInNew,null);Spacer(Modifier.width(8.dp));Text("Open module")}}}}}
   TokenXGlassCard{Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){Text("Module role",style=MaterialTheme.typography.titleMedium);Text("Unified Labs entry for the LSPosed module controlling FLAG_SECURE behavior.");HorizontalDivider();Text("The installed module remains the owner of its hook and LSPosed scope.")}}
  }
 }
}