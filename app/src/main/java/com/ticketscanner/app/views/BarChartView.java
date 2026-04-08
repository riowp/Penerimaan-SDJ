package com.ticketscanner.app.views;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

public class BarChartView extends View {

    private List<String> labels = new ArrayList<>();
    private List<Float> s1Values = new ArrayList<>();
    private List<Float> s2Values = new ArrayList<>();

    private Paint paintS1, paintS2, paintText, paintGrid, paintLabel, paintValue;

    public BarChartView(Context context) { super(context); init(); }
    public BarChartView(Context context, AttributeSet attrs) { super(context, attrs); init(); }
    public BarChartView(Context context, AttributeSet attrs, int def) { super(context, attrs, def); init(); }

    private void init() {
        paintS1 = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintS1.setColor(Color.parseColor("#1565C0"));
        paintS1.setStyle(Paint.Style.FILL);

        paintS2 = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintS2.setColor(Color.parseColor("#E65100"));
        paintS2.setStyle(Paint.Style.FILL);

        paintText = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintText.setColor(Color.parseColor("#455A64"));
        paintText.setTextSize(20f);
        paintText.setTextAlign(Paint.Align.CENTER);

        paintLabel = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintLabel.setColor(Color.parseColor("#455A64"));
        paintLabel.setTextSize(18f);

        paintGrid = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintGrid.setColor(Color.parseColor("#ECEFF1"));
        paintGrid.setStrokeWidth(1.5f);

        // Paint untuk nilai tonnase di atas bar
        paintValue = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintValue.setTextSize(18f);
        paintValue.setTextAlign(Paint.Align.CENTER);
        paintValue.setFakeBoldText(true);
    }

    public void setData(List<String> labels, List<Float> s1, List<Float> s2) {
        this.labels = labels;
        this.s1Values = s1;
        this.s2Values = s2;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (labels == null || labels.isEmpty()) return;

        canvas.drawColor(Color.WHITE);

        int n = labels.size();
        float w = getWidth();
        float h = getHeight();
        float padLeft = 60f, padRight = 16f, padTop = 40f, padBottom = 50f;
        float chartW = w - padLeft - padRight;
        float chartH = h - padTop - padBottom;

        // Hitung max value
        float maxVal = 10f;
        for (int i = 0; i < n; i++) {
            float total = (s1Values.get(i) != null ? s1Values.get(i) : 0)
                        + (s2Values.get(i) != null ? s2Values.get(i) : 0);
            if (total > maxVal) maxVal = total;
        }
        // Round up ke kelipatan 50
        maxVal = (float)(Math.ceil(maxVal / 50.0) * 50);
        if (maxVal < 50) maxVal = 50;

        // Grid lines + Y labels
        int gridLines = 5;
        for (int i = 0; i <= gridLines; i++) {
            float yVal = maxVal * i / gridLines;
            float yPos = padTop + chartH - (chartH * i / gridLines);
            canvas.drawLine(padLeft, yPos, padLeft + chartW, yPos, paintGrid);
            paintLabel.setTextAlign(Paint.Align.RIGHT);
            paintLabel.setColor(Color.parseColor("#78909C"));
            canvas.drawText(String.format("%.0f", yVal), padLeft - 4, yPos + 5, paintLabel);
        }

        // Bars
        float barGroupW = chartW / n;
        float barW = Math.min(barGroupW * 0.3f, 30f);

        for (int i = 0; i < n; i++) {
            float groupCenter = padLeft + i * barGroupW + barGroupW / 2f;
            float s1 = s1Values.get(i) != null ? s1Values.get(i) : 0;
            float s2 = s2Values.get(i) != null ? s2Values.get(i) : 0;
            float total = s1 + s2;

            // Shift 1 bar (kiri)
            if (s1 > 0) {
                float barH1 = (s1 / maxVal) * chartH;
                float l = groupCenter - barW - 2f;
                float t = padTop + chartH - barH1;
                canvas.drawRoundRect(new RectF(l, t, l + barW, padTop + chartH), 4, 4, paintS1);

                // Nilai di atas bar shift 1
                paintValue.setColor(Color.parseColor("#1565C0"));
                paintValue.setTextSize(16f);
                String s1Label = s1 >= 1000 ? String.format("%.0f", s1) : String.format("%.1f", s1);
                canvas.drawText(s1Label, l + barW / 2, t - 4, paintValue);
            }

            // Shift 2 bar (kanan)
            if (s2 > 0) {
                float barH2 = (s2 / maxVal) * chartH;
                float l = groupCenter + 2f;
                float t = padTop + chartH - barH2;
                canvas.drawRoundRect(new RectF(l, t, l + barW, padTop + chartH), 4, 4, paintS2);

                // Nilai di atas bar shift 2
                paintValue.setColor(Color.parseColor("#E65100"));
                paintValue.setTextSize(16f);
                String s2Label = s2 >= 1000 ? String.format("%.0f", s2) : String.format("%.1f", s2);
                canvas.drawText(s2Label, l + barW / 2, t - 4, paintValue);
            }

            // X label (tanggal)
            paintText.setTextAlign(Paint.Align.CENTER);
            paintText.setColor(Color.parseColor("#546E7A"));
            canvas.drawText(labels.get(i), groupCenter, h - 8f, paintText);
        }

        // Legend
        float legendY = 24f;
        float legendX = padLeft;

        canvas.drawRoundRect(new RectF(legendX, legendY - 12, legendX + 16, legendY + 2), 2, 2, paintS1);
        paintLabel.setTextAlign(Paint.Align.LEFT);
        paintLabel.setColor(Color.parseColor("#1565C0"));
        canvas.drawText("Shift 1", legendX + 20, legendY, paintLabel);

        float l2 = legendX + 80;
        canvas.drawRoundRect(new RectF(l2, legendY - 12, l2 + 16, legendY + 2), 2, 2, paintS2);
        paintLabel.setColor(Color.parseColor("#E65100"));
        canvas.drawText("Shift 2", l2 + 20, legendY, paintLabel);
    }
}
