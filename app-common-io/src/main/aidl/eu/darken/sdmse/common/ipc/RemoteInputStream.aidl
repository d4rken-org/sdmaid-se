package eu.darken.sdmse.common.ipc;

interface RemoteInputStream {
    int available();
    int read();
    int readBuffer(out byte[] b, int off, int len);
    void close();
}