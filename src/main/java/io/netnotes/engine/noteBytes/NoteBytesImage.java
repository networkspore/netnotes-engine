package io.netnotes.engine.noteBytes;


import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;

import org.bouncycastle.util.encoders.Hex;

import javafx.concurrent.WorkerStateEvent;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.image.Image;
import javafx.scene.image.PixelReader;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.image.WritablePixelFormat;
import javafx.concurrent.Task;
import javafx.event.EventHandler;
import io.netnotes.engine.messaging.NoteMessaging;
import io.netnotes.engine.noteBytes.processing.ByteDecoding.NoteBytesMetaData;
public class NoteBytesImage extends NoteBytes {
    public final static int EMPTY_ARGB = 0x00000000;
    public static final String UNKNWON_IMG_URL = "/assets/unknown-unit.png";
    /**
     * Scaling algorithm enumeration
     */
    public enum ScalingAlgorithm {
        NEAREST_NEIGHBOR,
        BILINEAR,
        BICUBIC,
        AREA_AVERAGING
    }


    public NoteBytesImage(Image image) throws IOException {
        super(getImageAsEncodedBytes(image, NoteMessaging.ImageEncoding.PNG), NoteBytesMetaData.IMAGE_TYPE);
    }

    public NoteBytesImage(Image image, String encoding) throws IOException {
        super(getImageAsEncodedBytes(image, encoding), NoteBytesMetaData.IMAGE_TYPE);
    }

     public static NoteBytes createNoteBytesImage(Image img){
        NoteBytes imgBytes = null;
        
        try{
            imgBytes = new NoteBytesImage(img);
        }catch(IOException e){
          
        }

        return imgBytes;
    }


    @Override
    public String getAsString() {
        return Hex.toHexString(get());
    }

    @Override
    public String toString() {
        return "NoteBytesImage{ size=" + get().length + " bytes}";
    }

    public NoteBytesImage scale(int width, int height, ScalingAlgorithm algorithm) throws IOException {
        Image fxImage = readEncodedImageFromStream(new ByteArrayInputStream(get()));
        BufferedImage buffered = SwingFXUtils.fromFXImage(fxImage, null);
        BufferedImage scaled = scaleImage(buffered, width, height, algorithm);
        return new NoteBytesImage(SwingFXUtils.toFXImage(scaled, null));
    }
    
    public static byte[] getImageAsEncodedBytes(Image fxImage, String encoding) throws IOException {
        BufferedImage bufferedImage = SwingFXUtils.fromFXImage(fxImage, null);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(bufferedImage, "png", baos);
        return baos.toByteArray();
    }

    public static byte[] toRawBytes(Image fxImage) {
        int width = (int) fxImage.getWidth();
        int height = (int) fxImage.getHeight();
        
        PixelReader pixelReader = fxImage.getPixelReader();
        byte[] pixels = new byte[width * height * 4];
        
        WritablePixelFormat<ByteBuffer> format = WritablePixelFormat.getByteBgraInstance();
        pixelReader.getPixels(0, 0, width, height, format, pixels, 0, width * 4);
    
        return pixels;
    }

    public static byte[] toJpegBytes(Image fxImage, float quality) throws IOException {
        BufferedImage bufferedImage = SwingFXUtils.fromFXImage(fxImage, null);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        ImageWriteParam param = writer.getDefaultWriteParam();
        param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        param.setCompressionQuality(quality);
        
        writer.setOutput(ImageIO.createImageOutputStream(baos));
        writer.write(null, new IIOImage(bufferedImage, null, null), param);
        
        return baos.toByteArray();
    }

 

     public static void writeEncodedImageToStream(Image img, OutputStream stream) throws IOException{
        if(img != null){

            ImageIO.write(SwingFXUtils.fromFXImage(img, null), "png", ImageIO.createImageOutputStream(stream));
        }
    }

    public static Image readEncodedImageFromStream(InputStream stream) throws IOException{

        return SwingFXUtils.toFXImage(ImageIO.read(stream), null);
    }

    
    public static void drawBarFillColor(int direction, boolean fillInverse, int fillColor, int RGB1, int RGB2, WritableImage img, PixelReader pR, PixelWriter pW, int x1, int y1, int x2, int y2) {
        
        int maxWidth = (int) img.getWidth()-1;
        int maxHeight = (int) img.getHeight()-1;

        x1 = x1 > maxWidth ? maxWidth : (x1 < 0 ? 0 : x1);
        x2 = x2 > maxWidth ? maxWidth : (x2 < 0 ? 0 : x2);
        y1 = y1 > maxHeight ? maxHeight : (y1 < 0 ? 0 : y1);
        y2 = y2 > maxHeight ? maxHeight : (y2 < 0 ? 0 : y2);

        int a1 = (RGB1 >> 24) & 0xff;
        int r1 = (RGB1 >> 16) & 0xff;
        int g1 = (RGB1 >> 8) & 0xff;
        int b1 = RGB1 & 0xff;

        int a2 = (RGB2 >> 24) & 0xff;
        int r2 = (RGB2 >> 16) & 0xff;
        int g2 = (RGB2 >> 8) & 0xff;
        int b2 = RGB2 & 0xff;

        int i = 0;
        int width;
        int height;
        double scaleA;
        double scaleR;
        double scaleG;
        double scaleB;

        switch (direction) {
            case 1:
                height = (y2 - y1) - 1;
                height = height < 1 ? 1 : height;
                // double middle = height / 2;
                //(0.6d * (double) height) / High

                scaleA = (double) (a2 - a1) / (double) height;
                scaleR = (double) (r2 - r1) / (double) height;
                scaleG = (double) (g2 - g1) / (double) height;
                scaleB = (double) (b2 - b1) / (double) height;

                /*try {
                    Files.writeString(logFile.toPath(), "\nscaleA: " + scaleA + " R " + scaleR + " G " + scaleG + " B " + scaleB, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                } catch (IOException e) {

                }*/
                i = 0;
                for (int x = x1; x < x2; x++) {
                    for (int y = y1; y < y2; y++) {
                        int oldRGB = pR.getArgb(x, y);

                        if (oldRGB == fillColor || (oldRGB != fillColor && fillInverse)) {
                            int a = (int) (a1 + (i * scaleA));
                            int r = (int) (r1 + (i * scaleR));
                            int g = (int) (g1 + (i * scaleG));
                            int b = (int) (b1 + (i * scaleB));

                            /*
                            try {
                                Files.writeString(logFile.toPath(), "\ni: " + i + " a: " + a + " r: " + r + " g: " + g + " b: " + b, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                            } catch (IOException e) {

                            }*/
                            int p = (a << 24) | (r << 16) | (g << 8) | b;

                            pW.setArgb(x, y, blendRGBA(oldRGB, p));
                        }
                        i++;
                    }
                    i = 0;
                }
                break;
            default:
                width = (x2 - x1) - 1;
                width = width < 1 ? 1 : width;
                // double middle = width / 2;
                //(0.6d * (double) height) / High

                scaleA = (double) (a2 - a1) / (double) width;
                scaleR = (double) (r2 - r1) / (double) width;
                scaleG = (double) (g2 - g1) / (double) width;
                scaleB = (double) (b2 - b1) / (double) width;

                i = 0;
                for (int x = x1; x < x2; x++) {
                    for (int y = y1; y < y2; y++) {
                        int oldRGB = pR.getArgb(x, y);

                        if (oldRGB == fillColor || (oldRGB != fillColor && fillInverse)) {

                            int a = (int) (a1 + (i * scaleA));
                            int r = (int) (r1 + (i * scaleR));
                            int g = (int) (g1 + (i * scaleG));
                            int b = (int) (b1 + (i * scaleB));

                            int p = (a << 24) | (r << 16) | (g << 8) | b;

                            pW.setArgb(x, y, blendRGBA(oldRGB, p));
                        }

                    }
                    i++;
                }
                break;
        }
    }

