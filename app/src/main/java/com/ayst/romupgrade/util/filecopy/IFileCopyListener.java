package com.ayst.romupgrade.util.filecopy;

public interface IFileCopyListener<T> {
    public void progress(int progress);
    public void completed(T file);
    public void error(Exception e);
}
