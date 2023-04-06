package eu.darken.sdmse.common.root.service.internal;

// This is the wrapper used internally by RootIPC(Receiver)

interface IRootIPC {
    void hello(IBinder self);
    IBinder getUserIPC();
    void bye(IBinder self);
}
