// swift-tools-version: 5.9
import PackageDescription

let package = Package(
    name: "CapacitorScanner",
    platforms: [.iOS(.v13)],
    products: [
        .library(
            name: "CapacitorScanner",
            targets: ["CapacitorScannerPlugin"])
    ],
    dependencies: [
        .package(url: "https://github.com/ionic-team/capacitor-swift-pm.git", branch: "main")
    ],
    targets: [
        .target(
            name: "CapacitorScannerPlugin",
            dependencies: [
                .product(name: "Capacitor", package: "capacitor-swift-pm"),
                .product(name: "Cordova", package: "capacitor-swift-pm")
            ],
            path: "ios/Sources/CapacitorScannerPlugin")
    ]
)