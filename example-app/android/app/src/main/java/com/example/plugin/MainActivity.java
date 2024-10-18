package com.example.plugin;
import android.os.Bundle;
import com.getcapacitor.BridgeActivity;
import com.leadliaion.llscanner.LLScannerPlugin;

@androidx.camera.core.ExperimentalGetImage
public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
    registerPlugin(LLScannerPlugin.class);
        super.onCreate(savedInstanceState);
    }
}
