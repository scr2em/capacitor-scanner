
import Foundation
import MLKitBarcodeScanning
import AVFoundation

@objc public class ScanSettings: NSObject {
    public var formats: [BarcodeFormat] = []
    public var cameraPosition: AVCaptureDevice.Position = .back
}