// swift-tools-version: 6.0
import PackageDescription

let package = Package(
    name: "ThoughtInput",
    platforms: [
        .macOS(.v15)
    ],
    targets: [
        .executableTarget(
            name: "ThoughtInput",
            path: "Sources/ThoughtInput",
            exclude: [
                "Resources/Info.plist"
            ],
            resources: [
                .process("Resources")
            ]
        ),
        .testTarget(
            name: "ThoughtInputTests",
            dependencies: ["ThoughtInput"],
            path: "Tests/ThoughtInputTests"
        )
    ]
)
