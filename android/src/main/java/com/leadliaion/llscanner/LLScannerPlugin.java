package com.leadliaion.llscanner;

import android.Manifest;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.provider.Settings;
import android.util.Base64;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.view.View;

import com.getcapacitor.JSArray;

import androidx.annotation.NonNull;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.Camera;
import androidx.camera.core.ExperimentalGetImage;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;

import com.getcapacitor.JSObject;
import com.getcapacitor.PermissionState;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

import com.getcapacitor.annotation.Permission;
import com.getcapacitor.annotation.PermissionCallback;
import android.widget.FrameLayout;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.barcode.BarcodeScanner;
import com.google.mlkit.vision.barcode.BarcodeScannerOptions;
import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.common.InputImage;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

import android.view.OrientationEventListener;

@ExperimentalGetImage
@CapacitorPlugin(
        name = "LLScanner",
        permissions = {
                @Permission(strings = { Manifest.permission.CAMERA }, alias = "camera")
        })
public class LLScannerPlugin extends Plugin {

    private PreviewView previewView;
    private ProcessCameraProvider cameraProvider;
    private BarcodeScanner scanner;
    private final Map<String, VoteStatus> scannedCodesVotes = new HashMap<>();
    private final int voteThreshold = 2;
    private final Executor executor = Executors.newSingleThreadExecutor();
    private final AtomicBoolean isScanning = new AtomicBoolean(false);
    private FrameLayout containerView;

    private OrientationEventListener orientationEventListener;
    private Preview preview;
    private ImageAnalysis imageAnalysis;
    private ImageCapture imageCapture;


