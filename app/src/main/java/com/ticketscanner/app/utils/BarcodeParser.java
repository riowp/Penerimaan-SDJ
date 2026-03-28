package com.ticketscanner.app.utils;

/**
 * Parses a raw barcode string into ticket number + tonnage.
 *
 * Expected barcode format (configurable):
 *   "<TICKET_NO>|<TONNAGE>"   e.g.  "TKT-00123|45.5"
 *
 * If the barcode does not contain a '|' separator the entire string is treated
 * as the ticket number and tonnage defaults to 0.
 */
public class BarcodeParser {

    private static final String SEPARATOR = "|";

    public static class ParseResult {
        public final String ticketNumber;
        public final double tonnage;
        public final boolean isValid;
        public final String errorMessage;

        public ParseResult(String ticketNumber, double tonnage) {
            this.ticketNumber = ticketNumber;
            this.tonnage = tonnage;
            this.isValid = true;
            this.errorMessage = null;
        }

        public ParseResult(String errorMessage) {
            this.ticketNumber = null;
            this.tonnage = 0;
            this.isValid = false;
            this.errorMessage = errorMessage;
        }
    }

    public static ParseResult parse(String rawBarcode) {
        if (rawBarcode == null || rawBarcode.trim().isEmpty()) {
            return new ParseResult("Barcode kosong");
        }

        rawBarcode = rawBarcode.trim();

        if (rawBarcode.contains(SEPARATOR)) {
            String[] parts = rawBarcode.split("\\" + SEPARATOR, 2);
            String ticketNo = parts[0].trim();
            String tonnageStr = parts[1].trim();

            if (ticketNo.isEmpty()) {
                return new ParseResult("Nomor tiket tidak ditemukan");
            }

            double tonnage;
            try {
                tonnage = Double.parseDouble(tonnageStr);
                if (tonnage < 0) {
                    return new ParseResult("Nilai tonnase tidak boleh negatif");
                }
            } catch (NumberFormatException e) {
                return new ParseResult("Format tonnase tidak valid: " + tonnageStr);
            }

            return new ParseResult(ticketNo, tonnage);
        } else {
            // Only ticket number, tonnage = 0 (user must input manually or barcode has no tonnage)
            return new ParseResult(rawBarcode, 0.0);
        }
    }
}
