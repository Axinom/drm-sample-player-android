package com.axinom.drm.sample.license.internal.utils;

import static com.google.android.exoplayer2.util.Assertions.checkNotNull;

import android.net.Uri;
import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;
import android.util.Pair;
import android.util.Xml;

import androidx.annotation.Nullable;
import androidx.collection.LruCache;

import com.axinom.drm.sample.license.internal.model.Manifest;
import com.axinom.drm.sample.license.internal.model.SchemeData;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.util.Util;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * Utility methods for saving, parsing and accessing {@link Manifest} models from LruCache
 */
@SuppressWarnings("unused")
public class ManifestUtils {

    private static final String TAG = ManifestUtils.class.getSimpleName();

    // Shared
    private static final String EMPTY = "";
    private static final String CONTENT_PROTECTION = "ContentProtection";

    private static final Pattern REGEX_KEYFORMATVERSIONS =
            Pattern.compile("KEYFORMATVERSIONS=\"(.+?)\"");
    private static final String KEYFORMAT_WIDEVINE_PSSH_BINARY =
            "urn:uuid:edef8ba9-79d6-4ace-a3c8-27dcd51d21ed";
    private static final Pattern REGEX_URI = Pattern.compile("URI=\"(.+?)\"");
    private static final String KEYFORMAT_WIDEVINE_PSSH_JSON = "com.widevine";
    private static final Pattern REGEX_VARIABLE_REFERENCE =
            Pattern.compile("\\{\\$([a-zA-Z0-9\\-_]+)\\}");
    private static final String TAG_SESSION_KEY = "#EXT-X-SESSION-KEY";
    private static final Pattern REGEX_KEYFORMAT = Pattern.compile("KEYFORMAT=\"(.+?)\"");
    private static final String KEYFORMAT_IDENTITY = "identity";

    private static final LruCache<String, Manifest> manifestLruCache = new LruCache<>(10);

    /**
     * Adding specified manifest to LruCache
     *
     * @param key      url to video manifest file used as LruCache key
     * @param manifest manifest model as LruCache value
     */
    public static void addManifest(String key, Manifest manifest) {
        if (!TextUtils.isEmpty(key) && manifest != null) {
            if (manifestLruCache.get(key) == null)
                manifestLruCache.put(key, manifest);
        }
    }

    /**
     * Remove specified manifest from LruCache
     *
     * @param key url to video manifest file used as LruCache key
     */
    public static void removeManifest(String key) {
        if (!TextUtils.isEmpty(key)) {
            manifestLruCache.remove(key);
        }
    }

    /**
     * Get specified manifest from LruCache
     *
     * @param key url to video manifest file used as LruCache key
     * @return Parsed manifest object
     */
    public static Manifest getManifest(String key) {
        return TextUtils.isEmpty(key) ? null : manifestLruCache.get(key);
    }

    /**
     * Parse DASH video manifest and add values to Manifest model.
     *
     * @param data An array containing the response data.
     * @return Manifest model object
     * @throws XmlPullParserException error while parsing
     * @throws IOException            IO exception
     */
    public static Manifest parseMpdManifest(byte[] data) throws XmlPullParserException, IOException {
        Manifest manifest = new Manifest();
        ArrayList<SchemeData> schemeDatas = new ArrayList<>();
        XmlPullParser xpp = Xml.newPullParser();
        xpp.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
        xpp.setInput(new ByteArrayInputStream(data), null);

        String currentTag = EMPTY;

        while (xpp.getEventType() != XmlPullParser.END_DOCUMENT) {
            if (isStartTag(xpp, CONTENT_PROTECTION)) {
                Pair<String, SchemeData> schemeDataPair = parseContentProtection(xpp);
                Log.d(TAG, "found schemeDataPair: " + schemeDataPair);
                if (schemeDataPair != null) schemeDatas.add(schemeDataPair.second);
            }
            xpp.next();
        }

        manifest.schemeDatas = new SchemeData[schemeDatas.size()];
        schemeDatas.toArray(manifest.schemeDatas);

        return manifest;
    }

