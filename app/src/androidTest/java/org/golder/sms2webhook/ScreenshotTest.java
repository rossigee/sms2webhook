package org.golder.sms2webhook;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Environment;
import androidx.preference.PreferenceManager;
import androidx.test.core.app.ActivityScenario;
import androidx.test.espresso.Espresso;
import androidx.test.espresso.action.ViewActions;
import androidx.test.espresso.contrib.RecyclerViewActions;
import androidx.test.espresso.matcher.ViewMatchers;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.GrantPermissionRule;
import androidx.test.uiautomator.UiDevice;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.File;
import java.io.IOException;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withContentDescription;
import static org.hamcrest.Matchers.allOf;

@RunWith(AndroidJUnit4.class)
public class ScreenshotTest {
    
    @Rule
    public GrantPermissionRule permissionRule = GrantPermissionRule.grant(
            Manifest.permission.READ_SMS,
            Manifest.permission.RECEIVE_SMS
    );
    
    private Context context;
    private UiDevice device;
    private File screenshotDir;
    
    @Before
    public void setUp() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        
        // Create screenshots directory
        screenshotDir = new File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "screenshots");
        if (!screenshotDir.exists()) {
            screenshotDir.mkdirs();
        }
        
        // Clear any existing preferences
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        prefs.edit().clear().apply();
    }
    
    @Test
    public void captureAppScreenshots() throws Exception {
        // Launch MainActivity
        ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class);
        
        // Wait for app to stabilize
        Thread.sleep(2000);
        
        // Take screenshot of main screen (empty state)
        takeScreenshot("01_main_screen_empty");
        
        // Add some sample log entries by simulating activity
        scenario.onActivity(activity -> {
            // Add some sample log entries
            activity.runOnUiThread(() -> {
                try {
                    // Use reflection to access the private viewModel field
                    java.lang.reflect.Field viewModelField = MainActivity.class.getDeclaredField("viewModel");
                    viewModelField.setAccessible(true);
                    MainViewModel viewModel = (MainViewModel) viewModelField.get(activity);
                    
                    if (viewModel != null) {
                        viewModel.addLogEntry("App started", MainViewModel.LogEntry.Type.INFO);
                        viewModel.addLogEntry("Checking for new messages...", MainViewModel.LogEntry.Type.INFO);
                        viewModel.addLogEntry("No webhook URL configured", MainViewModel.LogEntry.Type.WARNING);
                    }
                } catch (Exception e) {
                    System.err.println("Could not access viewModel: " + e.getMessage());
                }
            });
        });
        
        Thread.sleep(1000);
        takeScreenshot("02_main_screen_with_logs");
        
        // Open the overflow menu
        Espresso.openActionBarOverflowOrOptionsMenu(
                InstrumentationRegistry.getInstrumentation().getTargetContext());
        
        Thread.sleep(500);
        
        // Click on settings menu item
        Espresso.onView(withText("Settings"))
                .perform(ViewActions.click());
        
        Thread.sleep(1500);
        
        // Take screenshot of settings screen
        takeScreenshot("03_settings_screen_empty");
        
        // Click on webhook URL preference
        try {
            Espresso.onView(withText("Webhook URL"))
                    .perform(ViewActions.click());
            Thread.sleep(500);
            
            // Type webhook URL
            Espresso.onView(allOf(withId(android.R.id.edit), isDisplayed()))
                    .perform(ViewActions.replaceText("https://api.example.com/sms-webhook"));
            
            // Click OK
            Espresso.onView(withText("OK"))
                    .perform(ViewActions.click());
            Thread.sleep(500);
        } catch (Exception e) {
            System.out.println("Could not set webhook URL via dialog: " + e.getMessage());
        }
        
        // Click on API key preference
        try {
            Espresso.onView(withText("API Key"))
                    .perform(ViewActions.click());
            Thread.sleep(500);
            
            // Type API key
            Espresso.onView(allOf(withId(android.R.id.edit), isDisplayed()))
                    .perform(ViewActions.replaceText("sk-1234567890abcdef"));
            
            // Click OK
            Espresso.onView(withText("OK"))
                    .perform(ViewActions.click());
            Thread.sleep(500);
        } catch (Exception e) {
            System.out.println("Could not set API key via dialog: " + e.getMessage());
        }
        
        // Take screenshot with filled settings
        takeScreenshot("04_settings_screen_filled");
        
        // Go back to main screen
        Espresso.pressBack();
        Thread.sleep(1000);
        
        // Simulate successful webhook test
        scenario.onActivity(activity -> {
            activity.runOnUiThread(() -> {
                try {
                    // Use reflection to access the private viewModel field
                    java.lang.reflect.Field viewModelField = MainActivity.class.getDeclaredField("viewModel");
                    viewModelField.setAccessible(true);
                    MainViewModel viewModel = (MainViewModel) viewModelField.get(activity);
                    
                    if (viewModel != null) {
                        viewModel.addLogEntry("Webhook URL configured: https://api.example.com/sms-webhook", MainViewModel.LogEntry.Type.SUCCESS);
                        viewModel.addLogEntry("Testing webhook connection...", MainViewModel.LogEntry.Type.INFO);
                        viewModel.addLogEntry("Webhook test successful!", MainViewModel.LogEntry.Type.SUCCESS);
                        // Note: incrementProcessedCount and incrementUploadedCount don't exist in MainViewModel
                        // Instead, let's trigger a statistics reload
                        viewModel.loadStatistics();
                    }
                } catch (Exception e) {
                    System.err.println("Could not access viewModel: " + e.getMessage());
                }
            });
        });
        
        Thread.sleep(1000);
        
        // Click the sync button to trigger a refresh
        Espresso.onView(withId(R.id.syncButton))
                .perform(ViewActions.click());
        
        Thread.sleep(1000);
        
        // Take final screenshot of main screen with activity
        takeScreenshot("05_main_screen_configured");
        
        // Close the activity
        scenario.close();
        
        System.out.println("\n=== Screenshot Test Complete ===");
        System.out.println("Screenshots saved to: " + screenshotDir.getAbsolutePath());
        System.out.println("Files created:");
        for (File file : screenshotDir.listFiles()) {
            System.out.println("  - " + file.getName());
        }
    }
    
    private void takeScreenshot(String filename) {
        File screenshotFile = new File(screenshotDir, filename + ".png");
        boolean success = device.takeScreenshot(screenshotFile);
        if (success) {
            System.out.println("Screenshot saved: " + filename + ".png");
        } else {
            System.err.println("Failed to take screenshot: " + filename);
        }
    }
}