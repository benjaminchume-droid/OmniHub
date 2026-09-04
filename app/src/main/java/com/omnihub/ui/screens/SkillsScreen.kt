package com.omnihub.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.omnihub.ui.theme.OmniAmber
import java.io.BufferedInputStream
import java.io.File
import java.util.zip.ZipInputStream

data class SkillItem(val name: String, val path: String, val kind: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SkillsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val skillsDir = remember {
        File(context.filesDir, "skills").also { it.mkdirs() }
    }
    var skills by remember { mutableStateOf(listSkills(skillsDir)) }
    var status by remember { mutableStateOf<String?>(null) }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        var imported = 0
        uris.forEach { uri ->
            try {
                val name = uri.lastPathSegment?.substringAfterLast('/') ?: "skill_${System.currentTimeMillis()}"
                context.contentResolver.openInputStream(uri)?.use { input ->
                    when {
                        name.endsWith(".zip", true) -> {
                            ZipInputStream(BufferedInputStream(input)).use { zis ->
                                var entry = zis.nextEntry
                                while (entry != null) {
                                    if (!entry.isDirectory) {
                                        val outName = entry.name.substringAfterLast('/')
                                        if (outName.isNotBlank()) {
                                            File(skillsDir, outName).outputStream().use { zis.copyTo(it) }
                                            imported++
                                        }
                                    }
                                    entry = zis.nextEntry
                                }
                            }
                        }
                        else -> {
                            File(skillsDir, name).outputStream().use { input.copyTo(it) }
                            imported++
                        }
                    }
                }
            } catch (e: Exception) {
                status = e.message
            }
        }
        skills = listSkills(skillsDir)
        status = "Imported $imported file(s)"
        Toast.makeText(context, status, Toast.LENGTH_SHORT).show()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Skills", fontWeight = FontWeight.SemiBold, color = OmniAmber) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        picker.launch(arrayOf("*/*", "text/*", "application/zip", "application/json"))
                    }) {
                        Icon(Icons.Default.UploadFile, "Upload", tint = OmniAmber)
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Text(
                    "Upload .md, Claude-style skill files, JSON, or .zip bundles. Stored on device under skills/.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = {
                        picker.launch(arrayOf("*/*", "text/*", "application/zip", "application/json"))
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = OmniAmber, contentColor = Color.Black)
                ) {
                    Icon(Icons.Default.UploadFile, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Upload files / zip")
                }
            }
            status?.let { item { Text(it, color = OmniAmber) } }
            if (skills.isEmpty()) {
                item { Text("No skills imported yet.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            items(skills) { skill ->
                Card(shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                    ListItem(
                        headlineContent = { Text(skill.name) },
                        supportingContent = { Text(skill.kind) },
                        leadingContent = { Icon(Icons.Outlined.AutoAwesome, null, tint = OmniAmber) }
                    )
                }
            }
        }
    }
}

private fun listSkills(dir: File): List<SkillItem> {
    if (!dir.exists()) return emptyList()
    return dir.listFiles()?.map { f ->
        val kind = when {
            f.name.endsWith(".md", true) -> "Markdown skill"
            f.name.endsWith(".json", true) -> "JSON skill"
            f.name.endsWith(".txt", true) -> "Text skill"
            else -> "Skill file"
        }
        SkillItem(f.name, f.absolutePath, kind)
    }?.sortedBy { it.name } ?: emptyList()
}
