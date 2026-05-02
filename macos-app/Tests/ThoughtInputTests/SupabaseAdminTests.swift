import XCTest
@testable import ThoughtInput

final class SupabaseAdminTests: XCTestCase {

    func testProjectRefParsing() {
        XCTAssertEqual(SupabaseAdmin.projectRef(from: "https://abcdefgh.supabase.co"), "abcdefgh")
        XCTAssertEqual(SupabaseAdmin.projectRef(from: "https://abcdefgh.supabase.co/"), "abcdefgh")
        XCTAssertEqual(SupabaseAdmin.projectRef(from: "https://abcdefgh.supabase.co/rest/v1/"), "abcdefgh")
        XCTAssertEqual(SupabaseAdmin.projectRef(from: "https://ABCDEFGH.SUPABASE.CO"), "abcdefgh")
        XCTAssertEqual(SupabaseAdmin.projectRef(from: "abcdefgh.supabase.co"), "abcdefgh")
        XCTAssertNil(SupabaseAdmin.projectRef(from: ""))
        XCTAssertNil(SupabaseAdmin.projectRef(from: "https://example.com"))
        XCTAssertNil(SupabaseAdmin.projectRef(from: "not-a-url"))
        XCTAssertNil(SupabaseAdmin.projectRef(from: "https://.supabase.co"))
    }

    func testTableNameValidation() {
        XCTAssertTrue(SupabaseAdmin.validateTableName("captures"))
        XCTAssertTrue(SupabaseAdmin.validateTableName("_t1"))
        XCTAssertTrue(SupabaseAdmin.validateTableName("MyTable_2"))
        XCTAssertFalse(SupabaseAdmin.validateTableName(""))
        XCTAssertFalse(SupabaseAdmin.validateTableName("1table"))
        XCTAssertFalse(SupabaseAdmin.validateTableName("foo bar"))
        XCTAssertFalse(SupabaseAdmin.validateTableName("; DROP TABLE x;"))
        XCTAssertFalse(SupabaseAdmin.validateTableName(String(repeating: "a", count: 64)))
    }

    func testSqlForCreateContainsCanonicalSchema() {
        let sql = SupabaseAdmin.sqlFor(table: "captures", drop: false)
        XCTAssertTrue(sql.contains("CREATE TABLE IF NOT EXISTS \"captures\""))
        XCTAssertTrue(sql.contains("idempotency_key uuid        UNIQUE NOT NULL"))
        XCTAssertTrue(sql.contains("ALTER TABLE \"captures\" ENABLE ROW LEVEL SECURITY"))
        XCTAssertTrue(sql.contains("CREATE POLICY \"Allow anonymous inserts\""))
        XCTAssertFalse(sql.contains("DROP TABLE"))
    }

    func testSqlForDropPrependsDrop() {
        let sql = SupabaseAdmin.sqlFor(table: "thoughts", drop: true)
        XCTAssertTrue(sql.contains("DROP TABLE IF EXISTS \"thoughts\" CASCADE"))
        XCTAssertTrue(sql.contains("CREATE TABLE IF NOT EXISTS \"thoughts\""))
        // Drop must come before create
        let dropRange = sql.range(of: "DROP TABLE")!
        let createRange = sql.range(of: "CREATE TABLE")!
        XCTAssertLessThan(dropRange.lowerBound, createRange.lowerBound)
    }

    func testSqlEditorURL() {
        XCTAssertEqual(
            SupabaseAdmin.sqlEditorURL(projectRef: "abcdefgh")?.absoluteString,
            "https://supabase.com/dashboard/project/abcdefgh/sql/new"
        )
    }
}
