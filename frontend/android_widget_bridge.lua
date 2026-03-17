--[[--
Android Widget Bridge for KoReader

This module bridges KoReader's data (reading history and document settings)
to the Android native widget system by exporting progress data as JSON.

Reads from:
  - history.lua (reading history)
  - docsettings/{hash}.lua (per-document progress)

Writes to:
  - /koreader/widget_data.json (consumed by KoReaderWidget Android provider)

Usage:
  local widget_bridge = require("frontend/android_widget_bridge")
  widget_bridge:updateWidgetData()  -- Called on app close/update

@module frontend/android_widget_bridge
@author KoReader Team
]]

local DataStorage = require("datastorage")
local DocSettings = require("docsettings")
local ReadHistory = require("readhistory")
local json = require("json")
local logger = require("logger")
local util = require("util")
local ffiutil = require("ffi/util")
local datetime = require("datetime")
local lfs = require("libs/libkoreader-lfs")

local AndroidWidgetBridge = {
    enabled = false,
    widget_data_file = nil,
}

--- Initialize the widget bridge
-- @return boolean true if Android environment and is available
function AndroidWidgetBridge:_init()
    if self.enabled then
        return true
    end

    -- Check if we're running on Android
    if not (util and util.isAndroid) or not util:isAndroid() then
        logger.dbg("AndroidWidgetBridge: Not running on Android, disabling widget support")
        return false
    end

    -- Determine widget data file location
    local data_dir = DataStorage:getDataDir()
    self.widget_data_file = ffiutil.joinPath(data_dir, "widget_data.json")

    self.enabled = true
    logger.dbg("AndroidWidgetBridge: Initialized with data file:", self.widget_data_file)
    return true
end

