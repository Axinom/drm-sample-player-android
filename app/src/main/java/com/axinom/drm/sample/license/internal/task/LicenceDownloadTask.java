package com.axinom.drm.sample.license.internal.task;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.media.DeniedByServerException;
import android.media.MediaDrm;
import android.media.NotProvisionedException;
import android.media.ResourceBusyException;
import android.media.UnsupportedSchemeException;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.util.Log;
import android.util.Pair;

import com.axinom.drm.sample.license.LicenseManagerErrorCode;
import com.axinom.drm.sample.license.internal.exception.LicenseManagerException;
import com.axinom.drm.sample.license.internal.model.DrmMessage;
import com.axinom.drm.sample.license.internal.model.Manifest;
import com.axinom.drm.sample.license.internal.model.SchemeData;
import com.axinom.drm.sample.license.internal.utils.DrmUtils;
import com.axinom.drm.sample.license.internal.utils.LicenseFileUtils;
import com.axinom.drm.sample.license.internal.utils.ManifestUtils;
import com.axinom.drm.sample.license.internal.utils.RequestUtils;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.util.Util;

import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.util.Map;

/**
 * Class is responsible for making license request and storing license keys to device storage
 */

public class LicenceDownloadTask extends AsyncTask<LicenceDownloadTask.Params, Void, byte[]> {

    private static final String TAG = LicenceDownloadTask.class.getSimpleName();

    private String mManifestUrl;
    private String mErrorExtraData;
    private LicenseManagerErrorCode mErrorCode = null;
    private boolean mWithResult;
    private boolean mAutoSave;
    private ILicenceDownloadTaskCallback mListener;
    private MediaDrm mMediaDrm;
    private byte[] mSessionId;

    public interface ILicenceDownloadTaskCallback {
        void onLicenseDownloadedWithResult(String manifestUrl, byte[] keyIds);

        void onLicenseDownloaded(String manifestUrl);

        void onLicenseDownloadFailed(LicenseManagerErrorCode errorCode, String errorExtraData, String manifestUrl);
    }

    public LicenceDownloadTask(ILicenceDownloadTaskCallback listener, boolean withResult, boolean autoSave) {
        mListener = listener;
        mWithResult = withResult;
        mAutoSave = autoSave;
    }

    @SuppressLint("ObsoleteSdkInt")
    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
    @Override
    protected byte[] doInBackground(Params... params) {
        if (Build.VERSION.SDK_INT < 18) {
            mErrorCode = LicenseManagerErrorCode.ERROR_300;
            return null;
        }

        byte[] keySetId = null;
        try {
            keySetId = getKeySetId(params[0]);
        } catch (Exception e) {
            // NotProvisionedException happens when device was not previously provisioned.
            // In this case we will try to provision device firstly and then make license request retry.
            if (e instanceof NotProvisionedException) {
                keySetId = onNoProvisionError(params[0]);
            } else {
                onError(e);
            }
        } finally {
            if (mMediaDrm != null && mSessionId != null) mMediaDrm.closeSession(mSessionId);
        }

        return keySetId;
    }

