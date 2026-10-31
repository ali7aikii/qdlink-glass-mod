package com.qdmod;

// QdLink.java - the small "bridge" between our new screens and QDLink's own code.
// We do not change QDLink's code. Instead we call two of its existing functions:
//   1. the list of apps you picked in QDLink's Apps screen
//   2. the Navigation page's "open this app" function, so apps open the same way
//      QDLink opens them (with its own phone-lock check and car handover).
// QDLink's code uses shortened names (like "b" and "c"). These names are for
// QDLink 1.9.7 and must be checked again after a QDLink update.

import android.util.Log;
import android.view.View;
import android.view.ViewParent;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public class QdLink {

    // Tag for log messages, so they are easy to find
    private static final String LOG_TAG = "QdMod";

    // The QDLink class and function that return the apps picked in the Apps screen
    private static final String SAVED_APPS_CLASS = "com.neusoft.qdrivelink.otherapp.b";
    private static final String SAVED_APPS_FUNCTION = "c";

    // The function on each saved app that gives its package name (like "com.spotify.music")
    private static final String PACKAGE_NAME_FUNCTION = "getPackageName";

    // The Navigation page class, and its public "open this app" function
    private static final String NAVIGATION_PAGE_CLASS = "com.neusoft.qdrivelink.mapnavi.MapNaviView";
    private static final String OPEN_APP_FUNCTION = "f";


    // Return the package names of the apps picked in QDLink's Apps screen
    // (an empty list if there are none or if something goes wrong)
    public static List<String> loadSavedAppPackages() {
        List<String> packageNames = new ArrayList<String>();
        try {
            Class<?> savedAppsClass = Class.forName(SAVED_APPS_CLASS);
            Method loadFunction = savedAppsClass.getMethod(SAVED_APPS_FUNCTION);
            Object result = loadFunction.invoke(null);

            if (result instanceof List) {
                List<?> savedApps = (List<?>) result;
                for (Object savedApp : savedApps) {
                    String packageName = readPackageName(savedApp);
                    if (packageName != null) {
                        packageNames.add(packageName);
                    }
                }
            }
        } catch (Exception error) {
            Log.w(LOG_TAG, "Could not read the apps saved in QDLink", error);
        }
        return packageNames;
    }

    // Open an app using the Navigation page's own function.
    // Returns true if QDLink's function was called, false if it could not be found.
    public static boolean openApp(View fromView, String packageName) {
        View navigationPage = findParentByClassName(fromView, NAVIGATION_PAGE_CLASS);
        if (navigationPage == null) {
            Log.w(LOG_TAG, "Navigation page not found, can not open " + packageName);
            return false;
        }

        try {
            Method openFunction = navigationPage.getClass().getMethod(OPEN_APP_FUNCTION, String.class);
            openFunction.invoke(navigationPage, packageName);
            return true;
        } catch (Exception error) {
            Log.w(LOG_TAG, "Could not open " + packageName + " through QDLink", error);
            return false;
        }
    }

    // Read the package name from one saved app object (null if it has none)
    private static String readPackageName(Object savedApp) {
        if (savedApp == null) {
            return null;
        }
        try {
            Method nameFunction = savedApp.getClass().getMethod(PACKAGE_NAME_FUNCTION);
            Object name = nameFunction.invoke(savedApp);
            if (name == null) {
                return null;
            }
            return name.toString();
        } catch (Exception error) {
            return null;
        }
    }

    // Walk up from a view to its parents until one has the given class name
    private static View findParentByClassName(View startView, String className) {
        ViewParent parent = startView.getParent();
        while (parent != null) {
            String parentClassName = parent.getClass().getName();
            if (parentClassName.equals(className)) {
                return (View) parent;
            }
            parent = parent.getParent();
        }
        return null;
    }
}
