package com.qdmod;

// WeatherLoader.java - downloads the weather for your location in the background.
// It uses Open-Meteo (open-meteo.com): free, no account or key needed.
// Only your rounded position (about 1 km accuracy) is sent, nothing else.
// How to use:  new Thread(new WeatherLoader(context, location, listener)).start();

import android.content.Context;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.Locale;

public class WeatherLoader implements Runnable {

    // The Home screen implements this to receive the result
    public interface Listener {
        void onWeatherLoaded(WeatherInfo weather);
    }

    private static final String LOG_TAG = "QdMod";

    // The weather service address (the position is added at the end)
    private static final String WEATHER_ADDRESS = "https://api.open-meteo.com/v1/forecast"
            + "?current=temperature_2m,weather_code,is_day"
            + "&daily=temperature_2m_max,temperature_2m_min"
            + "&timezone=auto&forecast_days=1";

    // Give up if the service does not answer within 10 seconds
    private static final int TIMEOUT_MS = 10000;

    private final Context context;
    private final Location location;
    private final Listener listener;

    public WeatherLoader(Context appContext, Location position, Listener resultListener) {
        context = appContext;
        location = position;
        listener = resultListener;
    }

    // Runs on a background thread (network calls are not allowed on the screen thread)
    @Override
    public void run() {
        WeatherInfo weather = new WeatherInfo();
        try {
            // ---- Step 1: download the weather as JSON text ----
            // (JSON is a simple text format for data, like {"temperature": 24})
            String address = buildAddress();
            String jsonText = download(address);

            // ---- Step 2: read the values we need from the JSON ----
            readWeather(jsonText, weather);

            // ---- Step 3: find the city name for the position ----
            weather.city = findCityName();
        } catch (Exception error) {
            Log.w(LOG_TAG, "Weather download failed", error);
            weather.problem = "Weather unavailable";
        }

        // ---- Step 4: hand the result back to the screen thread ----
        Handler screenThread = new Handler(Looper.getMainLooper());
        screenThread.post(new DeliverResult(weather));
    }

    // Add the rounded position to the weather address
    private String buildAddress() {
        String latitude = String.format(Locale.US, "%.2f", location.getLatitude());
        String longitude = String.format(Locale.US, "%.2f", location.getLongitude());
        String address = WEATHER_ADDRESS + "&latitude=" + latitude + "&longitude=" + longitude;
        return address;
    }

    // Download a web address and return the answer as text
    private String download(String address) throws Exception {
        URL url = new URL(address);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setConnectTimeout(TIMEOUT_MS);
        connection.setReadTimeout(TIMEOUT_MS);

        StringBuilder text = new StringBuilder();
        try {
            int statusCode = connection.getResponseCode();
            if (statusCode != 200) {
                throw new Exception("Weather service answered with code " + statusCode);
            }
            InputStream stream = connection.getInputStream();
            BufferedReader reader = new BufferedReader(new InputStreamReader(stream, "UTF-8"));
            String line = reader.readLine();
            while (line != null) {
                text.append(line);
                line = reader.readLine();
            }
            reader.close();
        } finally {
            connection.disconnect();
        }
        return text.toString();
    }

    // Copy the temperature, code, day/night, high and low into the WeatherInfo
    private void readWeather(String jsonText, WeatherInfo weather) throws Exception {
        JSONObject root = new JSONObject(jsonText);

        JSONObject current = root.getJSONObject("current");
        double temperature = current.getDouble("temperature_2m");
        weather.temperature = (int) Math.round(temperature);
        weather.code = current.getInt("weather_code");
        int isDayNumber = current.getInt("is_day");
        if (isDayNumber == 1) {
            weather.isDay = true;
        } else {
            weather.isDay = false;
        }

        JSONObject daily = root.getJSONObject("daily");
        JSONArray highs = daily.getJSONArray("temperature_2m_max");
        JSONArray lows = daily.getJSONArray("temperature_2m_min");
        weather.high = (int) Math.round(highs.getDouble(0));
        weather.low = (int) Math.round(lows.getDouble(0));
    }

    // Ask Android for the city name at this position ("" if it does not know)
    private String findCityName() {
        if (!Geocoder.isPresent()) {
            return "";
        }
        try {
            Geocoder geocoder = new Geocoder(context, Locale.getDefault());
            List<Address> places = geocoder.getFromLocation(location.getLatitude(), location.getLongitude(), 1);
            if (places == null || places.size() == 0) {
                return "";
            }
            Address place = places.get(0);
            String city = place.getLocality();
            if (city == null) {
                city = place.getSubAdminArea();
            }
            if (city == null) {
                city = "";
            }
            return city;
        } catch (Exception error) {
            return "";
        }
    }

    // A small job that gives the result to the listener on the screen thread
    private class DeliverResult implements Runnable {
        private final WeatherInfo weather;

        DeliverResult(WeatherInfo result) {
            weather = result;
        }

        @Override
        public void run() {
            listener.onWeatherLoaded(weather);
        }
    }
}
