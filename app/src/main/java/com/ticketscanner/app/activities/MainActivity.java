package com.ticketscanner.app.activities;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.ticketscanner.app.R;
import com.ticketscanner.app.constants.AppConstants;
import com.ticketscanner.app.models.User;
import com.ticketscanner.app.repository.TicketRepository;
import com.ticketscanner.app.utils.ExcelExporter;
import com.ticketscanner.app.utils.ShiftUtils;
import com.ticketscanner.app.utils.SyncManager;
import com.ticketscanner.app.utils.UserManager;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

public class MainActivity extends BaseActivity {

    private static final int CAMERA_PERMISSION_CODE = 100;

    // Throttle sync: tidak sync ulang jika baru sync < 30 detik lalu
    private static final long SYNC_COOLDOWN_MS = 30_000;
    private long lastSyncTime = 0;

    private TextView tvCurrentDate, tvCurrentShift, tvShift1Count, tvShift1Tonnage;
    private TextView tvShift2Count, tvShift2Tonnage, tvTotalTickets, tvTotalTonnage;
    private TextView tvSyncStatus, tvUserInfo, tvSessionTimer, tvHistoricalLabel;
    private TextView tvShift1Stockpile, tvShift2Stockpile, tvTotalStockpile;
    private ExtendedFloatingActionButton btnScan;
    private MaterialButton btnExport, btnHistory, btnSummary, btnUserMgmt, btnChangePassword;
    private MaterialButton btnDatePrev, btnDateNext;
    private View cardUserMgmt;

