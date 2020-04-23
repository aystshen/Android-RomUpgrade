// IRomUpgradeService.aidl
package com.ayst.romupgrade;

// Declare any non-default types here with import statements

interface IRomUpgradeService {
    void checkUpdate();
    boolean installPackage(String packagePath);
    boolean verifyPackage(String packagePath);
    void deletePackage(String packagePath);
}
