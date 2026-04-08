package com.ticketscanner.app.utils;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.ticketscanner.app.database.AppDatabase;
import com.ticketscanner.app.models.TicketScan;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * SyncManager — satu-satunya class yang bertugas bicara dengan Google Sheets.
 *
 * Prinsip utama:
 *   APK <──sync()──> Sheets   harus selalu sama dari dua arah.
 *
 * Cara kerja sync():
 *   1. PUSH  — kirim semua data lokal unsynced ke Sheets
 *   2. PULL  — wipe semua data lokal untuk tanggal itu
 *   3. REWRITE — isi ulang dari Sheets (termasuk data yang baru di-push)
 *
 * Dengan urutan ini:
 *   - Data yang di-input dari APK          → masuk Sheets via PUSH → kembali via REWRITE
 *   - Data yang di-edit dari APK           → update Sheets via syncUpdate → kembali via REWRITE
 *   - Data yang di-hapus dari APK          → hapus Sheets via syncDelete → tidak kembali via REWRITE
 *   - Data yang di-tambah di Sheets manual → masuk APK via REWRITE
 *   - Data yang di-edit di Sheets manual   → masuk APK via REWRITE
 *   - Data yang di-hapus di Sheets manual  → tidak kembali via REWRITE (lokal sudah di-wipe)
 */
public class SyncManager {

    private static final String TAG = "SyncManager";

    // URL statis — dipakai oleh UserManager dan class lain yang tidak punya Context
    // Nilai ini harus sama dengan app/src/main/res/values/secrets.xml
    public static final String SCRIPT_URL =
        "https://script.google.com/macros/s/AKfycbzveD1y6G2Nf9YejsMgaZlOzPeNg0le55pQMMT8eECOWJPgLpzqU6gLTDmoi0FjkG7GQA/exec";

    public interface SyncCallback {
        void onSuccess(int count);
        void onError(String message);
    }

    public interface CheckCallback {
        void onResult(boolean exists);
    }

