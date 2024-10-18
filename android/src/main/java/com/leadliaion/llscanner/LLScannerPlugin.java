package com.leadliaion.llscanner;

import android.Manifest;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.provider.Settings;
import android.util.Base64;
import android.util.Log;
import android.util.Size;
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
import androidx.camera.core.Preview;
import androidx.camera.core.ImageCaptureException;
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
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

@ExperimentalGetImage
    @CapacitorPlugin(
        name = "LLScanner",
        permissions = {
                @Permission(strings = { Manifest.permission.CAMERA }, alias = "camera")
        })
public class LLScannerPlugin extends Plugin {

    private PreviewView previewView;
    private ProcessCameraProvider cameraProvider;
    private ImageCapture imageCapture;
    private BarcodeScanner scanner;
    private final Map<String, VoteStatus> scannedCodesVotes = new HashMap<>();
    private final int voteThreshold = 5;
    private final Executor executor = Executors.newSingleThreadExecutor();
    private final AtomicBoolean isScanning = new AtomicBoolean(false);
    private FrameLayout containerView;

    private static class VoteStatus {
        public int votes;
        public boolean done;

        public VoteStatus(int votes, boolean done) {
            this.votes = votes;
            this.done = done;
        }
    }

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
        echo( "startScanning");
        if (isScanning.get()) {
            call.resolve();
            return;
        }

        if (getPermissionState("camera") != PermissionState.GRANTED) {
            echo( "requestPermissionForAlias");
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
                    lensFacing = 1;
                }

                // Get formats
                JSArray formatsArray = call.getArray("formats");
                BarcodeScannerOptions.Builder optionsBuilder = new BarcodeScannerOptions.Builder();

