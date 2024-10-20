
import Foundation
import AVFoundation

public typealias BarcodeFormat = AVMetadataObject.ObjectType

public class CapacitorScannerHelpers {

    public static func getAllSupportedFormats() -> [BarcodeFormat] {
        return [.aztec,  .code39, .code93, .code128, .dataMatrix, .ean8, .ean13, .itf14, .pdf417, .qr, .upce]
    }

    public static func convertStringToBarcodeScannerFormat(_ value: String) -> BarcodeFormat? {
        switch value {
        case "AZTEC":
            return BarcodeFormat.aztec
        case "CODE_39":
            return BarcodeFormat.code39
        case "CODE_93":
            return BarcodeFormat.code93
        case "CODE_128":
            return BarcodeFormat.code128
        case "DATA_MATRIX":
            return BarcodeFormat.dataMatrix
        case "EAN_8":
            return BarcodeFormat.ean8
        case "EAN_13":
            return BarcodeFormat.ean13
        case "ITF14":
            return BarcodeFormat.itf14
        case "PDF_417":
            return BarcodeFormat.pdf417
        case "QR_CODE":
            return BarcodeFormat.qr
        case "UPC_E":
            return BarcodeFormat.upce
        default:
            return nil
        }
    }

    public static func convertBarcodeScannerFormatToString(_ format: BarcodeFormat) -> String? {
        switch format {
        case BarcodeFormat.aztec:
            return "AZTEC"
        case BarcodeFormat.code39:
            return "CODE_39"
        case BarcodeFormat.code93:
            return "CODE_93"
        case BarcodeFormat.code128:
            return "CODE_128"
        case BarcodeFormat.dataMatrix:
            return "DATA_MATRIX"
        case BarcodeFormat.ean8:
            return "EAN_8"
        case BarcodeFormat.ean13:
            return "EAN_13"
        case BarcodeFormat.itf14:
            return "ITF14"
        case BarcodeFormat.pdf417:
            return "PDF_417"
        case BarcodeFormat.qr:
            return "QR_CODE"
        case BarcodeFormat.upce:
            return "UPC_E"
        default:
            return nil
        }
    }

}
