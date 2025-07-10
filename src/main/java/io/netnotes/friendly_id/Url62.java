package io.netnotes.friendly_id;

import java.math.BigInteger;
import java.util.UUID;
import java.util.stream.IntStream;

/**
 * Class to convert UUID to Url62 IDs
 */
class Url62 {

	/**
	 * Encode UUID to Url62 id
	 *
	 * @param uuid UUID to be encoded
	 * @return url62 encoded UUID
	 */
	static String encode(UUID uuid) {
		BigInteger pair = UuidConverter.toBigInteger(uuid);
		return Base62.encode(pair);
	}

	static IntStream encodeToIntStream(UUID uuid) {
		BigInteger pair = UuidConverter.toBigInteger(uuid);
		return Base62.encodeToIntStream(pair);
	}

	static char[] encodeToBase64(UUID uuid){
		BigInteger pair = UuidConverter.toBigInteger(uuid);
		return Base62.encodeToBase64(pair);
	}

	static BigInteger toBigInteger(String id){
		return Base62.decode(id);
	}

	/**
	 * Decode url62 id to UUID
	 *
	 * @param id url62 encoded id
	 * @return decoded UUID
	 */
	static UUID decode(String id) {
		
		return UuidConverter.toUuid(toBigInteger(id));
	}

	static UUID decodeChars(char[] id) {
		BigInteger decoded = Base62.decodeChars(id);
		return UuidConverter.toUuid(decoded);
	}

}
