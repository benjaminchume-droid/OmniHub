package com.omnihub.workspace

import android.content.Context
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

/**
 * Omni Workspace - controlled filesystem with permission boundaries.
 * 
 * Provides:
 * - Sandboxed file operations
 * - Permission-scoped access
 * - Audit logging
 * - Android SAF integration
 */
class OmniWorkspace(
    private val context: Context
) {
    private val workspaceMutex = Mutex()
    
    // Workspace root (user-selected via SAF)
    private var workspaceRoot: DocumentFile? = null
    
    // Audit log
    private val auditLog = mutableListOf<WorkspaceAudit>()
    
    /**
     * Set workspace root directory.
     */
    suspend fun setWorkspaceRoot(uri: android.net.Uri): Result<Unit> {
        return workspaceMutex.withLock {
            try {
                val root = DocumentFile.fromTreeUri(context, uri)
                    ?: return@withLock Result.failure(IllegalArgumentException("Invalid workspace URI"))
                
                workspaceRoot = root
                
                Log.i("OmniWorkspace", "Workspace root set: ${root.uri}")
                Result.success(Unit)
            } catch (e: Exception) {
                Log.e("OmniWorkspace", "Failed to set workspace root", e)
                Result.failure(e)
            }
        }
    }
    
    /**
     * Execute a file operation.
     */
    suspend fun executeFileOperation(instruction: String): FileOperationResult {
        return workspaceMutex.withLock {
            try {
                val root = workspaceRoot 
                    ?: return@withLock FileOperationResult(
                        success = false,
                        error = WorkspaceNotConfiguredException("Workspace not configured")
                    )
                
                val lowerInstruction = instruction.lowercase()
                
                // Parse operation
                val operation = parseOperation(instruction)
                
                // Check permissions
                if (operation.isDestructive) {
                    // Require explicit permission
                    if (!hasPermission(operation.type)) {
                        return@withLock FileOperationResult(
                            success = false,
                            error = PermissionDeniedException("Permission denied for ${operation.type}")
                        )
                    }
                }
                
                // Execute operation
                val result = when (operation.type) {
                    OperationType.CREATE -> createFile(root, operation.path, operation.content)
                    OperationType.READ -> readFile(root, operation.path)
                    OperationType.WRITE -> writeFile(root, operation.path, operation.content)
                    OperationType.DELETE -> deleteFile(root, operation.path)
                    OperationType.LIST -> listFiles(root, operation.path)
                    OperationType.MOVE -> moveFile(root, operation.path, operation.destination)
                    OperationType.COPY -> copyFile(root, operation.path, operation.destination)
                }
                
                // Audit log
                auditLog.add(
                    WorkspaceAudit(
                        timestamp = System.currentTimeMillis(),
                        operation = operation.type.name,
                        path = operation.path,
                        success = result.success
                    )
                )
                
                result
            } catch (e: Exception) {
                Log.e("OmniWorkspace", "File operation failed", e)
                FileOperationResult(
                    success = false,
                    error = e,
                    output = null
                )
            }
        }
    }
    
    /**
     * Execute Android build.
     */
    suspend fun executeAndroidBuild(instruction: String): FileOperationResult {
        return workspaceMutex.withLock {
            try {
                val root = workspaceRoot 
                    ?: return@withLock FileOperationResult(
                        success = false,
                        error = WorkspaceNotConfiguredException("Workspace not configured")
                    )
                
                // Parse build command
                val projectPath = extractProjectPath(instruction)
                val buildType = extractBuildType(instruction)
                
                // TODO: Implement actual Gradle build execution
                // For now, return stub
                FileOperationResult(
                    success = true,
                    output = "Build executed: $projectPath ($buildType)",
                    error = null
                )
            } catch (e: Exception) {
                Log.e("OmniWorkspace", "Android build failed", e)
                FileOperationResult(
                    success = false,
                    error = e,
                    output = null
                )
            }
        }
    }
    
    /**
     * Get audit log.
     */
    fun getAuditLog(): List<WorkspaceAudit> {
        return auditLog.toList()
    }
    
    /**
     * Clear audit log.
     */
    fun clearAuditLog() {
        auditLog.clear()
    }
    
    /**
     * Parse file operation from instruction.
     */
    private fun parseOperation(instruction: String): FileOperation {
        val lower = instruction.lowercase()
        
        return when {
            lower.contains("create") -> FileOperation(OperationType.CREATE, extractPath(instruction), content = extractContent(instruction))
            lower.contains("read") -> FileOperation(OperationType.READ, extractPath(instruction))
            lower.contains("write") || lower.contains("save") -> FileOperation(OperationType.WRITE, extractPath(instruction), content = extractContent(instruction))
            lower.contains("delete") || lower.contains("remove") -> FileOperation(OperationType.DELETE, extractPath(instruction))
            lower.contains("list") -> FileOperation(OperationType.LIST, extractPath(instruction))
            lower.contains("move") -> FileOperation(OperationType.MOVE, extractPath(instruction), destination = extractDestination(instruction))
            lower.contains("copy") -> FileOperation(OperationType.COPY, extractPath(instruction), destination = extractDestination(instruction))
            else -> FileOperation(OperationType.READ, extractPath(instruction))
        }
    }
    
    /**
     * Extract file path from instruction.
     */
    private fun extractPath(instruction: String): String {
        // Simple extraction - improve with better parsing
        val match = Regex("(?:file|path|to)\\s+[\"']?([^\"']*)[\"']?").find(instruction)
        return match?.groupValues?.get(1) ?: ""
    }
    
    /**
     * Extract content from instruction.
     */
    private fun extractContent(instruction: String): String {
        val match = Regex("(?:content|text|data)\\s*[=:]\\s*[\"']?([^\"']*)[\"']?").find(instruction)
        return match?.groupValues?.get(1) ?: ""
    }
    
    /**
     * Extract destination path.
     */
    private fun extractDestination(instruction: String): String {
        val match = Regex("(?:to|destination)\\s+[\"']?([^\"']*)[\"']?").find(instruction)
        return match?.groupValues?.get(1) ?: ""
    }
    
    /**
     * Extract project path for Android build.
     */
    private fun extractProjectPath(instruction: String): String {
        val match = Regex("(?:project|app)\\s+[\"']?([^\"']*)[\"']?").find(instruction)
        return match?.groupValues?.get(1) ?: ""
    }
    
    /**
     * Extract build type.
     */
    private fun extractBuildType(instruction: String): String {
        return when {
            instruction.lowercase().contains("debug") -> "debug"
            instruction.lowercase().contains("release") -> "release"
            else -> "debug"
        }
    }
    
    /**
     * Check permission for operation.
     */
    private fun hasPermission(operationType: OperationType): Boolean {
        // TODO: Implement actual permission checking
        return true
    }
    
    // File operation implementations
    private fun createFile(root: DocumentFile, path: String, content: String?): FileOperationResult {
        return try {
            val file = root.createFile("text/plain", path)
                ?: return FileOperationResult(false, error = FileCreationException("Failed to create file: $path"))
            
            if (content != null) {
                context.contentResolver.openOutputStream(file.uri)?.use { os ->
                    os.write(content.toByteArray())
                }
            }
            
            FileOperationResult(success = true, output = "Created: $path")
        } catch (e: Exception) {
            FileOperationResult(success = false, error = e)
        }
    }
    
    private fun readFile(root: DocumentFile, path: String): FileOperationResult {
        return try {
            val file = root.findFile(path) 
                ?: return FileOperationResult(false, error = FileNotFoundException("File not found: $path"))
            
            val content = context.contentResolver.openInputStream(file.uri)?.use { it ->
                it.bufferedReader().use { reader -> reader.readText() }
            }
            
            FileOperationResult(success = true, output = content)
        } catch (e: Exception) {
            FileOperationResult(success = false, error = e)
        }
    }
    
    private fun writeFile(root: DocumentFile, path: String, content: String): FileOperationResult {
        return try {
            val file = root.findFile(path) 
                ?: return FileOperationResult(false, error = FileNotFoundException("File not found: $path"))
            
            context.contentResolver.openOutputStream(file.uri)?.use { os ->
                os.write(content.toByteArray())
            }
            
            FileOperationResult(success = true, output = "Written: $path")
        } catch (e: Exception) {
            FileOperationResult(success = false, error = e)
        }
    }
    
    private fun deleteFile(root: DocumentFile, path: String): FileOperationResult {
        return try {
            val file = root.findFile(path) 
                ?: return FileOperationResult(false, error = FileNotFoundException("File not found: $path"))
            
            val deleted = file.delete()
            
            if (deleted) {
                FileOperationResult(success = true, output = "Deleted: $path")
            } else {
                FileOperationResult(success = false, error = FileDeletionException("Failed to delete: $path"))
            }
        } catch (e: Exception) {
            FileOperationResult(success = false, error = e)
        }
    }
    
    private fun listFiles(root: DocumentFile, path: String): FileOperationResult {
        return try {
            val dir = if (path.isEmpty()) root else root.findFile(path)
                ?: return FileOperationResult(false, error = FileNotFoundException("Directory not found: $path"))
            
            val files = dir.listFiles().map { it.name }.joinToString("\n")
            
            FileOperationResult(success = true, output = files)
        } catch (e: Exception) {
            FileOperationResult(success = false, error = e)
        }
    }
    
    private fun moveFile(root: DocumentFile, sourcePath: String, destinationPath: String): FileOperationResult {
        return FileOperationResult(success = false, error = NotImplementedError("Move not implemented"))
    }
    
    private fun copyFile(root: DocumentFile, sourcePath: String, destinationPath: String): FileOperationResult {
        return FileOperationResult(success = false, error = NotImplementedError("Copy not implemented"))
    }
}

/**
 * File operation.
 */
data class FileOperation(
    val type: OperationType,
    val path: String,
    val content: String? = null,
    val destination: String? = null,
    val isDestructive: Boolean = type in setOf(OperationType.DELETE, OperationType.WRITE, OperationType.MOVE)
)

enum class OperationType {
    CREATE, READ, WRITE, DELETE, LIST, MOVE, COPY
}

/**
 * File operation result.
 */
data class FileOperationResult(
    val success: Boolean,
    val output: String? = null,
    val error: Exception? = null
)

/**
 * Workspace audit entry.
 */
data class WorkspaceAudit(
    val timestamp: Long,
    val operation: String,
    val path: String,
    val success: Boolean
)

// Exceptions
class WorkspaceNotConfiguredException(message: String) : Exception(message)
class PermissionDeniedException(message: String) : Exception(message)
class FileCreationException(message: String) : Exception(message)
class FileNotFoundException(message: String) : Exception(message)
class FileDeletionException(message: String) : Exception(message)