package com.axinom.drm.sample.util;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.MediaItem;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.UUID;

public class Utility {

    // Derives a DRM scheme from DRM UUID
    public static String getDrmSchemeFromUuid(UUID uuid) {
        if (uuid != null) {
            if (uuid == C.WIDEVINE_UUID) {
                return "widevine";
            } else if (uuid == C.PLAYREADY_UUID) {
                return "playready";
            } else if (uuid == C.CLEARKEY_UUID) {
                return "clearkey";
            }
        }
        return "";
    }

    // Utility method for returning the PlaybackProperties of the media
    public static MediaItem.LocalConfiguration getPlaybackProperties(MediaItem mediaItem) {
        if (mediaItem != null) {
            return mediaItem.localConfiguration;
        } else {
            return null;
        }
    }

    // Utility method for returning the DrmConfiguration of the media
    public static MediaItem.DrmConfiguration getDrmConfiguration(MediaItem mediaItem) {
        if (getPlaybackProperties(mediaItem) != null) {
            return getPlaybackProperties(mediaItem).drmConfiguration;
        } else {
            return null;
        }
    }

    // Returns current time stamp for displaying in console output
    public static String getCurrentTime() {
        SimpleDateFormat simpleDateFormat
                = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US);
        Date date = new Date();
        return "(" + simpleDateFormat.format(date) + ") ";
    }

}
