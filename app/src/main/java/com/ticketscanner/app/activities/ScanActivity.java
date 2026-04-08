package com.ticketscanner.app.activities;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.barcode.BarcodeScanner;
import com.google.mlkit.vision.barcode.BarcodeScannerOptions;
import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.common.InputImage;
import com.ticketscanner.app.R;
import com.ticketscanner.app.constants.AppConstants;
import com.ticketscanner.app.database.AppDatabase;
import com.ticketscanner.app.models.TicketScan;
import com.ticketscanner.app.utils.BarcodeParser;
import com.ticketscanner.app.utils.SessionManager;
import com.ticketscanner.app.utils.ShiftUtils;
import com.ticketscanner.app.utils.SyncManager;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Halaman scan barcode tiket.
 *
 * Fix v30:
 * 1. BUG CRASH: setStatus() dipanggil dari background thread (dbExecutor)
 *    → perbaiki: semua UI call harus lewat mainHandler.post()
 * 2. BUG DATA TIDAK MASUK: cek duplikat ke server menggunakan cache Apps Script
 *    yang tidak diinvalidate saat data dihapus dari Sheets
 *    → perbaiki: jika data pernah ada di lokal tapi sudah dihapus, bypass cek server
 *    → perbaiki: tambah flag deletedLocally untuk tiket yang pernah ada lalu dihapus
 * 3. BUG CRASH: doSaveTicket dipanggil dari dalam dbExecutor lalu memanggil dbExecutor lagi
 *    → perbaiki: doSaveTicket langsung eksekusi tanpa nested executor
 */
public class ScanActivity extends BaseActivity {

    private static final int CAMERA_PERMISSION_CODE = 100;

    private PreviewView previewView;
    private TextInputEditText etTicketNumber, etTonnage, etRemarkTonnage, etRemarkNote;
    private TextInputEditText etManualDate;
    private TextView tvCurrentShift, tvOperationalDate, tvScanStatus, tvTicketLabel;
    private TextView tvManualToggleLabel;
    private MaterialButton btnSaveManual;
    private Spinner spinnerStockpile, spinnerManualShift;
    private SwitchCompat switchRemark, switchManualInput;
    private LinearLayout layoutRemark, layoutManualToggle, layoutDatePicker;

    private AppDatabase db;
    private final ExecutorService cameraExecutor = Executors.newSingleThreadExecutor();
    private final ExecutorService dbExecutor     = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private BarcodeScanner barcodeScanner;
    private boolean isProcessing = false;
    private String selectedStockpile = "";

