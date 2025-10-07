package io.netnotes.engine.utils;

public class MathHelpers {
    public static int getAbsDifference(int num1, int num2 ){
        return Math.abs(Math.max(num1, num2) - Math.min(num1, num2));
    }
}
