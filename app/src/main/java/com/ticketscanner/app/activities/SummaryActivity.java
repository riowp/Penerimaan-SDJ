package com.ticketscanner.app.activities;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.ticketscanner.app.R;
import com.ticketscanner.app.database.AppDatabase;
import com.ticketscanner.app.utils.ShiftUtils;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SummaryActivity extends BaseActivity {

    private Spinner spinnerRange;
    private com.ticketscanner.app.views.BarChartView barChart;
    private TextView tvGrandTotal, tvShift1Total, tvShift2Total, tvAvgPerDay;

    private AppDatabase db;
    private ExecutorService executor = Executors.newSingleThreadExecutor();
    private Handler mainHandler = new Handler(Looper.getMainLooper());

    private int rangeDays = 7;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_summary);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Ringkasan");
        }

        db = AppDatabase.getInstance(this);

        spinnerRange  = findViewById(R.id.spinnerRange);
        barChart      = findViewById(R.id.barChart);
        tvGrandTotal  = findViewById(R.id.tvGrandTotal);
        tvShift1Total = findViewById(R.id.tvShift1Total);
        tvShift2Total = findViewById(R.id.tvShift2Total);
        tvAvgPerDay   = findViewById(R.id.tvAvgPerDay);

        String[] ranges = {"7 Hari Terakhir", "14 Hari Terakhir", "30 Hari Terakhir"};
        android.widget.ArrayAdapter<String> rangeAdapter =
            new android.widget.ArrayAdapter<String>(this,
                R.layout.item_spinner_dark, ranges) {
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
        rangeAdapter.setDropDownViewResource(R.layout.item_spinner_dark);
        spinnerRange.setAdapter(rangeAdapter);
        spinnerRange.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                rangeDays = pos == 0 ? 7 : pos == 1 ? 14 : 30;
                loadData();
            }
            @Override public void onNothingSelected(AdapterView<?> p) {}
        });

        loadData();
    }

    private void loadData() {
        executor.execute(() -> {
            // Generate daftar tanggal dalam range
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            List<String> dates = new ArrayList<>();
            List<String> labels = new ArrayList<>();
            SimpleDateFormat labelFmt = new SimpleDateFormat("dd/MM", Locale.getDefault());

            Calendar cal = Calendar.getInstance();
            // Mulai dari hari ini mundur sesuai range
            for (int i = rangeDays - 1; i >= 0; i--) {
                Calendar c = (Calendar) cal.clone();
                c.add(Calendar.DATE, -i);
                dates.add(sdf.format(c.getTime()));
                labels.add(labelFmt.format(c.getTime()));
            }

            // Ambil tonnase per hari
            List<Float> s1Values = new ArrayList<>();
            List<Float> s2Values = new ArrayList<>();
            double grandTotal = 0, s1Total = 0, s2Total = 0;
            int activeDays = 0;

            for (String date : dates) {
                double s1 = db.ticketScanDao().getTonnageByDateAndShift(date, 1);
                double s2 = db.ticketScanDao().getTonnageByDateAndShift(date, 2);
                s1Values.add((float) s1);
                s2Values.add((float) s2);
                s1Total += s1;
                s2Total += s2;
                grandTotal += s1 + s2;
                if (s1 + s2 > 0) activeDays++;
            }

            final double finalGrand = grandTotal;
            final double finalS1 = s1Total;
            final double finalS2 = s2Total;
            final double avg = activeDays > 0 ? grandTotal / activeDays : 0;

            mainHandler.post(() -> {
                barChart.setData(labels, s1Values, s2Values);
                tvGrandTotal.setText(String.format("%.2f Ton", finalGrand));
                tvShift1Total.setText(String.format("%.2f Ton", finalS1));
                tvShift2Total.setText(String.format("%.2f Ton", finalS2));
                tvAvgPerDay.setText(String.format("%.2f Ton/hari", avg));
            });
        });
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) { finish(); return true; }
        return super.onOptionsItemSelected(item);
    }
}