    public static void drawBarFillColor(int direction, boolean fillInverse, int fillColor, int RGB1, int RGB2, BufferedImage img, int x1, int y1, int x2, int y2) {
        
        int maxWidth = img.getWidth()-1;
        int maxHeight = img.getHeight()-1;

        x1 = x1 > maxWidth ? maxWidth : (x1 < 0 ? 0 : x1);
        x2 = x2 > maxWidth ? maxWidth : (x2 < 0 ? 0 : x2);
        y1 = y1 > maxHeight ? maxHeight : (y1 < 0 ? 0 : y1);
        y2 = y2 > maxHeight ? maxHeight : (y2 < 0 ? 0 : y2);

        int a1 = (RGB1 >> 24) & 0xff;
        int r1 = (RGB1 >> 16) & 0xff;
        int g1 = (RGB1 >> 8) & 0xff;
        int b1 = RGB1 & 0xff;



        int a2 = (RGB2 >> 24) & 0xff;
        int r2 = (RGB2 >> 16) & 0xff;
        int g2 = (RGB2 >> 8) & 0xff;
        int b2 = RGB2 & 0xff;

        int i = 0;
        int width;
        int height;
        double scaleA;
        double scaleR;
        double scaleG;
        double scaleB;

        switch (direction) {
            case 1:
                height = (y2 - y1) - 1;
                height = height < 1 ? 1 : height;
                // double middle = height / 2;
                //(0.6d * (double) height) / High

                scaleA = (double) (a2 - a1) / (double) height;
                scaleR = (double) (r2 - r1) / (double) height;
                scaleG = (double) (g2 - g1) / (double) height;
                scaleB = (double) (b2 - b1) / (double) height;

                /*try {
                    Files.writeString(logFile.toPath(), "\nscaleA: " + scaleA + " R " + scaleR + " G " + scaleG + " B " + scaleB, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                } catch (IOException e) {

                }*/
                i = 0;
                for (int x = x1; x < x2; x++) {
                    for (int y = y1; y < y2; y++) {
                        int oldRGB = img.getRGB(x, y);

                        if (oldRGB == fillColor || (oldRGB != fillColor && fillInverse)) {
                            int a = (int) (a1 + (i * scaleA));
                            int r = (int) (r1 + (i * scaleR));
                            int g = (int) (g1 + (i * scaleG));
                            int b = (int) (b1 + (i * scaleB));

                            /*
                            try {
                                Files.writeString(logFile.toPath(), "\ni: " + i + " a: " + a + " r: " + r + " g: " + g + " b: " + b, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                            } catch (IOException e) {

                            }*/
                            int p = (a << 24) | (r << 16) | (g << 8) | b;

                            img.setRGB(x, y, blendRGBA(oldRGB, p));
                        }
                        i++;
                    }
                    i = 0;
                }
                break;
            default:
                width = (x2 - x1) - 1;
                width = width < 1 ? 1 : width;
                // double middle = width / 2;
                //(0.6d * (double) height) / High

                scaleA = (double) (a2 - a1) / (double) width;
                scaleR = (double) (r2 - r1) / (double) width;
                scaleG = (double) (g2 - g1) / (double) width;
                scaleB = (double) (b2 - b1) / (double) width;

                i = 0;
                for (int x = x1; x < x2; x++) {
                    for (int y = y1; y < y2; y++) {
                        int oldRGB = img.getRGB(x, y);

                        if (oldRGB == fillColor || (oldRGB != fillColor && fillInverse)) {

                            int a = (int) (a1 + (i * scaleA));
                            int r = (int) (r1 + (i * scaleR));
                            int g = (int) (g1 + (i * scaleG));
                            int b = (int) (b1 + (i * scaleB));

                            int p = (a << 24) | (r << 16) | (g << 8) | b;

                            img.setRGB(x, y, blendRGBA(oldRGB, p));
                        }

                    }
                    i++;
                }
                break;
        }
    }
    /*
    public static void drawBar(int rgb1, int rgb2, WritableImage img,PixelReader pR, PixelWriter pW, int x1, int y1, int x2, int y2) {
        drawBar(0, rgb1, rgb2, img, pR, pW, x1, y1, x2, y2);
    }*/

    public static void drawBar(int rgb1, int rgb2, BufferedImage img, int x1, int y1, int x2, int y2) {
        drawBar(0, rgb1, rgb2, img, x1, y1, x2, y2);
    }

    public static void drawBar(java.awt.Color color1, java.awt.Color color2, BufferedImage img, int x1, int y1, int x2, int y2) {
        drawBar(0, color1, color2, img, x1, y1, x2, y2);
    }

    public static void drawBar(int direction, java.awt.Color color1, java.awt.Color color2, BufferedImage img, int x1, int y1, int x2, int y2) {
        drawBar(direction, color1.getRGB(), color2.getRGB(), img, x1, y1, x2, y2);
    }

    public static void drawBar(int direction, int RGB1, int RGB2, BufferedImage img, int x1, int y1, int x2, int y2) {

        int a1 = (RGB1 >> 24) & 0xff;
        int r1 = (RGB1 >> 16) & 0xff;
        int g1 = (RGB1 >> 8) & 0xff;
        int b1 = RGB1 & 0xff;

        int a2 = (RGB2 >> 24) & 0xff;
        int r2 = (RGB2 >> 16) & 0xff;
        int g2 = (RGB2 >> 8) & 0xff;
        int b2 = RGB2 & 0xff;

        int i = 0;
        int width;
        int height;
        double scaleA;
        double scaleR;
        double scaleG;
        double scaleB;

        int maxWidth = img.getWidth()-1;
        int maxHeight = img.getHeight()-1;

        x1 = x1 > maxWidth ? maxWidth : (x1 < 0 ? 0 : x1);
        x2 = x2 > maxWidth ? maxWidth : (x2 < 0 ? 0 : x2);
        y1 = y1 > maxHeight ? maxHeight : (y1 < 0 ? 0 : y1);
        y2 = y2 > maxHeight ? maxHeight : (y2 < 0 ? 0 : y2);

        switch (direction) {
            case 1:
                height = (y2 - y1) - 1;
                height = height < 1 ? 1 : height;
                // double middle = height / 2;
                //(0.6d * (double) height) / High

                scaleA = (double) (a2 - a1) / (double) height;
                scaleR = (double) (r2 - r1) / (double) height;
                scaleG = (double) (g2 - g1) / (double) height;
                scaleB = (double) (b2 - b1) / (double) height;

                i = 0;
                for (int x = x1; x < x2; x++) {
                    for (int y = y1; y < y2; y++) {
                        int oldRGB = img.getRGB(x, y);
                        int a = (int) (a1 + (i * scaleA));
                        int r = (int) (r1 + (i * scaleR));
                        int g = (int) (g1 + (i * scaleG));
                        int b = (int) (b1 + (i * scaleB));

                        int p = (a << 24) | (r << 16) | (g << 8) | b;
                        img.setRGB(x, y, blendRGBA(oldRGB, p));
                        i++;
                    }
                    i = 0;
                }
                break;
            default:
                width = (x2 - x1) - 1;
                width = width < 1 ? 1 : width;
                // double middle = width / 2;
                //(0.6d * (double) height) / High

                scaleA = (double) (a2 - a1) / (double) width;
                scaleR = (double) (r2 - r1) / (double) width;
                scaleG = (double) (g2 - g1) / (double) width;
                scaleB = (double) (b2 - b1) / (double) width;

                i = 0;
                for (int x = x1; x < x2; x++) {
                    for (int y = y1; y < y2; y++) {
                        int oldRGB = img.getRGB(x, y);
                        int a = (int) (a1 + (i * scaleA));
                        int r = (int) (r1 + (i * scaleR));
                        int g = (int) (g1 + (i * scaleG));
                        int b = (int) (b1 + (i * scaleB));

                        int p = (a << 24) | (r << 16) | (g << 8) | b;

                        img.setRGB(x, y, blendRGBA(oldRGB, p));

                    }
                    i++;
                }
                break;
        }
    }
   
