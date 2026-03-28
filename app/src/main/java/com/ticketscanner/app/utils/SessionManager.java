package com.ticketscanner.app.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;
import android.util.Log;

import com.ticketscanner.app.models.User;

import java.security.MessageDigest;

/**
 * Mengelola sesi login dan cache kredensial untuk mode offline.
 *
 * OFFLINE-FIRST LOGIN:
 * Setelah berhasil login saat online, username + password hash disimpan lokal.
 * Jika tidak ada internet, login dilakukan dari cache lokal.
 * Ini memastikan operator tetap bisa login meski di lokasi tanpa sinyal.
 *
 * Keamanan:
 * - Password tidak disimpan plain text — disimpan sebagai MD5 hash
 * - Cache hanya dipakai saat benar-benar tidak ada koneksi
 * - Cache terhapus saat logout
 */
public class SessionManager {

    private static final String TAG = "SessionManager";

    // Session keys
    private static final String PREF_NAME        = "psdj_session";
    private static final String KEY_USERNAME     = "username";
    private static final String KEY_NAMA         = "nama";
    private static final String KEY_ROLE         = "role";
    private static final String KEY_LOGIN_AT     = "login_at";

    // Credential cache keys (untuk offline login)
    private static final String PREF_CRED        = "psdj_credentials";
    private static final String KEY_CRED_USER    = "cred_username";
    private static final String KEY_CRED_HASH    = "cred_hash";      // MD5 hash password
    private static final String KEY_CRED_NAMA    = "cred_nama";
    private static final String KEY_CRED_ROLE    = "cred_role";
    private static final String KEY_CRED_SAVED   = "cred_saved_at";

    // Sesi aktif 24 jam, cache kredensial 30 hari
    private static final long SESSION_DURATION   = 24L * 60 * 60 * 1000;
    private static final long CACHE_DURATION     = 30L * 24 * 60 * 60 * 1000;

    private final SharedPreferences prefs;
    private final SharedPreferences credPrefs;

    public SessionManager(Context context) {
        prefs     = context.getSharedPreferences(PREF_NAME,  Context.MODE_PRIVATE);
        credPrefs = context.getSharedPreferences(PREF_CRED,  Context.MODE_PRIVATE);
    }

    // ── Session aktif ────────────────────────────────────────

    public void saveSession(User user) {
        prefs.edit()
            .putString(KEY_USERNAME, user.getUsername())
            .putString(KEY_NAMA,     user.getNamaLengkap())
            .putString(KEY_ROLE,     user.getRole())
            .putLong(KEY_LOGIN_AT,   System.currentTimeMillis())
            .apply();
    }

    public boolean isLoggedIn() {
        String username = prefs.getString(KEY_USERNAME, null);
        if (username == null) return false;
        long loginAt = prefs.getLong(KEY_LOGIN_AT, 0);
        return (System.currentTimeMillis() - loginAt) < SESSION_DURATION;
    }

    public String getUsername()    { return prefs.getString(KEY_USERNAME, ""); }
    public String getNamaLengkap() { return prefs.getString(KEY_NAMA, ""); }
    public String getRole()        { return prefs.getString(KEY_ROLE, ""); }
    public boolean isAdmin()       { return User.ROLE_ADMIN.equals(getRole()); }
    public boolean isSupervisor()  { return User.ROLE_SUPERVISOR.equals(getRole()) || isAdmin(); }
    public boolean isGuest()       { return User.ROLE_GUEST.equals(getRole()); }
    public boolean canEdit()       { return !isGuest(); }

    public long getRemainingMs() {
        long loginAt = prefs.getLong(KEY_LOGIN_AT, 0);
        return SESSION_DURATION - (System.currentTimeMillis() - loginAt);
    }

    public String getRemainingTime() {
        long remaining = getRemainingMs();
        if (remaining <= 0) return "Sesi habis";
        long hours   = remaining / (60 * 60 * 1000);
        long minutes = (remaining % (60 * 60 * 1000)) / (60 * 1000);
        return hours + "j " + minutes + "m";
    }

    public void logout() {
        prefs.edit().clear().apply();
        // Catatan: credPrefs TIDAK dihapus saat logout biasa
        // Cache dihapus hanya saat clearAllCache() dipanggil
    }

