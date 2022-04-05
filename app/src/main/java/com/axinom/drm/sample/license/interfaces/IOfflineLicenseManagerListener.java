package com.axinom.drm.sample.license.interfaces;

/**
 * Offline License Manager Listener
 */
public interface IOfflineLicenseManagerListener {

    /**
     * Dispatched when license successfully downloaded for the specific manifest
     *
     * @param manifestUrl Url of the manifest file
     */
    void onLicenseDownloaded(String manifestUrl);

    /**
     * Dispatched when license successfully downloaded for the specific manifest. Also returning key ids.
     *
     * @param manifestUrl Url of the manifest file
     * @param keyIds      Array of bytes used as a key for license restoring
     */
    void onLicenseDownloadedWithResult(String manifestUrl, byte[] keyIds);

    /**
     * Dispatched when license downloading failed for the specific manifest
     *
     * @param code        Error message code
     * @param description Error message description
     * @param manifestUrl Manifest url which was used for license downloading
     */
    void onLicenseDownloadFailed(int code, String description, String manifestUrl);

    /**
     * Dispatched when license successfully validated for the specific manifest
     *
     * @param isValid     returns true if license is currently valid
     * @param manifestUrl Manifest url which was used for license validation
     */
    void onLicenseCheck(boolean isValid, String manifestUrl);

    /**
     * Dispatched when license validation failed for the specific manifest
     *
     * @param code        Error message code
     * @param description Error message description
     * @param manifestUrl Manifest url which was used for license validation
     */
    void onLicenseCheckFailed(int code, String description, String manifestUrl);

    /**
     * Dispatched when license successfully released for the specific manifest
     *
     * @param manifestUrl Manifest url which was used for license release
     */
    void onLicenseReleased(String manifestUrl);

    /**
     * Dispatched when license releasing failed for the specific manifest
     *
     * @param code        Error message code
     * @param description Error message description
     * @param manifestUrl Manifest url which was used for license releasing
     */
    void onLicenseReleaseFailed(int code, String description, String manifestUrl);

    /**
     * Dispatched when license successfully restored for the specific manifest. Also returning key ids.
     *
     * @param manifestUrl Manifest url which was used for license restore
     * @param keyIds      Array of bytes used as a key for license restoring
     */
    void onLicenseKeysRestored(String manifestUrl, byte[] keyIds);

    /**
     * Dispatched when license downloading failed for the specific manifest
     *
     * @param code        Error message code
     * @param description Error message description
     * @param manifestUrl Manifest url which was used for license downloading
     */
    void onLicenseRestoreFailed(int code, String description, String manifestUrl);

    /**
     * Dispatched when all licenses successfully released
     */
    void onAllLicensesReleased();

    /**
     * Dispatched when license releasing failed for the specific manifest
     *
     * @param code        Error message code
     * @param description Error message description
     */
    void onAllLicensesReleaseFailed(int code, String description);
}
