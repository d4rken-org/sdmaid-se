package eu.darken.sdmse.common.files.local.root;

interface RemoteOutputStream {
    void write(int b);
    void writeBuffer(in byte[] b, int off, int len);
    void flush();
    void close();
}