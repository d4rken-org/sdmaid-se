package eu.darken.sdmse.common.shizuku.service.internal;

import eu.darken.sdmse.common.shizuku.service.internal.ShizukuHostOptions;

interface ShizukuConnection {

    void destroy() = 16777114; // Destroy method defined by Shizuku server

    void exit() = 1; // Exit method defined by user

    IBinder getUserConnection() = 2;

    void updateHostOptions(in ShizukuHostOptions options) = 3;
}