    /**
     * Parses a ContentProtection element.
     *
     * @param xpp The parser from which to read.
     * @return The scheme type and/or {@link SchemeData} parsed from the ContentProtection element.
     * Either or both may be null, depending on the ContentProtection element being parsed.
     * @throws XmlPullParserException If an error occurs parsing the element.
     * @throws IOException            If an error occurs reading the element.
     */
    private static Pair<String, SchemeData> parseContentProtection(XmlPullParser xpp)
            throws XmlPullParserException, IOException {
        String schemeType = null;
        byte[] data = null;
        UUID uuid = null;
        boolean requiresSecureDecoder = false;

        String schemeIdUri = xpp.getAttributeValue(null, "schemeIdUri");
        if (schemeIdUri != null) {
            switch (LicenseManagerUtils.toLowerInvariant(schemeIdUri)) {
                case "urn:mpeg:dash:mp4protection:2011":
                    schemeType = xpp.getAttributeValue(null, "value");
                    String defaultKid = xpp.getAttributeValue(null, "cenc:default_KID");
                    if (defaultKid != null && !"00000000-0000-0000-0000-000000000000".equals(defaultKid)) {
                        UUID keyId = UUID.fromString(defaultKid);
                        data = PsshAtomUtils.buildPsshAtom(new UUID[]{keyId}, null);
                        uuid = C.COMMON_PSSH_UUID;
                    }
                    break;
                case "urn:uuid:edef8ba9-79d6-4ace-a3c8-27dcd51d21ed":
                    uuid = C.WIDEVINE_UUID;
                    break;
                default:
                    break;
            }
        }

        do {
            xpp.next();
            if (isStartTag(xpp, "widevine:license")) {
                String robustnessLevel = xpp.getAttributeValue(null, "robustness_level");
                requiresSecureDecoder = robustnessLevel != null && robustnessLevel.startsWith("HW");
            } else if (data == null) {
                if (isStartTag(xpp, "cenc:pssh") && xpp.next() == XmlPullParser.TEXT) {
                    // The cenc:pssh element is defined in 23001-7:2015.
                    data = Base64.decode(xpp.getText(), Base64.DEFAULT);
                    uuid = PsshAtomUtils.parseUuid(data);
                    if (uuid == null) {
                        Log.w(TAG, "Skipping malformed cenc:pssh data");
                        data = null;
                    }
                }
            }
        } while (!isEndTag(xpp, "ContentProtection"));
        SchemeData schemeData = uuid != null
                ? new SchemeData(uuid, "video/mp4", data, requiresSecureDecoder) : null;
        return Pair.create(schemeType, schemeData);
    }

    /**
     * Returns whether the current event is an end tag with the specified name.
     *
     * @param xpp  The {@link XmlPullParser} to query.
     * @param name The specified name.
     * @return Whether the current event is an end tag with the specified name.
     * @throws XmlPullParserException If an error occurs querying the parser.
     */
    @SuppressWarnings("SameParameterValue")
    private static boolean isEndTag(XmlPullParser xpp, String name) throws XmlPullParserException {
        return isEndTag(xpp) && xpp.getName().equals(name);
    }

    /**
     * Returns whether the current event is an end tag.
     *
     * @param xpp The {@link XmlPullParser} to query.
     * @return Whether the current event is an end tag.
     * @throws XmlPullParserException If an error occurs querying the parser.
     */
    private static boolean isEndTag(XmlPullParser xpp) throws XmlPullParserException {
        return xpp.getEventType() == XmlPullParser.END_TAG;
    }

    /**
     * Returns whether the current event is a start tag with the specified name.
     *
     * @param xpp  The {@link XmlPullParser} to query.
     * @param name The specified name.
     * @return Whether the current event is a start tag with the specified name.
     * @throws XmlPullParserException If an error occurs querying the parser.
     */
    private static boolean isStartTag(XmlPullParser xpp, String name)
            throws XmlPullParserException {
        return isStartTag(xpp) && xpp.getName().equals(name);
    }

    /**
     * Returns whether the current event is a start tag.
     *
     * @param xpp The {@link XmlPullParser} to query.
     * @return Whether the current event is a start tag.
     * @throws XmlPullParserException If an error occurs querying the parser.
     */
    private static boolean isStartTag(XmlPullParser xpp) throws XmlPullParserException {
        return xpp.getEventType() == XmlPullParser.START_TAG;
    }

