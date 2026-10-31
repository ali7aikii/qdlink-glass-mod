package com.qdmod;

// LiveDateShort.java - shows a short date, like "Thu, 22 Oct". Used under the clock in the dock,
// where there is not much room.
// Can be used in a layout file as <com.qdmod.LiveDateShort ... />.

import android.content.Context;
import android.util.AttributeSet;

public class LiveDateShort extends LiveDate {

    public LiveDateShort(Context context, AttributeSet attributes) {
        super(context, attributes);
    }

    public LiveDateShort(Context context) {
        super(context);
    }

    // Short weekday, day number and short month name
    @Override
    protected String buildSkeleton() {
        return "EEEdMMM";
    }
}
