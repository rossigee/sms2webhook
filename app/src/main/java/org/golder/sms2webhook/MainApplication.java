package org.golder.sms2webhook;

import android.app.Application;
import android.content.Context;
import android.os.Handler;

import java.util.ArrayList;

public class MainApplication extends Application {
    private MainActivity mainActivity;
    private final ArrayList<String> messages = new ArrayList<>();

    @Override
    public void onCreate() {
        super.onCreate();
    }

    public void setMainActivity(MainActivity mainActivity) {
        this.mainActivity = mainActivity;
    }

    public String[] getMessages() {
        return messages.toArray(new String[0]);
    }

    public void updateUI() {
        Context ctx = getApplicationContext();
        Handler handler = new Handler(ctx.getMainLooper());
        handler.post(() -> {
            if(mainActivity != null) {
                mainActivity.updateUI(ctx);
            }
        });
    }

    public void addMessage(String line) {
        messages.add(line);
        Context ctx = getApplicationContext();
        Handler handler = new Handler(ctx.getMainLooper());
        handler.post(() -> {
            if(mainActivity != null) {
                mainActivity.addMessage(line);
            }
        });
    }
}