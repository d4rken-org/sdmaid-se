package eu.darken.sdmse.common.ipc;

interface RemoteFileHandle {
    boolean readWrite();
    // We need inout for the buffer, as FileHandle will pass partially filled buffers that need to stay partially filled when returned
    int read(long fileOffset, inout byte[] buffer, int bufferOffset, int byteCount);
    void write(long fileOffset, inout byte[] buffer, int bufferOffset, int byteCount);
    void resize(long size);
    long size();
    void flush();
    void close();
}