    /**
     * Check that current source path is a tar file
     *
     * @param mediaSourcePath Path to tar file with mpd ending
     * @return true if path is tar
     */
    public static boolean isTarSource(String mediaSourcePath) {
        return false;
        /*
        if (TextUtils.isEmpty(mediaSourcePath)) return false;
        String path = mediaSourcePath.toLowerCase();
        return path.endsWith(".tar") || path.contains(".tar/");
         */
    }

    public static Manifest parseM3U8Manifest(Uri uri, byte[] data) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(data)));
        HashMap<String, String> variableDefinitions = new HashMap<>();
        Manifest manifest = new Manifest();
        ArrayList<SchemeData> schemeDatas = new ArrayList<>();
        String line;
        try {
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.startsWith(TAG_SESSION_KEY)) {
                        String keyFormat = parseOptionalStringAttr(
                                line, REGEX_KEYFORMAT, KEYFORMAT_IDENTITY, variableDefinitions);
                        SchemeData schemeData = parseDrmSchemeData(line, keyFormat, variableDefinitions);
                    Log.d(TAG, "found schemeData: " + schemeData);
                        if (schemeData != null) schemeDatas.add(schemeData);
                }
            }
        } finally {
            Util.closeQuietly(reader);
        }

        manifest.schemeDatas = new SchemeData[schemeDatas.size()];
        schemeDatas.toArray(manifest.schemeDatas);

        return manifest;
    }

    @Nullable
    private static SchemeData parseDrmSchemeData(
            String line, String keyFormat, Map<String, String> variableDefinitions)
            throws IOException {
        String keyFormatVersions =
                parseOptionalStringAttr(line, REGEX_KEYFORMATVERSIONS, "1", variableDefinitions);
        if (KEYFORMAT_WIDEVINE_PSSH_BINARY.equals(keyFormat)) {
            String uriString = parseStringAttr(line, variableDefinitions);
            return new SchemeData(
                    com.google.android.exoplayer2.C.WIDEVINE_UUID,
                    MimeTypes.VIDEO_MP4,
                    Base64.decode(uriString.substring(uriString.indexOf(',')), Base64.DEFAULT));
        } else if (KEYFORMAT_WIDEVINE_PSSH_JSON.equals(keyFormat)) {
            return new SchemeData(com.google.android.exoplayer2.C.WIDEVINE_UUID, "hls", Util.getUtf8Bytes(line));
        }
        return null;
    }

    private static @Nullable String parseOptionalStringAttr(
            String line, Map<String, String> variableDefinitions) {
        return parseOptionalStringAttr(line, ManifestUtils.REGEX_URI, null, variableDefinitions);
    }

    private static String parseOptionalStringAttr(
            String line,
            Pattern pattern,
            String defaultValue,
            Map<String, String> variableDefinitions) {
        Matcher matcher = pattern.matcher(line);
        String value = matcher.find() ? checkNotNull(matcher.group(1)) : defaultValue;
        return variableDefinitions.isEmpty() || value == null
                ? value
                : replaceVariableReferences(value, variableDefinitions);
    }

    private static String replaceVariableReferences(
            String string, Map<String, String> variableDefinitions) {
        Matcher matcher = REGEX_VARIABLE_REFERENCE.matcher(string);
        StringBuffer stringWithReplacements = new StringBuffer();
        while (matcher.find()) {
            String groupName = matcher.group(1);
            if (variableDefinitions.containsKey(groupName)) {
                matcher.appendReplacement(stringWithReplacements, Matcher.quoteReplacement(
                        Objects.requireNonNull(variableDefinitions.get(groupName))));
            }
        }
        matcher.appendTail(stringWithReplacements);
        return stringWithReplacements.toString();
    }

    private static String parseStringAttr(
            String line, Map<String, String> variableDefinitions)
            throws IOException {
        String value = parseOptionalStringAttr(line, variableDefinitions);
        if (value != null) {
            return value;
        } else {
            throw new IOException(
                    "Couldn't match " + ManifestUtils.REGEX_URI.pattern() + " in " + line);
        }
    }
}
