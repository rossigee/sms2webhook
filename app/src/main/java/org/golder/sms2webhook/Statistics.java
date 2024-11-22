package org.golder.sms2webhook;

import android.annotation.SuppressLint;

public class Statistics {
    private int inboxCount = 0;
    private int processedCount = 0;

    private String status;

    private final Object lock = new Object();

    @SuppressLint("StaticFieldLeak")
    private static volatile Statistics instance = new Statistics();
    private static final Object monitor = new Object();

    public static Statistics getInstance() {
        return instance;
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
//        if (progress != null) {
//            progress.setMax(count);
//        }
    }

    public void setProcessedCount(int count) {
        synchronized (lock) {
            this.processedCount = count;
        }
//        if (progress != null) {
//            progress.setProgress(count);
//        }
        notifyListeners();
    }

    private void notifyListeners() {
//        if (textView == null || status == null) {
//            return;
//        }
//        textView.append(status);
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