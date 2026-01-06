package org.sagebionetworks.repo.model.grid.patch;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.sagebionetworks.repo.model.grid.ClockTable;
import org.sagebionetworks.repo.model.grid.encoding.CBORUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.dataformat.cbor.CBORGenerator;

public class ConValue {

	private final ConType type;
	/**
	 * an optional JSON/CBOR value, which is the value of the con node when it is
	 * not undefined and is not a timestamp. Implementation note: when the value is
	 * `undefined`, this will be a Java `null`. When the value is a JSON `null`,
	 * this will be a JSONObject.NULL.
	 */
	private final Object value;

	public ConValue(ConType type, Object value) {
		super();
		this.type = type;
		if (ConType.NULL.equals(type)) {
			this.value = JSONObject.NULL;
		} else if (ConType.UNDEFINED.equals(type)) {
			this.value = null;
		} else if (ConType.LONG.equals(type) && value instanceof Integer) {
			this.value = Long.valueOf((Integer) value);
		} else {
			this.value = value;
		}
	}

	/**
	 * Decode a ConValue from CBOR/binary format.
	 *
	 * @param in the input stream containing the encoded value
	 * @param clockTable the clock table for decoding timestamps
	 * @param isTimestamp true if the value is a logical timestamp (indicated by e=1 in node header)
	 * @return the decoded ConValue
	 */
	public static ConValue fromCbor(InputStream in, ClockTable clockTable, boolean isTimestamp) {
		// If the node header indicated this is a timestamp (e=1), decode it as such
		if (isTimestamp) {
			try {
				LogicalTimestamp ts = clockTable.decodeTimestamp(in);
				return new ConValue(ConType.TIMESTAMP, ts);
			} catch (IOException e) {
				throw new RuntimeException("Failed to decode timestamp from binary", e);
			}
		}

		// Wrap in BufferedInputStream to support mark/reset if needed
		if (!in.markSupported()) {
			in = new BufferedInputStream(in, 1);
		}

		// Check for CBOR undefined (0xF7) before parsing with Jackson
		// Jackson treats undefined as null, so we need to detect it manually
		try {
			in.mark(1);
			int firstByte = in.read();
			if (firstByte == 0xf7) {
				// CBOR undefined
				return new ConValue(ConType.UNDEFINED, null);
			}
			in.reset();
		} catch (IOException e) {
			throw new RuntimeException("Failed to read from stream", e);
		}

		// Use Jackson CBOR to decode the value
		JsonNode jsonNode;
		try {
			 jsonNode = CBORUtils.getCBORMapper().readTree(in);
		} catch (IOException e) {
			throw new RuntimeException("Failed to decode ConValue from CBOR", e);
		}

		if (jsonNode.isNull()) {
			return new ConValue(ConType.NULL, null);
		} else if (jsonNode.isBoolean()) {
			return new ConValue(ConType.BOOLEAN, jsonNode.asBoolean());
		} else if (jsonNode.isIntegralNumber()) {
			return new ConValue(ConType.LONG, jsonNode.asLong());
		} else if (jsonNode.isFloatingPointNumber()) {
			return new ConValue(ConType.DOUBLE, jsonNode.asDouble());
		} else if (jsonNode.isTextual()) {
			return new ConValue(ConType.STRING, jsonNode.asText());
		} else if (jsonNode.isArray()) {
			org.json.JSONArray jsonArray = new org.json.JSONArray();
			for (JsonNode element : jsonNode) {
				jsonArray.put(convertJsonNodeToOrgJsonCompatible(element));
			}
			return new ConValue(ConType.JSON_ARRAY, jsonArray);
		} else if (jsonNode.isObject()) {
			org.json.JSONObject jsonObject = new org.json.JSONObject();
			jsonNode.properties().forEach(entry -> {
				jsonObject.put(entry.getKey(), convertJsonNodeToOrgJsonCompatible(entry.getValue()));
			});
			return new ConValue(ConType.JSON_OBJECT, jsonObject);
		} else {
			throw new IllegalArgumentException("Unsupported CBOR node type: " + jsonNode.getNodeType());
		}
	}

