package io.netnotes.engine.noteBytes;

import io.netnotes.engine.noteBytes.ByteDecoding.NoteBytesMetaData;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

public  class NoteVideo extends NoteBytes {
    public NoteVideo(byte[] videoData) {
        super(videoData, NoteBytesMetaData.VIDEO_TYPE);
    }
    public NoteVideo(Path videoPath) throws IOException {
        super(videoToBytes(videoPath), NoteBytesMetaData.VIDEO_TYPE);
    }

    public enum VideoFormat {
        MP4, AVI, MKV, WEBM, OGG, MOV, UNKNOWN
    }
    
    // Magic number signatures for video format detection
    private static final byte[][] MP4_SIGNATURES = {
        {0x66, 0x74, 0x79, 0x70}, // "ftyp" at offset 4
        {0x6D, 0x6F, 0x6F, 0x76}, // "moov"
        {0x6D, 0x64, 0x61, 0x74}  // "mdat"
    };
    
    private static final byte[] AVI_RIFF = {0x52, 0x49, 0x46, 0x46}; // "RIFF"
    private static final byte[] AVI_AVI = {0x41, 0x56, 0x49, 0x20};  // "AVI "
    private static final byte[] MKV_EBML = {0x1A, 0x45, (byte)0xDF, (byte)0xA3}; // EBML
    private static final byte[] OGG_MAGIC = {0x4F, 0x67, 0x67, 0x53}; // "OggS"
    
    /**
     * Store video file as raw bytes in binary format
     */
    public static byte[] videoToBytes(Path videoFile) throws IOException {
        return Files.readAllBytes(videoFile);
    }
    
    /**
     * Store video from InputStream as bytes
     */
    public static byte[] videoToBytes(InputStream videoStream) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int bytesRead;
        
        while ((bytesRead = videoStream.read(buffer)) != -1) {
            baos.write(buffer, 0, bytesRead);
        }
        
        return baos.toByteArray();
    }
    
    /**
     * Get video as InputStream with format detection
     */
    public static VideoStreamInfo getVideoStreamInfo(byte[] videoData) {
        VideoFormat format = detectVideoFormat(videoData);
        InputStream stream = new ByteArrayInputStream(videoData);
        return new VideoStreamInfo(format, stream, videoData.length);
    }
    
    /**
     * Detect video format from byte array header
     */
    public static VideoFormat detectVideoFormat(byte[] data) {
        if (data.length < 12) {
            return VideoFormat.UNKNOWN;
        }
        
        // Check MP4/MOV - look for "ftyp" at offset 4
        if (data.length >= 8) {
            byte[] ftypCheck = Arrays.copyOfRange(data, 4, 8);
            if (Arrays.equals(ftypCheck, MP4_SIGNATURES[0])) {
                return VideoFormat.MP4;
            }
        }
        
        // Check AVI - "RIFF" at start, "AVI " at offset 8
        byte[] riffCheck = Arrays.copyOfRange(data, 0, 4);
        if (Arrays.equals(riffCheck, AVI_RIFF) && data.length >= 12) {
            byte[] aviCheck = Arrays.copyOfRange(data, 8, 12);
            if (Arrays.equals(aviCheck, AVI_AVI)) {
                return VideoFormat.AVI;
            }
        }
        
        // Check MKV/WebM - EBML header
        byte[] ebmlCheck = Arrays.copyOfRange(data, 0, 4);
        if (Arrays.equals(ebmlCheck, MKV_EBML)) {
            return VideoFormat.MKV; // Could be WebM too, but MKV is more general
        }
        
        // Check OGG
        byte[] oggCheck = Arrays.copyOfRange(data, 0, 4);
        if (Arrays.equals(oggCheck, OGG_MAGIC)) {
            return VideoFormat.OGG;
        }
        
        return VideoFormat.UNKNOWN;
    }
    
    /**
     * Create a bounded InputStream for streaming specific byte ranges
     * Useful for seeking or partial downloads
     */
    public static InputStream createBoundedStream(byte[] data, long offset, long length) {
        if (offset >= data.length) {
            return new ByteArrayInputStream(new byte[0]);
        }
        
        int start = (int) Math.max(0, offset);
        int end = (int) Math.min(data.length, offset + length);
        
        return new ByteArrayInputStream(data, start, end - start);
    }
    
    /**
     * Write video directly to OutputStream for streaming
     * Useful when you don't want to load entire video into memory
     */
    public static void streamVideoToOutput(InputStream videoInput, OutputStream output) throws IOException {
        byte[] buffer = new byte[8192];
        int bytesRead;
        
        while ((bytesRead = videoInput.read(buffer)) != -1) {
            output.write(buffer, 0, bytesRead);
        }
    }
    
    // Helper class to hold video stream information
    public static class VideoStreamInfo {
        private final VideoFormat format;
        private final InputStream stream;
        private final long size;
        
        public VideoStreamInfo(VideoFormat format, InputStream stream, long size) {
            this.format = format;
            this.stream = stream;
            this.size = size;
        }
        
        public VideoFormat getFormat() { return format; }
        public InputStream getStream() { return stream; }
        public long getSize() { return size; }
        
        /**
         * Get appropriate MIME type for HTTP streaming
         */
        public String getMimeType() {
            switch (format) {
                case MP4:
                case MOV:
                    return "video/mp4";
                case AVI:
                    return "video/x-msvideo";
                case MKV:
                    return "video/x-matroska";
                case WEBM:
                    return "video/webm";
                case OGG:
                    return "video/ogg";
                default:
                    return "application/octet-stream";
            }
        }
    }
    

    
    /**
     * For JavaFX Media (limited format support)
     */
    public void playWithJavaFX(byte[] videoData) throws IOException {
        // JavaFX Media requires a file or URL, so we'd need to write to temp file
        /*
        Path tempFile = Files.createTempFile("video", ".mp4");
        Files.write(tempFile, videoData);
        
        Media media = new Media(tempFile.toUri().toString());
        MediaPlayer player = new MediaPlayer(media);
        MediaView view = new MediaView(player);
        
        player.play();
        */
    }
    
    /**
     * For HTTP streaming - create response headers
     */
    public static void setupHttpStreamingHeaders(VideoStreamInfo info, 
                                               java.util.Map<String, String> headers) {
        headers.put("Content-Type", info.getMimeType());
        headers.put("Content-Length", String.valueOf(info.getSize()));
        headers.put("Accept-Ranges", "bytes");
        headers.put("Cache-Control", "no-cache");
    }

}
