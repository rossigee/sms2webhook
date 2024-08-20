package org.golder.sms2webhook;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.widget.ProgressBar;
import android.widget.TextView;

public class Statistics {
    public int inboxCount = 0;
    public int processedCount = 0;

    public String status;

    private TextView textView;
    private ProgressBar progress;

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
        if(textView != null) {
            new Activity().runOnUiThread(() -> textView.append(status));
        }
    }

    public void setInboxCount(int count) {
        this.inboxCount = count;
        if(progress != null) {
            progress.setMax(count);
        }
    }
    public void setProcessedCount(int count) {
        this.processedCount = count;
        if(progress != null) {
            progress.setProgress(count);
        }
    }
}
