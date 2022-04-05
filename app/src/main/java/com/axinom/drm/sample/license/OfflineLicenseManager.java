package com.axinom.drm.sample.license;

import android.content.Context;
import android.os.AsyncTask;

import com.axinom.drm.sample.license.interfaces.IOfflineLicenseManagerListener;
import com.axinom.drm.sample.license.internal.task.LicenceDownloadTask;
import com.axinom.drm.sample.license.internal.task.LicenseCheckTask;
import com.axinom.drm.sample.license.internal.task.LicenseReleaseTask;
import com.axinom.drm.sample.license.internal.task.LicenseRestoreTask;
import com.axinom.drm.sample.license.internal.utils.LicenseFileUtils;

import java.util.Map;

/**
 * Manager class for offline licenses
 */
public class OfflineLicenseManager {

    private final String mDefaultStoragePath;
    private Context mContext;
    private IOfflineLicenseManagerListener mListener;
    private final InternalListener mInternalListener;
    public static final long LICENSE_MIN_EXPIRE_SECONDS = 30;

    private LicenceDownloadTask mDownloadTask;
    private LicenseCheckTask mCheckTask;
    private LicenseReleaseTask mReleaseTask;
    private LicenseRestoreTask mRestoreTask;
    private long mMinExpireSeconds = LICENSE_MIN_EXPIRE_SECONDS;
    private Map<String, String> mRequestParams = null;

    /**
     * Create an instance of OfflinePlaybackManager
     *
     * @param context reference for the application context
     */
    public OfflineLicenseManager(Context context) {
        mContext = context;
        mDefaultStoragePath = context.getFilesDir().getAbsolutePath();
        mInternalListener = new InternalListener();
    }

    /**
     * Set OfflinePlaybackManager Event Listener
     *
     * @param listener object which implements EventListener
     */
    public void setEventListener(IOfflineLicenseManagerListener listener) {
        mListener = listener;
    }

    /**
     * Dispose all components of the OfflinePlaybackManager to prevent memory leaks
     */
    public void release() {
        cancelDownloadTask();
        cancelCheckTask();
        cancelReleaseTask();
        cancelRestoreTask();
        mContext = null;
        mListener = null;
    }

    /**
     * Start license keys restoring process. Should be used only if License Manager was used to save
     * license. Set event listener to receive callbacks. Returns result as keys array.
     *
     * @param manifestUrl URL of the video manifest file
     */
    public void getLicenseKeys(String manifestUrl) {
        cancelRestoreTask();
        LicenseRestoreTask.Params params = new LicenseRestoreTask.Params(
                manifestUrl, mDefaultStoragePath, mMinExpireSeconds
        );
        mRestoreTask = new LicenseRestoreTask(mInternalListener);
        mRestoreTask.execute(params);
    }

    private void cancelRestoreTask() {
        if (mRestoreTask != null && mRestoreTask.getStatus() == AsyncTask.Status.RUNNING) {
            mRestoreTask.cancel(true);
            mRestoreTask = null;
        }
    }

    public String getDefaultStoragePath() {
        return mDefaultStoragePath;
    }

    /**
     * Start license releasing process. Set event listener to receive callbacks.
     *
     * @param manifestUrl URL of the video manifest file
     */
    public void releaseLicense(String manifestUrl) {
        LicenseReleaseTask.Params params = new LicenseReleaseTask.Params(
                null, manifestUrl, mDefaultStoragePath, false,
                false, mRequestParams
        );
        runReleaseLicenseTask(params);
    }

    /**
     * Start license releasing process. Set event listener to receive callbacks.
     *
     * @param manifestUrl             URL of the video manifest file
     * @param licenseServerUrl        license server url to set release request on server
     * @param stopOnLicenseServerFail True should be used if device is online or license server
     *                                request error should stop the release process. False
     *                                should be used in offline scenario or license server
     *                                request error should NOT stop the release process
     */
    public void releaseLicense(String manifestUrl, String licenseServerUrl, boolean stopOnLicenseServerFail) {
        LicenseReleaseTask.Params params = new LicenseReleaseTask.Params(
                licenseServerUrl, manifestUrl, mDefaultStoragePath, false,
                stopOnLicenseServerFail, mRequestParams
        );
        runReleaseLicenseTask(params);
    }

