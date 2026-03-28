package com.ticketscanner.app.activities;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.ticketscanner.app.R;
import com.ticketscanner.app.models.User;
import com.ticketscanner.app.utils.SyncManager;
import com.ticketscanner.app.utils.UserManager;

/**
 * Halaman Login — mendukung login online dan offline.
 *
 * ONLINE:  Login ke server, simpan credential cache di HP
 * OFFLINE: Login dari credential cache lokal (valid 30 hari)
 *
 * Operator tidak akan pernah ter-block meski tidak ada internet,
 * selama sebelumnya pernah login online minimal 1 kali.
 */
public class LoginActivity extends BaseActivity {

    private TextInputEditText etUsername, etPassword;
    private MaterialButton    btnLogin;
    private TextView          tvError, tvLoading, tvOfflineInfo;

    @Override
    protected boolean requiresLogin() {
        return false; // Halaman login tidak butuh cek sesi
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Jika sudah login dan sesi masih aktif, langsung ke Dashboard
        if (session.isLoggedIn()) {
            startActivity(new Intent(this, MainActivity.class));
            finish();
            return;
        }

        setContentView(R.layout.activity_login);
        if (getSupportActionBar() != null) getSupportActionBar().hide();

        initViews();
        showOfflineStatusIfNeeded();
    }

    private void initViews() {
        etUsername   = findViewById(R.id.etUsername);
        etPassword   = findViewById(R.id.etPassword);
        btnLogin     = findViewById(R.id.btnLogin);
        tvError      = findViewById(R.id.tvError);
        tvLoading    = findViewById(R.id.tvLoading);
        tvOfflineInfo= findViewById(R.id.tvOfflineInfo);

        btnLogin.setOnClickListener(v -> doLogin());
    }

    /**
     * Tampilkan banner status jaringan di halaman login.
     * Operator langsung tahu apakah sedang online atau offline.
     */
    private void showOfflineStatusIfNeeded() {
        boolean isOnline = SyncManager.isOnline(this);

        if (!isOnline) {
            tvOfflineInfo.setVisibility(View.VISIBLE);

            if (session.hasValidCache()) {
                // Ada cache — bisa login offline
                String cachedUser = session.getCachedUsername();
                tvOfflineInfo.setText(
                    "📵  Tidak ada koneksi internet\n" +
                    "✓  Mode offline aktif — login tersimpan untuk: " + cachedUser
                );
                tvOfflineInfo.setBackgroundColor(Color.parseColor("#E8F5E9")); // hijau muda
                tvOfflineInfo.setTextColor(Color.parseColor("#1B5E20"));
            } else {
                // Tidak ada cache — harus online untuk login pertama kali
                tvOfflineInfo.setText(
                    "📵  Tidak ada koneksi internet\n" +
                    "⚠  Login pertama kali membutuhkan koneksi internet"
                );
                tvOfflineInfo.setBackgroundColor(Color.parseColor("#FFF3E0")); // oranye muda
                tvOfflineInfo.setTextColor(Color.parseColor("#E65100"));
            }
        } else {
            tvOfflineInfo.setVisibility(View.GONE);
        }
    }

    private void doLogin() {
        String username = getText(etUsername);
        String password = getText(etPassword);

        if (username.isEmpty()) { etUsername.setError("Username wajib diisi"); return; }
        if (password.isEmpty()) { etPassword.setError("Password wajib diisi"); return; }

        setLoading(true);
        tvError.setVisibility(View.GONE);

        boolean isOnline = SyncManager.isOnline(this);

        // Gunakan login dengan Context agar mendukung offline mode
        UserManager.login(this, username, password, isOnline,
            new UserManager.LoginCallback() {
                @Override
                public void onSuccess(User user) {
                    session.saveSession(user);
                    setLoading(false);

                    // Tampilkan info mode login ke operator
                    String welcomeMsg = isOnline
                        ? "Selamat datang, " + user.getNamaLengkap() + "!"
                        : "Login offline berhasil, " + user.getNamaLengkap() + "!";
                    Toast.makeText(LoginActivity.this, welcomeMsg, Toast.LENGTH_SHORT).show();

                    startActivity(new Intent(LoginActivity.this, MainActivity.class));
                    finish();
                }

                @Override
                public void onError(String message) {
                    setLoading(false);
                    tvError.setText(message);
                    tvError.setVisibility(View.VISIBLE);
                }
            });
    }

    private void setLoading(boolean loading) {
        btnLogin.setEnabled(!loading);
        btnLogin.setText(loading ? "Memverifikasi..." : "MASUK");
        if (tvLoading != null)
            tvLoading.setVisibility(loading ? View.VISIBLE : View.GONE);
    }

    private String getText(TextInputEditText et) {
        return et.getText() != null ? et.getText().toString().trim() : "";
    }
}
