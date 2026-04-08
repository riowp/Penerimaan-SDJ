package com.ticketscanner.app.activities;

import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.ticketscanner.app.R;
import com.ticketscanner.app.adapters.UserAdapter;
import com.ticketscanner.app.models.User;
import com.ticketscanner.app.utils.SessionManager;
import com.ticketscanner.app.utils.UserManager;

import java.util.ArrayList;
import java.util.List;

public class UserManagementActivity extends BaseActivity {

    private RecyclerView rvUsers;
    private ProgressBar progressBar;
    private TextView tvEmpty;
    private MaterialButton btnAddUser;

    private UserAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_user_management);

        // Guest tidak boleh akses halaman ini sama sekali
        if (isGuest()) {
            android.widget.Toast.makeText(this,
                "Akses ditolak — role Guest tidak bisa mengelola user",
                android.widget.Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Kelola User");
        }

        rvUsers     = findViewById(R.id.rvUsers);
        progressBar = findViewById(R.id.progressBar);
        tvEmpty     = findViewById(R.id.tvEmpty);
        btnAddUser  = findViewById(R.id.btnAddUser);

        adapter = new UserAdapter(new UserAdapter.OnUserActionListener() {
            @Override public void onEdit(User user)   { showEditDialog(user); }
            @Override public void onDelete(User user) { showDeleteDialog(user); }
            @Override public void onToggleStatus(User user) { toggleStatus(user); }
        });

        rvUsers.setLayoutManager(new LinearLayoutManager(this));
        rvUsers.setAdapter(adapter);

        btnAddUser.setOnClickListener(v -> showAddDialog());
        loadUsers();
    }

    private void loadUsers() {
        progressBar.setVisibility(View.VISIBLE);
        tvEmpty.setVisibility(View.GONE);

        UserManager.getAllUsers(new UserManager.UserListCallback() {
            @Override public void onSuccess(List<User> users) {
                progressBar.setVisibility(View.GONE);
                if (users.isEmpty()) {
                    tvEmpty.setVisibility(View.VISIBLE);
                } else {
                    adapter.setData(users);
                }
            }
            @Override public void onError(String message) {
                progressBar.setVisibility(View.GONE);
                Toast.makeText(UserManagementActivity.this, "Error: " + message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showAddDialog() {
        showUserDialog(null);
    }

    private void showEditDialog(User user) {
        showUserDialog(user);
    }

    private void showUserDialog(User existingUser) {
        View view = getLayoutInflater().inflate(R.layout.dialog_user, null);
        TextInputEditText etUsername    = view.findViewById(R.id.etDialogUsername);
        TextInputEditText etPassword    = view.findViewById(R.id.etDialogPassword);
        TextInputEditText etNama        = view.findViewById(R.id.etDialogNama);
        Spinner           spinnerRole   = view.findViewById(R.id.spinnerRole);

        String[] roles      = {"operator", "supervisor", "admin", "guest"};
        String[] roleLabels = {"Operator", "Supervisor", "Admin", "Guest (View Only)"};

        // Custom adapter agar teks selalu hitam di background putih (fix dark mode)
        android.widget.ArrayAdapter<String> roleAdapter =
            new android.widget.ArrayAdapter<String>(this,
                R.layout.item_spinner_dark, roleLabels) {
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
        roleAdapter.setDropDownViewResource(R.layout.item_spinner_dark);
        spinnerRole.setAdapter(roleAdapter);

        boolean isEdit = existingUser != null;
        if (isEdit) {
            etUsername.setText(existingUser.getUsername());
            etUsername.setEnabled(false); // tidak bisa ubah username
            etNama.setText(existingUser.getNamaLengkap());
            etPassword.setHint("Kosongkan jika tidak diubah");
            // Set role spinner
            for (int i = 0; i < roles.length; i++) {
                if (roles[i].equals(existingUser.getRole())) {
                    spinnerRole.setSelection(i);
                    break;
                }
            }
        }

        new AlertDialog.Builder(this)
                .setTitle(isEdit ? "Edit User" : "Tambah User Baru")
                .setView(view)
                .setPositiveButton(isEdit ? "Simpan" : "Tambah", (d, w) -> {
                    String username = etUsername.getText() != null ? etUsername.getText().toString().trim() : "";
                    String password = etPassword.getText() != null ? etPassword.getText().toString().trim() : "";
                    String nama     = etNama.getText() != null ? etNama.getText().toString().trim() : "";
                    String role     = roles[spinnerRole.getSelectedItemPosition()];

                    if (username.isEmpty()) { Toast.makeText(this, "Username wajib diisi", Toast.LENGTH_SHORT).show(); return; }
                    if (!isEdit && password.isEmpty()) { Toast.makeText(this, "Password wajib diisi", Toast.LENGTH_SHORT).show(); return; }
                    if (nama.isEmpty()) { Toast.makeText(this, "Nama wajib diisi", Toast.LENGTH_SHORT).show(); return; }

                    // Tidak boleh hapus/downgrade akun admin sendiri
                    if (isEdit && username.equals(session.getUsername())
                            && !role.equals(User.ROLE_ADMIN)) {
                        Toast.makeText(this, "Tidak bisa mengubah role akun sendiri", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    String finalPassword = (isEdit && password.isEmpty())
                            ? existingUser.getPassword() : password;

                    User user = new User(username, finalPassword, nama, role, "aktif");

                    UserManager.ActionCallback cb = new UserManager.ActionCallback() {
                        @Override public void onSuccess(String msg) {
                            Toast.makeText(UserManagementActivity.this,
                                    isEdit ? "User diperbarui" : "User ditambahkan", Toast.LENGTH_SHORT).show();
                            loadUsers();
                        }
                        @Override public void onError(String message) {
                            Toast.makeText(UserManagementActivity.this, "Gagal: " + message, Toast.LENGTH_SHORT).show();
                        }
                    };

                    if (isEdit) UserManager.updateUser(user, cb);
                    else        UserManager.addUser(user, cb);
                })
                .setNegativeButton("Batal", null)
                .show();
    }

    private void showDeleteDialog(User user) {
        if (user.getUsername().equals(session.getUsername())) {
            Toast.makeText(this, "Tidak bisa hapus akun sendiri", Toast.LENGTH_SHORT).show();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("Hapus User")
                .setMessage("Hapus user " + user.getNamaLengkap() + " (" + user.getUsername() + ")?")
                .setPositiveButton("Hapus", (d, w) ->
                        UserManager.deleteUser(user.getUsername(), new UserManager.ActionCallback() {
                            @Override public void onSuccess(String msg) {
                                Toast.makeText(UserManagementActivity.this, "User dihapus", Toast.LENGTH_SHORT).show();
                                loadUsers();
                            }
                            @Override public void onError(String message) {
                                Toast.makeText(UserManagementActivity.this, "Gagal: " + message, Toast.LENGTH_SHORT).show();
                            }
                        }))
                .setNegativeButton("Batal", null)
                .show();
    }

    private void toggleStatus(User user) {
        String newStatus = "aktif".equals(user.getStatus()) ? "nonaktif" : "aktif";
        user.setStatus(newStatus);
        UserManager.updateUser(user, new UserManager.ActionCallback() {
            @Override public void onSuccess(String msg) {
                Toast.makeText(UserManagementActivity.this,
                        "Status diubah ke " + newStatus, Toast.LENGTH_SHORT).show();
                loadUsers();
            }
            @Override public void onError(String message) {
                Toast.makeText(UserManagementActivity.this, "Gagal: " + message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) { finish(); return true; }
        return super.onOptionsItemSelected(item);
    }
}