                if (formatsArray != null && formatsArray.length() > 0) {
                    List<Integer> formatList = new ArrayList<>();
                    for (int i = 0; i < formatsArray.length(); i++) {
                        String formatStr = formatsArray.getString(i);
                        int formatInt = stringToBarcodeFormat(formatStr);
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

//                // Bring the WebView to front and make it transparent
//                WebView webView = getBridge().getWebView();
//                webView.setBackgroundColor(Color.TRANSPARENT);
//                webView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
//                webView.bringToFront();

                // Set up the camera preview
                previewView = new PreviewView(getContext());
                previewView.setLayoutParams(new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT));
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

        // Preview Use Case
        Preview preview = new Preview.Builder().build();

        // Camera Selector
        CameraSelector cameraSelector = new CameraSelector.Builder()
                .requireLensFacing(lensFacing)
                .build();

        // Image Analysis Use Case
        ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                .setTargetResolution(new Size(1280, 720))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build();

        imageAnalysis.setAnalyzer(executor, new BarcodeAnalyzer());

        // Image Capture Use Case
        imageCapture = new ImageCapture.Builder()
                .setTargetResolution(new Size(1280, 720))
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




    @ExperimentalGetImage private class BarcodeAnalyzer implements ImageAnalysis.Analyzer {
        @Override
        public void analyze(@NonNull ImageProxy imageProxy) {
            @androidx.camera.core.ExperimentalGetImage
            android.media.Image mediaImage = imageProxy.getImage();
            if (mediaImage != null) {
                InputImage image = InputImage.fromMediaImage(mediaImage, imageProxy.getImageInfo().getRotationDegrees());
                scanner.process(image)
                        .addOnSuccessListener(LLScannerPlugin.this::processBarcodes)
                        .addOnFailureListener(e -> {
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
//        echo("processBarcodes");
        for (Barcode barcode : barcodes) {
            var bytes = barcode.getDisplayValue();
            echo( "Value0 " + bytes);
            String rawValue = barcode.getRawValue();
            echo( "Raw Value " + rawValue);
            int format = barcode.getFormat();
            echo("Value2: " + format);

            VoteStatus voteStatus = scannedCodesVotes.get(rawValue);
            if (voteStatus == null) {
                voteStatus = new VoteStatus(0, false);
            }

            if (!voteStatus.done) {
                voteStatus.votes += 1;

                if (voteStatus.votes >= voteThreshold) {
                    voteStatus.done = true;

                    JSObject data = new JSObject();
                    data.put("scannedCode", rawValue);
                    data.put("format", barcodeFormatToString(format));
                    notifyListeners("barcodeScanned", data, true);
                }

                scannedCodesVotes.put(rawValue, voteStatus);
            }
        }
    }

    private int stringToBarcodeFormat(String formatStr) {
        return switch (formatStr) {
            case "AZTEC" -> Barcode.FORMAT_AZTEC;
            case "CODE_39" -> Barcode.FORMAT_CODE_39;
            case "CODE_93" -> Barcode.FORMAT_CODE_93;
            case "CODE_128" -> Barcode.FORMAT_CODE_128;
            case "DATA_MATRIX" -> Barcode.FORMAT_DATA_MATRIX;
            case "EAN_8" -> Barcode.FORMAT_EAN_8;
            case "EAN_13" -> Barcode.FORMAT_EAN_13;
            case "ITF14" -> Barcode.FORMAT_ITF;
            case "PDF_417" -> Barcode.FORMAT_PDF417;
            case "QR_CODE" -> Barcode.FORMAT_QR_CODE;
            case "UPC_E" -> Barcode.FORMAT_UPC_E;
            default -> -1;
        };
    }

    private String barcodeFormatToString(int format) {
        return switch (format) {
            case Barcode.FORMAT_AZTEC -> "AZTEC";
            case Barcode.FORMAT_CODE_39 -> "CODE_39";
            case Barcode.FORMAT_CODE_93 -> "CODE_93";
            case Barcode.FORMAT_CODE_128 -> "CODE_128";
            case Barcode.FORMAT_DATA_MATRIX -> "DATA_MATRIX";
            case Barcode.FORMAT_EAN_8 -> "EAN_8";
            case Barcode.FORMAT_EAN_13 -> "EAN_13";
            case Barcode.FORMAT_ITF -> "ITF14";
            case Barcode.FORMAT_PDF417 -> "PDF_417";
            case Barcode.FORMAT_QR_CODE -> "QR_CODE";
            case Barcode.FORMAT_UPC_E -> "UPC_E";
            default -> "UNKNOWN";
        };
    }

    @PluginMethod
    public void stopScanning(PluginCall call) {
        echo("CUSTOM_LOG_IDENTIFIER stopScanning");
        getActivity().runOnUiThread(() -> {
            if (cameraProvider != null) {
                cameraProvider.unbindAll();
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

            call.resolve();
        });
    }

    @PluginMethod
    public void capturePhoto(PluginCall call) {
        if (imageCapture == null) {
            call.reject("Camera is not set up or running");
            return;
        }

        // Create a file to save the image
        File photoFile = new File(getContext().getCacheDir(), System.currentTimeMillis() + ".jpg");

        ImageCapture.OutputFileOptions outputOptions =
                new ImageCapture.OutputFileOptions.Builder(photoFile).build();

        imageCapture.takePicture(outputOptions, ContextCompat.getMainExecutor(getContext()),
                new ImageCapture.OnImageSavedCallback() {
                    @Override
                    public void onImageSaved(@NonNull ImageCapture.OutputFileResults outputFileResults) {
                        // Read the image file and convert to base64
                        try {
                            FileInputStream fis = new FileInputStream(photoFile);
                            ByteArrayOutputStream baos = new ByteArrayOutputStream();
                            byte[] buffer = new byte[1024];
                            int len;
                            while ((len = fis.read(buffer)) != -1) {
                                baos.write(buffer, 0, len);
                            }
                            fis.close();
                            byte[] bytes = baos.toByteArray();
                            String base64 = Base64.encodeToString(bytes, Base64.NO_WRAP);
                            JSObject ret = new JSObject();
                            ret.put("imageBase64", "data:image/jpeg;base64," + base64);
                            call.resolve(ret);
                            // Delete the temporary file
                            photoFile.delete();
                        } catch (IOException e) {
                            call.reject("Failed to read image file: " + e.getMessage());
                        }
                    }

                    @Override
                    public void onError(@NonNull ImageCaptureException exception) {
                        call.reject("Photo capture failed: " + exception.getMessage());
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

    /**
     * Must run on UI thread.
     */
    private void hideWebViewBackground() {
        WebView webView = getBridge().getWebView();
        webView.setBackgroundColor(Color.TRANSPARENT);
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
    }

    /**
     * Must run on UI thread.
     */
    private void showWebViewBackground() {
        WebView webView = getBridge().getWebView();
        webView.setBackgroundColor(Color.WHITE);
//        webView.getBackground().setAlpha(255);
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
    }

}
