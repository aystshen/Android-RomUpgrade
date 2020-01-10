package com.ayst.romupgrade.util.filecopy;

import java.io.File;

/**
 * Class to hold the files for a copy task. Holds the source and the
 * destination file.
 *
 * @author ayst.shen@foxmail.com
 *
 */
public class FileCopyTaskParam {
    public File from;
    public File to;
    public IFileCopyListener<File> listener;
}
