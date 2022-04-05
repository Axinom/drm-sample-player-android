package com.axinom.drm.sample.license.internal.utils;

import android.media.MediaDrm;
import android.os.Build;
import android.util.Base64;
import android.util.Log;
import android.util.Pair;

import androidx.annotation.RequiresApi;

import com.axinom.drm.sample.license.internal.model.DrmMessage;
import com.axinom.drm.sample.license.internal.model.SchemeData;
import com.google.android.exoplayer2.C;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Drm utils
 */
public class DrmUtils {

    private static final String TAG = DrmUtils.class.getSimpleName();
    /**
     * Widevine specific key status field name for the remaining license duration, in seconds.
     */
    private static final String PROPERTY_LICENSE_DURATION_REMAINING = "LicenseDurationRemaining";
    /**
     * Widevine specific key status field name for the remaining playback duration, in seconds.
     */
    private static final String PROPERTY_PLAYBACK_DURATION_REMAINING = "PlaybackDurationRemaining";

    public static DrmMessage parseDrmString(String drmMessageString) {
        try {
            String token = drmMessageString.substring(
                    drmMessageString.indexOf(".") + 1, drmMessageString.lastIndexOf("."));
            String jsonString = new String(Base64.decode(token, Base64.DEFAULT));
            JSONObject jObject = new JSONObject(jsonString);
            DrmMessage drmMessage = new DrmMessage();
            drmMessage.version = jObject.has("version") ? jObject.getString("version") : "";
            drmMessage.comKeyId = jObject.has("com_key_id") ? jObject.getString("com_key_id") : "";
            if (jObject.has("message")) {
                JSONObject jMessage = jObject.getJSONObject("message");
                drmMessage.persistent =
                        jMessage.has("persistent") && jMessage.getBoolean("persistent")
                                || jMessage.has("license")
                                && jMessage.getJSONObject("license").has("allow_persistence")
                                && jMessage.getJSONObject("license").getBoolean("allow_persistence");
                drmMessage.keysBasedOnRequest =
                        jMessage.has("keys_based_on_request") && jMessage.getBoolean("keys_based_on_request");
            }
            return drmMessage;
        } catch (Exception e) {
            Log.e(DrmUtils.class.getSimpleName(), "Problem while drm message parsing: " + e.toString());
        }
        return null;
    }

    public static byte[] getSchemeInitData(SchemeData data, UUID uuid) {
        byte[] schemeInitData = data.data;
        if (Build.VERSION.SDK_INT < 21) {
            // Prior to L the Widevine CDM required data to be extracted from the PSSH atom.
            byte[] psshData = PsshAtomUtils.parseSchemeSpecificData(schemeInitData, uuid);
            if (psshData == null) {
                Log.e(TAG, "Extraction failed. schemeData isn't a Widevine PSSH atom, so leave it unchanged.");
            } else {
                schemeInitData = psshData;
            }
        }
        return schemeInitData;
    }

    /**
     * Extracts {@link SchemeData} suitable for the given DRM scheme {@link UUID}.
     *
     * @param drmInitData The drmInitData from which to extract the {@link SchemeData}.
     * @param uuid        The UUID.
     *                    returned.
     * @return The extracted {@link SchemeData}, or null if no suitable data is present.
     */
    public static SchemeData getSchemeData(SchemeData[] drmInitData, UUID uuid) {
        // Look for matching scheme data (matching the Common PSSH box for ClearKey).
        List<SchemeData> matchingSchemeDatas = new ArrayList<>(drmInitData.length);
        for (SchemeData schemeData : drmInitData) {
            boolean uuidMatches = schemeData.matches(uuid)
                    || (C.CLEARKEY_UUID.equals(uuid) && schemeData.matches(C.COMMON_PSSH_UUID));
            if (uuidMatches && schemeData.data != null) {
                matchingSchemeDatas.add(schemeData);
            }
        }

        if (matchingSchemeDatas.isEmpty()) {
            return null;
        }

        // For Widevine PSSH boxes, prefer V1 boxes from API 23 and V0 before.
        if (C.WIDEVINE_UUID.equals(uuid)) {
            for (int i = 0; i < matchingSchemeDatas.size(); i++) {
                SchemeData matchingSchemeData = matchingSchemeDatas.get(i);
                int version = matchingSchemeData.hasData()
                        ? PsshAtomUtils.parseVersion(matchingSchemeData.data) : -1;
                if (Build.VERSION.SDK_INT < 23 && version == 0) {
                    return matchingSchemeData;
                } else if (Build.VERSION.SDK_INT >= 23 && version == 1) {
                    return matchingSchemeData;
                }
            }
        }

        // If we don't have any special handling, prefer the first matching scheme data.
        return matchingSchemeDatas.get(0);
    }

    public static String getSchemeMimeType(SchemeData data, UUID uuid) {
        String schemeMimeType = data.mimeType;
        if (Build.VERSION.SDK_INT < 26 && C.CLEARKEY_UUID.equals(uuid)
                && ("video/mp4".equals(schemeMimeType) || "audio/mp4".equals(schemeMimeType))) {
            // Prior to API level 26 the ClearKey CDM only accepted "cenc" as the scheme for MP4.
            schemeMimeType = "cenc";
        }
        return schemeMimeType;
    }

    /**
     * Returns license and playback durations remaining in seconds.
     *
     * @param drmSession The drm session to query.
     * @param sessionId  Session id
     * @return  A {@link Pair} consisting of the remaining license and playback durations in seconds,
     *      * or null if called before the session has been opened or after it's been released.
     */
    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    public static Pair<Long, Long> getLicenseDurationRemainingSec(MediaDrm drmSession, byte[] sessionId) {
        Map<String, String> keyStatus = drmSession.queryKeyStatus(sessionId);
        //noinspection ConstantConditions
        if (keyStatus == null) {
            return null;
        }
        return new Pair<>(getDurationRemainingSec(keyStatus, PROPERTY_LICENSE_DURATION_REMAINING),
                getDurationRemainingSec(keyStatus, PROPERTY_PLAYBACK_DURATION_REMAINING));
    }

    private static long getDurationRemainingSec(Map<String, String> keyStatus, String property) {
        if (keyStatus != null) {
            try {
                String value = keyStatus.get(property);
                if (value != null) {
                    return Long.parseLong(value);
                }
            } catch (NumberFormatException e) {
                // do nothing.
            }
        }
        return C.TIME_UNSET;
    }

}
