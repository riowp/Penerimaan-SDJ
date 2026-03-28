package com.ticketscanner.app.utils;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;

import androidx.core.content.FileProvider;

import com.ticketscanner.app.models.TicketScan;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ExcelExporter {

    public interface ExportCallback {
        void onSuccess(File file, Uri shareUri);
        void onError(String message);
    }

    public static void exportToExcel(Context context,
                                     List<TicketScan> shift1Scans,
                                     List<TicketScan> shift2Scans,
                                     String operationalDate,
                                     ExportCallback callback) {
        new Thread(() -> {
            try {
                String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
                        .format(new Date());
                String fileName = "Laporan_SDJ_" + operationalDate + "_" + timestamp + ".csv";

                // Simpan di internal app dir (aman di semua versi Android)
                File dir = new File(context.getExternalFilesDir(null), "Laporan");
                if (!dir.exists()) dir.mkdirs();

                File outFile = new File(dir, fileName);

                try (PrintWriter pw = new PrintWriter(new FileWriter(outFile))) {
                    pw.print('\uFEFF'); // BOM untuk Excel UTF-8

                    // Judul
                    pw.println("LAPORAN PENERIMAAN SDJ," + ShiftUtils.formatOperationalDate(operationalDate));
                    pw.println("Diekspor pada:," + new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(new Date()));
                    pw.println();

                    // Header kolom
                    pw.println("No,No. Tiket,Tonnase (Ton),Shift,Waktu Scan,Tgl Operasional,Stockpile,Operator,Remark,Tonnase Remark,Keterangan Remark");

                    // SHIFT 1
                    pw.println(",--- SHIFT 1 (07:00 - 19:00) ---,,,,,,,,");
                    double shift1Total = 0;
                    int seq = 1;
                    for (TicketScan scan : shift1Scans) {
                        pw.printf("%d,%s,%.2f,Shift 1,%s,%s,%s,%s,%s,%s,%s%n",
                                seq++,
                                esc(scan.getTicketNumber()),
                                scan.getTonnage(),
                                ShiftUtils.formatDateTime(scan.getScanTimestamp()),
                                scan.getOperationalDate(),
                                esc(scan.getStockpile()),
                                esc(scan.getOperator()),
                                scan.isHasRemark() ? "Ya" : "Tidak",
                                scan.isHasRemark() ? String.format("%.2f", scan.getRemarkTonnage()) : "-",
                                scan.isHasRemark() ? esc(scan.getRemarkNote()) : "-"
                        );
                        shift1Total += scan.getTonnage();
                    }
                    pw.printf("SUB TOTAL SHIFT 1,,%.2f,,,,,,,,\n", shift1Total);
                    pw.println();

                    // SHIFT 2
                    pw.println(",--- SHIFT 2 (19:00 - 07:00) ---,,,,,,,,");
                    double shift2Total = 0;
                    seq = 1;
                    for (TicketScan scan : shift2Scans) {
                        pw.printf("%d,%s,%.2f,Shift 2,%s,%s,%s,%s,%s,%s,%s%n",
                                seq++,
                                esc(scan.getTicketNumber()),
                                scan.getTonnage(),
                                ShiftUtils.formatDateTime(scan.getScanTimestamp()),
                                scan.getOperationalDate(),
                                esc(scan.getStockpile()),
                                esc(scan.getOperator()),
                                scan.isHasRemark() ? "Ya" : "Tidak",
                                scan.isHasRemark() ? String.format("%.2f", scan.getRemarkTonnage()) : "-",
                                scan.isHasRemark() ? esc(scan.getRemarkNote()) : "-"
                        );
                        shift2Total += scan.getTonnage();
                    }
                    pw.printf("SUB TOTAL SHIFT 2,,%.2f,,,,,,,,\n", shift2Total);
                    pw.println();

                    // Grand total
                    pw.printf("TOTAL PENERIMAAN HARIAN,,%.2f,,,,,,,,\n", shift1Total + shift2Total);
                    pw.printf("Total Tiket,%d,,,,,,,,,\n", shift1Scans.size() + shift2Scans.size());
                }

                // Buat URI via FileProvider agar bisa di-share ke app lain
                Uri shareUri = FileProvider.getUriForFile(
                    context,
                    context.getPackageName() + ".fileprovider",
                    outFile
                );

                callback.onSuccess(outFile, shareUri);

            } catch (Exception e) {
                callback.onError("Gagal export: " + e.getMessage());
            }
        }).start();
    }

    private static String esc(String v) {
        if (v == null) return "";
        if (v.contains(",") || v.contains("\"") || v.contains("\n"))
            return "\"" + v.replace("\"", "\"\"") + "\"";
        return v;
    }
}
