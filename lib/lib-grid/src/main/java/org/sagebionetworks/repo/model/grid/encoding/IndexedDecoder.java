package org.sagebionetworks.repo.model.grid.encoding;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.sagebionetworks.repo.model.grid.ClockTable;
import org.sagebionetworks.repo.model.grid.EncodingUtils;
import org.sagebionetworks.repo.model.grid.node.ArrayNode;
import org.sagebionetworks.repo.model.grid.node.ConstantNode;
import org.sagebionetworks.repo.model.grid.node.ObjectNode;
import org.sagebionetworks.repo.model.grid.node.RGANode;
import org.sagebionetworks.repo.model.grid.node.VectorNode;
import org.sagebionetworks.repo.model.grid.patch.ConValue;
import org.sagebionetworks.repo.model.grid.patch.LogicalTimestamp;
import org.sagebionetworks.util.ValidateArgument;

/**
 * Decoder for Indexed format.
 * This format omits node IDs from the serialized output, making it more compact.
 * Node IDs are managed separately in an index structure. The actual node data
 * (values, types, etc.) are decoded from the same binary format as Binary Structural,
 * just without the ID prefix.
 * This is the primary decoding format for JSON CRDT nodes.
 *
 * @see <a href="https://jsonjoy.com/specs/json-crdt/encoding/indexed-encoding">Indexed Encoding</a>
 */
public class IndexedDecoder implements NodeDecoder {

	public IndexedDecoder() {
	}

	@Override
	public TypeAndLength readNodeTypeAndLength(InputStream in) throws IOException {
		ValidateArgument.required(in, "in");

		int firstByte = in.read();
		if (firstByte == -1) {
			throw new IOException("Unexpected end of stream while reading node type and length");
		}

		// Type occupies bits 7-5 (3 bits), length occupies bits 4-0 (5 bits)
		int nodeType = (firstByte >> 5) & 0x07;

		// Extract length from lower 5 bits
		int lengthPart = firstByte & 0x1F;

		long length;
		if (lengthPart < 31) {
			// Length is directly encoded in the lower 5 bits
			length = lengthPart;
		} else {
			// Length extension: read vu57 for actual length
			length = EncodingUtils.decodeVu57(in);
		}

		return new TypeAndLength(nodeType, length);
	}

	@Override
	public ConstantNode decodeConstantNode(LogicalTimestamp nodeId, ClockTable clockTable, InputStream in) throws IOException {
		ValidateArgument.required(nodeId, "nodeId");
		ValidateArgument.required(in, "in");

		TypeAndLength typeAndLength = readNodeTypeAndLength(in);
		if (typeAndLength.getNodeType() != 0) {
			throw new IllegalArgumentException("Expected ConstantNode type (0) but got: " + typeAndLength.getNodeType());
		}

		return decodeConstantNodeBody(nodeId, typeAndLength.getLength(), clockTable, in);
	}

	@Override
	public ConstantNode decodeConstantNodeBody(LogicalTimestamp nodeId, long length, ClockTable clockTable, InputStream in) throws IOException {
		ValidateArgument.required(nodeId, "nodeId");
		ValidateArgument.required(in, "in");

		boolean isTimestamp = length == 1;
		ConValue value = ConValue.fromCbor(in, clockTable, isTimestamp);

		return new ConstantNode()
			.setId(nodeId)
			.setValue(value);
	}


	@Override
	public ArrayNode decodeArrayNode(LogicalTimestamp nodeId, ClockTable clockTable, InputStream in) throws IOException {
		ValidateArgument.required(nodeId, "nodeId");
		ValidateArgument.required(in, "in");

		TypeAndLength typeAndLength = readNodeTypeAndLength(in);
		if (typeAndLength.getNodeType() != 6) {
			throw new IllegalArgumentException("Expected ArrayNode type (6) but got: " + typeAndLength.getNodeType());
		}

		return decodeArrayNodeBody(nodeId, typeAndLength.getLength(), clockTable, in);
	}

