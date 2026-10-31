package com.qdmod;

// WeatherInfo.java - the weather we show on the Home and Clock screens.
// It also turns the weather service's number code into words and an icon.

public class WeatherInfo {

    // Current temperature, today's high and low (degrees Celsius)
    public int temperature;
    public int high;
    public int low;

    // The weather service's code (0 = clear sky, 61 = rain, ...)
    public int code;

    // True during the day, false at night (to show a sun or a moon)
    public boolean isDay;

    // The city name ("" if unknown)
    public String city = "";

    // A message to show instead of the weather when something went wrong ("" if all is fine)
    public String problem = "";


    // True when the weather could not be loaded
    public boolean hasProblem() {
        return problem.length() > 0;
    }

    // Turn the weather code into a short description
    public String describe() {
        if (code == 0) {
            return "Clear";
        } else if (code == 1 || code == 2) {
            return "Partly cloudy";
        } else if (code == 3) {
            return "Cloudy";
        } else if (code == 45 || code == 48) {
            return "Fog";
        } else if (code >= 51 && code <= 57) {
            return "Drizzle";
        } else if (code >= 61 && code <= 67) {
            return "Rain";
        } else if (code >= 71 && code <= 77) {
            return "Snow";
        } else if (code >= 80 && code <= 82) {
            return "Showers";
        } else if (code == 85 || code == 86) {
            return "Snow showers";
        } else if (code >= 95) {
            return "Thunderstorm";
        } else {
            return "Weather";
        }
    }

    // Pick the matching icon for GlyphView
    public int pickGlyph() {
        if (code == 0) {
            if (isDay) {
                return GlyphView.SUN;
            } else {
                return GlyphView.MOON;
            }
        } else if (code == 1 || code == 2) {
            return GlyphView.PARTLY_CLOUDY;
        } else if (code == 3) {
            return GlyphView.CLOUD;
        } else if (code == 45 || code == 48) {
            return GlyphView.FOG;
        } else if (code >= 71 && code <= 77) {
            return GlyphView.SNOW;
        } else if (code == 85 || code == 86) {
            return GlyphView.SNOW;
        } else if (code >= 95) {
            return GlyphView.STORM;
        } else if (code >= 51) {
            return GlyphView.RAIN;
        } else {
            return GlyphView.CLOUD;
        }
    }

    // Pick the icon color: yellow for sun, light blue for rain, white otherwise
    public int pickGlyphColor() {
        int glyph = pickGlyph();
        if (glyph == GlyphView.SUN || glyph == GlyphView.PARTLY_CLOUDY) {
            return Ui.SUN_YELLOW;
        } else if (glyph == GlyphView.RAIN || glyph == GlyphView.STORM) {
            return Ui.RAIN_BLUE;
        } else {
            return Ui.TEXT_MAIN;
        }
    }

    // One line for the Clock screen, like "24° · Clear · Riyadh"
    public String oneLine() {
        if (hasProblem()) {
            return "";
        }
        String line = temperature + "° · " + describe();
        if (city.length() > 0) {
            line = line + " · " + city;
        }
        return line;
    }
}
