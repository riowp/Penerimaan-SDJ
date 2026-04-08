package com.ticketscanner.app.utils;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.ticketscanner.app.models.User;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class UserManager {

    private static final String TAG = "UserManager";
    private static final ExecutorService executor = Executors.newSingleThreadExecutor();
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    public interface LoginCallback {
        void onSuccess(User user);
        void onError(String message);
    }

    public interface UserListCallback {
        void onSuccess(List<User> users);
        void onError(String message);
    }

    public interface ActionCallback {
        void onSuccess(String message);
        void onError(String message);
    }

    /**
     * Login dengan dukungan offline.
     *
     * Alur:
     * 1. Jika online → login ke server seperti biasa, simpan cache lokal
     * 2. Jika offline + ada cache valid → login dari cache lokal
     * 3. Jika offline + tidak ada cache → tampilkan pesan informatif
     *
     * @param context dibutuhkan untuk akses SessionManager (credential cache)
     */
    public static void login(Context context, String username, String password,
                             boolean isOnline, LoginCallback callback) {

        SessionManager sessionManager = new SessionManager(context);

        // ── OFFLINE: coba dari cache dulu ────────────────────
        if (!isOnline) {
            User cachedUser = sessionManager.loginFromCache(username, password);
            if (cachedUser != null) {
                // Berhasil dari cache
                mainHandler.post(() -> callback.onSuccess(cachedUser));
            } else if (sessionManager.hasValidCache()) {
                // Ada cache tapi username/password salah
                mainHandler.post(() -> callback.onError(
                    "Username atau password salah.\n(Mode offline — cek koneksi internet)"));
            } else {
                // Tidak ada cache sama sekali
                mainHandler.post(() -> callback.onError(
                    "Tidak ada koneksi internet.\n\n" +
                    "Login pertama kali membutuhkan koneksi.\n" +
                    "Setelah pernah login online, Anda bisa login\n" +
                    "tanpa internet untuk 30 hari ke depan."));
            }
            return;
        }

        // ── ONLINE: login ke server ───────────────────────────
        executor.execute(() -> {
            try {
                String url = SyncManager.SCRIPT_URL
                        + "?action=login"
                        + "&username=" + enc(username)
                        + "&password=" + enc(password);

                String response = getRequest(url);
                if (response == null) {
                    // Server tidak bisa dicapai — coba fallback ke cache
                    User cachedUser = sessionManager.loginFromCache(username, password);
                    if (cachedUser != null) {
                        Log.w(TAG, "Server tidak bisa dicapai, login dari cache");
                        mainHandler.post(() -> callback.onSuccess(cachedUser));
                    } else {
                        mainHandler.post(() -> callback.onError(
                            "Tidak dapat terhubung ke server.\nCek koneksi internet."));
                    }
                    return;
                }

                JSONObject json = new JSONObject(response);
                String status = json.optString("status");

                if ("ok".equals(status)) {
                    JSONObject userData = json.getJSONObject("user");
                    User user = new User(
                        userData.getString("username"),
                        "",
                        userData.getString("namaLengkap"),
                        userData.getString("role"),
                        "aktif"
                    );
                    // Simpan cache untuk login offline berikutnya
                    sessionManager.saveCredentialCache(
                        user.getUsername(), password,
                        user.getNamaLengkap(), user.getRole());

                    mainHandler.post(() -> callback.onSuccess(user));
                } else {
                    String msg = json.optString("message", "Login gagal");
                    mainHandler.post(() -> callback.onError(msg));
                }

            } catch (Exception e) {
                Log.e(TAG, "Login error", e);
                // Fallback ke cache jika terjadi error jaringan
                User cachedUser = sessionManager.loginFromCache(username, password);
                if (cachedUser != null) {
                    mainHandler.post(() -> callback.onSuccess(cachedUser));
                } else {
                    mainHandler.post(() -> callback.onError("Error: " + e.getMessage()));
                }
            }
        });
    }

    /** @deprecated Gunakan login(Context, ...) untuk dukungan offline */
    public static void login(String username, String password,
                             boolean isOnline, LoginCallback callback) {
        if (!isOnline) {
            callback.onError("Tidak ada koneksi internet.");
            return;
        }
        login(null, username, password, true, callback);
    }

    /** Ambil daftar semua user (hanya admin) */
    public static void getAllUsers(UserListCallback callback) {
        executor.execute(() -> {
            try {
                String response = getRequest(SyncManager.SCRIPT_URL + "?action=getUsers");
                if (response == null) {
                    mainHandler.post(() -> callback.onError("Tidak dapat terhubung"));
                    return;
                }

                JSONObject json = new JSONObject(response);
                JSONArray arr = json.getJSONArray("users");
                List<User> users = new ArrayList<>();
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject u = arr.getJSONObject(i);
                    users.add(new User(
                            u.getString("username"),
                            u.optString("password", ""),
                            u.getString("namaLengkap"),
                            u.getString("role"),
                            u.getString("status")
                    ));
                }
                mainHandler.post(() -> callback.onSuccess(users));

            } catch (Exception e) {
                mainHandler.post(() -> callback.onError(e.getMessage()));
            }
        });
    }

    /** Tambah user baru */
    public static void addUser(User user, ActionCallback callback) {
        executor.execute(() -> {
            try {
                String url = SyncManager.SCRIPT_URL
                        + "?action=addUser"
                        + "&username="    + enc(user.getUsername())
                        + "&password="    + enc(user.getPassword())
                        + "&namaLengkap=" + enc(user.getNamaLengkap())
                        + "&role="        + enc(user.getRole())
                        + "&status=aktif";

                String response = getRequest(url);
                handleActionResponse(response, callback);
            } catch (Exception e) {
                mainHandler.post(() -> callback.onError(e.getMessage()));
            }
        });
    }

    /** Update user */
    public static void updateUser(User user, ActionCallback callback) {
        executor.execute(() -> {
            try {
                String url = SyncManager.SCRIPT_URL
                        + "?action=updateUser"
                        + "&username="    + enc(user.getUsername())
                        + "&password="    + enc(user.getPassword())
                        + "&namaLengkap=" + enc(user.getNamaLengkap())
                        + "&role="        + enc(user.getRole())
                        + "&status="      + enc(user.getStatus());

                String response = getRequest(url);
                handleActionResponse(response, callback);
            } catch (Exception e) {
                mainHandler.post(() -> callback.onError(e.getMessage()));
            }
        });
    }

    /** Hapus user */
    public static void deleteUser(String username, ActionCallback callback) {
        executor.execute(() -> {
            try {
                String url = SyncManager.SCRIPT_URL
                        + "?action=deleteUser&username=" + enc(username);
                String response = getRequest(url);
                handleActionResponse(response, callback);
            } catch (Exception e) {
                mainHandler.post(() -> callback.onError(e.getMessage()));
            }
        });
    }

    private static void handleActionResponse(String response, ActionCallback callback) {
        try {
            if (response == null) {
                mainHandler.post(() -> callback.onError("Tidak dapat terhubung"));
                return;
            }
            JSONObject json = new JSONObject(response);
            String status = json.optString("status");
            String msg    = json.optString("message", "");
            if ("ok".equals(status)) mainHandler.post(() -> callback.onSuccess(msg));
            else mainHandler.post(() -> callback.onError(msg));
        } catch (Exception e) {
            mainHandler.post(() -> callback.onError(e.getMessage()));
        }
    }

    private static String getRequest(String urlString) {
        try {
            URL url = new URL(urlString);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setInstanceFollowRedirects(true);
            conn.setConnectTimeout(20000);
            conn.setReadTimeout(20000);
            BufferedReader br = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
            br.close();
            conn.disconnect();
            return sb.toString();
        } catch (Exception e) {
            Log.e(TAG, "getRequest error: " + e.getMessage());
            return null;
        }
    }

    private static String enc(String v) {
        try { return URLEncoder.encode(v != null ? v : "", "UTF-8"); }
        catch (Exception e) { return ""; }
    }
}
