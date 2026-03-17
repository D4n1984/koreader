# KoReader Android Widget - Complete Integration Guide

## Overview

This document provides a complete roadmap for integrating the 3x2 Reading Progress Widget into KoReader's Android build system.

## Deliverables

### 1. Android Widget Provider (Java)
- **File:** `platform/android/KoReaderWidget.java`
- **Purpose:** Main widget provider that displays reading progress
- **Extends:** `AppWidgetProvider`
- **Responsibilities:**
  - Loads widget data from `widget_data.json`
  - Creates RemoteViews layout
  - Renders progress bars (black/white for e-ink)
  - Handles tap-to-open events

### 2. Widget Layout (XML)
- **File:** `platform/android/res/layout/widget_reading_progress.xml`
- **Purpose:** 3x2 grid layout with 2 book slots
- **Features:**
  - 2 symmetric book containers
  - Title (max 2 lines, truncated)
  - Author (1 line)
  - Progress bar (black on white)
  - Percentage + last read time
  - E-ink optimized (no gradients, pure b/w)

### 3. Widget Configuration (XML)
- **File:** `platform/android/res/xml/widget_provider.xml`
- **Purpose:** AppWidgetProvider metadata
- **Configures:**
  - Widget dimensions (3x2 grid cells)
  - Update frequency (1 hour default)
  - Layout resource
  - Preview for widget picker

### 4. Drawables (XML)
- **File:** `platform/android/res/drawable/widget_slot_background.xml`
  - Simple black stroke border, white fill (e-ink compatible)
- **File:** `platform/android/res/drawable/widget_preview.xml`
  - Widget picker preview appearance

### 5. Data Bridge (Lua)
- **File:** `frontend/android_widget_bridge.lua`
- **Purpose:** Exports reading progress to JSON
- **Functionality:**
  - Reads reading history and document settings
  - Extracts title, author, progress, last-read time
  - Exports 2 most recent in-progress books
  - Writes atomic JSON to `widget_data.json`
  - Called on app lifecycle events

### 6. Manifest Integration
- **File:** `platform/android/AndroidManifest.snippet.xml`
- **Contents:** Manifest snippets to integrate into project's AndroidManifest.xml

## Build System Integration

### Step 1: Add Java Source Path

In your Android Gradle build file (`build.gradle` or module `build.gradle`):

```gradle
android {
    sourceSets {
        main {
            java {
                srcDirs += 'platform/android'
            }
        }
    }
}
```

### Step 2: Add Resource Directories

In your Android Gradle build file:

```gradle
android {
    sourceSets {
        main {
            res.srcDirs += [
                'platform/android/res/layout',
                'platform/android/res/xml',
                'platform/android/res/drawable'
            ]
        }
    }
}
```

### Step 3: Update AndroidManifest.xml

Add these entries to your project's `AndroidManifest.xml`:

#### Permissions (in `<manifest>` element):
```xml
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" />
```

#### Widget Provider (in `<application>` element):
```xml
<receiver
    android:name="rocks.koreader.widget.KoReaderWidget"
    android:label="KoReader Reading Progress"
    android:icon="@mipmap/ic_launcher"
    android:exported="true">
    <intent-filter>
        <action android:name="android.appwidget.action.APPWIDGET_UPDATE" />
    </intent-filter>
    <meta-data
        android:name="android.appwidget.provider"
        android:resource="@xml/widget_provider" />
</receiver>
```

### Step 4: Package References

The Java code references these resources via R.java (auto-generated):
- `R.layout.widget_reading_progress` — ✅ Provided
- `R.id.book1_title`, `R.id.book1_author`, etc. — ✅ Auto-generated from layout XML
- `R.drawable.widget_slot_background` — ✅ Provided
- `R.xml.widget_provider` — ✅ Provided

No additional R.java configuration needed.

### Step 5: JSON Library

Ensure your Android project includes JSON support. Add to `build.gradle`:

```gradle
dependencies {
    implementation 'org.json:json:20201115'  // or later
}
```

The Java code uses `org.json.JSONObject` and `org.json.JSONArray`.

## File Checklist

Before building, verify all files are in place:

```
platform/android/
├── KoReaderWidget.java ✓
├── AndroidManifest.snippet.xml ✓
├── RUNTIME_INTEGRATION.md ✓
├── INTEGRATION_GUIDE.md (this file)✓
└── res/
    ├── layout/
    │   └── widget_reading_progress.xml ✓
    ├── xml/
    │   └── widget_provider.xml ✓
    └── drawable/
        ├── widget_slot_background.xml ✓
        └── widget_preview.xml ✓

frontend/
└── android_widget_bridge.lua ✓
```

## Runtime Integration

