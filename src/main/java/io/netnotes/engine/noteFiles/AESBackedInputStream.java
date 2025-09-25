package io.netnotes.engine.noteFiles;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.SecretKey;

import org.apache.commons.io.input.UnsynchronizedByteArrayInputStream;
import org.apache.commons.io.output.UnsynchronizedByteArrayOutputStream;

import io.netnotes.engine.crypto.CryptoService;
import io.netnotes.engine.messaging.StreamUtils;

public final class AESBackedInputStream extends InputStream {
    private final File m_file;
    private final InputStream m_delegate;
    private final boolean m_fileBacked;

    public AESBackedInputStream(
            File file,
            SecretKey secretKey
    ) throws Exception {
        this.m_fileBacked = true;
        m_file = file;
        // Open file, read IV, init cipher
        InputStream fileIn = Files.newInputStream(file.toPath());

        byte[] iv = StreamUtils.readByteAmount(CryptoService.AES_IV_SIZE, fileIn);

        Cipher decryptCipher = CryptoService.getAESDecryptCipher(iv, secretKey);
        this.m_delegate = new CipherInputStream(fileIn, decryptCipher);
    }

    public AESBackedInputStream(
        UnsynchronizedByteArrayOutputStream outputStream
    ){
        m_file = null;
        this.m_fileBacked = false;
        this.m_delegate = outputStream.toInputStream();
    }

    public AESBackedInputStream(
        byte[] rawBytes
    ){
        m_file = null;
        this.m_fileBacked = false;
        this.m_delegate = new UnsynchronizedByteArrayInputStream(rawBytes);
    }

    public AESBackedInputStream(
            AESBackedOutputStream aesBackedOutputStream,
            SecretKey secretKey
    ) throws Exception {
        if(aesBackedOutputStream.isFileBacked()){
            this.m_fileBacked = true;
            m_file = aesBackedOutputStream.getFile();
            // Open file, read IV, init cipher
            InputStream fileIn = Files.newInputStream(m_file.toPath());
            byte[] iv = StreamUtils.readByteAmount(CryptoService.AES_IV_SIZE, fileIn);
            Cipher decryptCipher = CryptoService.getAESDecryptCipher(iv, secretKey);
            this.m_delegate = new CipherInputStream(fileIn, decryptCipher);
        }else{  
            m_file = null;
            this.m_fileBacked = false;
            this.m_delegate =  aesBackedOutputStream.getUnsynchronizedByteArrayOutputStream().toInputStream();
        }
    }



    @Override
    public int read() throws IOException {
        return m_delegate.read();
    }

    @Override
    public int read(byte[] b) throws IOException {
        return m_delegate.read(b);
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        return m_delegate.read(b, off, len);
    }

    @Override
    public long skip(long n) throws IOException {
        return m_delegate.skip(n);
    }

    @Override
    public int available() throws IOException {
        return m_delegate.available();
    }

    @Override
    public void close() throws IOException {
        m_delegate.close();
        if(m_file != null){
            try{
                Files.deleteIfExists(m_file.toPath());
            }catch(IOException e){
                System.err.println("Failed to delete temp file: " + m_file.getAbsolutePath());
            }
        }
    }

    @Override
    public void mark(int readlimit) {
        m_delegate.mark(readlimit);
    }

    @Override
    public void reset() throws IOException {
        m_delegate.reset();
    }

    @Override
    public boolean markSupported() {
        return m_delegate.markSupported();
    }

    public boolean isM_fileBacked() {
        return m_fileBacked;
    }

    

}
