package com.ticketscanner.app.constants;

import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Satu tempat untuk semua thread executor.
 * Sebelumnya setiap Activity punya executor sendiri-sendiri — boros memori.
 * Sekarang semua pakai instance yang sama.
 *
 * Cara pakai:
 *   AppExecutors.IO.execute(() -> { ... kerja di background ... });
 *   AppExecutors.MAIN.post(() -> { ... update UI ... });
 */
public final class AppExecutors {

    private AppExecutors() {}

    /** Background thread — untuk database, network, file I/O */
    public static final ExecutorService IO = Executors.newFixedThreadPool(3);

    /** Main/UI thread — untuk update tampilan */
    public static final Handler MAIN = new Handler(Looper.getMainLooper());

    /** Shortcut untuk run di IO lalu hasilnya di Main */
    public static void runIO(Runnable bgTask) {
        IO.execute(bgTask);
    }

    public static void runMain(Runnable uiTask) {
        MAIN.post(uiTask);
    }

    public static void runIOThenMain(Runnable bgTask, Runnable uiTask) {
        IO.execute(() -> {
            bgTask.run();
            MAIN.post(uiTask);
        });
    }
}