    /**
     * Start all licenses releasing process. Set event listener to receive callbacks.
     *
     * @param licenseServerUrl        license server url to set release request on server
     * @param stopOnLicenseServerFail True should be used if device is online or license server
     *                                request error should stop the release process. False
     *                                should be used in offline scenario or license server
     *                                request error should NOT stop the release process
     */
    public void releaseAllLicenses(String licenseServerUrl, boolean stopOnLicenseServerFail) {
        LicenseReleaseTask.Params params = new LicenseReleaseTask.Params(
                licenseServerUrl, null, mDefaultStoragePath, true,
                stopOnLicenseServerFail, mRequestParams
        );
        runReleaseLicenseTask(params);
    }

    /**
     * Start all licenses releasing process. Set event listener to receive callbacks.
     */
    public void releaseAllLicenses() {
        LicenseReleaseTask.Params params = new LicenseReleaseTask.Params(
                null, null, mDefaultStoragePath, true,
                false, mRequestParams
        );
        runReleaseLicenseTask(params);
    }

    private void runReleaseLicenseTask(LicenseReleaseTask.Params params) {
        cancelReleaseTask();
        mReleaseTask = new LicenseReleaseTask(mInternalListener);
        mReleaseTask.execute(params);
    }

    private void cancelReleaseTask() {
        if (mReleaseTask != null && mReleaseTask.getStatus() == AsyncTask.Status.RUNNING) {
            mReleaseTask.cancel(true);
            mReleaseTask = null;
        }
    }

    /**
     * Start license validation process. Set event listener to receive callbacks. Use this method
     * if license was stored using downloadLicense(licenseServerUrl, manifestUrl, drmMessage) method
     * or autoSave was set to true.
     *
     * @param manifestUrl URL of the video manifest file
     */
    public void checkLicenseValid(String manifestUrl) {
        cancelCheckTask();
        LicenseCheckTask.Params params = new LicenseCheckTask.Params(
                manifestUrl,
                mDefaultStoragePath,
                mMinExpireSeconds
        );
        mCheckTask = new LicenseCheckTask(mInternalListener);
        mCheckTask.execute(params);
    }

    /**
     * Start license validation process with provided keyIds. Set event listener to receive callbacks.
     *
     * @param manifestUrl URL of the video manifest file
     * @param keyIds      Array of bytes used as a key for license checking
     */
    public void checkLicenseValidWithKeys(String manifestUrl, byte[] keyIds) {
        cancelCheckTask();
        LicenseCheckTask.Params params = new LicenseCheckTask.Params(
                manifestUrl,
                mDefaultStoragePath,
                mMinExpireSeconds,
                keyIds
        );
        mCheckTask = new LicenseCheckTask(mInternalListener);
        mCheckTask.execute(params);
    }

    private void cancelCheckTask() {
        if (mCheckTask != null && mCheckTask.getStatus() == AsyncTask.Status.RUNNING) {
            mCheckTask.cancel(true);
            mCheckTask = null;
        }
    }

    /**
     * Start license downloading process. Use Set event listener to receive callbacks.
     * Returns result as keys array. If autoSave parameter set to true, License Manager will save
     * keys to predefined location. Use setCustomStoragePath() to set custom location.
     *
     * @param licenseServerUrl URL of the license server
     * @param manifestUrl      URL of the video manifest file
     * @param drmMessage       DRM message (token)
     * @param autoSave         Automatically save license key to default location
     */
    @SuppressWarnings("SameParameterValue")
    public void downloadLicenseWithResult(String licenseServerUrl, String manifestUrl,
                                          String drmMessage, boolean autoSave) {
        cancelDownloadTask();
        LicenceDownloadTask.Params params =
                new LicenceDownloadTask.Params(
                        mRequestParams,
                        manifestUrl,
                        licenseServerUrl,
                        drmMessage,
                        mDefaultStoragePath,
                        mMinExpireSeconds
                );
        mDownloadTask = new LicenceDownloadTask(mInternalListener, true, autoSave);
        mDownloadTask.execute(params);
    }

    /**
     * Start license downloading and saving process to predefined location.
     * Set event listener to receive callbacks.
     *
     * @param licenseServerUrl URL of the license server
     * @param manifestUrl      URL of the video manifest file
     * @param drmMessage       DRM message (token)
     */
    public void downloadLicense(String licenseServerUrl, String manifestUrl, String drmMessage) {
        cancelDownloadTask();
        LicenceDownloadTask.Params params =
                new LicenceDownloadTask.Params(
                        mRequestParams,
                        manifestUrl,
                        licenseServerUrl,
                        drmMessage,
                        mDefaultStoragePath,
                        mMinExpireSeconds
                );
        mDownloadTask = new LicenceDownloadTask(mInternalListener, false, true);
        mDownloadTask.execute(params);
    }