    private static final ExecutorService executor = Executors.newSingleThreadExecutor();
    private static final Handler mainHandler      = new Handler(Looper.getMainLooper());
    private static final SimpleDateFormat SDF     =
        new SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault());

    // ── Cek koneksi ──────────────────────────────────────────────
    public static boolean isOnline(Context context) {
        ConnectivityManager cm = (ConnectivityManager)
            context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;
        NetworkCapabilities caps = cm.getNetworkCapabilities(cm.getActiveNetwork());
        return caps != null && (
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)     ||
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET));
    }

    // ================================================================
    // PUSH ONLY — dipakai ScanActivity setelah scan baru
    // Hanya kirim unsynced, tidak rewrite lokal
    // ================================================================
    public static void syncPending(Context context, SyncCallback callback) {
        if (!isOnline(context)) {
            post(callback, false, 0, "Tidak ada koneksi");
            return;
        }
        executor.execute(() -> {
            try {
                AppDatabase db = AppDatabase.getInstance(context);
                List<TicketScan> pending = db.ticketScanDao().getUnsyncedScans();
                if (pending.isEmpty()) { post(callback, true, 0, null); return; }

                String deviceId = android.provider.Settings.Secure.getString(
                    context.getContentResolver(),
                    android.provider.Settings.Secure.ANDROID_ID);

                int pushed = 0;
                for (TicketScan scan : pending) {

                    // Langsung push ke server — validasi duplikat dilakukan
                    // di sisi Apps Script (Fix 3 / v3.0+). Tidak perlu checkTicket
                    // dulu karena itu menambah 1 request extra per tiket = lambat.
                    String params = "action=add"
                        + "&id="              + scan.getId()
                        + "&ticketNumber="    + enc(scan.getTicketNumber())
                        + "&tonnage="         + scan.getTonnage()
                        + "&shiftNumber="     + scan.getShiftNumber()
                        + "&scanTime="        + enc(ShiftUtils.formatDateTime(scan.getScanTimestamp()))
                        + "&operationalDate=" + enc(scan.getOperationalDate())
                        + "&stockpile="       + enc(scan.getStockpile())
                        + "&operator="        + enc(scan.getOperator())
                        + "&hasRemark="       + scan.isHasRemark()
                        + "&remarkTonnage="   + scan.getRemarkTonnage()
                        + "&remarkNote="      + enc(scan.getRemarkNote())
                        + "&deviceId="        + enc(deviceId);

                    String resp = request(SCRIPT_URL + "?" + params);
                    if (resp != null && resp.contains("\"ok\"")) {
                        scan.setSynced(true);
                        db.ticketScanDao().update(scan);
                        pushed++;
                    } else if (resp != null && resp.contains("duplicate")) {
                        // Apps Script menolak karena duplikat
                        Log.w(TAG, "Server tolak duplikat: " + scan.getTicketNumber());
                        db.ticketScanDao().delete(scan);
                        final String tn = scan.getTicketNumber();
                        mainHandler.post(() ->
                            com.ticketscanner.app.notifications.NotificationHelper
                                .notifyDuplicateTicketRolledBack(context, tn));
                    }
                    // Jika resp null (timeout/no internet) — biarkan synced=0,
                    // akan dicoba lagi saat sync berikutnya
                }
                post(callback, true, pushed, null);
            } catch (Exception e) {
                post(callback, false, 0, e.getMessage());
            }
        });
    }

    // ================================================================
    // SYNC — satu-satunya entry point untuk sinkronisasi penuh
    //
    // Dipanggil dari:
    //   - Tombol SYNC di dashboard
    //   - onResume MainActivity
    //   - Setelah buka HistoryActivity (untuk tanggal tertentu)
    //
    // Callback onSuccess(pushed) memberi tahu berapa data yang dikirim.
    // ================================================================
    public static void sync(Context context, String date, SyncCallback callback) {
        if (!isOnline(context)) {
            post(callback, false, 0, "Tidak ada koneksi internet");
            return;
        }

        executor.execute(() -> {
            try {
                AppDatabase db = AppDatabase.getInstance(context);
                String deviceId = android.provider.Settings.Secure.getString(
                    context.getContentResolver(),
                    android.provider.Settings.Secure.ANDROID_ID);

                // ── STEP 1: PUSH ──────────────────────────────────
                // Kirim semua data lokal yang belum ke Sheets.
                // Hanya untuk tanggal yang di-sync agar tidak cross-date.
                int pushed = 0;
                List<TicketScan> unsynced = db.ticketScanDao().getUnsyncedScansByDate(date);
                for (TicketScan scan : unsynced) {
                    String params = "action=add"
                        + "&id="              + scan.getId()
                        + "&ticketNumber="    + enc(scan.getTicketNumber())
                        + "&tonnage="         + scan.getTonnage()
                        + "&shiftNumber="     + scan.getShiftNumber()
                        + "&scanTime="        + enc(ShiftUtils.formatDateTime(scan.getScanTimestamp()))
                        + "&operationalDate=" + enc(scan.getOperationalDate())
                        + "&stockpile="       + enc(scan.getStockpile())
                        + "&operator="        + enc(scan.getOperator())
                        + "&hasRemark="       + scan.isHasRemark()
                        + "&remarkTonnage="   + scan.getRemarkTonnage()
                        + "&remarkNote="      + enc(scan.getRemarkNote())
                        + "&deviceId="        + enc(deviceId);

                    String resp = request(SCRIPT_URL + "?" + params);
                    if (resp != null && resp.contains("\"ok\"")) {
                        scan.setSynced(true);
                        db.ticketScanDao().update(scan);
                        pushed++;
                        Log.d(TAG, "PUSH OK: " + scan.getTicketNumber());
                    } else {
                        Log.w(TAG, "PUSH GAGAL: " + scan.getTicketNumber() + " resp=" + resp);
                    }
                }

                // ── STEP 2: AMBIL DATA TERBARU DARI SHEETS ───────
                String response = request(SCRIPT_URL + "?action=getDataByDate&date=" + enc(date));
                if (response == null) {
                    post(callback, false, 0, "Gagal terhubung ke server");
                    return;
                }

                JSONObject json = new JSONObject(response);
                if (!"ok".equals(json.optString("status"))) {
                    post(callback, false, 0, json.optString("message", "Error server"));
                    return;
                }

                JSONArray records = json.getJSONArray("records");
                Log.d(TAG, "Sheets punya " + records.length() + " record untuk " + date);

                // ── STEP 3: WIPE LOKAL ────────────────────────────
                // Hapus SEMUA data lokal untuk tanggal ini.
                // Aman karena Step 1 sudah memastikan semua data
                // yang perlu disimpan sudah ada di Sheets.
                List<TicketScan> localAll = db.ticketScanDao().getScansByDate(date);
                for (TicketScan local : localAll) {
                    db.ticketScanDao().delete(local);
                }
                Log.d(TAG, "WIPE lokal: hapus " + localAll.size() + " record");

                // ── STEP 4: REWRITE DARI SHEETS ──────────────────
                // Insert ulang semua data dari Sheets ke lokal.
                // ID dari Sheets di-set ke lokal agar delete bisa cocok.
                int inserted = 0;
                for (int i = 0; i < records.length(); i++) {
                    JSONObject r    = records.getJSONObject(i);
                    String ticketNo = r.optString("ticketNumber", "").trim();
                    String opDate   = r.optString("operationalDate", date);
                    if (ticketNo.isEmpty()) continue;

                    double tonnage  = parseDouble(r.optString("tonnage",  "0"));
                    int    shiftNum = parseInt(r.optString("shiftNumber", "1"), 1);
                    long   scanTime = parseDateTime(r.optString("scanTime", ""));

                    TicketScan scan = new TicketScan(
                        ticketNo, tonnage, scanTime, shiftNum, opDate,
                        r.optString("stockpile", ""));

                    // Set ID dari Sheets — WAJIB agar delete APK = delete Sheets
                    long sid = parseLong(r.optString("id", "0"));
                    scan.setId(sid > 0 ? sid : (System.currentTimeMillis() / 1000 + i));

                    scan.setSynced(true);
                    scan.setOperator(r.optString("operator", ""));
                    scan.setHasRemark("Ya".equals(r.optString("hasRemark")));

                    String rt = r.optString("remarkTonnage", "");
                    if (!rt.isEmpty() && !rt.equals("-") && !rt.equals("0"))
                        scan.setRemarkTonnage(parseDouble(rt));

                    scan.setRemarkNote(r.optString("remarkNote", ""));
                    db.ticketScanDao().insert(scan);
                    inserted++;
                }

                Log.d(TAG, "REWRITE selesai: " + inserted + " record untuk " + date);
                final int finalPushed = pushed;
                post(callback, true, finalPushed, null);

            } catch (Exception e) {
                Log.e(TAG, "sync error: " + e.getMessage(), e);
                post(callback, false, 0, e.getMessage());
            }
        });
    }

    // ================================================================
    // UPDATE — Edit satu data di Sheets
    // Dipanggil langsung setelah user simpan dialog edit di Riwayat.
    // ================================================================
    public static void syncUpdate(Context context, long id, String ticketNumber, String opDate,
                                   double tonnage, String stockpile,
                                   boolean hasRemark, double remarkTonnage, String remarkNote,
                                   SyncCallback callback) {
        executor.execute(() -> {
            try {
                String response = request(SCRIPT_URL
                    + "?action=update"
                    + "&id="              + id
                    + "&ticketNumber="    + enc(ticketNumber)
                    + "&operationalDate=" + enc(opDate)
                    + "&tonnage="         + tonnage
                    + "&stockpile="       + enc(stockpile  != null ? stockpile  : "")
                    + "&hasRemark="       + hasRemark
                    + "&remarkTonnage="   + remarkTonnage
                    + "&remarkNote="      + enc(remarkNote != null ? remarkNote : ""));

                boolean ok = response != null && response.contains("\"ok\"");
                Log.d(TAG, "syncUpdate " + ticketNumber + " → " + (ok ? "OK" : "GAGAL"));
                post(callback, ok, ok ? 1 : 0, ok ? null : "Gagal update di Sheets");
            } catch (Exception e) {
                Log.e(TAG, "syncUpdate error: " + e.getMessage());
                post(callback, false, 0, e.getMessage());
            }
        });
    }

    // ================================================================
    // DELETE — Hapus satu data di Sheets berdasarkan ID
    // Dipanggil langsung setelah user konfirmasi hapus di Riwayat.
    // ================================================================
    public static void syncDelete(Context context, long id, String operationalDate, SyncCallback callback) {
        executor.execute(() -> {
            try {
                String url = SCRIPT_URL + "?action=delete&id=" + id
                    + (operationalDate != null ? "&operationalDate=" + enc(operationalDate) : "");
                String response = request(url);
                boolean ok = response != null && response.contains("\"ok\"");
                Log.d(TAG, "syncDelete id=" + id + " → " + (ok ? "OK" : "GAGAL"));
                post(callback, ok, ok ? 1 : 0, ok ? null : "Gagal hapus di Sheets");
            } catch (Exception e) {
                Log.e(TAG, "syncDelete error: " + e.getMessage());
                post(callback, false, 0, e.getMessage());
            }
        });
    }

    // ================================================================
    // CHECK DUPLIKAT — Cek apakah tiket sudah ada di Sheets
    // ================================================================
    public static void checkTicketExistsInSheets(Context context, String ticketNumber, CheckCallback callback) {
        executor.execute(() -> {
            try {
                String response = request(SCRIPT_URL
                    + "?action=checkTicket&ticketNumber=" + enc(ticketNumber));
                boolean exists = response != null &&
                    new JSONObject(response).optBoolean("exists", false);
                mainHandler.post(() -> callback.onResult(exists));
            } catch (Exception e) {
                mainHandler.post(() -> callback.onResult(false));
            }
        });
    }

    // ================================================================
    // HTTP REQUEST
    // ================================================================
    private static String request(String urlString) {
        HttpURLConnection conn = null;
        try {
            conn = openConn(urlString);

            int status = conn.getResponseCode();
            // Follow redirect manual — Apps Script redirect HTTP→HTTPS
            if (status == HttpURLConnection.HTTP_MOVED_TEMP
                    || status == HttpURLConnection.HTTP_MOVED_PERM
                    || status == 307 || status == 308) {
                String newUrl = conn.getHeaderField("Location");
                conn.disconnect();
                if (newUrl == null || newUrl.isEmpty()) return null;
                conn   = openConn(newUrl);
                status = conn.getResponseCode();
            }

            if (status != HttpURLConnection.HTTP_OK) {
                Log.w(TAG, "HTTP " + status);
                return null;
            }

            BufferedReader br = new BufferedReader(
                new InputStreamReader(conn.getInputStream(), "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
            br.close();
            return sb.toString();

        } catch (Exception e) {
            Log.e(TAG, "request error: " + e.getMessage());
            return null;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private static HttpURLConnection openConn(String urlString) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlString).openConnection();
        conn.setRequestMethod("GET");
        conn.setInstanceFollowRedirects(true);
        conn.setConnectTimeout(20000);
        conn.setReadTimeout(30000);
        conn.setRequestProperty("Accept", "application/json");
        return conn;
    }

    // ── Parse helpers ─────────────────────────────────────────────
    private static double parseDouble(String s) {
        try { return Double.parseDouble(s.trim()); } catch (Exception e) { return 0; }
    }

    private static int parseInt(String s, int fallback) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return fallback; }
    }

    private static long parseLong(String s) {
        try { return Long.parseLong(s.trim()); } catch (Exception e) { return 0; }
    }

    private static long parseDateTime(String s) {
        try {
            if (s == null || s.isEmpty()) return System.currentTimeMillis();
            return SDF.parse(s).getTime();
        } catch (Exception e) { return System.currentTimeMillis(); }
    }

    private static String enc(String value) {
        try { return URLEncoder.encode(value != null ? value : "", "UTF-8"); }
        catch (Exception e) { return ""; }
    }

    private static void post(SyncCallback cb, boolean ok, int count, String error) {
        if (cb == null) return;
        if (ok) mainHandler.post(() -> cb.onSuccess(count));
        else    mainHandler.post(() -> cb.onError(error != null ? error : "Error"));
    }
}
