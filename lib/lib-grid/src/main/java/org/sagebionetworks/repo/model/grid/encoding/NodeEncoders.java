package org.sagebionetworks.repo.model.grid.encoding;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Factory class for obtaining NodeEncoder instances for different encoding formats.
 * This class provides a centralized registry of encoders and ensures that the same
 * encoder instance is reused for each format (singleton pattern per format).
 */
public class NodeEncoders {

	private static final Map<EncodingFormat, NodeEncoder> encoders = new ConcurrentHashMap<>();

	static {
		// Register encoders
		register(EncodingFormat.INDEXED, new IndexedEncoder());
	}

	/**
	 * Get the encoder for the specified format.
	 *
	 * @param format the encoding format
	 * @return the encoder for the specified format
	 * @throws IllegalArgumentException if no encoder is registered for the format
	 */
	public static NodeEncoder getEncoder(EncodingFormat format) {
		NodeEncoder encoder = encoders.get(format);
		if (encoder == null) {
			throw new IllegalArgumentException("No encoder registered for format: " + format);
		}
		return encoder;
	}

	/**
	 * Register an encoder for a specific format.
	 * This allows custom encoders to be registered at runtime.
	 *
	 * @param format the encoding format
	 * @param encoder the encoder implementation
	 */
	public static void register(EncodingFormat format, NodeEncoder encoder) {
		encoders.put(format, encoder);
	}

	/**
	 * Check if an encoder is registered for the specified format.
	 *
	 * @param format the encoding format
	 * @return true if an encoder is registered, false otherwise
	 */
	public static boolean hasEncoder(EncodingFormat format) {
		return encoders.containsKey(format);
	}

	// Private constructor to prevent instantiation
	private NodeEncoders() {
		throw new AssertionError("Cannot instantiate NodeEncoders");
	}
}

