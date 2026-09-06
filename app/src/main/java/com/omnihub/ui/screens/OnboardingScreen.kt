package com.omnihub.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.omnihub.data.UserPrefs
import com.omnihub.ui.theme.OmniAmber
import java.io.File

@Composable
fun OnboardingScreen(
    onComplete: () -> Unit,
    onOpenLegal: (LegalDoc) -> Unit
) {
    val context = LocalContext.current
    var step by remember { mutableStateOf(0) }
    var name by remember { mutableStateOf("") }
    var age by remember { mutableStateOf("") }
    var profession by remember { mutableStateOf("") }
    var bio by remember { mutableStateOf("") }
    var accepted by remember { mutableStateOf(false) }
    var folder by remember {
        mutableStateOf(
            File(context.getExternalFilesDir(null) ?: context.filesDir, "Omni").absolutePath
        )
    }

    Column(
        Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        when (step) {
            0 -> {
                Text("Welcome to OmniHub", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("Your AI operating layer.")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = accepted, onCheckedChange = { accepted = it })
                    Text("I agree to the Terms and Privacy Policy")
                }
                TextButton(onClick = { onOpenLegal(LegalDoc.TERMS) }) { Text("Terms") }
                TextButton(onClick = { onOpenLegal(LegalDoc.PRIVACY) }) { Text("Privacy") }
                Button(
                    onClick = {
                        UserPrefs.acceptLegal(context)
                        step = 1
                    },
                    enabled = accepted,
                    colors = ButtonDefaults.buttonColors(containerColor = OmniAmber, contentColor = Color.Black),
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Continue") }
            }
            1 -> {
                Text("About you", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = age, onValueChange = { age = it.filter { ch -> ch.isDigit() }.take(3) }, label = { Text("Age") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = profession, onValueChange = { profession = it }, label = { Text("Profession") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = bio, onValueChange = { bio = it }, label = { Text("Bio") }, modifier = Modifier.fillMaxWidth(), minLines = 3)
                Button(
                    onClick = {
                        UserPrefs.saveProfile(
                            context,
                            name = name.ifBlank { "Friend" },
                            age = age.toIntOrNull(),
                            profession = profession,
                            bio = bio
                        )
                        step = 2
                    },
                    enabled = name.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = OmniAmber, contentColor = Color.Black),
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Continue") }
            }
            else -> {
                Text("Omni folder", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("Omni works inside this folder — files, git, builds.")
                OutlinedTextField(
                    value = folder,
                    onValueChange = { folder = it },
                    label = { Text("Path") },
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Default is app storage.", style = MaterialTheme.typography.bodySmall)
                Button(
                    onClick = {
                        val f = File(folder)
                        f.mkdirs()
                        UserPrefs.setOmniFolder(context, f.absolutePath)
                        UserPrefs.markSetupComplete(context)
                        onComplete()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = OmniAmber, contentColor = Color.Black),
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Create and finish") }
            }
        }
    }
}
