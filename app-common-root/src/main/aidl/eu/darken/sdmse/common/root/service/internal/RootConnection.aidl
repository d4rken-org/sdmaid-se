package eu.darken.sdmse.common.root.service.internal;

// This is the wrapper used internally by RootIPC(Receiver)

import eu.darken.sdmse.common.root.service.internal.RootHostOptions;

interface RootConnection {
    void hello(IBinder self);
    void bye(IBinder self);
    IBinder getUserConnection();
    void updateHostOptions(in RootHostOptions options);
}