    private byte[] onNoProvisionError(Params params) {
        boolean result = makeProvisioning();
        if (!result) {
            mErrorCode = LicenseManagerErrorCode.ERROR_309;
            return null;
        }

        // Provisioning was successfully done. Make license request.
        byte[] keySetId = null;
        try {
            keySetId = getKeySetId(params);
        } catch (Exception e) {
            onError(e);
        }

        return keySetId;
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
    private boolean makeProvisioning() {
        // Get data required for provisioning
        MediaDrm.ProvisionRequest request = mMediaDrm.getProvisionRequest();
        // Prepare url
        String url = request.getDefaultUrl() + "&signedRequest=" + new String(request.getData());
        try {
            // make request to default provisioning server (usually it is google server)
            byte[] response = RequestUtils.executePost(url, null, null, null);
            // Provide provisioning response to MediaDrm
            mMediaDrm.provideProvisionResponse(response);
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }

        return false;
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
    private byte[] getKeySetId(Params params) throws NotProvisionedException,
            UnsupportedSchemeException, ResourceBusyException, IOException, XmlPullParserException,
            DeniedByServerException, LicenseManagerException {
        mManifestUrl = params.manifestUrl;

        String axDrmMessage = params.axDrmMessage;
        Log.d(TAG, "axDrmMessage: " + axDrmMessage);
        // If message is not properly formatted or has no "persistent" flag,
        // then exception will be thrown
        checkDrmMessage(axDrmMessage);

        // Creating media DRM session
        if (mMediaDrm == null) {
            mMediaDrm = new MediaDrm(C.WIDEVINE_UUID);
            mSessionId = mMediaDrm.openSession();
        }

        Manifest manifest = null;
        byte[] manifestData = RequestUtils.getManifest(mManifestUrl, null);
        int format = Util.inferContentType(mManifestUrl);
        // Parsing manifest to get encryption scheme data
        if (format == com.google.android.exoplayer2.C.TYPE_DASH) {
            manifest = ManifestUtils.parseMpdManifest(manifestData);
        } else if (format == com.google.android.exoplayer2.C.TYPE_HLS) {
            manifest = ManifestUtils.parseM3U8Manifest(Uri.parse(mManifestUrl), manifestData);
        }
        SchemeData schemeData = null;
        if (manifest != null) {
            schemeData = DrmUtils.getSchemeData(manifest.schemeDatas, C.WIDEVINE_UUID);
        }
        if (schemeData == null) {
            throw new LicenseManagerException(
                    LicenseManagerErrorCode.ERROR_301, "schemeData is null");
        }

        // Get data required for license initialization
        byte[] initData = DrmUtils.getSchemeInitData(schemeData, C.WIDEVINE_UUID);
        String mimeType = DrmUtils.getSchemeMimeType(schemeData, C.WIDEVINE_UUID);

        if (initData == null) {
            throw new LicenseManagerException(
                    LicenseManagerErrorCode.ERROR_301, "DRM initData is null");
        }

        // Get request data from MediaDrm needed to be sent to License Server
        MediaDrm.KeyRequest keyRequest = mMediaDrm.getKeyRequest(
                mSessionId, initData, mimeType, MediaDrm.KEY_TYPE_OFFLINE, null);

        Map<String, String> requestProperties = params.requestProperties;
        String licenseServerUrl = params.licenseServerUrl;
        Log.d(TAG, "requestProperties: " + requestProperties);
        Log.d(TAG, "licenseServerUrl: " + licenseServerUrl);

        // Make license server post request and acquire response
        byte[] response = RequestUtils.executePost(
                licenseServerUrl, axDrmMessage, keyRequest.getData(), requestProperties);

        if (response == null || response.length == 0) {
            throw new LicenseManagerException(
                    LicenseManagerErrorCode.ERROR_302, "Server response is empty");
        }

        // Provide license server response to MediaDrm. MediaDrm return keys, required to restore
        // license later.
        byte[] keySetId = mMediaDrm.provideKeyResponse(mSessionId, response);
        if (keySetId == null || keySetId.length == 0) {
            Log.d(TAG, "keySetId is null");
            throw new LicenseManagerException(
                    LicenseManagerErrorCode.ERROR_302, "keySetId is empty");
        }
        Log.d(TAG, "keySetId: " + new String(keySetId));

        Pair<Long, Long> remainingSec = DrmUtils.getLicenseDurationRemainingSec(mMediaDrm, mSessionId);
        Log.d(TAG, "remainingSec pair: " + remainingSec);
        if (remainingSec != null && remainingSec.first <= params.minExpireSecond) {
            throw new LicenseManagerException(LicenseManagerErrorCode.ERROR_308);
        }

        // Saving keys to file in the specific folder
        Log.d(TAG, "Auto save is active: " + mAutoSave);
        if (mAutoSave) {
            LicenseFileUtils.writeLicenseFile(params.defaultStoragePath, mManifestUrl, keySetId);
            Log.d(TAG, "Path to licnese file: " + params.defaultStoragePath);
        }

        return keySetId;
    }

    private void checkDrmMessage(String drmMessageString) throws LicenseManagerException {
        DrmMessage drmMessage = DrmUtils.parseDrmString(drmMessageString);
        if (drmMessage == null) {
            throw new LicenseManagerException(LicenseManagerErrorCode.ERROR_306);
        }

        if (!drmMessage.persistent) {
            throw new LicenseManagerException(LicenseManagerErrorCode.ERROR_307);
        }
    }

    private void onError(Exception e) {
        Log.d(TAG, "License download failed with error:\n " + e.toString());
        if (e instanceof LicenseManagerException) {
            mErrorCode = ((LicenseManagerException) e).getErrorCode();
            mErrorExtraData = ((LicenseManagerException) e).getExtraData();
        } else if (e instanceof UnsupportedSchemeException) {
            mErrorCode = LicenseManagerErrorCode.ERROR_301;
            mErrorExtraData = e.getMessage();
        } else {
            mErrorCode = LicenseManagerErrorCode.ERROR_302;
            if (e.getCause() != null) {
                mErrorExtraData = e.getCause().getMessage();
            } else {
                mErrorExtraData = e.getMessage();
            }
        }
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
    @Override
    protected void onPostExecute(byte[] keyIds) {
        if (mListener != null) {
            if (keyIds != null) {
                if (mWithResult) mListener.onLicenseDownloadedWithResult(mManifestUrl, keyIds);
                else mListener.onLicenseDownloaded(mManifestUrl);
            } else {
                mListener.onLicenseDownloadFailed(mErrorCode, mErrorExtraData, mManifestUrl);
            }
        }
        if (mMediaDrm != null) mMediaDrm.setOnEventListener(null);
        mListener = null;
        mMediaDrm = null;
        mSessionId = null;
    }

    public static class Params {
        final long minExpireSecond;
        final Map<String, String> requestProperties;
        final String licenseServerUrl, manifestUrl, axDrmMessage, defaultStoragePath;

        public Params(Map<String, String> requestProperties, String manifestUrl,
                      String licenseServerUrl, String axDrmMessage,
                      String defaultStoragePath, long minExpireSecond) {
            this.requestProperties = requestProperties;
            this.manifestUrl = manifestUrl;
            this.licenseServerUrl = licenseServerUrl;
            this.axDrmMessage = axDrmMessage;
            this.defaultStoragePath = defaultStoragePath;
            this.minExpireSecond = minExpireSecond;
        }
    }
}
