package com.lagradost.cloudstream3.desktop.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lagradost.cloudstream3.desktop.init.PluginFailureState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun PluginFailureDialog() {
    val failureReport by PluginFailureState.activeFailure.collectAsState()
    val report = failureReport ?: return
    val coroutineScope = rememberCoroutineScope()

    CloudstreamAlertDialog(
        show = true,
        onDismissRequest = { PluginFailureState.dismiss() },
        title = {
            Text("Extension Failed to Load", fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "The extension '${report.pluginName}' failed to initialize due to an internal bytecode or transpilation error:",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                ) {
                    Text(
                        text = report.errorMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(10.dp),
                    )
                }
                Text(
                    text = "It has been quarantined to prevent application crashes. Would you like to uninstall this extension?",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    coroutineScope.launch(Dispatchers.IO) {
                        try {
                            val f = report.file
                            com.lagradost.cloudstream3.desktop.sandbox.SandboxedPluginManager.unloadPlugin(f)
                            com.lagradost.runtime.loader.ExtensionLoader.unloadPlugin(f.absolutePath)
                            f.delete()
                            File(f.parentFile, "${f.name}.load_failed").delete()
                            File(f.parentFile, "${f.nameWithoutExtension}.load_failed").delete()
                            File(f.parentFile, "${f.nameWithoutExtension}.d2j_failed").delete()
                            File(f.parentFile, "${f.nameWithoutExtension}-jvm.jar").delete()
                            File(f.parentFile, "${f.nameWithoutExtension}-secure.jar").delete()
                            com.lagradost.cloudstream3.desktop.repo.ActiveProviderRepository.refreshProviders()
                            AppToastManager.showSuccess("Uninstalled '${report.pluginName}'")
                        } catch (t: Throwable) {
                            AppToastManager.showError("Failed to remove extension: ${t.message}")
                        }
                    }
                    PluginFailureState.dismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            ) {
                Text("Uninstall Extension")
            }
        },
        dismissButton = {
            TextButton(onClick = { PluginFailureState.dismiss() }) {
                Text("Keep Quarantined")
            }
        },
    )
}