    @Override
    public void load() {
        super.load();
        // Initialize ML Kit barcode scanner with all formats
        scanner = BarcodeScanning.getClient(new BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS)
                .build());
    }

    @PluginMethod
    public void startScanning(PluginCall call) {
        echo("startScanning");
        if (isScanning.get()) {
            call.resolve();
            return;
        }

        if (getPermissionState("camera") != PermissionState.GRANTED) {
            echo("requestPermissionForAlias");
            requestPermissionForAlias("camera", call, "cameraPermsCallback");
            return;
        }

        getActivity().runOnUiThread(() -> {
            isScanning.set(true);

            try {
                // Get camera direction
                String cameraDirectionStr = call.getString("cameraDirection", "BACK");
                int lensFacing;
                if (cameraDirectionStr != null) {
                    lensFacing = cameraDirectionStr.equals("FRONT") ? CameraSelector.LENS_FACING_FRONT : CameraSelector.LENS_FACING_BACK;
                } else {
                    lensFacing = CameraSelector.LENS_FACING_BACK;
                }

                // Get formats
                JSArray formatsArray = call.getArray("formats");
                BarcodeScannerOptions.Builder optionsBuilder = new BarcodeScannerOptions.Builder();

                if (formatsArray != null && formatsArray.length() > 0) {
                    List<Integer> formatList = new ArrayList<>();
                    for (int i = 0; i < formatsArray.length(); i++) {
                        String formatStr = formatsArray.getString(i);
                        int formatInt = BarcodeFormatHelper.stringToBarcodeFormat(formatStr);
                        if (formatInt != -1) {
                            formatList.add(formatInt);
                        }
                    }
                    if (!formatList.isEmpty()) {
                        int firstFormat = formatList.get(0);
                        int[] additionalFormats = new int[formatList.size() - 1];
                        for (int i = 1; i < formatList.size(); i++) {
                            additionalFormats[i - 1] = formatList.get(i);
                        }
                        optionsBuilder.setBarcodeFormats(firstFormat, additionalFormats);
                    } else {
                        optionsBuilder.setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS);
                    }
                } else {
                    optionsBuilder.setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS);
                }

                scanner = BarcodeScanning.getClient(optionsBuilder.build());

                // Set up the camera preview
                previewView = new PreviewView(getContext());
                previewView.setLayoutParams(new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT));
                // Adjust the scale type to FILL_CENTER to fill the view
                previewView.setScaleType(PreviewView.ScaleType.FILL_CENTER);

                // Create containerView and add previewView to it
                containerView = new FrameLayout(getContext());
                containerView.setLayoutParams(new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT));

                // Add the previewView to the container
                containerView.addView(previewView);

                // Get the WebView and its parent
                WebView webView = getBridge().getWebView();
                ViewGroup webViewParent = (ViewGroup) webView.getParent();

                // Insert containerView behind the WebView
                int webViewIndex = webViewParent.indexOfChild(webView);
                webViewParent.addView(containerView, webViewIndex);

                // Make WebView transparent
                hideWebViewBackground();

                // Initialize CameraX
                ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(getContext());

                cameraProviderFuture.addListener(() -> {
                    try {
                        cameraProvider = cameraProviderFuture.get();
                        bindCamera(cameraProvider, previewView, lensFacing);

                        // Initialize and enable the OrientationEventListener
                        orientationEventListener = new OrientationEventListener(getContext()) {
                            @Override
                            public void onOrientationChanged(int orientation) {
                                if (orientation == OrientationEventListener.ORIENTATION_UNKNOWN) {
                                    return;
                                }
                                int rotation = getDisplaySurfaceRotation();

                                // Update the target rotation
                                if (imageAnalysis != null) {
                                    imageAnalysis.setTargetRotation(rotation);
                                }
                                if (preview != null) {
                                    preview.setTargetRotation(rotation);
                                }
                                if (imageCapture != null) {
                                    imageCapture.setTargetRotation(rotation);
                                }
                            }
                        };

                        if (orientationEventListener.canDetectOrientation()) {
                            orientationEventListener.enable();
                        }

                        call.resolve();
                    } catch (ExecutionException | InterruptedException e) {

                        echo("Failed to initialize camera: " + e.getMessage());
                        call.reject("Failed to initialize camera: " + e.getMessage());
                    }
                }, ContextCompat.getMainExecutor(getContext()));
            } catch (Exception e) {
                echo("Error setting up camera preview: " + e.getMessage());
                call.reject("Error setting up camera preview: " + e.getMessage());
            }
        });
    }

    private void bindCamera(@NonNull ProcessCameraProvider cameraProvider, PreviewView previewView, int lensFacing) {
        cameraProvider.unbindAll();

        // Get screen dimensions
        DisplayMetrics metrics = new DisplayMetrics();
        getActivity().getWindowManager().getDefaultDisplay().getRealMetrics(metrics);
        int screenWidth = metrics.widthPixels;
        int screenHeight = metrics.heightPixels;

        // Determine if the device is in portrait or landscape mode
        int rotation = getDisplaySurfaceRotation();
        boolean isPortrait = rotation == Surface.ROTATION_0 || rotation == Surface.ROTATION_180;

        // Swap width and height if in landscape
        int targetWidth = isPortrait ? screenWidth : screenHeight;
        int targetHeight = isPortrait ? screenHeight : screenWidth;

        Size targetResolution = new Size(targetWidth, targetHeight);

        // Preview Use Case
        preview = new Preview.Builder()
                .setTargetResolution(targetResolution)
                .setTargetRotation(rotation)
                .build();

        // Camera Selector
        CameraSelector cameraSelector = new CameraSelector.Builder()
                .requireLensFacing(lensFacing)
                .build();

        // Image Analysis Use Case
        imageAnalysis = new ImageAnalysis.Builder()
                .setTargetResolution(targetResolution)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setTargetRotation(rotation)
                .build();

        imageAnalysis.setAnalyzer(executor, new BarcodeAnalyzer());

        // Image Capture Use Case
        imageCapture = new ImageCapture.Builder()
                .setTargetResolution(targetResolution)
                .setTargetRotation(rotation)
                .build();

        // Bind to Lifecycle
        try {
            Camera camera = cameraProvider.bindToLifecycle((LifecycleOwner) getActivity(), cameraSelector, preview, imageAnalysis, imageCapture);
            // Set the surface provider AFTER binding to lifecycle
            preview.setSurfaceProvider(previewView.getSurfaceProvider());
        } catch (Exception e) {
            echo("Failed to bind camera to lifecycle: " + e.getMessage());
        }
    }

    private int getDisplaySurfaceRotation() {
        return getActivity().getWindowManager().getDefaultDisplay().getRotation();
    }

    @ExperimentalGetImage private class BarcodeAnalyzer implements ImageAnalysis.Analyzer {
        @Override
        public void analyze(@NonNull ImageProxy imageProxy) {
            @androidx.camera.core.ExperimentalGetImage
            android.media.Image mediaImage = imageProxy.getImage();
            if (mediaImage != null) {
                InputImage image = InputImage.fromMediaImage(mediaImage, imageProxy.getImageInfo().getRotationDegrees());
                scanner.process(image)
                        .addOnSuccessListener(executor,LLScannerPlugin.this::processBarcodes)
                        .addOnFailureListener(executor,e -> {
                            // Handle error
                        })
                        .addOnCompleteListener(task -> {
                            imageProxy.close();
                        });
            } else {
                imageProxy.close();
            }
        }
    }
    private void processBarcodes(List<Barcode> barcodes) {
        for (Barcode barcode : barcodes) {
            String rawValue = barcode.getRawValue();
            if (rawValue == null || rawValue.isEmpty()) {
                byte[] rawBytes = barcode.getRawBytes();
                if (rawBytes != null && rawBytes.length > 0) {
                    // Convert bytes to string by mapping each byte to a character
                    rawValue = bytesToString(rawBytes);
                } else {
                    echo("Barcode has no rawValue or rawBytes, skipping");
                    continue; // Cannot proceed without rawValue
                }
            }
            echo("Processing barcode with rawValue: " + rawValue);
            int format = barcode.getFormat();

            VoteStatus voteStatus = scannedCodesVotes.get(rawValue);
            if (voteStatus == null) {
                voteStatus = new VoteStatus(0, false);
                scannedCodesVotes.put(rawValue, voteStatus);
            }

            if (!voteStatus.done) {
                voteStatus.votes += 1;

                echo("Barcode " + rawValue + " votes: " + voteStatus.votes + " done: ");

                if (voteStatus.votes >= voteThreshold) {
                    voteStatus.done = true;

                    JSObject data = new JSObject();
                    data.put("scannedCode", rawValue);
                    data.put("format", BarcodeFormatHelper.barcodeFormatToString(format));
                    notifyListeners("barcodeScanned", data, true);
                    echo(  "Barcode NO_MORE" + rawValue + " scanned with format: " + BarcodeFormatHelper.barcodeFormatToString(format));
                }
            }
        }
    }

    private String bytesToString(byte[] bytes) {
        char[] chars = new char[bytes.length];
        for (int i = 0; i < bytes.length; i++) {
            chars[i] = (char) (bytes[i] & 0xFF);
        }
        return new String(chars);
    }


    @PluginMethod
    public void stopScanning(PluginCall call) {
        echo("CUSTOM_LOG_IDENTIFIER stopScanning");
        getActivity().runOnUiThread(() -> {

            if (scanner != null) {
                scanner.close();
                scanner = null;
            }

            if (cameraProvider != null) {
                cameraProvider.unbindAll();
                cameraProvider = null;
            }

            if (previewView != null) {
                ViewGroup parentView = (ViewGroup) previewView.getParent();
                if (parentView != null) {
                    parentView.removeView(previewView);
                }
                previewView = null;
            }
            scannedCodesVotes.clear();
            isScanning.set(false);

            showWebViewBackground();


            if (orientationEventListener != null) {
                orientationEventListener.disable();
                orientationEventListener = null;
            }

            call.resolve();
        });
    }

    @PluginMethod
    public void capturePhoto(PluginCall call) {
        if (imageCapture == null) {
            call.reject("Camera is not set up or running");
            return;
        }

        // Create a ByteArrayOutputStream to capture the image in memory
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        // Create OutputFileOptions using the ByteArrayOutputStream
        ImageCapture.OutputFileOptions outputOptions =
                new ImageCapture.OutputFileOptions.Builder(outputStream).build();

        // Capture the image
        imageCapture.takePicture(outputOptions, ContextCompat.getMainExecutor(getContext()),
                new ImageCapture.OnImageSavedCallback() {
                    @Override
                    public void onImageSaved(@NonNull ImageCapture.OutputFileResults outputFileResults) {
                        try {
                            // Get the image bytes from the OutputStream
                            byte[] bytes = outputStream.toByteArray();

                            // Encode the image bytes to Base64
                            String base64 = Base64.encodeToString(bytes, Base64.NO_WRAP);

                            // Create the response object
                            JSObject ret = new JSObject();
                            ret.put("imageBase64", "data:image/jpeg;base64," + base64);

                            // Resolve the call with the Base64 image
                            call.resolve(ret);
                        } catch (Exception e) {
                            call.reject("Failed to process image: " + e.getMessage());
                        } finally {
                            // Always close the stream to free up resources
                            try {
                                outputStream.close();
                            } catch (IOException e) {
                                echo("Failed to close output stream: " + e.getMessage());
                            }
                        }
                    }

                    @Override
                    public void onError(@NonNull ImageCaptureException exception) {
                        call.reject("Photo capture failed: " + exception.getMessage());
                        echo("Photo capture failed: " + exception.getMessage());
                        try {
                            outputStream.close();
                        } catch (IOException e) {
                           echo("Failed to close output stream: " + e.getMessage());
                        }
                    }
                });
    }


    @PluginMethod
    public void checkPermissions(PluginCall call) {
        PermissionState cameraPermState = getPermissionState("camera");
        String status = "prompt";
        if (cameraPermState == PermissionState.DENIED) {
            status = "denied";
        } else if (cameraPermState == PermissionState.GRANTED) {
            status = "granted";
        }

        JSObject ret = new JSObject();
        ret.put("camera", status);

        if (status.equals("granted")) {
             startScanning(call);
        }
        call.resolve(ret);
    }

    @PluginMethod
    public void requestPermissions(PluginCall call) {
        requestPermissionForAlias("camera", call, "cameraPermsCallback");
    }

    @PermissionCallback
    private void cameraPermsCallback(PluginCall call) {
        checkPermissions(call);
    }

    @PluginMethod
    public void openSettings(PluginCall call) {
        try {
            Intent intent = new Intent();
            intent.setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            Uri uri = Uri.fromParts("package", getContext().getPackageName(), null);
            intent.setData(uri);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            getContext().startActivity(intent);
            call.resolve();
        } catch (Exception ex) {
            call.reject("Failed to open settings: " + ex.getMessage());
        }
    }

    private void logLongMessage(String message) {
        if (message.length() > 4000) {
            // Split the message into chunks of 4000 characters
            int chunkCount = message.length() / 4000;
            for (int i = 0; i <= chunkCount; i++) {
                int max = 4000 * (i + 1);
                if (max >= message.length()) {
                    Log.d("SCANNER_LOG_IDENTIFIER", message.substring(4000 * i));
                } else {
                    Log.d("SCANNER_LOG_IDENTIFIER", message.substring(4000 * i, max));
                }
            }
        } else {
            Log.d("SCANNER_LOG_IDENTIFIER", message);
        }
    }

    private void echo(String value) {
        logLongMessage(value);
    }

    private void hideWebViewBackground() {
        WebView webView = getBridge().getWebView();
        webView.setBackgroundColor(Color.TRANSPARENT);
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
    }

    private void showWebViewBackground() {
        WebView webView = getBridge().getWebView();
        webView.setBackgroundColor(Color.WHITE);
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
    }

}
