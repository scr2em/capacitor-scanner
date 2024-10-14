// swift-tools-version: 5.9
import PackageDescription

let package = Package(
    name: "LlScanner",
    platforms: [.iOS(.v13)],
    products: [
        .library(
            name: "LlScanner",
            targets: ["LLScannerPlugin"])
    ],
    dependencies: [
        .package(url: "https://github.com/ionic-team/capacitor-swift-pm.git", branch: "main")
    ],
    targets: [
        .target(
            name: "LLScannerPlugin",
            dependencies: [
                .product(name: "Capacitor", package: "capacitor-swift-pm"),
                .product(name: "Cordova", package: "capacitor-swift-pm")
            ],
            path: "ios/Sources/LLScannerPlugin"),
        .testTarget(
            name: "LLScannerPluginTests",
            dependencies: ["LLScannerPlugin"],
            path: "ios/Tests/LLScannerPluginTests")
    ]
)