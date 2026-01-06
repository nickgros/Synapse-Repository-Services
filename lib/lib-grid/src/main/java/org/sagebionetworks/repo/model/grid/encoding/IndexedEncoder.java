package org.sagebionetworks.repo.model.grid.encoding;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Map;

import org.sagebionetworks.repo.model.grid.ClockTable;
import org.sagebionetworks.repo.model.grid.EncodingUtils;
import org.sagebionetworks.repo.model.grid.node.ArrayNode;
import org.sagebionetworks.repo.model.grid.node.ConstantNode;
import org.sagebionetworks.repo.model.grid.node.ObjectNode;
import org.sagebionetworks.repo.model.grid.node.RGANode;
import org.sagebionetworks.repo.model.grid.node.VectorNode;
import org.sagebionetworks.repo.model.grid.patch.ConType;
import org.sagebionetworks.repo.model.grid.patch.LogicalTimestamp;
import org.sagebionetworks.util.ValidateArgument;

import com.fasterxml.jackson.dataformat.cbor.CBORGenerator;

/**
 * Encoder for Indexed format.
 *
 * This format omits node IDs from the serialized output, making it more compact.
 * Node IDs are managed separately in an index structure. The actual node data
 * (values, types, etc.) are encoded in the same binary format as Binary Structural,
 * just without the ID prefix.
 *
 * This is the primary encoding format for JSON CRDT nodes.
 *
 * @see <a href="https://jsonjoy.com/specs/json-crdt/encoding/indexed-encoding">Indexed Encoding</a>
 */
public class IndexedEncoder implements NodeEncoder {

	public IndexedEncoder() {
	}


	public int writeNodeTypeAndLength(int nodeType, Long length, OutputStream out) throws IOException {
		ValidateArgument.required(out, "nodeType");
		ValidateArgument.required(out, "length");
		ValidateArgument.required(out, "out");

		int bytesWritten = 0;
		// Type occupies bits 7-5 (3 bits), length occupies bits 4-0 (5 bits)
		nodeType = (byte) (nodeType << 5);
		if (length < 31) {
			// When length e is less than 31, the first 3 bits of TL encode the node type c and the remaining 5 bits
			// encode the length e.
			nodeType |= length.byteValue();
			out.write(nodeType);
			bytesWritten += 1;
		} else {
			// When length is 31 or greater, the first byte encodes the node type c, and the remaining bits are set to 1.
			// The length is encoded as a vu57 integer.
			nodeType |= 0b0001_1111; // length extension indicator
			out.write(nodeType);
			bytesWritten += 1;
			byte[] encodedLength = EncodingUtils.encodeVu57(length);
			out.write(encodedLength);
			bytesWritten += encodedLength.length;
		}
		return bytesWritten;
	}

	@Override
	public int encode(ConstantNode node, ClockTable clockTable, OutputStream out) throws IOException {
		ValidateArgument.required(node, "node");
		ValidateArgument.required(node.getId(), "node.id");
		ValidateArgument.required(out, "out");

		int bytesWritten = 0;

		/**
		 * The con node encoding:
		 * [Optional: node ID]
		 * Type (000) and length byte
		 * Value (CBOR or timestamp)
		 *
		 *           Type (000)
		 *           |
		 *  ID       |  Length (0)
		 *  |        |  |
		 *  |        |  |     Value
		 *  |        |  |     |
		 * +========+---|----+========+
		 * |   id   |00000000|  CBOR  |   (with ID)
		 * +========+--------+========+
		 *
		 *          +---|----+========+
		 *          |00000000|  CBOR  |   (without ID)
		 *          +--------+========+
		 *
		 * When the node holds a logical timestamp:
		 *
		 *           Type (000)
		 *           |
		 *  ID       |  Length (1)
		 *  |        |  |
		 *  |        |  |     Timestamp
		 *  |        |  |     |
		 * +========+---|----+========+
		 * |   id   |00000001|   id   |   (with ID)
		 * +========+-------^+========+
		 *
		 *          +---|----+========+
		 *          |00000001|   id   |   (without ID)
		 *          +-------^+========+
		 */

		Long length = 0L;
		if (ConType.TIMESTAMP.equals(node.getConValue().getType())) {
			length = 1L;
		}
		bytesWritten += writeNodeTypeAndLength(0b000, length, out);

		// Write the value
		byte[] valueBytes = node.getConValue().toBinary(clockTable);
		out.write(valueBytes);
		bytesWritten += valueBytes.length;

		return bytesWritten;
	}

