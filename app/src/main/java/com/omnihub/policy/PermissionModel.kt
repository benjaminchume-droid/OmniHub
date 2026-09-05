package com.omnihub.policy

enum class RiskLevel {
    READ_ONLY,
    LOCAL_SANDBOX,
    EXTERNAL_COMM,
    SENSITIVE,
    DESTRUCTIVE,
    CRITICAL,
    UNKNOWN
}

enum class OmniCapability {
    READ_FILE, WRITE_FILE, MOVE_FILE, DELETE_FILE, CREATE_DIRECTORY,
    RUN_PROCESS, HTTP_REQUEST, OPEN_APP, UI_CLICK, UI_TYPE,
    SEND_MESSAGE, SCHEDULE_TASK, MCP_TOOL, CREDENTIAL_EXPORT
}

data class PolicyDecision(
    val allowed: Boolean,
    val requiresUserApproval: Boolean = false,
    val requiresBiometric: Boolean = false,
    val reason: String = ""
)

/** Fail-closed permission engine. UNKNOWN → DENY. */
object PermissionEngine {
    fun riskFor(capability: OmniCapability): RiskLevel = when (capability) {
        OmniCapability.READ_FILE -> RiskLevel.READ_ONLY
        OmniCapability.WRITE_FILE, OmniCapability.CREATE_DIRECTORY -> RiskLevel.LOCAL_SANDBOX
        OmniCapability.HTTP_REQUEST, OmniCapability.OPEN_APP, OmniCapability.SEND_MESSAGE -> RiskLevel.EXTERNAL_COMM
        OmniCapability.MCP_TOOL, OmniCapability.SCHEDULE_TASK -> RiskLevel.SENSITIVE
        OmniCapability.DELETE_FILE, OmniCapability.MOVE_FILE, OmniCapability.RUN_PROCESS,
        OmniCapability.UI_CLICK, OmniCapability.UI_TYPE -> RiskLevel.DESTRUCTIVE
        OmniCapability.CREDENTIAL_EXPORT -> RiskLevel.CRITICAL
    }

    fun evaluate(capability: OmniCapability, userGranted: Set<OmniCapability> = emptySet()): PolicyDecision {
        val risk = riskFor(capability)
        return when (risk) {
            RiskLevel.READ_ONLY, RiskLevel.LOCAL_SANDBOX ->
                PolicyDecision(allowed = true)
            RiskLevel.EXTERNAL_COMM ->
                PolicyDecision(
                    allowed = capability in userGranted,
                    requiresUserApproval = capability !in userGranted,
                    reason = if (capability in userGranted) "granted" else "needs approval"
                )
            RiskLevel.SENSITIVE ->
                PolicyDecision(
                    allowed = capability in userGranted,
                    requiresUserApproval = true,
                    reason = "sensitive capability"
                )
            RiskLevel.DESTRUCTIVE ->
                PolicyDecision(
                    allowed = false,
                    requiresUserApproval = true,
                    reason = "destructive — explicit confirmation required"
                )
            RiskLevel.CRITICAL ->
                PolicyDecision(
                    allowed = false,
                    requiresUserApproval = true,
                    requiresBiometric = true,
                    reason = "critical — biometric + confirmation"
                )
            RiskLevel.UNKNOWN ->
                PolicyDecision(allowed = false, reason = "unknown capability — deny")
        }
    }
}
