import AVFoundation
import Capacitor
import Foundation

/**
 * Please read the Capacitor iOS Plugin Development Guide
 * here: https://capacitorjs.com/docs/plugins/ios
 */
struct VoteStatus {
    var votes: Int
    var done: Bool
}

@objc(CapacitorScannerPlugin)
public class CapacitorScannerPlugin: CAPPlugin, CAPBridgedPlugin, AVCaptureMetadataOutputObjectsDelegate, AVCapturePhotoCaptureDelegate {
    public let identifier = "CapacitorScannerPlugin"
    public let jsName = "CapacitorScanner"
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

    private let voteThreshold = 5
    private var scannedCodesVotes: [String: VoteStatus] = [:]

    // for capturing images
    private var photoOutput: AVCapturePhotoOutput?
    private var capturePhotoCall: CAPPluginCall?

    private var orientationObserver: NSObjectProtocol?

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
        self.capturePhotoCall?.resolve(["imageBase64": "data:image/jpeg;base64, \(base64String)"])
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

            if captureSession.canAddInput(videoInput) {
                captureSession.addInput(videoInput)
            } else {
                call.reject("Unable to add video input to capture session")
                return
            }

            let metadataOutput = AVCaptureMetadataOutput()

            if captureSession.canAddOutput(metadataOutput) {
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
            if captureSession.canAddOutput(photoOutput) {
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

                // Add orientation change observer
                self.addOrientationChangeObserver()

                // Initial orientation setup
                self.updatePreviewOrientation()

                DispatchQueue.global(qos: .background).async {
                    captureSession.startRunning()
                    call.resolve()
                }

            } else {
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
            self.scannedCodesVotes = [:]
            self.showWebViewBackground()

            self.removeOrientationChangeObserver()

            call.resolve()
        }
    }

    private func addOrientationChangeObserver() {
        self.orientationObserver = NotificationCenter.default.addObserver(
            forName: UIDevice.orientationDidChangeNotification,
            object: nil,
            queue: .main
        ) { [weak self] _ in
            self?.updatePreviewOrientation()
        }
    }

    private func removeOrientationChangeObserver() {
        if let observer = self.orientationObserver {
            NotificationCenter.default.removeObserver(observer)
            self.orientationObserver = nil
        }
    }

    private func updatePreviewOrientation() {
        guard let previewLayer = self.previewLayer,
              let connection = previewLayer.connection,
              let cameraView = self.cameraView
        else {
            return
        }

        let deviceOrientation = UIDevice.current.orientation

        if deviceOrientation.isFlat == true || deviceOrientation == .portraitUpsideDown {
            return
        }

        let newOrientation: AVCaptureVideoOrientation

        switch deviceOrientation {
        case .landscapeLeft:
            newOrientation = .landscapeRight
        case .landscapeRight:
            newOrientation = .landscapeLeft
        default:
            newOrientation = .portrait
        }

        connection.videoOrientation = newOrientation

        // Update camera view and preview layer frames
        let screenBounds = UIScreen.main.bounds
        let screenWidth = screenBounds.width
        let screenHeight = screenBounds.height

        // Determine the correct dimensions based on orientation
        let width: CGFloat
        let height: CGFloat
        if newOrientation == .portrait {
            width = min(screenWidth, screenHeight)
            height = max(screenWidth, screenHeight)
        } else {
            width = max(screenWidth, screenHeight)
            height = min(screenWidth, screenHeight)
        }

        // Update frames
        cameraView.frame = CGRect(x: 0, y: 0, width: width, height: height)
        previewLayer.frame = cameraView.bounds
    }

    public func metadataOutput(_ output: AVCaptureMetadataOutput, didOutput metadataObjects: [AVMetadataObject], from connection: AVCaptureConnection) {
        guard let metadataObject = metadataObjects.first as? AVMetadataMachineReadableCodeObject,
              let stringValue = metadataObject.stringValue
        else {
            return
        }

        /*
         this is a voting system to
         1. avoid scanning the same code
         2. avoid scanning any code that doesn't appear for at least in 10 frames of the video stream to reduce the number of false positives
         */

        var voteStatus = self.scannedCodesVotes[stringValue] ?? VoteStatus(votes: 0, done: false)

        if !voteStatus.done {
            voteStatus.votes += 1

            if voteStatus.votes >= self.voteThreshold {
                voteStatus.done = true

                self.notifyListeners("barcodeScanned", data: [
                    "scannedCode": stringValue,
                    "format": CapacitorScannerHelpers.convertBarcodeScannerFormatToString(metadataObject.type)
                ])
            }
        }

        self.scannedCodesVotes[stringValue] = voteStatus
    }

    private func getCaptureDevice(position: AVCaptureDevice.Position) -> AVCaptureDevice? {
        let discoverySession = AVCaptureDevice.DiscoverySession(
            deviceTypes: [.builtInWideAngleCamera],
            mediaType: .video,
            position: position
        )
        return discoverySession.devices.first
    }

    private func getMetadataObjectTypes(from formats: [String]) -> [BarcodeFormat] {
        if formats.isEmpty {
            return CapacitorScannerHelpers.getAllSupportedFormats()
        }

        return formats.compactMap { format in
            CapacitorScannerHelpers.convertStringToBarcodeScannerFormat(format)
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

        var stringStatus = "prompt"

        if status == .denied || status == .restricted {
            stringStatus = "denied"
        }

        if status == .authorized {
            stringStatus = "granted"
        }

        call.resolve(["camera": stringStatus])
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
            } else {
                call.reject("unknown")
            }
        }
    }
}
