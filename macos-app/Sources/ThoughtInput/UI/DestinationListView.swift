import SwiftUI

struct DestinationListView: View {
    @ObservedObject var store: DestinationStore
    @State private var editingDestination: Destination?
    @State private var isAddingNew = false
    @State private var selection: UUID?

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            if store.destinations.isEmpty {
                Text("No destinations configured. Add one to start capturing.")
                    .foregroundColor(.secondary)
                    .font(.callout)
                    .padding(.vertical, 8)
            } else {
                List(selection: $selection) {
                    ForEach(store.destinations) { destination in
                        DestinationRow(destination: destination, store: store)
                            .tag(destination.id)
                            .contentShape(Rectangle())
                            .onTapGesture(count: 2) {
                                editingDestination = destination
                            }
                    }
                }
                .listStyle(.bordered)
                .frame(minHeight: 120, maxHeight: 200)
            }

            HStack(spacing: 4) {
                Button(action: { isAddingNew = true }) {
                    Image(systemName: "plus")
                }

                Button(action: deleteSelected) {
                    Image(systemName: "minus")
                }
                .disabled(selection == nil)

                Spacer()

                if store.destinations.count > 1 {
                    Text("Double-click to edit")
                        .font(.caption)
                        .foregroundColor(.secondary)
                }
            }
        }
        .sheet(isPresented: $isAddingNew) {
            DestinationEditorView(store: store, existing: nil) {
                isAddingNew = false
            }
        }
        .sheet(item: $editingDestination) { destination in
            DestinationEditorView(store: store, existing: destination) {
                editingDestination = nil
            }
        }
    }

    private func deleteSelected() {
        guard let id = selection,
              let destination = store.destinations.first(where: { $0.id == id }) else { return }
        store.delete(destination)
        selection = nil
    }
}

// MARK: - Row

private struct DestinationRow: View {
    let destination: Destination
    @ObservedObject var store: DestinationStore

    var body: some View {
        HStack {
            Image(systemName: destination.type.iconName)
                .frame(width: 20)
                .foregroundColor(.secondary)

            VStack(alignment: .leading, spacing: 2) {
                Text(destination.name)
                    .fontWeight(destination.isActive ? .semibold : .regular)
                Text(destination.type.displayName)
                    .font(.caption)
                    .foregroundColor(.secondary)
            }

            Spacer()

            if destination.isActive {
                Image(systemName: "checkmark.circle.fill")
                    .foregroundColor(.accentColor)
            } else {
                Button("Set Active") {
                    store.setActive(destination)
                }
                .buttonStyle(.borderless)
                .font(.caption)
            }
        }
        .padding(.vertical, 2)
    }
}
