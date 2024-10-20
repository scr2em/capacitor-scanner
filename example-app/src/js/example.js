import { CapacitorScanner } from 'capacitor-scanner';

const toggleScannerUI = (active) => {
  document.querySelector('main')?.classList.toggle('barcode-scanner-active', active);
};

const handleScanningError = (error) => {
  alert(JSON.stringify({ error }));
};

const startScanning = (options) => {
  toggleScannerUI(true);
  CapacitorScanner.startScanning(options).catch(handleScanningError);
};

window.startBackScanningQR = () => startScanning({ cameraDirection: 'BACK', formats: ['QR_CODE'] });
window.startFrontScanning = () => startScanning({ cameraDirection: 'FRONT' });
window.startBackScanning = () => startScanning({ cameraDirection: 'BACK' });

window.stopScanning = () => {
  toggleScannerUI(false);
  CapacitorScanner.stopScanning();
};

window.openSettings = CapacitorScanner.openSettings;

window.checkPermissions = () => {
  CapacitorScanner.checkPermissions().then((v) => alert(JSON.stringify(v)));
};

window.requestPermissions = CapacitorScanner.requestPermissions;

window.capturePhoto = () => {
  CapacitorScanner.capturePhoto().then((v) => {
    document.getElementById('photo').src = v.imageBase64;
    window.stopScanning();
  });
};

CapacitorScanner.addListener('barcodeScanned', (e) => {
  alert(JSON.stringify(e));
});