	/**
	 * Convert a Jackson JsonNode to a Java object suitable for org.json types.
	 */
	private static Object convertJsonNodeToOrgJsonCompatible(JsonNode node) {
		if (node.isNull()) {
			return org.json.JSONObject.NULL;
		} else if (node.isBoolean()) {
			return node.asBoolean();
		} else if (node.isIntegralNumber()) {
			// NOTE: `org.json` creates `Integer` values by default (though Long is likely more correct for JavaScript
			// integers). `asInt` here ensures behavior matches (and equality checks work as expected).
			return node.asInt();
		} else if (node.isFloatingPointNumber()) {
			return node.asDouble();
		} else if (node.isTextual()) {
			return node.asText();
		} else if (node.isArray()) {
			org.json.JSONArray arr = new org.json.JSONArray();
			for (JsonNode element : node) {
				arr.put(convertJsonNodeToOrgJsonCompatible(element));
			}
			return arr;
		} else if (node.isObject()) {
			org.json.JSONObject obj = new org.json.JSONObject();
			node.properties().forEach(entry -> {
				obj.put(entry.getKey(), convertJsonNodeToOrgJsonCompatible(entry.getValue()));
			});
			return obj;
		}
		return node.asText();
	}

	public ConType getType() {
		return type;
	}

	public Object getValue() {
		return value;
	}

	/**
	 * If true, this value is an undefined literal value.
	 */
	public boolean isUndefined() {
		return ConType.UNDEFINED.equals(type);
	}

	@Override
	public int hashCode() {
		return Objects.hash(type, value);
	}

	private static boolean valueEquals(Object value, Object otherValue) {
		// For JSONObject and JSONArray, `equals` does not work; use `similar` to do a
		// deep comparison.
		if (value instanceof JSONObject && otherValue instanceof JSONObject) {
			return ((JSONObject) value).similar(otherValue);
		}
		if (value instanceof JSONArray && otherValue instanceof JSONArray) {
			return ((JSONArray) value).similar(otherValue);
		}

		return Objects.equals(value, otherValue);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		ConValue other = (ConValue) obj;
		Object otherValue = ((ConValue) obj).value;
		return type == other.type && valueEquals(value, otherValue);
	}

	@Override
	public String toString() {
		return "ConValue [type=" + type + ", value=" + value + "]";
	}

	/**
	 * Returns the 'partial' compact serialization of this ConValue (the last 1-2
	 * parts).
	 *
	 * This omits the non-value-related information that is stored in a Constant
	 * node (i.e. prefix indicating that the node is a constant, and ID of the
	 * node).
	 * 
	 * @return
	 */
	public JSONArray toCompact() {
		JSONArray compact = new JSONArray();

		// UNDEFINED and TIMESTAMP always have first element 0
		if (ConType.UNDEFINED.equals(type) || ConType.TIMESTAMP.equals(type)) {
			compact.put(0);
		}

		// Append the value
		Object value = this.value;
		if (ConType.UNDEFINED.equals(type)) {
			// Value for UNDEFINED is always 0
			value = 0;
		} else if (ConType.TIMESTAMP.equals(type)) {
			// Serialize the TIMESTAMP to a JSONArray
			LogicalTimestamp ts = (LogicalTimestamp) this.value;
			JSONArray timestamp = new JSONArray();
			timestamp.put(ts.getReplicaId());
			timestamp.put(ts.getSequenceNumber());
			value = timestamp;
		} else if (ConType.NULL.equals(type) || this.value == null) {
			value = JSONObject.NULL;
		}
		compact.put(value);
		return compact;
	}

	/**
	 * Attempt to read the provided string as JSON value. If value is not a JSON
	 * value, it will be treated as a string.
	 * 
	 * @param value
	 * @return
	 */
	public static ConValue fromString(String value) {
		if (value == null) {
			return new ConValue(ConType.NULL, value);
		}
		JSONTokener t = new JSONTokener(value);
		char n = t.nextClean();
		if (0 == n) {
			return new ConValue(ConType.STRING, value);
		}
		t.back();
		try {
			Object o = t.nextValue();
			n = t.nextClean();
			String oString = o.toString();
			if (n == oString.charAt(oString.length() - 1)) {
				n = t.nextClean();
			}

			if (0 == n) {
				// at eof so this is a JSON type.
				return new ConValue(ConType.fromValue(o), o);
			} else {
				// Not JSON type so it is a string.
				return new ConValue(ConType.STRING, value);
			}
		} catch (JSONException e) {
			// Not JSON type so it is a string.
			return new ConValue(ConType.STRING, value);
		}
	}