    // Tanggal yang sedang ditampilkan di dashboard — default = hari ini
    private String selectedDate = null;
    private final SimpleDateFormat sdfOp = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());

    private final Handler sessionHandler = new Handler(Looper.getMainLooper());

    private final Runnable sessionTimerRunnable = new Runnable() {
        @Override public void run() {
            if (tvSessionTimer == null) return;
            if (!session.isLoggedIn()) { goToLogin(); return; }
            tvSessionTimer.setText("Sesi: " + session.getRemainingTime());
            sessionHandler.postDelayed(this, 60_000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!session.isLoggedIn()) return;

        setContentView(R.layout.activity_main);
        initViews();
        applyRoleAccess();
        updateDashboard();
        sessionHandler.post(sessionTimerRunnable);
    }

    private void initViews() {
        tvCurrentDate   = findViewById(R.id.tvCurrentDate);
        tvCurrentShift  = findViewById(R.id.tvCurrentShift);
        tvHistoricalLabel = findViewById(R.id.tvHistoricalLabel);
        tvShift1Count   = findViewById(R.id.tvShift1Count);
        tvShift1Tonnage = findViewById(R.id.tvShift1Tonnage);
        tvShift2Count   = findViewById(R.id.tvShift2Count);
        tvShift2Tonnage = findViewById(R.id.tvShift2Tonnage);
        tvTotalTickets  = findViewById(R.id.tvTotalTickets);
        tvTotalTonnage  = findViewById(R.id.tvTotalTonnage);
        tvSyncStatus    = findViewById(R.id.tvSyncStatus);
        tvUserInfo      = findViewById(R.id.tvUserInfo);
        tvSessionTimer  = findViewById(R.id.tvSessionTimer);
        tvShift1Stockpile = findViewById(R.id.tvShift1Stockpile);
        tvShift2Stockpile = findViewById(R.id.tvShift2Stockpile);
        tvTotalStockpile  = findViewById(R.id.tvTotalStockpile);
        btnScan         = findViewById(R.id.btnScan);
        btnExport       = findViewById(R.id.btnExport);
        btnHistory      = findViewById(R.id.btnHistory);
        btnSummary      = findViewById(R.id.btnSummary);
        btnUserMgmt     = findViewById(R.id.btnUserMgmt);
        btnChangePassword = findViewById(R.id.btnChangePassword);
        btnDatePrev     = findViewById(R.id.btnDatePrev);
        btnDateNext     = findViewById(R.id.btnDateNext);
        cardUserMgmt    = findViewById(R.id.cardUserMgmt);

        // Inisialisasi tanggal = hari ini
        selectedDate = ShiftUtils.getCurrentOperationalDate();

        btnScan.setOnClickListener(v -> checkCameraPermissionAndScan());
        btnExport.setOnClickListener(v -> showExportConfirmation());
        btnHistory.setOnClickListener(v -> startActivity(new Intent(this, HistoryActivity.class)));
        btnSummary.setOnClickListener(v -> startActivity(new Intent(this, SummaryActivity.class)));
        btnUserMgmt.setOnClickListener(v -> startActivity(new Intent(this, UserManagementActivity.class)));
        btnChangePassword.setOnClickListener(v -> showChangePasswordDialog());
        findViewById(R.id.btnSync).setOnClickListener(v -> performSync(true));
        findViewById(R.id.btnLogout).setOnClickListener(v -> showLogoutConfirmation());

        // Navigator tanggal ◀ / ▶
        btnDatePrev.setOnClickListener(v -> shiftDate(-1));
        btnDateNext.setOnClickListener(v -> shiftDate(+1));

        tvUserInfo.setText(getCurrentUserName() + "  •  " + getRoleLabel(getCurrentRole()));
    }

    private void applyRoleAccess() {
        // Export: supervisor ke atas
        btnExport.setVisibility(isSupervisor() ? View.VISIBLE : View.GONE);
        // Ringkasan: semua role termasuk guest
        btnSummary.setVisibility(View.VISIBLE);
        // Kelola User: admin saja
        cardUserMgmt.setVisibility(isAdmin() ? View.VISIBLE : View.GONE);
        // SCAN: semua role kecuali guest
        btnScan.setVisibility(canEdit() ? View.VISIBLE : View.GONE);
        // Ganti password: semua role termasuk guest
        btnChangePassword.setVisibility(View.VISIBLE);
    }

    private String getRoleLabel(String role) {
        switch (role != null ? role : "") {
            case AppConstants.ROLE_ADMIN:      return "👑 Admin";
            case AppConstants.ROLE_SUPERVISOR: return "🔷 Supervisor";
            case AppConstants.ROLE_GUEST:      return "👁 Guest";
            default:                           return "👷 Operator";
        }
    }

    // ── Navigator tanggal ─────────────────────────────────────
    private void shiftDate(int deltaDays) {
        try {
            Calendar cal = Calendar.getInstance();
            cal.setTime(sdfOp.parse(selectedDate));
            cal.add(Calendar.DATE, deltaDays);
            String newDate = sdfOp.format(cal.getTime());

            // Tidak boleh maju melampaui hari ini
            String today = ShiftUtils.getCurrentOperationalDate();
            if (newDate.compareTo(today) > 0) {
                Toast.makeText(this, "Tidak bisa maju ke hari yang belum terjadi", Toast.LENGTH_SHORT).show();
                return;
            }
            selectedDate = newDate;
            updateDashboard();
        } catch (Exception e) {
            selectedDate = ShiftUtils.getCurrentOperationalDate();
            updateDashboard();
        }
    }

    // ── Dashboard — baca dari DB lokal saja, cepat ───────────
    private void updateDashboard() {
        String today = ShiftUtils.getCurrentOperationalDate();
        int shift    = ShiftUtils.getCurrentShift();
        boolean isToday = selectedDate.equals(today);

        // Label tanggal
        tvCurrentDate.setText("Tgl: " + ShiftUtils.formatOperationalDate(selectedDate));

        // Shift label — hanya tampil "Aktif" jika melihat hari ini
        if (isToday) {
            tvCurrentShift.setText("Shift Aktif: " + ShiftUtils.getShiftLabel(shift));
        } else {
            tvCurrentShift.setText("Data Historis");
        }

        // Label peringatan historis
        if (tvHistoricalLabel != null) {
            tvHistoricalLabel.setVisibility(isToday ? View.GONE : View.VISIBLE);
        }

        // Tombol ▶ — disable jika sudah di hari ini
        if (btnDateNext != null) {
            btnDateNext.setAlpha(isToday ? 0.3f : 1f);
            btnDateNext.setEnabled(!isToday);
        }

        ticketRepo.getTodayStats(selectedDate, stats -> {
            tvShift1Count.setText(String.valueOf(stats.shift1Count()));
            tvShift1Tonnage.setText(String.format("%.2f Ton", stats.shift1Ton));
            tvShift2Count.setText(String.valueOf(stats.shift2Count()));
            tvShift2Tonnage.setText(String.format("%.2f Ton", stats.shift2Ton));
            tvTotalTickets.setText(String.valueOf(stats.totalTickets));
            tvTotalTonnage.setText(String.format("%.2f Ton", stats.totalTon));
            updateSyncStatusUI(stats.unsyncedCount);

            // Tampilkan breakdown tonnage per stockpile
            String[] stockpiles = com.ticketscanner.app.constants.AppConstants.STOCKPILE_EDIT_OPTIONS;
            if (stats.shift1StockpileTon != null) {
                StringBuilder sb1 = new StringBuilder();
                StringBuilder sb2 = new StringBuilder();
                StringBuilder sbT = new StringBuilder();
                for (int i = 0; i < stockpiles.length; i++) {
                    if (stats.shift1StockpileTon[i] > 0)
                        sb1.append(stockpiles[i]).append(": ").append(String.format("%.2f Ton", stats.shift1StockpileTon[i])).append("\n");
                    if (stats.shift2StockpileTon[i] > 0)
                        sb2.append(stockpiles[i]).append(": ").append(String.format("%.2f Ton", stats.shift2StockpileTon[i])).append("\n");
                    if (stats.totalStockpileTon[i] > 0)
                        sbT.append(stockpiles[i]).append(": ").append(String.format("%.2f Ton", stats.totalStockpileTon[i])).append("\n");
                }
                tvShift1Stockpile.setText(sb1.length() > 0 ? sb1.toString().trim() : "-");
                tvShift2Stockpile.setText(sb2.length() > 0 ? sb2.toString().trim() : "-");
                tvTotalStockpile.setText(sbT.length() > 0 ? sbT.toString().trim() : "-");
            }
        });
    }

    private void updateSyncStatusUI(long unsyncedCount) {
        boolean isOnline = SyncManager.isOnline(this);
        if (!isOnline && unsyncedCount > 0) {
            tvSyncStatus.setText("📵 Offline — " + unsyncedCount + " data menunggu sync");
            tvSyncStatus.setTextColor(0xFFE65100);
        } else if (!isOnline) {
            tvSyncStatus.setText("📵 Offline — Semua data tersimpan lokal");
            tvSyncStatus.setTextColor(0xFF546E7A);
        } else if (unsyncedCount > 0) {
            tvSyncStatus.setText("⏳ " + unsyncedCount + " data belum tersync");
            tvSyncStatus.setTextColor(0xFFE65100);
        } else {
            tvSyncStatus.setText("☁ Semua data sinkron");
            tvSyncStatus.setTextColor(0xFF2E7D32);
        }
    }

    // ── Sync — dengan throttle ────────────────────────────────
    // forceSync=true → dari tombol SYNC (tidak ada throttle)
    // forceSync=false → dari onResume (kena throttle 30 detik)
    private void performSync(boolean forceSync) {
        if (!SyncManager.isOnline(this)) {
            updateSyncStatusUI(0); // refresh UI status offline
            return;
        }

        // Throttle: skip jika baru sync < 30 detik lalu dan bukan dari tombol
        long now = System.currentTimeMillis();
        if (!forceSync && (now - lastSyncTime) < SYNC_COOLDOWN_MS) {
            return;
        }

        tvSyncStatus.setText("⏳ Menyinkronkan...");
        tvSyncStatus.setTextColor(0xFFE65100);

        String today = ShiftUtils.getCurrentOperationalDate();
        SyncManager.sync(this, today, new SyncManager.SyncCallback() {
            @Override public void onSuccess(int pushed) {
                lastSyncTime = System.currentTimeMillis();
                updateDashboard();
                String msg = pushed > 0
                    ? "☁ " + pushed + " data dikirim, sinkron"
                    : "☁ Semua data sinkron";
                tvSyncStatus.setText(msg);
                tvSyncStatus.setTextColor(0xFF2E7D32);
                if (forceSync)
                    Toast.makeText(MainActivity.this, msg, Toast.LENGTH_SHORT).show();
            }
            @Override public void onError(String message) {
                tvSyncStatus.setText("⚠ Gagal sync: " + message);
                tvSyncStatus.setTextColor(0xFFC62828);
            }
        });
    }

    // ── Export ────────────────────────────────────────────────
    private void showExportConfirmation() {
        // Gunakan selectedDate (tanggal yang sedang ditampilkan di dashboard)
        // bukan getCurrentOperationalDate() yang selalu hari ini
        final String exportDate = selectedDate;

        ticketRepo.getForExport(exportDate, data ->
            new AlertDialog.Builder(this)
                .setTitle("Export Data ke CSV")
                .setMessage("Tanggal: " + ShiftUtils.formatOperationalDate(exportDate) +
                        "\nTotal Tiket: " + data.totalTickets +
                        "\nTotal Tonnase: " + String.format("%.2f Ton", data.totalTonnage) +
                        "\n\nLanjutkan export?")
                .setPositiveButton("Ya, Export", (d, w) ->
                    ExcelExporter.exportToExcel(this, data.shift1, data.shift2, exportDate,
                        new ExcelExporter.ExportCallback() {
                            @Override public void onSuccess(java.io.File file, android.net.Uri shareUri) {
                                runOnUiThread(() -> {
                                    android.content.Intent shareIntent = new android.content.Intent(android.content.Intent.ACTION_SEND);
                                    shareIntent.setType("text/csv");
                                    shareIntent.putExtra(android.content.Intent.EXTRA_STREAM, shareUri);
                                    shareIntent.putExtra(android.content.Intent.EXTRA_SUBJECT,
                                        "Laporan SDJ " + ShiftUtils.formatOperationalDate(exportDate));
                                    shareIntent.addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);

                                    new AlertDialog.Builder(MainActivity.this)
                                        .setTitle("Export Berhasil ✓")
                                        .setMessage("File: " + file.getName() +
                                            "\n\nBagikan file sekarang?")
                                        .setPositiveButton("📤 Bagikan", (dd, ww) ->
                                            startActivity(android.content.Intent.createChooser(
                                                shareIntent, "Bagikan laporan via...")))
                                        .setNegativeButton("Tutup", null)
                                        .show();
                                });
                            }
                            @Override public void onError(String message) {
                                runOnUiThread(() ->
                                    Toast.makeText(MainActivity.this,
                                        "Gagal export: " + message, Toast.LENGTH_LONG).show());
                            }
                        })
                )
                .setNegativeButton("Batal", null)
                .show()
        );
    }

    // ── Camera ────────────────────────────────────────────────
    private void checkCameraPermissionAndScan() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_CODE);
        } else {
            startActivity(new Intent(this, ScanActivity.class));
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_PERMISSION_CODE && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startActivity(new Intent(this, ScanActivity.class));
        }
    }

    // ── Ganti Password ────────────────────────────────────────
    private void showChangePasswordDialog() {
        // Inflate dialog — paksa light theme agar teks terbaca di dark mode
        android.view.ContextThemeWrapper lightCtx = new android.view.ContextThemeWrapper(
            this, com.google.android.material.R.style.Theme_MaterialComponents_Light_Dialog);
        android.view.View dialogView = android.view.LayoutInflater.from(lightCtx)
            .inflate(R.layout.dialog_change_pin, null);

        com.google.android.material.textfield.TextInputEditText etOld  =
            dialogView.findViewById(R.id.etOldPassword);
        com.google.android.material.textfield.TextInputEditText etNew  =
            dialogView.findViewById(R.id.etNewPin);
        com.google.android.material.textfield.TextInputEditText etConf =
            dialogView.findViewById(R.id.etConfirmPin);
        android.widget.TextView tvInfo = dialogView.findViewById(R.id.tvDialogInfo);

        AlertDialog dialog = new AlertDialog.Builder(this)
            .setTitle("Ganti Password")
            .setMessage("Akun: " + getCurrentUsername())
            .setView(dialogView)
            .setPositiveButton("Simpan", null) // dihandle manual agar dialog tidak tutup jika error
            .setNegativeButton("Batal", null)
            .create();

        dialog.setOnShowListener(di -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String oldPass  = etOld.getText()  != null ? etOld.getText().toString().trim()  : "";
                String newPass  = etNew.getText()  != null ? etNew.getText().toString().trim()  : "";
                String confPass = etConf.getText() != null ? etConf.getText().toString().trim() : "";

                // Validasi di sisi klien
                if (oldPass.isEmpty()) {
                    showDialogError(tvInfo, "Password saat ini wajib diisi"); return; }
                if (newPass.isEmpty()) {
                    showDialogError(tvInfo, "Password baru wajib diisi"); return; }
                if (newPass.length() < 4) {
                    showDialogError(tvInfo, "Password baru minimal 4 karakter"); return; }
                if (!newPass.equals(confPass)) {
                    showDialogError(tvInfo, "Konfirmasi password tidak cocok"); return; }
                if (newPass.equals(oldPass)) {
                    showDialogError(tvInfo, "Password baru tidak boleh sama dengan yang lama"); return; }

                // Verifikasi password lama ke server, lalu update jika benar
                if (!SyncManager.isOnline(this)) {
                    showDialogError(tvInfo, "Tidak ada koneksi internet"); return;
                }

                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
                tvInfo.setTextColor(0xFF1565C0);
                tvInfo.setText("⏳ Memverifikasi...");
                tvInfo.setVisibility(android.view.View.VISIBLE);

                // Login ulang untuk verifikasi password lama
                UserManager.login(this, getCurrentUsername(), oldPass, true,
                    new UserManager.LoginCallback() {
                        @Override public void onSuccess(com.ticketscanner.app.models.User user) {
                            // Password lama benar — update ke password baru
                            User updated = new User(
                                user.getUsername(), newPass,
                                user.getNamaLengkap(), user.getRole(), "aktif");

                            UserManager.updateUser(updated, new UserManager.ActionCallback() {
                                @Override public void onSuccess(String message) {
                                    dialog.dismiss();
                                    // Update cache session dengan password baru
                                    session.updateCachedPassword(newPass);
                                    Toast.makeText(MainActivity.this,
                                        "✓ Password berhasil diubah", Toast.LENGTH_SHORT).show();
                                }
                                @Override public void onError(String message) {
                                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                                    showDialogError(tvInfo, "Gagal update: " + message);
                                }
                            });
                        }
                        @Override public void onError(String message) {
                            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                            showDialogError(tvInfo, "Password saat ini salah");
                        }
                    });
            });
        });
        dialog.show();
    }

    private void showDialogError(android.widget.TextView tv, String msg) {
        tv.setTextColor(0xFFE65100);
        tv.setText(msg);
        tv.setVisibility(android.view.View.VISIBLE);
    }

    // ── Logout ────────────────────────────────────────────────
    private void showLogoutConfirmation() {
        new AlertDialog.Builder(this)
            .setTitle("Logout")
            .setMessage("Apakah Anda yakin ingin keluar?")
            .setPositiveButton("Logout", (d, w) -> {
                sessionHandler.removeCallbacks(sessionTimerRunnable);
                goToLogin();
            })
            .setNegativeButton("Batal", null)
            .show();
    }

    // ── Lifecycle ─────────────────────────────────────────────
    @Override
    protected void onResume() {
        super.onResume();
        if (!session.isLoggedIn()) return;

        // Jika sedang melihat hari ini dan hari sudah berganti → ikuti hari baru
        // Jika sedang melihat historis → biarkan tetap di tanggal yang dipilih
        String today = ShiftUtils.getCurrentOperationalDate();
        if (selectedDate == null || selectedDate.equals(today)) {
            selectedDate = today; // tetap hari ini (atau inisialisasi pertama)
        }
        // Jika melihat historis, selectedDate tidak berubah → data tetap tampil

        updateDashboard();
        performSync(false);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        sessionHandler.removeCallbacks(sessionTimerRunnable);
    }
}
