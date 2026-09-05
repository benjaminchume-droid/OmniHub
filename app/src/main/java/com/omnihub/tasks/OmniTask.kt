package com.omnihub.tasks

data class OmniTask(
    val id: String,
    val instruction: String,
    val schedule: String? = null,
    val agent: String? = null,
    val permissions: List<String> = emptyList(),
    val tools: List<String> = emptyList(),
    val workspace: String? = null,
    val sourcePreferences: List<String> = emptyList(),
    val lastRun: Long? = null,
    val nextRun: Long? = null,
    val executionHistory: List<String> = emptyList()
)
