import { LLScanner } from 'll-scanner';

window.startBackScanningQR = () => {
  document.querySelector('main')?.classList.add('barcode-scanner-active');

  LLScanner.startScanning({ cameraDirection: 'BACK', formats: ['QR_CODE'] });
};

window.startFrontScanning = () => {
  document.querySelector('main')?.classList.add('barcode-scanner-active');

  LLScanner.startScanning({ cameraDirection: 'FRONT' });
};

window.startBackScanning = () => {
  document.querySelector('main')?.classList.add('barcode-scanner-active');

  LLScanner.startScanning({ cameraDirection: 'BACK' });
};
window.stopScanning = () => {
  document.querySelector('main')?.classList.remove('barcode-scanner-active');

  LLScanner.stopScanning();
};

const set = new Set();

LLScanner.addListener('barcodesScanned', (e) => {
  const scannedCode = e.scannedCode;
  if (set.has(scannedCode)) {
    return;
  }
  set.add(scannedCode);
  alert(JSON.stringify(e));
  // LLScanner.stopScanning();
});
