# Wave Share - Bluetooth File Transfer App

A Kotlin-based Android application built with Jetpack Compose that allows users to scan nearby Bluetooth devices and receive files securely. The app includes necessary permission handling for Android 12+ and ensures user privacy through time-limited visibility.

---

## 🛠 Tech Stack

- **Language**: Kotlin  
- **UI**: Jetpack Compose  
- **Minimum Android Version**: API 21 (Android 6)  
- **Target Android Version**: (API 32) Android 12+
- **Maximum Android Version**: (API 35) Android 15

---

## 📂 Project Structure

```
├── MainActivity.kt
├── Navigation
│   └── Navigation.kt
└── Screens
    ├── DeviceListScreen.kt
    └── ReceiveScreen.kt
```

---

## 📱 Features

### A. Main Activity
1. **Permission Handling**  
   - Requests permissions for Bluetooth, file storage, and location (for device scanning).  
   - For Android 12+, it also requests **BLUETOOTH_ADVERTISE** permission to comply with updated Bluetooth API access.  
   - Follows modern Android permission flow with dynamic checks.

2. **Initial Screen Navigation**  
   - Once permissions are granted, the user is redirected to the **Device List Screen**, which serves as the app's dashboard.

---

### B. Navigation
- Defined in `Navigation.kt`
- Manages routing between screens
- **Initial Route**: DeviceListScreen

---

### C. Screens
1. **DeviceListScreen**  
   - Displays a list of available Bluetooth devices for connection.
   - Acts as the dashboard/home screen.

2. **ReceiveScreen**  
   - Interface for receiving files via Bluetooth.

---

### D. Privacy Considerations
- For Android 11 and above, the app only allows Bluetooth scanning/visibility for **300 seconds (5 minutes)** to protect user privacy.

---

## 🔐 Permissions Required

| Permission | Purpose |
|------------|---------|
| `BLUETOOTH_SCAN` | Scanning nearby devices |
| `BLUETOOTH_CONNECT` | Establishing connections |
| `BLUETOOTH_ADVERTISE` | Required for Android 12+ |
| `ACCESS_FINE_LOCATION` | Required for scanning on some Android versions |
| `READ_EXTERNAL_STORAGE` | File access for receiving data |

---

## 📌 Notes
- Make sure to enable Bluetooth and Location on the device before testing.
- The app behavior might vary slightly based on Android version due to permission changes in Android 12.

## 🧑‍💻 Developed by Rohit Using Kotlin & Jetpack Compose