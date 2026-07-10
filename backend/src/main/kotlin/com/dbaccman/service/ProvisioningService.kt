package com.dbaccman.service

import com.dbaccman.config.SessionConnectionManager
import com.dbaccman.config.useOracleScriptContext
import com.dbaccman.dialect.DatabaseDialect
import com.dbaccman.dialect.MySQLDialect
import com.dbaccman.dialect.OracleDialect
import com.dbaccman.dialect.PostgreSQLDialect
import com.dbaccman.model.*
import com.dbaccman.util.AuditLogger
import com.dbaccman.util.InputValidator
import com.dbaccman.websocket.EventBroadcaster
import com.dbaccman.websocket.EventType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import java.sql.SQLException

/**
 * A provisioning step with both the executable SQL and a password-masked
 * variant that is safe to return to clients and write to logs.
 */
data class ExecutableProvisionStep(
    val order: Int,
    val title: String,
    val description: String,
    val sql: String,
    val maskedSql: String
)

private const val PASSWORD_MASK = "********"
private const val MAX_ROLES = 10

/**
 * Builds the ordered list of DDL statements for a provisioning request.
 * Pure (no session/connection dependency) so plans can be unit-tested per dialect.
 */
object ProvisionPlanner {

    fun buildSteps(dialect: DatabaseDialect, request: ProvisionRequest): List<ExecutableProvisionStep> {
        InputValidator.validateIdentifier(request.username, "username")
        InputValidator.validatePassword(request.password)

        return when (dialect) {
            is OracleDialect -> buildOracleSteps(dialect, request)
            is PostgreSQLDialect -> buildPostgresSteps(dialect, request)
            is MySQLDialect -> buildMySqlSteps(dialect, request)
            else -> throw IllegalArgumentException("Provisioning is not supported for this database type")
        }
    }

    private fun buildOracleSteps(dialect: OracleDialect, request: ProvisionRequest): List<ExecutableProvisionStep> {
        val opts = request.oracle
            ?: throw IllegalArgumentException("Oracle provisioning options are required")

        InputValidator.validateIdentifier(opts.dataTablespace, "dataTablespace")
        InputValidator.validateFilePath(opts.dataFilePath, "dataFilePath")
        InputValidator.validateSize(opts.dataSize, "dataSize")
        InputValidator.validateIdentifier(opts.tempTablespace, "tempTablespace")
        InputValidator.validateFilePath(opts.tempFilePath, "tempFilePath")
        InputValidator.validateSize(opts.tempSize, "tempSize")
        InputValidator.validateQuota(opts.quota)

        val indexTablespace = opts.indexTablespace?.takeIf { it.isNotBlank() }
        if (indexTablespace != null) {
            InputValidator.validateIdentifier(indexTablespace, "indexTablespace")
            val indexFilePath = opts.indexFilePath
                ?: throw IllegalArgumentException("indexFilePath is required when indexTablespace is specified")
            InputValidator.validateFilePath(indexFilePath, "indexFilePath")
            InputValidator.validateSize(opts.indexSize, "indexSize")
        }

        if (opts.roles.size > MAX_ROLES) {
            throw IllegalArgumentException("Too many roles specified (maximum $MAX_ROLES)")
        }
        opts.roles.forEach { InputValidator.validateIdentifier(it, "role") }
        opts.profile?.takeIf { it.isNotBlank() }?.let { InputValidator.validateIdentifier(it, "profile") }

        val steps = mutableListOf<ExecutableProvisionStep>()
        var order = 1

        val dataTsSql = dialect.getCreateDataTablespaceSql(opts.dataTablespace, opts.dataFilePath, opts.dataSize, opts.autoExtend)
        steps.add(ExecutableProvisionStep(order++, "Create data tablespace",
            "Tablespace ${opts.dataTablespace} (${opts.dataSize})", dataTsSql, dataTsSql))

        if (indexTablespace != null) {
            val indexTsSql = dialect.getCreateDataTablespaceSql(indexTablespace, opts.indexFilePath!!, opts.indexSize, opts.autoExtend)
            steps.add(ExecutableProvisionStep(order++, "Create index tablespace",
                "Tablespace $indexTablespace (${opts.indexSize})", indexTsSql, indexTsSql))
        }

        val tempTsSql = dialect.getCreateTempTablespaceSql(opts.tempTablespace, opts.tempFilePath, opts.tempSize)
        steps.add(ExecutableProvisionStep(order++, "Create temporary tablespace",
            "Tablespace ${opts.tempTablespace} (${opts.tempSize})", tempTsSql, tempTsSql))

        steps.add(ExecutableProvisionStep(order++, "Create user",
            "User ${request.username} with default/temporary tablespaces",
            dialect.getCreateUserWithTablespaceSql(request.username, request.password, opts.dataTablespace, opts.tempTablespace),
            dialect.getCreateUserWithTablespaceSql(request.username, PASSWORD_MASK, opts.dataTablespace, opts.tempTablespace)))

        opts.roles.forEach { role ->
            val grantSql = dialect.getGrantRoleSql(request.username, role)
            steps.add(ExecutableProvisionStep(order++, "Grant role $role",
                "Grant $role to ${request.username}", grantSql, grantSql))
        }

        val dataQuotaSql = dialect.getSetQuotaSql(request.username, opts.dataTablespace, opts.quota)
        steps.add(ExecutableProvisionStep(order++, "Set quota on data tablespace",
            "Quota ${opts.quota} on ${opts.dataTablespace}", dataQuotaSql, dataQuotaSql))

        if (indexTablespace != null) {
            val indexQuotaSql = dialect.getSetQuotaSql(request.username, indexTablespace, opts.quota)
            steps.add(ExecutableProvisionStep(order++, "Set quota on index tablespace",
                "Quota ${opts.quota} on $indexTablespace", indexQuotaSql, indexQuotaSql))
        }

        opts.profile?.takeIf { it.isNotBlank() }?.let { profile ->
            val profileSql = dialect.getAssignProfileSql(request.username, profile)
            steps.add(ExecutableProvisionStep(order++, "Assign profile",
                "Profile $profile for ${request.username}", profileSql, profileSql))
        }

        return steps
    }