	@Override
	public ArrayNode decodeArrayNodeBody(LogicalTimestamp nodeId, long length, ClockTable clockTable, InputStream in) throws IOException {
		ValidateArgument.required(nodeId, "nodeId");
		ValidateArgument.required(in, "in");

		List<RGANode> elements = new ArrayList<>();

		// Read chunks
		long elementsRead = 0;
		while (elementsRead < length) {
			// 1. Read chunk ID (the position in the array) - use raw timestamp decoding for indexed format
			LogicalTimestamp chunkId = clockTable.decodeTimestamp(in);

			// 2. Read chunk header: b1u56 (flag=isDeleted, value=chunkLength)
			EncodingUtils.B1u56Result chunkHeader = EncodingUtils.decodeB1u56(in);
			boolean isDeleted = chunkHeader.getFlag();
			long chunkLength = chunkHeader.getValue();

			// 3. Read content IDs for each element in the chunk
			for (int i = 0; i < chunkLength; i++) {
				RGANode rgaNode = new RGANode()
					.setNodeId(nodeId)  // parent array ID
					.setDataId(chunkId)    // chunk/position ID
					.setIsDeleted(isDeleted);

				if (!isDeleted) {
					// Read the content/data ID for non-deleted elements - use raw timestamp decoding
					LogicalTimestamp dataId = clockTable.decodeTimestamp(in);
					rgaNode.setDataId(dataId);
				}

				elements.add(rgaNode);
				elementsRead++;
			}
		}

		return new ArrayNode()
			.setId(nodeId)
			.setLength(length)
			.setElements(elements);
	}

	@Override
	public ObjectNode decodeObjectNode(LogicalTimestamp nodeId, ClockTable clockTable, InputStream in) throws IOException {
		ValidateArgument.required(nodeId, "nodeId");
		ValidateArgument.required(in, "in");

		TypeAndLength typeAndLength = readNodeTypeAndLength(in);
		if (typeAndLength.getNodeType() != 2) {
			throw new IllegalArgumentException("Expected ObjectNode type (2) but got: " + typeAndLength.getNodeType());
		}

		return decodeObjectNodeBody(nodeId, typeAndLength.getLength(), clockTable, in);
	}

	@Override
	public ObjectNode decodeObjectNodeBody(LogicalTimestamp nodeId, long length, ClockTable clockTable, InputStream in) throws IOException {
		ValidateArgument.required(nodeId, "nodeId");
		ValidateArgument.required(in, "in");

		Map<String, LogicalTimestamp> map = new LinkedHashMap<>();

		for (long i = 0; i < length; i++) {
			// Read CBOR-encoded key (text string)
			String key = readCborTextString(in);

			// Read timestamp value - use raw timestamp decoding for indexed format
			LogicalTimestamp value = clockTable.decodeTimestamp(in);

			map.put(key, value);
		}

		return new ObjectNode()
			.setId(nodeId)
			.setValue(map);
	}

