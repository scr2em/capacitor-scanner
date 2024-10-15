import Foundation
import Capacitor
import AVFoundation

/**
 * Please read the Capacitor iOS Plugin Development Guide
 * here: https://capacitorjs.com/docs/plugins/ios
 */
@objc(LLScannerPlugin)
public class LLScannerPlugin: CAPPlugin, CAPBridgedPlugin,AVCaptureMetadataOutputObjectsDelegate, AVCapturePhotoCaptureDelegate {
    public let identifier = "LLScannerPlugin"
    public let jsName = "LLScanner"
    public let pluginMethods: [CAPPluginMethod] = [
        CAPPluginMethod(name: "startScanning", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "stopScanning", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "checkPermissions", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "requestPermissions", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "openSettings", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "capturePhoto", returnType: CAPPluginReturnPromise)
    ]
    
    
    private var captureSession: AVCaptureSession?
    private var cameraView: UIView?
    private var previewLayer: AVCaptureVideoPreviewLayer?    
    
    // for capturing images
    private var photoOutput: AVCapturePhotoOutput?
    private var capturePhotoCall: CAPPluginCall?
    
    @objc func capturePhoto(_ call: CAPPluginCall) {
        guard let photoOutput = self.photoOutput, captureSession?.isRunning == true else {
            call.reject("Camera is not set up or running")
            return
        }
        
        let photoSettings = AVCapturePhotoSettings()
        self.capturePhotoCall = call
        photoOutput.capturePhoto(with: photoSettings, delegate: self)
    }
    
    public func photoOutput(_ output: AVCapturePhotoOutput, didFinishProcessingPhoto photo: AVCapturePhoto, error: Error?) {
        
        if let captureError = error {
            self.capturePhotoCall?.reject("Photo capture failed: \(captureError.localizedDescription)")
            return
        }
        
        guard let imageData = photo.fileDataRepresentation() else {
            self.capturePhotoCall?.reject("Unable to get image data")
            return
        }
        
        let base64String = imageData.base64EncodedString()
        self.capturePhotoCall?.resolve(["imageBase64": "data:image/jpeg;base64, \(base64String)" ])
    }
    
    
    @objc func startScanning(_ call: CAPPluginCall) {
        self.stopScanning(call)
        
        DispatchQueue.main.async {
            
            let captureSession = AVCaptureSession()
            captureSession.sessionPreset = AVCaptureSession.Preset.hd1280x720
            
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
            
            if (captureSession.canAddInput(videoInput)) {
                captureSession.addInput(videoInput)
            } else {
                call.reject("Unable to add video input to capture session")
                return
            }
            
            let metadataOutput = AVCaptureMetadataOutput()
            
            if (captureSession.canAddOutput(metadataOutput)) {
                captureSession.addOutput(metadataOutput)
                
                metadataOutput.setMetadataObjectsDelegate(self, queue: DispatchQueue.main)
                
                let formats = call.getArray("formats", String.self) ?? []
                let metadataObjectTypes = self.getMetadataObjectTypes(from: formats)
                metadataOutput.metadataObjectTypes = metadataObjectTypes
            } else {
                call.reject("Unable to add metadata output to capture session")
                return
            }
            
            // Add photo output
            let photoOutput = AVCapturePhotoOutput()
            self.photoOutput = photoOutput
            if captureSession.canAddOutput(photoOutput)  {
                captureSession.addOutput(photoOutput)
            } else {
                call.reject("Unable to add photo output to capture session")
                return
            }
            
            
            self.hideWebViewBackground()
            
            if let webView = self.webView, let superView = webView.superview {
                
                let cameraView = UIView(frame: superView.bounds)
                let previewLayer = AVCaptureVideoPreviewLayer(session: captureSession)
                previewLayer.videoGravity = .resizeAspectFill
                previewLayer.frame = superView.bounds
                cameraView.layer.addSublayer(previewLayer)
                
                webView.superview?.insertSubview(cameraView, belowSubview: webView)
                
                self.captureSession = captureSession
                self.cameraView = cameraView
                self.previewLayer = previewLayer
                
                
                
                DispatchQueue.global(qos: .background).async {
                    captureSession.startRunning()
                    call.resolve()
                }
                
                
                
            }else{
                call.reject("unknown")
            }
            
        }
    }
    
    @objc func stopScanning(_ call: CAPPluginCall) {
        
        DispatchQueue.main.async {
            if let captureSession = self.captureSession {
                captureSession.stopRunning()
            }
            
            self.previewLayer?.removeFromSuperlayer()
            self.cameraView?.removeFromSuperview()
            
            self.captureSession = nil
            self.cameraView = nil
            self.previewLayer = nil
            
            self.showWebViewBackground()
            
            call.resolve()
        }
        
    }
    
    public func metadataOutput(_ output: AVCaptureMetadataOutput, didOutput metadataObjects: [AVMetadataObject], from connection: AVCaptureConnection) {
        guard let metadataObject = metadataObjects.first as? AVMetadataMachineReadableCodeObject,
              let stringValue = metadataObject.stringValue else {
            return
        }
        
        
        self.notifyListeners("barcodeScanned", data: [
            "scannedCode": stringValue,
            "format": LLScannerHelpers.convertBarcodeScannerFormatToString(metadataObject.type)
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
            return LLScannerHelpers.getAllSupportedFormats()
        }
        
        return formats.compactMap { format in
            return LLScannerHelpers.convertStringToBarcodeScannerFormat(format)
            
        }
    }
    
    /**
     * Must run on UI thread.
     */
    private func hideWebViewBackground() {
        guard let webView = self.webView else {
            return
        }
        webView.isOpaque = false
        webView.backgroundColor = UIColor.clear
        webView.scrollView.backgroundColor = UIColor.clear
    }
    
    /**
     * Must run on UI thread.
     */
    private func showWebViewBackground() {
        guard let webView = self.webView else {
            return
        }
        webView.isOpaque = true
        webView.backgroundColor = UIColor.white
        webView.scrollView.backgroundColor = UIColor.white
    }
    
    
    @objc override public func checkPermissions(_ call: CAPPluginCall) {
        let status = AVCaptureDevice.authorizationStatus(for: .video)
        
        var stringStatus : String = "prompt"
        
        if status == .denied || status == .restricted {
            stringStatus = "denied"
        }
        
        if status == .authorized {
            stringStatus = "granted"
        }
        
        call.resolve(["camera":stringStatus ])
    }
    
    
    @objc override public func requestPermissions(_ call: CAPPluginCall) {
        AVCaptureDevice.requestAccess(for: .video) { _ in
            self.checkPermissions(call)
        }
    }
    
    
    @objc func openSettings(_ call: CAPPluginCall) {
        let url = URL(string: UIApplication.openSettingsURLString)
        DispatchQueue.main.async {
            if let url = url, UIApplication.shared.canOpenURL(url) {
                UIApplication.shared.open(url)
                call.resolve()
            }else{
                call.reject("unknown")
            }
            
        }
    }
    
    
}
