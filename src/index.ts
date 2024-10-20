import { registerPlugin } from '@capacitor/core';

import type { CapacitorScannerPlugin } from './definitions';

export const CapacitorScanner = registerPlugin<CapacitorScannerPlugin>('CapacitorScanner');

export * from './definitions';
