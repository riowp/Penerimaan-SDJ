package com.ticketscanner.app.activities;

import android.app.DatePickerDialog;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.ticketscanner.app.R;
import com.ticketscanner.app.adapters.ScanAdapter;
import com.ticketscanner.app.database.AppDatabase;
import com.ticketscanner.app.models.TicketScan;
import com.ticketscanner.app.utils.ShiftUtils;
import com.ticketscanner.app.utils.SyncManager;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class HistoryActivity extends BaseActivity {

    private static final String[] STOCKPILE_OPTIONS = {
            "1C", "TC2", "Jetty 2", "Jetty 3/3B"
    };

    private MaterialButton btnPickDate;
    private Spinner spinnerShift;
    private RecyclerView rvHistory;
    private TextView tvSummary, tvSelectedDate, tvSyncInfo;
    private ProgressBar progressBar;
    private TextInputEditText etSearch;

    private AppDatabase db;
    private ScanAdapter adapter;
    private ExecutorService executor = Executors.newSingleThreadExecutor();
    private Handler mainHandler = new Handler(Looper.getMainLooper());

    private String selectedDate = null;
    private int selectedShift = 0;
    private String searchQuery = "";
    private List<TicketScan> allScans = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Riwayat Scan");
        }

        db = AppDatabase.getInstance(this);

        btnPickDate    = findViewById(R.id.btnPickDate);
        spinnerShift   = findViewById(R.id.spinnerShift);
        rvHistory      = findViewById(R.id.rvHistory);
        tvSummary      = findViewById(R.id.tvSummary);
        tvSelectedDate = findViewById(R.id.tvSelectedDate);
        tvSyncInfo     = findViewById(R.id.tvSyncInfo);
        progressBar    = findViewById(R.id.progressBar);
        etSearch       = findViewById(R.id.etSearch);

        adapter = new ScanAdapter();
        adapter.setCanEdit(canEdit()); // guest tidak bisa edit/hapus
        adapter.setOnActionListener(new ScanAdapter.OnActionListener() {
            @Override public void onEdit(TicketScan scan) { showEditDialog(scan); }
            @Override public void onDelete(TicketScan scan) { showDeleteDialog(scan); }
        });
        rvHistory.setLayoutManager(new LinearLayoutManager(this));
        rvHistory.setAdapter(adapter);

        selectedDate = ShiftUtils.getCurrentOperationalDate();
        tvSelectedDate.setText(ShiftUtils.formatOperationalDate(selectedDate));

        // Spinner shift — teks selalu hitam di background putih (fix dark mode)
        String[] shiftOptions = {"Semua Shift", "Shift 1 (07:00-19:00)", "Shift 2 (19:00-07:00)"};
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
        shiftAdapter.setDropDownViewResource(R.layout.item_spinner_dark);
        spinnerShift.setAdapter(shiftAdapter);
        spinnerShift.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                selectedShift = pos;
                pullAndLoad();
            }
            @Override public void onNothingSelected(AdapterView<?> p) {}
        });

        btnPickDate.setOnClickListener(v -> showDatePicker());

        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) {}
            @Override public void afterTextChanged(Editable s) {
                searchQuery = s.toString().trim().toLowerCase();
                applySearchFilter();
            }
        });

        pullAndLoad();
    }

    private void showDatePicker() {
        Calendar cal = Calendar.getInstance();
        try { cal.setTime(new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(selectedDate)); }
        catch (Exception e) { /* pakai hari ini */ }

        new DatePickerDialog(this, (view, year, month, day) -> {
            selectedDate = String.format(Locale.getDefault(), "%04d-%02d-%02d", year, month + 1, day);
            tvSelectedDate.setText(ShiftUtils.formatOperationalDate(selectedDate));
            etSearch.setText("");
            searchQuery = "";
            pullAndLoad();
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void pullAndLoad() {
        if (!SyncManager.isOnline(this)) { loadData(); return; }
        progressBar.setVisibility(View.VISIBLE);
        tvSyncInfo.setText("⏳ Menyinkronkan...");
        tvSyncInfo.setVisibility(View.VISIBLE);
        SyncManager.sync(this, selectedDate, new SyncManager.SyncCallback() {
            @Override public void onSuccess(int count) {
                progressBar.setVisibility(View.GONE);
                tvSyncInfo.setText("☁ Up-to-date");
                loadData();
            }
            @Override public void onError(String message) {
                progressBar.setVisibility(View.GONE);
                tvSyncInfo.setText("⚠ " + message);
                loadData();
            }
        });
    }

    private void loadData() {
        executor.execute(() -> {
            List<TicketScan> scans = selectedShift == 0
                    ? db.ticketScanDao().getScansByDate(selectedDate)
                    : db.ticketScanDao().getScansByDateAndShift(selectedDate, selectedShift);
            mainHandler.post(() -> { allScans = scans; applySearchFilter(); });
        });
    }

    private void applySearchFilter() {
        List<TicketScan> filtered = new ArrayList<>();
        if (searchQuery.isEmpty()) {
            filtered.addAll(allScans);
        } else {
            for (TicketScan scan : allScans) {
                if (scan.getTicketNumber().toLowerCase().contains(searchQuery) ||
                    scan.getStockpile().toLowerCase().contains(searchQuery) ||
                    scan.getOperator().toLowerCase().contains(searchQuery)) {
                    filtered.add(scan);
                }
            }
        }
        double totalTon = 0;
        for (TicketScan s : filtered) totalTon += s.getEffectiveTonnage();
        final double ft = totalTon;
        final int count = filtered.size();
        adapter.setData(filtered);
        if (searchQuery.isEmpty()) {
            tvSummary.setText(count == 0 ? "Tidak ada data"
                    : String.format("%d tiket | %.2f Ton", count, ft));
        } else {
            tvSummary.setText(count == 0 ? "Tidak ditemukan: \"" + searchQuery + "\""
                    : String.format("Hasil: %d tiket | %.2f Ton", count, ft));
        }
    }

    private void showEditDialog(TicketScan scan) {
        // Inflate custom dialog dengan tonnase + stockpile + remark
        // Paksa light theme pada dialog agar teks selalu hitam di dark mode
        android.view.ContextThemeWrapper lightCtx = new android.view.ContextThemeWrapper(
                this, com.google.android.material.R.style.Theme_MaterialComponents_Light_Dialog);
        View dialogView = android.view.LayoutInflater.from(lightCtx)
                .inflate(R.layout.dialog_edit_scan, null);

        TextInputEditText etTonnage   = dialogView.findViewById(R.id.etEditTonnage);
        Spinner spinnerSp             = dialogView.findViewById(R.id.spinnerEditStockpile);
        SwitchCompat switchRemark     = dialogView.findViewById(R.id.switchEditRemark);
        LinearLayout layoutRemark     = dialogView.findViewById(R.id.layoutEditRemark);
        TextInputEditText etRemTon    = dialogView.findViewById(R.id.etEditRemarkTonnage);
        TextInputEditText etRemNote   = dialogView.findViewById(R.id.etEditRemarkNote);

        // Isi data awal
        etTonnage.setText(String.valueOf(scan.getTonnage()));

        // Stockpile spinner - teks selalu hitam di background putih
        ArrayAdapter<String> spAdapter = new ArrayAdapter<String>(this,
                R.layout.item_spinner_dark, STOCKPILE_OPTIONS) {
            @Override
            public View getView(int pos, View cv, android.view.ViewGroup p) {
                View v = super.getView(pos, cv, p);
                if (v instanceof TextView) {
                    ((TextView) v).setTextColor(android.graphics.Color.parseColor("#212121"));
                    ((TextView) v).setBackgroundColor(android.graphics.Color.WHITE);
                }
                return v;
            }
            @Override
            public View getDropDownView(int pos, View cv, android.view.ViewGroup p) {
                View v = super.getDropDownView(pos, cv, p);
                if (v instanceof TextView) {
                    ((TextView) v).setTextColor(android.graphics.Color.parseColor("#212121"));
                    ((TextView) v).setBackgroundColor(android.graphics.Color.WHITE);
                    ((TextView) v).setPadding(32, 24, 32, 24);
                }
                return v;
            }
        };
        spAdapter.setDropDownViewResource(R.layout.item_spinner_dark);
        spinnerSp.setAdapter(spAdapter);
        for (int i = 0; i < STOCKPILE_OPTIONS.length; i++) {
            if (STOCKPILE_OPTIONS[i].equals(scan.getStockpile())) {
                spinnerSp.setSelection(i); break;
            }
        }

        // Remark
        switchRemark.setChecked(scan.isHasRemark());
        layoutRemark.setVisibility(scan.isHasRemark() ? View.VISIBLE : View.GONE);
        if (scan.isHasRemark()) {
            etRemTon.setText(String.valueOf(scan.getRemarkTonnage()));
            etRemNote.setText(scan.getRemarkNote());
        }
        switchRemark.setOnCheckedChangeListener((btn, checked) ->
                layoutRemark.setVisibility(checked ? View.VISIBLE : View.GONE));

        new AlertDialog.Builder(this)
                .setTitle("Edit Data Scan")
                .setMessage("Tiket: " + scan.getTicketNumber())
                .setView(dialogView)
                .setPositiveButton("Simpan", (d, w) -> {
                    try {
                        double newTon = Double.parseDouble(
                                etTonnage.getText().toString().trim());
                        String newSp = STOCKPILE_OPTIONS[spinnerSp.getSelectedItemPosition()];
                        boolean hasRem = switchRemark.isChecked();
                        double remTon = 0;
                        String remNote = "";
                        if (hasRem) {
                            try { remTon = Double.parseDouble(
                                    etRemTon.getText().toString().trim()); }
                            catch (Exception ignored) {}
                            remNote = etRemNote.getText() != null
                                    ? etRemNote.getText().toString().trim() : "";
                        }

                        final double fRemTon = remTon;
                        final String fRemNote = remNote;

                        executor.execute(() -> {
                            scan.setTonnage(newTon);
                            scan.setStockpile(newSp);
                            scan.setHasRemark(hasRem);
                            scan.setRemarkTonnage(fRemTon);
                            scan.setRemarkNote(fRemNote);
                            scan.setSynced(true);
                            db.ticketScanDao().update(scan);
                            if (SyncManager.isOnline(this))
                                SyncManager.syncUpdate(this, 
                                        scan.getId(),
                                        scan.getTicketNumber(),
                                        scan.getOperationalDate(),
                                        newTon,
                                        newSp,
                                        hasRem,
                                        fRemTon,
                                        fRemNote,
                                        null);
                            mainHandler.post(() -> {
                                loadData();
                                Toast.makeText(this, "✓ Data diperbarui",
                                        Toast.LENGTH_SHORT).show();
                            });
                        });
                    } catch (Exception e) {
                        Toast.makeText(this, "Format tidak valid", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Batal", null).show();
    }

    private void showDeleteDialog(TicketScan scan) {
        new AlertDialog.Builder(this)
                .setTitle("Hapus Data")
                .setMessage("Hapus tiket " + scan.getTicketNumber() + "?\n" +
                        "Tonnase: " + scan.getTonnage() + " Ton")
                .setPositiveButton("Hapus", (d, w) -> executor.execute(() -> {
                    // Hapus dari lokal dulu
                    db.ticketScanDao().delete(scan);

                    // Kirim perintah hapus ke Sheets dengan ID + tanggal
                    if (SyncManager.isOnline(this)) {
                        SyncManager.syncDelete(this, scan.getId(), scan.getOperationalDate(),
                            new SyncManager.SyncCallback() {
                                @Override public void onSuccess(int count) {
                                    Log.d("HistoryActivity", "Hapus dari Sheets berhasil: " + scan.getTicketNumber());
                                }
                                @Override public void onError(String message) {
                                    Log.e("HistoryActivity", "Gagal hapus dari Sheets: " + message);
                                }
                            });
                    }

                    mainHandler.post(() -> {
                        loadData();
                        Toast.makeText(this, "Data dihapus", Toast.LENGTH_SHORT).show();
                    });
                }))
                .setNegativeButton("Batal", null).show();
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) { finish(); return true; }
        return super.onOptionsItemSelected(item);
    }
}
