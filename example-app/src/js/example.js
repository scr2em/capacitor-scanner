import { LLScanner } from 'll-scanner';

const toggleScannerUI = (active) => {
  document.querySelector('main')?.classList.toggle('barcode-scanner-active', active);
};

const handleScanningError = (error) => {
  alert(JSON.stringify({ error }));
};

const handleScanningSuccess = (data) => {
  alert(JSON.stringify(data));
};
const startScanning = (options) => {
  toggleScannerUI(true);
  LLScanner.startScanning(options).then(handleScanningSuccess).catch(handleScanningError);
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

const scannedCodes = new Set();

LLScanner.addListener('barcodesScanned', (e) => {
  const { scannedCode } = e;
  if (!scannedCodes.has(scannedCode)) {
    scannedCodes.add(scannedCode);
    alert(JSON.stringify(e));
    // LLScanner.stopScanning();
  }
});
