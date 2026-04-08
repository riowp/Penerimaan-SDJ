package com.ticketscanner.app.activities;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;

import com.ticketscanner.app.repository.TicketRepository;
import com.ticketscanner.app.utils.SessionManager;

/**
 * Base class untuk semua Activity.
 *
 * Sebelumnya setiap Activity mengulang kode yang sama:
 * - Cek session
 * - Inisialisasi SessionManager
 * - Redirect ke Login kalau sesi habis
 *
 * Sekarang cukup extends BaseActivity — semua otomatis terlindungi.
 */
public abstract class BaseActivity extends AppCompatActivity {

    private static final String TAG = "BaseActivity";

    protected SessionManager session;
    protected TicketRepository ticketRepo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        session    = new SessionManager(this);
        ticketRepo = TicketRepository.getInstance(this);

        // Cek sesi — jika tidak valid, langsung redirect ke Login
        if (requiresLogin() && !session.isLoggedIn()) {
            Log.w(TAG, "Sesi tidak valid di " + getClass().getSimpleName() + " — redirect ke Login");
            goToLogin();
            return;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Cek ulang sesi setiap Activity muncul kembali
        if (requiresLogin() && !session.isLoggedIn()) {
            goToLogin();
        }
    }

    /**
     * Override ke false untuk Activity yang tidak butuh login (misal: SplashActivity, LoginActivity).
     */
    protected boolean requiresLogin() {
        return true;
    }

    /**
     * Logout dan redirect ke Login, bersihkan back stack.
     */
    protected void goToLogin() {
        session.logout();
        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    /**
     * Informasi user yang sedang login.
     */
    protected String getCurrentUserName()  { return session.getNamaLengkap(); }
    protected String getCurrentUsername()  { return session.getUsername(); }
    protected String getCurrentRole()      { return session.getRole(); }
    protected boolean isAdmin()            { return session.isAdmin(); }
    protected boolean isSupervisor()       { return session.isSupervisor(); }
    protected boolean isGuest()            { return session.isGuest(); }
    protected boolean canEdit()            { return session.canEdit(); } // false untuk guest
}
