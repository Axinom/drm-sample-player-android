/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.axinom.drm.sample.license.internal.model;

import android.os.Parcel;
import android.os.Parcelable;

import com.axinom.drm.sample.BuildConfig;
import com.axinom.drm.sample.license.internal.utils.LicenseManagerUtils;
import com.google.android.exoplayer2.C;

import java.util.Arrays;
import java.util.UUID;

/**
 * Scheme initialization data.
 */
@SuppressWarnings({"unused", "WeakerAccess"})
public final class SchemeData implements Parcelable {

    // Lazily initialized hashcode.
    private int hashCode;

    /**
     * The {@link UUID} of the DRM scheme, or {@link C#UUID_NIL} if the data is universal (i.e.
     * applies to all schemes).
     */
    public final UUID uuid;
    /**
     * The mimeType of {@link #data}.
     */
    public final String mimeType;
    /**
     * The initialization data. May be null for scheme support checks only.
     */
    public final byte[] data;
    /**
     * Whether secure decryption is required.
     */
    public final boolean requiresSecureDecryption;

    /**
     * @param uuid     The {@link UUID} of the DRM scheme, or {@link C#UUID_NIL} if the data is
     *                 universal (i.e. applies to all schemes).
     * @param mimeType See {@link #mimeType}.
     * @param data     See {@link #data}.
     */
    public SchemeData(UUID uuid, String mimeType, byte[] data) {
        this(uuid, mimeType, data, false);
    }

    /**
     * @param uuid                     The {@link UUID} of the DRM scheme, or {@link C#UUID_NIL} if the data is
     *                                 universal (i.e. applies to all schemes).
     * @param mimeType                 See {@link #mimeType}.
     * @param data                     See {@link #data}.
     * @param requiresSecureDecryption See {@link #requiresSecureDecryption}.
     */
    public SchemeData(UUID uuid, String mimeType, byte[] data, boolean requiresSecureDecryption) {
        if (BuildConfig.DEBUG && (uuid == null || mimeType == null)) {
            throw new NullPointerException();
        }
        this.uuid = uuid;
        this.mimeType = mimeType;
        this.data = data;
        this.requiresSecureDecryption = requiresSecureDecryption;
    }

    /* package */ SchemeData(Parcel in) {
        uuid = new UUID(in.readLong(), in.readLong());
        mimeType = in.readString();
        data = in.createByteArray();
        requiresSecureDecryption = in.readByte() != 0;
    }

    /**
     * Returns whether this initialization data applies to the specified scheme.
     *
     * @param schemeUuid The scheme {@link UUID}.
     * @return Whether this initialization data applies to the specified scheme.
     */
    public boolean matches(UUID schemeUuid) {
        return C.UUID_NIL.equals(uuid) || schemeUuid.equals(uuid);
    }

    /**
     * Returns whether this {@link SchemeData} can be used to replace {@code other}.
     *
     * @param other A {@link SchemeData}.
     * @return Whether this {@link SchemeData} can be used to replace {@code other}.
     */
    public boolean canReplace(SchemeData other) {
        return hasData() && !other.hasData() && matches(other.uuid);
    }

    /**
     * Returns whether {@link #data} is non-null.
     * @return boolean
     */
    public boolean hasData() {
        return data != null;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof SchemeData)) {
            return false;
        }
        if (obj == this) {
            return true;
        }
        SchemeData other = (SchemeData) obj;
        return mimeType.equals(other.mimeType) && LicenseManagerUtils.areEqual(uuid, other.uuid)
                && Arrays.equals(data, other.data);
    }

    @Override
    public int hashCode() {
        if (hashCode == 0) {
            int result = uuid.hashCode();
            result = 31 * result + mimeType.hashCode();
            result = 31 * result + Arrays.hashCode(data);
            hashCode = result;
        }
        return hashCode;
    }

    // Parcelable implementation.

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeLong(uuid.getMostSignificantBits());
        dest.writeLong(uuid.getLeastSignificantBits());
        dest.writeString(mimeType);
        dest.writeByteArray(data);
        dest.writeByte((byte) (requiresSecureDecryption ? 1 : 0));
    }

    @SuppressWarnings("hiding")
    public static final Parcelable.Creator<SchemeData> CREATOR =
            new Parcelable.Creator<SchemeData>() {

                @Override
                public SchemeData createFromParcel(Parcel in) {
                    return new SchemeData(in);
                }

                @Override
                public SchemeData[] newArray(int size) {
                    return new SchemeData[size];
                }

            };

}
