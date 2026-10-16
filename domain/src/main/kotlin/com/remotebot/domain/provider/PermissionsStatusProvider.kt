package com.remotebot.domain.provider

data class PermissionItemInfo(
    val id: String,
    val name: String,
    val group: String, // "critical", "confidential", "standard"
    val isGranted: Boolean,
    val description: String = "",
)

interface PermissionsStatusProvider {
    fun getPermissionsStatus(): List<PermissionItemInfo>
}