	/**
	 * Read a CBOR text string from the input stream.
	 * This is a low-level method that reads exactly the bytes needed for one text string,
	 * without buffering extra data.
	 */
	private String readCborTextString(InputStream in) throws IOException {
		int firstByte = in.read();
		if (firstByte == -1) {
			throw new IOException("Unexpected end of stream while reading CBOR text string");
		}

		// Major type is in bits 7-5, additional info is in bits 4-0
		int majorType = (firstByte >> 5) & 0x07;
		int additionalInfo = firstByte & 0x1F;

		if (majorType != 3) {
			throw new IOException("Expected CBOR text string (major type 3) but got major type: " + majorType);
		}

		// Determine the length
		long length;
		if (additionalInfo < 24) {
			// Length is directly encoded
			length = additionalInfo;
		} else if (additionalInfo == 24) {
			// 1-byte length follows
			int len = in.read();
			if (len == -1) {
				throw new IOException("Unexpected end of stream while reading CBOR text string length");
			}
			length = len & 0xFF;
		} else if (additionalInfo == 25) {
			// 2-byte length follows (big endian)
			int b1 = in.read();
			int b2 = in.read();
			if (b1 == -1 || b2 == -1) {
				throw new IOException("Unexpected end of stream while reading CBOR text string length");
			}
			length = ((b1 & 0xFF) << 8) | (b2 & 0xFF);
		} else if (additionalInfo == 26) {
			// 4-byte length follows (big endian)
			int b1 = in.read();
			int b2 = in.read();
			int b3 = in.read();
			int b4 = in.read();
			if (b1 == -1 || b2 == -1 || b3 == -1 || b4 == -1) {
				throw new IOException("Unexpected end of stream while reading CBOR text string length");
			}
			length = ((long)(b1 & 0xFF) << 24) | ((b2 & 0xFF) << 16) | ((b3 & 0xFF) << 8) | (b4 & 0xFF);
		} else if (additionalInfo == 27) {
			// 8-byte length follows (big endian)
			long len = 0;
			for (int i = 0; i < 8; i++) {
				int b = in.read();
				if (b == -1) {
					throw new IOException("Unexpected end of stream while reading CBOR text string length");
				}
				len = (len << 8) | (b & 0xFF);
			}
			length = len;
		} else {
			throw new IOException("Invalid CBOR text string additional info: " + additionalInfo);
		}

		// Read the string bytes
		byte[] stringBytes = new byte[(int) length];
		int bytesRead = 0;
		while (bytesRead < length) {
			int read = in.read(stringBytes, bytesRead, (int) length - bytesRead);
			if (read == -1) {
				throw new IOException("Unexpected end of stream while reading CBOR text string content");
			}
			bytesRead += read;
		}

		return new String(stringBytes, java.nio.charset.StandardCharsets.UTF_8);
	}

	@Override
	public VectorNode decodeVectorNode(LogicalTimestamp nodeId, ClockTable clockTable, InputStream in) throws IOException {
		ValidateArgument.required(nodeId, "nodeId");
		ValidateArgument.required(in, "in");

		TypeAndLength typeAndLength = readNodeTypeAndLength(in);
		if (typeAndLength.getNodeType() != 3) {
			throw new IllegalArgumentException("Expected VectorNode type (3) but got: " + typeAndLength.getNodeType());
		}

		return decodeVectorNodeBody(nodeId, typeAndLength.getLength(), clockTable, in);
	}

	@Override
	public VectorNode decodeVectorNodeBody(LogicalTimestamp nodeId, long length, ClockTable clockTable, InputStream in) throws IOException {
		ValidateArgument.required(nodeId, "nodeId");
		ValidateArgument.required(in, "in");

		Map<Integer, ConstantNode> values = new LinkedHashMap<>();

		// The encoder writes either a 0x00 byte for missing entries or 0x01 followed by the full timestamp for present
		// entries.
		// https://github.com/streamich/json-joy/issues/976
		for (int index = 0; index < length; index++) {
			// Peek at the first byte to see if it's a zero (missing entry) or a timestamp
			int firstByte = in.read();
			if (firstByte == -1) {
				throw new IOException("Unexpected end of stream while reading VectorNode entries");
			}

			if (firstByte != 0x00) {
				// Non-zero byte - this is the start of a timestamp
				// Put the byte back and decode the full timestamp - use raw timestamp decoding for indexed format
				java.io.ByteArrayInputStream tempIn = new java.io.ByteArrayInputStream(new byte[]{(byte) firstByte});
				java.io.SequenceInputStream combinedIn = new java.io.SequenceInputStream(tempIn, in);
				LogicalTimestamp entryId = clockTable.decodeTimestamp(combinedIn);

				// Create a ConstantNode for this entry
				// Note: The actual value of the ConstantNode is stored elsewhere in the indexed format
				// Here we only have the ID reference
				ConstantNode constantNode = new ConstantNode().setId(entryId);
				values.put(index, constantNode);
			}
		}

		return new VectorNode()
			.setId(nodeId)
			.setValues(values);
	}
}