    /*
    public static void drawBar(int direction, int RGB1, int RGB2, WritableImage img, PixelReader pR, PixelWriter pW, int x1, int y1, int x2, int y2) {

        int a1 = (RGB1 >> 24) & 0xff;
        int r1 = (RGB1 >> 16) & 0xff;
        int g1 = (RGB1 >> 8) & 0xff;
        int b1 = RGB1 & 0xff;

        int a2 = (RGB2 >> 24) & 0xff;
        int r2 = (RGB2 >> 16) & 0xff;
        int g2 = (RGB2 >> 8) & 0xff;
        int b2 = RGB2 & 0xff;

        int i = 0;
        int width;
        int height;
        double scaleA;
        double scaleR;
        double scaleG;
        double scaleB;

        switch (direction) {
            case 1:
                height = (y2 - y1) - 1;
                height = height < 1 ? 1 : height;
                // double middle = height / 2;
                //(0.6d * (double) height) / High

                scaleA = (double) (a2 - a1) / (double) height;
                scaleR = (double) (r2 - r1) / (double) height;
                scaleG = (double) (g2 - g1) / (double) height;
                scaleB = (double) (b2 - b1) / (double) height;

                i = 0;
                for (int x = x1; x < x2; x++) {
                    for (int y = y1; y < y2; y++) {
                        if(x > -1 && x < (int) img.getWidth() && y > -1 && y < (int) img.getHeight()){

                        
                            int oldRGB = pR.getArgb(x, y);

                            int a = (int) (a1 + (i * scaleA));
                            int r = (int) (r1 + (i * scaleR));
                            int g = (int) (g1 + (i * scaleG));
                            int b = (int) (b1 + (i * scaleB));

                            int p = (a << 24) | (r << 16) | (g << 8) | b;
                            pW.setArgb(x, y, blendRGBA(oldRGB, p));
                        }
                        i++;
                    }
                    i = 0;
                }
                break;
            default:
                width = (x2 - x1) - 1;
                width = width < 1 ? 1 : width;
                // double middle = width / 2;
                //(0.6d * (double) height) / High

                scaleA = (double) (a2 - a1) / (double) width;
                scaleR = (double) (r2 - r1) / (double) width;
                scaleG = (double) (g2 - g1) / (double) width;
                scaleB = (double) (b2 - b1) / (double) width;

                i = 0;
                for (int x = x1; x < x2; x++) {
                    for (int y = y1; y < y2; y++) {

                        int oldRGB = pR.getArgb(x, y);
                        int a = (int) (a1 + (i * scaleA));
                        int r = (int) (r1 + (i * scaleR));
                        int g = (int) (g1 + (i * scaleG));
                        int b = (int) (b1 + (i * scaleB));

                        int p = (a << 24) | (r << 16) | (g << 8) | b;
                        
                        pW.setArgb(x, y, blendRGBA(oldRGB, p));

                    }
                    i++;
                }
                break;
        }
    }*/


    public static void fillArea(BufferedImage img, int RGB, int x1, int y1, int x2, int y2) {
        fillArea(img, RGB, x1, y1, x2, y2, true);
    }

    /*int avg = (r + g + b) / 3;*/
    public static void fillArea(BufferedImage img, int RGB, int x1, int y1, int x2, int y2, boolean blend) {
        x1 = x1 < 0 ? 0 : x1;
        y1 = y1 < 0 ? 0 : y1;
        x2 = x2 > img.getWidth()? img.getWidth() : x2;
        y2 = y2 > img.getHeight() ? img.getHeight(): y2;
        

        for (int x = x1; x < x2; x++) {
            for (int y = y1; y < y2; y++) {
                if (blend) {
                    int oldRGB = img.getRGB(x, y);
                    img.setRGB(x, y, blendRGBA(oldRGB, RGB));
                } else {
                    img.setRGB(x, y, RGB);
                }
            }
        }
    }
    public static void fillArea(WritableImage img, PixelReader pR, PixelWriter pW,  int RGB, int x1, int y1, int x2, int y2) {
        fillArea(img, pR, pW, RGB, x1, y1, x2, y2, true);
    }

    public static void fillArea(WritableImage img, PixelReader pR, PixelWriter pW,  int RGB, int x1, int y1, int x2, int y2, boolean blend) {
        x1 = x1 < 0 ? 0 : x1;
        y1 = y1 < 0 ? 0 : y1;
        x2 = x2 > img.getWidth() ? (int) img.getWidth() : x2;
        y2 = y2 > img.getHeight() ? (int) img.getHeight(): y2;
        
        blend = pR == null ? false : blend;
     
        
        for (int x = x1; x < x2; x++) {
            for (int y = y1; y < y2; y++) {
                if (blend) {
                    int oldRGB = pR.getArgb(x, y);
                                        
                    pW.setArgb(x, y, blendRGBA(oldRGB, RGB));
                } else {
                    pW.setArgb(x, y, RGB);
                }
            }
        }
    }
    /*
    public static void drawFadeHLine(WritableImage img, PixelReader pR, PixelWriter pW, int RGB1, int RGB2, int thickness, int x1, int y1, int x2, int y2, boolean blend){
       // int halfThickness = (int) Math.ceil(thickness / 2);
        
        boolean change = y2 < y1;
        int y3 = y1;
        y1 = change ? y2 : y1;
        y2 = change ? y3 : y2;
        
        double m =(y2 - y1) /  (x2 - x1);
        int y = 0;
  

        for (int x = x1; x < x2; x++) {
            //y = m(x − x1) + y2;

            y = (int) (m *(x - x1)) + y2; 
            if(x > 0 && x < img.getWidth() && y > 0 && y < img.getHeight()){
                y3 = y + thickness > img.getHeight() ? (int) img.getHeight() : y + thickness;
                if(y2 < y3){
                    
                }else{
                    
                }
                fillArea(img, pR, pW, RGB1, x1, y1,x2, y2, false);
                drawBar(1,change? RGB2 : RGB1, change ? RGB1 : RGB2, img, pR, pW, x, y , x2, y3 );
            }
           // int direction, int RGB1, int RGB2, WritableImage img, PixelReader pR, PixelWriter pW, 
        }
    }*/

    public static void drawFadeHLine(BufferedImage img, int RGB1, int RGB2, int thickness, int x1, int y1, int x2, int y2, boolean blend){
     //   int halfThickness = (int) Math.ceil(thickness / 2);
        
        boolean change = y2 < y1;
        int y3 = y1;
        y1 = change ? y2 : y1;
        y2 = change ? y3 : y2;
        
        double m =(y2 - y1) /  (x2 - x1);
        int y = 0;
  

        for (int x = x1; x < x2; x++) {
            //y = m(x − x1) + y2;

            y = (int) (m *(x - x1)) + y2; 
            if(x > 0 && x < img.getWidth() && y > 0 && y < img.getHeight()){
                y3 = y + thickness > img.getHeight() ? (int) img.getHeight() : y + thickness;
                if(y2 < y3){
                    
                }else{
                    
                }
                fillArea(img, RGB1, x1, y1,x2, y2, false);
                drawBar(1,change? RGB2 : RGB1, change ? RGB1 : RGB2, img, x, y , x2, y3 );
            }
           // int direction, int RGB1, int RGB2, WritableImage img, PixelReader pR, PixelWriter pW, 
        }
    }

    public static void clearImage(BufferedImage img){
        fillArea(img, EMPTY_ARGB, 0, 0, img.getWidth(), img.getHeight(), false);
    }

    public static void clearImage(WritableImage img){
        
        PixelWriter pW = img.getPixelWriter();

        fillArea(img,null, pW,0x00000000, 0, 0, (int) img.getWidth(), (int) img.getHeight(), false);



    }

    public static void clearImage(WritableImage img, PixelWriter pW, int width, int height){
        
        fillArea(img,null, pW,0x00000000, 0, 0, width, height, false);

    }

    public static void drawImageExact(BufferedImage img, Image img2, int x1, int y1){
        drawImageLimit(img, img2, x1, y1, -1);
    }
    
