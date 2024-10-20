// This is a draft file that was not used in the final implementation of the plugin. It is kept here for reference.
// This file contains the initial implementation of the plugin using CameraX and ZXing for barcode scanning
// instead of the ML Kit Barcode Scanning API.

//package com.leadliaion.capacitorscanner;
//
//
//import android.Manifest;
//import android.content.Intent;
//import android.graphics.Bitmap;
//import android.graphics.BitmapFactory;
//import android.graphics.ImageFormat;
//import android.graphics.Rect;
//import android.graphics.YuvImage;
//import android.net.Uri;
//import android.provider.Settings;
//import android.util.Base64;
//import android.util.Size;
//import android.view.ViewGroup;
//import android.webkit.WebView;
//import android.view.View;
//import androidx.annotation.NonNull;
//import androidx.camera.core.CameraSelector;
//import androidx.camera.core.Camera;
//import androidx.camera.core.ExperimentalGetImage;
//import androidx.camera.core.ImageAnalysis;
//import androidx.camera.core.ImageCapture;
//import androidx.camera.core.ImageProxy;
//import androidx.camera.core.Preview;
//import androidx.camera.lifecycle.ProcessCameraProvider;
//import androidx.camera.view.PreviewView;
//import androidx.core.content.ContextCompat;
//import androidx.lifecycle.LifecycleOwner;
//
//import com.getcapacitor.JSArray;
//import com.getcapacitor.JSObject;
//import com.getcapacitor.PermissionState;
//import com.getcapacitor.Plugin;
//import com.getcapacitor.PluginCall;
//import com.getcapacitor.PluginMethod;
//import com.getcapacitor.annotation.CapacitorPlugin;
//import com.getcapacitor.annotation.Permission;
//import com.getcapacitor.annotation.PermissionCallback;
//
//
//import com.google.common.util.concurrent.ListenableFuture;
//import com.google.zxing.BarcodeFormat;
//import com.google.zxing.DecodeHintType;
//import com.google.zxing.MultiFormatReader;
//import com.google.zxing.Result;
//import com.google.zxing.BinaryBitmap;
//import com.google.zxing.LuminanceSource;
//import com.google.zxing.common.HybridBinarizer;
//import com.google.zxing.NotFoundException;
//import com.google.zxing.RGBLuminanceSource;
//import com.google.zxing.multi.GenericMultipleBarcodeReader;
//
//import java.io.ByteArrayOutputStream;
//import java.io.File;
//import java.io.FileInputStream;
//import java.io.IOException;
//import java.nio.ByteBuffer;
//import java.util.concurrent.ExecutionException;
//import java.util.concurrent.Executor;
//import java.util.concurrent.Executors;
//import java.util.*;
//        import java.util.concurrent.atomic.AtomicBoolean;
//
//import android.graphics.Bitmap;
//import android.graphics.BitmapFactory;
//import android.media.Image;
//
//import android.graphics.Color;
//import androidx.camera.core.ImageCaptureException;
//import android.widget.FrameLayout;
//import android.util.Log;
//
//
//
//@ExperimentalGetImage
//@CapacitorPlugin(
//        name = "CapacitorScanner",
//        permissions = {
//                @Permission(strings = { Manifest.permission.CAMERA }, alias = "camera")
//        }
//)
//public class CapacitorScannerPlugin extends Plugin {
//
//    private PreviewView previewView;
//    private ProcessCameraProvider cameraProvider;
//    private ImageCapture imageCapture;
//    private Map<String, VoteStatus> scannedCodesVotes = new HashMap<>();
//    private final int voteThreshold = 5;
//    private Executor executor = Executors.newSingleThreadExecutor();
//    private AtomicBoolean isScanning = new AtomicBoolean(false);
//
//    private class VoteStatus {
//        public int votes;
//        public boolean done;
//
//        public VoteStatus(int votes, boolean done) {
//            this.votes = votes;
//            this.done = done;
//        }
//    }
//
//    @Override
//    public void load() {
//        super.load();
//        // ZXing's MultiFormatReader is initialized in BarcodeAnalyzer
//    }
//
//    @PluginMethod
//    public void startScanning(PluginCall call) {
//        echo("startScanning");
//        if (isScanning.get()) {
//            call.resolve();
//            return;
//        }
//
//        if (getPermissionState("camera") != PermissionState.GRANTED) {
//            echo("requestPermissionForAlias");
//            requestPermissionForAlias("camera", call, "cameraPermsCallback");
//            return;
//        }
//
//        getActivity().runOnUiThread(() -> {
//            isScanning.set(true);
//
//            try {
//                // Get camera direction
//                String cameraDirectionStr = call.getString("cameraDirection", "BACK");
//                int lensFacing = cameraDirectionStr.equals("FRONT") ? CameraSelector.LENS_FACING_FRONT : CameraSelector.LENS_FACING_BACK;
//
//                // Get formats
//                JSArray formatsArray = call.getArray("formats");
//                // Set up barcode formats for ZXing
//                List<BarcodeFormat> barcodeFormats = new ArrayList<>();
//                if (formatsArray != null && formatsArray.length() > 0) {
//                    for (int i = 0; i < formatsArray.length(); i++) {
//                        String formatStr = formatsArray.getString(i);
//                        BarcodeFormat format = stringToZXingBarcodeFormat(formatStr);
//                        if (format != null) {
//                            barcodeFormats.add(format);
//                        }
//                    }
//                } else {
//                    // If no formats specified, default to common ones
//                    barcodeFormats.add(BarcodeFormat.AZTEC);
//                    barcodeFormats.add(BarcodeFormat.QR_CODE);
//                    barcodeFormats.add(BarcodeFormat.CODE_128);
//                    // Add other default formats as needed
//                }
//
//                // Set up the camera preview
//                previewView = new PreviewView(getContext());
//                previewView.setLayoutParams(new FrameLayout.LayoutParams(
//                        FrameLayout.LayoutParams.MATCH_PARENT,
//                        FrameLayout.LayoutParams.MATCH_PARENT));
//                previewView.setScaleType(PreviewView.ScaleType.FILL_CENTER);
//
//                // Insert the previewView into the view hierarchy
//                FrameLayout containerView = new FrameLayout(getContext());
//                containerView.setLayoutParams(new FrameLayout.LayoutParams(
//                        FrameLayout.LayoutParams.MATCH_PARENT,
//                        FrameLayout.LayoutParams.MATCH_PARENT));
//
//                // Add the previewView to the container
//                containerView.addView(previewView);
//
//                // Get the root view and add the containerView
//                ViewGroup rootView = (ViewGroup) getActivity().findViewById(android.R.id.content);
//                rootView.addView(containerView);
//
//                // Bring the WebView to front and make it transparent
//                WebView webView = getBridge().getWebView();
//                webView.setBackgroundColor(Color.TRANSPARENT);
//                webView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
//                webView.bringToFront();
//
//                // Initialize CameraX
//                ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(getContext());
//
//                cameraProviderFuture.addListener(() -> {
//                    try {
//                        cameraProvider = cameraProviderFuture.get();
//                        bindCamera(cameraProvider, previewView, lensFacing, barcodeFormats);
//                        call.resolve();
//                    } catch (ExecutionException | InterruptedException e) {
//                        e.printStackTrace();
//                        call.reject("Failed to initialize camera: " + e.getMessage());
//                    }
//                }, ContextCompat.getMainExecutor(getContext()));
//            } catch (Exception e) {
//                e.printStackTrace();
//                call.reject("Error setting up camera preview: " + e.getMessage());
//            }
//        });
//    }
//    @ExperimentalGetImage
//    private void  bindCamera(@NonNull ProcessCameraProvider cameraProvider, PreviewView previewView, int lensFacing, List<BarcodeFormat> barcodeFormats) {
//        cameraProvider.unbindAll();
//
//        // Preview Use Case
//        Preview preview = new Preview.Builder().build();
//
//        // Camera Selector
//        CameraSelector cameraSelector = new CameraSelector.Builder()
//                .requireLensFacing(lensFacing)
//                .build();
//
//        // Image Analysis Use Case
//        ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
//                .setTargetResolution(new Size(1920, 1080))
//                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
//                .build();
//
//        // Initialize BarcodeAnalyzer with desired formats
//        imageAnalysis.setAnalyzer(executor, new BarcodeAnalyzer(barcodeFormats));
//
//        // Image Capture Use Case
//        imageCapture = new ImageCapture.Builder()
//                .setTargetResolution(new Size(1280, 720))
//                .build();
//
//        // Bind to Lifecycle
//        try {
//            Camera camera = cameraProvider.bindToLifecycle((LifecycleOwner) getActivity(), cameraSelector, preview, imageAnalysis, imageCapture);
//            // Set the surface provider AFTER binding to lifecycle
//            preview.setSurfaceProvider(previewView.getSurfaceProvider());
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
//    }
//
//    @ExperimentalGetImage /**
//     * ZXing-based BarcodeAnalyzer to process camera frames and detect barcodes.
//     */
//    private class BarcodeAnalyzer implements ImageAnalysis.Analyzer {
//        private final MultiFormatReader reader;
//
//        public BarcodeAnalyzer(List<BarcodeFormat> barcodeFormats) {
//            reader = new MultiFormatReader();
//            Map<DecodeHintType, Object> hints = new EnumMap<>(DecodeHintType.class);
//            hints.put(DecodeHintType.POSSIBLE_FORMATS, barcodeFormats);
//            hints.put(DecodeHintType.CHARACTER_SET, "UTF-8");
//            hints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE); // Add this line
//            reader.setHints(hints);
//        }
//
//        @Override
//        public void analyze(@NonNull ImageProxy imageProxy) {
//            @androidx.camera.core.ExperimentalGetImage
//            Image mediaImage = imageProxy.getImage();
//            if (mediaImage != null) {
//                try {
//                    // Convert Image to Bitmap
//                    Bitmap bitmap = toBitmap(mediaImage);
//                    if (bitmap == null) {
//                        echo("Failed to convert Image to Bitmap.");
//                        return;
//                    }
//                    int width = bitmap.getWidth();
//                    int height = bitmap.getHeight();
//                    int[] pixels = new int[width * height];
//                    bitmap.getPixels(pixels, 0, width, 0, 0, width, height);
//
//                    // Convert Bitmap to LuminanceSource
//                    LuminanceSource source = new RGBLuminanceSource(width, height, pixels);
//                    BinaryBitmap binaryBitmap = new BinaryBitmap(new HybridBinarizer(source));
//
//                    // Decode the barcode
//                    Result result = reader.decodeWithState(binaryBitmap);
//                    if (result != null) {
//                        List<Result> results = new ArrayList<>();
//                        results.add(result);
//                        processBarcodes(results);
//                    }
//                } catch (NotFoundException e) {
//                    // No barcode found in this frame
//                } catch (Exception e) {
//                    e.printStackTrace();
//                } finally {
//                    imageProxy.close();
//                }
//            } else {
//                imageProxy.close();
//            }
//        }
//
//        /**
//         * Converts an Image object in YUV_420_888 format to a Bitmap using YuvImage.
//         *
//         * @param image The YUV_420_888 Image to convert.
//         * @return The resulting Bitmap or null if conversion fails.
//         */
//        private Bitmap toBitmap(Image image) {
//            try {
//                ByteBuffer yBuffer = image.getPlanes()[0].getBuffer(); // Y
//                ByteBuffer uBuffer = image.getPlanes()[1].getBuffer(); // U
//                ByteBuffer vBuffer = image.getPlanes()[2].getBuffer(); // V
//
//                int ySize = yBuffer.remaining();
//                int uSize = uBuffer.remaining();
//                int vSize = vBuffer.remaining();
//
//                byte[] nv21 = new byte[ySize + uSize + vSize];
//
//                // U and V are swapped
//                yBuffer.get(nv21, 0, ySize);
//                vBuffer.get(nv21, ySize, vSize);
//                uBuffer.get(nv21, ySize + vSize, uSize);
//
//                YuvImage yuvImage = new YuvImage(nv21, ImageFormat.NV21, image.getWidth(), image.getHeight(), null);
//                ByteArrayOutputStream out = new ByteArrayOutputStream();
//                yuvImage.compressToJpeg(new Rect(0, 0, image.getWidth(), image.getHeight()), 100, out);
//                byte[] jpegBytes = out.toByteArray();
//                return BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.length);
//            } catch (Exception e) {
//                e.printStackTrace();
//                return null;
//            }
//        }
//    }
//
//    /**
//     * Processes a list of ZXing Result objects to extract barcode information.
//     *
//     * @param barcodes A list of ZXing Result objects representing detected barcodes.
//     */
//    private void processBarcodes(List<Result> barcodes) {
//
//        for (Result barcodeResult : barcodes) {
//            String decodedText = barcodeResult.getText();
//            BarcodeFormat format = barcodeResult.getBarcodeFormat();
//
//            echo("Decoded Text: " + decodedText);
//            echo("Barcode Format: " + barcodeFormatToString(format));
//
//            echo("Decoded Text Length: " + decodedText.length());
//
//
//            if (decodedText == null || decodedText.isEmpty()) {
//                echo("Decoded text is null or empty. Skipping this barcode.");
//                continue;
//            }
//
//            VoteStatus voteStatus = scannedCodesVotes.get(decodedText);
//            if (voteStatus == null) {
//                voteStatus = new VoteStatus(0, false);
//            }
//
//            if (!voteStatus.done) {
//                voteStatus.votes += 1;
//
//                if (voteStatus.votes >= voteThreshold) {
//                    voteStatus.done = true;
//
//                    JSObject data = new JSObject();
//                    data.put("scannedCode", decodedText);
//                    data.put("format", barcodeFormatToString(format));
//                    notifyListeners("barcodeScanned", data, true);
//                }
//
//                scannedCodesVotes.put(decodedText, voteStatus);
//            }
//        }
//    }
//
//    /**
//     * Converts ZXing's BarcodeFormat to a readable string.
//     *
//     * @param format The BarcodeFormat enum from ZXing.
//     * @return A string representation of the barcode format.
//     */
//    private String barcodeFormatToString(BarcodeFormat format) {
//        switch (format) {
//            case AZTEC:
//                return "AZTEC";
//            case CODE_39:
//                return "CODE_39";
//            case CODE_93:
//                return "CODE_93";
//            case CODE_128:
//                return "CODE_128";
//            case DATA_MATRIX:
//                return "DATA_MATRIX";
//            case EAN_8:
//                return "EAN_8";
//            case EAN_13:
//                return "EAN_13";
//            case ITF:
//                return "ITF14";
//            case PDF_417:
//                return "PDF_417";
//            case QR_CODE:
//                return "QR_CODE";
//            case UPC_E:
//                return "UPC_E";
//            default:
//                return "UNKNOWN";
//        }
//    }
//
//    /**
//     * Converts a string representation of a barcode format to ZXing's BarcodeFormat enum.
//     *
//     * @param formatStr The string representation (e.g., "AZTEC").
//     * @return The corresponding BarcodeFormat enum or null if not found.
//     */
//    private BarcodeFormat stringToZXingBarcodeFormat(String formatStr) {
//        switch (formatStr) {
//            case "AZTEC":
//                return BarcodeFormat.AZTEC;
//            case "CODE_39":
//                return BarcodeFormat.CODE_39;
//            case "CODE_93":
//                return BarcodeFormat.CODE_93;
//            case "CODE_128":
//                return BarcodeFormat.CODE_128;
//            case "DATA_MATRIX":
//                return BarcodeFormat.DATA_MATRIX;
//            case "EAN_8":
//                return BarcodeFormat.EAN_8;
//            case "EAN_13":
//                return BarcodeFormat.EAN_13;
//            case "ITF14":
//                return BarcodeFormat.ITF;
//            case "PDF_417":
//                return BarcodeFormat.PDF_417;
//            case "QR_CODE":
//                return BarcodeFormat.QR_CODE;
//            case "UPC_E":
//                return BarcodeFormat.UPC_E;
//            default:
//                return null;
//        }
//    }
//
//    @PluginMethod
//    public void stopScanning(PluginCall call) {
//        System.out.println("CUSTOM_LOG_IDENTIFIER stopScanning");
//        getActivity().runOnUiThread(() -> {
//            if (cameraProvider != null) {
//                cameraProvider.unbindAll();
//            }
//            if (previewView != null) {
//                ViewGroup parentView = (ViewGroup) previewView.getParent();
//                if (parentView != null) {
//                    parentView.removeView(previewView);
//                }
//                previewView = null;
//            }
//            scannedCodesVotes.clear();
//            isScanning.set(false);
//
//            // Restore WebView background
//            WebView webView = getBridge().getWebView();
//            webView.setBackgroundColor(Color.WHITE);
//            webView.getBackground().setAlpha(255);
//
//            call.resolve();
//        });
//    }
//
//    @PluginMethod
//    public void capturePhoto(PluginCall call) {
//        if (imageCapture == null) {
//            call.reject("Camera is not set up or running");
//            return;
//        }
//
//        // Create a file to save the image
//        File photoFile = new File(getContext().getCacheDir(), System.currentTimeMillis() + ".jpg");
//
//        ImageCapture.OutputFileOptions outputOptions =
//                new ImageCapture.OutputFileOptions.Builder(photoFile).build();
//
//        imageCapture.takePicture(outputOptions, ContextCompat.getMainExecutor(getContext()),
//                new ImageCapture.OnImageSavedCallback() {
//                    @Override
//                    public void onImageSaved(@NonNull ImageCapture.OutputFileResults outputFileResults) {
//                        // Read the image file and convert to base64
//                        try {
//                            FileInputStream fis = new FileInputStream(photoFile);
//                            ByteArrayOutputStream baos = new ByteArrayOutputStream();
//                            byte[] buffer = new byte[1024];
//                            int len;
//                            while ((len = fis.read(buffer)) != -1) {
//                                baos.write(buffer, 0, len);
//                            }
//                            fis.close();
//                            byte[] bytes = baos.toByteArray();
//                            String base64 = Base64.encodeToString(bytes, Base64.NO_WRAP);
//                            JSObject ret = new JSObject();
//                            ret.put("imageBase64", "data:image/jpeg;base64," + base64);
//                            call.resolve(ret);
//                            // Delete the temporary file
//                            photoFile.delete();
//                        } catch (IOException e) {
//                            call.reject("Failed to read image file: " + e.getMessage());
//                        }
//                    }
//
//                    @Override
//                    public void onError(@NonNull ImageCaptureException exception) {
//                        call.reject("Photo capture failed: " + exception.getMessage());
//                    }
//                });
//    }
//
//    @PluginMethod
//    public void checkPermissions(PluginCall call) {
//        PermissionState cameraPermState = getPermissionState("camera");
//        String status = "prompt";
//        if (cameraPermState == PermissionState.DENIED) {
//            status = "denied";
//        } else if (cameraPermState == PermissionState.GRANTED) {
//            status = "granted";
//        }
//
//        JSObject ret = new JSObject();
//        ret.put("camera", status);
//        call.resolve(ret);
//    }
//
//    @PluginMethod
//    public void requestPermissions(PluginCall call) {
//        requestPermissionForAlias("camera", call, "cameraPermsCallback");
//    }
//
//    @PermissionCallback
//    private void cameraPermsCallback(PluginCall call) {
//        checkPermissions(call);
//    }
//
//    @PluginMethod
//    public void openSettings(PluginCall call) {
//        try {
//            Intent intent = new Intent();
//            intent.setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
//            Uri uri = Uri.fromParts("package", getContext().getPackageName(), null);
//            intent.setData(uri);
//            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
//            getContext().startActivity(intent);
//            call.resolve();
//        } catch (Exception ex) {
//            call.reject("Failed to open settings: " + ex.getMessage());
//        }
//    }
//
//
//    private void logLongMessage(String tag, String message) {
//        if (message.length() > 4000) {
//            // Split the message into chunks of 4000 characters
//            int chunkCount = message.length() / 4000;
//            for (int i = 0; i <= chunkCount; i++) {
//                int max = 4000 * (i + 1);
//                if (max >= message.length()) {
//                    Log.d(tag, message.substring(4000 * i));
//                } else {
//                    Log.d(tag, message.substring(4000 * i, max));
//                }
//            }
//        } else {
//            Log.d(tag, message);
//        }
//    }
//
//    private void echo(String value) {
//        logLongMessage("CUSTOM_LOG_IDENTIFIER", value);
//    }
//
//
//}