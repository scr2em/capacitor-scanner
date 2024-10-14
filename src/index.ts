import { registerPlugin } from '@capacitor/core';

import type { LLScannerPlugin } from './definitions';

const LLScanner = registerPlugin<LLScannerPlugin>('LLScanner', {
  web: () => import('./web').then((m) => new m.LLScannerWeb()),
});

export * from './definitions';
export { LLScanner };