    public static void drawImageLimit(WritableImage img, PixelReader pR1, PixelWriter pW1, Image img2, int x1, int y1, int limitAlpha){
    
        PixelReader pR = img2.getPixelReader();
        int width = (int) img2.getWidth();
        int height = (int) img2.getHeight();
        int x2 = x1 + width;
        int y2 = y1 + height;
 

        for (int x = x1; x < x2; x++) {
            for (int y = y1; y < y2; y++) {
                int img2x = x - x1;
                int img2y = y - y1;
                
                if(img2x > -1 && img2x < width && img2y > -1 && img2y < height && x > -1 && x < img.getWidth() && y > -1 && y < img.getHeight()){
                
                    int rgba = pR.getArgb(img2x, img2y);
                   
  
                    if(x < img.getWidth() && y < img.getHeight()){
                        int oldRGB = pR1.getArgb(x, y);
                        
                        rgba = blendRGBA(oldRGB, rgba);
                        
                        int a = (rgba >> 24) & 0xff;
                        int r = (rgba >> 16) & 0xff;
                        int g = (rgba >> 8) & 0xff;
                        int b = rgba & 0xff;
    
                        a = limitAlpha < 0 ? a : (a < limitAlpha ? a : limitAlpha);
                        
                        int p = ((a << 24) | (r << 16) | (g << 8) | b);
                        
                        pW1.setArgb(x, y, p );
                    }
                    
                }
            }
        }

    }

    public static void drawImageLimit(BufferedImage img, Image img2, int x1, int y1, int limitAlpha){
    
        PixelReader pR = img2.getPixelReader();
        int width = (int) img2.getWidth();
        int height = (int) img2.getHeight();
        int x2 = x1 + width;
        int y2 = y1 + height;
 

        for (int x = x1; x < x2; x++) {
            for (int y = y1; y < y2; y++) {
                int img2x = x - x1;
                int img2y = y - y1;
                
                if(img2x > -1 && img2x < img2.getWidth() && img2y > -1 && img2y < img2.getWidth() && x > -1 && x < img.getWidth() && y > -1 && y < img.getHeight()){
                
                    int rgba = pR.getArgb(img2x, img2y);
                   
  
                    if(x < img.getWidth() && y < img.getHeight()){
                        int oldRGB = img.getRGB(x, y);
                        
                        rgba = blendRGBA(oldRGB, rgba);
                        
                        int a = (rgba >> 24) & 0xff;
                        int r = (rgba >> 16) & 0xff;
                        int g = (rgba >> 8) & 0xff;
                        int b = rgba & 0xff;
    
                        a = limitAlpha < 0 ? a : (a < limitAlpha ? a : limitAlpha);
                        
                        int p = ((a << 24) | (r << 16) | (g << 8) | b);
                        
                        img.setRGB(x, y, p );
                    }
                    
                }
            }
        }

    }
    
    public static void drawImageExact(WritableImage img, PixelReader pR, PixelWriter pW, BufferedImage img2, int x1, int y1, int width, int height, boolean blend) {
        int x2 = x1 + width;
        int y2 = y1 + height;


        x1 = x1 < 0 ? 0 : x1;
        y1 = y1 < 0 ? 0 : y1;        

        for (int x = x1; x < x2; x++) {
            for (int y = y1; y < y2; y++) {
                int img2x = x - x1;
                int img2y = y - y1;
                

                img2x = img2x < 0 ? 0 : (img2x > img2.getWidth() ? img2.getWidth()  : img2x);
                img2y = img2y < 0 ? 0 : (img2y > img2.getHeight()  ? img2.getHeight() : img2y);
                
                int newRGB = img2.getRGB(img2x, img2y);
                
                if (blend) {
                   
                   
                    if(x < img.getWidth() && y < img.getHeight()){
                        int oldRGB = pR.getArgb(x, y);
                        pW.setArgb(x, y, blendRGBA(oldRGB, newRGB));
                    }
                } else {
                    if(x < img.getWidth() && y < img.getHeight()){
                        pW.setArgb(x, y, newRGB);
                    }
                }
            }
        }
    }

    public static void drawImageExact(BufferedImage img, BufferedImage img2, int x1, int y1, int width, int height, boolean blend) {
        int x2 = x1 + width;
        int y2 = y1 + height;


        x1 = x1 < 0 ? 0 : x1;
        y1 = y1 < 0 ? 0 : y1;        

        for (int x = x1; x < x2; x++) {
            for (int y = y1; y < y2; y++) {
                int img2x = x - x1;
                int img2y = y - y1;
                

                img2x = img2x < 0 ? 0 : (img2x > img2.getWidth() ? img2.getWidth()  : img2x);
                img2y = img2y < 0 ? 0 : (img2y > img2.getHeight()  ? img2.getHeight() : img2y);
                
                int newRGB = img2.getRGB(img2x, img2y);
                
                if (blend) {
                   
                   
                    if(x < img.getWidth() && y < img.getHeight()){
                        int oldRGB = img.getRGB(x, y);
                        img.setRGB(x, y, blendRGBA(oldRGB, newRGB));
                    }
                } else {
                    if(x < img.getWidth() && y < img.getHeight()){
                        img.setRGB(x, y, newRGB);
                    }
                }
            }
        }
    }

    public static void drawImageExact(BufferedImage img, BufferedImage img2, int x1, int y1, boolean blend) {
        int x2 = x1 + img2.getWidth();
        int y2 = y1 + img2.getHeight();


        x1 = x1 < 0 ? 0 : x1;
        y1 = y1 < 0 ? 0 : y1;        

        for (int x = x1; x < x2; x++) {
            for (int y = y1; y < y2; y++) {
                int img2x = x - x1;
                int img2y = y - y1;

                img2x = img2x < 0 ? 0 : (img2x > img2.getWidth() ? img2.getWidth()  : img2x);
                img2y = img2y < 0 ? 0 : (img2y > img2.getHeight()  ? img2.getHeight() : img2y);
                
                int newRGB = img2.getRGB(img2x, img2y);
                
                if (blend) {
                   
                   
                    if(x < img.getWidth() && y < img.getHeight()){
                        int oldRGB = img.getRGB(x, y);
                        img.setRGB(x, y, blendRGBA(oldRGB, newRGB));
                    }
                } else {
                    if(x < img.getWidth() && y < img.getHeight()){
                        img.setRGB(x, y, newRGB);
                    }
                }
            }
        }
    }

    public static void drawImageExact(WritableImage img, Image img2, int x1, int y1, boolean blend){
        PixelReader pR = img.getPixelReader();
        PixelWriter pW = img.getPixelWriter();
        PixelReader pR2 = img2.getPixelReader();
        drawImageExact(img, pR, pW, img2, pR2, x1, y1, blend);
    }

    public static void drawImageExact(WritableImage img, PixelReader pR1, PixelWriter pW, Image img2, PixelReader pR2, int x1, int y1, boolean blend) {
        int imgWidth = (int) img.getWidth();
        int imgHeight = (int) img.getHeight();
        
        int img2Width = (int) img2.getWidth();
        int img2Height = (int) img2.getHeight();

        int x2 = x1 + img2Width;
        int y2 = y1 + img2Height;

        x2 = x2 > imgWidth ? imgWidth  : x2;
        y2 = y2 > imgHeight ? imgHeight : y2;

        x1 = x1 < 0 ? 0 : x1;
        y1 = y1 < 0 ? 0 : y1;        

        for (int x = x1; x < x2; x++) {
            for (int y = y1; y < y2; y++) {
                int img2x = x - x1;
                int img2y = y - y1;

                img2x = img2x < 0 ? 0 : (img2x > img2Width ? img2Width : img2x);
                img2y = img2y < 0 ? 0 : (img2y > img2Height ? img2Height  : img2y);
                
                int newRGB = pR2.getArgb(img2x, img2y);
                
                if (blend) {
                    int oldRGB = pR1.getArgb(x, y);
                   

                    pW.setArgb(x, y, blendRGBA(oldRGB, newRGB));
                } else {
                    pW.setArgb(x, y, newRGB);
                }
            }
        }
    }

    public static void fillAreaDotted(int size, BufferedImage img, int RGB, int x1, int y1, int x2, int y2) {

        int j = 0;
        
        int maxWidth = img.getWidth();
        int maxHeight = img.getHeight();

        x1 = x1 > maxWidth ? maxWidth : (x1 < 0 ? 0 : x1);
        x2 = x2 > maxWidth ? maxWidth : (x2 < 0 ? 0 : x2);
        y1 = y1 > maxHeight ? maxHeight : (y1 < 0 ? 0 : y1);
        y2 = y2 > maxHeight ? maxHeight : (y2 < 0 ? 0 : y2);


        for (int x = x1; x < x2; x += size) {
            for (int y = y1; y < y2; y += size) {

                if (j % 2 == 0) {
                    // int oldRGB = img.getRGB(x, y);
                    //  img.setRGB(x, y, blendRGBA(oldRGB, RGB));
                    fillArea(img, RGB, x, y, x + size, y + size);
                }
                j++;
            }

        }

    }

