import { LLScanner } from 'll-scanner';

const toggleScannerUI = (active) => {
  document.querySelector('main')?.classList.toggle('barcode-scanner-active', active);
};

const handleScanningError = (error) => {
  alert(JSON.stringify({ error }));
};

const startScanning = (options) => {
  toggleScannerUI(true);
  LLScanner.startScanning(options).catch(handleScanningError);
};

window.startBackScanningQR = () => startScanning({ cameraDirection: 'BACK', formats: ['QR_CODE'] });
window.startFrontScanning = () => startScanning({ cameraDirection: 'FRONT' });
window.startBackScanning = () => startScanning({ cameraDirection: 'BACK' });

window.stopScanning = () => {
  toggleScannerUI(false);
  LLScanner.stopScanning();
};

window.openSettings = LLScanner.openSettings;

window.checkPermissions = () => {
  LLScanner.checkPermissions().then((v) => alert(JSON.stringify(v)));
};

window.requestPermissions = LLScanner.requestPermissions;

window.capturePhoto = () => {
  LLScanner.capturePhoto().then((v) => {
    document.getElementById('photo').src = v.imageBase64;
    window.stopScanning();
  });
};

LLScanner.addListener('barcodeScanned', (e) => {
  alert(JSON.stringify(e));
});
