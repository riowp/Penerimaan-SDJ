package com.ticketscanner.app.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.ticketscanner.app.R;
import com.ticketscanner.app.models.DailyStats;
import com.ticketscanner.app.utils.ShiftUtils;

import java.util.ArrayList;
import java.util.List;

public class SummaryAdapter extends RecyclerView.Adapter<SummaryAdapter.ViewHolder> {

    private List<DailyStats> data = new ArrayList<>();

    public void setData(List<DailyStats> newData) {
        this.data = newData != null ? newData : new ArrayList<>();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_summary, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        DailyStats stats = data.get(position);
        holder.tvDate.setText(ShiftUtils.formatOperationalDate(stats.getOperationalDate()));
        holder.tvShift1.setText(String.format("Shift 1: %d tiket — %.2f Ton",
                stats.getShift1Tickets(), stats.getShift1Tonnage()));
        holder.tvShift2.setText(String.format("Shift 2: %d tiket — %.2f Ton",
                stats.getShift2Tickets(), stats.getShift2Tonnage()));
        holder.tvTotal.setText(String.format("TOTAL: %d tiket | %.2f Ton",
                stats.getTotalTickets(), stats.getTotalTonnage()));
    }

    @Override
    public int getItemCount() { return data.size(); }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvDate, tvShift1, tvShift2, tvTotal;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvDate   = itemView.findViewById(R.id.tvDate);
            tvShift1 = itemView.findViewById(R.id.tvShift1);
            tvShift2 = itemView.findViewById(R.id.tvShift2);
            tvTotal  = itemView.findViewById(R.id.tvTotal);
        }
    }
}
