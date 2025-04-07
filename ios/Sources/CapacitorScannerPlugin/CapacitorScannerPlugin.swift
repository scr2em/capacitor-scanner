import AVFoundation
import Capacitor
import Foundation
import Vision // For barcode detection

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
		CAPPluginMethod(name: "capturePhoto", returnType: CAPPluginReturnPromise),
		CAPPluginMethod(name: "flipCamera", returnType: CAPPluginReturnPromise),
		CAPPluginMethod(name: "toggleFlash", returnType: CAPPluginReturnPromise),
	]

	private var captureSession: AVCaptureSession?
	private var cameraView: UIView?
	private var previewLayer: AVCaptureVideoPreviewLayer?

	private let voteThreshold = 3
	private var scannedCodesVotes: [String: VoteStatus] = [:]

	// for capturing images
	private var photoOutput: AVCapturePhotoOutput?
	private var capturePhotoCall: CAPPluginCall?

	private var orientationObserver: NSObjectProtocol?
	private var videoDataOutput: AVCaptureVideoDataOutput? // For getting video frames

	// This is how we create a Vision request for detecting barcodes
	private lazy var barcodeDetectionRequest: VNDetectBarcodesRequest = {
		let request = VNDetectBarcodesRequest { [weak self] request, error in
			// This closure is called when Vision finds barcodes
			self?.processClassification(request, error: error)
		}
		return request
	}()

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
			let cameraDirection: AVCaptureDevice.Position = call.getString("cameraDirection", "BACK") == "BACK" ? .back : .front
			guard let videoCaptureDevice = self.getCaptureDevice(position: cameraDirection) else {
				print("No camera available")
				return
			}

			// Check if the selected camera supports the desired preset
			let desiredPreset = AVCaptureSession.Preset.hd1920x1080
			if videoCaptureDevice.supportsSessionPreset(desiredPreset) && captureSession.canSetSessionPreset(desiredPreset) {
				captureSession.sessionPreset = desiredPreset
			} else if captureSession.canSetSessionPreset(.hd1280x720) {
				captureSession.sessionPreset = .hd1280x720
			} else {
				captureSession.sessionPreset = .medium // Fallback to a lower resolution
			}

			guard let videoInput = try? AVCaptureDeviceInput(device: videoCaptureDevice) else {
				print("Could not create video input")
				return
			}
			captureSession.addInput(videoInput)

			// 2. Setup video output for Vision
			let videoOutput = AVCaptureVideoDataOutput()
			videoOutput.setSampleBufferDelegate(self, queue: DispatchQueue.global(qos: .userInitiated))
			captureSession.addOutput(videoOutput)
			self.videoDataOutput = videoOutput

			let formats = call.getArray("formats", String.self) ?? []

			self.barcodeDetectionRequest.symbologies = self.getSupportedFormats(from: formats)

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
			self.videoDataOutput?.setSampleBufferDelegate(nil, queue: nil)

			self.captureSession = nil
			self.cameraView = nil
			self.previewLayer = nil
			self.videoDataOutput = nil
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

	private func getSupportedFormats(from formats: [String]) -> [BarcodeFormat] {
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

	// Keep the enhanced device selection method
	private func getCaptureDevice(position: AVCaptureDevice.Position) -> AVCaptureDevice? {
		let discoverySession = AVCaptureDevice.DiscoverySession(
			deviceTypes: [.builtInDualCamera, .builtInTripleCamera, .builtInWideAngleCamera],
			mediaType: .video,
			position: position
		)
		// Prioritize higher quality cameras first
		if let device = discoverySession.devices.first(where: { $0.deviceType == .builtInTripleCamera }) ??
			discoverySession.devices.first(where: { $0.deviceType == .builtInDualCamera }) ??
			discoverySession.devices.first(where: { $0.deviceType == .builtInWideAngleCamera })
		{
			return device
		}

		return nil
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

	private func processClassification(_ request: VNRequest, error: Error?) {
		// Handle any errors
		if let error = error {
			print("Vision error: \(error)")
			return
		}

		// Get the barcode observations
		guard let observations = request.results as? [VNBarcodeObservation] else {
			return
		}

		// First find the barcode with highest confidence
		let highestConfidenceBarcode = observations
			.filter { $0.payloadStringValue != nil }
			.max(by: { $0.confidence < $1.confidence })

		// Then process only that barcode if found
		if let bestObservation = highestConfidenceBarcode,
		   let payload = bestObservation.payloadStringValue
		{
			/*
			 this is a voting system to
			 1. avoid scanning the same code
			 2. avoid scanning any code that doesn't appear for at least in 10 frames
			 of the video stream to reduce the number of false positives
			 */

			var voteStatus = self.scannedCodesVotes[payload] ?? VoteStatus(votes: 0, done: false)

			if !voteStatus.done {
				voteStatus.votes += 1

				if voteStatus.votes >= self.voteThreshold {
					voteStatus.done = true

					self.notifyListeners("barcodeScanned", data: [
						"scannedCode": payload,
						"format": CapacitorScannerHelpers.convertBarcodeScannerFormatToString(bestObservation.symbology),
					])

					// Reset votes after successful scan
					self.scannedCodesVotes = [:]
				}
			}

			self.scannedCodesVotes[payload] = voteStatus
		}
	}

	@objc func flipCamera(_ call: CAPPluginCall) {
		guard let session = self.captureSession else {
			call.reject("Scanning session not active.")
			return
		}

		DispatchQueue.global(qos: .userInitiated).async {
			session.beginConfiguration()
			// Defer ensures commitConfiguration is called even if errors occur
			defer { session.commitConfiguration() }

			guard let currentInput = session.inputs.first as? AVCaptureDeviceInput else {
				call.reject("Could not get current camera input.")
				return
			}

			let currentPosition = currentInput.device.position
			let preferredPosition: AVCaptureDevice.Position = (currentPosition == .back) ? .front : .back

			guard let newDevice = self.getCaptureDevice(position: preferredPosition) else {
				call.reject("Could not find camera for position: \(preferredPosition).")
				return
			}

			guard let newInput = try? AVCaptureDeviceInput(device: newDevice) else {
				call.reject("Could not create input for new camera device.")
				return
			}

			// Remove the existing input
			session.removeInput(currentInput)

			// Add the new input
			if session.canAddInput(newInput) {
				session.addInput(newInput)

				// Session automatically handles connecting outputs (like photoOutput and videoDataOutput)
				// to the new input. No explicit reconnection needed here.

				// Ensure preview layer orientation is updated for the new camera
				DispatchQueue.main.async {
					self.updatePreviewOrientation()
					call.resolve() // Resolve the call on the main thread after UI update
				}

			} else {
				// Important: If adding the new input fails, try to add the old one back!
				print("Failed to add new input, attempting to restore previous input.")
				if session.canAddInput(currentInput) {
					session.addInput(currentInput)
				}
				call.reject("Could not add new camera input to session.")
			}
		}
	}

	@objc func toggleFlash(_ call: CAPPluginCall) {
		guard let session = self.captureSession, let currentInput = session.inputs.first as? AVCaptureDeviceInput else {
			call.reject("Scanning session not active or camera input not found.")
			return
		}

		let device = currentInput.device

		guard device.hasTorch, device.isTorchAvailable else {
			call.resolve(["enabled": false]) // Report flash as disabled if not available/supported
			return
		}

		do {
			try device.lockForConfiguration()
			let currentMode = device.torchMode
			let newMode: AVCaptureDevice.TorchMode = (currentMode == .on) ? .off : .on
			if device.isTorchModeSupported(newMode) {
				device.torchMode = newMode
			}
			device.unlockForConfiguration()
			call.resolve(["enabled": device.torchMode == .on])
		} catch {
			call.reject("Could not lock device for flash configuration: \(error.localizedDescription)")
		}
	}
}

extension CapacitorScannerPlugin: AVCaptureVideoDataOutputSampleBufferDelegate {
	public func captureOutput(_ output: AVCaptureOutput,
	                          didOutput sampleBuffer: CMSampleBuffer,
	                          from connection: AVCaptureConnection)
	{
		// This is called for every frame from the camera

		// 1. Get the pixel buffer from the frame
		guard let pixelBuffer = CMSampleBufferGetImageBuffer(sampleBuffer) else {
			return
		}

		// 2. Create a Vision image handler
		let imageRequestHandler = VNImageRequestHandler(cvPixelBuffer: pixelBuffer,
		                                                orientation: .up,
		                                                options: [:])

		// 3. Perform the barcode detection
		do {
			try imageRequestHandler.perform([self.barcodeDetectionRequest])
		} catch {
			print("Failed to perform Vision request: \(error)")
		}
	}
}
