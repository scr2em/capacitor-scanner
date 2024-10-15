import { registerPlugin } from '@capacitor/core';

import type { LLScannerPlugin } from './definitions';

export const LLScanner = registerPlugin<LLScannerPlugin>('LLScanner', );

export * from './definitions';
