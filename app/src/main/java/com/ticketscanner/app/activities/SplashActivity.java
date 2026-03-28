package com.ticketscanner.app.activities;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;

import com.ticketscanner.app.R;
import com.ticketscanner.app.notifications.NotificationHelper;

/**
 * Layar awal (splash screen).
 * Tidak butuh login check — override requiresLogin() = false.
 */
public class SplashActivity extends BaseActivity {

    @Override
    protected boolean requiresLogin() {
        return false;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);
        if (getSupportActionBar() != null) getSupportActionBar().hide();

        // Setup notification channels di awal aplikasi
        NotificationHelper.createChannels(this);

        new Handler().postDelayed(() -> {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
        }, 2500);
    }
}
