package io.netnotes.engine.utils;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.math.RoundingMode;

public class MathHelpers {

    public final static int DEFAULT_DIVISION_SCALE = 18;
    public final static BigDecimal BigPi = new BigDecimal("3.14159265358979323846264338327950288419716939937510");
    public final static BigDecimal ONE_HALF = new BigDecimal("0.5");
    public final static BigDecimal ONE_SIXTH = new BigDecimal("0.16666666666666666666666666666666666666666666666667");
    public final static BigDecimal ONE_THIRD = new BigDecimal("0.33333333333333333333333333333333333333333333333333");
    
    public final static BigDecimal SIX = new BigDecimal("6");
    public final static BigDecimal EIGHT = new BigDecimal("8");
    public final static BigDecimal TWELVE = new BigDecimal("12");
    public final static BigDecimal TWENTY_FOUR = new BigDecimal("24");
    public final static BigDecimal THIRTY = new BigDecimal("30");
    public final static BigDecimal FOURTY_EIGHT = new BigDecimal("48");

    /**
     * Compute sin(x) using BigDecimal and the requested MathContext (precision + rounding).
     *
     * @param x  argument in radians
     * @param mc MathContext controlling precision
     * @return sin(x) rounded to mc
     */
    public static BigDecimal sin(BigDecimal x, MathContext mc) {
        if (mc.getPrecision() <= 0) {
            throw new IllegalArgumentException("MathContext precision must be > 0");
        }

        // Normalize to [-pi, pi]
        BigDecimal pi = pi(mc);
        BigDecimal twoPi = pi.multiply(BigDecimal.valueOf(2), mc);
        BigDecimal xn = reduceToRange(x, twoPi, mc); // in [0, 2π)
        // shift to [-π, π]
        if (xn.compareTo(pi) > 0) {
            xn = xn.subtract(twoPi, mc);
        }

        // For faster convergence, further reduce to [-π/2, π/2] using identities
        boolean negate = false;
        if (xn.compareTo(pi.divide(BigDecimal.valueOf(2), mc)) > 0) {
            // sin(x) = sin(π - x)
            xn = pi.subtract(xn, mc);
        } else if (xn.compareTo(pi.divide(BigDecimal.valueOf(-2), mc)) < 0) {
            // xn < -π/2; sin(x) = -sin(-x) and we can use symmetry
            xn = xn.negate();
            negate = true;
            if (xn.compareTo(pi.divide(BigDecimal.valueOf(2), mc)) > 0) {
                xn = pi.subtract(xn, mc);
            }
        }

        BigDecimal result = sinTaylor(xn, mc);
        if (negate) result = result.negate();

        return result.round(mc);
    }

    // --- Taylor series around 0, assumes |x| <= pi/2 for good convergence ---
    public static BigDecimal sinTaylor(BigDecimal x, MathContext mc) {
        int precision = mc.getPrecision();
        // safety scale: use extra guard digits for intermediate computations
        int extraDigits = 6;
        MathContext mcWork = new MathContext(precision + extraDigits, mc.getRoundingMode());

        BigDecimal term = x; // first term x^1 / 1!
        BigDecimal sum = term;
        BigDecimal x2 = x.multiply(x, mcWork);

        // We'll iterate n = 1.. until |term| < 10^( -precision-2 )
        BigDecimal tolerance = BigDecimal.ONE.scaleByPowerOfTen(-(precision + 2));

        // recurrence: term_{k+1} = term_k * ( - x^2 ) / ( (2k)*(2k+1) )
        // starting with k=1 term is x  (which corresponds to 1st term)
        int k = 1;
        while (true) {
            // divisor = (2k)*(2k+1)
            BigInteger d1 = BigInteger.valueOf(2L * k);
            BigInteger d2 = BigInteger.valueOf(2L * k + 1L);
            BigDecimal divisor = new BigDecimal(d1.multiply(d2));

            term = term.multiply(x2, mcWork).divide(divisor, mcWork).negate();
            sum = sum.add(term, mcWork);

            if (term.abs().compareTo(tolerance) <= 0) {
                break;
            }
            // safety: prevent infinite loop
            if (k > 5000) {
                throw new ArithmeticException("sinTaylor failed to converge after many iterations");
            }
            k++;
        }

        return sum.round(mc);
    }


     // Reduce x modulo mod (0 <= result < mod). Uses integer quotient extraction.
    public static BigDecimal reduceToRange(BigDecimal x, BigDecimal mod, MathContext mc) {
        if (mod.signum() <= 0) throw new IllegalArgumentException("mod must be positive");
        // Scale of division: choose sufficient scale to extract integer part reliably
        int scale = Math.max(mc.getPrecision(), 32);
        BigDecimal quotient = x.divide(mod, scale, RoundingMode.DOWN);
        BigInteger qInt = quotient.toBigInteger(); // floor for positive, truncated toward zero for negative
        // For negative numbers, ensure floor behavior
        if (x.signum() < 0 && !quotient.stripTrailingZeros().equals(new BigDecimal(qInt))) {
            // If x negative and quotient had fractional part, subtract 1 to get floor
            qInt = qInt.subtract(BigInteger.ONE);
        }
        BigDecimal res = x.subtract(mod.multiply(new BigDecimal(qInt), mc), mc);
        // res should be in [0, mod)
        // normalize if negative due to rounding issues
        if (res.signum() < 0) {
            res = res.add(mod, mc);
        }
        // final rounding
        return res.round(mc);
    }

