package com.qdmod;

// LiveText.java - a text label that always shows the real current time or date.
// Android's own TextClock can stop following the real clock when its view is moved
// to the car's screen. This class does not trust any stored time: on every update it
// asks the system for the current time and time zone again.
// It updates:
//   1. right after it appears on screen,
//   2. at the start of every new minute,
//   3. whenever the phone's time, date or time zone changes,
//   4. on the system's own once-a-minute tick (a second safety net).
// A subclass says what to show by filling in buildPattern().

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public abstract class LiveText extends TextView {

    // One minute in milliseconds
    private static final long MINUTE_MS = 60L * 1000L;

    // Wake up a little after the minute starts, so the new minute is surely showing
    private static final long EXTRA_DELAY_MS = 40L;

    private final Handler screenThread;
    private final MinuteTicker minuteTicker;
    private final TimeChangeReceiver timeChangeReceiver;
    private boolean running;
    private Context registeredContext;

    // Android uses this one when it creates the view from a layout file
    public LiveText(Context context, AttributeSet attributes) {
        super(context, attributes);
        screenThread = new Handler(Looper.getMainLooper());
        minuteTicker = new MinuteTicker();
        timeChangeReceiver = new TimeChangeReceiver();
        running = false;
        registeredContext = null;
    }

    public LiveText(Context context) {
        this(context, null);
    }

    // The subclass returns the date pattern to show, like "HH:mm" or "EEEE, d MMMM"
    protected abstract String buildPattern();

    // Read the real time right now and show it (only changes the text if it is different)
    public void updateNow() {
        // Ask the system again every time, so a changed time zone or 12/24 hour setting is used
        TimeZone timeZone = TimeZone.getDefault();
        SimpleDateFormat format = new SimpleDateFormat(buildPattern(), Locale.getDefault());
        format.setTimeZone(timeZone);
        String newText = format.format(new Date(System.currentTimeMillis()));

        CharSequence oldText = getText();
        if (oldText == null || !newText.contentEquals(oldText)) {
            setText(newText);
        }
    }

    // Start updating when the view appears on a screen (phone or car)
    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        startRunning();
    }

    // Stop updating when the view is removed (saves battery)
    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        stopRunning();
    }

    private void startRunning() {
        if (running) {
            return;
        }
        running = true;
        updateNow();
        scheduleNextMinute();

        // Listen for time changes on the application context, which stays valid on the car's screen
        registeredContext = getContext().getApplicationContext();
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_TIME_CHANGED);
        filter.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        filter.addAction(Intent.ACTION_DATE_CHANGED);
        filter.addAction(Intent.ACTION_TIME_TICK);
        registeredContext.registerReceiver(timeChangeReceiver, filter);
    }

    private void stopRunning() {
        if (!running) {
            return;
        }
        running = false;
        screenThread.removeCallbacks(minuteTicker);
        if (registeredContext != null) {
            try {
                registeredContext.unregisterReceiver(timeChangeReceiver);
            } catch (IllegalArgumentException alreadyGone) {
                // It was not registered any more; nothing to do
            }
            registeredContext = null;
        }
    }

    // Plan the next update for the start of the next minute
    private void scheduleNextMinute() {
        long now = System.currentTimeMillis();
        long millisIntoMinute = now % MINUTE_MS;
        long delay = MINUTE_MS - millisIntoMinute + EXTRA_DELAY_MS;
        screenThread.removeCallbacks(minuteTicker);
        screenThread.postDelayed(minuteTicker, delay);
    }

    // Runs at the start of every minute
    private class MinuteTicker implements Runnable {
        @Override
        public void run() {
            if (!running) {
                return;
            }
            updateNow();
            scheduleNextMinute();
        }
    }

    // Runs when the phone's time, date or time zone changes, and on the system's minute tick
    private class TimeChangeReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (!running) {
                return;
            }
            updateNow();
            scheduleNextMinute();
        }
    }
}
