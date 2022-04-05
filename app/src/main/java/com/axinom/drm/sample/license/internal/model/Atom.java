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

import com.axinom.drm.sample.license.internal.utils.LicenseManagerUtils;

public abstract class Atom {

    /**
     * Size of a full atom header, in bytes.
     */
    public static final int FULL_HEADER_SIZE = 12;

    public static final int TYPE_pssh = LicenseManagerUtils.getIntegerCodeForString("pssh");

    public final int type;

    public Atom(int type) {
        this.type = type;
    }

    @Override
    public String toString() {
        return getAtomTypeString(type);
    }

    /**
     * Parses the version number out of the additional integer component of a full atom.
     *
     * @param fullAtomInt full atom int
     * @return int
     */
    public static int parseFullAtomVersion(int fullAtomInt) {
        return 0x000000FF & (fullAtomInt >> 24);
    }

    /**
     * Converts a numeric atom type to the corresponding four character string.
     *
     * @param type The numeric atom type.
     * @return The corresponding four character string.
     */
    public static String getAtomTypeString(int type) {
        return "" + (char) ((type >> 24) & 0xFF)
                + (char) ((type >> 16) & 0xFF)
                + (char) ((type >> 8) & 0xFF)
                + (char) (type & 0xFF);
    }

}
