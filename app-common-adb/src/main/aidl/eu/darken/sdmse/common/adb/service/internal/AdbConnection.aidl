package eu.darken.sdmse.common.adb.service.internal;

import eu.darken.sdmse.common.adb.service.AdbHostOptions;

interface AdbConnection {

    void destroy() = 16777114; // Destroy method defined by Shizuku server

    void exit() = 1; // Exit method defined by user

    IBinder getUserConnection() = 2;

    void updateHostOptions(in AdbHostOptions options) = 3;
}