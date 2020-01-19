/*
 * Copyright(c) 2020 Bob Shen <ayst.shen@foxmail.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ayst.romupgrade.entity;

import java.io.File;

public class LocalPackage {
    public static final int TYPE_APP = 0;
    public static final int TYPE_ROM = 1;

    private int type;
    private File file;

    public LocalPackage(int type, File file) {
        this.type = type;
        this.file = file;
    }

    public int getType() {
        return type;
    }

    public void setType(int type) {
        this.type = type;
    }

    public File getFile() {
        return file;
    }

    public void setFile(File file) {
        this.file = file;
    }

    @Override
    public String toString() {
        return "LocalPackage{" +
                "type=" + type +
                ", file=" + file.getAbsolutePath() +
                '}';
    }
}
