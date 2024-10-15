import Foundation
import Capacitor
import AVFoundation

/**
 * Please read the Capacitor iOS Plugin Development Guide
 * here: https://capacitorjs.com/docs/plugins/ios
 */
@objc(LLScannerPlugin)
public class LLScannerPlugin: CAPPlugin, CAPBridgedPlugin,AVCaptureMetadataOutputObjectsDelegate {
    public let identifier = "LLScannerPlugin"
    public let jsName = "LLScanner"
    public let pluginMethods: [CAPPluginMethod] = [
        CAPPluginMethod(name: "startScanning", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "stopScanning", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "flipCamera", returnType: CAPPluginReturnPromise),
    ]
    
    
    private var captureSession: AVCaptureSession?
    private var previewLayer: AVCaptureVideoPreviewLayer?
    
    @objc func startScanning(_ call: CAPPluginCall) {
        DispatchQueue.main.async {
            guard let rootView = self.bridge?.viewController?.view else {
                call.reject("Unable to access the root view")
                return
            }
            
            self.captureSession = AVCaptureSession()
            
            let cameraDirection: AVCaptureDevice.Position = call.getString("cameraDirection", "BACK") == "BACK" ? .back : .front
            
            guard let videoCaptureDevice = self.getCaptureDevice(position: cameraDirection) else {
                call.reject("Unable to access the camera")
                return
            }
            
            let videoInput: AVCaptureDeviceInput
            
            do {
                videoInput = try AVCaptureDeviceInput(device: videoCaptureDevice)
            } catch {
                call.reject("Unable to initialize video input")
                return
            }
            
            if (self.captureSession?.canAddInput(videoInput) ?? false) {
                self.captureSession?.addInput(videoInput)
            } else {
                call.reject("Unable to add video input to capture session")
                return
            }
            
            let metadataOutput = AVCaptureMetadataOutput()
            
            if (self.captureSession?.canAddOutput(metadataOutput) ?? false) {
                self.captureSession?.addOutput(metadataOutput)
                
                metadataOutput.setMetadataObjectsDelegate(self, queue: DispatchQueue.main)
                
                let formats = call.getArray("formats", String.self) ?? []
                let metadataObjectTypes = self.getMetadataObjectTypes(from: formats)
                metadataOutput.metadataObjectTypes = metadataObjectTypes
            } else {
                call.reject("Unable to add metadata output to capture session")
                return
            }
            
            self.previewLayer = AVCaptureVideoPreviewLayer(session: self.captureSession!)
            self.previewLayer?.frame = rootView.bounds
            self.previewLayer?.videoGravity = .resizeAspectFill
            rootView.layer.addSublayer(self.previewLayer!)
            
            self.captureSession?.startRunning()
            call.resolve()
        }
    }
    
    @objc func stopScanning(_ call: CAPPluginCall) {
        DispatchQueue.main.async {
            self.captureSession?.stopRunning()
            self.previewLayer?.removeFromSuperlayer()
            self.captureSession = nil
            self.previewLayer = nil
            call.resolve()
        }
    }
    
    public func metadataOutput(_ output: AVCaptureMetadataOutput, didOutput metadataObjects: [AVMetadataObject], from connection: AVCaptureConnection) {
        guard let metadataObject = metadataObjects.first as? AVMetadataMachineReadableCodeObject,
              let stringValue = metadataObject.stringValue else {
            return
        }
    
        self.notifyListeners("barcodesScanned", data: [
            "scannedCode": stringValue,
            "format": metadataObject.type.rawValue
        ])
        
    }
    
    private func getCaptureDevice(position: AVCaptureDevice.Position) -> AVCaptureDevice? {
        let discoverySession = AVCaptureDevice.DiscoverySession(
            deviceTypes: [.builtInWideAngleCamera],
            mediaType: .video,
            position: .unspecified
        )
        return discoverySession.devices.first { $0.position == position }
    }
    
    private func getMetadataObjectTypes(from formats: [String]) -> [AVMetadataObject.ObjectType] {
        if formats.isEmpty {
            return [.aztec,  .code39, .code93, .code128, .dataMatrix, .ean8, .ean13, .itf14, .pdf417, .qr, .upce]
        }
        
        return formats.compactMap { format in
            switch format {
            case "AZTEC": return .aztec
            case "CODE_39": return .code39
            case "CODE_93": return .code93
            case "CODE_128": return .code128
            case "DATA_MATRIX": return .dataMatrix
            case "EAN_8": return .ean8
            case "EAN_13": return .ean13
            case "ITF": return .itf14
            case "PDF_417": return .pdf417
            case "QR_CODE": return .qr
            case "UPC_E": return .upce
            default: return nil
            }
        }
    }
}
