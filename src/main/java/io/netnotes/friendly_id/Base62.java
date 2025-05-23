package io.netnotes.friendly_id;

import java.math.BigInteger;
import java.util.function.BiFunction;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

import static java.util.Objects.requireNonNull;

/**
 * Base62 encoder/decoder.
 * <p>
 * This is free and unencumbered public domain software
 * <p>
 * Source: https://github.com/opencoinage/opencoinage/blob/master/src/java/org/opencoinage/util/Base62.java
 */
class Base62 {

	private static final BigInteger BASE = BigInteger.valueOf(62);
	private static final String DIGITS = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";

	/**
	 * Encodes a number using Base62 encoding.
	 *
	 * @param number a positive integer
	 * @return a Base62 string
	 *
	 * @throws IllegalArgumentException if <code>number</code> is a negative integer
	 */
	static String encode(BigInteger number) {
		if (number.compareTo(BigInteger.ZERO) < 0) {
			throwIllegalArgumentException("number must not be negative");
		}
		StringBuilder result = new StringBuilder();
		while (number.compareTo(BigInteger.ZERO) > 0) {
			BigInteger[] divmod = number.divideAndRemainder(BASE);
			number = divmod[0];
			int digit = divmod[1].intValue();
			result.insert(0, DIGITS.charAt(digit));
		}
		return (result.length() == 0) ? DIGITS.substring(0, 1) : result.toString();
	}

	private static BigInteger throwIllegalArgumentException(String format, Object... args) {
		throw new IllegalArgumentException(String.format(format, args));
	}

	/**
	 * Decodes a string using Base62 encoding.
	 *
	 * @param string a Base62 string
	 * @return a positive integer
	 *
	 * @throws IllegalArgumentException if <code>string</code> is empty
	 */
	static BigInteger decode(final String string) {
		return decode(string, 128);
	}

	static BigInteger decode(final String string, int bitLimit) {
		requireNonNull(string, "Decoded string must not be null");
		if (string.length() == 0) {
			return throwIllegalArgumentException("String '%s' must not be empty", string);
		}

		if (!Pattern.matches("[" + DIGITS + "]*", string)) {
			throwIllegalArgumentException("String '%s' contains illegal characters, only '%s' are allowed", string, DIGITS);
		}

		return IntStream.range(0, string.length())
				.mapToObj(index -> BigInteger.valueOf(charAt.apply(string, index)).multiply(BASE.pow(index)))
				.reduce(BigInteger.ZERO, (acc, value) -> {
					BigInteger sum = acc.add(value);
					if (bitLimit > 0 && sum.bitLength() > bitLimit) {
						throwIllegalArgumentException("String '%s' contains more than 128bit information", string);
					}
					return sum;
				});

	}

	private static BiFunction<String, Integer, Integer> charAt = (string, index) ->
			DIGITS.indexOf(string.charAt(string.length() - index - 1));

}