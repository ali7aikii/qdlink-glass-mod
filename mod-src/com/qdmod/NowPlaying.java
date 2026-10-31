package com.qdmod;

// NowPlaying.java - finds the music app that is playing (Spotify, YouTube Music, ...)
// and gives the Home screen its song title, artist, cover picture and buttons.
// It needs "Notification access" for QDLink (see MediaListenerService.java).

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.SystemClock;
import android.util.Log;

import java.util.List;

public class NowPlaying {

    // The Home screen implements this to hear about changes
    public interface Listener {
        void onNowPlayingChanged();
    }

    private static final String LOG_TAG = "QdMod";

    private final Context context;
    private final Listener listener;
    private final ComponentName listenerComponent;
    private final SessionsWatcher sessionsWatcher;
    private final SongWatcher songWatcher;

    private MediaSessionManager sessionManager;
    private MediaController currentPlayer;
    private boolean hasAccess;
    private boolean started;

    public NowPlaying(Context appContext, Listener changeListener) {
        context = appContext;
        listener = changeListener;
        listenerComponent = new ComponentName(context, MediaListenerService.class);
        sessionsWatcher = new SessionsWatcher();
        songWatcher = new SongWatcher();
        hasAccess = false;
        started = false;
    }

    // ---- Starting and stopping ----

    // Start watching for music players (call when the Home screen appears)
    public void start() {
        if (started) {
            return;
        }
        sessionManager = (MediaSessionManager) context.getSystemService(Context.MEDIA_SESSION_SERVICE);
        if (sessionManager == null) {
            return;
        }
        try {
            List<MediaController> players = sessionManager.getActiveSessions(listenerComponent);
            sessionManager.addOnActiveSessionsChangedListener(sessionsWatcher, listenerComponent);
            hasAccess = true;
            started = true;
            choosePlayer(players);
        } catch (SecurityException noAccess) {
            // Notification access is not switched on yet
            hasAccess = false;
            Log.i(LOG_TAG, "No notification access yet, now playing is hidden");
        }
        listener.onNowPlayingChanged();
    }

    // Stop watching (call when the Home screen is hidden, to save battery)
    public void stop() {
        if (!started) {
            return;
        }
        sessionManager.removeOnActiveSessionsChangedListener(sessionsWatcher);
        setCurrentPlayer(null);
        started = false;
    }

    // ---- Information for the Home screen ----

    // True when notification access is switched on
    public boolean hasAccess() {
        return hasAccess;
    }

    // True when there is a music app to show
    public boolean hasPlayer() {
        return currentPlayer != null;
    }

    public String getTitle() {
        return readText(MediaMetadata.METADATA_KEY_TITLE);
    }

    public String getArtist() {
        String artist = readText(MediaMetadata.METADATA_KEY_ARTIST);
        if (artist.length() == 0) {
            artist = readText(MediaMetadata.METADATA_KEY_ALBUM_ARTIST);
        }
        return artist;
    }

    // The song's cover picture, or null if the app gives none
    public Bitmap getCover() {
        MediaMetadata metadata = getMetadata();
        if (metadata == null) {
            return null;
        }
        Bitmap cover = metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
        if (cover == null) {
            cover = metadata.getBitmap(MediaMetadata.METADATA_KEY_ART);
        }
        return cover;
    }

    // The package name of the playing app (like "com.spotify.music")
    public String getPlayerPackage() {
        if (currentPlayer == null) {
            return "";
        }
        return currentPlayer.getPackageName();
    }

    // The readable name of the playing app (like "Spotify")
    public String getPlayerName() {
        String packageName = getPlayerPackage();
        if (packageName.length() == 0) {
            return "";
        }
        try {
            PackageManager packageManager = context.getPackageManager();
            ApplicationInfo appInfo = packageManager.getApplicationInfo(packageName, 0);
            CharSequence label = packageManager.getApplicationLabel(appInfo);
            return label.toString();
        } catch (PackageManager.NameNotFoundException error) {
            return packageName;
        }
    }

