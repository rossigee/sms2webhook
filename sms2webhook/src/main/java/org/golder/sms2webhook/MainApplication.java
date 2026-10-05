package org.golder.sms2webhook;

import android.app.Application;
import android.os.Handler;

/**
 * Bridges the sync worker, which runs off the main thread, to the activity.
 *
 * <p>The activity reference is dropped in {@code MainActivity.onDestroy}. Holding
 * it for the life of the process leaked the activity and every view it owns, and
 * because every method below posts to the main looper and dereferences it, a
 * destroyed activity kept receiving callbacks.
 */
public class MainApplication extends Application {

    /**
     * Bound to the main looper once. Allocating a Handler per call meant a fresh
     * allocation for every log line of every synced message.
     */
    private static final Handler MAIN_HANDLER = new Handler(android.os.Looper.getMainLooper());

    private MainActivity mainActivity;

    public void setMainActivity(MainActivity mainActivity) {
        this.mainActivity = mainActivity;
    }

    public void updateStats() {
        refreshStats(true);
    }

    /**
     * Refreshes the displayed counts.
     *
     * @param withDiagnostics run the consistency checks, which append to the
     *        activity log. Off for the periodic refresh during a sync so the
     *        same finding is not reported once per batch of messages.
     */
    public void refreshStats(boolean withDiagnostics) {
        MAIN_HANDLER.post(() -> {
            MainActivity activity = mainActivity;
            if (activity != null) {
                activity.refreshStats(withDiagnostics);
            }
        });
    }

    /**
     * Reports how far a sync has got.
     *
     * <p>Separate from {@link #updateStats()} because that re-queries the cache
     * and the SMS provider. Doing that per message made a sync spend its time in
     * the database rather than uploading, and each result could be coalesced away
     * by LiveData before it was ever drawn.
     */
    public void reportSyncProgress(int processed, int total) {
        MAIN_HANDLER.post(() -> {
            MainActivity activity = mainActivity;
            if (activity != null) {
                activity.reportSyncProgress(processed, total);
            }
        });
    }

    public void addMessage(String line) {
        MAIN_HANDLER.post(() -> {
            MainActivity activity = mainActivity;
            if (activity != null) {
                activity.addMessage(line);
            }
        });
    }
}