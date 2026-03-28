package com.ticketscanner.app.adapters;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.ticketscanner.app.R;
import com.ticketscanner.app.models.TicketScan;
import com.ticketscanner.app.utils.ShiftUtils;

import java.util.ArrayList;
import java.util.List;

public class ScanAdapter extends RecyclerView.Adapter<ScanAdapter.ViewHolder> {

    public interface OnActionListener {
        void onEdit(TicketScan scan);
        void onDelete(TicketScan scan);
    }

    private List<TicketScan> data = new ArrayList<>();
    private OnActionListener listener;
    private boolean canEdit = true; // default: bisa edit/hapus

    public void setData(List<TicketScan> newData) {
        this.data = newData != null ? newData : new ArrayList<>();
        notifyDataSetChanged();
    }

    public void setOnActionListener(OnActionListener l) { this.listener = l; }

    /** Panggil ini setelah inisialisasi adapter untuk guest role */
    public void setCanEdit(boolean canEdit) {
        this.canEdit = canEdit;
        notifyDataSetChanged();
    }

    @NonNull @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_scan, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder h, int position) {
        TicketScan scan = data.get(position);

        h.tvNo.setText(String.valueOf(position + 1));
        h.tvTicket.setText(scan.getTicketNumber());
        h.tvTonnage.setText(String.format("%.2f Ton", scan.getTonnage()));
        h.tvTime.setText(ShiftUtils.formatDateTime(scan.getScanTimestamp()));
        h.tvOperator.setText(scan.getOperator() != null && !scan.getOperator().isEmpty()
            ? scan.getOperator() : "-");
        h.tvSyncStatus.setText(scan.isSynced() ? "☁✓" : "⏳");
        h.tvSyncStatus.setTextColor(scan.isSynced() ? Color.parseColor("#2E7D32") : Color.parseColor("#E65100"));

        // Stockpile badge
        String sp = scan.getStockpile();
        if (sp != null && !sp.isEmpty()) {
            h.tvStockpile.setVisibility(View.VISIBLE);
            h.tvStockpile.setText(sp);
        } else {
            h.tvStockpile.setVisibility(View.GONE);
        }

        // Shift badge
        h.tvShift.setText("Shift " + scan.getShiftNumber());
        int badgeColor = scan.getShiftNumber() == 1 ? Color.parseColor("#1565C0") : Color.parseColor("#E65100");
        h.tvShift.setBackgroundTintList(android.content.res.ColorStateList.valueOf(badgeColor));

        h.btnEdit.setOnClickListener(v -> { if (listener != null) listener.onEdit(scan); });
        h.btnDelete.setOnClickListener(v -> { if (listener != null) listener.onDelete(scan); });

        // Sembunyikan tombol edit/hapus untuk guest
        int btnVisibility = canEdit ? View.VISIBLE : View.GONE;
        h.btnEdit.setVisibility(btnVisibility);
        h.btnDelete.setVisibility(btnVisibility);
    }

    @Override public int getItemCount() { return data.size(); }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvNo, tvTicket, tvTonnage, tvShift, tvTime, tvSyncStatus, tvStockpile, tvOperator;
        MaterialButton btnEdit, btnDelete;

        ViewHolder(@NonNull View v) {
            super(v);
            tvNo         = v.findViewById(R.id.tvNo);
            tvTicket     = v.findViewById(R.id.tvTicket);
            tvTonnage    = v.findViewById(R.id.tvTonnage);
            tvShift      = v.findViewById(R.id.tvShift);
            tvTime       = v.findViewById(R.id.tvTime);
            tvSyncStatus = v.findViewById(R.id.tvSyncStatus);
            tvStockpile  = v.findViewById(R.id.tvStockpile);
            tvOperator   = v.findViewById(R.id.tvOperator);
            btnEdit      = v.findViewById(R.id.btnEdit);
            btnDelete    = v.findViewById(R.id.btnDelete);
        }
    }
}
