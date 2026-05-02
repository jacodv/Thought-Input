package com.thoughtinput.capture.data.destinations

import com.thoughtinput.capture.data.ApiClient
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class SupabaseAdminException(message: String) : Exception(message)

class SupabaseAdmin(
    private val apiClient: ApiClient = ApiClient(),
    private val managementBase: String = "https://api.supabase.com"
) {

    private val json = Json { ignoreUnknownKeys = true }

    fun projectRef(projectURL: String): String? {
        var s = projectURL.trim()
        if (s.isEmpty()) return null
        if (!s.contains("://")) s = "https://$s"
        val host = runCatching { java.net.URI(s).host }.getOrNull()?.lowercase() ?: return null
        val suffix = ".supabase.co"
        if (!host.endsWith(suffix)) return null
        val ref = host.dropLast(suffix.length)
        if (ref.isEmpty() || ref.contains('.')) return null
        return ref
    }

    fun validateTableName(name: String): Boolean =
        Regex("^[A-Za-z_][A-Za-z0-9_]{0,62}$").matches(name)

    fun sqlFor(table: String, drop: Boolean): String {
        val drops = if (drop) "DROP TABLE IF EXISTS \"$table\" CASCADE;\n\n" else ""
        return drops + """
CREATE TABLE IF NOT EXISTS "$table" (
    id              bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    text            text        NOT NULL,
    timestamp       timestamptz NOT NULL,
    source_platform text        NOT NULL,
    client_version  text        NOT NULL,
    capture_method  text        NOT NULL,
    idempotency_key uuid        UNIQUE NOT NULL,
    device_name     text        NOT NULL,
    created_at      timestamptz DEFAULT now()
);

ALTER TABLE "$table" ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "Allow anonymous inserts" ON "$table";
CREATE POLICY "Allow anonymous inserts"
    ON "$table"
    FOR INSERT
    TO anon
    WITH CHECK (true);
""".trimStart('\n')
    }

    fun sqlEditorURL(projectRef: String): String =
        "https://supabase.com/dashboard/project/$projectRef/sql/new"

    suspend fun tableExists(projectRef: String, pat: String, table: String): Boolean {
        require(validateTableName(table)) { "Invalid table name" }
        val escaped = table.replace("'", "''")
        val sql = "SELECT EXISTS (SELECT 1 FROM pg_tables WHERE schemaname='public' AND tablename='$escaped') AS exists"
        val rows = runQuery(projectRef, pat, sql)
        val first = rows.firstOrNull() ?: throw SupabaseAdminException("Empty response from Management API")
        val value = first["exists"] ?: throw SupabaseAdminException("Missing 'exists' field in response")
        return when {
            value is JsonPrimitive && value.booleanOrNull() != null -> value.booleanOrNull()!!
            value is JsonPrimitive -> value.content == "t" || value.content == "true"
            else -> throw SupabaseAdminException("'exists' was not a boolean")
        }
    }

    suspend fun rowCount(projectRef: String, pat: String, table: String): Long {
        require(validateTableName(table)) { "Invalid table name" }
        val rows = runQuery(projectRef, pat, "SELECT count(*) AS count FROM \"$table\"")
        val first = rows.firstOrNull() ?: throw SupabaseAdminException("Empty response from Management API")
        val value = first["count"] ?: throw SupabaseAdminException("Missing 'count' field in response")
        return (value as? JsonPrimitive)?.content?.toLongOrNull()
            ?: throw SupabaseAdminException("'count' was not a number")
    }

    suspend fun initialize(projectRef: String, pat: String, table: String, drop: Boolean) {
        require(validateTableName(table)) { "Invalid table name" }
        runQuery(projectRef, pat, sqlFor(table, drop))
    }

    private suspend fun runQuery(projectRef: String, pat: String, sql: String): List<JsonObject> {
        val url = "$managementBase/v1/projects/$projectRef/database/query"
        val body = buildJsonObject { put("query", sql) }.toString().toByteArray(Charsets.UTF_8)
        val headers = mapOf("Authorization" to "Bearer $pat")

        val result = apiClient.post(url, headers, body, contentType = "application/json", timeoutMs = 30_000)
        when (result) {
            is ApiClient.Result.Success -> {
                if (result.body.isBlank()) return emptyList()
                val parsed: JsonElement = runCatching { json.parseToJsonElement(result.body) }
                    .getOrElse { throw SupabaseAdminException("Couldn't parse Management API response: ${it.message}") }
                return when (parsed) {
                    is JsonArray -> parsed.mapNotNull { it as? JsonObject }
                    is JsonObject -> listOf(parsed)
                    else -> emptyList()
                }
            }
            is ApiClient.Result.HttpError ->
                throw SupabaseAdminException("Management API returned ${result.statusCode}: ${result.body.take(500)}")
            is ApiClient.Result.IoError ->
                throw SupabaseAdminException("Network error: ${result.message}")
        }
    }

    private fun JsonPrimitive.booleanOrNull(): Boolean? =
        when (content.lowercase()) {
            "true" -> true
            "false" -> false
            else -> null
        }
}
