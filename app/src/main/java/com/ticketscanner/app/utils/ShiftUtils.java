package com.ticketscanner.app.utils;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Shift Logic:
 *  Shift 1 → 07:00 – 19:00  (same calendar day)
 *  Shift 2 → 19:00 – 07:00  (next calendar day)
 *
 * Operational Date cut-off = 07:00.
 *  Example: 01-Jan 07:00 ... 02-Jan 06:59  → operational date = "2025-01-01"
 *
 * A scan at  00:30 on 02-Jan belongs to:
 *   operational date = "2025-01-01"  (still within the 01-Jan operational day)
 *   shift 2  (19:00 01-Jan → 07:00 02-Jan)
 */
public class ShiftUtils {

    private static final SimpleDateFormat SDF =
            new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());

    /**
     * Returns the operational date string (yyyy-MM-dd) for the given epoch millis.
     * Operational day starts at 07:00 and ends at 06:59 the following calendar day.
     */
    public static String getOperationalDate(long epochMillis) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(epochMillis);

        int hour = cal.get(Calendar.HOUR_OF_DAY);

        // If the clock time is before 07:00 this calendar day, the operational date
        // is the PREVIOUS calendar day.
        if (hour < 7) {
            cal.add(Calendar.DATE, -1);
        }

        return SDF.format(cal.getTime());
    }

    /**
     * Returns 1 (Shift-1: 07:00–19:00) or 2 (Shift-2: 19:00–07:00).
     */
    public static int getShiftNumber(long epochMillis) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(epochMillis);
        int hour = cal.get(Calendar.HOUR_OF_DAY);

        // Shift 1: 07:00 <= hour < 19:00
        if (hour >= 7 && hour < 19) {
            return 1;
        }
        // Shift 2: 19:00–23:59 or 00:00–06:59
        return 2;
    }

    /** Human-readable shift label */
    public static String getShiftLabel(int shiftNumber) {
        return shiftNumber == 1 ? "Shift 1 (07:00 – 19:00)" : "Shift 2 (19:00 – 07:00)";
    }

    /** Current operational date */
    public static String getCurrentOperationalDate() {
        return getOperationalDate(System.currentTimeMillis());
    }

    /** Current shift number */
    public static int getCurrentShift() {
        return getShiftNumber(System.currentTimeMillis());
    }

    /** Format epoch millis to readable date-time */
    public static String formatDateTime(long epochMillis) {
        SimpleDateFormat fmt = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault());
        return fmt.format(new Date(epochMillis));
    }

    /** Format epoch millis to readable time only */
    public static String formatTime(long epochMillis) {
        SimpleDateFormat fmt = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
        return fmt.format(new Date(epochMillis));
    }

    /** Format operational date to a friendlier display */
    public static String formatOperationalDate(String yyyyMMdd) {
        try {
            Date d = SDF.parse(yyyyMMdd);
            SimpleDateFormat display = new SimpleDateFormat("dd MMMM yyyy", new Locale("id", "ID"));
            return display.format(d);
        } catch (Exception e) {
            return yyyyMMdd;
        }
    }

    /**
     * Cek apakah sekarang mendekati pergantian shift (dalam 30 menit).
     * Dipakai untuk trigger notifikasi reminder sync.
     * Pergantian shift: jam 19:00 dan jam 07:00.
     */
    public static boolean isNearShiftEnd() {
        java.util.Calendar now = java.util.Calendar.getInstance();
        now.setTimeZone(java.util.TimeZone.getTimeZone("Asia/Jakarta"));
        int hour   = now.get(java.util.Calendar.HOUR_OF_DAY);
        int minute = now.get(java.util.Calendar.MINUTE);
        int totalMinutes = hour * 60 + minute;

        // 30 menit sebelum jam 07:00 = 06:30 = 390 menit
        // 30 menit sebelum jam 19:00 = 18:30 = 1110 menit
        return (totalMinutes >= 390 && totalMinutes <= 420)   // 06:30 - 07:00
            || (totalMinutes >= 1110 && totalMinutes <= 1140); // 18:30 - 19:00
    }
}
