import { LLScanner } from 'll-scanner';

window.startFrontScanning = () => {
  LLScanner.startScanning({ cameraDirection: 'FRONT' });
};

window.startBackScanning = () => {
  LLScanner.startScanning({ cameraDirection: 'BACK' });
};
window.stopScanning = () => {
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