	@Override
	public int encode(ArrayNode node, ClockTable clockTable, OutputStream out) throws IOException {
		ValidateArgument.required(node, "node");
		ValidateArgument.required(node.getId(), "node.id");
		ValidateArgument.required(out, "out");

		int bytesWritten = 0;

		/**
		 * Array node encoding:
		 * [Optional: node ID]
		 * - Node type: 001 (1)
		 * - Contains: arrayId, dataId, referenceNodeId
		 */

		// TODO: Rewrite the RGANodes as chunks, the length can be derived from the number of chunks.
		// This would result in a smaller binary representation for arrays with many sequential elements.

		// Write node type (110) and length
		bytesWritten += writeNodeTypeAndLength(0b110, node.getLength(), out);

		/*
		 * Chunks are consecutively encoded one after another. A chunk begins with its ID, followed by b1vu56 integer,
		 *  where the flag is truthy if the chunk is a tombstone. The value of the b1vu56 integer is the span of the
		 *  chunk. If the chunk is not deleted (is not a tombstone) it is followed by contents of the chunk, a list of
		 *  CRDT nodes of length equal to the span of the chunk.
		 *
		 * For the indexed encoding format, only the chunk IDs are encoded, without the contents.
		 * Chunks contain node IDs, instead of inlined nodes.

			 Chunk 1
			 |                 Chunk 2 (tombstone)
			 |                 |
			 |                 |        Chunk 3
			 |                 |        |
			+========+========+========+========+========+........+
			| b1vu56 |   id   | b1vu56 | b1vu56 |   id   |        |
			+========+========+========+========+========+........+
		 */

		if (node.getElements() != null) {
			for (RGANode rgaNode : node.getElements()) {
				// Currently, each element is written as a chunk of span 1.
				final int chunkLength = 1;

				// 1. Write chunk ID (the position in the array) - always written
				// Use raw timestamp encoding for indexed format
				byte[] chunkId = clockTable.encodeTimestamp(rgaNode.getDataId());
				out.write(chunkId);
				bytesWritten += chunkId.length;

				// 2. Write b1vu56 header (deleted flag, span)
				byte[] chunkHeader = EncodingUtils.encodeB1u56(rgaNode.getIsDeleted() == true, chunkLength);
				out.write(chunkHeader);
				bytesWritten += chunkHeader.length;

				// 3. Write content IDs (the data at this position) - only if not deleted
				// Use raw timestamp encoding for indexed format
				if (rgaNode.getIsDeleted() != true && rgaNode.getDataId() != null) {
					byte[] contentId = clockTable.encodeTimestamp(rgaNode.getDataId());
					out.write(contentId);
					bytesWritten += contentId.length;
				}
			}
		}

		return bytesWritten;
	}

	@Override
	public int encode(ObjectNode node, ClockTable clockTable, OutputStream out) throws IOException {
		ValidateArgument.required(node, "node");
		ValidateArgument.required(node.getId(), "node.id");
		ValidateArgument.required(node.getValue(), "node.value");
		ValidateArgument.required(out, "out");

		int bytesWritten = 0;

		/**
		 * Object node encoding:
		 * [Optional: node ID]
		 * - Node type: 010 (2)
		 * - Contains: map of string keys to LogicalTimestamp values
		 */


		int nodeType = 0b010;
		Long length = (long) node.getValue().size();

		bytesWritten += writeNodeTypeAndLength(nodeType, length, out);


		for (Map.Entry<String, LogicalTimestamp> entry : node.getValue().entrySet()) {
			// Key-value pairs are encoded as a key (CBOR), followed by the ID of the nested node.
			// Use CBOR for other types
			try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
				try (CBORGenerator generator = CBORUtils.getCBORFactory().createGenerator(baos)) {
					generator.writeString(entry.getKey());
				}
				byte[] encodedKey = baos.toByteArray();
				out.write(encodedKey);
				bytesWritten += encodedKey.length;
			} catch (IOException e) {
				throw new RuntimeException("Failed to encode ObjectNode key to CBOR", e);
			}
			// Use raw timestamp encoding for indexed format (not difference encoding)
			byte[] encodedValue = clockTable.encodeTimestamp(entry.getValue());
			out.write(encodedValue);
			bytesWritten += encodedValue.length;
		}
		return bytesWritten;
	}

	@Override
	public int encode(VectorNode node, ClockTable clockTable, OutputStream out) throws IOException {
		ValidateArgument.required(node, "node");
		ValidateArgument.required(node.getId(), "node.id");
		ValidateArgument.required(out, "out");

		int bytesWritten = 0;

		/**
		 * Vector node encoding:
		 * [Optional: node ID]
		 * - Node type: 011 (3)
		 * - Length: the total number of slots (maxIndex + 1), not the number of entries
		 * - Contains: slot entries from index 0 to maxIndex, each either a node ID or 0x00 for absent
		 */

		int nodeType = 0b011;

		// Get the maximum index to determine the length of the vector
		// The length in the header must match the number of slots we write (maxIndex + 1)
		int maxIndex = node.getValues().keySet().stream().max(Integer::compareTo).orElse(-1);
		Long length = (long) (maxIndex + 1);
		bytesWritten += writeNodeTypeAndLength(nodeType, length, out);

		for (int i = 0; i <= maxIndex; i++) {
			if (!node.getValues().containsKey(i)) {
				// If the index is missing, add a 0-byte
				byte[] zeroByte = new byte[] { 0x00 };
				out.write(zeroByte);
				bytesWritten += zeroByte.length;
			} else {
				out.write(0x01); // Presence byte
				bytesWritten += 1;
				// Write the ID of the node - use raw timestamp encoding for indexed format
				byte[] nodeId = clockTable.encodeTimestamp(node.getValues().get(i).getId());
				out.write(nodeId);
				bytesWritten += nodeId.length;
			}
		}
		return bytesWritten;
	}
}

