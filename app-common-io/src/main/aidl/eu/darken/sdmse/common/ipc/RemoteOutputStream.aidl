package eu.darken.sdmse.common.ipc;

interface RemoteOutputStream {
    void write(int b);
    void writeBuffer(in byte[] b, int off, int len);
    void flush();
    void close();
}