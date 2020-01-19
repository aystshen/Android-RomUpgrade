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
