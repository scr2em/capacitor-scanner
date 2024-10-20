package com.leadliaion.capacitorscanner;

import com.google.mlkit.vision.barcode.common.Barcode;

public class BarcodeFormatHelper {

    public static int stringToBarcodeFormat(String formatStr) {
        return switch (formatStr) {
            case "AZTEC" -> Barcode.FORMAT_AZTEC;
            case "CODE_39" -> Barcode.FORMAT_CODE_39;
            case "CODE_93" -> Barcode.FORMAT_CODE_93;
            case "CODE_128" -> Barcode.FORMAT_CODE_128;
            case "DATA_MATRIX" -> Barcode.FORMAT_DATA_MATRIX;
            case "EAN_8" -> Barcode.FORMAT_EAN_8;
            case "EAN_13" -> Barcode.FORMAT_EAN_13;
            case "ITF14" -> Barcode.FORMAT_ITF;
            case "PDF_417" -> Barcode.FORMAT_PDF417;
            case "QR_CODE" -> Barcode.FORMAT_QR_CODE;
            case "UPC_E" -> Barcode.FORMAT_UPC_E;
            default -> -1;
        };
    }

    public static String barcodeFormatToString(int format) {
        return switch (format) {
            case Barcode.FORMAT_AZTEC -> "AZTEC";
            case Barcode.FORMAT_CODE_39 -> "CODE_39";
            case Barcode.FORMAT_CODE_93 -> "CODE_93";
            case Barcode.FORMAT_CODE_128 -> "CODE_128";
            case Barcode.FORMAT_DATA_MATRIX -> "DATA_MATRIX";
            case Barcode.FORMAT_EAN_8 -> "EAN_8";
            case Barcode.FORMAT_EAN_13 -> "EAN_13";
            case Barcode.FORMAT_ITF -> "ITF14";
            case Barcode.FORMAT_PDF417 -> "PDF_417";
            case Barcode.FORMAT_QR_CODE -> "QR_CODE";
            case Barcode.FORMAT_UPC_E -> "UPC_E";
            default -> "UNKNOWN";
        };
    }
}
