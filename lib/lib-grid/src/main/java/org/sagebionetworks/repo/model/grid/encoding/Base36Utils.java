package org.sagebionetworks.repo.model.grid.encoding;

import org.sagebionetworks.repo.model.grid.patch.LogicalTimestamp;
import org.sagebionetworks.util.ValidateArgument;

/**
 * Utility class for Base36 encoding and decoding of node keys.
 *
 * Node keys in the indexed model format are encoded as "&lt;sid&gt;_&lt;seq&gt;" where:
 * - sid is the session index (index in clock table) encoded as Base36
 * - seq is the sequence number encoded as Base36
 */
public class Base36Utils {

	private static final String BASE36_CHARS = "0123456789abcdefghijklmnopqrstuvwxyz";

	/**
	 * Encode a long value to Base36 string.
	 *
	 * @param value the value to encode (must be non-negative)
	 * @return the Base36 encoded string
	 */
	public static String encodeBase36(long value) {
		if (value < 0) {
			throw new IllegalArgumentException("Value must be non-negative: " + value);
		}
		if (value == 0) {
			return "0";
		}

		StringBuilder sb = new StringBuilder();
		while (value > 0) {
			sb.insert(0, BASE36_CHARS.charAt((int) (value % 36)));
			value /= 36;
		}
		return sb.toString();
	}

	/**
	 * Decode a Base36 string to a long value.
	 *
	 * @param encoded the Base36 encoded string
	 * @return the decoded value
	 */
	public static long decodeBase36(String encoded) {
		ValidateArgument.required(encoded, "encoded");
		if (encoded.isEmpty()) {
			throw new IllegalArgumentException("Encoded string cannot be empty");
		}

		long result = 0;
		for (int i = 0; i < encoded.length(); i++) {
			char c = Character.toLowerCase(encoded.charAt(i));
			int digit = BASE36_CHARS.indexOf(c);
			if (digit < 0) {
				throw new IllegalArgumentException("Invalid Base36 character: " + c);
			}
			result = result * 36 + digit;
		}
		return result;
	}

	/**
	 * Encode a node key from session index and sequence number.
	 * Format: "&lt;sid&gt;_&lt;seq&gt;" where both are Base36 encoded.
	 *
	 * @param sessionIndex the session index in the clock table
	 * @param sequenceNumber the sequence number
	 * @return the encoded node key
	 */
	public static String encodeNodeKey(long sessionIndex, long sequenceNumber) {
		return encodeBase36(sessionIndex) + "_" + encodeBase36(sequenceNumber);
	}

	/**
	 * Decode a node key to session index and sequence number.
	 *
	 * @param nodeKey the encoded node key
	 * @return array of [sessionIndex, sequenceNumber]
	 */
	public static long[] decodeNodeKey(String nodeKey) {
		ValidateArgument.required(nodeKey, "nodeKey");

		int underscoreIndex = nodeKey.indexOf('_');
		if (underscoreIndex < 0) {
			throw new IllegalArgumentException("Invalid node key format, missing underscore: " + nodeKey);
		}

		String sidPart = nodeKey.substring(0, underscoreIndex);
		String seqPart = nodeKey.substring(underscoreIndex + 1);

		return new long[] {
			decodeBase36(sidPart),
			decodeBase36(seqPart)
		};
	}

	/**
	 * Create a node key from a LogicalTimestamp, given a clock table for session lookup.
	 *
	 * @param timestamp the timestamp to encode
	 * @param clockTable the clock table for looking up session index
	 * @return the encoded node key
	 */
	public static String encodeNodeKey(LogicalTimestamp timestamp, java.util.List<LogicalTimestamp> clockTable) {
		ValidateArgument.required(timestamp, "timestamp");
		ValidateArgument.required(clockTable, "clockTable");

		long sessionIndex = -1;
		for (int i = 0; i < clockTable.size(); i++) {
			if (clockTable.get(i).getReplicaId().equals(timestamp.getReplicaId())) {
				sessionIndex = i;
				break;
			}
		}
		if (sessionIndex == -1) {
			throw new IllegalArgumentException("Replica ID not found in clock table: " + timestamp.getReplicaId());
		}

		return encodeNodeKey(sessionIndex, timestamp.getSequenceNumber());
	}

	/**
	 * Decode a node key to a LogicalTimestamp, given a clock table for session lookup.
	 *
	 * @param nodeKey the encoded node key
	 * @param clockTable the clock table for looking up replica ID
	 * @return the decoded LogicalTimestamp
	 */
	public static LogicalTimestamp decodeNodeKeyToTimestamp(String nodeKey, java.util.List<LogicalTimestamp> clockTable) {
		ValidateArgument.required(nodeKey, "nodeKey");
		ValidateArgument.required(clockTable, "clockTable");

		long[] parts = decodeNodeKey(nodeKey);
		long sessionIndex = parts[0];
		long sequenceNumber = parts[1];

		if (sessionIndex >= clockTable.size()) {
			throw new IllegalArgumentException("Session index out of bounds: " + sessionIndex + " >= " + clockTable.size());
		}

		return new LogicalTimestamp()
			.setReplicaId(clockTable.get((int) sessionIndex).getReplicaId())
			.setSequenceNumber(sequenceNumber);
	}
}
