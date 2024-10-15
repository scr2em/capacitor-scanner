export type ScannerOptions = {
  formats?: BarcodeFormat[];
  cameraDirection?: 'BACK' | 'FRONT';
};

export interface LLScannerPlugin {
  startScanning(options?: ScannerOptions): Promise<void>;
  stopScanning(): Promise<void>;
  addListener(
    event: 'barcodesScanned',
    listenerFunc: (result: { scannedCode: string; format: string }) => void,
  ): Promise<any>;
  removeAllListeners(): Promise<void>;
}

export enum BarcodeFormat {
  Aztec = 'AZTEC',
  Codabar = 'CODABAR',
  Code39 = 'CODE_39',
  Code93 = 'CODE_93',
  Code128 = 'CODE_128',
  DataMatrix = 'DATA_MATRIX',
  Ean8 = 'EAN_8',
  Ean13 = 'EAN_13',
  Itf = 'ITF',
  Pdf417 = 'PDF_417',
  QrCode = 'QR_CODE',
  UpcA = 'UPC_A',
  UpcE = 'UPC_E',
}

declare global {
  interface PluginRegistry {
    QRScanner: LLScannerPlugin;
  }
}
