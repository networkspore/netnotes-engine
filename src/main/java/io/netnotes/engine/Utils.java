package io.netnotes.engine;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import java.text.DecimalFormat;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;
import java.util.zip.ZipFile;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.ShortBufferException;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.io.StringReader;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.net.URLConnection;
import java.lang.Double;

import javafx.animation.PauseTransition;
import javafx.beans.binding.Binding;
import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.value.ChangeListener;
import javafx.collections.*;
import javafx.concurrent.Task;
import javafx.concurrent.WorkerStateEvent;
import javafx.event.EventHandler;
import javafx.geometry.Point2D;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.HBox;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;
import oshi.util.tuples.Pair;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.Region;
import javafx.scene.control.Tooltip;

import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.input.ReaderInputStream;
import org.ergoplatform.sdk.SecretString;

import java.io.FilenameFilter;
import at.favre.lib.crypto.bcrypt.BCrypt;
import at.favre.lib.crypto.bcrypt.LongPasswordStrategies;
import io.netnotes.engine.apps.AppConstants;
import io.netnotes.ove.crypto.digest.Blake2b;
import scala.util.Try;
import scorex.util.encode.Base64;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class Utils {
    public static final KeyCombination keyCombCtrZ = new KeyCodeCombination(KeyCode.Z, KeyCombination.SHORTCUT_DOWN);
    public static final int DEFAULT_BUFFER_SIZE = 2048;
    public static final int PRINTABLE_CHAR_RANGE_START = 32;
    public static final int PRINTABLE_CHAR_RANGE_END = 127;
    



    public static byte[] getTimeStampBytes(){
        long timeStamp = System.currentTimeMillis();
        return longToBytes(timeStamp);
    }


    public static char[] getPrintableCharArray(){
        return getCharRange(PRINTABLE_CHAR_RANGE_START, PRINTABLE_CHAR_RANGE_END);
    }

    public static ByteBuffer getLongBuffer() {
        return ByteBuffer.allocate(Long.BYTES);
    }

    public static byte[] longToBytes(long x){
        return longToBytes(getLongBuffer(), x);
    }

    public static byte[] longToBytes(ByteBuffer buffer, long x) {
        buffer.putLong(x);
        return buffer.array();
    }



    public static byte[] unboxInts(Integer[] integerObjects){
        byte[] bytes = new byte[integerObjects.length];
        int i = 0;
        for(Integer myInt: integerObjects)
            bytes[i++] = (byte) (myInt & 0xff); 
        return bytes;
    }


    public static long bytesToLong(ByteBuffer buffer, byte[] bytes) {
        buffer.put(bytes);
        buffer.flip();//need flip 
        return buffer.getLong();
    }


    public static char[] getCharRange(int rangeStart, int rangeEnd){
        char[] chars = new char[rangeEnd - rangeStart];
        int j = 0;
        int i = 32;
        while(i < 127){
            chars[j] = (char) i;
            j++;
            i++;
        }

        return chars;
    }

    public final static char[] getAsciiCharArray(){
        char[] chars = new char[128];

        for(int i = 0; i < 128; i++){
            chars[i] = (char) i;
        }

        return chars;
    }

    // Security.addProvider(new Blake2bProvider());
    public final static String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/56.0.2924.87 Safari/537.36";

    public static String getBcryptHashString(char[] password) {
        SecureRandom sr = new SecureRandom();
    

        return BCrypt.with(BCrypt.Version.VERSION_2A, sr, LongPasswordStrategies.hashSha512(BCrypt.Version.VERSION_2A)).hashToString(15, password);
    }

    public static boolean verifyBCryptPassword(char[] password, String hash) {
        BCrypt.Result result = BCrypt.verifyer(BCrypt.Version.VERSION_2A, LongPasswordStrategies.hashSha512(BCrypt.Version.VERSION_2A)).verify(password, hash.getBytes());

        return result.verified;
    }

    public static byte[] digestFile(File file) throws  IOException {

        return digestFileBlake2b(file,32);
        
    }

    private static volatile long m_rootsTimeStamp = 0;
    private static File[] m_roots = null;

    public static boolean findPathPrefixInRoots(String filePathString){
        File roots[] = getRoots();
        return findPathPrefixInRoots(roots, filePathString);
    }

    public static File[] getRoots(){
    
        if((System.currentTimeMillis() - m_rootsTimeStamp) > 1000){
            m_roots = File.listRoots();
            m_rootsTimeStamp = System.currentTimeMillis();
            return m_roots;
        }else{
            return m_roots;
        }
    }


    public static boolean findPathPrefixInRoots(File roots[], String filePathString){
        

        if(roots != null && roots.length > 0 && filePathString != null && filePathString.length() > 0){

            String appDirPrefix = FilenameUtils.getPrefix(filePathString);

            for(int i = 0; i < roots.length; i++){
                String rootString = roots[i].getAbsolutePath();

                if(rootString.startsWith(appDirPrefix)){
                    return true;
                }
            }
        }

        return false;
    }




    public static byte[] digestFileBlake2b(File file, int digestLength) throws IOException {
        final Blake2b digest = Blake2b.Digest.newInstance(digestLength);
        try(
            FileInputStream fis = new FileInputStream(file);
        ){
            int bufferSize = file.length() < DEFAULT_BUFFER_SIZE ? (int) file.length() : DEFAULT_BUFFER_SIZE;

            byte[] byteArray = new byte[bufferSize];
            int bytesCount = 0;

            while ((bytesCount = fis.read(byteArray)) != -1) {
                digest.update(byteArray, 0, bytesCount);
            };

            byte[] hashBytes = digest.digest();

            return hashBytes;
        }
    }


      
    /*
        eturn (data[0]<<24)&0xff000000|
            (data[1]<<16)&0x00ff0000|
            (data[2]<< 8)&0x0000ff00|
            (data[3]<< 0)&0x000000ff;
     */

    public static Map<String, List<String>> parseArgs(String args[]) {

        final Map<String, List<String>> params = new HashMap<>();

        List<String> options = null;
        for (int i = 0; i < args.length; i++) {
            final String a = args[i];

            if (a.charAt(0) == '-') {
                if (a.length() < 2) {
                    System.err.println("Error at argument " + a);
                    return null;
                }

                options = new ArrayList<>();
                params.put(a.substring(1), options);
            } else if (options != null) {
                options.add(a);
            } else {
                System.err.println("Illegal parameter usage");
                return null;
            }
        }

        return params;
    }


    public static String getLatestFileString(String directoryString) {

        if (!Files.isDirectory(Paths.get(directoryString))) {
            return "";
        }

        String fileFormat = "netnotes-0.0.0.jar";
        int fileLength = fileFormat.length();

        File f = new File(directoryString);

        File[] matchingFiles = f.listFiles(new FilenameFilter() {
            public boolean accept(File dir, String name) {
                return name.startsWith("netnotes") && name.endsWith(".jar");
            }
        });

        if (matchingFiles == null) {
            return "";
        }

        int start = 7;

        String latestString = "";

        String versionA = "0.0.0";

        for (File file : matchingFiles) {

            String fileName = file.getName();

            if (fileName.equals("netnotes.jar")) {
                if (versionA.equals("0.0.0")) {
                    latestString = "netnotes.jar";
                }
            } else if (fileName.length() == fileLength) {

                int end = fileName.length() - 4;

                int i = end;
                char p = '.';

                while (i > start) {
                    char c = fileName.charAt(i);
                    if (Character.isDigit(c) || Character.compare(c, p) == 0) {
                        i--;
                    } else {
                        break;
                    }

                }

                String versionB = fileName.substring(i + 1, end);

                if (versionB.matches("[0-9]+(\\.[0-9]+)*")) {

                    Version vA = new Version(versionA);
                    Version vB = new Version(versionB);

                    if (vA.compareTo(vB) == -1) {
                        versionA = versionB;
                        latestString = fileName;
                    } else if (latestString.equals("")) {
                        latestString = fileName;
                    }
                }

            }

        }

        return latestString;
    }

    public static byte[] appendBytes(byte[] a, byte[] b){
        byte[] bytes = new byte[a.length + b.length];
       
        System.arraycopy(a, 0, bytes, 0, a.length);
        System.arraycopy(b, 0, bytes, a.length, b.length);
        return bytes;
    }



    public static Version getFileNameVersion(String fileName){
        int end = fileName.length() - 4;

        int start = fileName.indexOf("-");

        int i = end;
        char p = '.';

        while (i > start) {
            char c = fileName.charAt(i);
            if (Character.isDigit(c) || Character.compare(c, p) == 0) {
                i--;
            } else {
                break;
            }

        }

        String versionString = fileName.substring(i + 1, end);

 
        if (versionString.matches("[0-9]+(\\.[0-9]+)*")) {
            Version version = null;
            try{
                version = new Version(versionString);
            }catch(IllegalArgumentException e){

            }
            return version;
        }
        return null;
    }



    public static PriceAmount getAmountByString(String text, PriceCurrency priceCurrency) {
        if (text != null && priceCurrency != null) {
            text = text.replace(",", ".");

            char[] ch = text.toCharArray();

            for (int i = 0; i < ch.length; ++i) {
                if (Character.isDigit(ch[i])) {
                    ch[i] = Character.forDigit(Character.getNumericValue(ch[i]), 10);
                }
            }

            text = new String(ch);

            try {
                double parsedDouble = Double.parseDouble(text);
                return new PriceAmount(parsedDouble, priceCurrency);
            } catch (NumberFormatException ex) {

            }
        }
        return new PriceAmount(0, priceCurrency);
    }
    
    public static String currencySymbol(String currency){
         switch (currency) {
            case "ERG":
                return "Σ";
            case "USD":
                return "$";
            case "USDT":
                return "$";
            case "EUR":
                return "€‎";
             
            case "BTC":
                return "฿";
        }
        return currency;
    }

    public static String formatCryptoString(BigDecimal price, String target, int precision, boolean valid) {
       String formatedDecimals = String.format("%."+precision+"f", price);
        String priceTotal = valid ? formatedDecimals : "-";
    
      
     
        switch (target) {
            case "ERG":
                priceTotal = priceTotal + " ERG";
                break;
            case "USD":
                priceTotal = "$" + priceTotal;
                break;
            case "USDT":
                priceTotal = priceTotal + " USDT";
                break;
            case "EUR":
                priceTotal = "€‎" + priceTotal;
                break;
            case "BTC":
                priceTotal ="฿" + priceTotal;
                break;
        }

        return priceTotal;
    }

    public static String formatAddressString(String addressString, double width, double characterSize){
        
        int adrStrLen = addressString.length();
        
        if(adrStrLen > 5){
            int characters = ((int) ((width - (characterSize*2)) / characterSize));
            if(characters > 3 && characters < adrStrLen){
                
                int len = (int) (characters / 2);         
                String returnString = addressString.substring(0, len ) + "…" + addressString.substring(adrStrLen- len, adrStrLen) ;
            
                return returnString;
            }else{
                return addressString;
            }
        }else{
            return addressString;
        }
    }
     
    public static String parseMsgForJsonId(String msg){
        if(msg != null){
            JsonParser jsonParser = new JsonParser();

            JsonElement jsonElement = jsonParser.parse(msg);

            if(jsonElement != null && jsonElement.isJsonObject()){
                JsonObject json = jsonElement.getAsJsonObject();
                JsonElement idElement = json.get("id");
                if(idElement != null && !idElement.isJsonNull()){
                    return idElement.getAsString();
                }
            }
        }

        return null;
    }

    public static String formatCryptoString(double price, String target, int precision, boolean valid) {
        String formatedDecimals = String.format("%."+precision+"f", price);
        String priceTotal = valid ? formatedDecimals : "-";
    
        switch (target) {

            case "USD":
                priceTotal = "$" + priceTotal;
                break;
            case "EUR":
                priceTotal = "€‎" + priceTotal;
                break;
            default:
                priceTotal = priceTotal + " " + target;
        }

        return priceTotal;
    }


    public static String formatCryptoString(double price, String target, boolean valid) {
        String formatedDecimals = String.format("%.2f", price);
        String priceTotal = valid ? formatedDecimals : "-.--";

        

        switch (target) {
            case "ERG":
                priceTotal = (valid ? String.format("%.3f", price) : "-.--") + " ERG";
                break;
            case "USD":
                priceTotal = "$" + priceTotal;
                break;
            case "USDT":
                priceTotal = priceTotal + " USDT";
                break;
            case "EUR":
                priceTotal = "€‎" + priceTotal;
                break;
            case "BTC":
                priceTotal ="฿" + (valid ? String.format("%.8f", price) : "-.--");
                break;
        }

        return priceTotal;
    }
    public static String truncateText(String text,FontMetrics metrics, double width) {
       
        String truncatedString = text.substring(0, 5) + "..";
        if (text.length() > 3) {
            int i = text.length() - 3;
            truncatedString = text.substring(0, i) + "..";

            while (metrics.stringWidth(truncatedString) > width && i > 1) {
                i = i - 1;
                truncatedString = text.substring(0, i) + "..";

            }
        }
        return truncatedString;
    }
    public static double computeTextWidth(Font font, String text, double wrappingWidth) {
    
        m_helper = new Text();
        m_helper.setFont(font);
        m_helper.setText(text);
        // Note that the wrapping width needs to be set to zero before
        // getting the text's real preferred width.
        m_helper.setWrappingWidth(0);
        m_helper.setLineSpacing(0);
        double w = Math.min(m_helper.prefWidth(-1), wrappingWidth);
        m_helper.setWrappingWidth((int)Math.ceil(w));
        double textWidth = Math.ceil(m_helper.getLayoutBounds().getWidth());
        m_helper = null;
        return textWidth;
    }

    public static double computeTextWidth(Font font, String text) {
    
        m_helper = new Text();
        m_helper.setFont(font);
        m_helper.setText(text);
        double w =  Math.ceil(m_helper.getLayoutBounds().getWidth());
        m_helper = null;
        return w;
    }
    private static Text m_helper;
    private static BufferedImage m_tmpImg = null;
    private static Graphics2D m_tmpG2d = null;
    private static java.awt.Font m_tmpFont = null;
    private static FontMetrics m_tmpFm = null;

    public static int getStringWidth(String str){
        return getStringWidth(str, 14);
    }

    public static int getCharacterSize(int fontSize){
        m_tmpImg = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        m_tmpG2d = m_tmpImg.createGraphics();
        m_tmpFont = new java.awt.Font("OCR A Extended", java.awt.Font.PLAIN, fontSize);
        m_tmpG2d.setFont(m_tmpFont);
        m_tmpFm = m_tmpG2d.getFontMetrics();
        
        int width = m_tmpFm.charWidth(' ');

        m_tmpFm = null;
        m_tmpG2d.dispose();
        m_tmpG2d = null;
        m_tmpFont = null;

        m_tmpImg = null;


        return width;
    }

    public static int getStringWidth(String str, int fontSize){
        return getStringWidth(str, fontSize, "OCR A Extended", java.awt.Font.PLAIN);
    }

    public static int getStringWidth(String str, int fontSize, String fontName, int fontStyle){
        m_tmpImg = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        m_tmpG2d = m_tmpImg.createGraphics();
        m_tmpFont = new java.awt.Font(fontName, fontStyle, fontSize);
        m_tmpG2d.setFont(m_tmpFont);
        m_tmpFm = m_tmpG2d.getFontMetrics();
        
        int width = m_tmpFm.stringWidth(str);

        m_tmpFm = null;
        m_tmpG2d.dispose();
        m_tmpG2d = null;
        m_tmpFont = null;

        m_tmpImg = null;


        return width;
    }



    public static String formatCryptoString(PriceAmount priceAmount, boolean valid) {
         int precision = priceAmount.getCurrency().getFractionalPrecision();
        DecimalFormat df = new DecimalFormat("0");
        df.setMaximumFractionDigits(precision);

        String formatedDecimals = df.format(priceAmount.getDoubleAmount());
        String priceTotal = valid ? formatedDecimals : "-.--";

        

        switch (priceAmount.getCurrency().getSymbol()) {
            case "ERG":
                priceTotal = "Σ"+ priceTotal;
                break;
            case "USD":
                priceTotal = "$" + priceTotal;
                break;
            case "EUR":
                priceTotal = "€‎" + priceTotal;
                break;
            case "BTC":
                priceTotal ="฿" + priceTotal;
                break;
            default:
                priceTotal = priceTotal + " " + priceAmount.getCurrency().getSymbol();
        }

        return priceTotal;
    }


    public static long getNowEpochMillis(LocalDateTime now) {
        Instant instant = now.atZone(ZoneId.systemDefault()).toInstant();
        return instant.toEpochMilli();
    }

    public static String formatDateTimeString(LocalDateTime localDateTime) {

        DateTimeFormatter formater = DateTimeFormatter.ofPattern("MM-dd-yyyy hh:mm:ss.SSS a");

        return formater.format(localDateTime);
    }

    public static String formatTimeString(LocalDateTime localDateTime) {

        DateTimeFormatter formater = DateTimeFormatter.ofPattern("hh:mm:ss a");

        return formater.format(localDateTime);
    }

    public static LocalDateTime milliToLocalTime(long timestamp) {
        Instant timeInstant = Instant.ofEpochMilli(timestamp);

        return LocalDateTime.ofInstant(timeInstant, ZoneId.systemDefault());
    }

    public static String readHexDecodeString(File file) {
        String fileHexString = null;

        try {
            fileHexString = file != null && file.isFile() ? Files.readString(file.toPath()) : null;
        } catch (IOException e) {

        }
        byte[] bytes = null;

        try {
            bytes = fileHexString != null ? Hex.decodeHex(fileHexString) : null;
        } catch (DecoderException e) {

        }

        return bytes != null ? new String(bytes, StandardCharsets.UTF_8) : null;
    }
    public static int getDifference(int num1, int num2 ){
        return Math.abs(Math.max(num1, num2) - Math.min(num1, num2));
    }



    public static Future<?> checkDrive(File file, ExecutorService execService, EventHandler<WorkerStateEvent> onSucceeded, EventHandler<WorkerStateEvent> onFailed) {

        Task<Object> task = new Task<Object>() {
            @Override
            public Object call() throws Exception {
                
                if(file == null){
                    throw new NullPointerException(NoteConstants.ERROR_EXISTS);
                }
                String path = file.getCanonicalPath();
                return findPathPrefixInRoots(path) ? path : null;
             
            }
        };

        task.setOnFailed(onFailed);

        task.setOnSucceeded(onSucceeded);

        return execService.submit(task);
    }

    public static Future<?> returnException(String errorString, ExecutorService execService, EventHandler<WorkerStateEvent> onFailed) {

        Task<Object> task = new Task<Object>() {
            @Override
            public Object call() throws Exception {
                throw new Exception(errorString);
            }
        };

        task.setOnFailed(onFailed);

        return execService.submit(task);

    }

    public static Future<?> returnException(Throwable throwable, ExecutorService execService, EventHandler<WorkerStateEvent> onFailed){
        if(throwable != null && throwable instanceof Exception){
            return Utils.returnException((Exception) throwable, execService, onFailed);
        }else{
            return Utils.returnException(throwable != null ? throwable.getMessage() : NoteConstants.ERROR_IO, execService, onFailed);
        }
    }

    public static Future<?> returnException(Exception exception, ExecutorService execService, EventHandler<WorkerStateEvent> onFailed) {

        Task<Object> task = new Task<Object>() {
            @Override
            public Object call() throws Exception {
                throw exception;
            }
        };

        task.setOnFailed(onFailed);

        return execService.submit(task);

    }

    public static void logJson(String heading, JsonObject json){

        try {
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            Files.writeString(AppConstants.LOG_FILE.toPath(), "**"+heading+"**" + gson.toJson(json) +"\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {

        }
        
    }
    public static void logJsonArray(String heading, JsonArray jsonArray){

        try {
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            Files.writeString(AppConstants.LOG_FILE.toPath(), "**"+heading+"**" + gson.toJson(jsonArray) +"\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {

        }
        
    }

    public static int getIntFromField(TextField field){
        return field == null ? 0 : Utils.isTextZero(field.getText()) ? 0 :  Integer.parseInt(Utils.formatStringToNumber(field.getText(), 0));
    }

    public static BigDecimal getBigDecimalFromField(TextField field, int decimals){
        return field == null ? BigDecimal.ZERO : Utils.isTextZero(field.getText()) ? BigDecimal.ZERO :  new BigDecimal(Utils.formatStringToNumber(field.getText(), 0));
    }

    public static String formatStringLineLength(String str, int len){
        return str.replaceAll("(.{"+len+"})", "$1\n");
    }

    public static Binding<String> createFormFieldIdBinding(TextField textField){
        return Bindings.createObjectBinding(()-> textField != null ? (textField.textProperty().get().length() > 0 ? null : "formField") : null, textField.textProperty());
    }

    public static ChangeListener<String> createFieldEnterBtnAddListener(TextField textField, HBox textFieldBox, Button enterBtn){
        ChangeListener<String> changeListener = (obs, oldval, newval) ->{
            if(textField != null && textFieldBox != null && enterBtn != null){
                if(newval.length() > 0){
                    if(!textFieldBox.getChildren().contains(enterBtn)){
                        textFieldBox.getChildren().add(1, enterBtn);
                    }
                }else{
                    if(textFieldBox.getChildren().contains(enterBtn)){
                        textFieldBox.getChildren().remove(enterBtn);
                    }
                }
            }
        };
        return changeListener;
    }


    public static Future<?> returnObject(Object object, ExecutorService execService, EventHandler<WorkerStateEvent> onSucceeded, EventHandler<WorkerStateEvent> onFailed) {

        Task<Object> task = new Task<Object>() {
            @Override
            public Object call() {

                return object;
            }
        };

        task.setOnFailed(onFailed);

        task.setOnSucceeded(onSucceeded);

        return execService.submit(task);

    }

    

    public static Future<?> readEncryptedFile( File file,AtomicBoolean isAquired,Semaphore dataSemaphore, SecretKey secretKey, PipedOutputStream pipedOutput, ExecutorService execService, EventHandler<WorkerStateEvent> onSucceeded, EventHandler<WorkerStateEvent> onFailed){

        Task<Object> task = new Task<Object>() {
            @Override
            public Object call() throws InterruptedException, InvalidKeyException, NoSuchPaddingException, NoSuchAlgorithmException, InvalidAlgorithmParameterException, BadPaddingException, IllegalBlockSizeException, IOException{
                try (
                        FileInputStream fileInputStream = new FileInputStream(file);
                ) {
                    dataSemaphore.acquire();
                    isAquired.set(true);
                    if(file.length() < 12){
                        return false;
                    }
                    byte[] iV = new byte[12];
                    fileInputStream.read(iV);

                    Cipher decryptCipher = Cipher.getInstance("AES/GCM/NoPadding");
                    GCMParameterSpec parameterSpec = new GCMParameterSpec(128, iV);
                    decryptCipher.init(Cipher.DECRYPT_MODE, secretKey, parameterSpec);

                    int length = 0;
                    byte[] readBuffer = new byte[Utils.DEFAULT_BUFFER_SIZE];
                
                    while ((length = fileInputStream.read(readBuffer)) != -1) {
                        pipedOutput.write(decryptCipher.update(readBuffer, 0, length));
                        pipedOutput.flush();
                    }
                }
                

                return true;
            }
        };
        task.setOnFailed(onFailed);

        task.setOnSucceeded(onSucceeded);

        return execService.submit(task);

    }
    
    public static Future<?> writeEncryptedFile(File file, SecretKey secretKey, PipedInputStream pipedWriterInput,  ExecutorService execService, EventHandler<WorkerStateEvent> onSucceeded, EventHandler<WorkerStateEvent> onFailed){

        Task<Object> task = new Task<Object>() {
            @Override
            public Object call() throws InterruptedException, InvalidKeyException, NoSuchPaddingException, NoSuchAlgorithmException, InvalidAlgorithmParameterException, BadPaddingException, IllegalBlockSizeException, IOException{
                int length = 0;  
                byte[] readBuffer = new byte[Utils.DEFAULT_BUFFER_SIZE];
                try(
                    FileOutputStream fileOutputStream = new FileOutputStream(file);
                ){
                    
                    byte[] outIV = Utils.getIV();
                    Cipher encryptCipher = Cipher.getInstance("AES/GCM/NoPadding");
                    GCMParameterSpec outputParameterSpec = new GCMParameterSpec(128, outIV);
                    encryptCipher.init(Cipher.ENCRYPT_MODE, secretKey, outputParameterSpec);

                    fileOutputStream.write(outIV);

                    while((length = pipedWriterInput.read(readBuffer)) != -1){
                        fileOutputStream.write(encryptCipher.update(readBuffer, 0, length));
                    }
                    byte[] bytes = encryptCipher.doFinal();
                    if(bytes == null){
                        fileOutputStream.write(bytes);        
                    }
                }
                return true;
            }
        };

        task.setOnFailed(onFailed);

        task.setOnSucceeded(onSucceeded);

        return execService.submit(task);
    }

    public static Future<?> delayObject(Object object, long delayMillis, ExecutorService execService, EventHandler<WorkerStateEvent> onSucceeded) {

        Task<Object> task = new Task<Object>() {
            @Override
            public Object call() throws InterruptedException {
                Thread.sleep(delayMillis);
                return object;
            }
        };

        task.setOnSucceeded(onSucceeded);

        return execService.submit(task);

    }

    public static boolean isCollection(Object obj) {
        return obj.getClass().isArray() || obj instanceof Collection;
      }

    public static List<?> convertObjectToList(Object obj) {
        List<?> list = new ArrayList<>();
        if (obj.getClass().isArray()) {
            list = Arrays.asList((Object[]) obj);
        } else if (obj instanceof Collection) {
            list = new ArrayList<>((Collection<?>) obj);
        }
        return list;
    }

    public static Future<?> returnObject(Object object, ExecutorService execService, EventHandler<WorkerStateEvent> onSucceeded) {

        Task<Object> task = new Task<Object>() {
            @Override
            public Object call() {

                return object;
            }
        };

        task.setOnSucceeded(onSucceeded);

        return execService.submit(task);

    }

    public static boolean checkErgoId(String tokenId){
        if(tokenId.length() == 64){
            String b58Test = tokenId.replaceAll("[^A-HJ-NP-Za-km-z0-9]", "");
                
            return b58Test.equals(tokenId);
        }
        return false;
    }


    public static int getJsonElementType(JsonElement jsonElement){
        return jsonElement.isJsonNull() ? -1 : jsonElement.isJsonObject() ? 1 : jsonElement.isJsonArray() ? 2 : jsonElement.isJsonPrimitive() ? 3 : 0;
    }

    public static int arrayCopy(Object src, int srcOffset, Object dst, int dstOffset, int length){
        System.arraycopy(src, srcOffset, dst, dstOffset, length);
        return dstOffset + length;
    }

    public static byte[] readInputStreamAsBytes(InputStream inputStream) throws IOException{
   
        try(
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ){
            byte[] buffer = new byte[DEFAULT_BUFFER_SIZE];
            int length = 0;

            while((length = inputStream.read(buffer)) != -1){
                outputStream.write(buffer, 0, length);
            }

            return outputStream.toByteArray();
        }
    }

    public static String getUrlOutputStringSync(String urlString) throws IOException{
        URL url = new URL(urlString);
        URLConnection con = url.openConnection();
        con.setRequestProperty("User-Agent", USER_AGENT);

        try(
            InputStream inputStream = con.getInputStream();
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ){

            byte[] buffer = new byte[2048];

            int length;

            while ((length = inputStream.read(buffer)) != -1) {

                outputStream.write(buffer, 0, length);
              
            }
     

            return outputStream.toString();
   
        }
    }
    
    public static Future<?> getUrlFile(String urlString, Image icon, String headingString, File file, ExecutorService execService, EventHandler<WorkerStateEvent> onSucceeded, EventHandler<WorkerStateEvent> onFailed) {
        Stage progressStage = new Stage();
        progressStage.setResizable(false);
        progressStage.initStyle(StageStyle.UNDECORATED);
        Button closeBtn = new Button();
        ProgressBar progressBar = new ProgressBar();
        SimpleStringProperty contextString = new SimpleStringProperty("");

        contextString.bind(Bindings.createObjectBinding(()->{
            double progress = progressBar.progressProperty().get();
            BigDecimal decimalProgress = BigDecimal.valueOf(progress).multiply(BigDecimal.valueOf(100)).setScale(1, RoundingMode.HALF_UP);
            return progress == 0 ? "Starting..." : decimalProgress + "%";
        }, progressBar.progressProperty()));

        progressStage.setScene(Stages.getFileProgressScene(icon, headingString, contextString,file.getName(),progressBar, progressStage, closeBtn));
        progressStage.show();

        Task<Object> task = new Task<Object>() {
            @Override
            public Object call() throws IOException {
           
                URL url = new URL(urlString);
                URLConnection con = url.openConnection();
                con.setRequestProperty("User-Agent", USER_AGENT);
        
                try(
                    InputStream inputStream = con.getInputStream();
                    FileOutputStream outputStream = new FileOutputStream(file);
                ){
        
                    byte[] buffer = new byte[2024];
                    int length = 0;
                    long downloaded = 0;
                    long contentLength = con.getContentLengthLong();
                    
                    updateProgress(downloaded, contentLength);
                    
                    while ((length = inputStream.read(buffer)) != -1) {
                        downloaded += length;
                        outputStream.write(buffer, 0, length);

                        updateProgress(downloaded, contentLength == -1 ? con.getContentLengthLong() : contentLength );
                    }
             
        
                    return true;
           
                }

            }

        };
        progressBar.progressProperty().bind( task.progressProperty());
      
        task.setOnFailed((error)->{
            closeBtn.setOnAction(null);
            progressBar.progressProperty().unbind();
            contextString.unbind();
            progressStage.close();
            Throwable throwable = error.getSource().getException();
            Exception ex = throwable != null && throwable instanceof Exception ? (Exception) throwable : null;
            if(ex != null){
                if(ex instanceof InterruptedException){
                    Utils.returnException(new InterruptedException("Canceled"), execService, onFailed);
                }else{
                    Utils.returnException(ex, execService, onFailed);
                }
            }else{
                Utils.returnException("Download failed", execService, onFailed);
            }
        });

        task.setOnSucceeded((onFinished)->{
            closeBtn.setOnAction(null);
            progressBar.progressProperty().unbind();
            contextString.unbind();
            progressStage.close();
            Utils.returnObject(onFinished.getSource().getValue(), execService, onSucceeded);
        });

        Future<?> future = execService.submit(task);

        closeBtn.setOnAction(e->{
            future.cancel(true);
            
        });

        return future;

    }


    public static Future<?> getUrlJson(String urlString, ExecutorService execService, EventHandler<WorkerStateEvent> onSucceeded, EventHandler<WorkerStateEvent> onFailed) {
  
        Task<JsonObject> task = new Task<JsonObject>() {
            @Override
            public JsonObject call() throws IOException {
           
                String outputString = getUrlOutputStringSync(urlString);
    
                JsonElement jsonElement = outputString != null ? new JsonParser().parse(outputString) : null;

                return jsonElement != null && jsonElement.isJsonObject() ? jsonElement.getAsJsonObject() : null;

            }

        };

      
        task.setOnFailed(onFailed);

        task.setOnSucceeded(onSucceeded);

        return execService.submit(task);

    }
    


    public static String formatStringToNumber(String number){
        number = number.replaceAll("[^0-9.]", "");
        int index = number.indexOf(".");
        String leftSide = index != -1 ? number.substring(0, index + 1) : number;
        leftSide = leftSide.equals(".") ? "0." : leftSide;
        String rightSide = index != -1 && index != number.length() - 1 ?  number.substring(index + 1) : "";
        rightSide = rightSide.length() > 0 ? rightSide.replaceAll("[^0-9]", "") : "";

        number = leftSide + rightSide;
        return number;
    
    }

    public static String formatStringToNumber(String number, int decimals){
        number = number.replaceAll("[^0-9.]", "");
        int index = number.indexOf(".");
        String leftSide = index != -1 ? number.substring(0, index + 1) : number;
        leftSide = leftSide.equals(".") ? "0." : leftSide;
        String rightSide = index != -1 && index != number.length() - 1 ?  number.substring(index + 1) : "";
        rightSide = rightSide.length() > 0 ? rightSide.replaceAll("[^0-9]", "") : "";
        rightSide = rightSide.length() > decimals ? rightSide.substring(0, decimals) : rightSide;

        number = leftSide + rightSide;
        return number;

    }

    public static Future<?> getUrlJsonArray(String urlString, ExecutorService execService, EventHandler<WorkerStateEvent> onSucceeded, EventHandler<WorkerStateEvent> onFailed) {

        Task<JsonArray> task = new Task<JsonArray>() {
            @Override
            public JsonArray call() throws JsonParseException, MalformedURLException, IOException {
              
                String outputString = getUrlOutputStringSync(urlString);

                JsonElement jsonElement = outputString != null ? new JsonParser().parse(outputString) : null;

                return jsonElement != null && jsonElement.isJsonArray() ? jsonElement.getAsJsonArray() : null;

            }

        };

        task.setOnFailed(onFailed);

        task.setOnSucceeded(onSucceeded);

        return execService.submit(task);
    }

    public static JsonObject getJsonFromUrlSync(String urlString) throws IOException{
                                             
        String outputString = getUrlOutputStringSync(urlString);

        JsonElement jsonElement = outputString != null ? new JsonParser().parse(outputString) : null;

        JsonObject jsonObject = jsonElement != null && jsonElement.isJsonObject() ? jsonElement.getAsJsonObject() : null;

        return jsonObject == null ? null : jsonObject;
           

   }

    public static String formatedBytes(long bytes, int decimals) {

        if (bytes == 0) {
            return "0 Bytes";
        }

        double k = 1024;
        int dm = decimals < 0 ? 0 : decimals;

        String[] sizes = new String[]{"Bytes", "KB", "MB", "GB", "TB", "PB", "EB", "ZB", "YB"};

        int i = (int) Math.floor(Math.log((double) bytes) / Math.log(k));

        return String.format("%." + dm + "f", bytes / Math.pow(k, i)) + " " + sizes[i];

    }

    public static BufferedImage greyScaleImage(BufferedImage img) {

        int height = img.getHeight();
        int width = img.getWidth();

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int p = img.getRGB(x, y);

                int a = (p >> 24) & 0xff;
                int r = (p >> 16) & 0xff;
                int g = (p >> 8) & 0xff;
                int b = p & 0xff;

                //calculate average
                int avg = (r + g + b) / 3;

                //replace RGB value with avg
                p = (a << 24) | (avg << 16) | (avg << 8) | avg;

                img.setRGB(x, y, p);
            }
        }

        return img;
    }

    public static void showTip(String msg, Region region, Tooltip tooltip, PauseTransition pt){
    
        Point2D p = region.localToScene(0.0, 0.0);
        Scene scene = region.getScene();
        Window window = scene.getWindow();

        tooltip.setText(msg != null ? msg : "Error");
        tooltip.show(region,
                p.getX() + scene.getX()
                        + window.getX()
                        + region.getLayoutBounds().getWidth(),
                (p.getY() + scene.getY()
                        + window.getY()) - 30);
        pt.setOnFinished(e->tooltip.hide());
        pt.playFromStart();
    }


    
    public static Image checkAndLoadImage(String imageString, HashData hashData) {
        if(imageString != null ){
            
            if(imageString.startsWith(AppConstants.ASSETS_DIRECTORY + "/")){
                return new Image(imageString);
            }
            File checkFile = new File(imageString);

            try {
                HashData checkFileHashData = new HashData(checkFile);
                /*try {
                    Files.writeString(logFile.toPath(), "\nhashString: " +checkFileHashData.getHashStringHex()+ " hashDataString: " + hashData.getHashStringHex(), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                } catch (IOException e) {

                }*/
                if (checkFileHashData.getHashStringHex().equals(hashData.getHashStringHex())) {
                    
                    return getImageByFile(checkFile);
                }
            } catch (Exception e) {
                try {
                    Files.writeString(new File("netnotes-log.txt").toPath(), "\nCheck and load image: " + imageString + " *" + e , StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                } catch (IOException e2) {

                }
            }
        }

  
        return Stages.unknownImg;
        

    }

    public static Image getImageByFile(File file) throws IOException{
        if (file != null && file.isFile()) {
           
          
            String contentType  = Files.probeContentType(file.toPath());
            contentType = contentType.split("/")[0];
            if (contentType != null && contentType.equals("image")) {
                
                FileInputStream iStream = new FileInputStream(file);
                Image img = new Image(iStream);
                iStream.close();
                return img;
            }
           
        }
         return null;
    }

    public static String removeInvalidChars(String str)
    {
        return str.replaceAll("[^a-zA-Z0-9\\.\\-]", "");
    }


    public static TimeUnit stringToTimeUnit(String str) {
        switch (str.toLowerCase()) {
            case "μs":
            case "microsecond":
            case "microseconds":
                return TimeUnit.MICROSECONDS;
            case "ms":
            case "millisecond":
            case "milliseconds":
                return TimeUnit.MILLISECONDS;
            case "s":
            case "sec":
            case "second":
            case "seconds":
                return TimeUnit.SECONDS;
            case "min":
            case "minute":
            case "minutes":
                return TimeUnit.MINUTES;
            case "h":
            case "hour":
            case "hours":
                return TimeUnit.HOURS;
            case "day":
            case "days":
                return TimeUnit.DAYS;
            default:
                return null;
        }
    }

    public static String timeUnitToString(TimeUnit unit) {
        switch (unit) {
            case MICROSECONDS:
                return "μs";
            case MILLISECONDS:
                return "ms";
            case SECONDS:
                return "s";
            case MINUTES:
                return "m";
            case HOURS:
                return "h";
            case DAYS:
                return "days";
            default:
                return "~";
        }
    }


    public static boolean checkJar(File jarFile) {
        boolean isJar = false;
        if (jarFile != null && jarFile.isFile()) {
            try {
                ZipFile zip = new ZipFile(jarFile);
                isJar = true;
                zip.close();
            } catch (Exception zipException) {

            }
        }
        return isJar;
    }

    public static BigInteger[] addFractions(BigInteger num1, BigInteger denom1, BigInteger num2, BigInteger denom2){
        int result = denom1.compareTo(denom2);
        if(result == 0){
            return new BigInteger[]{num1.add(num2), denom1};
        }else if(result == 1){
            BigInteger divisor = denom1.divide(denom2);
            return new BigInteger[]{(num1.add(num2.multiply(divisor))), denom1};
        }else{
            BigInteger divisor = denom2.divide(denom1);
            return new BigInteger[]{(num2.add(num1.multiply(divisor))), denom2};
        }
    }

    public static BigInteger[] subtractFractions(BigInteger num1, BigInteger denom1, BigInteger num2, BigInteger denom2){
        int result = denom1.compareTo(denom2);
        if(result == 0){
            return new BigInteger[]{num1.subtract(num2), denom1};
        }else if(result == 1){
            BigInteger divisor = denom1.divide(denom2);
            return new BigInteger[]{(num1.subtract(num2.multiply(divisor))), denom1};
        }else{
            BigInteger divisor = denom2.divide(denom1);
            return new BigInteger[]{(num1.multiply(divisor).subtract(num2)), denom2};
        }
    }

    public static long calculateErgFromPerToken(long tokens, BigInteger num, BigInteger denom){
        BigInteger x = BigInteger.valueOf(tokens).multiply(num);
        if(denom.compareTo(BigInteger.valueOf(1)) == 1){
            return addFractions(x, denom, denom.divide(BigInteger.valueOf(10)), denom)[0].divide(denom).longValue();
        }else{
            return x.longValue();
        }
    }

    public static long calculateTokensFromPerToken(long nanoErg, BigInteger num, BigInteger denom){

        BigInteger x =  BigInteger.valueOf(nanoErg).multiply(denom);
        BigInteger y = x.divide(num);
        String str = "" +
        "nanoErg:" + nanoErg + "\n" +
        "num:    " + num + "\n" + 
        "denom:  " + denom + "\n" +
        "x:      " + x + "\n" + 
        "y:      " + y + "\n";
        try {
            Files.writeString(AppConstants.LOG_FILE.toPath(),str, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {

        } 
        
        return  y.longValue();
       
    }

    public static final BigInteger i64Max = BigInteger.valueOf(Long.MAX_VALUE);
    public static final BigInteger i128Max = BigInteger.TWO.pow(127).subtract(BigInteger.ONE);

    public static BigInteger[] decimalToBigFractional(BigDecimal decimal){

        if(decimal == null || decimal.equals(BigDecimal.ZERO)){
            return new BigInteger[]{BigInteger.ZERO, BigInteger.ONE};
        }
        BigInteger leftSide = decimal.toBigInteger();
        int scale = decimal.scale();
        if(scale == 0){
            BigInteger bigInt = decimal.toBigInteger();
            return new BigInteger[]{bigInt.bitLength() > 128 ? BigInteger.ZERO : bigInt, BigInteger.ONE};
        }else if(scale < 0){
            return new BigInteger[]{ BigInteger.ZERO, BigInteger.ONE};
        }
        
        BigInteger rightSide = decimal.remainder(BigDecimal.ONE).movePointRight(scale).abs().toBigInteger();

        //String rightSide = number.substring(number.indexOf(".") + 1);
       // int numDecimals = rightSide.length();

        //  new BigInteger(rightSide)
        BigInteger denominator = BigInteger.valueOf(10).pow(scale);
        BigInteger numerator =  leftSide.multiply(denominator).add(rightSide);
        return new BigInteger[]{numerator, denominator};
    }

    public static BigInteger[] decimalToFractional(BigDecimal decimal){
        if(decimal == null || decimal.equals(BigDecimal.ZERO)){
            return new BigInteger[]{BigInteger.ZERO, BigInteger.ONE};
        }
        decimal = decimal.stripTrailingZeros();
        int scale = decimal.scale();
        if(scale == 0){
            BigInteger bigInt = decimal.toBigInteger();
            return new BigInteger[]{bigInt.bitLength() > 128 ? BigInteger.ZERO : bigInt, BigInteger.ONE};
        }
        BigInteger fees[] = decimalToBigFractional(decimal);
        while (fees[0].compareTo(i128Max) >= 1 || fees[1].compareTo(i128Max) >= 1) {
            decimal = decimal.setScale(decimal.scale() - 1, RoundingMode.FLOOR).stripTrailingZeros();
            fees = decimalToBigFractional(decimal);
        }
        return new BigInteger[] {fees[0], fees[1]};
    
    }

    public static Future<?> checkAddress(String addressString, ExecutorService execService, EventHandler<WorkerStateEvent> onSucceeded, EventHandler<WorkerStateEvent> onFailed) {
        Task<byte[]> task = new Task<byte[]>() {
            @Override
            public byte[] call() throws Exception {

                byte[] addressBytes = null;

                Try<byte[]> bytes = scorex.util.encode.Base58.decode(addressString);

                addressBytes = bytes.get();

                byte[] checksumBytes = new byte[]{addressBytes[addressBytes.length - 4], addressBytes[addressBytes.length - 3], addressBytes[addressBytes.length - 2], addressBytes[addressBytes.length - 1]};

                byte[] testBytes = new byte[addressBytes.length - 4];

                for (int i = 0; i < addressBytes.length - 4; i++) {
                    testBytes[i] = addressBytes[i];
                }

                byte[] hashBytes = ByteHashing.digestBytesToBytes(testBytes);

                if (!(checksumBytes[0] == hashBytes[0]
                        && checksumBytes[1] == hashBytes[1]
                        && checksumBytes[2] == hashBytes[2]
                        && checksumBytes[3] == hashBytes[3])) {
                    return null;
                }

                return addressBytes;
            }
        };

        task.setOnSucceeded(onSucceeded);

        task.setOnFailed(onFailed);

        return execService.submit(task);
    }

    public static String getRandomString(int length){
        char[] chars = new char[length];
        fillCharArray(chars);
        return new String(chars);
    
    }

    public static void fillCharArray(char[] charArray) {
        fillCharArray(charArray, getPrintableCharArray());
    }

    public static void fillCharArray(char[] charArray, char[] chars){
        if(charArray != null && charArray.length > 0 && chars != null && chars.length > 0){

            for(int i = 0; i < charArray.length ; i++){
                charArray[i] = chars[getRandomInt(0, chars.length)];
            }
        }
    }



    public static int getRandomInt(int min, int max) {
        SecureRandom secureRandom = new SecureRandom();
        return secureRandom.nextInt(min, max);
    }

 

    public static void saveJson(SecretKey appKey, JsonObject json, File dataFile) throws IOException, NoSuchAlgorithmException, InvalidKeyException, NoSuchPaddingException, InvalidAlgorithmParameterException, BadPaddingException, IllegalBlockSizeException {


        Utils.writeEncryptedString(appKey, dataFile, json.toString());

    }

    public static void saveEncryptedData(SecretKey appKey, byte[] data, File dataFile) throws IOException, NoSuchAlgorithmException, InvalidKeyException, NoSuchPaddingException, InvalidAlgorithmParameterException, BadPaddingException, IllegalBlockSizeException, ShortBufferException {

        byte[] iV = getIV();

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        GCMParameterSpec parameterSpec = new GCMParameterSpec(128, iV);
        cipher.init(Cipher.ENCRYPT_MODE, appKey, parameterSpec);
        

        if (dataFile.isFile()) {
            Files.delete(dataFile.toPath());
        }

        try(
            ByteArrayInputStream byteStream = new ByteArrayInputStream(data);
            FileOutputStream fileStream = new FileOutputStream(dataFile);
        ){
            fileStream.write(iV);    
            int bufferSize = data.length < DEFAULT_BUFFER_SIZE ? (int) data.length : DEFAULT_BUFFER_SIZE;

            byte[] byteArray = new byte[bufferSize];
            byte[] output;

            int length = 0;
            while((length = byteStream.read(byteArray)) != -1){

                output = cipher.update(byteArray, 0, length);
                if(output != null){
                    fileStream.write(output);
                }
            }

            output = cipher.doFinal();
            if(output != null){
                fileStream.write(output);
            }



        }
        
    }





    public static Future<?> saveJsonArray(SecretKey appKey, JsonArray jsonArray, File dataFile, ExecutorService execService, EventHandler<WorkerStateEvent> onSucceeded, EventHandler<WorkerStateEvent> onFailed) {
        if(appKey != null && jsonArray != null && dataFile != null){
       
            Task<Object> task = new Task<Object>() {
                @Override
                public Object call() throws IOException, NoSuchAlgorithmException, InvalidKeyException, NoSuchPaddingException, InvalidAlgorithmParameterException, BadPaddingException, IllegalBlockSizeException  {
                    
                    String jsonString = jsonArray.toString();
                    writeEncryptedString(appKey, dataFile, jsonString);
                    
                    return true;
                }
            };
    
            task.setOnFailed(onFailed);
    
            task.setOnSucceeded(onSucceeded);
    
            return execService.submit(task);
        }
        return null;
    }

    public static Future<?> decryptBytesFromFile(SecretKey appKey, File file, ExecutorService execService,  EventHandler<WorkerStateEvent> onSucceeded, EventHandler<WorkerStateEvent> onFailed) throws IOException, InvalidKeyException, NoSuchPaddingException, NoSuchAlgorithmException, InvalidAlgorithmParameterException, BadPaddingException, IllegalBlockSizeException{
        Task<byte[]> task = new Task<byte[]>() {
            @Override
            public byte[] call() throws InvalidKeyException, NoSuchPaddingException, NoSuchAlgorithmException, InvalidAlgorithmParameterException, BadPaddingException, IllegalBlockSizeException, IOException{
                
                return decryptFileToBytes(appKey, file);

            }
        };
        

        task.setOnFailed(onFailed);

        task.setOnSucceeded(onSucceeded);

        return execService.submit(task);
    }

    public static boolean encryptBytesToFile(SecretKey appKey, byte[] bytes, File outputFile)throws NoSuchAlgorithmException, MalformedURLException, IOException, InvalidKeyException, InvalidAlgorithmParameterException, NoSuchPaddingException, IllegalBlockSizeException, BadPaddingException{
        long contentLength = -1;

                 
        SecureRandom secureRandom = new SecureRandom();
        byte[] iV = new byte[12];
        secureRandom.nextBytes(iV);
        
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        GCMParameterSpec parameterSpec = new GCMParameterSpec(128, iV);

        cipher.init(Cipher.ENCRYPT_MODE, appKey, parameterSpec);

       

        try(
            FileOutputStream outputStream = new FileOutputStream(outputFile);
            ByteArrayInputStream inputStream = new ByteArrayInputStream(bytes);
        ){
            long bytesSize = bytes.length;
            int bufferSize = bytesSize < 1024 ? (int) bytesSize :1024;
            
            byte[] buffer = new byte[bufferSize];
            byte[] output;
            int length;
            long copied = 0;
   
            outputStream.write(iV);

            while ((length = inputStream.read(buffer)) != -1) {

               

                output = cipher.update(buffer, 0, length);
                if(output != null){
                    outputStream.write(output);
                }
                copied += (long) length;
                
           
            }

            output = cipher.doFinal();

            if(output != null){
                outputStream.write(output);
            }

       
            if( contentLength == copied){
                return true;
            }

        }

      
        return false;
    }

    public boolean compareStreams(Stream<Object> stream1, Stream<Object> stream2){
        return stream1.count() == stream2.count() && stream1
               .allMatch(element -> stream2.anyMatch(element2 -> element2.equals(element)));
    }



    public static byte[] getRandomBytes(int size){
        byte[] randomBytes = new byte[size];
        SecureRandom secureRandom = new SecureRandom();
        secureRandom.nextBytes(randomBytes);
        return randomBytes;
    }

    public static Future<?> encryptBytesToFile(SecretKey appKey, byte[] bytes, File outputFile, ExecutorService execService, EventHandler<WorkerStateEvent> onSucceeded, EventHandler<WorkerStateEvent> onFailed) {

        Task<Object> task = new Task<Object>() {
            @Override
            public Object call() throws NoSuchAlgorithmException, MalformedURLException, IOException, InvalidKeyException, InvalidAlgorithmParameterException, NoSuchPaddingException, IllegalBlockSizeException, BadPaddingException {
                return encryptBytesToFile(appKey, bytes, outputFile);
            }

        };

        task.setOnFailed(onFailed);

        task.setOnSucceeded(onSucceeded);

        return execService.submit(task);
    }

     public static Byte[] charsToBoxedBytes(char[] chars){
        CharBuffer charBuffer = CharBuffer.wrap(chars);
        ByteBuffer byteBuffer = StandardCharsets.UTF_8.encode(charBuffer);
        
        int limit = byteBuffer.limit();
        int position = byteBuffer.position();
        byte[] arr = byteBuffer.array();


        int size = limit - position;
        Byte[] bytes = new Byte[size];

        int i = position;
        int j = 0;
        while( i < limit){
            bytes[j] = arr[i];
            j++;
            i++;
        }
        byteBuffer.clear();
        charBuffer.clear();

        return bytes;
    }




    public static byte[] createKeyBytes(SecretString password) throws InvalidKeySpecException, NoSuchAlgorithmException  {
        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        KeySpec spec = new PBEKeySpec(password.getData(), ByteDecoding.charsToBytes(password.getData()), 65536, 256);
        SecretKey tmp = factory.generateSecret(spec);
        return tmp.getEncoded();

    }
    public static Object getKeyObject(List<? extends Object> items, String key){
        for(int i = 0; i < items.size(); i++){
            Object item = items.get(i);
            if(item instanceof KeyInterface){
                KeyInterface keyItem = (KeyInterface) item;
                if(keyItem.getKey().equals(key)){
                    return keyItem;
                }
            }
        }
        return null;
    }


    public static void removeOldKeys(List<? extends Object> items, long timeStamp){
        ArrayList<String> keyRemoveList  = new ArrayList<>();

        for(int i = 0; i < items.size(); i++){
            Object item = items.get(i);
            if(item instanceof KeyInterface){
                KeyInterface keyItem = (KeyInterface) item;
                if(keyItem.getTimeStamp() < timeStamp){
                    keyRemoveList.add(keyItem.getKey());        
                }
            }
        }

        for(String key : keyRemoveList){
            removeKey(items, key);
        }
    }

    public static Object removeKey(List<? extends Object> items, String key){
        for(int i = 0; i < items.size(); i++){
            Object item = items.get(i);
            if(item instanceof KeyInterface){
                KeyInterface keyItem = (KeyInterface) item;
                if(keyItem.getKey().equals(key)){
                    return items.remove(i);
                }
            }
        }
        return null;
    }

    
    public static boolean updateFileEncryption(SecretKey oldAppKey, SecretKey newAppKey, File file, File tmpFile) throws FileNotFoundException, IOException, NoSuchAlgorithmException, NoSuchPaddingException, InvalidKeyException, InvalidAlgorithmParameterException, IllegalBlockSizeException, BadPaddingException {
        if(file != null && file.isFile()){
       
            try(
                FileInputStream inputStream = new FileInputStream(file);
                FileOutputStream outStream = new FileOutputStream(tmpFile);
            ){
                
                byte[] oldIV = new byte[12];

                byte[] newIV = getIV();

                inputStream.read(oldIV);
                outStream.write(newIV);

                Cipher decryptCipher = Cipher.getInstance("AES/GCM/NoPadding");
                GCMParameterSpec parameterSpec = new GCMParameterSpec(128, oldIV);
                decryptCipher.init(Cipher.DECRYPT_MODE, oldAppKey, parameterSpec);

                Cipher encryptCipher = Cipher.getInstance("AES/GCM/NoPadding");
                GCMParameterSpec newSpec = new GCMParameterSpec(128, newIV);
                encryptCipher.init(Cipher.ENCRYPT_MODE, newAppKey, newSpec);

                long fileSize = file.length();
                int bufferSize = fileSize < DEFAULT_BUFFER_SIZE ? (int) fileSize : DEFAULT_BUFFER_SIZE;

                byte[] readBuffer = new byte[bufferSize];
                byte[] decryptedBuffer;
                byte[] encryptedBuffer;

                int length = 0;

                while ((length = inputStream.read(readBuffer)) != -1) {
                    decryptedBuffer = decryptCipher.update(readBuffer, 0, length);
                    if(decryptedBuffer != null){
                        encryptedBuffer = encryptCipher.update(decryptedBuffer);
                        if(encryptedBuffer != null){
                            outStream.write(encryptedBuffer);
                        }
                    }
                }

                decryptedBuffer = decryptCipher.doFinal();

                if(decryptedBuffer != null){
                    encryptedBuffer = encryptCipher.update(decryptedBuffer);
                    if(encryptedBuffer != null){
                        outStream.write(encryptedBuffer);
                    }
                }

                encryptedBuffer = encryptCipher.doFinal();

                if(encryptedBuffer != null){
                    outStream.write(encryptedBuffer);
                }
                
            }

            Path filePath = file.toPath();
            Files.delete(filePath);
            FileUtils.moveFile(tmpFile, file);

            return true;
        }
        return false;
    }


    public static String hashChars(char[] chars){

        return Base64.encode(ByteHashing.digestBytesToBytes(ByteDecoding.charsToBytes(chars)));
    }

    public static String getStringFromResource(String resourceLocation) throws IOException{
        URL location = resourceLocation != null ? Utils.class.getResource(resourceLocation) : null;
        if(location != null){
            try(
                ByteArrayOutputStream outStream = new ByteArrayOutputStream();
                BufferedInputStream inStream = new BufferedInputStream(location.openStream());
            ){
                byte[] buffer = new byte[DEFAULT_BUFFER_SIZE];
                int length = 0;

                while ((length = inStream.read(buffer)) != -1){
                    outStream.write(buffer, 0, length);
                }

                return outStream.toString();
            }
        }else{
            return null;
        }
    }





    public static boolean decryptFileToFile(SecretKey appKey, File encryptedFile, File decryptedFile) throws IOException, InvalidKeyException, NoSuchPaddingException, NoSuchAlgorithmException, InvalidAlgorithmParameterException, BadPaddingException, IllegalBlockSizeException{
        if(encryptedFile != null && encryptedFile.isFile() && encryptedFile.length() > 12){
            
            try(
                FileInputStream inputStream = new FileInputStream(encryptedFile);
                FileOutputStream outStream = new FileOutputStream(decryptedFile);
            ){
                
                byte[] iV = new byte[12];

                inputStream.read(iV);

                Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
                GCMParameterSpec parameterSpec = new GCMParameterSpec(128, iV);
        
                cipher.init(Cipher.DECRYPT_MODE, appKey, parameterSpec);

                long fileSize = encryptedFile.length();
                int bufferSize = fileSize < (8 * 1024) ? (int) fileSize :(8 * 1024);

                byte[] buffer = new byte[bufferSize];
                byte[] decryptedBuffer;
                int length = 0;
                long decrypted = 0;

                while ((length = inputStream.read(buffer)) != -1) {
                    decryptedBuffer = cipher.update(buffer, 0, length);
                    if(decryptedBuffer != null){
                        outStream.write(decryptedBuffer);
                    }
                    decrypted += length;
                }

                decryptedBuffer = cipher.doFinal();

                if(decryptedBuffer != null){
                    outStream.write(decryptedBuffer);
                }

                if(decrypted == fileSize){
                    return true;
                }
            }

      
        }
        return false;
    }

  
    
    public static byte[] decryptFileToBytes(SecretKey appKey, File file) throws IOException, InvalidKeyException, NoSuchPaddingException, NoSuchAlgorithmException, InvalidAlgorithmParameterException, BadPaddingException, IllegalBlockSizeException{
        if(file != null && file.isFile()){
            
            try(
                FileInputStream inputStream = new FileInputStream(file);
                ByteArrayOutputStream outStream = new ByteArrayOutputStream();
            ){
                
                byte[] iV = new byte[12];

                int length = inputStream.read(iV);

                if(length < 12){
                    return null;
                }

                Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
                GCMParameterSpec parameterSpec = new GCMParameterSpec(128, iV);
        
                cipher.init(Cipher.DECRYPT_MODE, appKey, parameterSpec);

                long fileSize = file.length();
                int bufferSize = fileSize < (long) DEFAULT_BUFFER_SIZE ? (int) fileSize : DEFAULT_BUFFER_SIZE;

                byte[] buffer = new byte[bufferSize];
                byte[] decryptedBuffer;
               

                while ((length = inputStream.read(buffer)) != -1) {
                    decryptedBuffer = cipher.update(buffer, 0, length);
                    if(decryptedBuffer != null){
                        outStream.write(decryptedBuffer);
                    }
                }

                decryptedBuffer = cipher.doFinal();

                if(decryptedBuffer != null){
                    outStream.write(decryptedBuffer);
                }

                return outStream.toByteArray();
               
            }
        }
        return null;

    }


    public static byte[] readEncryptedData(SecretKey appKey, File dataFile) throws IOException, NoSuchAlgorithmException, InvalidKeyException, NoSuchPaddingException, InvalidAlgorithmParameterException, BadPaddingException, IllegalBlockSizeException, ShortBufferException {

        File tmpFile = new File(dataFile.getAbsolutePath() + ".tmp");
        byte[] outputData;

        try(
            FileInputStream inputStream = new FileInputStream(dataFile);
            FileOutputStream fileOutputStream = new FileOutputStream(tmpFile);
            ByteArrayOutputStream outStream = new ByteArrayOutputStream();
        ){
            
            byte[] inIV = new byte[12];
            int length = inputStream.read(inIV);

            if(length < 12){
                return null;
            }

            Cipher inCipher = Cipher.getInstance("AES/GCM/NoPadding");
            GCMParameterSpec inSpec = new GCMParameterSpec(128, inIV);
            inCipher.init(Cipher.DECRYPT_MODE, appKey, inSpec);


            byte[] outIV = getIV();

            Cipher outCipher = Cipher.getInstance("AES/GCM/NoPadding");
            GCMParameterSpec outSpec = new GCMParameterSpec(128, outIV);
            outCipher.init(Cipher.ENCRYPT_MODE, appKey, outSpec);

            byte[] byteArray = new byte[DEFAULT_BUFFER_SIZE];
            byte[] output;

            fileOutputStream.write(outIV);    


            while((length = inputStream.read(byteArray)) != -1){

                output = outCipher.update(byteArray, 0, length);
                if(output != null){
                    fileOutputStream.write(output);
                    outStream.write(output);
                }
            }

            output = outCipher.doFinal();
            if(output != null){
                fileOutputStream.write(output);
                outStream.write(output);
            }

            outputData = outStream.toByteArray();

        }

        Path filePath = dataFile.toPath();
        Files.delete(filePath);
        FileUtils.moveFile(tmpFile, dataFile);
        
        
        return outputData;
        
    }

    public static Future<?> getImageFromBytes(byte[] bytes, ExecutorService execService, EventHandler<WorkerStateEvent> onSucceeded, EventHandler<WorkerStateEvent> onFailed){
        
        Task<Image> task = new Task<Image>() {
            @Override
            public Image call() throws IOException{

                Image image = new Image (new ByteArrayInputStream(bytes));
                return image;
            }
        };
        task.setOnFailed(onFailed);

        task.setOnSucceeded(onSucceeded);

        return execService.submit(task);
    }

    public static Image downloadImageAndEncryptFile(String urlString, SecretKey appKey, File downloadFile) throws NoSuchAlgorithmException, NoSuchPaddingException, IOException, InvalidKeyException, InvalidAlgorithmParameterException, IllegalBlockSizeException, BadPaddingException{
        if(downloadFile != null){
            byte[] iV = Utils.getIV();

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            GCMParameterSpec parameterSpec = new GCMParameterSpec(128, iV);
            cipher.init(Cipher.ENCRYPT_MODE, appKey, parameterSpec);

            URL url = new URL(urlString);
            URLConnection con = url.openConnection();
            con.setRequestProperty("User-Agent", Utils.USER_AGENT);
            long contentLength = con.getContentLengthLong();

            if(downloadFile.isFile()){
                Files.delete(downloadFile.toPath());
            }

            try(
                InputStream inputStream = con.getInputStream();
                ByteArrayOutputStream byteOutputStream = new ByteArrayOutputStream();
                FileOutputStream fileStream = new FileOutputStream(downloadFile);
            ){
            
                
                byte[] output;
                byte[] buffer = new byte[contentLength != -1 && contentLength < DEFAULT_BUFFER_SIZE ? (int) contentLength : DEFAULT_BUFFER_SIZE];

                int length;

                while ((length = inputStream.read(buffer)) != -1) {
                    byteOutputStream.write(buffer, 0 ,length);
                    output = cipher.update(buffer, 0, length);

                    if(output != null){
                        fileStream.write(output);
                    }
                }

                output = cipher.doFinal();

                if(output != null){
                    fileStream.write(output);
                }

                return new Image(new ByteArrayInputStream(byteOutputStream.toByteArray()));
            }
        }
        return null;
    }

    public static Future<?> dowloadAndEncryptFile(String urlString,Image icon, String headingString, SecretKey appKey, File file, ExecutorService execService, EventHandler<WorkerStateEvent> onSucceeded, EventHandler<WorkerStateEvent> onFailed, ProgressIndicator progressIndicator){
        Stage progressStage = new Stage();
        progressStage.setResizable(false);
        progressStage.initStyle(StageStyle.UNDECORATED);
        Button closeBtn = new Button();
        ProgressBar progressBar = new ProgressBar();
        SimpleStringProperty contextString = new SimpleStringProperty("");

        contextString.bind(Bindings.createObjectBinding(()->{
            double progress = progressBar.progressProperty().get();
            BigDecimal decimalProgress = progress != 0 ? BigDecimal.valueOf(progress).multiply(BigDecimal.valueOf(100)).setScale(1, RoundingMode.HALF_UP) : BigDecimal.ZERO;
            return progress == 0 ? "Starting..." : decimalProgress + "%";
        }, progressBar.progressProperty()));

        progressStage.setScene(Stages.getFileProgressScene(icon, headingString, contextString,file.getName(),progressBar, progressStage, closeBtn));
        progressStage.show();

        Task<Object> task = new Task<Object>() {
            @Override
            public Object call() throws IOException, NoSuchAlgorithmException, NoSuchPaddingException, InvalidKeyException, InvalidAlgorithmParameterException, IllegalBlockSizeException, BadPaddingException {
                byte[] iV = Utils.getIV();

                Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
                GCMParameterSpec parameterSpec = new GCMParameterSpec(128, iV);
                cipher.init(Cipher.ENCRYPT_MODE, appKey, parameterSpec);


                URL url = new URL(urlString);
                URLConnection con = url.openConnection();
                con.setRequestProperty("User-Agent", USER_AGENT);
        
                try(
                    InputStream inputStream = con.getInputStream();
                    FileOutputStream outputStream = new FileOutputStream(file);
                ){
        
                    byte[] buffer = new byte[2024];
                    int length = 0;
                    long downloaded = 0;
                    long contentLength = con.getContentLengthLong();

                    outputStream.write(iV);
                    
                    updateProgress(downloaded, contentLength);
                    byte[] output;
                    while ((length = inputStream.read(buffer)) != -1) {
                        downloaded += length;

                        output = cipher.update(buffer, 0, length);

                        outputStream.write(output, 0, output.length);

                        updateProgress(downloaded, contentLength );
                    }


                    output = cipher.doFinal();

                    if(output != null){
                        outputStream.write(output);
                    }
        
                    return true;
           
                }

            }

        };
        progressBar.progressProperty().bind( task.progressProperty());
      
        task.setOnFailed((error)->{
            closeBtn.setOnAction(null);
            progressBar.progressProperty().unbind();
            contextString.unbind();
            progressStage.close();
            Throwable throwable = error.getSource().getException();
            Exception ex = throwable != null && throwable instanceof Exception ? (Exception) throwable : null;
            if(ex != null){
                if(ex instanceof InterruptedException){
                    Utils.returnException(new InterruptedException("Canceled"), execService, onFailed);
                }else{
                    Utils.returnException(ex, execService, onFailed);
                }
            }else{
                Utils.returnException("Download failed", execService, onFailed);
            }
        });

        task.setOnSucceeded((onFinished)->{
            closeBtn.setOnAction(null);
            progressBar.progressProperty().unbind();
            contextString.unbind();
            progressStage.close();
            Utils.returnObject(onFinished.getSource().getValue(), execService, onSucceeded);
        });

        Future<?> future = execService.submit(task);

        closeBtn.setOnAction(e->{
            future.cancel(true);
            
        });

        return future;
    }

    public static Future<?> encryptFileAndHash(SecretKey appKey, Image icon, String headingString, ExecutorService execService, File inputFile, File file, EventHandler<WorkerStateEvent> onSucceeded, EventHandler<WorkerStateEvent> onFailed) {

        Stage progressStage = new Stage();
        progressStage.setResizable(false);
        progressStage.initStyle(StageStyle.UNDECORATED);
        Button closeBtn = new Button();
        ProgressBar progressBar = new ProgressBar();
        SimpleStringProperty contextString = new SimpleStringProperty("");

        contextString.bind(Bindings.createObjectBinding(()->{
            double progress = progressBar.progressProperty().get();
            BigDecimal decimalProgress = BigDecimal.valueOf(progress).multiply(BigDecimal.valueOf(100)).setScale(1, RoundingMode.HALF_UP);
            return progress == 0 ? "Starting..." : decimalProgress + "%";
        }, progressBar.progressProperty()));

        progressStage.setScene(Stages.getFileProgressScene(icon, headingString, contextString,file.getName(),progressBar, progressStage, closeBtn));
        progressStage.show();

        Task<Object> task = new Task<Object>() {
            @Override
            public Object call() throws IOException, NoSuchAlgorithmException, NoSuchPaddingException, InvalidKeyException, InvalidAlgorithmParameterException, IllegalBlockSizeException, BadPaddingException {
                
                SecureRandom secureRandom = new SecureRandom();
                byte[] iV = new byte[12];
                secureRandom.nextBytes(iV);
                
                Blake2b digest = Blake2b.Digest.newInstance(32);
                Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
                GCMParameterSpec parameterSpec = new GCMParameterSpec(128, iV);

                cipher.init(Cipher.ENCRYPT_MODE, appKey, parameterSpec);

     
                try(
                    FileInputStream inputStream = new FileInputStream(inputFile);
                    FileOutputStream outputStream = new FileOutputStream(file);
                ){
        
                    byte[] buffer = new byte[2024];
                    int length = 0;
                    long copied = 0;
                    long contentLength = inputFile.length();
                    
                    updateProgress(copied, contentLength);
                    outputStream.write(iV);
                    byte[] encrypted;
                    while ((length = inputStream.read(buffer)) != -1) {
                        copied += length;
                        encrypted = cipher.update(buffer, 0, length);
                        digest.update(buffer, 0, length);
                        outputStream.write(encrypted);

                        updateProgress(copied, contentLength );
                    }
                    encrypted = cipher.doFinal();
                    if(encrypted != null){
                        outputStream.write(encrypted);
                    }
                    return new HashData(digest.digest());
           
                }

            }

        };
        progressBar.progressProperty().bind( task.progressProperty());
      
        task.setOnFailed((error)->{
            closeBtn.setOnAction(null);
            progressBar.progressProperty().unbind();
            contextString.unbind();
            progressStage.close();
            Throwable throwable = error.getSource().getException();
            Exception ex = throwable != null && throwable instanceof Exception ? (Exception) throwable : null;
            if(ex != null){
                if(ex instanceof InterruptedException){
                    Utils.returnException(new InterruptedException("Canceled"), execService, onFailed);
                }else{
                    Utils.returnException(ex, execService, onFailed);
                }
            }else{
                Utils.returnException("Download failed", execService, onFailed);
            }
        });

        task.setOnSucceeded((onFinished)->{
            closeBtn.setOnAction(null);
            progressBar.progressProperty().unbind();
            contextString.unbind();
            progressStage.close();
            Utils.returnObject(onFinished.getSource().getValue(), execService, onSucceeded);
        });

        Future<?> future = execService.submit(task);

        closeBtn.setOnAction(e->{
            future.cancel(true);
            
        });

        return future;


      
    }
    
    public static byte[] getIV() throws NoSuchAlgorithmException{
        SecureRandom secureRandom = new SecureRandom();
        byte[] iV = new byte[12];
        secureRandom.nextBytes(iV);
        return iV;
    }

    public static boolean encryptFile(SecretKey appKey, File inputFile, File outputFile ) throws NoSuchAlgorithmException, NoSuchPaddingException, InvalidKeyException, InvalidAlgorithmParameterException, IOException, IllegalBlockSizeException, BadPaddingException {

        long contentLength = -1;
      
        byte[] iV = getIV();
        
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        GCMParameterSpec parameterSpec = new GCMParameterSpec(128, iV);

        cipher.init(Cipher.ENCRYPT_MODE, appKey, parameterSpec);

        if (inputFile != null && inputFile.isFile() && outputFile != null && !inputFile.getAbsolutePath().equals(outputFile.getAbsolutePath())) {
            contentLength = Files.size(inputFile.toPath());
        } else {
            return false;
        }

        if (outputFile.isFile()) {
            Files.delete(outputFile.toPath());
        }

        try(
            FileOutputStream outputStream = new FileOutputStream(outputFile);
            FileInputStream inputStream = new FileInputStream(inputFile);
        ){
            long fileSize = inputFile.length();
            int bufferSize = fileSize < (8 * 1024) ? (int) fileSize :(8 * 1024);
            
            byte[] buffer = new byte[bufferSize];
            byte[] output;
            int length;
            long copied = 0;
    
            outputStream.write(iV);

            while ((length = inputStream.read(buffer)) != -1) {


                output = cipher.update(buffer, 0, length);
                if(output != null){
                    outputStream.write(output);
                }
                copied += (long) length;
                
                
            }

            output = cipher.doFinal();

            if(output != null){
                outputStream.write(output);
            }

            if( contentLength == copied){
                return true;
            }

        }

        
        return false;
            
   
    }
    

    public static JsonObject readJsonFile(SecretKey appKey, File file) throws InvalidKeyException, NoSuchPaddingException, NoSuchAlgorithmException, InvalidAlgorithmParameterException, BadPaddingException, IllegalBlockSizeException, IOException {

        byte[] bytes = decryptFileToBytes(appKey,file);
        
        if(bytes != null){

            JsonElement jsonElement = new JsonParser().parse(new String(bytes));

            return jsonElement != null && jsonElement.isJsonObject() ? jsonElement.getAsJsonObject() : null;
            
        }
        return null;

    }

    public static Future<?> readJsonFile(SecretKey appKey, File file, ExecutorService execService, EventHandler<WorkerStateEvent> onSucceeded, EventHandler<WorkerStateEvent> onFailed){
        if(appKey != null && file != null && file.isFile()  && execService != null){
            Task<Object> task = new Task<Object>() {
                @Override
                public Object call() throws InterruptedException, InvalidKeyException, NoSuchPaddingException, NoSuchAlgorithmException, InvalidAlgorithmParameterException, BadPaddingException, IllegalBlockSizeException, IOException{
    
                    JsonObject json = readJsonFile(appKey, file);

                    return json;
                }
            };
            task.setOnFailed(onFailed);

            task.setOnSucceeded(onSucceeded);

            return execService.submit(task);
        }
        return null;
    }
    
    
    public static Future<?> readJsonArrayFile(SecretKey appKey, File file, ExecutorService execService, EventHandler<WorkerStateEvent> onSucceeded, EventHandler<WorkerStateEvent> onFailed) {
        
        if(appKey != null && file != null){
            Task<Object> task = new Task<Object>() {
                @Override
                public Object call() throws InvalidKeyException, NoSuchPaddingException, NoSuchAlgorithmException, InvalidAlgorithmParameterException, BadPaddingException, IllegalBlockSizeException, IOException, InterruptedException {
                    
                    byte[] bytes = decryptFileToBytes(appKey, file);
                    if(bytes != null){
                        JsonElement jsonElement = new JsonParser().parse(new String(bytes));
                        if (jsonElement != null && jsonElement.isJsonArray()) {
                            return jsonElement.getAsJsonArray();
                        }
                    }

                    return null;
                    
                }
            };
        
            task.setOnFailed(onFailed);

            task.setOnSucceeded(onSucceeded);

            return execService.submit(task);
        }
        return null;
    }


    public static void writeEncryptedString(SecretKey secretKey, File dataFile, String str) throws NoSuchAlgorithmException, NoSuchPaddingException, InvalidKeyException, InvalidAlgorithmParameterException, IllegalBlockSizeException, BadPaddingException, IOException {
        if( secretKey != null && dataFile != null && str != null){

            byte[] iV = getIV();

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            GCMParameterSpec parameterSpec = new GCMParameterSpec(128, iV);

            cipher.init(Cipher.ENCRYPT_MODE, secretKey, parameterSpec);

            
            if (dataFile.isFile()) {
                Files.delete(dataFile.toPath());
            }
  
            
            try(
                ReaderInputStream inputStream = new ReaderInputStream(new StringReader(str), StandardCharsets.UTF_8);
                FileOutputStream outputStream = new FileOutputStream(dataFile);
            ){
                
                outputStream.write(iV);

                //int written = 0;
                int bufferLength =  1024;
                int length = 0;

                byte[] intputBuffer = new byte[bufferLength];
                byte[] output;

                while ((length = inputStream.read(intputBuffer)) != -1) {
                    output = cipher.update(intputBuffer, 0, length);
                    if(output != null){
                        outputStream.write(output);
                    }
                    //written += length;
                }

                output = cipher.doFinal();

                if(output != null){
                    outputStream.write(output);
                }

                
            }

 
        }

    }

    public static String readStringFile(SecretKey appKey, File file) throws InvalidKeyException, NoSuchPaddingException, NoSuchAlgorithmException, InvalidAlgorithmParameterException, BadPaddingException, IllegalBlockSizeException, IOException {

        byte[] bytes = decryptFileToBytes(appKey, file);
        if(bytes != null){
            return new String(bytes);
        }else{
            return null;
        }
    }

   

    public static Future<?> copyFileAndHash(File inputFile, File outputFile, ExecutorService execService, EventHandler<WorkerStateEvent> onSucceeded, EventHandler<WorkerStateEvent> onFailed, ProgressIndicator progressIndicator) {

        Task<HashData> task = new Task<HashData>() {
            @Override
            public HashData call() throws NoSuchAlgorithmException, MalformedURLException, IOException {
                long contentLength = -1;

                if (inputFile != null && inputFile.isFile() && outputFile != null && !inputFile.getAbsolutePath().equals(outputFile.getAbsolutePath())) {
                    contentLength = Files.size(inputFile.toPath());
                } else {
                    return null;
                }
                final Blake2b digest = Blake2b.Digest.newInstance(32);

                try(
                    FileInputStream inputStream = new FileInputStream(inputFile);
                    FileOutputStream outputStream = new FileOutputStream(outputFile);
                ){
                    byte[] buffer = new byte[contentLength < (long) DEFAULT_BUFFER_SIZE ? (int) contentLength : DEFAULT_BUFFER_SIZE];

                    int length;
                    long copied = 0;

                    while ((length = inputStream.read(buffer)) != -1) {

                        outputStream.write(buffer, 0, length);
                        digest.update(buffer, 0, length);

                        copied += (long) length;
                        if(progressIndicator != null){
                            updateProgress(copied, contentLength);
                        }
                    }



                }

                return new HashData(digest.digest());

            }

        };

        if (progressIndicator != null) {
            progressIndicator.progressProperty().bind(task.progressProperty());
        }

        task.setOnFailed(onFailed);

        task.setOnSucceeded(onSucceeded);

        return execService.submit(task);
    }
    

   
    public static Future<?> getUrlFileHash(String urlString, Image icon, String headingString, File file, ExecutorService execService, EventHandler<WorkerStateEvent> onSucceeded, EventHandler<WorkerStateEvent> onFailed) {
        Stage progressStage = new Stage();
        progressStage.setResizable(false);
        progressStage.initStyle(StageStyle.UNDECORATED);
        
        Button closeBtn = new Button();
        ProgressBar progressBar = new ProgressBar();
        SimpleStringProperty contextString = new SimpleStringProperty("");

        contextString.bind(Bindings.createObjectBinding(()->{
            double progress = progressBar.progressProperty().get();
            BigDecimal decimalProgress = BigDecimal.valueOf(progress).multiply(BigDecimal.valueOf(100)).setScale(1, RoundingMode.HALF_UP);
            return progress == 0 ? "Starting..." : decimalProgress + "%";
        }, progressBar.progressProperty()));

        progressStage.setScene(Stages.getFileProgressScene(icon, headingString, contextString,file.getName(),progressBar, progressStage, closeBtn));
       
        progressStage.show();

        Task<Object> task = new Task<Object>() {
            @Override
            public Object call() throws IOException {
           
                URL url = new URL(urlString);
                URLConnection con = url.openConnection();
                con.setRequestProperty("User-Agent", USER_AGENT);
                final Blake2b digest = Blake2b.Digest.newInstance(32);
                try(
                    InputStream inputStream = con.getInputStream();
                    FileOutputStream outputStream = new FileOutputStream(file);
                ){
        
                    byte[] buffer = new byte[2024];
                    int length = 0;
                    long downloaded = 0;
                    long contentLength = con.getContentLengthLong();
                    
                    updateProgress(downloaded, contentLength);
                    
                    while ((length = inputStream.read(buffer)) != -1) {
                        downloaded += length;
                        outputStream.write(buffer, 0, length);
                        digest.update(buffer,0,length);
                        updateProgress(downloaded, contentLength );
                    }
                    

                    
                    return new HashData(digest.digest());
           
                }

            }

        };
        progressBar.progressProperty().bind( task.progressProperty());
      
        task.setOnFailed((error)->{
            closeBtn.setOnAction(null);
            progressBar.progressProperty().unbind();
            contextString.unbind();
            progressStage.close();
            Throwable throwable = error.getSource().getException();
            Exception ex = throwable != null && throwable instanceof Exception ? (Exception) throwable : null;
            if(ex != null){
                if(ex instanceof InterruptedException){
                    Utils.returnException(new InterruptedException("Canceled"), execService, onFailed);
                }else{
                    Utils.returnException(ex, execService, onFailed);
                }
            }else{
                Utils.returnException("Download failed", execService, onFailed);
            }
        });

        task.setOnSucceeded((onFinished)->{
            closeBtn.setOnAction(null);
            progressBar.progressProperty().unbind();
            contextString.unbind();
            progressStage.close();
            Utils.returnObject(onFinished.getSource().getValue(), execService, onSucceeded);
        });

        Future<?> future = execService.submit(task);

        closeBtn.setOnAction(e->{
            future.cancel(true);
            
        });

        return future;

    }
   

   

    public static void centerStage(Stage stage, Rectangle screenRectangle){
        stage.setX(screenRectangle.getWidth()/2 - stage.getWidth()/2);
        stage.setY(screenRectangle.getHeight()/2 - stage.getHeight()/2);
    }

    

    public static String[] pslastPID(String jarname){
          try {
          //  File logFile = new File("wmicTerminate-log.txt");
            //Get-Process | Where {$_.ProcessName -Like "SearchIn*"}
         //   String[] wmicCmd = {"powershell", "Get-Process", "|", "Where", "{$_.ProcessName", "-Like", "'*" +  jarname+ "*'}"};
            Process psProc = Runtime.getRuntime().exec("powershell Get-WmiObject Win32_Process | WHERE {$_.CommandLine -Like '*"+jarname+"*' } | Select ProcessId");

            BufferedReader psStderr = new BufferedReader(new InputStreamReader(psProc.getErrorStream()));
            //String pserr = null;


            ArrayList<String> pids = new ArrayList<>();

            BufferedReader psStdInput = new BufferedReader(new InputStreamReader(psProc.getInputStream()));

            String psInput = null;
           // boolean gotInput = false;
            //   int pid = -1;
               
            while ((psInput = psStdInput.readLine()) != null) {
              //  
              //  gotInput = true;
                psInput.trim();
                if(!psInput.equals("") && !psInput.startsWith("ProcessId") && !psInput.startsWith("---------")){
                    
                    pids.add(psInput);
                }
            }
            
            String  pserr = null;
            while ((pserr = psStderr.readLine()) != null) {
                try {
                    Files.writeString(new File("netnotes-log.txt").toPath(), "\npsPID err: " + pserr, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                } catch (IOException e1) {
                
                }
               // Files.writeString(logFile.toPath(), "\nps err: " + wmicerr, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                
            }

            psProc.waitFor();
            if( pids.size() > 0){
                String[] pidArray = new String[pids.size()];

                pidArray =  pids.toArray(pidArray);
                
                return pidArray;
            }else{
                return null;
            }
            

        } catch (Exception e) {
              try {
                Files.writeString(new File("netnotes-log.txt").toPath(), "\npsPID: " + e.toString(), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (IOException e1) {
             
            }
             
            return null;
        }
   
    }

     public static void psStopProcess(String pid){
          try {
          //  File logFile = new File("wmicTerminate-log.txt");
            //Get-Process | Where {$_.ProcessName -Like "SearchIn*"}
         //   String[] wmicCmd = {"powershell", "Get-Process", "|", "Where", "{$_.ProcessName", "-Like", "'*" +  jarname+ "*'}"};
            Process psProc = Runtime.getRuntime().exec("powershell stop-process -id " + pid );


            psProc.waitFor();



        } catch (Exception e) {
            
        }
   
    }

     public static void cmdTaskKill(String pid){
          try {

            Process psProc = Runtime.getRuntime().exec("cmd /c taskkill /PID " + pid );


            psProc.waitFor();



        } catch (Exception e) {
            
        }
   
    }

   

    public static int findMenuItemIndex(ObservableList<MenuItem> list, String id){
        if(id != null){
            for(int i = 0; i < list.size() ; i++){
                MenuItem menuItem = list.get(i);
                Object userData = menuItem.getUserData();

                if(userData != null && userData instanceof String){
                    String menuItemId = (String) userData;
                    if(menuItemId.equals(id)){
                        return i;
                    }
                }
            }
        }

        return -1;
    }

    public static boolean wmicTerminate(String jarName) {
        try {
          //  File logFile = new File("wmicTerminate-log.txt");
     
            String[] wmicCmd = {"cmd", "/c", "wmic", "Path", "win32_process", "Where", "\"CommandLine", "Like", "'%" + jarName + "%'\"", "Call", "Terminate"};
            Process wmicProc = Runtime.getRuntime().exec(wmicCmd);

            BufferedReader wmicStderr = new BufferedReader(new InputStreamReader(wmicProc.getErrorStream()));
            //String wmicerr = null;


         

            BufferedReader wmicStdInput = new BufferedReader(new InputStreamReader(wmicProc.getInputStream()));

           // String wmicInput = null;
            boolean gotInput = false;

            while ((wmicStdInput.readLine()) != null) {
            
            
                gotInput = true;
            }

            while ((wmicStderr.readLine()) != null) {

               // Files.writeString(logFile.toPath(), "\nwmic err: " + wmicerr, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                return false;
            }

            wmicProc.waitFor();

            if (gotInput) {
                return true;
            }

        } catch (Exception e) {
            return false;
        }
        return false;
    }


    public static String[] getShellCmd(String cmd){
        
        return new String[]{"bash", "-c", cmd};
    }
    

    public static String[] findPIDs(String jarName){
        try {
            //  File logFile = new File("wmicTerminate-log.txt");
              //Get-Process | Where {$_.ProcessName -Like "SearchIn*"}
           //   String[] wmicCmd = {"powershell", "Get-Process", "|", "Where", "{$_.ProcessName", "-Like", "'*" +  jarname+ "*'}"};
            String execString = "ps -ef | grep -v grep | grep " + jarName + " | awk '{print $2}'";
            String[] cmd = new String[]{ "bash", "-c", execString};
            Process proc = Runtime.getRuntime().exec(cmd);
  
              BufferedReader stderr = new BufferedReader(new InputStreamReader(proc.getErrorStream()));
              //String pserr = null;
  
  
              ArrayList<String> pids = new ArrayList<>();
  
              BufferedReader stdInput = new BufferedReader(new InputStreamReader(proc.getInputStream()));
  
              String input = null;
                 
              while ((input = stdInput.readLine()) != null) {
                input.trim();
                pids.add(input);
                  
              }
              
              String  pserr = null;
              while ((pserr = stderr.readLine()) != null) {
                  try {
                      Files.writeString(new File("netnotes-log.txt").toPath(), "\nutils: " + execString + ": " + pserr, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                  } catch (IOException e1) {
                  
                  }
                 // Files.writeString(logFile.toPath(), "\nps err: " + wmicerr, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                  
              }
  
              proc.waitFor();
              if( pids.size() > 0){
             
                  String[] pidArray = new String[pids.size()];
  
                  pidArray =  pids.toArray(pidArray);
                  
                  return pidArray;
              }else{
                  return null;
              }
              
  
          } catch (Exception e) {
                try {
                  Files.writeString(new File("netnotes-log.txt").toPath(), "\npsPID: " + e.toString(), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
              } catch (IOException e1) {
               
              }
               
              return null;
          }

    }
    /*
    public static boolean sendTermSig(String pid){
        try {
            //  File logFile = new File("wmicTerminate-log.txt");
       
              //String[] wmicCmd = {"cmd", "/c", "wmic", "Path", "win32_process", "Where", "\"CommandLine", "Like", "'%" + jarName + "%'\"", "Call", "Terminate"};
              String[] cmd = new String[]{ "bash", "-c",  "kill -SIGTERM " + pid};

              Process wmicProc = Runtime.getRuntime().exec(cmd);
  
              BufferedReader wmicStderr = new BufferedReader(new InputStreamReader(wmicProc.getErrorStream()));
  
              BufferedReader wmicStdInput = new BufferedReader(new InputStreamReader(wmicProc.getInputStream()));
  
             // String wmicInput = null;
              boolean gotInput = false;
  
              while ((wmicStdInput.readLine()) != null) {
              
              
                  gotInput = true;
              }
  
              while ((wmicStderr.readLine()) != null) {
  
                 // Files.writeString(logFile.toPath(), "\nwmic err: " + wmicerr, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                  return false;
              }
  
              wmicProc.waitFor();
  
              if (gotInput) {
                  return true;
              }
  
          } catch (Exception e) {
              return false;
          }
          return false;
    }*/
     
    
    public static Future<?> sendTermSig(String jarName, ExecutorService execService, EventHandler<WorkerStateEvent> onSucceeded,EventHandler<WorkerStateEvent> onFailed) {
        

        Task<Object> task = new Task<Object>() {
            @Override
            public Object call() throws Exception {
                String execString = "kill $(ps -ef | grep -v grep | grep " + jarName + " | awk '{print $2}')";

                String[] cmd = new String[]{ "bash", "-c", execString};
    
                Process proc = Runtime.getRuntime().exec(cmd);
    
                BufferedReader stdErr = new BufferedReader(new InputStreamReader(proc.getErrorStream()));
                //String wmicerr = null;
    
    
                BufferedReader wmicStdInput = new BufferedReader(new InputStreamReader(proc.getInputStream()));
    
               // String wmicInput = null;
                boolean gotInput = false;
    
                while ((wmicStdInput.readLine()) != null) {
                
                
                    gotInput = true;
                }
                String errStr = "";
                while ((errStr = stdErr.readLine()) != null) {
    
                 
                    gotInput = false;
                }
    
                proc.waitFor();
    
     
                if(gotInput){
                    return true;
                }else{
                    throw new Exception("\nsig term err: " + errStr + "\n'" + execString + "'");
                }
                

                
            }
        };

        task.setOnFailed(onFailed);

        task.setOnSucceeded(onSucceeded);

        return execService.submit(task);
    }

    public static boolean sendTermSig(String jarName) {
        try {
          //  File logFile = new File("wmicTerminate-log.txt");
     
            //String[] wmicCmd = {"cmd", "/c", "wmic", "Path", "win32_process", "Where", "\"CommandLine", "Like", "'%" + jarName + "%'\"", "Call", "Terminate"};
            String execString = "kill $(ps -ef | grep -v grep | grep " + jarName + " | awk '{print $2}')";

            String[] cmd = new String[]{ "bash", "-c", execString};

            Process proc = Runtime.getRuntime().exec(cmd);

            BufferedReader stdErr = new BufferedReader(new InputStreamReader(proc.getErrorStream()));
            //String wmicerr = null;


            BufferedReader wmicStdInput = new BufferedReader(new InputStreamReader(proc.getInputStream()));

           // String wmicInput = null;
            boolean gotInput = false;

            while ((wmicStdInput.readLine()) != null) {
            
            
                gotInput = true;
            }
            String errStr = "";
            while ((errStr = stdErr.readLine()) != null) {

                Files.writeString(new File("netnotes-log.txt").toPath(), "\nsig term err: " + errStr + "\n'" + execString + "'", StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                gotInput = false;
            }

            proc.waitFor();

            if (gotInput) {
                return true;
            }

        } catch (Exception e) {
            return false;
        }
        return false;
    }
    public static void open(File file) throws Exception{

        String[] cmd = new String[]{ "bash", "-c",  "xdg-open " + file.getCanonicalPath()};

        Runtime.getRuntime().exec(cmd);
  
    }






    public static FreeMemory getFreeMemory() {
        try{ 
            String[] cmd = new String[]{ "bash", "-c",  "cat /proc/meminfo | awk '{print $1,$2}'"};

            Process proc = Runtime.getRuntime().exec(cmd);


            BufferedReader stdInput = new BufferedReader(new InputStreamReader(proc.getInputStream()));
            BufferedReader stdErr = new BufferedReader(new InputStreamReader(proc.getErrorStream()));
        

            long swapTotal = -1;
            long swapFree = -1;
            long memFree = -1;
            long memAvailable = -1;
            long memTotal = -1;

            String s = null;

            String delimiter = ": ";
            int delimiterSize = delimiter.length();

            while ((s = stdInput.readLine()) != null) {
                
                int spaceIndex = s.indexOf(delimiter);
                
                
                String rowStr = s.substring(0, spaceIndex);
                long value = Long.parseLong(s.substring(spaceIndex + delimiterSize ));
                
                switch(rowStr){
                    case "SwapTotal":
                        swapTotal = value;
                    break;
                    case "SwapFree":
                        swapFree = value;
                    break;
                    case "MemTotal":
                        memTotal = value;
                    break;
                    case "MemFree":
                        memFree = value;
                    break;
                    case "MemAvailable":
                        memAvailable = value;
                    break;
                }

            }

            String errStr = stdErr.readLine();
            
            proc.waitFor();

            if(errStr == null){
                return new FreeMemory(swapTotal, swapFree, memFree, memAvailable, memTotal);
            }
        }catch(IOException | InterruptedException e){
            try {
                Files.writeString(new File("netnotes-log.txt").toPath(), "\nUtils getFreeMemory:" + e.toString(), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (IOException e1) {

            }
        }

        return null;
    }

    public static String getIncreseSwapUrl(){
        return "https://askubuntu.com/questions/178712/how-to-increase-swap-space";
    }


    public static void pingIP(String ip, int timeout, ExecutorService execService, EventHandler< WorkerStateEvent> onSucceeded, EventHandler< WorkerStateEvent> onFailed){
        Task<Boolean> task = new Task<Boolean>() {
            @Override
            public Boolean call() {

                try{
                    InetAddress address = InetAddress.getByName(ip);
                    return address.isReachable(timeout);

                
                }catch(IOException e){
                   
                }
                return false;
            }

        };

        task.setOnSucceeded(onSucceeded);
        task.setOnFailed(onFailed);

    
        execService.submit(task);
    }
   

    public static void pingIPconsole(String ip, int pingTimes, ExecutorService execService, EventHandler< WorkerStateEvent> onSucceeded, EventHandler< WorkerStateEvent> onFailed){

        Task<Ping> task = new Task<Ping>() {
            @Override
            public Ping call()throws IOException {

                String[] cmd = {"bash", "-c", "ping -c 3 " + ip};
                Ping ping = new Ping(false, "", -1);

                String line;
        

                Process proc = Runtime.getRuntime().exec(cmd);

                try(
                    BufferedReader wmicStderr = new BufferedReader(new InputStreamReader(proc.getErrorStream()));
                ){

                    while ((line = wmicStderr.readLine()) != null) {
                        ping.setError(ping.getError() + line + " ");
                    }

                    if(!ping.getError().equals(""))
                    {
                        return ping;
                    }
                   
                }catch(IOException e){
                    ping.setError(e.toString());
                    return ping;
                }
                
                try(
            
                    BufferedReader stdInput = new BufferedReader(new InputStreamReader(proc.getInputStream()));
                ){

                
                    String timeString = "time=";

                    while (true) {
                    
                        line = stdInput.readLine();
                

                        if(line == null){
                            break;
                        }

                        
                        
                    
                
                        int indexOftimeString = line.indexOf(timeString);

                        if (line.indexOf("service not known") > -1) {
                            ping.setAvailable(false);
                            ping.setError("Unreachable");
                            break;
                        }

                        if (line.indexOf("timed out") > -1) {

                            ping.setAvailable(false);
                            ping.setError( "Timed out");
                            break;
                        }

                        if (indexOftimeString > -1) {
                            int lengthOftime = timeString.length();

                            int indexOfms = line.indexOf("ms");

                            ping.setAvailable(true);

                            String time = line.substring(indexOftimeString + lengthOftime, indexOfms).trim();
        

                            ping.setAvgPing(Double.parseDouble(time));
                        
                        }

                        String avgString = "min/avg/max/mdev = ";
                        int indexOfAvgString = line.indexOf(avgString);

                        if (indexOfAvgString > -1) {
                            int lengthOfAvg = avgString.length();

                            String avgStr = line.substring(indexOfAvgString + lengthOfAvg);
                            int slashIndex = avgStr.indexOf("/");

                            avgStr = avgStr.substring(slashIndex+1, avgStr.indexOf("/",slashIndex + 1) ).trim();
                        
                            ping.setAvailable(true);
                            ping.setAvgPing(Double.parseDouble(avgStr));
                    
                        }

                    }
                }catch(Exception e){
                    try {
                        Files.writeString(AppConstants.LOG_FILE.toPath(),e.toString() +"\n" , StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                    } catch (IOException e1) {
            
                    }
            
                }

                try {
                    Files.writeString(AppConstants.LOG_FILE.toPath(), ping.getJsonObject().toString() +"\n" , StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                } catch (IOException e1) {
        
                }


                return ping;
            }
        };
     
        task.setOnSucceeded(onSucceeded);
        task.setOnFailed(onFailed);

    
        execService.submit(task);
            // String[] splitStr = javaOutputList.get(0).trim().split("\\s+");
            //Version jV = new Version(splitStr[1].replaceAll("/[^0-9.]/g", ""));
        

    }

    public static boolean sendKillSig(String jarName) {
        try {
          //  File logFile = new File("wmicTerminate-log.txt");
     
            //String[] wmicCmd = {"cmd", "/c", "wmic", "Path", "win32_process", "Where", "\"CommandLine", "Like", "'%" + jarName + "%'\"", "Call", "Terminate"};
            String execString = "kill $(ps -ef | grep -v grep | grep " + jarName + " | awk '{print $2}')";

            String[] cmd = new String[]{ "bash", "-c", execString};

            Process wmicProc = Runtime.getRuntime().exec(cmd);

            BufferedReader wmicStderr = new BufferedReader(new InputStreamReader(wmicProc.getErrorStream()));
            //String wmicerr = null;


         

            BufferedReader wmicStdInput = new BufferedReader(new InputStreamReader(wmicProc.getInputStream()));

           // String wmicInput = null;
            boolean gotInput = false;

            while ((wmicStdInput.readLine()) != null) {
            
            
                gotInput = true;
            }

       
            while ((wmicStderr.readLine()) != null) {

               // Files.writeString(logFile.toPath(), "\nwmic err: " + wmicerr, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                return false;
            }

            wmicProc.waitFor();

            if (gotInput) {
                return true;
            }

        } catch (Exception e) {
            return false;
        }
        return false;
    }

    public static boolean onlyZero(String str) {
        
        for (int i = 0 ; i < str.length() ; i++){
            String c = str.substring(i, i+1);
            if(!(c.equals("0") || c.equals("."))){
                return false;
            }
        }
        return true;
    }

    public static boolean isTextZero(String str){
        str = str.strip();
        
        if(str.length() == 0){
            return true;
        }

        int index = str.indexOf(".");

        String leftSide = index != -1 ? str.substring(0, index) : str;
        
        String rightSide = index != -1 ? str.substring(index + 1) : "";
        
        for (int i = 0 ; i < leftSide.length() ; i++){
            String c = leftSide.substring(i, i+1);
            if(!c.equals("0")){
                return false;
            }
        }

        for (int i = 0 ; i < rightSide.length() ; i++){
            String c = rightSide.substring(i, i+1);
            if(!c.equals("0")){
                return false;
            }
        }
        
        return true;
    }
    
    public static URL getLocation(final Class<?> c) {

        if (c == null) {
            return null; // could not load the class
        }
        // try the easy way first
        try {
            final URL codeSourceLocation = c.getProtectionDomain().getCodeSource().getLocation();
            if (codeSourceLocation != null) {
                return codeSourceLocation;
            }
        } catch (final SecurityException e) {
            // NB: Cannot access protection domain.
        } catch (final NullPointerException e) {
            // NB: Protection domain or code source is null.
        }

        // NB: The easy way failed, so we try the hard way. We ask for the class
        // itself as a resource, then strip the class's path from the URL string,
        // leaving the base path.
        // get the class's raw resource path
        final URL classResource = c.getResource(c.getSimpleName() + ".class");
        if (classResource == null) {
            return null; // cannot find class resource
        }
        final String url = classResource.toString();
        final String suffix = c.getCanonicalName().replace('.', '/') + ".class";
        if (!url.endsWith(suffix)) {
            return null; // weird URL
        }
        // strip the class's path from the URL string
        final String base = url.substring(0, url.length() - suffix.length());

        String path = base;

        // remove the "jar:" prefix and "!/" suffix, if present
        if (path.startsWith("jar:")) {
            path = path.substring(4, path.length() - 2);
        }

        try {
            return new URL(path);
        } catch (final MalformedURLException e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Converts the given {@link URL} to its corresponding {@link File}.
     * <p>
     * This method is similar to calling {@code new File(url.toURI())} except
     * that it also handles "jar:file:" URLs, returning the path to the JAR
     * file.
     * </p>
     *
     * @param url The URL to convert.
     * @return A file path suitable for use with e.g. {@link FileInputStream}
     * @throws IllegalArgumentException if the URL does not correspond to a
     * file.
     */
    public static File urlToFile(final URL url) {
        return url == null ? null : urlToFile(url.toString());
    }

    /**
     * Converts the given URL string to its corresponding {@link File}.
     *
     * @param url The URL to convert.
     * @return A file path suitable for use with e.g. {@link FileInputStream}
     * @throws IllegalArgumentException if the URL does not correspond to a
     * file.
     */
    public static File urlToFile(final String url) {
        String path = url;
        if (path.startsWith("jar:")) {
            // remove "jar:" prefix and "!/" suffix
            final int index = path.indexOf("!/");
            path = path.substring(4, index);
        }

        try {

            if (path.matches("file:[A-Za-z]:.*")) {
                path = "file:/" + path.substring(5);
            }
            return new File(new URL(path).toURI());
        } catch (final MalformedURLException e) {
            // NB: URL is not completely well-formed.

        } catch (final URISyntaxException e) {
            // NB: URL is not completely well-formed.
        }
        if (path.startsWith("file:")) {
            // pass through the URL as-is, minus "file:" prefix
            path = path.substring(5);
            return new File(path);
        }
        throw new IllegalArgumentException("Invalid URL: " + url);
    }

}
