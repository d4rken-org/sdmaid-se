package eu.darken.sdmse.common.files.local.root;

interface RemoteInputStream {
    int available();
    int read();
    int readBuffer(out byte[] b, int off, int len);
    void close();
}