    // Mode manual: tanggal dan shift yang dipilih
    private boolean manualInputMode    = false;
    private String  manualSelectedDate = null; // null = pakai hari ini
    private int     manualSelectedShift = 0;   // 0 = pakai shift otomatis

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scan);

        // Guest tidak boleh scan
        if (isGuest()) {
            android.widget.Toast.makeText(this,
                "Akses ditolak — role Guest hanya bisa melihat data",
                android.widget.Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Scan Barcode");
        }

        db             = AppDatabase.getInstance(this);
        barcodeScanner = BarcodeScanning.getClient(
            new BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS).build());

        previewView        = findViewById(R.id.previewView);
        etTicketNumber     = findViewById(R.id.etTicketNumber);
        etTonnage          = findViewById(R.id.etTonnage);
        etRemarkTonnage    = findViewById(R.id.etRemarkTonnage);
        etRemarkNote       = findViewById(R.id.etRemarkNote);
        etManualDate       = findViewById(R.id.etManualDate);
        tvCurrentShift     = findViewById(R.id.tvCurrentShift);
        tvOperationalDate  = findViewById(R.id.tvOperationalDate);
        tvScanStatus       = findViewById(R.id.tvScanStatus);
        tvTicketLabel      = findViewById(R.id.tvTicketLabel);
        tvManualToggleLabel= findViewById(R.id.tvManualToggleLabel);
        btnSaveManual      = findViewById(R.id.btnSaveManual);
        spinnerStockpile   = findViewById(R.id.spinnerStockpile);
        spinnerManualShift = findViewById(R.id.spinnerManualShift);
        switchRemark       = findViewById(R.id.switchRemark);
        switchManualInput  = findViewById(R.id.switchManualInput);
        layoutRemark       = findViewById(R.id.layoutRemark);
        layoutManualToggle = findViewById(R.id.layoutManualToggle);
        layoutDatePicker   = findViewById(R.id.layoutDatePicker);

        switchRemark.setOnCheckedChangeListener((btn, checked) ->
            layoutRemark.setVisibility(checked ? View.VISIBLE : View.GONE));

        // Toggle manual tersedia untuk Admin DAN Operator
        // (Supervisor juga termasuk karena isSupervisor() sudah include Admin)
        if (isAdmin() || isOperator()) {
            layoutManualToggle.setVisibility(View.VISIBLE);
            // Label sesuai role
            if (isAdmin()) {
                tvManualToggleLabel.setText("✏ Input Manual (Admin)");
            } else {
                tvManualToggleLabel.setText("✏ Input Manual (Operator)");
            }
            switchManualInput.setOnCheckedChangeListener((btn, checked) -> {
                manualInputMode = checked;
                applyManualInputMode(checked);
            });
        }

        // Setup shift spinner manual
        setupManualShiftSpinner();

        // Date picker — tap field tanggal buka DatePickerDialog
        etManualDate.setOnClickListener(v -> showDatePicker());

        // Default: lock No. Tiket (hanya dari barcode)
        lockTicketField(true);

        setupSpinner();
        btnSaveManual.setOnClickListener(v -> saveManualEntry());
        updateShiftInfo();
        checkCameraAndStart();
    }

    private void setupSpinner() {
        android.widget.ArrayAdapter<String> adapter =
            new android.widget.ArrayAdapter<String>(this,
                R.layout.item_spinner_dark, AppConstants.STOCKPILE_OPTIONS) {
                @Override public boolean isEnabled(int pos) { return pos != 0; }
                @Override public View getView(int pos, View cv, android.view.ViewGroup p) {
                    View v = super.getView(pos, cv, p);
                    if (v instanceof TextView) {
                        ((TextView) v).setTextColor(pos == 0
                            ? android.graphics.Color.GRAY
                            : android.graphics.Color.parseColor("#212121"));
                        ((TextView) v).setBackgroundColor(android.graphics.Color.WHITE);
                    }
                    return v;
                }
                @Override public View getDropDownView(int pos, View cv, android.view.ViewGroup p) {
                    View v = super.getDropDownView(pos, cv, p);
                    if (v instanceof TextView) {
                        ((TextView) v).setTextColor(pos == 0
                            ? android.graphics.Color.GRAY
                            : android.graphics.Color.parseColor("#212121"));
                        ((TextView) v).setBackgroundColor(android.graphics.Color.WHITE);
                        ((TextView) v).setPadding(32, 24, 32, 24);
                    }
                    return v;
                }
            };
        adapter.setDropDownViewResource(R.layout.item_spinner_dark);
        spinnerStockpile.setAdapter(adapter);
        spinnerStockpile.setSelection(0);
        spinnerStockpile.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                selectedStockpile = pos == 0 ? "" : AppConstants.STOCKPILE_OPTIONS[pos];
            }
            @Override public void onNothingSelected(AdapterView<?> p) {}
        });
    }

    /** Setup spinner shift manual */
    private void setupManualShiftSpinner() {
        String[] shiftOptions = {"Shift 1 (07:00 – 19:00)", "Shift 2 (19:00 – 07:00)"};
        android.widget.ArrayAdapter<String> shiftAdapter =
            new android.widget.ArrayAdapter<String>(this,
                R.layout.item_spinner_dark, shiftOptions) {
                @Override
                public View getView(int pos, View cv, android.view.ViewGroup parent) {
                    View v = super.getView(pos, cv, parent);
                    if (v instanceof android.widget.TextView) {
                        ((android.widget.TextView) v).setTextColor(
                            android.graphics.Color.parseColor("#212121"));
                        ((android.widget.TextView) v).setBackgroundColor(
                            android.graphics.Color.WHITE);
                    }
                    return v;
                }
                @Override
                public View getDropDownView(int pos, View cv, android.view.ViewGroup parent) {
                    View v = super.getDropDownView(pos, cv, parent);
                    if (v instanceof android.widget.TextView) {
                        ((android.widget.TextView) v).setTextColor(
                            android.graphics.Color.parseColor("#212121"));
                        ((android.widget.TextView) v).setBackgroundColor(
                            android.graphics.Color.WHITE);
                        ((android.widget.TextView) v).setPadding(32, 24, 32, 24);
                    }
                    return v;
                }
            };
        spinnerManualShift.setAdapter(shiftAdapter);
        // Default: shift aktif saat ini (index 0 = shift 1, index 1 = shift 2)
        int currentShift = ShiftUtils.getCurrentShift();
        spinnerManualShift.setSelection(currentShift - 1);
        manualSelectedShift = currentShift;

        spinnerManualShift.setOnItemSelectedListener(
            new android.widget.AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(android.widget.AdapterView<?> parent,
                        View view, int position, long id) {
                    manualSelectedShift = position + 1; // 1-based
                }
                @Override
                public void onNothingSelected(android.widget.AdapterView<?> parent) {}
            });
    }

    /** Cek apakah user adalah Operator */
    private boolean isOperator() {
        com.ticketscanner.app.utils.SessionManager sess =
            new com.ticketscanner.app.utils.SessionManager(this);
        return com.ticketscanner.app.models.User.ROLE_OPERATOR.equals(sess.getRole());
    }

    private void updateShiftInfo() {
        String opDate = ShiftUtils.getCurrentOperationalDate();
        int shift     = ShiftUtils.getCurrentShift();
        tvCurrentShift.setText(ShiftUtils.getShiftLabel(shift));
        tvOperationalDate.setText("Tgl Operasional: " + ShiftUtils.formatOperationalDate(opDate));
    }

    /** Lock / unlock field No. Tiket */
    private void lockTicketField(boolean locked) {
        etTicketNumber.setFocusable(!locked);
        etTicketNumber.setFocusableInTouchMode(!locked);
        etTicketNumber.setClickable(!locked);
        etTicketNumber.setCursorVisible(!locked);
        if (locked) {
            etTicketNumber.setKeyListener(null);
            etTicketNumber.setHint("Menunggu scan barcode...");
        } else {
            etTicketNumber.setKeyListener(
                android.text.method.DigitsKeyListener.getInstance(
                    "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_/"));
            etTicketNumber.setHint("Ketik No. Tiket manual...");
            etTicketNumber.requestFocus();
        }
        etTicketNumber.setTextColor(locked
            ? android.graphics.Color.parseColor("#212121")
            : android.graphics.Color.parseColor("#E65100"));
    }

    /** Aktifkan / nonaktifkan mode input manual */
    private void applyManualInputMode(boolean enabled) {
        lockTicketField(!enabled);
        layoutDatePicker.setVisibility(enabled ? View.VISIBLE : View.GONE);

        if (enabled) {
            manualSelectedDate = ShiftUtils.getCurrentOperationalDate();
            etManualDate.setText(ShiftUtils.formatOperationalDate(manualSelectedDate));
            // Set default shift spinner ke shift aktif
            int currentShift = ShiftUtils.getCurrentShift();
            spinnerManualShift.setSelection(currentShift - 1);
            manualSelectedShift = currentShift;
            tvTicketLabel.setText("No. Tiket (Input Manual)");
            tvTicketLabel.setTextColor(android.graphics.Color.parseColor("#E65100"));
            etTicketNumber.setText("");
            String roleLabel = isAdmin() ? "Admin" : "Operator";
            setStatus("⚠ Mode Manual Aktif — " + roleLabel, false);
        } else {
            manualSelectedDate  = null;
            manualSelectedShift = 0;
            etTicketNumber.setText("");
            tvTicketLabel.setText("No. Tiket (dari Barcode)");
            tvTicketLabel.setTextColor(android.graphics.Color.parseColor("#1565C0"));
            setStatus("Kamera aktif — arahkan ke barcode", true);
        }
    }

    /** Date picker untuk tanggal operasional manual */
    private void showDatePicker() {
        java.util.Calendar cal = java.util.Calendar.getInstance();
        if (manualSelectedDate != null) {
            try {
                java.text.SimpleDateFormat sdf =
                    new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault());
                cal.setTime(sdf.parse(manualSelectedDate));
            } catch (Exception ignored) {}
        }

        new android.app.DatePickerDialog(this,
            (view, year, month, day) -> {
                manualSelectedDate = String.format(java.util.Locale.getDefault(),
                    "%04d-%02d-%02d", year, month + 1, day);
                etManualDate.setText(ShiftUtils.formatOperationalDate(manualSelectedDate));
            },
            cal.get(java.util.Calendar.YEAR),
            cal.get(java.util.Calendar.MONTH),
            cal.get(java.util.Calendar.DAY_OF_MONTH)
        ).show();
    }

    // ── Camera ────────────────────────────────────────────────
    private void checkCameraAndStart() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) startCamera();
        else ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_CODE);
    }

    @Override
    public void onRequestPermissionsResult(int req, @NonNull String[] perms,
                                           @NonNull int[] res) {
        super.onRequestPermissionsResult(req, perms, res);
        if (req == CAMERA_PERMISSION_CODE && res.length > 0
                && res[0] == PackageManager.PERMISSION_GRANTED)
            startCamera();
        else setStatus("Izin kamera ditolak", false);
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> future =
            ProcessCameraProvider.getInstance(this);
        future.addListener(() -> {
            try {
                ProcessCameraProvider cp = future.get();
                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                ImageAnalysis analysis = new ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build();

                analysis.setAnalyzer(cameraExecutor, imageProxy -> {
                    if (!isProcessing && imageProxy.getImage() != null) {
                        InputImage img = InputImage.fromMediaImage(
                            imageProxy.getImage(),
                            imageProxy.getImageInfo().getRotationDegrees());
                        barcodeScanner.process(img)
                            .addOnSuccessListener(barcodes -> {
                                if (!barcodes.isEmpty()) {
                                    String raw = barcodes.get(0).getRawValue();
                                    if (raw != null && !raw.isEmpty()) {
                                        isProcessing = true;
                                        mainHandler.post(() -> processBarcodeResult(raw));
                                        mainHandler.postDelayed(() -> isProcessing = false, 2000);
                                    }
                                }
                            })
                            .addOnCompleteListener(t -> imageProxy.close());
                    } else {
                        imageProxy.close();
                    }
                });

                cp.unbindAll();
                cp.bindToLifecycle(this,
                    CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis);
                setStatus("Kamera aktif — arahkan ke barcode", true);

            } catch (ExecutionException | InterruptedException e) {
                setStatus("Gagal buka kamera: " + e.getMessage(), false);
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void processBarcodeResult(String rawBarcode) {
        BarcodeParser.ParseResult parsed = BarcodeParser.parse(rawBarcode);
        if (!parsed.isValid) {
            // Format tidak dikenal — tetap set No. Tiket dari barcode (bukan kosongkan)
            // Operator tidak bisa ubah, tapi data tetap dari scan
            etTicketNumber.setText(rawBarcode.trim());
            setStatus("Format tidak dikenal — isi tonnase & pilih stockpile", false);
            return;
        }
        etTicketNumber.setText(parsed.ticketNumber);
        if (parsed.tonnage > 0) {
            etTonnage.setText(String.valueOf(parsed.tonnage));
            setStatus("✓ Terbaca: " + parsed.ticketNumber +
                " — Pilih stockpile lalu Simpan", true);
        } else {
            setStatus("Tiket: " + parsed.ticketNumber +
                " — Isi tonnase & pilih stockpile", false);
        }
    }

    // ── Validasi input ────────────────────────────────────────
    private void saveManualEntry() {
        String ticketNo   = getText(etTicketNumber);
        String tonnageStr = getText(etTonnage);

        // Validasi No. Tiket
        if (ticketNo.isEmpty()) {
            if (manualInputMode) {
                Toast.makeText(this, "⚠ Isi No. Tiket terlebih dahulu!", Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(this, "⚠ Scan barcode tiket terlebih dahulu!", Toast.LENGTH_LONG).show();
                setStatus("⚠ Arahkan kamera ke barcode tiket DT", false);
            }
            return;
        }

        // Validasi tanggal jika mode manual
        if (manualInputMode && (manualSelectedDate == null || manualSelectedDate.isEmpty())) {
            Toast.makeText(this, "⚠ Pilih tanggal operasional!", Toast.LENGTH_SHORT).show();
            return;
        }

        if (selectedStockpile.isEmpty()) {
            Toast.makeText(this, "⚠ Pilih Lokasi Stockpile terlebih dahulu!",
                Toast.LENGTH_SHORT).show();
            return;
        }
        if (tonnageStr.isEmpty()) { etTonnage.setError("Wajib diisi"); return; }

        double tonnage;
        try {
            tonnage = Double.parseDouble(tonnageStr);
            if (tonnage <= 0) { etTonnage.setError("Harus lebih dari 0"); return; }
        } catch (NumberFormatException e) {
            etTonnage.setError("Format tidak valid");
            return;
        }

        boolean hasRemark    = switchRemark.isChecked();
        double remarkTonnage = 0;
        String remarkNote    = "";
        if (hasRemark) {
            String rt = getText(etRemarkTonnage);
            String rn = getText(etRemarkNote);
            if (rt.isEmpty()) { etRemarkTonnage.setError("Isi tonnase remark"); return; }
            if (rn.isEmpty()) { etRemarkNote.setError("Isi keterangan remark"); return; }
            try {
                remarkTonnage = Double.parseDouble(rt);
            } catch (NumberFormatException e) {
                etRemarkTonnage.setError("Format tidak valid");
                return;
            }
            remarkNote = rn;
        }

        saveTicket(ticketNo, tonnage, hasRemark, remarkTonnage, remarkNote);
    }

    // ── Proses simpan tiket — OPTIMISTIC SAVE + BACKGROUND VALIDATION ──
    //
    // Alur:
    // 1. Cek duplikat LOKAL (~50ms)
    // 2. Simpan ke SQLite lokal (~50ms)
    // 3. Kembali ke dashboard SEGERA ← operator tidak perlu tunggu
    // 4. Background: cek duplikat ke SERVER
    //    → Jika AMAN: sync data ke server (normal)
    //    → Jika DUPLIKAT: ROLLBACK (hapus dari lokal) + notifikasi operator
    //
    private void saveTicket(String ticketNo, double tonnage, boolean hasRemark,
                             double remarkTonnage, String remarkNote) {
        long now         = System.currentTimeMillis();
        // Mode manual → pakai tanggal dan shift yang dipilih
        String opDate    = (manualInputMode && manualSelectedDate != null)
                           ? manualSelectedDate
                           : ShiftUtils.getOperationalDate(now);
        int shift        = (manualInputMode && manualSelectedShift > 0)
                           ? manualSelectedShift
                           : ShiftUtils.getShiftNumber(now);
        String stockpile = selectedStockpile;
        SessionManager sess = new SessionManager(this);
        String operatorName = sess.getNamaLengkap() + " (" + sess.getUsername() + ")"
                            + (manualInputMode ? " [MANUAL]" : "");

        btnSaveManual.setEnabled(false);
        setStatus("⏳ Menyimpan...", false);

        dbExecutor.execute(() -> {
            // Step 1: Cek duplikat lokal — cepat, tidak perlu internet
            int dupCount = db.ticketScanDao().countTicketEver(ticketNo);
            if (dupCount > 0) {
                mainHandler.post(() -> {
                    btnSaveManual.setEnabled(true);
                    new AlertDialog.Builder(ScanActivity.this)
                        .setTitle("⚠ Tiket Sudah Ada di HP Ini!")
                        .setMessage("Tiket " + ticketNo + " sudah pernah tersimpan.\n\n" +
                            "Gunakan menu Riwayat → Edit jika ingin mengubah data.")
                        .setPositiveButton("OK", null)
                        .setIcon(android.R.drawable.ic_dialog_alert)
                        .show();
                    setStatus("⚠ Tiket " + ticketNo + " sudah pernah tersimpan!", false);
                });
                return;
            }

            // Step 2: Simpan ke SQLite lokal
            try {
                long uniqueId = now / 1000;
                TicketScan scan = new TicketScan(
                    ticketNo, tonnage, now, shift, opDate, stockpile);
                scan.setId(uniqueId);
                scan.setOperator(operatorName);
                scan.setHasRemark(hasRemark);
                scan.setRemarkTonnage(remarkTonnage);
                scan.setRemarkNote(remarkNote);
                db.ticketScanDao().insert(scan);

                // Step 3: Kembali ke dashboard SEGERA
                mainHandler.post(() -> {
                    Toast.makeText(ScanActivity.this,
                        "✓ " + ticketNo + " | " +
                        String.format("%.2f", tonnage) + " Ton | " + stockpile,
                        Toast.LENGTH_SHORT).show();
                    setStatus("✓ Tersimpan!", true);
                    // Finish dulu, sync berjalan di background setelah activity tutup
                    if (!isFinishing()) finish();
                });

                // Step 4: Sync ke server di background — tidak tunggu hasilnya,
                // operator sudah bisa scan berikutnya. Duplikat ditangani server.
                if (SyncManager.isOnline(ScanActivity.this)) {
                    SyncManager.syncPending(ScanActivity.this.getApplicationContext(), null);
                }

            } catch (Exception e) {
                mainHandler.post(() -> {
                    btnSaveManual.setEnabled(true);
                    setStatus("❌ Gagal simpan: " + e.getMessage(), false);
                });
            }
        });
    }

    /**
     * Validasi duplikat ke server di background setelah operator kembali ke dashboard.
     *
     * Jika server bilang tiket sudah ada (dari HP lain):
     * → ROLLBACK: hapus dari database lokal
     * → Tampilkan notifikasi ke operator
     * → Data tidak jadi tersync ke server
     *
     * Jika server bilang aman:
     * → Sync data ke server seperti biasa
     */
    private void validateAndSyncInBackground(TicketScan scan) {
        SyncManager.checkTicketExistsInSheets(ScanActivity.this, scan.getTicketNumber(), exists -> {
            if (exists) {
                // DUPLIKAT DITEMUKAN — rollback dari lokal
                dbExecutor.execute(() -> {
                    try {
                        db.ticketScanDao().delete(scan);
                        android.util.Log.w("ScanActivity",
                            "ROLLBACK: tiket duplikat " + scan.getTicketNumber());
                    } catch (Exception e) {
                        android.util.Log.e("ScanActivity", "Rollback gagal: " + e.getMessage());
                    }
                });

                // Notifikasi ke operator via NotificationHelper
                com.ticketscanner.app.notifications.NotificationHelper
                    .notifyDuplicateTicketRolledBack(
                        ScanActivity.this, scan.getTicketNumber());

            } else {
                // Aman — push data baru ke Sheets saja (tidak perlu full sync)
                // Full sync akan terjadi saat onResume di MainActivity
                SyncManager.syncPending(ScanActivity.this, null);
            }
        });
    }

    // ── Helper ────────────────────────────────────────────────
    private String getText(TextInputEditText et) {
        return et.getText() != null ? et.getText().toString().trim() : "";
    }

    private void setStatus(String msg, boolean isSuccess) {
        if (tvScanStatus == null) return;
        Runnable r = () -> {
            tvScanStatus.setText(msg);
            tvScanStatus.setTextColor(0xFFFFFFFF);
            tvScanStatus.setBackgroundColor(isSuccess ? 0xFF1B5E20 : 0xFF37474F);
        };
        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
            r.run();
        } else {
            mainHandler.post(r);
        }
    }

    // ── Lifecycle ─────────────────────────────────────────────
    @Override
    protected void onDestroy() {
        super.onDestroy();
        cameraExecutor.shutdown();
        dbExecutor.shutdown();
        if (barcodeScanner != null) barcodeScanner.close();
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) { finish(); return true; }
        return super.onOptionsItemSelected(item);
    }
}
