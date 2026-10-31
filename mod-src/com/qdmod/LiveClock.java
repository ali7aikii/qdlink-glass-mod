package com.qdmod;

// LiveClock.java - shows the current time, like "08:32" (or "8:32" with a 12-hour phone setting).
// Can be used in a layout file as <com.qdmod.LiveClock ... />.

import android.content.Context;
import android.text.format.DateFormat;
import android.util.AttributeSet;

public class LiveClock extends LiveText {

    public LiveClock(Context context, AttributeSet attributes) {
        super(context, attributes);
    }

    public LiveClock(Context context) {
        super(context);
    }

    // Follow the phone's 12 or 24 hour setting
    @Override
    protected String buildPattern() {
        boolean uses24Hours = DateFormat.is24HourFormat(getContext());
        if (uses24Hours) {
            return "HH:mm";
        } else {
            return "h:mm";
        }
    }
}
