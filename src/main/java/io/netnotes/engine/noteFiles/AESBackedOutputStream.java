package io.netnotes.engine.noteFiles;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;

import javax.crypto.Cipher;
import javax.crypto.CipherOutputStream;
import javax.crypto.SecretKey;

import org.apache.commons.io.output.UnsynchronizedByteArrayOutputStream;

import io.netnotes.engine.crypto.CryptoService;
import io.netnotes.engine.crypto.RandomService;

public final class AESBackedOutputStream extends OutputStream {

    private final OutputStream delegate;
    private final File file;
    private final boolean fileBacked;

    public AESBackedOutputStream(
            File dataDir,
            SecretKey secretKey,
            int contentLength,
            int thresholdBytes,
            boolean syncronized
    ) throws Exception {
        if (contentLength <= thresholdBytes) {
            this.delegate = new UnsynchronizedByteArrayOutputStream(contentLength);
            this.file = null;
            this.fileBacked = false;
        } else {
            this.file = new File(FileStreamUtils.getNewUUIDFilePath(dataDir) + ".tmp");
            this.fileBacked = true;
            OutputStream tmpOut = Files.newOutputStream(this.file.toPath());

            byte[] iv = RandomService.getIV();
            Cipher encryptCipher = CryptoService.getAESEncryptCipher(iv, secretKey);

            tmpOut.write(iv); // write IV first
            this.delegate = new CipherOutputStream(tmpOut, encryptCipher);
        }
    }

    @Override
    public void write(int b) throws IOException {
        delegate.write(b);
    }

    @Override
    public void write(byte[] b) throws IOException {
        delegate.write(b);
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        delegate.write(b, off, len);
    }

    @Override
    public void flush() throws IOException {
        delegate.flush();
    }

    @Override
    public void close() throws IOException {
        delegate.close();
    }

    public boolean isFileBacked() {
        return fileBacked;
    }


    public File getFile() {
        if (fileBacked && file != null) {
            return file;
        }else{
            return null;
        }
    }

    public UnsynchronizedByteArrayOutputStream getUnsynchronizedByteArrayOutputStream(){
       
        return  (UnsynchronizedByteArrayOutputStream) this.delegate;
    }

    public InputStream toInputStream(SecretKey secretKey) throws Exception{
        if (fileBacked) {
            return new AESBackedInputStream(file, secretKey);
        }else{
            return new AESBackedInputStream(getUnsynchronizedByteArrayOutputStream());
        }
    }
}
