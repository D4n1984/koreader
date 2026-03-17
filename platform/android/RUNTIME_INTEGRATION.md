# Android Widget Bridge - Runtime Integration Guide

## Overview

The Android widget system needs to be updated whenever reading progress changes in KoReader. This guide explains where and how to integrate the `android_widget_bridge.lua` module into the KoReader app lifecycle.

## Module Location

```
frontend/android_widget_bridge.lua
```

## Integration Points

### 1. **App Lifecycle - Home Button / Background**

When the user presses HOME or the KoReader app is about to be backgrounded, update widget data.

**File:** `frontend/apps/reader/readerui.lua` or equivalent app lifecycle handler

**Code to add:**

```lua
-- In app lifecycle handler (e.g., ReaderUI:onClose or similar)
if util.isAndroid() then
    local widget_bridge = require("frontend/android_widget_bridge")
    widget_bridge:updateWidgetData()
end
```

### 2. **Document Close / Book Finished**

When a user finishes reading a book or closes a document.

**File:** `frontend/apps/reader/modules/readerui.lua`

**Code to add:**

```lua
-- When document is closed
if util.isAndroid() then
    local widget_bridge = require("frontend/android_widget_bridge")
    widget_bridge:updateWidgetData()
end
```

### 3. **Book Progress Update (Optional)**

For more frequent updates, wire into the page update event (may impact performance):

**File:** `frontend/apps/reader/modules/readerfooter.lua`

**Code to add:**

```lua
-- In rare cases where you want real-time widget updates
-- NOTE: This will cause more frequent file I/O. Use sparingly.
-- self:registerScreenshotWatcher(...)  -- or similar hooking mechanism
if util.isAndroid() and should_frequent_update then
    local widget_bridge = require("frontend/android_widget_bridge")
    widget_bridge:updateWidgetData()
end
```

### 4. **Settings/Profile Change**

When user changes reading settings or book status:

**File:** Any module that modifies `DocSettings:saveSetting(...)`

**Code to add:**

```lua
-- After modifying document settings
if util.isAndroid() then
    local widget_bridge = require("frontend/android_widget_bridge")
    widget_bridge:updateWidgetData()
end
```

## Integration Pattern

The recommended pattern is to call widget updates at **low-frequency, high-impact events**:

1. ✅ **App Pause/Home** (most important)
2. ✅ **Document Close**
3. ✅ **Document Open** (optional)
4. ❌ **Per-page Turn** (too frequent, avoid)
5. ❌ **Per-scroll** (way too frequent, avoid)

## Example Implementation

### Simple Integration (Recommended)

Add to `reader.lua` or main app initialization:

```lua
-- At app shutdown/pause point
function ReaderUI:onClose()
    -- ... existing close logic ...
    
    -- Update Android widget before closing
    if util.isAndroid() then
        local widget_bridge = require("frontend/android_widget_bridge")
        if widget_bridge:isAvailable() then
            widget_bridge:updateWidgetData()
        end
    end
    
    -- ... rest of close logic ...
end
```

### Error Handling

The widget bridge is robust to errors. However, you can add defensive code:

```lua
if util.isAndroid() then
    local ok, widget_bridge = pcall(function()
        return require("frontend/android_widget_bridge")
    end)
    
    if ok and widget_bridge:isAvailable() then
        widget_bridge:updateWidgetData()
    end
end
```

## Data Format

The module exports data to: `/koreader/widget_data.json`

Example output:
```json
{
  "format_version": "1.0",
  "updated_at": 1710700000,
  "books": [
    {
      "title": "The Great Gatsby",
      "author": "F. Scott Fitzgerald",
      "file_path": "/storage/emulated/0/Books/gatsby.epub",
      "progress_percent": 35.5,
      "doc_pages": 200,
      "last_read_time": "2026-03-17 14:30",
      "timestamp": 1710700000
    },
    {
      "title": "1984",
      "author": "George Orwell",
      "file_path": "/storage/emulated/0/Books/1984.pdf",
      "progress_percent": 62.0,
      "doc_pages": 328,
      "last_read_time": "2026-03-16 20:15",
      "timestamp": 1710614000
    }
  ]
}
```

The Android widget provider (`KoReaderWidget.java`) reads this JSON and renders it on the home screen.

## Testing

To verify integration:

1. **Enable verbose logging:**
   ```lua
   logger:setLevel(logger.DEBUG)
   ```

2. **Call widget update manually:**
   ```lua
   local widget_bridge = require("frontend/android_widget_bridge")
   widget_bridge:updateWidgetData()
   ```

3. **Verify JSON on device:**
   ```bash
   adb shell cat /storage/emulated/0/koreader/widget_data.json
   ```

4. **Check widget on home screen:**
   - Long-press home screen → Add widget → KoReader Reading Progress
   - Verify it displays books and progress from JSON

## Permissions

The module requires `READ_EXTERNAL_STORAGE` permission (added to AndroidManifest.xml).

On Android 6.0+ (API 23+), ensure runtime permission is requested:

```lua
if util.isAndroid() and not util:checkPermission("android.permission.READ_EXTERNAL_STORAGE") then
    util:requestPermission("android.permission.READ_EXTERNAL_STORAGE")
end
```

## Troubleshooting

### Widget Shows "No books"
- Check that `widget_data.json` exists and is readable:
  ```bash
  adb shell ls -l /storage/emulated/0/koreader/widget_data.json
  ```
- Verify KoReader has written data by opening a book and triggering an update
- Check app logs: `adb logcat | grep KoReaderWidget`

### Widget Doesn't Update
- Ensure `android_widget_bridge:updateWidgetData()` is called at app lifecycle events
- Check that widget refresh frequency (1 hour default) hasn't prevented updates
- Manually trigger widget update in system settings or with:
  ```bash
  adb shell am broadcast -a android.appwidget.action.APPWIDGET_UPDATE -n rocks.koreader.launcher/.KoReaderWidget
  ```

### JSON Not Valid
- Check for special characters in book titles/authors
- Verify Lua json.encode() is working correctly
- Add logging in android_widget_bridge.lua debug output

## API Reference

### `updateWidgetData()`
Updates widget data from current reading history. Safe to call frequently (performs atomic updates).

### `isAvailable()`
Returns `true` if Android environment is available and widget support is enabled.

### `clearWidgetData()`
Clears widget data (call on uninstall or data reset).

### `getCurrentWidgetData()`
Returns the current widget data as a Lua table (for debugging).

## Future Enhancements

- [ ] Persistent sync queue for offline updates
- [ ] Widget refresh on app wake (via broadcast receiver)
- [ ] Per-book widget variants (3x2, 2x2, 1x1)
- [ ] Configurable update frequency
- [ ] Widget customization (colors, fonts for different e-ink displays)
