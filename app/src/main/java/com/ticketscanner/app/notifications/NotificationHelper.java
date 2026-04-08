package com.ticketscanner.app.notifications;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.ticketscanner.app.R;
import com.ticketscanner.app.activities.MainActivity;
import com.ticketscanner.app.constants.AppConstants;

/**
 * Semua logika notifikasi ada di sini.
 * Activity tinggal panggil method sesuai kejadian — tidak perlu tahu detail notifikasi.
 *
 * 3 notifikasi prioritas tinggi:
 * 1. Sync berhasil / gagal
 * 2. Data baru masuk dari HP lain
 * 3. Data pending belum tersync (reminder)
 */
public class NotificationHelper {

    private static final String TAG = "NotificationHelper";

    // ── Setup — dipanggil sekali saat aplikasi pertama jalan ─
    public static void createChannels(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;

        NotificationManager nm = context.getSystemService(NotificationManager.class);
        if (nm == null) return;

        // Channel 1: Sync status
        NotificationChannel chSync = new NotificationChannel(
            AppConstants.NOTIF_CHANNEL_SYNC,
            "Status Sinkronisasi",
            NotificationManager.IMPORTANCE_DEFAULT);
        chSync.setDescription("Notifikasi saat data berhasil atau gagal tersync");
        nm.createNotificationChannel(chSync);

        // Channel 2: Data baru
        NotificationChannel chData = new NotificationChannel(
            AppConstants.NOTIF_CHANNEL_DATA,
            "Data Baru",
            NotificationManager.IMPORTANCE_DEFAULT);
        chData.setDescription("Notifikasi saat ada data baru dari HP lain");
        nm.createNotificationChannel(chData);

        // Channel 3: Session & reminder
        NotificationChannel chSession = new NotificationChannel(
            AppConstants.NOTIF_CHANNEL_SESSION,
            "Pengingat",
            NotificationManager.IMPORTANCE_HIGH);
        chSession.setDescription("Pengingat penting: sesi hampir habis, data pending");
        nm.createNotificationChannel(chSession);

        Log.d(TAG, "Notification channels created");
    }

    // ── 1. Sync berhasil ─────────────────────────────────────

    /**
     * Dipanggil setelah sync berhasil.
     * @param pushed jumlah data yang dikirim ke server
     * @param pulled jumlah data baru yang diterima dari server
     */
    public static void notifySyncSuccess(Context context, int pushed, int pulled) {
        String title, message;

        if (pushed == 0 && pulled == 0) {
            // Tidak ada yang berubah — tidak perlu notifikasi
            return;
        } else if (pushed > 0 && pulled > 0) {
            title   = "☁ Sync Berhasil";
            message = pushed + " data dikirim, " + pulled + " data baru diterima.";
        } else if (pushed > 0) {
            title   = "☁ Data Terkirim";
            message = pushed + " data berhasil dikirim ke server.";
        } else {
            title   = "📥 Data Baru Masuk";
            message = pulled + " data baru dari operator lain sudah masuk.";
        }

        show(context,
            AppConstants.NOTIF_CHANNEL_SYNC,
            AppConstants.NOTIF_ID_SYNC_SUCCESS,
            title, message,
            NotificationCompat.PRIORITY_DEFAULT);
    }

    // ── 2. Sync gagal ─────────────────────────────────────────

    /**
     * Dipanggil saat sync gagal.
     * @param pendingCount jumlah data yang masih belum tersync
     */
    public static void notifySyncFailed(Context context, long pendingCount) {
        String message = pendingCount > 0
            ? pendingCount + " data tersimpan lokal, belum tersync. Cek koneksi internet."
            : "Gagal terhubung ke server. Cek koneksi internet.";

        show(context,
            AppConstants.NOTIF_CHANNEL_SYNC,
            AppConstants.NOTIF_ID_SYNC_FAILED,
            "⚠ Sync Gagal",
            message,
            NotificationCompat.PRIORITY_DEFAULT);
    }

    // ── 3. Data baru dari HP lain ─────────────────────────────

