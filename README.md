# Capacitor Scanner

scan codes

## Install

```bash
npm install capacitor-scanner
npx cap sync
```

## API

<docgen-index>

* [`startScanning(...)`](#startscanning)
* [`stopScanning()`](#stopscanning)
* [`openSettings()`](#opensettings)
* [`capturePhoto()`](#capturephoto)
* [`checkPermissions()`](#checkpermissions)
* [`requestPermissions()`](#requestpermissions)
* [`addListener('barcodeScanned', ...)`](#addlistenerbarcodescanned-)
* [`removeAllListeners()`](#removealllisteners)
* [Type Aliases](#type-aliases)
* [Enums](#enums)

</docgen-index>

<docgen-api>
<!--Update the source file JSDoc comments and rerun docgen to update the docs below-->

### startScanning(...)

```typescript
startScanning(options?: ScannerOptions | undefined) => Promise<void>
```

| Param         | Type                                                      |
| ------------- | --------------------------------------------------------- |
| **`options`** | <code><a href="#scanneroptions">ScannerOptions</a></code> |

--------------------


### stopScanning()

```typescript
stopScanning() => Promise<void>
```

--------------------


### openSettings()

```typescript
openSettings() => Promise<void>
```

--------------------


### capturePhoto()

```typescript
capturePhoto() => Promise<CapturePhotoResult>
```

**Returns:** <code>Promise&lt;<a href="#capturephotoresult">CapturePhotoResult</a>&gt;</code>

--------------------


### checkPermissions()

```typescript
checkPermissions() => Promise<PermissionsResult>
```

**Returns:** <code>Promise&lt;<a href="#permissionsresult">PermissionsResult</a>&gt;</code>

--------------------


### requestPermissions()

```typescript
requestPermissions() => Promise<PermissionsResult>
```

**Returns:** <code>Promise&lt;<a href="#permissionsresult">PermissionsResult</a>&gt;</code>

--------------------


### addListener('barcodeScanned', ...)

```typescript
addListener(event: 'barcodeScanned', listenerFunc: (result: BarcodeScannedEvent) => void) => Promise<void>
```

| Param              | Type                                                                                     |
| ------------------ | ---------------------------------------------------------------------------------------- |
| **`event`**        | <code>'barcodeScanned'</code>                                                            |
| **`listenerFunc`** | <code>(result: <a href="#barcodescannedevent">BarcodeScannedEvent</a>) =&gt; void</code> |

--------------------


### removeAllListeners()

```typescript
removeAllListeners() => Promise<void>
```

--------------------


### Type Aliases


#### ScannerOptions

<code>{ formats?: BarcodeFormat[]; cameraDirection?: 'BACK' | 'FRONT'; }</code>


#### CapturePhotoResult

<code>{ imageBase64: string }</code>


#### PermissionsResult

<code>{ camera: 'prompt' | 'denied' | 'granted' }</code>


#### BarcodeScannedEvent

<code>{ scannedCode: string; format: string }</code>


### Enums


#### BarcodeFormat

| Members          | Value                      |
| ---------------- | -------------------------- |
| **`Aztec`**      | <code>'AZTEC'</code>       |
| **`Code39`**     | <code>'CODE_39'</code>     |
| **`Code93`**     | <code>'CODE_93'</code>     |
| **`Code128`**    | <code>'CODE_128'</code>    |
| **`DataMatrix`** | <code>'DATA_MATRIX'</code> |
| **`Ean8`**       | <code>'EAN_8'</code>       |
| **`Ean13`**      | <code>'EAN_13'</code>      |
| **`Itf14`**      | <code>'ITF14'</code>       |
| **`Pdf417`**     | <code>'PDF_417'</code>     |
| **`QrCode`**     | <code>'QR_CODE'</code>     |
| **`UpcE`**       | <code>'UPC_E'</code>       |

</docgen-api>
