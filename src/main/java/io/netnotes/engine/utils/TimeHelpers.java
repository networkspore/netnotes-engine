package io.netnotes.engine.utils;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;

import io.netnotes.noteBytes.processing.ByteDecoding;

public class TimeHelpers {
    
    public static byte[] getTimeStampBytes(){
        long timeStamp = System.currentTimeMillis();
        return ByteDecoding.longToBytesBigEndian(timeStamp);
    }


    public static long getNowEpochMillis(LocalDateTime now) {
        Instant instant = now.atZone(ZoneId.systemDefault()).toInstant();
        return instant.toEpochMilli();
    }

    public static String formatTimeAmount(long timeAmount) {
        long seconds = timeAmount / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;
        
        if (days > 0) {
            return String.format("%dd %dh", days, hours % 24);
        } else if (hours > 0) {
            return String.format("%dh %dm", hours, minutes % 60);
        } else if (minutes > 0) {
            return String.format("%dm %ds", minutes, seconds % 60);
        } else {
            return String.format("%ds", seconds);
        }
    }

    /**
     * Format timestamp for display
     */
    public static String formatDate(long timestamp) {
        return new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm")
            .format(new java.util.Date(timestamp));
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

    public static void timeDelay(int seconds){
        try {
            Thread.sleep(Duration.ofSeconds(seconds));
        } catch (InterruptedException e) {
            
        }
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
}
