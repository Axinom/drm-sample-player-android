package com.axinom.drm.sample.license.internal.task;

import android.annotation.SuppressLint;
import android.media.DeniedByServerException;
import android.media.MediaDrm;
import android.media.NotProvisionedException;
import android.media.ResourceBusyException;
import android.media.UnsupportedSchemeException;
import android.os.AsyncTask;
import android.os.Build;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.RequiresApi;

import com.axinom.drm.sample.license.LicenseManagerErrorCode;
import com.axinom.drm.sample.license.internal.exception.LicenseManagerException;
import com.axinom.drm.sample.license.internal.utils.LicenseFileUtils;
import com.axinom.drm.sample.license.internal.utils.RequestUtils;
import com.google.android.exoplayer2.C;

import java.io.IOException;
import java.util.Map;

/**
 * AsyncTask for checking that license is available and valid
 */

public class LicenseReleaseTask extends AsyncTask<LicenseReleaseTask.Params, Void, Void> {

    private static final String TAG = LicenseReleaseTask.class.getSimpleName();
    private String mManifestUrl;
    private LicenseManagerErrorCode mErrorCode = null;
    private String mErrorExtraData;
    private ILicenseReleaseTaskCallback mListener;
    private MediaDrm mMediaDrm;
    private byte[] mSessionId;
    private boolean mAllLicenseRelease;

    public LicenseReleaseTask(ILicenseReleaseTaskCallback listener) {
        mListener = listener;
    }

    public interface ILicenseReleaseTaskCallback {
        void onLicenseReleased(String manifestUrl);

        void onLicenseReleaseFailed(
                LicenseManagerErrorCode errorCode, String errorExtraData, String manifestUrl);

        void onAllLicensesReleased();

        void onAllLicensesReleaseFailed(LicenseManagerErrorCode errorCode, String errorExtraData);
    }

