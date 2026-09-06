package com.omnihub.policy

enum class RiskLevel {
    READ,
    LOCAL_WRITE,
    EXTERNAL,
    SENSITIVE,
    DESTRUCTIVE,
    CRITICAL
}

enum class OmniCapability {
    READ_FILE,
    WRITE_FILE,
    MOVE_FILE,
    DELETE_FILE,
    CREATE_DIRECTORY,
    RUN_PROCESS,
    HTTP_REQUEST,
    OPEN_APP,
    UI_CLICK,
    UI_TYPE,
    SEND_MESSAGE,
    SCHEDULE_TASK,
    GIT,
    TERMINAL
}

data class PermissionDecision(
    val allowed: Boolean,
    val requiresUserApproval: Boolean = false,
    val reason: String = ""
)

object PermissionEngine {
    fun evaluate(capability: OmniCapability): PermissionDecision = when (capability) {
        OmniCapability.READ_FILE, OmniCapability.CREATE_DIRECTORY ->
            PermissionDecision(allowed = true, reason = "read/sandbox ok")
        OmniCapability.WRITE_FILE, OmniCapability.MOVE_FILE ->
            PermissionDecision(allowed = true, reason = "local write in Omni folder")
        OmniCapability.HTTP_REQUEST, OmniCapability.SEND_MESSAGE, OmniCapability.GIT ->
            PermissionDecision(allowed = true, requiresUserApproval = false, reason = "external")
        OmniCapability.RUN_PROCESS, OmniCapability.TERMINAL ->
            PermissionDecision(allowed = true, requiresUserApproval = false, reason = "terminal sandbox")
        OmniCapability.DELETE_FILE ->
            PermissionDecision(allowed = true, requiresUserApproval = true, reason = "destructive")
        OmniCapability.OPEN_APP, OmniCapability.UI_CLICK, OmniCapability.UI_TYPE, OmniCapability.SCHEDULE_TASK ->
            PermissionDecision(allowed = true, requiresUserApproval = true, reason = "device action")
    }

    fun allow(risk: RiskLevel): Boolean = when (risk) {
        RiskLevel.READ, RiskLevel.LOCAL_WRITE -> true
        RiskLevel.EXTERNAL, RiskLevel.SENSITIVE, RiskLevel.DESTRUCTIVE -> true
        RiskLevel.CRITICAL -> false
    }
}