    private void cancelDownloadTask() {
        if (mDownloadTask != null && mDownloadTask.getStatus() == AsyncTask.Status.RUNNING) {
            mDownloadTask.cancel(true);
            mDownloadTask = null;
        }
    }

    /**
     * Set the minimum time required for license to be valid. If, for example, minExpireSeconds will
     * be equal to 30 and the license will be valid for 25 seconds, validation process will return
     * that license is not valid already.
     *
     * @param minExpireSeconds time in seconds. Default value is 30.
     */
    public void setMinExpireSeconds(long minExpireSeconds) {
        mMinExpireSeconds = minExpireSeconds;
    }

    /**
     * Set custom storage path where keys for the licenses will be stored.
     *
     * @param path path string. Should have read/write permissions
     */
    public void setCustomStoragePath(String path) {
        if (!path.endsWith("/")) path = path + "/";
        LicenseFileUtils.customStoragePath = path;
    }

    /**
     * Set custom request header parameters to be added to every license post request.
     *
     * @param requestParams Request headers HashMap, where map key equal header property key and
     *                      where map value equal header property value
     */
    public void setRequestParams(Map<String, String> requestParams) {
        mRequestParams = requestParams;
    }

    private class InternalListener implements LicenceDownloadTask.ILicenceDownloadTaskCallback,
            LicenseCheckTask.ILicenceCheckTaskCallback,
            LicenseReleaseTask.ILicenseReleaseTaskCallback,
            LicenseRestoreTask.ILicenceRestoreTaskCallback {

        @Override
        public void onLicenseDownloadedWithResult(String manifestUrl, byte[] keyIds) {
            if (mListener != null) mListener.onLicenseDownloadedWithResult(manifestUrl, keyIds);
        }

        @Override
        public void onLicenseDownloaded(String manifestUrl) {
            if (mListener != null) mListener.onLicenseDownloaded(manifestUrl);
        }

        @Override
        public void onLicenseDownloadFailed(LicenseManagerErrorCode errorCode, String errorExtraData, String manifestUrl) {
            String description = getErrorDescription(errorCode, errorExtraData);
            if (mListener != null)
                mListener.onLicenseDownloadFailed(errorCode.getCode(), description, manifestUrl);
        }

        @Override
        public void onLicenseCheck(Boolean isValid, String manifestUrl) {
            if (mListener != null) mListener.onLicenseCheck(isValid, manifestUrl);
        }

        @Override
        public void onLicenseCheckFailed(LicenseManagerErrorCode errorCode, String errorExtraData, String manifestUrl) {
            String description = getErrorDescription(errorCode, errorExtraData);
            if (mListener != null)
                mListener.onLicenseCheckFailed(errorCode.getCode(), description, manifestUrl);
        }

        @Override
        public void onLicenseReleased(String manifestUrl) {
            if (mListener != null) mListener.onLicenseReleased(manifestUrl);
        }

        @Override
        public void onLicenseReleaseFailed(LicenseManagerErrorCode errorCode, String errorExtraData, String manifestUrl) {
            String description = getErrorDescription(errorCode, errorExtraData);
            if (mListener != null)
                mListener.onLicenseReleaseFailed(errorCode.getCode(), description, manifestUrl);
        }

        @Override
        public void onAllLicensesReleased() {
            if (mListener != null) mListener.onAllLicensesReleased();
        }

        @Override
        public void onAllLicensesReleaseFailed(LicenseManagerErrorCode errorCode, String errorExtraData) {
            String description = getErrorDescription(errorCode, errorExtraData);
            if (mListener != null)
                mListener.onAllLicensesReleaseFailed(errorCode.getCode(), description);
        }

        @Override
        public void onLicenseKeysRestored(String manifestUrl, byte[] keySetId) {
            if (mListener != null) mListener.onLicenseKeysRestored(manifestUrl, keySetId);
        }

        @Override
        public void onLicenseRestoreFailed(LicenseManagerErrorCode errorCode, String errorExtraData, String manifestUrl) {
            String description = getErrorDescription(errorCode, errorExtraData);
            if (mListener != null)
                mListener.onLicenseRestoreFailed(errorCode.getCode(), description, manifestUrl);
        }

        private String getErrorDescription(LicenseManagerErrorCode errorCode, String errorExtraData) {
            String description = "";
            if (mContext != null) {
                if (errorExtraData == null) {
                    description = mContext.getResources().getString(errorCode.getDescription());
                } else {
                    description = mContext.getResources().getString(errorCode.getDescription(), errorExtraData);
                }
            }
            return description;
        }
    }
}
