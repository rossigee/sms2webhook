package org.golder.sms2webhook;

import android.app.Application;
import android.content.Context;
import android.os.Handler;
import android.widget.Toast;

import java.util.ArrayList;

public class MainApplication extends Application {
    private static MainApplication singleton;

    private MainActivity mainActivity;
    private final ArrayList<String> messages = new ArrayList<>();

//    public MainApplication getInstance() {
//        return singleton;
//    }
//
    @Override
    public void onCreate() {
        super.onCreate();
        singleton = this;
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
        handler.post(new Runnable() {
            @Override
            public void run() {
                if(mainActivity != null) {
                    mainActivity.updateUI(ctx);
                }
            }
        });
    }

    public void addMessage(String line) {
        messages.add(line);
        Context ctx = getApplicationContext();
        Handler handler = new Handler(ctx.getMainLooper());
        handler.post(new Runnable() {
            @Override
            public void run() {
                if(mainActivity != null) {
                    mainActivity.addMessage(ctx, line);
                }
            }
        });
    }
}