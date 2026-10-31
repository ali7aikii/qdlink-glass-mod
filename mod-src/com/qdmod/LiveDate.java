package com.qdmod;

// LiveDate.java - shows the full date, like "Thursday, 22 October".
// The order of the words follows the phone's language.
// Can be used in a layout file as <com.qdmod.LiveDate ... />.

import android.content.Context;
import android.text.format.DateFormat;
import android.util.AttributeSet;

import java.util.Locale;

public class LiveDate extends LiveText {

    public LiveDate(Context context, AttributeSet attributes) {
        super(context, attributes);
    }

    public LiveDate(Context context) {
        super(context);
    }

    // Which date parts to show: weekday, day number and full month name
    protected String buildSkeleton() {
        return "EEEEdMMMM";
    }

    // Let Android order the parts correctly for the phone's language
    @Override
    protected String buildPattern() {
        return DateFormat.getBestDateTimePattern(Locale.getDefault(), buildSkeleton());
    }
}