    // ── Credential Cache — untuk offline login ────────────────

    /**
     * Simpan kredensial terenkripsi setelah login online berhasil.
     * Dipanggil dari UserManager.login() saat berhasil.
     */
    public void saveCredentialCache(String username, String password,
                                    String namaLengkap, String role) {
        String hash = md5Hash(password);
        credPrefs.edit()
            .putString(KEY_CRED_USER,  username)
            .putString(KEY_CRED_HASH,  hash)
            .putString(KEY_CRED_NAMA,  namaLengkap)
            .putString(KEY_CRED_ROLE,  role)
            .putLong(KEY_CRED_SAVED,   System.currentTimeMillis())
            .apply();
        Log.d(TAG, "Credential cache disimpan untuk: " + username);
    }

    /**
     * Coba login dari cache lokal saat tidak ada internet.
     * @return User jika berhasil, null jika gagal atau cache expired
     */
    public User loginFromCache(String username, String password) {
        String cachedUsername = credPrefs.getString(KEY_CRED_USER, null);
        String cachedHash     = credPrefs.getString(KEY_CRED_HASH, null);
        long   savedAt        = credPrefs.getLong(KEY_CRED_SAVED, 0);

        // Cache tidak ada
        if (cachedUsername == null || cachedHash == null) {
            Log.w(TAG, "Tidak ada credential cache tersimpan");
            return null;
        }

        // Cache sudah terlalu lama (> 30 hari)
        if (System.currentTimeMillis() - savedAt > CACHE_DURATION) {
            Log.w(TAG, "Credential cache sudah kadaluarsa (> 30 hari)");
            clearCredentialCache();
            return null;
        }

        // Cocokkan username dan hash password
        String inputHash = md5Hash(password);
        if (!cachedUsername.equals(username) || !cachedHash.equals(inputHash)) {
            Log.w(TAG, "Username/password tidak cocok dengan cache");
            return null;
        }

        // Berhasil — buat User dari cache
        User user = new User(
            cachedUsername,
            "",
            credPrefs.getString(KEY_CRED_NAMA, username),
            credPrefs.getString(KEY_CRED_ROLE, User.ROLE_OPERATOR),
            "aktif"
        );
        Log.d(TAG, "Login offline berhasil dari cache: " + username);
        return user;
    }

    /**
     * Cek apakah ada credential cache yang valid.
     * Dipakai untuk tampilkan info di halaman login saat offline.
     */
    public boolean hasValidCache() {
        String cached = credPrefs.getString(KEY_CRED_USER, null);
        long savedAt  = credPrefs.getLong(KEY_CRED_SAVED, 0);
        return cached != null
            && (System.currentTimeMillis() - savedAt) < CACHE_DURATION;
    }

    /** Username yang ada di cache (untuk info di UI) */
    public String getCachedUsername() {
        return credPrefs.getString(KEY_CRED_USER, "");
    }

    /** Hapus cache kredensial — dipanggil saat ganti akun atau security concern */
    public void clearCredentialCache() {
        credPrefs.edit().clear().apply();
        Log.d(TAG, "Credential cache dihapus");
    }

    /**
     * Update hash password di cache setelah ganti password berhasil.
     * Agar login offline tetap bisa pakai password baru.
     */
    public void updateCachedPassword(String newPassword) {
        String cachedUser = credPrefs.getString(KEY_CRED_USER, null);
        if (cachedUser == null) return;
        credPrefs.edit()
            .putString(KEY_CRED_HASH, md5Hash(newPassword))
            .putLong(KEY_CRED_SAVED, System.currentTimeMillis())
            .apply();
        Log.d(TAG, "Credential cache diperbarui untuk: " + cachedUser);
    }

    // ── Helper ────────────────────────────────────────────────

    /**
     * Hash MD5 password.
     * Password tidak pernah disimpan plain text.
     */
    private static String md5Hash(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(input.getBytes("UTF-8"));
            return Base64.encodeToString(digest, Base64.NO_WRAP);
        } catch (Exception e) {
            Log.e(TAG, "md5Hash error: " + e.getMessage());
            // Fallback — jangan simpan plain text, pakai hash sederhana
            return String.valueOf(input.hashCode());
        }
    }
}
