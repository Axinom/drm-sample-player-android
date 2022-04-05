package com.axinom.drm.sample.license.internal.task;

import android.annotation.SuppressLint;
import android.media.MediaDrm;
import android.media.UnsupportedSchemeException;
import android.os.AsyncTask;
import android.os.Build;
import android.util.Log;
import android.util.Pair;

import androidx.annotation.RequiresApi;

import com.axinom.drm.sample.license.LicenseManagerErrorCode;
import com.axinom.drm.sample.license.internal.exception.LicenseManagerException;
import com.axinom.drm.sample.license.internal.utils.DrmUtils;
import com.axinom.drm.sample.license.internal.utils.LicenseFileUtils;
import com.google.android.exoplayer2.C;

/**
 * AsyncTask for checking that license is available and valid
 */

public class LicenseCheckTask extends AsyncTask<LicenseCheckTask.Params, Void, Boolean> {

    private static final String TAG = LicenseCheckTask.class.getSimpleName();
    private String mManifestUrl;
    private LicenseManagerErrorCode mErrorCode = null;
    private String mErrorExtraData;
    private ILicenceCheckTaskCallback mListener;
    private MediaDrm mMediaDrm;
    private byte[] mSessionId;

    public LicenseCheckTask(ILicenceCheckTaskCallback listener) {
        mListener = listener;
    }

    public interface ILicenceCheckTaskCallback {
        void onLicenseCheck(Boolean isValid, String manifestUrl);

        void onLicenseCheckFailed(LicenseManagerErrorCode errorCode, String errorExtraData, String manifestUrl);
    }

    @SuppressLint("ObsoleteSdkInt")
    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    @Override
    protected Boolean doInBackground(Params... params) {
        if (Build.VERSION.SDK_INT < 18) {
            mErrorCode = LicenseManagerErrorCode.ERROR_300;
            return null;
        }

        boolean isValid = false;
        mManifestUrl = params[0].manifestUrl;
        byte[] savedKeys = params[0].savedKeys;
        try {
            Log.d(TAG, "Trying to restore keys for: " + mManifestUrl);
            byte[] offlineLicenseKeySetId = savedKeys == null ? LicenseFileUtils.readLicenseFile(
                    params[0].defaultStoragePath, mManifestUrl) : savedKeys;

            // Creating media DRM session
            if (mMediaDrm == null) {
                mMediaDrm = new MediaDrm(C.WIDEVINE_UUID);
                mSessionId = mMediaDrm.openSession();
            }

            mMediaDrm.restoreKeys(mSessionId, offlineLicenseKeySetId);
            Log.d(TAG, "Keys restored!");
            Pair<Long, Long> remainingSec = DrmUtils.getLicenseDurationRemainingSec(mMediaDrm, mSessionId);
            Log.d(TAG, "remainingSec pair: " + remainingSec);
            isValid = remainingSec != null && remainingSec.first >= params[0].minExpireSecond;
        } catch (Exception e) {
            onError(e);
        }

        closeSession();
        return isValid;
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
    protected void onPostExecute(Boolean isValid) {
        if (mListener != null) {
            if (mErrorCode == null) mListener.onLicenseCheck(isValid, mManifestUrl);
            else mListener.onLicenseCheckFailed(mErrorCode, mErrorExtraData, mManifestUrl);
        }
        mListener = null;
        mMediaDrm = null;
        mSessionId = null;
    }

    public static class Params {
        final long minExpireSecond;
        final String manifestUrl, defaultStoragePath;
        final byte[] savedKeys;

        public Params(String manifestUrl, String defaultStoragePath, long minExpireSecond) {
            this(manifestUrl, defaultStoragePath, minExpireSecond, null);
        }

        @SuppressWarnings("WeakerAccess")
        public Params(String manifestUrl, String defaultStoragePath,
                      long minExpireSecond, byte[] savedKeys) {
            this.manifestUrl = manifestUrl;
            this.defaultStoragePath = defaultStoragePath;
            this.minExpireSecond = minExpireSecond;
            this.savedKeys = savedKeys;
        }
    }
}
