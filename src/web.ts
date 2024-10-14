import { WebPlugin } from '@capacitor/core';

import type { LLScannerPlugin } from './definitions';

export class LLScannerWeb extends WebPlugin implements LLScannerPlugin {
  async echo(options: { value: string }): Promise<{ value: string }> {
    console.log('ECHO', options);
    return options;
  }
}
