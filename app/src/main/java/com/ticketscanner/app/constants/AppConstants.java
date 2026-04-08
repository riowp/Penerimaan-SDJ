package com.ticketscanner.app.constants;

/**
 * Semua konstanta aplikasi di satu tempat.
 * Jika perlu ubah nilai (stockpile baru, warna, dsb) → cukup ubah di sini.
 */
public final class AppConstants {

    private AppConstants() {} // Tidak boleh di-instantiate

    // ── Shift ────────────────────────────────────────────────
    public static final int SHIFT_1 = 1;
    public static final int SHIFT_2 = 2;
    public static final int SHIFT_1_START_HOUR = 7;   // 07:00
    public static final int SHIFT_2_START_HOUR = 19;  // 19:00

    // ── Stockpile ────────────────────────────────────────────
    public static final String STOCKPILE_PLACEHOLDER = "-- Pilih Lokasi Stockpile --";
    public static final String[] STOCKPILE_OPTIONS = {
        STOCKPILE_PLACEHOLDER, "1C", "TC2", "Jetty 2", "Jetty 3/3B"
    };
    public static final String[] STOCKPILE_EDIT_OPTIONS = {
        "1C", "TC2", "Jetty 2", "Jetty 3/3B"
    };

    // ── Session ──────────────────────────────────────────────
    public static final long SESSION_DURATION_MS = 24 * 60 * 60 * 1000L; // 24 jam
    public static final long SESSION_WARNING_MS  = 30 * 60 * 1000L;      // 30 menit

    // ── Sync ─────────────────────────────────────────────────
    /** Menit sebelum pergantian shift untuk reminder sync */
    public static final int SYNC_REMINDER_MINUTES_BEFORE_SHIFT = 30;
    /** Maksimal data pending sebelum tampil warning */
    public static final int MAX_PENDING_WARNING = 5;

    // ── Roles ────────────────────────────────────────────────
    public static final String ROLE_ADMIN      = "admin";
    public static final String ROLE_SUPERVISOR = "supervisor";
    public static final String ROLE_OPERATOR   = "operator";
    public static final String ROLE_GUEST      = "guest";

    // ── Notification Channels ────────────────────────────────
    public static final String NOTIF_CHANNEL_SYNC    = "channel_sync";
    public static final String NOTIF_CHANNEL_SESSION = "channel_session";
    public static final String NOTIF_CHANNEL_DATA    = "channel_data";

    // ── Notification IDs ─────────────────────────────────────
    public static final int NOTIF_ID_SYNC_SUCCESS  = 1001;
    public static final int NOTIF_ID_SYNC_FAILED   = 1002;
    public static final int NOTIF_ID_SYNC_PENDING  = 1003;
    public static final int NOTIF_ID_DATA_NEW      = 1004;
    public static final int NOTIF_ID_SESSION_WARN  = 1005;
    public static final int NOTIF_ID_DUPLICATE     = 1006; // tiket duplikat di-rollback

    // ── Database ─────────────────────────────────────────────
    public static final String DB_NAME    = "penerimaan_sdj.db";
    public static final int    DB_VERSION = 7;

    // ── Barcode ──────────────────────────────────────────────
    public static final String BARCODE_SEPARATOR = "|";

    // ── UI ───────────────────────────────────────────────────
    public static final int SCAN_BACK_DELAY_MS = 800;  // delay kembali ke dashboard
}