    @SuppressLint("ObsoleteSdkInt")
    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    @Override
    protected Void doInBackground(Params... params) {
        if (Build.VERSION.SDK_INT < 18) {
            mErrorCode = LicenseManagerErrorCode.ERROR_300;
            return null;
        }

        mAllLicenseRelease = params[0].deleteAll;
        mManifestUrl = params[0].manifestUrl;
        String defaultPath = params[0].defaultStoragePath;
        String licenseServerUrl = params[0].licenseServerUrl;
        Map<String, String> requestProperties = params[0].requestProperties;
        boolean stopOnLicenseServerFail = params[0].stopOnLicenseServerFail;

        // If current task requires to delete all licenses
        if (mAllLicenseRelease) {
            Log.d(TAG, "Releasing all licenses. Has license server URL: " +
                    !TextUtils.isEmpty(licenseServerUrl));
            // If no license server specified, just delete files
            if (TextUtils.isEmpty(licenseServerUrl)) {
                try {
                    LicenseFileUtils.deleteAllLicenses(defaultPath);
                    Log.d(TAG, "All license key file deleted! ");
                } catch (Exception e) {
                    onError(e);
                }
                return null;
            }

            // Find all license files and read their keys
            String[] allLicensePaths = LicenseFileUtils.getAllLicenseFilesPaths(defaultPath);
            for (String licensePath : allLicensePaths) {
                Log.d(TAG, "licensePath = " + licensePath);
                String manifestUrl;
                try {
                    manifestUrl = LicenseFileUtils.getNameFromBase64(licensePath);
                    Log.d(TAG, "licensePath = " + licensePath);
                } catch (Exception e) {
                    Log.d(TAG, "License file cannot be read, ignoring: " + licensePath);
                    continue;
                }

                Exception exception = null;
                try {
                    releaseLicenseFromServer(defaultPath, manifestUrl, licenseServerUrl, requestProperties);
                } catch (Exception e) {
                    if (stopOnLicenseServerFail) {
                        exception = e;
                    }
                } finally {
                    closeSession();
                }

                if (exception != null) {
                    onError(exception);
                    return null;
                }

                try {
                    Log.d(TAG, "Deleting license key file for manifest: " + manifestUrl);
                    LicenseFileUtils.deleteLicenseFile(defaultPath, manifestUrl);
                    Log.d(TAG, "License key file deleted! ");
                } catch (Exception e) {
                    onError(e);
                    return null;
                }
            }
        } else {
            Log.d(TAG, "Releasing one license. Has license server URL: " +
                    !TextUtils.isEmpty(licenseServerUrl));

            // If license server specified, try to release license on server
            if (!TextUtils.isEmpty(licenseServerUrl)) {
                Exception exception = null;
                try {
                    releaseLicenseFromServer(defaultPath, mManifestUrl, licenseServerUrl, requestProperties);
                } catch (Exception e) {
                    if (stopOnLicenseServerFail) {
                        exception = e;
                    }
                } finally {
                    closeSession();
                }

                if (exception != null) {
                    onError(exception);
                    return null;
                }
            }

            try {
                Log.d(TAG, "Deleting license key file for manifest: " + mManifestUrl);
                LicenseFileUtils.deleteLicenseFile(defaultPath, mManifestUrl);
                Log.d(TAG, "License key file deleted! ");
            } catch (Exception e) {
                onError(e);
                return null;
            }
        }

        return null;
    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    private void releaseLicenseFromServer(String defaultPath, String manifestUrl, String licenseServerUrl,
                                          Map<String, String> requestProperties) throws
            NotProvisionedException, UnsupportedSchemeException,
            ResourceBusyException, LicenseManagerException, IOException, DeniedByServerException {

        Log.d(TAG, "Trying to release keys for: " + manifestUrl);
        byte[] keySetId = LicenseFileUtils.readLicenseFile(defaultPath, manifestUrl);

        // Creating media DRM session
        if (mMediaDrm == null) {
            mMediaDrm = new MediaDrm(C.WIDEVINE_UUID);
            mSessionId = mMediaDrm.openSession();
        }

        // Ger request data from MediaDrm needed to be sent to License Server
        MediaDrm.KeyRequest keyRequest = mMediaDrm.getKeyRequest(
                keySetId, null, null, MediaDrm.KEY_TYPE_RELEASE, null
        );

        Log.d(TAG, "Keys for release acquired!");
        Log.d(TAG, "requestProperties: " + requestProperties);
        Log.d(TAG, "licenseServerUrl: " + licenseServerUrl);

        // Make license server post request and acquire response
        byte[] response = RequestUtils.executePost(
                licenseServerUrl, null, keyRequest.getData(), requestProperties);

        if (response == null || response.length == 0) {
            throw new LicenseManagerException(
                    LicenseManagerErrorCode.ERROR_302, "Server response is empty");
        }

        mMediaDrm.provideKeyResponse(keySetId, response);

        Log.d(TAG, "Keys released!");
    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    private void closeSession() {
        if (mMediaDrm != null && mSessionId != null) mMediaDrm.closeSession(mSessionId);
    }

    private void onError(Exception e) {
        Log.d(TAG, "License check failed with error:\n " + e.toString());
        if (e instanceof LicenseManagerException) {
            mErrorCode = ((LicenseManagerException) e).getErrorCode();
            mErrorExtraData = ((LicenseManagerException) e).getExtraData();
        } else if (e instanceof UnsupportedSchemeException) {
            mErrorCode = LicenseManagerErrorCode.ERROR_301;
            mErrorExtraData = e.toString();
        } else {
            mErrorCode = LicenseManagerErrorCode.ERROR_302;
            if (e.getCause() != null) {
                mErrorExtraData = e.getCause().toString();
            } else {
                mErrorExtraData = e.toString();
            }
        }
    }

    @Override
    protected void onPostExecute(Void voidParam) {
        if (mListener != null) {
            if (mAllLicenseRelease) {
                if (mErrorCode == null) mListener.onAllLicensesReleased();
                else mListener.onAllLicensesReleaseFailed(mErrorCode, mErrorExtraData);
            } else {
                if (mErrorCode == null) mListener.onLicenseReleased(mManifestUrl);
                else mListener.onLicenseReleaseFailed(mErrorCode, mErrorExtraData, mManifestUrl);
            }
        }
        mListener = null;
        mMediaDrm = null;
        mSessionId = null;
    }

    public static class Params {
        final String manifestUrl, defaultStoragePath, licenseServerUrl;
        final boolean deleteAll, stopOnLicenseServerFail;
        final Map<String, String> requestProperties;

        public Params(String licenseServerUrl, String manifestUrl, String defaultStoragePath,
                      boolean deleteAll, boolean stopOnLicenseServerFail,
                      Map<String, String> requestProperties) {
            this.licenseServerUrl = licenseServerUrl;
            this.manifestUrl = manifestUrl;
            this.defaultStoragePath = defaultStoragePath;
            this.deleteAll = deleteAll;
            this.stopOnLicenseServerFail = stopOnLicenseServerFail;
            this.requestProperties = requestProperties;
        }
    }
}
