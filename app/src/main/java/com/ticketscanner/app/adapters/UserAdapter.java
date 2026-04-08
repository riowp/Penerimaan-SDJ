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
import com.ticketscanner.app.models.User;

import java.util.ArrayList;
import java.util.List;

public class UserAdapter extends RecyclerView.Adapter<UserAdapter.ViewHolder> {

    public interface OnUserActionListener {
        void onEdit(User user);
        void onDelete(User user);
        void onToggleStatus(User user);
    }

    private List<User> data = new ArrayList<>();
    private OnUserActionListener listener;

    public UserAdapter(OnUserActionListener listener) {
        this.listener = listener;
    }

    public void setData(List<User> newData) {
        this.data = newData != null ? newData : new ArrayList<>();
        notifyDataSetChanged();
    }

    @NonNull @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_user, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder h, int position) {
        User user = data.get(position);

        h.tvNama.setText(user.getNamaLengkap());
        h.tvUsername.setText("@" + user.getUsername());
        h.tvRole.setText(user.getRoleLabel());

        // Warna badge role
        int roleColor;
        switch (user.getRole()) {
            case User.ROLE_ADMIN:      roleColor = Color.parseColor("#1A237E"); break;
            case User.ROLE_SUPERVISOR: roleColor = Color.parseColor("#1B5E20"); break;
            case User.ROLE_GUEST:      roleColor = Color.parseColor("#546E7A"); break;
            default:                   roleColor = Color.parseColor("#E65100"); break;
        }
        h.tvRole.setBackgroundTintList(android.content.res.ColorStateList.valueOf(roleColor));

        // Status
        boolean isAktif = "aktif".equals(user.getStatus());
        h.tvStatus.setText(isAktif ? "● Aktif" : "○ Nonaktif");
        h.tvStatus.setTextColor(isAktif ? Color.parseColor("#2E7D32") : Color.parseColor("#9E9E9E"));
        h.btnToggle.setText(isAktif ? "Nonaktifkan" : "Aktifkan");

        h.btnEdit.setOnClickListener(v -> { if (listener != null) listener.onEdit(user); });
        h.btnDelete.setOnClickListener(v -> { if (listener != null) listener.onDelete(user); });
        h.btnToggle.setOnClickListener(v -> { if (listener != null) listener.onToggleStatus(user); });
    }

    @Override public int getItemCount() { return data.size(); }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvNama, tvUsername, tvRole, tvStatus;
        MaterialButton btnEdit, btnDelete, btnToggle;

        ViewHolder(@NonNull View v) {
            super(v);
            tvNama    = v.findViewById(R.id.tvNama);
            tvUsername = v.findViewById(R.id.tvUsername);
            tvRole    = v.findViewById(R.id.tvRole);
            tvStatus  = v.findViewById(R.id.tvStatus);
            btnEdit   = v.findViewById(R.id.btnEditUser);
            btnDelete = v.findViewById(R.id.btnDeleteUser);
            btnToggle = v.findViewById(R.id.btnToggleStatus);
        }
    }
}