	public static ConValue fromCompact(JSONArray json) {
		if (json.length() == 2) {
			// It is either timestamp or undefined
			if (json.optInt(0) == 0 && json.optJSONArray(1) != null) {
				JSONArray timestampVal = json.getJSONArray(1);
				LogicalTimestamp ts = new LogicalTimestamp().setReplicaId(timestampVal.getLong(0))
						.setSequenceNumber(timestampVal.getLong(1));
				return new ConValue(ConType.TIMESTAMP, ts);
			} else if (json.optInt(0) == 0 && json.optInt(1) == 0) {
				return new ConValue(ConType.UNDEFINED, null);

			} else {
				throw new IllegalArgumentException("Invalid compact ConValue: " + json);
			}
		} else if (json.length() == 1) {
			// could be null or other value
			return new ConValue(ConType.fromValue(json.get(0)), json.get(0));
		}
		throw new IllegalArgumentException("Invalid compact ConValue: " + json);
	}

	/**
	 * Convert this ConValue to binary representation using CBOR encoding.
	 * For TIMESTAMP types, the LogicalTimestamp's binary format is used.
	 * For other types, CBOR encoding is used.
	 *
	 * @return the binary representation of this value
	 */
	public byte[] toBinary(ClockTable clockTable) {
		try {
			if (ConType.TIMESTAMP.equals(type)) {
				// For timestamps, use raw encoding (not difference encoding) for indexed format
				return clockTable.encodeTimestamp((LogicalTimestamp) value);
			} else if (ConType.UNDEFINED.equals(type)) {
				// CBOR undefined is encoded as 0xf7
				return new byte[] { (byte) 0xf7 };
			} else {
				// Use CBOR for other types
				ByteArrayOutputStream baos = new ByteArrayOutputStream();
				CBORGenerator generator = CBORUtils.getCBORFactory().createGenerator(baos);

				if (ConType.NULL.equals(type) || value == JSONObject.NULL || value == null) {
					generator.writeNull();
				} else if (value instanceof Boolean) {
					generator.writeBoolean((Boolean) value);
				} else if (value instanceof Long) {
					generator.writeNumber((Long) value);
				} else if (value instanceof Integer) {
					generator.writeNumber((Integer) value);
				} else if (value instanceof Double) {
					generator.writeNumber((Double) value);
				} else if (value instanceof String) {
					generator.writeString((String) value);
				} else if (value instanceof JSONArray) {
					// Convert JSONArray to CBOR array
					JSONArray arr = (JSONArray) value;
					generator.writeStartArray(arr.length());
					for (int i = 0; i < arr.length(); i++) {
						writeJsonValueToCbor(generator, arr.get(i));
					}
					generator.writeEndArray();
				} else if (value instanceof JSONObject) {
					// Convert JSONObject to CBOR map
					JSONObject obj = (JSONObject) value;
					generator.writeStartObject();
					for (String key : obj.keySet()) {
						generator.writeFieldName(key);
						writeJsonValueToCbor(generator, obj.get(key));
					}
					generator.writeEndObject();
				} else {
					// Fallback: write as string
					generator.writeString(value.toString());
				}

				generator.close();
				return baos.toByteArray();
			}
		} catch (IOException e) {
			throw new RuntimeException("Failed to encode ConValue to binary", e);
		}
	}

	/**
	 * Helper method to write a JSON value to CBOR.
	 */
	private static void writeJsonValueToCbor(CBORGenerator generator, Object value) throws IOException {
		if (value == null || value == JSONObject.NULL) {
			generator.writeNull();
		} else if (value instanceof Boolean) {
			generator.writeBoolean((Boolean) value);
		} else if (value instanceof Long) {
			generator.writeNumber((Long) value);
		} else if (value instanceof Integer) {
			generator.writeNumber((Integer) value);
		} else if (value instanceof Double) {
			generator.writeNumber((Double) value);
		} else if (value instanceof String) {
			generator.writeString((String) value);
		} else if (value instanceof JSONArray) {
			JSONArray arr = (JSONArray) value;
			generator.writeStartArray(arr.length());
			for (int i = 0; i < arr.length(); i++) {
				writeJsonValueToCbor(generator, arr.get(i));
			}
			generator.writeEndArray();
		} else if (value instanceof JSONObject) {
			JSONObject obj = (JSONObject) value;
			generator.writeStartObject();
			for (String key : obj.keySet()) {
				generator.writeFieldName(key);
				writeJsonValueToCbor(generator, obj.get(key));
			}
			generator.writeEndObject();
		} else {
			generator.writeString(value.toString());
		}
	}

}
