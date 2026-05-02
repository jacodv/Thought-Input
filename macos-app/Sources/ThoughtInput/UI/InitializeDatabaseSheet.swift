import SwiftUI
import AppKit

struct InitializeDatabaseSheet: View {
    let projectURL: String
    let tableName: String
    let onClose: () -> Void
    let onCompleted: () -> Void

    @State private var pat: String = ""
    @State private var manualRefInput: String = ""
    @State private var isWorking = false
    @State private var status: String?
    @State private var statusIsError = false
    @State private var pendingDropConfirmRowCount: Int?

    private var projectRef: String? {
        SupabaseAdmin.projectRef(from: projectURL) ?? (manualRefInput.isEmpty ? nil : manualRefInput)
    }

    private var tableNameValid: Bool {
        SupabaseAdmin.validateTableName(tableName)
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            Text("Initialize Database")
                .font(.title2).bold()

            if !tableNameValid {
                errorBanner("Invalid table name. Must start with a letter or underscore and contain only letters, digits, underscores (≤63 chars).")
            } else {
                Text("Target table: \(tableName)\(projectRef.map { "  •  Project ref: \($0)" } ?? "")")
                    .font(.caption)
                    .foregroundColor(.secondary)
            }

            if SupabaseAdmin.projectRef(from: projectURL) == nil {
                projectRefField
            }

            Divider()

            // Path 1: PAT
            VStack(alignment: .leading, spacing: 8) {
                Text("Run from this app").font(.headline)
                Text("Paste a Supabase Personal Access Token. Used once and discarded — never stored.")
                    .font(.caption)
                    .foregroundColor(.secondary)

                SecureField("Personal Access Token", text: $pat)
                    .textFieldStyle(.roundedBorder)

                HStack {
                    Button(action: openTokensPage) {
                        Text("Where do I get one?").font(.caption)
                    }
                    .buttonStyle(.link)
                    Spacer()
                    Button("Run via API") {
                        Task { await runViaAPI() }
                    }
                    .disabled(!canRunAPI)
                }
            }

            Divider()

            // Path 2: copy SQL
            VStack(alignment: .leading, spacing: 8) {
                Text("Run it yourself").font(.headline)
                Text("Copy the SQL and run it in the Supabase SQL Editor. No PAT required.")
                    .font(.caption)
                    .foregroundColor(.secondary)

                HStack {
                    Button("Copy SQL (Create)") { copySQL(drop: false) }
                    Button("Copy SQL (Drop + Recreate)") { copySQL(drop: true) }
                    if let ref = projectRef {
                        Button("Open SQL Editor") {
                            if let url = SupabaseAdmin.sqlEditorURL(projectRef: ref) {
                                NSWorkspace.shared.open(url)
                            }
                        }
                    }
                }
            }

            // Status / progress
            if isWorking {
                HStack {
                    ProgressView().controlSize(.small)
                    Text("Working…").font(.caption).foregroundColor(.secondary)
                }
            } else if let status {
                Text(status)
                    .font(.caption)
                    .foregroundColor(statusIsError ? .red : .green)
                    .lineLimit(4)
            }

            Spacer(minLength: 0)

            HStack {
                Spacer()
                Button("Close", action: onClose)
                    .keyboardShortcut(.cancelAction)
            }
        }
        .padding(20)
        .frame(width: 520, height: 460)
    }

    private var projectRefField: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text("Project Ref").font(.caption).foregroundColor(.secondary)
            TextField("e.g. abcdefgh (subdomain of your project URL)", text: $manualRefInput)
                .textFieldStyle(.roundedBorder)
        }
    }

    private var canRunAPI: Bool {
        !isWorking && tableNameValid && !pat.isEmpty && projectRef != nil
    }

    private func errorBanner(_ message: String) -> some View {
        Text(message)
            .font(.caption)
            .foregroundColor(.red)
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(8)
            .background(Color.red.opacity(0.08))
            .cornerRadius(6)
    }

    private func openTokensPage() {
        if let url = URL(string: "https://supabase.com/dashboard/account/tokens") {
            NSWorkspace.shared.open(url)
        }
    }

    private func copySQL(drop: Bool) {
        guard tableNameValid else { return }
        let sql = SupabaseAdmin.sqlFor(table: tableName, drop: drop)
        let pb = NSPasteboard.general
        pb.clearContents()
        pb.setString(sql, forType: .string)
        setStatus("SQL copied to clipboard.", error: false)
    }

    private func runViaAPI() async {
        guard let ref = projectRef else { return }
        isWorking = true
        defer { isWorking = false }
        setStatus(nil)

        do {
            let exists = try await SupabaseAdmin.tableExists(projectRef: ref, pat: pat, table: tableName)
            if exists {
                let count = (try? await SupabaseAdmin.rowCount(projectRef: ref, pat: pat, table: tableName)) ?? 0
                let confirm = NSAlert()
                confirm.messageText = "Drop and recreate '\(tableName)'?"
                confirm.informativeText = count > 0
                    ? "Table has \(count) row\(count == 1 ? "" : "s"). Continuing will drop the table and recreate it. Data will be lost."
                    : "Table is empty. Continuing will drop the table and recreate it."
                confirm.alertStyle = .warning
                confirm.addButton(withTitle: "Drop and Recreate")
                confirm.addButton(withTitle: "Cancel")
                guard confirm.runModal() == .alertFirstButtonReturn else {
                    setStatus("Cancelled.", error: false)
                    return
                }
                try await SupabaseAdmin.initialize(projectRef: ref, pat: pat, table: tableName, drop: true)
                setStatus("Recreated.", error: false)
            } else {
                try await SupabaseAdmin.initialize(projectRef: ref, pat: pat, table: tableName, drop: false)
                setStatus("Initialized.", error: false)
            }
            pat = ""
            onCompleted()
        } catch {
            setStatus(error.localizedDescription, error: true)
        }
    }

    private func setStatus(_ text: String?, error: Bool = false) {
        status = text
        statusIsError = error
    }
}