    public static void fillAreaDotted(int size, WritableImage img, PixelReader pR, PixelWriter pW, int RGB, int x1, int y1, int x2, int y2) {

        int j = 0;
        
        int maxWidth = (int) img.getWidth();
        int maxHeight = (int) img.getHeight();

        x1 = x1 > maxWidth ? maxWidth : (x1 < 0 ? 0 : x1);
        x2 = x2 > maxWidth ? maxWidth : (x2 < 0 ? 0 : x2);
        y1 = y1 > maxHeight ? maxHeight : (y1 < 0 ? 0 : y1);
        y2 = y2 > maxHeight ? maxHeight : (y2 < 0 ? 0 : y2);


        for (int x = x1; x < x2; x += size) {
            for (int y = y1; y < y2; y += size) {

                if (j % 2 == 0) {
                    // int oldRGB = img.getRGB(x, y);
                    //  img.setRGB(x, y, blendRGBA(oldRGB, RGB));
                    fillArea(img,pR, pW, RGB, x, y, x + size, y + size, true);
                }
                j++;
            }

        }

    }

    public static void setImageAlpha(BufferedImage img, int alpha){
        int width = img.getWidth();
        int height = img.getHeight();

        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y ++) {
                int rgba = img.getRGB(x, y);

                int a = (rgba >> 24) & 0xff;
                int r = (rgba >> 16) & 0xff;
                int g = (rgba >> 8) & 0xff;
                int b = rgba & 0xff;

                a = a < alpha ? a : alpha;

                int p = (a << 24) | (r << 16) | (g << 8) | b;

                img.setRGB(x, y, p);
            }

        }
    }


    public static int blendRGBA(int RGB1, int RGB2) {

        int aA = (RGB1 >> 24) & 0xff;
        int rA = (RGB1 >> 16) & 0xff;
        int gA = (RGB1 >> 8) & 0xff;
        int bA = RGB1 & 0xff;

        int aB = (RGB2 >> 24) & 0xff;
        int rB = (RGB2 >> 16) & 0xff;
        int gB = (RGB2 >> 8) & 0xff;
        int bB = RGB2 & 0xff;

        int r = (int) ((rA * (255 - aB) + rB * aB) / 255);
        int g = (int) ((gA * (255 - aB) + gB * aB) / 255);
        int b = (int) ((bA * (255 - aB) + bB * aB) / 255);
        int a = (int) (0xff - ((0xff - aA) * (0xff - aB) / 0xff));

        return (a << 24) | (r << 16) | (g << 8) | b;

    }

    //topleft to bottomRight
    public static void drawLineRect(BufferedImage img, int RGB, int lineSize, int x1, int y1, int x2, int y2) {
        
        fillArea(img, RGB, x1, y1, x1 + lineSize, y2);
        fillArea(img, RGB, x2 - lineSize, y1, x2, y2);

        fillArea(img, RGB, x1, y1, x2, y1 + lineSize);
        fillArea(img, RGB, x1, y2 - lineSize, x1, y2 - lineSize);
    }

    public static Image getPosNegText(String text,java.awt.Color posColor, java.awt.Color posHighlightColor, java.awt.Color negColor, java.awt.Color negHeightlightColor,  boolean positive, boolean neutral ) {
     
        int height = 30;


        java.awt.Font font = new java.awt.Font("OCR A Extended", java.awt.Font.BOLD, 15);

        BufferedImage img = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = img.createGraphics();
        g2d.setFont(font);

        FontMetrics fm = g2d.getFontMetrics();

        int textWidth = fm.stringWidth(text);
        int fontAscent = fm.getAscent();
        int fontHeight = fm.getHeight();
        int stringY = ((height - fontHeight) / 2) + fontAscent;


        img = new BufferedImage(textWidth, height, BufferedImage.TYPE_INT_ARGB);
        g2d = img.createGraphics();

        g2d.setFont(font);


        if (neutral) {
            g2d.setColor(new java.awt.Color(0x777777));
            g2d.drawString(text, 0, stringY);

        } else {
            java.awt.Color fillColor = java.awt.Color.BLUE;
            g2d.setColor(fillColor);
            g2d.drawString(text, 0, stringY);

            int x1 = 0;
            int y1 = (height / 2) - (fontHeight / 2);
            int x2 = textWidth;
            int y2 = y1 + fontHeight;
            java.awt.Color color1 = positive ? posColor : negHeightlightColor;
            java.awt.Color color2 = positive ? posHighlightColor : negColor;

            drawBarFillColor(positive ? 0 : 1, false, fillColor.getRGB(), color1.getRGB(), color2.getRGB(), img, x1, y1, x2, y2);

        }

        g2d.dispose();

        return SwingFXUtils.toFXImage(img, null);
    }

    
    public static BufferedImage fastScale(BufferedImage src, int w, int h)
    {
        BufferedImage img =  new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        int x, y;
        int ww = src.getWidth();
        int hh = src.getHeight();
        int[] ys = new int[h];
        
        for (y = 0; y < h; y++){
            ys[y] = y * hh / h;
        }
        
        for (x = 0; x < w; x++) {
            int newX = x * ww / w;
            for (y = 0; y < h; y++) {
                int col = src.getRGB(newX, ys[y]);
                img.setRGB(x, y, col);
            }
        }
        
        return img;
    }

    public static BufferedImage resizeImage(BufferedImage buf, int width, int height) {
        final BufferedImage bufImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        final Graphics2D g2 = bufImage.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g2.drawImage(buf, 0, 0, width, height, null);
        g2.dispose();
       
        return bufImage;
    }
    


    public static BufferedImage resizeImage(BufferedImage buf, int width, int height, boolean maintainRatio){
        if(!maintainRatio){
            return resizeImage(buf, width, height);  
        }
        if(buf.getWidth() == buf.getHeight()){
            int size = width < height ? width : height;

            return resizeImage(buf, size, size);
        }else{
            double wR = buf.getWidth() / width;
            double hR = buf.getHeight() / height;
            boolean gT = wR > 1 || hR > 1;

             if(gT ? wR < hR : wR > hR){
                wR = 1 /wR;
                return resizeImage(buf, (int)(wR * buf.getWidth()), (int)(wR * buf.getHeight()));
             }else{
                hR = 1 / hR;
                return resizeImage(buf, (int)(hR * buf.getWidth()), (int)(hR * buf.getHeight()));
             }
        }  
    }

    public static Dimension getScaledDimension(Dimension imgSize, Dimension boundary) {
        
        int original_width = imgSize.width;
        int original_height = imgSize.height;
        int bound_width = boundary.width;
        int bound_height = boundary.height;
        int new_width = original_width;
        int new_height = original_height;
    
        // first check if we need to scale width
        if (original_width > bound_width) {
            //scale width to fit
            new_width = bound_width;
            //scale height to maintain aspect ratio
            new_height = (new_width * original_height) / original_width;
        }
    
        // then check if we need to scale even with the new height
        if (new_height > bound_height) {
            //scale height to fit instead
            new_height = bound_height;
            //scale width to maintain aspect ratio
            new_width = (new_height * original_width) / original_height;
        }
    
        return new Dimension(new_width, new_height);
    }

    public static Future<?> convertHexStringToImg(String hex, ExecutorService execService, EventHandler<WorkerStateEvent> onSucceeded, EventHandler<WorkerStateEvent> onFailed){
        Task<Object> task = new Task<Object>() {
            @Override
            public Object call() throws NullPointerException{
                byte[] bytes = hex != null && execService != null ? Hex.decode(hex) : null;

                if(bytes != null){
                    return new Image( new ByteArrayInputStream(bytes));
                }
                
                throw new NullPointerException(NoteMessaging.Error.INVALID);
            }
        };

        task.setOnFailed(onFailed);

        task.setOnSucceeded(onSucceeded);

        return execService.submit(task);
    }

    
    // Enhanced scaling methods for NoteBytesImage class

    /**
     * Scale image using specified algorithm with quality control
     */
    public static BufferedImage scaleImage(BufferedImage src, int targetWidth, int targetHeight, 
                                        ScalingAlgorithm algorithm) {
        if (src == null || targetWidth <= 0 || targetHeight <= 0) {
            throw new IllegalArgumentException("Invalid source image or dimensions");
        }
        
        switch (algorithm) {
            case NEAREST_NEIGHBOR:
                return scaleNearestNeighbor(src, targetWidth, targetHeight);
            case BILINEAR:
                return scaleBilinear(src, targetWidth, targetHeight);
            case BICUBIC:
                return scaleBicubic(src, targetWidth, targetHeight);
            case AREA_AVERAGING:
                return scaleAreaAveraging(src, targetWidth, targetHeight);
            default:
                return scaleBilinear(src, targetWidth, targetHeight);
        }
    }

    /**
     * Scale with aspect ratio preservation
     */
    public static BufferedImage scaleWithAspectRatio(BufferedImage src, int maxWidth, int maxHeight, 
                                                    ScalingAlgorithm algorithm) {
        Dimension scaled = getScaledDimension(
            new Dimension(src.getWidth(), src.getHeight()),
            new Dimension(maxWidth, maxHeight)
        );
        return scaleImage(src, scaled.width, scaled.height, algorithm);
    }

    /**
     * Nearest neighbor scaling - fast but pixelated
     */
    public static BufferedImage scaleNearestNeighbor(BufferedImage src, int targetWidth, int targetHeight) {
        BufferedImage result = new BufferedImage(targetWidth, targetHeight, src.getType());
        
        int srcWidth = src.getWidth();
        int srcHeight = src.getHeight();
        
        double xRatio = (double) srcWidth / targetWidth;
        double yRatio = (double) srcHeight / targetHeight;
        
        for (int y = 0; y < targetHeight; y++) {
            for (int x = 0; x < targetWidth; x++) {
                int srcX = (int) (x * xRatio);
                int srcY = (int) (y * yRatio);
                
                // Clamp to source bounds
                srcX = Math.min(srcX, srcWidth - 1);
                srcY = Math.min(srcY, srcHeight - 1);
                
                result.setRGB(x, y, src.getRGB(srcX, srcY));
            }
        }
        
        return result;
    }

    /**
     * Bilinear interpolation scaling - good quality/performance balance
     */
    public static BufferedImage scaleBilinear(BufferedImage src, int targetWidth, int targetHeight) {
        BufferedImage result = new BufferedImage(targetWidth, targetHeight, src.getType());
        
        int srcWidth = src.getWidth();
        int srcHeight = src.getHeight();
        
        double xRatio = (double) (srcWidth - 1) / targetWidth;
        double yRatio = (double) (srcHeight - 1) / targetHeight;
        
        for (int y = 0; y < targetHeight; y++) {
            for (int x = 0; x < targetWidth; x++) {
                double srcX = x * xRatio;
                double srcY = y * yRatio;
                
                int x1 = (int) srcX;
                int y1 = (int) srcY;
                int x2 = Math.min(x1 + 1, srcWidth - 1);
                int y2 = Math.min(y1 + 1, srcHeight - 1);
                
                double xWeight = srcX - x1;
                double yWeight = srcY - y1;
                
                int rgb = bilinearInterpolate(
                    src.getRGB(x1, y1), src.getRGB(x2, y1),
                    src.getRGB(x1, y2), src.getRGB(x2, y2),
                    xWeight, yWeight
                );
                
                result.setRGB(x, y, rgb);
            }
        }
        
        return result;
    }

    /**
     * Bicubic scaling using Graphics2D - high quality
     */
    public static BufferedImage scaleBicubic(BufferedImage src, int targetWidth, int targetHeight) {
        BufferedImage result = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = result.createGraphics();
        
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        
        g2d.drawImage(src, 0, 0, targetWidth, targetHeight, null);
        g2d.dispose();
        
        return result;
    }

    /**
     * Area averaging for downscaling - reduces aliasing
     */
    public static BufferedImage scaleAreaAveraging(BufferedImage src, int targetWidth, int targetHeight) {
        // For upscaling, fall back to bilinear
        if (targetWidth > src.getWidth() || targetHeight > src.getHeight()) {
            return scaleBilinear(src, targetWidth, targetHeight);
        }
        
        BufferedImage result = new BufferedImage(targetWidth, targetHeight, src.getType());
        
        double xRatio = (double) src.getWidth() / targetWidth;
        double yRatio = (double) src.getHeight() / targetHeight;
        
        for (int y = 0; y < targetHeight; y++) {
            for (int x = 0; x < targetWidth; x++) {
                int srcX1 = (int) (x * xRatio);
                int srcY1 = (int) (y * yRatio);
                int srcX2 = (int) ((x + 1) * xRatio);
                int srcY2 = (int) ((y + 1) * yRatio);
                
                srcX2 = Math.min(srcX2, src.getWidth());
                srcY2 = Math.min(srcY2, src.getHeight());
                
                long totalR = 0, totalG = 0, totalB = 0, totalA = 0;
                int pixelCount = 0;
                
                for (int sy = srcY1; sy < srcY2; sy++) {
                    for (int sx = srcX1; sx < srcX2; sx++) {
                        int rgb = src.getRGB(sx, sy);
                        totalA += (rgb >> 24) & 0xFF;
                        totalR += (rgb >> 16) & 0xFF;
                        totalG += (rgb >> 8) & 0xFF;
                        totalB += rgb & 0xFF;
                        pixelCount++;
                    }
                }
                
                if (pixelCount > 0) {
                    int avgA = (int) (totalA / pixelCount);
                    int avgR = (int) (totalR / pixelCount);
                    int avgG = (int) (totalG / pixelCount);
                    int avgB = (int) (totalB / pixelCount);
                    
                    int avgRGB = (avgA << 24) | (avgR << 16) | (avgG << 8) | avgB;
                    result.setRGB(x, y, avgRGB);
                }
            }
        }
        
        return result;
    }

    /**
     * Progressive scaling for large size differences - better quality
     */
    public static BufferedImage scaleProgressive(BufferedImage src, int targetWidth, int targetHeight, 
                                            ScalingAlgorithm algorithm) {
        int srcWidth = src.getWidth();
        int srcHeight = src.getHeight();
        
        // If scaling down by more than 50%, do it progressively
        if (targetWidth < srcWidth * 0.5 || targetHeight < srcHeight * 0.5) {
            BufferedImage current = src;
            
            while (current.getWidth() > targetWidth * 2 || current.getHeight() > targetHeight * 2) {
                int newWidth = Math.max(current.getWidth() / 2, targetWidth);
                int newHeight = Math.max(current.getHeight() / 2, targetHeight);
                current = scaleImage(current, newWidth, newHeight, algorithm);
            }
            
            // Final scale to exact target
            return scaleImage(current, targetWidth, targetHeight, algorithm);
        } else {
            return scaleImage(src, targetWidth, targetHeight, algorithm);
        }
    }

    /**
     * Scale to fit within bounds while maintaining aspect ratio
     */
    public static BufferedImage scaleToFit(BufferedImage src, int maxWidth, int maxHeight, 
                                        ScalingAlgorithm algorithm) {
        return scaleWithAspectRatio(src, maxWidth, maxHeight, algorithm);
    }

    /**
     * Scale to fill bounds (may crop) while maintaining aspect ratio
     */
    public static BufferedImage scaleToFill(BufferedImage src, int targetWidth, int targetHeight, 
                                        ScalingAlgorithm algorithm) {
        double srcRatio = (double) src.getWidth() / src.getHeight();
        double targetRatio = (double) targetWidth / targetHeight;
        
        int scaleWidth, scaleHeight;
        
        if (srcRatio > targetRatio) {
            // Source is wider, scale by height
            scaleHeight = targetHeight;
            scaleWidth = (int) (targetHeight * srcRatio);
        } else {
            // Source is taller, scale by width
            scaleWidth = targetWidth;
            scaleHeight = (int) (targetWidth / srcRatio);
        }
        
        BufferedImage scaled = scaleImage(src, scaleWidth, scaleHeight, algorithm);
        
        // Crop to target size
        int cropX = (scaleWidth - targetWidth) / 2;
        int cropY = (scaleHeight - targetHeight) / 2;
        
        return scaled.getSubimage(cropX, cropY, targetWidth, targetHeight);
    }

    /**
     * Helper method for bilinear interpolation
     */
    private static int bilinearInterpolate(int rgb00, int rgb10, int rgb01, int rgb11, 
                                        double xWeight, double yWeight) {
        int a00 = (rgb00 >> 24) & 0xFF;
        int r00 = (rgb00 >> 16) & 0xFF;
        int g00 = (rgb00 >> 8) & 0xFF;
        int b00 = rgb00 & 0xFF;
        
        int a10 = (rgb10 >> 24) & 0xFF;
        int r10 = (rgb10 >> 16) & 0xFF;
        int g10 = (rgb10 >> 8) & 0xFF;
        int b10 = rgb10 & 0xFF;
        
        int a01 = (rgb01 >> 24) & 0xFF;
        int r01 = (rgb01 >> 16) & 0xFF;
        int g01 = (rgb01 >> 8) & 0xFF;
        int b01 = rgb01 & 0xFF;
        
        int a11 = (rgb11 >> 24) & 0xFF;
        int r11 = (rgb11 >> 16) & 0xFF;
        int g11 = (rgb11 >> 8) & 0xFF;
        int b11 = rgb11 & 0xFF;
        
        int a = (int) (a00 * (1 - xWeight) * (1 - yWeight) +
                    a10 * xWeight * (1 - yWeight) +
                    a01 * (1 - xWeight) * yWeight +
                    a11 * xWeight * yWeight);
        
        int r = (int) (r00 * (1 - xWeight) * (1 - yWeight) +
                    r10 * xWeight * (1 - yWeight) +
                    r01 * (1 - xWeight) * yWeight +
                    r11 * xWeight * yWeight);
        
        int g = (int) (g00 * (1 - xWeight) * (1 - yWeight) +
                    g10 * xWeight * (1 - yWeight) +
                    g01 * (1 - xWeight) * yWeight +
                    g11 * xWeight * yWeight);
        
        int b = (int) (b00 * (1 - xWeight) * (1 - yWeight) +
                    b10 * xWeight * (1 - yWeight) +
                    b01 * (1 - xWeight) * yWeight +
                    b11 * xWeight * yWeight);
        
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    /**
     * Get memory-efficient thumbnail
     */
    public static BufferedImage createThumbnail(BufferedImage src, int maxSize) {
        return scaleWithAspectRatio(src, maxSize, maxSize, ScalingAlgorithm.AREA_AVERAGING);
    }

    /**
     * Optimized scaling for power-of-2 sizes (useful for textures)
     */
    public static BufferedImage scaleToPowerOfTwo(BufferedImage src, ScalingAlgorithm algorithm) {
        int width = src.getWidth();
        int height = src.getHeight();
        
        int newWidth = nextPowerOfTwo(width);
        int newHeight = nextPowerOfTwo(height);
        
        if (newWidth == width && newHeight == height) {
            return src; // Already power of two
        }
        
        return scaleImage(src, newWidth, newHeight, algorithm);
    }

    private static int nextPowerOfTwo(int n) {
        n--;
        n |= n >> 1;
        n |= n >> 2;
        n |= n >> 4;
        n |= n >> 8;
        n |= n >> 16;
        return n + 1;
    }

    public static BufferedImage makeSeamlessTile(BufferedImage src) {
        int width = src.getWidth();
        int height = src.getHeight();
        BufferedImage result = new BufferedImage(width, height, src.getType());
        
        // Copy original image
        Graphics2D g2d = result.createGraphics();
        g2d.drawImage(src, 0, 0, null);
        g2d.dispose();
        
        // Apply offset filter - shift by half dimensions
        int offsetX = width / 2;
        int offsetY = height / 2;
        
        BufferedImage shifted = new BufferedImage(width, height, src.getType());
        
        // Shift the image
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int srcX = (x + offsetX) % width;
                int srcY = (y + offsetY) % height;
                shifted.setRGB(x, y, result.getRGB(srcX, srcY));
            }
        }
        
        // Blend the shifted version with original using soft blending
        return blendForSeamless(result, shifted);
    }

    /**
     * Advanced seamless tiling using mirror and blend technique
     */
    public static BufferedImage makeSeamlessTileMirror(BufferedImage src) {
        int width = src.getWidth();
        int height = src.getHeight();
        BufferedImage result = new BufferedImage(width, height, src.getType());
        
        // Create mirrored edges
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int rgb = src.getRGB(x, y);
                
                // Calculate distance from edges
                double distFromLeftEdge = (double) x / width;
                double distFromRightEdge = (double) (width - x) / width;
                double distFromTopEdge = (double) y / height;
                double distFromBottomEdge = (double) (height - y) / height;
                
                // Find minimum distance to any edge
                double minDistToEdge = Math.min(Math.min(distFromLeftEdge, distFromRightEdge),
                                            Math.min(distFromTopEdge, distFromBottomEdge));
                
                // If close to edge, blend with mirrored pixel
                if (minDistToEdge < 0.15) { // 15% border
                    int mirrorX = x;
                    int mirrorY = y;
                    
                    if (distFromLeftEdge < 0.15) mirrorX = width - 1 - x;
                    if (distFromRightEdge < 0.15) mirrorX = width - 1 - x;
                    if (distFromTopEdge < 0.15) mirrorY = height - 1 - y;
                    if (distFromBottomEdge < 0.15) mirrorY = height - 1 - y;
                    
                    int mirrorRgb = src.getRGB(mirrorX, mirrorY);
                    double blendFactor = (0.15 - minDistToEdge) / 0.15;
                    rgb = blendRGBA(rgb, mirrorRgb, blendFactor);
                }
                
                result.setRGB(x, y, rgb);
            }
        }
        
        return result;
    }

    /**
     * Wang tiling - creates multiple tile variants that can connect seamlessly
     * Returns array of tiles with different edge colors for proper matching
     */
    public static BufferedImage[] createWangTiles(BufferedImage src, int tileCount) {
        BufferedImage[] tiles = new BufferedImage[tileCount];
        Random random = new Random();
        
        for (int i = 0; i < tileCount; i++) {
            tiles[i] = createWangTileVariant(src, random.nextLong());
        }
        
        return tiles;
    }

    /**
     * Create a single Wang tile variant with specified seed for reproducible randomness
     */
    public static BufferedImage createWangTileVariant(BufferedImage src, long seed) {
        Random random = new Random(seed);
        BufferedImage result = new BufferedImage(src.getWidth(), src.getHeight(), src.getType());
        
        // Start with seamless base
        result = makeSeamlessTile(src);
        
        // Apply procedural modifications
        result = applyNoiseVariation(result, random, 0.1f); // 10% noise variation
        result = applyColorShift(result, random, 0.05f); // 5% color shift
        result = applyMicroDisplacement(result, random, 2); // 2 pixel max displacement
        
        return result;
    }

    /**
     * Position-based tile generation - creates unique tile based on grid coordinates
     * This ensures neighboring tiles are similar but not identical
     */
    public static BufferedImage createPositionalTile(BufferedImage baseTile, int gridX, int gridY) {
        // Use grid coordinates as seed for reproducible but varied results
        long seed = ((long)gridX << 32) | (gridY & 0xffffffffL);
        Random random = new Random(seed);
        
        BufferedImage result = new BufferedImage(baseTile.getWidth(), baseTile.getHeight(), baseTile.getType());
        
        // Copy base tile
        Graphics2D g2d = result.createGraphics();
        g2d.drawImage(baseTile, 0, 0, null);
        g2d.dispose();
        
        // Apply position-based variations
        float variationStrength = 0.08f; // 8% variation
        
        // Slight rotation based on position
        double rotationAngle = (Math.sin(gridX * 0.1) * Math.cos(gridY * 0.1)) * Math.PI / 32; // Small rotation
        if (Math.abs(rotationAngle) > 0.01) {
            result = rotateImage(result, rotationAngle);
        }
        
        // Color variation based on position
        result = applyColorShift(result, random, variationStrength);
        
        // Subtle noise based on position
        result = applyNoiseVariation(result, random, variationStrength * 0.5f);
        
        return result;
    }

    /**
     * Perlin noise-based tile shuffling for organic variation
     */
    public static BufferedImage createPerlinVariantTile(BufferedImage src, int tileX, int tileY, 
                                                    double noiseScale, float strength) {
        int width = src.getWidth();
        int height = src.getHeight();
        BufferedImage result = new BufferedImage(width, height, src.getType());
        
        // Generate Perlin noise values for this tile position
        double baseNoiseX = tileX * noiseScale;
        double baseNoiseY = tileY * noiseScale;
        
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                // Calculate noise-based offset
                double noiseX = baseNoiseX + (x / (double)width) * noiseScale;
                double noiseY = baseNoiseY + (y / (double)height) * noiseScale;
                
                double noise = simplexNoise(noiseX, noiseY);
                
                // Apply displacement based on noise
                int offsetX = (int)(noise * strength * width * 0.1);
                int offsetY = (int)(noise * strength * height * 0.1);
                
                int srcX = Math.floorMod(x + offsetX, width);
                int srcY = Math.floorMod(y + offsetY, height);
                
                int rgb = src.getRGB(srcX, srcY);
                
                // Apply subtle color variation based on noise
                if (strength > 0.1f) {
                    rgb = applyNoiseColorShift(rgb, noise, strength * 0.2f);
                }
                
                result.setRGB(x, y, rgb);
            }
        }
        
        return result;
    }

    /**
     * Create a tileable texture atlas - multiple variations in one image
     */
    public static BufferedImage createTileAtlas(BufferedImage baseTile, int atlasWidth, int atlasHeight) {
        int tileWidth = baseTile.getWidth();
        int tileHeight = baseTile.getHeight();
        
        BufferedImage atlas = new BufferedImage(
            atlasWidth * tileWidth, 
            atlasHeight * tileHeight, 
            baseTile.getType()
        );
        
        Graphics2D g2d = atlas.createGraphics();
        
        for (int y = 0; y < atlasHeight; y++) {
            for (int x = 0; x < atlasWidth; x++) {
                BufferedImage variant = createPositionalTile(baseTile, x, y);
                g2d.drawImage(variant, x * tileWidth, y * tileHeight, null);
            }
        }
        
        g2d.dispose();
        return atlas;
    }

    // Helper methods

    private static BufferedImage blendForSeamless(BufferedImage img1, BufferedImage img2) {
        int width = img1.getWidth();
        int height = img1.getHeight();
        BufferedImage result = new BufferedImage(width, height, img1.getType());
        
        int blendWidth = width / 4; // 25% blend zone
        int blendHeight = height / 4;
        
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int rgb1 = img1.getRGB(x, y);
                int rgb2 = img2.getRGB(x, y);
                
                // Calculate blend factor based on distance from seam areas
                double blendFactor = 0.5; // Default 50/50 blend
                
                if (x < blendWidth || x >= width - blendWidth || 
                    y < blendHeight || y >= height - blendHeight) {
                    // In seam area - use gradient blending
                    double distFromSeam = Math.min(
                        Math.min(x, width - x),
                        Math.min(y, height - y)
                    );
                    blendFactor = Math.max(0.3, Math.min(0.7, distFromSeam / (double)Math.min(blendWidth, blendHeight)));
                }
                
                result.setRGB(x, y, blendRGBA(rgb1, rgb2, blendFactor));
            }
        }
        
        return result;
    }

    private static BufferedImage applyNoiseVariation(BufferedImage src, Random random, float strength) {
        int width = src.getWidth();
        int height = src.getHeight();
        BufferedImage result = new BufferedImage(width, height, src.getType());
        
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int rgb = src.getRGB(x, y);
                
                int a = (rgb >> 24) & 0xFF;
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;
                
                // Add random variation
                int variation = (int)((random.nextGaussian() * 255 * strength));
                
                r = Math.max(0, Math.min(255, r + variation));
                g = Math.max(0, Math.min(255, g + variation));
                b = Math.max(0, Math.min(255, b + variation));
                
                result.setRGB(x, y, (a << 24) | (r << 16) | (g << 8) | b);
            }
        }
        
        return result;
    }

    private static BufferedImage applyColorShift(BufferedImage src, Random random, float strength) {
        int width = src.getWidth();
        int height = src.getHeight();
        BufferedImage result = new BufferedImage(width, height, src.getType());
        
        // Generate consistent color shift for entire tile
        float hueShift = (random.nextFloat() - 0.5f) * strength * 2.0f;
        float satShift = (random.nextFloat() - 0.5f) * strength;
        float briShift = (random.nextFloat() - 0.5f) * strength * 0.5f;
        
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int rgb = src.getRGB(x, y);
                result.setRGB(x, y, shiftHSB(rgb, hueShift, satShift, briShift));
            }
        }
        
        return result;
    }

    private static BufferedImage applyMicroDisplacement(BufferedImage src, Random random, int maxDisplacement) {
        int width = src.getWidth();
        int height = src.getHeight();
        BufferedImage result = new BufferedImage(width, height, src.getType());
        
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int offsetX = random.nextInt(maxDisplacement * 2 + 1) - maxDisplacement;
                int offsetY = random.nextInt(maxDisplacement * 2 + 1) - maxDisplacement;
                
                int srcX = Math.floorMod(x + offsetX, width);
                int srcY = Math.floorMod(y + offsetY, height);
                
                result.setRGB(x, y, src.getRGB(srcX, srcY));
            }
        }
        
        return result;
    }

    private static BufferedImage rotateImage(BufferedImage src, double angle) {
        int width = src.getWidth();
        int height = src.getHeight();
        BufferedImage result = new BufferedImage(width, height, src.getType());
        
        Graphics2D g2d = result.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2d.rotate(angle, width / 2.0, height / 2.0);
        g2d.drawImage(src, 0, 0, null);
        g2d.dispose();
        
        return result;
    }

    private static int blendRGBA(int rgb1, int rgb2, double factor) {
        int a1 = (rgb1 >> 24) & 0xFF;
        int r1 = (rgb1 >> 16) & 0xFF;
        int g1 = (rgb1 >> 8) & 0xFF;
        int b1 = rgb1 & 0xFF;
        
        int a2 = (rgb2 >> 24) & 0xFF;
        int r2 = (rgb2 >> 16) & 0xFF;
        int g2 = (rgb2 >> 8) & 0xFF;
        int b2 = rgb2 & 0xFF;
        
        int a = (int)(a1 * factor + a2 * (1 - factor));
        int r = (int)(r1 * factor + r2 * (1 - factor));
        int g = (int)(g1 * factor + g2 * (1 - factor));
        int b = (int)(b1 * factor + b2 * (1 - factor));
        
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static int shiftHSB(int rgb, float hueShift, float satShift, float briShift) {
        int a = (rgb >> 24) & 0xFF;
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        
        float[] hsb = java.awt.Color.RGBtoHSB(r, g, b, null);
        
        hsb[0] = (hsb[0] + hueShift) % 1.0f;
        if (hsb[0] < 0) hsb[0] += 1.0f;
        
        hsb[1] = Math.max(0, Math.min(1, hsb[1] + satShift));
        hsb[2] = Math.max(0, Math.min(1, hsb[2] + briShift));
        
        int newRgb = java.awt.Color.HSBtoRGB(hsb[0], hsb[1], hsb[2]);
        return (a << 24) | (newRgb & 0xFFFFFF);
    }

    private static int applyNoiseColorShift(int rgb, double noise, float strength) {
        int a = (rgb >> 24) & 0xFF;
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        
        int variation = (int)(noise * 255 * strength);
        
        r = Math.max(0, Math.min(255, r + variation));
        g = Math.max(0, Math.min(255, g + variation));
        b = Math.max(0, Math.min(255, b + variation));
        
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    // Simplified Simplex noise implementation
    private static double simplexNoise(double x, double y) {
        // This is a simplified version - for production use, consider a full implementation
        return (Math.sin(x * 12.9898) * Math.cos(y * 78.233) * 43758.5453) % 1.0;
    }
}
