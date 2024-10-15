import { LLScanner } from 'll-scanner';

window.testEcho = () => {
  LLScanner.startScanning();
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