    public boolean isPlaying() {
        PlaybackState state = getState();
        if (state == null) {
            return false;
        }
        return state.getState() == PlaybackState.STATE_PLAYING;
    }

    // How much of the song has played, from 0.0 to 1.0
    public float getProgress() {
        MediaMetadata metadata = getMetadata();
        PlaybackState state = getState();
        if (metadata == null || state == null) {
            return 0f;
        }
        long lengthMs = metadata.getLong(MediaMetadata.METADATA_KEY_DURATION);
        if (lengthMs <= 0) {
            return 0f;
        }

        // The app tells us the position at a certain moment; if it is playing,
        // add the time that has passed since that moment
        long positionMs = state.getPosition();
        if (state.getState() == PlaybackState.STATE_PLAYING) {
            long passedMs = SystemClock.elapsedRealtime() - state.getLastPositionUpdateTime();
            positionMs = positionMs + (long) (passedMs * state.getPlaybackSpeed());
        }
        float progress = (float) positionMs / (float) lengthMs;
        return progress;
    }

    // ---- Buttons ----

    public void playOrPause() {
        if (currentPlayer == null) {
            return;
        }
        if (isPlaying()) {
            currentPlayer.getTransportControls().pause();
        } else {
            currentPlayer.getTransportControls().play();
        }
    }

    public void nextSong() {
        if (currentPlayer != null) {
            currentPlayer.getTransportControls().skipToNext();
        }
    }

    public void previousSong() {
        if (currentPlayer != null) {
            currentPlayer.getTransportControls().skipToPrevious();
        }
    }

    // ---- Helpers ----

    // Pick which player to show: the one that is playing, otherwise the first one
    private void choosePlayer(List<MediaController> players) {
        MediaController chosen = null;
        if (players != null) {
            for (MediaController player : players) {
                PlaybackState state = player.getPlaybackState();
                boolean playing = false;
                if (state != null && state.getState() == PlaybackState.STATE_PLAYING) {
                    playing = true;
                }
                if (playing) {
                    chosen = player;
                    break;
                }
                if (chosen == null) {
                    chosen = player;
                }
            }
        }
        setCurrentPlayer(chosen);
    }

    // Switch to a new player and listen to its song changes
    private void setCurrentPlayer(MediaController newPlayer) {
        if (currentPlayer != null) {
            currentPlayer.unregisterCallback(songWatcher);
        }
        currentPlayer = newPlayer;
        if (currentPlayer != null) {
            currentPlayer.registerCallback(songWatcher);
        }
    }

    private MediaMetadata getMetadata() {
        if (currentPlayer == null) {
            return null;
        }
        return currentPlayer.getMetadata();
    }

    private PlaybackState getState() {
        if (currentPlayer == null) {
            return null;
        }
        return currentPlayer.getPlaybackState();
    }

    // Read one text field from the song information ("" if missing)
    private String readText(String key) {
        MediaMetadata metadata = getMetadata();
        if (metadata == null) {
            return "";
        }
        CharSequence text = metadata.getText(key);
        if (text == null) {
            return "";
        }
        return text.toString();
    }

    // Hears when music apps start or stop (for example you open Spotify)
    private class SessionsWatcher implements MediaSessionManager.OnActiveSessionsChangedListener {
        @Override
        public void onActiveSessionsChanged(List<MediaController> players) {
            choosePlayer(players);
            listener.onNowPlayingChanged();
        }
    }

    // Hears when the song, cover or play/pause state changes
    private class SongWatcher extends MediaController.Callback {
        @Override
        public void onMetadataChanged(MediaMetadata metadata) {
            listener.onNowPlayingChanged();
        }

        @Override
        public void onPlaybackStateChanged(PlaybackState state) {
            listener.onNowPlayingChanged();
        }

        @Override
        public void onSessionDestroyed() {
            setCurrentPlayer(null);
            listener.onNowPlayingChanged();
        }
    }
}
