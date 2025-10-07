package io.netnotes.engine.utils;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;

public class ErgoHelpers {
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
       
        /* String str = "" +
        "nanoErg:" + nanoErg + "\n" +
        "num:    " + num + "\n" + 
        "denom:  " + denom + "\n" +
        "x:      " + x + "\n" + 
        "y:      " + y + "\n";
        try {
            Files.writeString(ResourceFactory.LOG_FILE.toPath(),str, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {

        } */
        
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


    public static boolean checkErgoId(String tokenId){
        if(tokenId.length() == 64){
            String b58Test = tokenId.replaceAll("[^A-HJ-NP-Za-km-z0-9]", "");
                
            return b58Test.equals(tokenId);
        }
        return false;
    }
}