Add Lua widget bridge calls to app lifecycle handlers. See `RUNTIME_INTEGRATION.md` for details.

**Quick integration:**

In `frontend/apps/reader/readerui.lua` or equivalent:

```lua
function ReaderUI:onClose()
    -- ... existing close logic ...
    
    if util.isAndroid() then
        local widget_bridge = require("frontend/android_widget_bridge")
        widget_bridge:updateWidgetData()
    end
    
    -- ... rest of close logic ...
end
```

## Data Flow

```
KoReader App Running
      ↓
   [Document Open/Close Events]
      ↓
   [android_widget_bridge.lua]
      ↓
   [Reads history.lua + docsettings]
      ↓
   [Extracts 2 most recent books]
      ↓
   [Writes widget_data.json]
      ↓
   [Android System]
      ↓
   [KoReaderWidget.java]
      ↓
   [Reads widget_data.json]
      ↓
   [Renders RemoteViews]
      ↓
   [Home Screen Widget Display]
```

## Testing Checklist

### Before Building
- [ ] All Java/XML/Lua files are syntactically correct
- [ ] Package names match: `rocks.koreader.widget.KoReaderWidget`
- [ ] Class extends `AppWidgetProvider`
- [ ] Resource IDs match between Java and XML
- [ ] Gradle includes JSON dependency

### After Building / Installation
- [ ] App compiles without errors
- [ ] APK installs on device (API 24+)
- [ ] Widget appears in widget picker (long-press home screen)
- [ ] Widget can be added to home screen
- [ ] Widget displays "No books" initially (if no data yet)

### After Opening a Book
- [ ] Close KoReader (triggers widget data export)
- [ ] Open widget on home screen
- [ ] Verify widget shows 2 most recent books
- [ ] Verify titles, authors, progress percentages display
- [ ] Verify progress bar renders (black/white, no errors)
- [ ] Verify last-read times display correctly

### Tap-to-Open Test
- [ ] Long-press widget book title
- [ ] Tap book in widget
- [ ] KoReader app opens (or book opens in reader)

### Edge Cases
- [ ] Widget displayed when ≤1 book in history → Shows "No books" gracefully
- [ ] Widget displayed when corrupted `widget_data.json` → Shows "No books"
- [ ] App uninstalled/reinstalled → Widget updates successfully
- [ ] External storage unavailable → Widget shows "No books" (no crash)

## Performance Considerations

### File I/O
- Widget data written atomically to `widget_data.json`
- One write per app close/update (not per page turn)
- Minimal data (only 2 books × ~5 fields)

### Widget Updates
- Default refresh: 1 hour (configurable in `widget_provider.xml`)
- Custom broadcast receiver for manual updates available
- Progress bar rendered programmatically (no image assets)

### E-ink Optimization
- Black/white rendering only (no colors, gradients, animations)
- Simple rectangular progress bar
- Large, readable text (8sp minimum)
- High contrast black on white background
- Suitable for Boox Note Air5 Color and similar devices

## Troubleshooting

### Widget Doesn't Appear in Widget Picker
- Verify `android:exported="true"` in manifest
- Check that `<intent-filter>` is present
- Verify `android:name="rocks.koreader.widget.KoReaderWidget"` matches Java class

### Widget Shows No Books
- Open a book in KoReader and close it (triggers data export)
- Check `adb shell cat /storage/emulated/0/koreader/widget_data.json` exists
- Check KoReader logs: `adb logcat | grep "AndroidWidgetBridge"`

### Widget Crashes
- Check Android logs: `adb logcat | grep KoReaderWidget`
- Verify `widget_data.json` is valid JSON
- Verify resource IDs in Java match XML

### Tap-to-Open Doesn't Work
- Verify launcher activity: `rocks.koreader.launcher.MainActivity`
- Check file path in `widget_data.json` is valid
- Test manually: `adb shell am start -a android.intent.action.VIEW -d file://path/to/book.pdf`

## Future Enhancements

- [ ] 2x2 and 1x1 widget variants
- [ ] Configurable update frequency via widget settings
- [ ] Widget customization (font size, colors for different displays)
- [ ] Cover image thumbnails (requires image rendering)
- [ ] Reading statistics (pages/day, estimated finish date)
- [ ] Per-library widget filtering
- [ ] Tap-to-resume from last page
- [ ] Multiple book selection in widget (4, 6, or 9 books)

## Support & Contribution

For issues, feature requests, or contributions:
- KoReader GitHub: https://github.com/koreader/koreader
- KoReader Forum: https://www.mobileread.com/forums/forumdisplay.php?f=276

---

**Version:** 1.0  
**Last Updated:** March 17, 2026  
**Target Android:** API 24+ (Android 7.0+)
