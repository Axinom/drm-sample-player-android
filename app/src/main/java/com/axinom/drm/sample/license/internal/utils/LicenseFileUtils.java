package com.axinom.drm.sample.license.internal.utils;

import android.text.TextUtils;
import android.util.Base64;

import com.axinom.drm.sample.license.LicenseManagerErrorCode;
import com.axinom.drm.sample.license.internal.exception.LicenseManagerException;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * Utility class for working with license key set id files
 */

@SuppressWarnings("WeakerAccess")
public class LicenseFileUtils {
    public static String customStoragePath;

    public static void writeLicenseFile(String defaultStoragePath, String manifestUrl,
                                        byte[] offlineLicenseKeySetId) throws LicenseManagerException {
        String licenseFileName = getBase64Name(manifestUrl);
        if (TextUtils.isEmpty(licenseFileName)) {
            throw new LicenseManagerException(LicenseManagerErrorCode.ERROR_303,
                    "Problem while saving keys. Incorrect manifest name: " + manifestUrl);
        }

        File licenseFile = getFileFromDataFolder(defaultStoragePath, licenseFileName);
        File licenseFileFolder = getDataFolder(defaultStoragePath);
        boolean canContinue = true;

        if (!licenseFileFolder.exists()) canContinue = licenseFileFolder.mkdirs();
        if (!canContinue) {
            throw new LicenseManagerException(LicenseManagerErrorCode.ERROR_303,
                    "Problem while saving keys. Cannot create folder for licenses.");
        }
        if (licenseFile.exists()) canContinue = licenseFile.delete();
        if (!canContinue) {
            throw new LicenseManagerException(LicenseManagerErrorCode.ERROR_303,
                    "Problem while saving keys. Cannot delete old license.");
        }
        try {
            canContinue = licenseFile.createNewFile();
        } catch (IOException e) {
            throw new LicenseManagerException(LicenseManagerErrorCode.ERROR_303, e.getMessage());
        }
        if (!canContinue) {
            throw new LicenseManagerException(LicenseManagerErrorCode.ERROR_303,
                    "Problem while saving keys. Cannot create new file for license.");
        }

        FileOutputStream fos;
        try {
            fos = new FileOutputStream(licenseFile);
            fos.write(offlineLicenseKeySetId);
            fos.close();
        } catch (IOException e) {
            throw new LicenseManagerException(LicenseManagerErrorCode.ERROR_303, e.getMessage());
        }
    }

    public static void deleteLicenseFile(String defaultStoragePath, String manifestUrl) throws LicenseManagerException {
        String licenseFileName = getBase64Name(manifestUrl);
        if (TextUtils.isEmpty(licenseFileName)) {
            throw new LicenseManagerException(LicenseManagerErrorCode.ERROR_303,
                    "Problem deleting saving keys. Incorrect manifest name: " + manifestUrl);
        }

        File licenseFile = getFileFromDataFolder(defaultStoragePath, licenseFileName);
        boolean canContinue = true;
        if (licenseFile.exists()) canContinue = licenseFile.delete();
        if (!canContinue) {
            throw new LicenseManagerException(LicenseManagerErrorCode.ERROR_303,
                    "Problem deleting saving keys. Cannot delete license file.");
        }
    }

    public static void deleteAllLicenses(String defaultStoragePath) throws LicenseManagerException {
        File licenseFolder = getDataFolder(defaultStoragePath);
        boolean canContinue = true;

        if (licenseFolder.exists()) canContinue = deleteRecursive(licenseFolder);
        if (!canContinue) {
            throw new LicenseManagerException(LicenseManagerErrorCode.ERROR_303,
                    "Problem deleting all keys. Cannot delete licenses folder.");
        }
    }

    private static boolean deleteRecursive(File fileOrDirectory) {
        if (fileOrDirectory.isDirectory())
            for (File child : Objects.requireNonNull(fileOrDirectory.listFiles()))
                deleteRecursive(child);

        return fileOrDirectory.delete();
    }

    public static String[] getAllLicenseFilesPaths(String defaultStoragePath) {
        File licenseFolder = getDataFolder(defaultStoragePath);
        if (!licenseFolder.exists()) return new String[0];

        String[] files = licenseFolder.list();
        String[] filesPaths = new String[Objects.requireNonNull(files).length];
        for (int i = 0; i < files.length; i++) {
            filesPaths[i] = getNameFromBase64(files[i]);
        }

        return filesPaths;
    }

    public static byte[] readLicenseFile(String defaultStoragePath, String manifestUrl) throws LicenseManagerException {
        byte[] offlineLicenseKeySetId;
        String licenseFileName = getBase64Name(manifestUrl);
        if (TextUtils.isEmpty(licenseFileName)) {
            throw new LicenseManagerException(LicenseManagerErrorCode.ERROR_303,
                    "Problem while reading keys. Incorrect manifest name: " + manifestUrl);
        }

        File licenseFile = getFileFromDataFolder(defaultStoragePath, licenseFileName);
        if (!licenseFile.exists()) {
            throw new LicenseManagerException(LicenseManagerErrorCode.ERROR_303,
                    "Problem while reading keys. Keys do not exists.");
        }

        FileInputStream fis;
        try {
            fis = new FileInputStream(licenseFile);
            offlineLicenseKeySetId = new byte[(int) licenseFile.length()];
            //noinspection ResultOfMethodCallIgnored
            fis.read(offlineLicenseKeySetId);
            fis.close();
        } catch (IOException e) {
            throw new LicenseManagerException(LicenseManagerErrorCode.ERROR_303, e.getMessage());
        }

        return offlineLicenseKeySetId;
    }

    private static String getBase64Name(String fileName) {
        byte[] fileNameData;
        fileNameData = fileName.getBytes(StandardCharsets.UTF_8);
        return Base64.encodeToString(fileNameData, Base64.DEFAULT);
    }

    public static String getNameFromBase64(String base64Name) {
        byte[] fileNameData;
        fileNameData = base64Name.getBytes(StandardCharsets.UTF_8);
        return new String(Base64.decode(fileNameData, Base64.DEFAULT));
    }

    private static File getFileFromDataFolder(String defaultStoragePath, String fileName) {
        return new File(getDataFolderPath(defaultStoragePath) + fileName);
    }

    private static File getDataFolder(String defaultStoragePath) {
        return new File(getDataFolderPath(defaultStoragePath));
    }

    private static String getDataFolderPath(String defaultStoragePath) {
        if (customStoragePath != null) return customStoragePath;
        return defaultStoragePath + "/drm/";
    }

    public static byte[] readFullFile(String filePath) {
        byte[] data = null;
        File manifestFile = new File(filePath);
        FileInputStream fis = null;
        try {
            fis = new FileInputStream(manifestFile);
            data = new byte[(int) manifestFile.length()];
            int result = fis.read(data);
            if (result < manifestFile.length()) {
                return null;
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (fis != null) {
                try {
                    fis.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        return data;
    }

}
