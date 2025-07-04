# SMS2Webhook Minimum SDK Analysis

## Current Configuration
- **minSdk**: 28 (Android 9.0 Pie)
- **targetSdk**: 35
- **compileSdk**: 35

## Dependency Requirements

### Material Design 3
- **Dependency**: com.google.android.material:material:1.12.0
- **Minimum SDK**: API 21 (Android 5.0 Lollipop)
- **Status**: ✅ Compatible with current minSdk 28

### AndroidX WorkManager
- **Dependency**: androidx.work:work-runtime:2.10.1
- **Minimum SDK**: API 14 (Android 4.0 Ice Cream Sandwich)
- **Status**: ✅ Compatible with current minSdk 28

### AndroidX Room Database
- **Dependencies**: 
  - androidx.room:room-runtime:2.7.1
  - androidx.room:room-common:2.7.1
- **Minimum SDK**: API 14 (Android 4.0 Ice Cream Sandwich)
- **Status**: ✅ Compatible with current minSdk 28

### Other AndroidX Libraries
- **androidx.appcompat:appcompat:1.7.0** - Minimum SDK: API 14
- **androidx.constraintlayout:constraintlayout:2.2.1** - Minimum SDK: API 14
- **androidx.preference:preference:1.2.1** - Minimum SDK: API 14
- **androidx.lifecycle** components - Minimum SDK: API 14
- **androidx.recyclerview:recyclerview:1.3.2** - Minimum SDK: API 14
- **androidx.swiperefreshlayout:swiperefreshlayout:1.1.0** - Minimum SDK: API 14

## API Usage Analysis

### Direct API Calls Requiring Specific Versions

1. **Permission APIs in MainActivity.java**
   - `checkSelfPermission()` - Requires API 23
   - `requestPermissions()` - Requires API 23
   - **Current Usage**: Direct API calls without compatibility wrappers
   - **Issue**: These APIs require API 23, but the app uses them directly

2. **SMS APIs in SmsBroadcastReceiver.java**
   - `SmsMessage.createFromPdu(byte[], String format)` - Requires API 23
   - **Current Usage**: Using the two-parameter version with format
   - **Issue**: This method signature requires API 23

## Findings

### Critical Issues
1. The app is using direct API calls that require API 23:
   - `checkSelfPermission()` and `requestPermissions()` in MainActivity
   - `SmsMessage.createFromPdu(byte[], String)` in SmsBroadcastReceiver

2. However, the minSdk is set to 28, which is higher than API 23, so these APIs are available.

### Actual Minimum SDK Requirements

Based on the analysis:
- **Dependencies alone**: The app could work with API 14 (Android 4.0)
- **Direct API usage**: The app requires at least API 23 (Android 6.0)
- **Current minSdk 28**: More than sufficient for all APIs used

## Recommendations

### If you want to lower minSdk below 28:

1. **To support API 23-27** (Android 6.0-8.1):
   - No code changes needed
   - All APIs used are available from API 23+
   - Simply change minSdk to 23 in build.gradle

2. **To support API 21-22** (Android 5.0-5.1):
   - Replace direct permission calls with compatibility versions:
     ```java
     // Instead of: checkSelfPermission(permission)
     ContextCompat.checkSelfPermission(this, permission)
     
     // Instead of: requestPermissions(permissions, code)
     ActivityCompat.requestPermissions(this, permissions, code)
     ```
   - Handle SMS parsing conditionally:
     ```java
     if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
         sms = SmsMessage.createFromPdu((byte[])pdus[0], format);
     } else {
         sms = SmsMessage.createFromPdu((byte[])pdus[0]);
     }
     ```

3. **To support API 14-20** (Android 4.0-4.4):
   - Same changes as above
   - Note: Material Design 3 requires API 21, so you'd need to use older Material Components

### Conclusion

The app's current minSdk of 28 is higher than necessary. The absolute minimum SDK the app needs is:
- **API 23** with current code
- **API 21** with minor compatibility changes
- **API 14** with compatibility changes and older Material Design library

The decision to use minSdk 28 appears to be a conservative choice that ensures compatibility with more recent Android features and avoids the need for compatibility code.