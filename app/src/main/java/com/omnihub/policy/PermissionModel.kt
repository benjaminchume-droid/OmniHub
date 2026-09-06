package com.omnihub.policy

enum class RiskLevel {
    READ,
    LOCAL_WRITE,
    EXTERNAL,
    SENSITIVE,
    DESTRUCTIVE,
    CRITICAL
}

class PermissionEngine {
    fun allow(risk: RiskLevel): Boolean = when (risk) {
        RiskLevel.READ, RiskLevel.LOCAL_WRITE -> true
        RiskLevel.EXTERNAL -> true
        RiskLevel.SENSITIVE, RiskLevel.DESTRUCTIVE -> true
        RiskLevel.CRITICAL -> false
    }
}