    public static BigDecimal pi(MathContext mc) {
        int precision = mc.getPrecision();
        if (precision <= 0) {
            throw new IllegalArgumentException("MathContext precision must be > 0");
        }

        // If we have enough precision from the constant, just use it
        if (precision <= BigPi.precision()) {
            return BigPi.round(mc);
        }

        // Otherwise compute π dynamically for higher precision
        return computePi(mc);
    }

    private static BigDecimal computePi(MathContext mc) {
        int precision = mc.getPrecision();
        int extra = 10;
        MathContext mcWork = new MathContext(precision + extra, mc.getRoundingMode());

        BigDecimal arctan1_5 = arctan(BigDecimal.ONE.divide(BigDecimal.valueOf(5), mcWork), mcWork);
        BigDecimal arctan1_239 = arctan(BigDecimal.ONE.divide(BigDecimal.valueOf(239), mcWork), mcWork);

        BigDecimal piOver4 = arctan1_5.multiply(BigDecimal.valueOf(4), mcWork)
                .subtract(arctan1_239, mcWork);

        return piOver4.multiply(BigDecimal.valueOf(4), mcWork).round(mc);
    }

     private static BigDecimal arctan(BigDecimal z, MathContext mc) {
        BigDecimal zPower = z;
        BigDecimal sum = z;
        BigDecimal z2 = z.multiply(z, mc);
        int precision = mc.getPrecision();
        BigDecimal tolerance = BigDecimal.ONE.scaleByPowerOfTen(-(precision + 4));
        boolean negative = false;
        for (int n = 1; ; n++) {
            zPower = zPower.multiply(z2, mc);
            int denom = 2 * n + 1;
            BigDecimal term = zPower.divide(BigDecimal.valueOf(denom), mc);
            if (negative) sum = sum.subtract(term, mc);
            else sum = sum.add(term, mc);
            negative = !negative;

            if (term.abs().compareTo(tolerance) <= 0) break;
            if (n > 20000) throw new ArithmeticException("arctan failed to converge");
        }
        return sum;
    }

     public static int nextPowerOfTwo(int n) {
        n--;
        n |= n >> 1;
        n |= n >> 2;
        n |= n >> 4;
        n |= n >> 8;
        n |= n >> 16;
        return n + 1;
    }


    public static BigDecimal inv(BigDecimal x, MathContext mc) {
        return BigDecimal.ONE.divide(x, mc);
    }

    public static int getAbsDifference(int num1, int num2 ){
        return Math.abs(Math.max(num1, num2) - Math.min(num1, num2));
    }

     public static BigDecimal clampDimension(BigDecimal value, int dimension){
        return value.max(BigDecimal.ZERO).min( BigDecimal.valueOf(dimension));
    }

    public static BigDecimal clamp(BigDecimal value, BigDecimal min, BigDecimal max){
        return value.max(min).min(max);
    }

    public static BigDecimal clampMin(BigDecimal value, BigDecimal min){
        return value.max(min);
    }

    public static BigDecimal clampMax(BigDecimal value, BigDecimal max){
        return value.min(max);
    }

    public static BigDecimal divideNearestNeighbor(BigDecimal numerator, BigDecimal demoninator){
        int scale = Math.max(DEFAULT_DIVISION_SCALE, (Math.max(numerator.scale(), demoninator.scale())));
        return numerator.divide(demoninator, scale, RoundingMode.HALF_EVEN);
    }

    public static BigDecimal divideMC(BigDecimal numerator, BigDecimal demoninator, MathContext mc){
        return numerator.divide(demoninator, mc);
    }

    public static int divideNearestNeighborToInt(BigDecimal numerator, BigDecimal demoninator){
        return numerator.divide(demoninator, 0, RoundingMode.HALF_EVEN).intValue();
    }

    public static BigDecimal divideNearestNeighbor(long numerator, long demoninator){
        return BigDecimal.valueOf(numerator).divide(BigDecimal.valueOf(demoninator), 10, RoundingMode.HALF_EVEN);
    }

    public static int divideNearestNeighborToInt(long numerator, long demoninator){
        return BigDecimal.valueOf(numerator).divide(BigDecimal.valueOf(demoninator), 0, RoundingMode.HALF_EVEN).intValue();
    }


    public static BigDecimal multiplyLong(BigDecimal multiplicand, long multiplier){
        return BigDecimal.valueOf(multiplier).multiply(multiplicand);
    }

    public static int multiplyToInt(BigDecimal multiplicand, int multiplier){
        return BigDecimal.valueOf(multiplier).multiply(multiplicand).setScale(0, RoundingMode.HALF_EVEN).intValue();
    }
}
