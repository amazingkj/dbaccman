package com.dbaccman.model

import kotlinx.serialization.Serializable

/**
 * Generic paginated response wrapper.
 */
@Serializable
data class PaginatedResponse<T>(
    val data: List<T>,
    val pagination: PaginationInfo
)

@Serializable
data class PaginationInfo(
    val page: Int,
    val pageSize: Int,
    val totalItems: Int,
    val totalPages: Int
) {
    companion object {
        fun of(page: Int, pageSize: Int, totalItems: Int): PaginationInfo {
            val totalPages = if (totalItems == 0) 1 else (totalItems + pageSize - 1) / pageSize
            return PaginationInfo(
                page = page,
                pageSize = pageSize,
                totalItems = totalItems,
                totalPages = totalPages
            )
        }
    }
}

/**
 * Paginated accounts response with additional stats.
 */
@Serializable
data class PaginatedAccountsResponse(
    val data: List<Account>,
    val pagination: PaginationInfo,
    val stats: AccountStats
)

@Serializable
data class AccountStats(
    val totalAccounts: Int,
    val lockedAccounts: Int,
    val activeAccounts: Int
)
