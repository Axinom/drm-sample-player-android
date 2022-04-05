package com.axinom.drm.sample.license.internal.utils;

import android.text.TextUtils;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Map;

/**
 * Request utility functions.
 */
public class RequestUtils {

    private static final String TAG = RequestUtils.class.getSimpleName();

    @SuppressWarnings("SameParameterValue")
    public static byte[] getManifest(String path, Map<String, String> requestProperties)
            throws IOException {

        Log.d(TAG, "Getting manifest from: " + path);

        if (!path.toLowerCase().startsWith("http://") && !path.toLowerCase().startsWith("https://")) {
            Log.d(TAG, "Path is local! Loading from file.");
            byte[] manifestFromFile = LicenseFileUtils.readFullFile(path);
            if (manifestFromFile == null) {
                throw new IOException("Problem while reading local file");
            }
            return manifestFromFile;
        }

        Log.d(TAG, "Path is not local! Loading from network.");
        InputStream in;
        HttpURLConnection urlConnection = null;
        try {
            urlConnection = (HttpURLConnection) new URL(path).openConnection();
            urlConnection.setRequestMethod("GET");
            urlConnection.setDoInput(true);
            if (requestProperties != null) {
                for (Map.Entry<String, String> requestProperty : requestProperties.entrySet()) {
                    urlConnection.setRequestProperty(requestProperty.getKey(), requestProperty.getValue());
                }
            }
            int responseCode = urlConnection.getResponseCode();
            Log.d(TAG, "Connection response code: " + responseCode);
            if (responseCode >= 400 && responseCode <= 499) {
                throw new IOException("Unexpected response status code: " + responseCode);
            }
            in = new BufferedInputStream(urlConnection.getInputStream());

        } catch (Exception e) {
            e.printStackTrace();
            if (urlConnection != null) urlConnection.disconnect();
            throw new IOException(e);
        }

        byte[] response = convertInputStreamToByteArray(in);
        urlConnection.disconnect();
        return response;
    }

    public static byte[] executePost(String url, String drmMessage, byte[] data,
                                     Map<String, String> requestProperties) throws IOException {
        Log.d(TAG, "Executing license server post request: " + url);
        HttpURLConnection urlConnection = null;
        InputStream in;
        try {
            urlConnection = (HttpURLConnection) new URL(url).openConnection();
            urlConnection.setRequestMethod("POST");
            urlConnection.setDoOutput(data != null);
            urlConnection.setDoInput(true);
            if (requestProperties != null) {
                for (Map.Entry<String, String> requestProperty : requestProperties.entrySet()) {
                    urlConnection.setRequestProperty(requestProperty.getKey(), requestProperty.getValue());
                }
            }
            if (!TextUtils.isEmpty(drmMessage))
                urlConnection.setRequestProperty("X-AxDRM-Message", drmMessage);
            if (data != null) {
                OutputStream out = new BufferedOutputStream(urlConnection.getOutputStream());
                out.write(data);
                out.close();
            }
            int responseCode = urlConnection.getResponseCode();
            Log.d(TAG, "Connection response code: " + responseCode);
            if (responseCode >= 400 && responseCode <= 499) {
                throw new IOException("Unexpected response status code: " + responseCode);
            }
            in = new BufferedInputStream(urlConnection.getInputStream());
        } catch (Exception e) {
            e.printStackTrace();
            if (urlConnection != null) urlConnection.disconnect();
            throw new IOException(e);
        }

        byte[] response = convertInputStreamToByteArray(in);
        urlConnection.disconnect();
        return response;
    }

    private static byte[] convertInputStreamToByteArray(InputStream inputStream) throws IOException {
        byte[] bytes;
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] data = new byte[1024];
        int count;
        while ((count = inputStream.read(data)) != -1) {
            bos.write(data, 0, count);
        }
        bos.flush();
        bos.close();
        inputStream.close();
        bytes = bos.toByteArray();
        return bytes;
    }

}
