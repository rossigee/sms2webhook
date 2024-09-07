package org.golder.sms2webhook;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.widget.ProgressBar;
import android.widget.TextView;

public class Statistics {
    private int inboxCount = 0;
    private int processedCount = 0;

    private String status;

    private TextView textView;
    private ProgressBar progress;

    private final Object lock = new Object();

    @SuppressLint("StaticFieldLeak")
    private static volatile Statistics instance;
    private static final Object monitor = new Object();

    public static Statistics getInstance() {
        if (instance == null) {
            synchronized (monitor) {
                if (instance == null) {
                    instance = new Statistics();
                }
            }
        }
        return instance;
    }

    public void setWidgets(TextView textView, ProgressBar progress) {
        this.textView = textView;
        this.progress = progress;
    }

    public void setStatus(String status) {
        this.status = status;
        notifyListeners();
    }

    public void setInboxCount(int count) {
        synchronized (lock) {
        this.inboxCount = count;
        }
        notifyListeners();
        if (progress != null) {
            progress.setMax(count);
        }
    }

    public void setProcessedCount(int count) {
        synchronized (lock) {
            this.processedCount = count;
}
        if (progress != null) {
            progress.setProgress(count);
        }
        notifyListeners();
    }

    private void notifyListeners() {
        if (textView != null) {
            new Activity().runOnUiThread(() -> textView.append(status));
        } else {
            textView = null;
        }
    }

    public int getInboxCount() {
        synchronized (lock) {
            return inboxCount;
        }
    }

    public int getProcessedCount() {
        synchronized (lock) {
            return processedCount;
        }
    }
}