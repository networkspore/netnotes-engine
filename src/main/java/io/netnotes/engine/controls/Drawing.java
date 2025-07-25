package io.netnotes.engine.controls;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import javax.imageio.ImageIO;

import io.netnotes.engine.noteBytes.NoteConstants;
import javafx.concurrent.Task;
import javafx.concurrent.WorkerStateEvent;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.image.Image;
import javafx.scene.image.PixelReader;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import scorex.util.encode.Base16;
import javafx.event.EventHandler;


public class Drawing {

    public final static int EMPTY_ARGB = 0x00000000;
    public static java.awt.Color POSITIVE_HIGHLIGHT_COLOR = new java.awt.Color(0xff3dd9a4, true);
    public static java.awt.Color POSITIVE_COLOR = new java.awt.Color(0xff028A0F, true);

    public static java.awt.Color NEGATIVE_COLOR = new java.awt.Color(0xff9A2A2A, true);
    public static java.awt.Color NEGATIVE_HIGHLIGHT_COLOR = new java.awt.Color(0xffe96d71, true);
    public static java.awt.Color NEUTRAL_COLOR = new java.awt.Color(0x111111);


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

    public static void drawBar(Color color1, Color color2, BufferedImage img, int x1, int y1, int x2, int y2) {
        drawBar(0, color1, color2, img, x1, y1, x2, y2);
    }

    public static void drawBar(int direction, Color color1, Color color2, BufferedImage img, int x1, int y1, int x2, int y2) {
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

    public static Image getPosNegText(String text, boolean positive, boolean neutral ) {
     
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
            java.awt.Color color1 = positive ? POSITIVE_COLOR : NEGATIVE_HIGHLIGHT_COLOR;
            java.awt.Color color2 = positive ? POSITIVE_HIGHLIGHT_COLOR : NEGATIVE_COLOR;

            Drawing.drawBarFillColor(positive ? 0 : 1, false, fillColor.getRGB(), color1.getRGB(), color2.getRGB(), img, x1, y1, x2, y2);

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
                byte[] bytes = hex != null && execService != null ? Base16.decode(hex).getOrElse(null) : null;

                if(bytes != null){
                    return new Image( new ByteArrayInputStream(bytes));
                }
                
                throw new NullPointerException(NoteConstants.ERROR_EXISTS);
            }
        };

        task.setOnFailed(onFailed);

        task.setOnSucceeded(onSucceeded);

        return execService.submit(task);
    }

    public static void writeEncodedImageToStream(Image img, OutputStream stream) throws IOException{
        if(img != null){

            ImageIO.write(SwingFXUtils.fromFXImage(img, null), "png", ImageIO.createImageOutputStream(stream));
        }
    }

    public static Image readEncodedImageFromStream(InputStream stream) throws IOException{

        return SwingFXUtils.toFXImage(ImageIO.read(stream), null);
    }
}
