import type { PluginListenerHandle } from '@capacitor/core';

export type ScannerOptions = {
  formats?: BarcodeFormat[];
  cameraDirection?: 'BACK' | 'FRONT';
};

export interface LLScannerPlugin {
  startScanning(options?: ScannerOptions): Promise<void>;
  stopScanning(): Promise<void>;
  openSettings(): Promise<void>;
  capturePhoto(): Promise<{ imageBase64: string }>;
  checkPermissions(): Promise<{ camera: 'prompt' | 'denied' | 'granted' }>;
  requestPermissions(): Promise<void>;
  addListener(
    event: 'barcodeScanned',
    listenerFunc: (result: { scannedCode: string; format: string }) => void,
  ): Promise<PluginListenerHandle>;
  removeAllListeners(): Promise<void>;
}

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
    QRScanner: LLScannerPlugin;
  }
}
