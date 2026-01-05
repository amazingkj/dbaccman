package com.dbaccman.model

import kotlinx.serialization.Serializable

@Serializable
data class Account(
    val username: String,
    val host: String,
    val created: String? = null,
    val passwordLastChanged: String? = null,
    val passwordLifetime: Int? = null,
    val accountLocked: Boolean = false
)

@Serializable
data class CreateAccountRequest(
    val username: String,
    val host: String = "%",
    val password: String,
    val expireDays: Int = 90
)

@Serializable
data class ChangePasswordRequest(
    val password: String,
    val expireImmediately: Boolean = false
)

@Serializable
data class ExpiringAccount(
    val username: String,
    val host: String,
    val daysUntilExpiry: Int
)

@Serializable
data class SetTablespaceRequest(
    val username: String,
    val host: String = "%",
    val tablespace: String,
    val quota: String? = null  // e.g., "UNLIMITED", "100M", "1G"
)

@Serializable
data class LoginRequest(
    val username: String,
    val password: String
)

@Serializable
data class LoginResponse(
    val token: String,
    val username: String,
    val role: String
)

@Serializable
data class UserInfo(
    val username: String,
    val role: String
)

// ==================== Clone Account ====================

@Serializable
data class CloneAccountRequest(
    val sourceUsername: String,
    val sourceHost: String = "%",
    val newUsername: String,
    val newHost: String = "%",
    val newPassword: String,
    val copyPermissions: Boolean = true,
    val expireDays: Int = 90
)

// ==================== Batch Operations ====================

@Serializable
data class BatchCreateAccountRequest(
    val accounts: List<CreateAccountRequest>
)

@Serializable
data class BatchDeleteRequest(
    val accounts: List<AccountIdentifier>
)

@Serializable
data class BatchUnlockRequest(
    val accounts: List<AccountIdentifier>
)

@Serializable
data class AccountIdentifier(
    val username: String,
    val host: String = "%"
)

@Serializable
data class BatchOperationResult(
    val success: List<String>,
    val failed: List<BatchOperationError>
)

@Serializable
data class BatchOperationError(
    val account: String,
    val error: String
)

// ==================== Role Management (Oracle) ====================

@Serializable
data class Role(
    val name: String,
    val isDefault: Boolean = false,
    val isAdmin: Boolean = false
)

@Serializable
data class UserRole(
    val username: String,
    val roleName: String,
    val isDefault: Boolean = false,
    val isAdmin: Boolean = false
)

@Serializable
data class GrantRoleRequest(
    val username: String,
    val host: String = "%",
    val roles: List<String>,
    val withAdminOption: Boolean = false
)

@Serializable
data class RevokeRoleRequest(
    val username: String,
    val host: String = "%",
    val roles: List<String>
)

// ==================== PDB Management (Oracle) ====================

@Serializable
data class PdbInfo(
    val name: String,
    val openMode: String,
    val restricted: Boolean = false
)

// ==================== Export ====================

@Serializable
data class ExportRequest(
    val format: String = "csv",  // csv, json
    val includePermissions: Boolean = false
)
