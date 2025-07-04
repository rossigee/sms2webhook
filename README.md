# sms2webhook

A simple Android app to dump SMS history to an HTTP webhook.

## Screenshots

<div align="center">
  <img src="screenshots/01_main_screen_empty.png" width="30%" alt="Main screen - empty state" />
  <img src="screenshots/02_main_screen_with_logs.png" width="30%" alt="Main screen with activity logs" />
  <img src="screenshots/05_main_screen_configured.png" width="30%" alt="Main screen after configuration" />
</div>

<div align="center">
  <img src="screenshots/03_settings_screen_empty.png" width="30%" alt="Settings screen - empty" />
  <img src="screenshots/04_settings_screen_filled.png" width="30%" alt="Settings screen - configured" />
</div>

The webhook sent looks something like this...

```json
{
  "_id": "2",
  "thread_id": "2",
  "address": "6505551212",
  "date": 1724154171042,
  "date_sent": 1724154170000,
  "protocol": "0",
  "read": "0",
  "status": "-1",
  "type": "1",
  "reply_path_present": "0",
  "body": "This is the message body",
  "locked": "0",
  "sub_id": "1",
  "error_code": "0",
  "creator": "com.google.android.apps.messaging",
  "seen": "1"
}
```