    /**
     * Dipanggil saat pull menemukan data baru dari server (dari HP lain).
     * @param newCount jumlah data baru yang masuk
     */
    public static void notifyNewDataReceived(Context context, int newCount) {
        if (newCount <= 0) return;

        show(context,
            AppConstants.NOTIF_CHANNEL_DATA,
            AppConstants.NOTIF_ID_DATA_NEW,
            "📥 " + newCount + " Data Baru",
            newCount + " tiket baru dari operator lain sudah masuk ke dashboard.",
            NotificationCompat.PRIORITY_DEFAULT);
    }

    // ── 4. Reminder data pending sebelum shift selesai ───────

    /**
     * Dipanggil 30 menit sebelum pergantian shift jika masih ada data pending.
     * @param pendingCount jumlah data belum tersync
     */
    public static void notifyPendingSyncReminder(Context context, long pendingCount) {
        if (pendingCount <= 0) return;

        show(context,
            AppConstants.NOTIF_CHANNEL_SESSION,
            AppConstants.NOTIF_ID_SYNC_PENDING,
            "⏰ Jangan Lupa Sync!",
            pendingCount + " data belum tersync. Shift hampir selesai — tekan Sync sekarang.",
            NotificationCompat.PRIORITY_HIGH);
    }

    // ── 5. Sesi hampir habis ─────────────────────────────────

    /**
     * Dipanggil saat sesi login tersisa 30 menit.
     */
    public static void notifySessionExpiringSoon(Context context) {
        show(context,
            AppConstants.NOTIF_CHANNEL_SESSION,
            AppConstants.NOTIF_ID_SESSION_WARN,
            "⏰ Sesi Hampir Berakhir",
            "Sesi login Anda berakhir dalam 30 menit. Buka aplikasi untuk perpanjang.",
            NotificationCompat.PRIORITY_HIGH);
    }

    // ── Helper internal ───────────────────────────────────────

    private static void show(Context context, String channel, int id,
                              String title, String message, int priority) {
        try {
            // Intent — tap notifikasi buka MainActivity
            Intent intent = new Intent(context, MainActivity.class)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

            int flags = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                ? PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
                : PendingIntent.FLAG_UPDATE_CURRENT;

            PendingIntent pi = PendingIntent.getActivity(context, id, intent, flags);

            NotificationCompat.Builder builder = new NotificationCompat.Builder(context, channel)
                .setSmallIcon(android.R.drawable.ic_popup_sync)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(message))
                .setPriority(priority)
                .setContentIntent(pi)
                .setAutoCancel(true);  // hilang otomatis saat diketuk

            NotificationManagerCompat.from(context).notify(id, builder.build());
            Log.d(TAG, "Notifikasi ditampilkan: " + title);

        } catch (SecurityException e) {
            // Izin notifikasi belum diberikan (Android 13+)
            Log.w(TAG, "Izin notifikasi belum diberikan: " + e.getMessage());
        } catch (Exception e) {
            Log.e(TAG, "Gagal tampilkan notifikasi: " + e.getMessage(), e);
        }
    }

    /**
     * Dipanggil saat tiket terdeteksi duplikat di server setelah disimpan lokal.
     * Data sudah di-rollback dari lokal — operator perlu tahu.
     */
    public static void notifyDuplicateTicketRolledBack(Context context, String ticketNo) {
        show(context,
            AppConstants.NOTIF_CHANNEL_SYNC,
            AppConstants.NOTIF_ID_SYNC_FAILED,
            "⚠ Tiket Dibatalkan Otomatis",
            "Tiket " + ticketNo + " sudah ada di server (HP lain). " +
            "Data dibatalkan dan dihapus dari HP ini. Periksa menu Riwayat.",
            NotificationCompat.PRIORITY_HIGH);
    }

    /** Hapus semua notifikasi sync (dipakai saat user buka app) */
    public static void cancelSyncNotifications(Context context) {
        NotificationManagerCompat nm = NotificationManagerCompat.from(context);
        nm.cancel(AppConstants.NOTIF_ID_SYNC_SUCCESS);
        nm.cancel(AppConstants.NOTIF_ID_SYNC_FAILED);
        nm.cancel(AppConstants.NOTIF_ID_SYNC_PENDING);
        nm.cancel(AppConstants.NOTIF_ID_DATA_NEW);
    }
}
