// swift-tools-version: 6.0
import PackageDescription

let package = Package(
    name: "BrainInput",
    platforms: [
        .macOS(.v15)
    ],
    targets: [
        .executableTarget(
            name: "BrainInput",
            path: "Sources/BrainInput",
            resources: [
                .process("Resources")
            ]
        ),
        .testTarget(
            name: "BrainInputTests",
            dependencies: ["BrainInput"],
            path: "Tests/BrainInputTests"
        )
    ]
)
