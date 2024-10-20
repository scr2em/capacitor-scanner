export interface CapacitorScannerPlugin {
  startScanning(options?: ScannerOptions): Promise<void>;
  stopScanning(): Promise<void>;
  openSettings(): Promise<void>;
  capturePhoto(): Promise<CapturePhotoResult>;
  checkPermissions(): Promise<PermissionsResult>;
  requestPermissions(): Promise<PermissionsResult>;
  addListener(event: 'barcodeScanned', listenerFunc: (result: BarcodeScannedEvent) => void): Promise<void>;
  removeAllListeners(): Promise<void>;
}

export type ScannerOptions = {
  formats?: BarcodeFormat[];
  cameraDirection?: 'BACK' | 'FRONT';
};

export type BarcodeScannedEvent = { scannedCode: string; format: string };

export type PermissionsResult = { camera: 'prompt' | 'denied' | 'granted' };

export type CapturePhotoResult = { imageBase64: string };

export enum BarcodeFormat {
  Aztec = 'AZTEC',
  Code39 = 'CODE_39',
  Code93 = 'CODE_93',
  Code128 = 'CODE_128',
  DataMatrix = 'DATA_MATRIX',
  Ean8 = 'EAN_8',
  Ean13 = 'EAN_13',
  Itf14 = 'ITF14',
  Pdf417 = 'PDF_417',
  QrCode = 'QR_CODE',
  UpcE = 'UPC_E',
}

declare global {
  interface PluginRegistry {
    QRScanner: CapacitorScannerPlugin;
  }
}