--- Read reading history and get most recent books in progress
-- @return table Array of {file, time, percent_finished} for up to 2 books
function AndroidWidgetBridge:_getRecentBooksInProgress()
    local books = {}

    if not ReadHistory then
        logger.warn("AndroidWidgetBridge: ReadHistory not available")
        return books
    end

    -- Ensure history is loaded
    if type(ReadHistory.getIndexByFile) ~= "function" then
        logger.warn("AndroidWidgetBridge: ReadHistory interface not ready")
        return books
    end

    -- Get history entries (most recent first)
    local history = ReadHistory.hist or {}

    for i = 1, math.min(2, #history) do
        local entry = history[i]
        if entry and entry.file and lfs.attributes(entry.file, "mode") == "file" then
            -- Try to get document settings
            local doc_settings = DocSettings:open(entry.file)
            if doc_settings then
                local percent = doc_settings:readSetting("percent_finished") or 0
                local doc_pages = doc_settings:readSetting("doc_pages") or 0

                -- Only include books that are in progress (0 < progress < 1)
                if percent < 1.0 then
                    table.insert(books, {
                        file = entry.file,
                        time = entry.time or 0,
                        percent_finished = percent,
                        doc_pages = doc_pages,
                    })
                end

                -- If we have 2 books, stop
                if #books >= 2 then
                    break
                end
            end
        end
    end

    logger.dbg("AndroidWidgetBridge: Found", #books, "books in progress")
    return books
end

--- Extract metadata for a book file
-- @param file_path string Path to the document file
-- @return table Book metadata {title, author, series}
function AndroidWidgetBridge:_extractBookMetadata(file_path)
    local metadata = {
        title = file_path:match("([^/]+)$") or "Unknown",
        author = "Unknown",
        series = nil,
    }

    if not file_path then
        return metadata
    end

    -- Try to load document settings for metadata
    local doc_settings = DocSettings:open(file_path)
    if doc_settings then
        local doc_props = doc_settings:readSetting("doc_props")
        if doc_props then
            metadata.title = doc_props.title or metadata.title
            metadata.author = doc_props.authors or metadata.author
            metadata.series = doc_props.series
        end

        -- Fallback: use summary if available
        if not doc_props or not doc_props.title then
            local summary = doc_settings:readSetting("summary")
            if summary and summary.title then
                metadata.title = summary.title
            end
        end
    end

    return metadata
end

--- Format timestamp as readable string
-- @param unix_time number Unix timestamp
-- @return string Formatted datetime (e.g., "2026-03-17 14:30")
function AndroidWidgetBridge:_formatTime(unix_time)
    if not unix_time or unix_time == 0 then
        return "Never"
    end

    -- Try to format using KoReader's datetime utilities
    if datetime and datetime.secondsToDateTime then
        return datetime.secondsToDateTime(unix_time)
    end

    -- Fallback to OS date formatting
    return os.date("%Y-%m-%d %H:%M", unix_time)
end

--- Build widget data structure from books
-- @param books table Array of book entries with progress
-- @return table Widget data structure ready for JSON export
function AndroidWidgetBridge:_buildWidgetData(books)
    local widget_data = {
        format_version = "1.0",
        updated_at = os.time(),
        books = {},
    }

    for idx, book in ipairs(books) do
        local metadata = self:_extractBookMetadata(book.file)
        local progress_pct = book.percent_finished * 100

        table.insert(widget_data.books, {
            title = metadata.title,
            author = metadata.author,
            file_path = book.file,
            progress_percent = progress_pct,
            doc_pages = book.doc_pages,
            last_read_time = self:_formatTime(book.time),
            timestamp = book.time,
        })
    end

    return widget_data
end

--- Safely write widget data to JSON file (atomic update)
-- @param data table Widget data structure
-- @return boolean true if successful
function AndroidWidgetBridge:_writeWidgetDataJSON(data)
    if not self.widget_data_file then
        logger.warn("AndroidWidgetBridge: No widget data file configured")
        return false
    end

    -- Use safe JSON encoding
    local json_str, err = json.encode(data)
    if not json_str then
        logger.error("AndroidWidgetBridge: JSON encoding failed:", err)
        return false
    end

    -- Write to temporary file first
    local temp_file = self.widget_data_file .. ".tmp"
    local success, write_err = util.writeToFile(json_str, temp_file, true, true)

    if not success then
        logger.error("AndroidWidgetBridge: Failed to write temp file:", write_err)
        return false
    end

    -- Atomic rename (overwrites existing file)
    local ok, rename_err = os.rename(temp_file, self.widget_data_file)
    if not ok then
        logger.error("AndroidWidgetBridge: Failed to rename widget data file:", rename_err)
        os.remove(temp_file) -- Clean up temp file on error
        return false
    end

    logger.info("AndroidWidgetBridge: Widget data updated:", self.widget_data_file)
    return true
end

--- Update widget data on disk
-- This should be called whenever:
--   * A book is closed/finished
--   * Progress is updated
--   * A new book is opened
-- Typically called on app lifecycle events (onPause, onDestroy)
-- @return boolean true if update was successful
function AndroidWidgetBridge:updateWidgetData()
    if not self:_init() then
        logger.dbg("AndroidWidgetBridge: Not available, skipping widget update")
        return false
    end

    logger.dbg("AndroidWidgetBridge: Starting widget data update...")

    -- Get recent books in progress
    local books = self:_getRecentBooksInProgress()

    -- Build widget data structure
    local widget_data = self:_buildWidgetData(books)

    -- Write to JSON file
    local success = self:_writeWidgetDataJSON(widget_data)

    if success then
        logger.info("AndroidWidgetBridge: Widget data updated with", #books, "books")
    else
        logger.warn("AndroidWidgetBridge: Failed to update widget data")
    end

    return success
end

--- Completely clean widget data (call on uninstall or data reset)
-- @return boolean true if successful
function AndroidWidgetBridge:clearWidgetData()
    if not self.widget_data_file then
        return true
    end

    local ok, err = os.remove(self.widget_data_file)
    if ok then
        logger.info("AndroidWidgetBridge: Widget data cleared")
        return true
    else
        logger.warn("AndroidWidgetBridge: Failed to clear widget data:", err)
        return false
    end
end

--- Check if widget is available and enabled
-- @return boolean true if widget support is available
function AndroidWidgetBridge:isAvailable()
    return self:_init()
end

--- Get current widget data (for debugging/testing)
-- @return table Current widget data structure or nil if not available
function AndroidWidgetBridge:getCurrentWidgetData()
    if not self.widget_data_file or not lfs.attributes(self.widget_data_file, "mode") then
        return nil
    end

    local content = util.readFromFile(self.widget_data_file)
    if not content then
        return nil
    end

    local ok, data = pcall(json.decode, content)
    if ok then
        return data
    end

    return nil
end

return AndroidWidgetBridge
