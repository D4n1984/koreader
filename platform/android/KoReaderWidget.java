package rocks.koreader.widget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Build;
import android.util.Log;
import android.widget.RemoteViews;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * KoReader Android Widget Provider
 * Displays the 2 most recent books in progress with reading progress
 *
 * @author KoReader Team
 * @version 1.0
 * @since 2026-03-17
 */
public class KoReaderWidget extends AppWidgetProvider {
    private static final String TAG = "KoReaderWidget";
    private static final int BOOK_SLOT_WIDTH = 200;
    private static final int BOOK_SLOT_HEIGHT = 200;
    private static final int PROGRESS_BAR_HEIGHT = 8;
    private static final int ICON_SIZE = 80;

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        Log.d(TAG, "onUpdate called for " + appWidgetIds.length + " widgets");
        updateAllWidgets(context, appWidgetManager, appWidgetIds);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        Log.d(TAG, "onReceive: action=" + intent.getAction());

        if (intent.getAction() != null && intent.getAction().equals("rocks.koreader.widget.FORCE_UPDATE")) {
            AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
            ComponentName componentName = new ComponentName(context, KoReaderWidget.class);
            int[] appWidgetIds = appWidgetManager.getAppWidgetIds(componentName);
            updateAllWidgets(context, appWidgetManager, appWidgetIds);
        }
    }

    private void updateAllWidgets(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        for (int appWidgetId : appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId);
        }
    }

    private void updateAppWidget(Context context, AppWidgetManager appWidgetManager, int appWidgetId) {
        try {
            RemoteViews remoteViews = new RemoteViews(context.getPackageName(), R.layout.widget_reading_progress);

            // Load widget data from JSON
            WidgetData[] books = loadWidgetData();

            // Update book slots
            if (books.length > 0) {
                updateBookSlot(context, remoteViews, books[0], 0);
            } else {
                showEmptySlot(remoteViews, 0);
            }

            if (books.length > 1) {
                updateBookSlot(context, remoteViews, books[1], 1);
            } else {
                showEmptySlot(remoteViews, 1);
            }

            appWidgetManager.updateAppWidget(appWidgetId, remoteViews);
            Log.d(TAG, "Widget updated successfully with " + books.length + " books");
        } catch (Exception e) {
            Log.e(TAG, "Error updating widget", e);
        }
    }

    private void updateBookSlot(Context context, RemoteViews remoteViews, WidgetData book, int slot) {
        try {
            // Set book title
            int titleViewId = slot == 0 ? R.id.book1_title : R.id.book2_title;
            remoteViews.setTextViewText(titleViewId, book.title);

            // Set author
            int authorViewId = slot == 0 ? R.id.book1_author : R.id.book2_author;
            remoteViews.setTextViewText(authorViewId, book.author);

            // Set progress percentage
            int percentViewId = slot == 0 ? R.id.book1_percent : R.id.book2_percent;
            remoteViews.setTextViewText(percentViewId, String.format(Locale.US, "%.0f%%", book.progressPercent));

            // Set last read time
            int timeViewId = slot == 0 ? R.id.book1_time : R.id.book2_time;
            remoteViews.setTextViewText(timeViewId, book.lastReadTime);

            // Create and set progress bar bitmap
            Bitmap progressBitmap = createProgressBar(book.progressPercent);
            int progressViewId = slot == 0 ? R.id.book1_progress : R.id.book2_progress;
            remoteViews.setImageViewBitmap(progressViewId, progressBitmap);

            // Set tap intent to open book
            Intent openIntent = createOpenBookIntent(context, book.filePath);
            int containerViewId = slot == 0 ? R.id.book1_container : R.id.book2_container;
            remoteViews.setOnClickPendingIntent(containerViewId, createPendingIntent(context, openIntent));

            Log.d(TAG, "Updated slot " + slot + ": " + book.title + " (" + book.progressPercent + "%)");
        } catch (Exception e) {
            Log.e(TAG, "Error updating book slot " + slot, e);
        }
    }

    private void showEmptySlot(RemoteViews remoteViews, int slot) {
        int titleViewId = slot == 0 ? R.id.book1_title : R.id.book2_title;
        int authorViewId = slot == 0 ? R.id.book1_author : R.id.book2_author;
        int percentViewId = slot == 0 ? R.id.book1_percent : R.id.book2_percent;
        int timeViewId = slot == 0 ? R.id.book1_time : R.id.book2_time;

        remoteViews.setTextViewText(titleViewId, "No books");
        remoteViews.setTextViewText(authorViewId, "Start reading to see progress");
        remoteViews.setTextViewText(percentViewId, "0%");
        remoteViews.setTextViewText(timeViewId, "N/A");
    }

    private Bitmap createProgressBar(double progressPercent) {
        Bitmap bitmap = Bitmap.createBitmap(180, PROGRESS_BAR_HEIGHT, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);

        // Fill background with white
        Paint bgPaint = new Paint();
        bgPaint.setColor(android.graphics.Color.WHITE);
        canvas.drawRect(0, 0, 180, PROGRESS_BAR_HEIGHT, bgPaint);

        // Draw border
        Paint borderPaint = new Paint();
        borderPaint.setColor(android.graphics.Color.BLACK);
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(1);
        canvas.drawRect(0, 0, 180, PROGRESS_BAR_HEIGHT, borderPaint);

        // Draw progress (black fill)
        Paint progressPaint = new Paint();
        progressPaint.setColor(android.graphics.Color.BLACK);
        float progressWidth = (float) (180 * progressPercent / 100.0);
        canvas.drawRect(0, 0, progressWidth, PROGRESS_BAR_HEIGHT, progressPaint);

        return bitmap;
    }

    private WidgetData[] loadWidgetData() throws IOException, JSONException {
        File dataFile = new File(getKoReaderDataDir(), "widget_data.json");

        if (!dataFile.exists()) {
            Log.w(TAG, "Widget data file not found: " + dataFile.getAbsolutePath());
            return new WidgetData[0];
        }

        String jsonContent = readFileAsString(dataFile);
        JSONObject jsonRoot = new JSONObject(jsonContent);
        JSONArray booksArray = jsonRoot.getJSONArray("books");

        WidgetData[] books = new WidgetData[Math.min(2, booksArray.length())];

        for (int i = 0; i < books.length; i++) {
            JSONObject bookObj = booksArray.getJSONObject(i);
            books[i] = new WidgetData(
                bookObj.getString("title"),
                bookObj.getString("author"),
                bookObj.getDouble("progress_percent"),
                bookObj.getString("last_read_time"),
                bookObj.getString("file_path")
            );
        }

        Log.d(TAG, "Loaded " + books.length + " books from widget data");
        return books;
    }

    private String getKoReaderDataDir() {
        // KoReader typically stores data in /sdcard/koreader or similar
        String externalStorage = System.getenv("EXTERNAL_STORAGE");
        if (externalStorage == null) {
            externalStorage = "/storage/emulated/0";
        }
        return externalStorage + "/koreader";
    }

    private String readFileAsString(File file) throws IOException {
        StringBuilder content = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line);
            }
        }
        return content.toString();
    }

    private Intent createOpenBookIntent(Context context, String filePath) {
        Intent intent = new Intent();
        intent.setAction("android.intent.action.VIEW");
        intent.setData(android.net.Uri.fromFile(new File(filePath)));
        intent.setClassName("rocks.koreader.launcher", "rocks.koreader.launcher.MainActivity");
        return intent;
    }

    private PendingIntent createPendingIntent(Context context, Intent intent) {
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        return PendingIntent.getActivity(context, 0, intent, flags);
    }

    /**
     * Internal data class for widget book information
     */
    private static class WidgetData {
        String title;
        String author;
        double progressPercent;
        String lastReadTime;
        String filePath;

        WidgetData(String title, String author, double progressPercent, String lastReadTime, String filePath) {
            this.title = title != null ? title : "Unknown";
            this.author = author != null ? author : "Unknown Author";
            this.progressPercent = progressPercent;
            this.lastReadTime = lastReadTime != null ? lastReadTime : "N/A";
            this.filePath = filePath;
        }
    }
}