    private fun buildPostgresSteps(dialect: PostgreSQLDialect, request: ProvisionRequest): List<ExecutableProvisionStep> {
        val opts = request.postgres
            ?: throw IllegalArgumentException("PostgreSQL provisioning options are required")

        InputValidator.validateIdentifier(opts.tablespace, "tablespace")
        InputValidator.validateFilePath(opts.location, "location")
        InputValidator.validateEncoding(opts.encoding)
        if (opts.connectionLimit < -1 || opts.connectionLimit > 10000) {
            throw IllegalArgumentException("connectionLimit must be between -1 (unlimited) and 10000")
        }
        val databaseName = opts.databaseName?.takeIf { it.isNotBlank() }
        databaseName?.let { InputValidator.validateIdentifier(it, "databaseName") }

        val steps = mutableListOf<ExecutableProvisionStep>()
        var order = 1

        val userOptions = listOfNotNull(
            "LOGIN",
            if (opts.createDb) "CREATEDB" else null,
            if (opts.createRole) "CREATEROLE" else null,
            if (opts.replication) "REPLICATION" else null
        ).joinToString(" ")
        steps.add(ExecutableProvisionStep(order++, "Create user",
            "User ${request.username} with $userOptions",
            dialect.getCreateUserWithOptionsSql(request.username, request.password, opts.createDb, opts.createRole, opts.replication),
            dialect.getCreateUserWithOptionsSql(request.username, PASSWORD_MASK, opts.createDb, opts.createRole, opts.replication)))

        val tsSql = dialect.getCreateOwnedTablespaceSql(opts.tablespace, request.username, opts.location)
        steps.add(ExecutableProvisionStep(order++, "Create tablespace",
            "Tablespace ${opts.tablespace} at ${opts.location} owned by ${request.username}", tsSql, tsSql))

        if (databaseName != null) {
            val dbSql = dialect.getCreateDatabaseSql(databaseName, request.username, opts.encoding, opts.tablespace, opts.connectionLimit)
            steps.add(ExecutableProvisionStep(order, "Create database",
                "Database $databaseName (${opts.encoding}) on ${opts.tablespace}", dbSql, dbSql))
        }

        return steps
    }

