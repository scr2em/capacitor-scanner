
import Foundation
import AVFoundation

@objc public class ScanSettings: NSObject {
    public var formats: [AVMetadataObject.ObjectType] = []
    public var cameraPosition: AVCaptureDevice.Position = .back
}
