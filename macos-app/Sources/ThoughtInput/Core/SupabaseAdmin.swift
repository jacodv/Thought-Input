import Foundation

enum SupabaseAdminError: LocalizedError {
    case invalidProjectURL
    case invalidTableName
    case managementAPIError(statusCode: Int, body: String)
    case malformedResponse(String)

    var errorDescription: String? {
        switch self {
        case .invalidProjectURL:
            return "Couldn't parse the Supabase project ref from the project URL. Expected something like https://<ref>.supabase.co."
        case .invalidTableName:
            return "Table name must start with a letter or underscore and contain only letters, digits, and underscores (max 63 chars)."
        case .managementAPIError(let code, let body):
            let trimmed = body.trimmingCharacters(in: .whitespacesAndNewlines)
            return "Supabase Management API returned \(code)\(trimmed.isEmpty ? "" : ": \(trimmed)")"
        case .malformedResponse(let message):
            return "Unexpected response from Supabase Management API: \(message)"
        }
    }
}

enum SupabaseAdmin {

    private static let managementBase = "https://api.supabase.com"

    // MARK: - Pure helpers

    static func projectRef(from projectURL: String) -> String? {
        var s = projectURL.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !s.isEmpty else { return nil }
        if !s.contains("://") { s = "https://" + s }
        guard let url = URL(string: s), let host = url.host else { return nil }
        let lower = host.lowercased()
        let suffix = ".supabase.co"
        guard lower.hasSuffix(suffix) else { return nil }
        let ref = String(lower.dropLast(suffix.count))
        guard !ref.isEmpty, !ref.contains(".") else { return nil }
        return ref
    }

    static func validateTableName(_ name: String) -> Bool {
        let pattern = #"^[A-Za-z_][A-Za-z0-9_]{0,62}$"#
        return name.range(of: pattern, options: .regularExpression) != nil
    }

    static func sqlFor(table: String, drop: Bool) -> String {
        let drops = drop ? "DROP TABLE IF EXISTS \"\(table)\" CASCADE;\n\n" : ""
        return drops + """
        CREATE TABLE IF NOT EXISTS "\(table)" (
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

        ALTER TABLE "\(table)" ENABLE ROW LEVEL SECURITY;

        DROP POLICY IF EXISTS "Allow anonymous inserts" ON "\(table)";
        CREATE POLICY "Allow anonymous inserts"
            ON "\(table)"
            FOR INSERT
            TO anon
            WITH CHECK (true);
        """
    }

    static func sqlEditorURL(projectRef: String) -> URL? {
        URL(string: "https://supabase.com/dashboard/project/\(projectRef)/sql/new")
    }

    // MARK: - Network

    static func tableExists(projectRef: String, pat: String, table: String) async throws -> Bool {
        guard validateTableName(table) else { throw SupabaseAdminError.invalidTableName }
        let escaped = table.replacingOccurrences(of: "'", with: "''")
        let query = "SELECT EXISTS (SELECT 1 FROM pg_tables WHERE schemaname='public' AND tablename='\(escaped)') AS exists"
        let rows = try await runQuery(projectRef: projectRef, pat: pat, sql: query)
        guard let first = rows.first,
              let value = first["exists"] else {
            throw SupabaseAdminError.malformedResponse("Missing 'exists' field")
        }
        if let b = value as? Bool { return b }
        if let s = value as? String { return s == "t" || s == "true" }
        throw SupabaseAdminError.malformedResponse("'exists' was not a boolean")
    }

    static func rowCount(projectRef: String, pat: String, table: String) async throws -> Int {
        guard validateTableName(table) else { throw SupabaseAdminError.invalidTableName }
        let rows = try await runQuery(projectRef: projectRef, pat: pat, sql: "SELECT count(*) AS count FROM \"\(table)\"")
        guard let first = rows.first, let value = first["count"] else {
            throw SupabaseAdminError.malformedResponse("Missing 'count' field")
        }
        if let n = value as? Int { return n }
        if let n = value as? NSNumber { return n.intValue }
        if let s = value as? String, let n = Int(s) { return n }
        throw SupabaseAdminError.malformedResponse("'count' was not a number")
    }

    static func initialize(projectRef: String, pat: String, table: String, drop: Bool) async throws {
        guard validateTableName(table) else { throw SupabaseAdminError.invalidTableName }
        _ = try await runQuery(projectRef: projectRef, pat: pat, sql: sqlFor(table: table, drop: drop))
    }

    @discardableResult
    private static func runQuery(projectRef: String, pat: String, sql: String) async throws -> [[String: Any]] {
        let urlString = "\(managementBase)/v1/projects/\(projectRef)/database/query"
        guard let url = URL(string: urlString) else {
            throw SupabaseAdminError.invalidProjectURL
        }

        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.setValue("Bearer \(pat)", forHTTPHeaderField: "Authorization")
        request.timeoutInterval = 30
        request.httpBody = try JSONSerialization.data(withJSONObject: ["query": sql])

        let (data, response) = try await URLSession.shared.data(for: request)
        let status = (response as? HTTPURLResponse)?.statusCode ?? -1

        guard (200...299).contains(status) else {
            let body = String(data: data, encoding: .utf8) ?? ""
            throw SupabaseAdminError.managementAPIError(statusCode: status, body: body)
        }

        if data.isEmpty { return [] }
        let parsed = try? JSONSerialization.jsonObject(with: data)
        if let rows = parsed as? [[String: Any]] { return rows }
        if let _ = parsed as? [Any] { return [] }
        if let dict = parsed as? [String: Any] { return [dict] }
        return []
    }
}
