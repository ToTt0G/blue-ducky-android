# 🦆 BlueDucky for Android

**BlueDucky** is a non-root Android application written in Kotlin and Jetpack Compose that turns your smartphone into a wireless Bluetooth Virtual Keyboard. It connects to target devices (like a Windows PC, Mac, or Linux machine) via the Android `BluetoothHidDevice` API and executes **DuckyScript** payloads just like an actual USB Rubber Ducky.

## ✨ Features

- **No Root Required**: Built fully on standard Android APIs (API 28+ / Android 9 and newer).
- **Wireless Execution**: Triggers DuckyScript commands securely over Bluetooth without needing to plug in physical hardware.
- **Modern UI**: Designed with Jetpack Compose featuring a beautiful Material Design 3 interface, dark mode, and dynamic status indicators.
- **Built-in Editor**: Write, test, and tweak your scripts directly on your phone.
- **File Management**: Load from or save `.txt` DuckyScript payload files directly to your phone's storage.
- **Intelligent Bluetooth Scanning**: Built-in BLE/Classic device discovery allows you to search for and initiate a connection directly from the app.
- **International Keyboard Support**: The payload parser is uniquely configured for the **Swedish (ISO-SE) keyboard layout**, mapping symbols, numbers, and ÅÄÖ correctly when the target computer is expecting a Swedish keyboard.

## 🚀 How to Use

### 1. Requirements
* An Android Phone running Android 9.0 (Pie) or higher.
* Bluetooth enabled on both your phone and your target host (e.g., your laptop).

### 2. Pairing & Connection
**Crucial Step:** Because BlueDucky acts as a human interface device, Windows and other OSes are very strict about the order of connection. 

If your devices are *already paired*, you should unpair/forget them first to ensure a clean HID handshake.

1. Turn on Bluetooth on your target computer (e.g., open *Bluetooth & devices* settings on Windows). Ensure your PC is discoverable.
2. Open BlueDucky on your Android phone and grant any requested permissions (Bluetooth, Location).
3. Tap **Enable & Register Keyboard**. Wait until the status changes to *Registered*.
4. Tap **Select Host Device**. 
5. Tap the **Refresh / Scan** icon in the dialog to search for your computer.
6. Select your computer from the list. Accept the pairing PIN on *both* the PC and the phone.
7. Wait for the app status to change to **Connected** (Green).

### 3. Executing DuckyScript
1. Once connected, open a text editor (like Notepad) on your target computer and make sure the window is focused.
2. Type a script into the BlueDucky Editor. Example:
   ```text
   DELAY 1000
   GUI r
   DELAY 200
   STRING notepad
   ENTER
   DELAY 500
   STRING Hello World from BlueDucky!
   ENTER
   ```
3. Tap **Execute Payload**. 
4. Watch the script run wirelessly!

## ⌨️ DuckyScript Capabilities

The interpreter currently supports the most important core features of DuckyScript 1.0:
- `STRING <text>` / `STRINGLN <text>` (Types out sequential keystrokes)
- `DELAY <ms>` (Pauses execution for a specified duration)
- `DEFAULTDELAY <ms>` (Sets a default delay between every executed keystroke/command)
- `ENTER`, `SPACE`, `TAB`, `ESCAPE`, `BACKSPACE`, etc.
- Keys combined with Modifiers (`GUI`, `WINDOWS`, `CTRL`, `ALT`, `SHIFT`)
- **Note:** `CHAR_MAP` mapping is optimized for target systems utilizing the **Swedish** input layout. 

## 🛠️ Building the Project

1. Clone the repository: `git clone https://github.com/ToTt0G/blue-ducky-android.git`
2. Open the project in **Android Studio** (Iguana or newer recommended).
3. Let Gradle sync and download the required dependencies.
4. Hit **Run** (`Shift + F10`) to compile and install the debug APK onto your connected device.

You can also rely on the included GitHub Actions workflow which automatically builds a debug APK on every push to `main`.

## ⚠️ Disclaimer

This tool is created for educational and authorized penetration testing purposes only. You should strictly only test scripts against your own devices or devices you have explicitly been given permission to test.
