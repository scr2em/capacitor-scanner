export interface LLScannerPlugin {
  echo(options: { value: string }): Promise<{ value: string }>;
}
