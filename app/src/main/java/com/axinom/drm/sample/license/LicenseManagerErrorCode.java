package com.axinom.drm.sample.license;

import androidx.annotation.StringRes;

import com.axinom.drm.sample.R;

/**
 * Player error codes
 */
@SuppressWarnings("WeakerAccess")
public enum LicenseManagerErrorCode {
    /**
     * DRM scheme is not supported by current Android SDK
     */
    ERROR_300(300, R.string.license_player_error_300),
    /**
     * Unsupported DRM scheme
     */
    ERROR_301(301, R.string.license_player_error_301),
    /**
     * DRM session error
     */
    ERROR_302(302, R.string.license_player_error_302),
    /**
     * License file read/write exception
     */
    ERROR_303(303, R.string.license_player_error_303),
    /**
     * Error while reading manifest from .tar file
     */
    ERROR_304(304, R.string.license_player_error_304),
    /**
     * Cannot delete all licenses
     */
    ERROR_305(305, R.string.license_player_error_305),
    /**
     * Cannot parse DRM message
     */
    ERROR_306(306, R.string.license_player_error_306),
    /**
     * DRM message has no persistent flag
     */
    ERROR_307(307, R.string.license_player_error_307),
    /**
     * DRM license expired or will expire soon
     */
    ERROR_308(308, R.string.license_player_error_308),
    /**
     * Device provisioning failed
     */
    ERROR_309(309, R.string.license_player_error_309);

    private int mCode;
    private int mDescription;

    LicenseManagerErrorCode(int code, @StringRes int description) {
        mCode = code;
        mDescription = description;
    }

    public int getCode() {
        return mCode;
    }

    public int getDescription() {
        return mDescription;
    }

    @SuppressWarnings("unused")
    public static LicenseManagerErrorCode getByCode(int code) {
        return valueOf("ERROR_" + code);
    }
}