    private fun buildMySqlSteps(dialect: MySQLDialect, request: ProvisionRequest): List<ExecutableProvisionStep> {
        val opts = request.mysql ?: MySqlProvisionOptions()

        InputValidator.validateHost(opts.host)
        if (!Regex("^[a-zA-Z0-9_]+$").matches(opts.charset)) {
            throw IllegalArgumentException("Invalid charset name")
        }
        val databaseName = opts.databaseName?.takeIf { it.isNotBlank() }
        databaseName?.let { InputValidator.validateIdentifier(it, "databaseName") }

        val steps = mutableListOf<ExecutableProvisionStep>()
        var order = 1

        steps.add(ExecutableProvisionStep(order++, "Create user",
            "User ${request.username}@${opts.host}",
            dialect.getCreateUserSql(request.username, opts.host, request.password),
            dialect.getCreateUserSql(request.username, opts.host, PASSWORD_MASK)))

        if (databaseName != null) {
            val dbSql = dialect.getCreateDatabaseSql(databaseName, opts.charset)
            steps.add(ExecutableProvisionStep(order++, "Create database",
                "Database $databaseName (${opts.charset})", dbSql, dbSql))

            if (opts.grantAllOnDatabase) {
                val grantSql = dialect.getGrantAllOnDatabaseSql(databaseName, request.username, opts.host)
                steps.add(ExecutableProvisionStep(order++, "Grant privileges",
                    "ALL PRIVILEGES on $databaseName.* to ${request.username}@${opts.host}", grantSql, grantSql))
            }
        }

        dialect.getFlushPrivilegesSql().let { flushSql ->
            steps.add(ExecutableProvisionStep(order, "Flush privileges",
                "Reload privilege tables", flushSql, flushSql))
        }

        return steps
    }
}

/**
 * Executes account provisioning: tablespaces, user, grants, and quotas
 * in a single guided flow. Steps run sequentially; on the first failure the
 * remaining steps are reported as SKIPPED so the operator can see exactly
 * where the flow stopped and what still needs to be done.
 */
class ProvisioningService {
    private val logger = LoggerFactory.getLogger(ProvisioningService::class.java)
    private val eventScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun preview(sessionId: String, request: ProvisionRequest): ProvisionPlan {
        val dialect = SessionConnectionManager.getDialect(sessionId)
        val steps = ProvisionPlanner.buildSteps(dialect, request)
        return ProvisionPlan(
            dbType = dialect.type.name,
            steps = steps.map { ProvisionStep(it.order, it.title, it.description, it.maskedSql) }
        )
    }

    fun execute(sessionId: String, request: ProvisionRequest): ProvisionResult {
        val results = mutableListOf<ProvisionStepResult>()
        var failed = false

        useOracleScriptContext(sessionId) { conn, dialect ->
            val steps = ProvisionPlanner.buildSteps(dialect, request)

            for (step in steps) {
                if (failed) {
                    results.add(ProvisionStepResult(step.order, step.title, step.maskedSql, "SKIPPED"))
                    continue
                }

                val start = System.currentTimeMillis()
                try {
                    conn.createStatement().use { stmt ->
                        stmt.execute(step.sql)
                    }
                    results.add(ProvisionStepResult(
                        step.order, step.title, step.maskedSql, "SUCCESS",
                        durationMs = System.currentTimeMillis() - start
                    ))
                } catch (e: SQLException) {
                    logger.error("Provisioning step '${step.title}' failed: ${e.message}, ErrorCode: ${e.errorCode}")
                    results.add(ProvisionStepResult(
                        step.order, step.title, step.maskedSql, "FAILED",
                        error = e.message,
                        durationMs = System.currentTimeMillis() - start
                    ))
                    failed = true
                }
            }
        }

        val success = !failed
        AuditLogger.log(
            "PROVISION_ACCOUNT",
            "Provisioned account ${request.username}: " +
                "${results.count { it.status == "SUCCESS" }} succeeded, " +
                "${results.count { it.status == "FAILED" }} failed, " +
                "${results.count { it.status == "SKIPPED" }} skipped"
        )

        if (success) {
            eventScope.launch {
                try {
                    EventBroadcaster.broadcast(EventType.ACCOUNT_CREATED, mapOf("username" to request.username))
                } catch (e: Exception) {
                    logger.warn("Failed to broadcast provisioning event: ${e.message}")
                }
            }
        }

        return ProvisionResult(success, results)
    }
}
