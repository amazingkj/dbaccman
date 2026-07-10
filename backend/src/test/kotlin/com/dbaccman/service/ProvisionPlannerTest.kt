package com.dbaccman.service

import com.dbaccman.dialect.MySQLDialect
import com.dbaccman.dialect.OracleDialect
import com.dbaccman.dialect.PostgreSQLDialect
import com.dbaccman.exception.ValidationException
import com.dbaccman.model.MySqlProvisionOptions
import com.dbaccman.model.OracleProvisionOptions
import com.dbaccman.model.PostgresProvisionOptions
import com.dbaccman.model.ProvisionRequest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ProvisionPlannerTest {

    private val oracleDialect = OracleDialect()
    private val postgresDialect = PostgreSQLDialect()
    private val mysqlDialect = MySQLDialect()

    private fun oracleRequest(
        options: OracleProvisionOptions = OracleProvisionOptions(
            dataTablespace = "apim_demo_tablespace",
            dataFilePath = "/u02/oradata/apim/apim_demo_tablespace.dbf",
            dataSize = "8G",
            indexTablespace = "apim_demo_indexspace",
            indexFilePath = "/u02/oradata/apim/apim_demo_indexspace.dbf",
            indexSize = "1G",
            tempTablespace = "apim_demo_tempspace",
            tempFilePath = "/u02/oradata/apim/temp/apim_demo_tempspace.dbf",
            tempSize = "256M",
            roles = listOf("CONNECT", "RESOURCE")
        )
    ) = ProvisionRequest(
        username = "apim_demo",
        password = "apimdemo2024!!09",
        oracle = options
    )

    // ==================== Oracle ====================

    @Test
    @DisplayName("Oracle plan should follow tablespaces -> user -> grants -> quotas order")
    fun testOraclePlanOrder() {
        val steps = ProvisionPlanner.buildSteps(oracleDialect, oracleRequest())

        val titles = steps.map { it.title }
        assertEquals(
            listOf(
                "Create data tablespace",
                "Create index tablespace",
                "Create temporary tablespace",
                "Create user",
                "Grant role CONNECT",
                "Grant role RESOURCE",
                "Set quota on data tablespace",
                "Set quota on index tablespace"
            ),
            titles
        )
        assertEquals((1..8).toList(), steps.map { it.order })
    }

    @Test
    @DisplayName("Oracle plan should generate correct DDL statements")
    fun testOraclePlanSql() {
        val steps = ProvisionPlanner.buildSteps(oracleDialect, oracleRequest())

        assertEquals(
            "CREATE TABLESPACE APIM_DEMO_TABLESPACE DATAFILE '/u02/oradata/apim/apim_demo_tablespace.dbf' SIZE 8G",
            steps[0].sql
        )
        assertEquals(
            "CREATE TEMPORARY TABLESPACE APIM_DEMO_TEMPSPACE TEMPFILE '/u02/oradata/apim/temp/apim_demo_tempspace.dbf' SIZE 256M",
            steps[2].sql
        )
        assertEquals(
            "CREATE USER APIM_DEMO IDENTIFIED BY \"apimdemo2024!!09\"" +
                " DEFAULT TABLESPACE APIM_DEMO_TABLESPACE TEMPORARY TABLESPACE APIM_DEMO_TEMPSPACE",
            steps[3].sql
        )
        assertEquals("GRANT CONNECT TO APIM_DEMO", steps[4].sql)
        assertEquals("ALTER USER APIM_DEMO QUOTA UNLIMITED ON APIM_DEMO_TABLESPACE", steps[6].sql)
    }

    @Test
    @DisplayName("Oracle create user step should mask password in maskedSql")
    fun testOraclePasswordMasked() {
        val steps = ProvisionPlanner.buildSteps(oracleDialect, oracleRequest())
        val createUser = steps.first { it.title == "Create user" }

        assertFalse(createUser.maskedSql.contains("apimdemo2024!!09"))
        assertTrue(createUser.maskedSql.contains("********"))
        assertTrue(createUser.sql.contains("apimdemo2024!!09"))
    }

    @Test
    @DisplayName("Oracle plan without index tablespace should skip index steps")
    fun testOracleWithoutIndexTablespace() {
        val request = oracleRequest(
            OracleProvisionOptions(
                dataTablespace = "app_ts",
                dataFilePath = "/u02/oradata/app_ts.dbf",
                tempTablespace = "app_temp",
                tempFilePath = "/u02/oradata/app_temp.dbf",
                roles = emptyList()
            )
        )
        val steps = ProvisionPlanner.buildSteps(oracleDialect, request)

        assertTrue(steps.none { it.title.contains("index") })
        assertEquals(
            listOf("Create data tablespace", "Create temporary tablespace", "Create user", "Set quota on data tablespace"),
            steps.map { it.title }
        )
    }

    @Test
    @DisplayName("Oracle plan with autoExtend should add AUTOEXTEND clause")
    fun testOracleAutoExtend() {
        val request = oracleRequest(
            OracleProvisionOptions(
                dataTablespace = "app_ts",
                dataFilePath = "/u02/oradata/app_ts.dbf",
                tempTablespace = "app_temp",
                tempFilePath = "/u02/oradata/app_temp.dbf",
                autoExtend = true
            )
        )
        val steps = ProvisionPlanner.buildSteps(oracleDialect, request)

        assertTrue(steps[0].sql.contains("AUTOEXTEND ON NEXT 100M MAXSIZE UNLIMITED"))
    }

    @Test
    @DisplayName("Oracle plan with profile should append profile assignment step")
    fun testOracleProfile() {
        val request = oracleRequest(
            OracleProvisionOptions(
                dataTablespace = "app_ts",
                dataFilePath = "/u02/oradata/app_ts.dbf",
                tempTablespace = "app_temp",
                tempFilePath = "/u02/oradata/app_temp.dbf",
                profile = "defaultprofile"
            )
        )
        val steps = ProvisionPlanner.buildSteps(oracleDialect, request)

        assertEquals("Assign profile", steps.last().title)
        assertEquals("ALTER USER APIM_DEMO PROFILE DEFAULTPROFILE", steps.last().sql)
    }

    @Test
    @DisplayName("Oracle plan should reject missing options")
    fun testOracleMissingOptions() {
        val request = ProvisionRequest(username = "app", password = "Password1!")
        assertThrows<IllegalArgumentException> {
            ProvisionPlanner.buildSteps(oracleDialect, request)
        }
    }

    @Test
    @DisplayName("Oracle plan should reject malicious file path")
    fun testOracleMaliciousPath() {
        val request = oracleRequest(
            OracleProvisionOptions(
                dataTablespace = "app_ts",
                dataFilePath = "/u02/oradata/app.dbf'; DROP TABLE users; --",
                tempTablespace = "app_temp",
                tempFilePath = "/u02/oradata/app_temp.dbf"
            )
        )
        assertThrows<ValidationException> {
            ProvisionPlanner.buildSteps(oracleDialect, request)
        }
    }

    @Test
    @DisplayName("Oracle plan should reject path traversal")
    fun testOraclePathTraversal() {
        val request = oracleRequest(
            OracleProvisionOptions(
                dataTablespace = "app_ts",
                dataFilePath = "/u02/../etc/passwd.dbf",
                tempTablespace = "app_temp",
                tempFilePath = "/u02/oradata/app_temp.dbf"
            )
        )
        assertThrows<ValidationException> {
            ProvisionPlanner.buildSteps(oracleDialect, request)
        }
    }

    @Test
    @DisplayName("Oracle plan should require indexFilePath when indexTablespace is set")
    fun testOracleIndexFilePathRequired() {
        val request = oracleRequest(
            OracleProvisionOptions(
                dataTablespace = "app_ts",
                dataFilePath = "/u02/oradata/app_ts.dbf",
                indexTablespace = "app_idx",
                indexFilePath = null,
                tempTablespace = "app_temp",
                tempFilePath = "/u02/oradata/app_temp.dbf"
            )
        )
        assertThrows<IllegalArgumentException> {
            ProvisionPlanner.buildSteps(oracleDialect, request)
        }
    }

    // ==================== PostgreSQL ====================

    private fun postgresRequest(
        options: PostgresProvisionOptions = PostgresProvisionOptions(
            tablespace = "apim_demo_tablespace",
            location = "/postgre/apim/apim_demo",
            createDb = true,
            databaseName = "apim_demo",
            encoding = "UTF8",
            connectionLimit = -1
        )
    ) = ProvisionRequest(
        username = "apim_demo",
        password = "apimdemo2024!!09",
        postgres = options
    )

    @Test
    @DisplayName("PostgreSQL plan should follow user -> tablespace -> database order")
    fun testPostgresPlanOrder() {
        val steps = ProvisionPlanner.buildSteps(postgresDialect, postgresRequest())

        assertEquals(listOf("Create user", "Create tablespace", "Create database"), steps.map { it.title })
    }

    @Test
    @DisplayName("PostgreSQL plan should generate correct DDL statements")
    fun testPostgresPlanSql() {
        val steps = ProvisionPlanner.buildSteps(postgresDialect, postgresRequest())

        assertEquals(
            "CREATE USER \"apim_demo\" WITH LOGIN CREATEDB PASSWORD 'apimdemo2024!!09'",
            steps[0].sql
        )
        assertEquals(
            "CREATE TABLESPACE \"apim_demo_tablespace\" OWNER \"apim_demo\" LOCATION '/postgre/apim/apim_demo'",
            steps[1].sql
        )
        assertEquals(
            "CREATE DATABASE \"apim_demo\" WITH OWNER = \"apim_demo\" ENCODING = 'UTF8'" +
                " TABLESPACE = \"apim_demo_tablespace\" CONNECTION LIMIT = -1",
            steps[2].sql
        )
    }

    @Test
    @DisplayName("PostgreSQL create user step should mask password")
    fun testPostgresPasswordMasked() {
        val steps = ProvisionPlanner.buildSteps(postgresDialect, postgresRequest())

        assertFalse(steps[0].maskedSql.contains("apimdemo2024!!09"))
        assertTrue(steps[0].maskedSql.contains("********"))
    }

    @Test
    @DisplayName("PostgreSQL plan without database name should skip database step")
    fun testPostgresWithoutDatabase() {
        val request = postgresRequest(
            PostgresProvisionOptions(
                tablespace = "app_ts",
                location = "/postgre/app",
                databaseName = null
            )
        )
        val steps = ProvisionPlanner.buildSteps(postgresDialect, request)

        assertEquals(listOf("Create user", "Create tablespace"), steps.map { it.title })
    }

    @Test
    @DisplayName("PostgreSQL plan should reject invalid connection limit")
    fun testPostgresInvalidConnectionLimit() {
        val request = postgresRequest(
            PostgresProvisionOptions(
                tablespace = "app_ts",
                location = "/postgre/app",
                connectionLimit = -5
            )
        )
        assertThrows<IllegalArgumentException> {
            ProvisionPlanner.buildSteps(postgresDialect, request)
        }
    }

    @Test
    @DisplayName("PostgreSQL plan should reject invalid encoding")
    fun testPostgresInvalidEncoding() {
        val request = postgresRequest(
            PostgresProvisionOptions(
                tablespace = "app_ts",
                location = "/postgre/app",
                encoding = "UTF8'; DROP DATABASE x; --"
            )
        )
        assertThrows<ValidationException> {
            ProvisionPlanner.buildSteps(postgresDialect, request)
        }
    }

    // ==================== MySQL ====================

    @Test
    @DisplayName("MySQL plan should create user, database, grant, and flush")
    fun testMySqlPlan() {
        val request = ProvisionRequest(
            username = "app_user",
            password = "AppUser1!",
            mysql = MySqlProvisionOptions(host = "%", databaseName = "app_db")
        )
        val steps = ProvisionPlanner.buildSteps(mysqlDialect, request)

        assertEquals(
            listOf("Create user", "Create database", "Grant privileges", "Flush privileges"),
            steps.map { it.title }
        )
        assertEquals("CREATE DATABASE `app_db` CHARACTER SET utf8mb4", steps[1].sql)
        assertEquals("GRANT ALL PRIVILEGES ON `app_db`.* TO `app_user`@`%`", steps[2].sql)
    }

    @Test
    @DisplayName("MySQL plan without database should only create user and flush")
    fun testMySqlWithoutDatabase() {
        val request = ProvisionRequest(
            username = "app_user",
            password = "AppUser1!",
            mysql = MySqlProvisionOptions(host = "%")
        )
        val steps = ProvisionPlanner.buildSteps(mysqlDialect, request)

        assertEquals(listOf("Create user", "Flush privileges"), steps.map { it.title })
    }

    @Test
    @DisplayName("MySQL plan should mask password in create user step")
    fun testMySqlPasswordMasked() {
        val request = ProvisionRequest(
            username = "app_user",
            password = "AppUser1!",
            mysql = MySqlProvisionOptions()
        )
        val steps = ProvisionPlanner.buildSteps(mysqlDialect, request)

        assertFalse(steps[0].maskedSql.contains("AppUser1!"))
        assertTrue(steps[0].maskedSql.contains("********"))
    }

    // ==================== Common validation ====================

    @Test
    @DisplayName("Weak password should be rejected for all dialects")
    fun testWeakPasswordRejected() {
        val request = ProvisionRequest(
            username = "app_user",
            password = "weak",
            mysql = MySqlProvisionOptions()
        )
        assertThrows<ValidationException> {
            ProvisionPlanner.buildSteps(mysqlDialect, request)
        }
    }

    @Test
    @DisplayName("Invalid username should be rejected")
    fun testInvalidUsernameRejected() {
        val request = ProvisionRequest(
            username = "bad;user",
            password = "AppUser1!",
            mysql = MySqlProvisionOptions()
        )
        assertThrows<ValidationException> {
            ProvisionPlanner.buildSteps(mysqlDialect, request)
        }
    }
}
