package org.sagebionetworks.repo.model.grid.encoding;

import java.io.IOException;
import java.io.InputStream;

import org.sagebionetworks.repo.model.grid.ClockTable;
import org.sagebionetworks.repo.model.grid.node.ArrayNode;
import org.sagebionetworks.repo.model.grid.node.ConstantNode;
import org.sagebionetworks.repo.model.grid.node.Node;
import org.sagebionetworks.repo.model.grid.node.ObjectNode;
import org.sagebionetworks.repo.model.grid.node.VectorNode;

/**
 * Interface for decoding nodes from various serialization formats.
 * Implementations should handle format-specific decoding logic for each node type.
 * Decoding options (such as whether node IDs are included) are determined internally
 * by each decoder implementation based on the format requirements.
 *
 * Supported formats include:
 * - Binary Structural (binary format with node IDs)
 * - Indexed Encoding (binary format without node IDs for compact representation)
 */
public interface NodeDecoder {

	/**
	 * Decode a ConstantNode from the input stream.
	 *
	 * @param nodeId the ID of the node being decoded (provided externally for indexed format)
	 * @param clockTable the clock table for timestamp decoding
	 * @param in the input stream
	 * @return the decoded ConstantNode
	 * @throws IOException if an I/O error occurs
	 */
	ConstantNode decodeConstantNode(org.sagebionetworks.repo.model.grid.patch.LogicalTimestamp nodeId, ClockTable clockTable, InputStream in) throws IOException;

	/**
	 * Decode an ArrayNode from the input stream.
	 *
	 * @param nodeId the ID of the node being decoded (provided externally for indexed format)
	 * @param clockTable the clock table for timestamp decoding
	 * @param in the input stream
	 * @return the decoded ArrayNode
	 * @throws IOException if an I/O error occurs
	 */
	ArrayNode decodeArrayNode(org.sagebionetworks.repo.model.grid.patch.LogicalTimestamp nodeId, ClockTable clockTable, InputStream in) throws IOException;

	/**
	 * Decode an ObjectNode from the input stream.
	 *
	 * @param nodeId the ID of the node being decoded (provided externally for indexed format)
	 * @param clockTable the clock table for timestamp decoding
	 * @param in the input stream
	 * @return the decoded ObjectNode
	 * @throws IOException if an I/O error occurs
	 */
	ObjectNode decodeObjectNode(org.sagebionetworks.repo.model.grid.patch.LogicalTimestamp nodeId, ClockTable clockTable, InputStream in) throws IOException;

	/**
	 * Decode a VectorNode from the input stream.
	 *
	 * @param nodeId the ID of the node being decoded (provided externally for indexed format)
	 * @param clockTable the clock table for timestamp decoding
	 * @param in the input stream
	 * @return the decoded VectorNode
	 * @throws IOException if an I/O error occurs
	 */
	VectorNode decodeVectorNode(org.sagebionetworks.repo.model.grid.patch.LogicalTimestamp nodeId, ClockTable clockTable, InputStream in) throws IOException;

	/**
	 * Result of reading the type and length header from an encoded node.
	 */
	public static class TypeAndLength {
		private final int nodeType;
		private final long length;

		public TypeAndLength(int nodeType, long length) {
			this.nodeType = nodeType;
			this.length = length;
		}

		public int getNodeType() {
			return nodeType;
		}

		public long getLength() {
			return length;
		}
	}

	/**
	 * Read and decode the node type and length from the input stream.
	 *
	 * @param in the input stream
	 * @return the decoded type and length
	 * @throws IOException if an I/O error occurs
	 */
	TypeAndLength readNodeTypeAndLength(InputStream in) throws IOException;

	/**
	 * Decode a node based on the type code. This method reads the type/length header
	 * and dispatches to the appropriate type-specific decode method.
	 *
	 * @param nodeId the ID of the node being decoded (provided externally for indexed format)
	 * @param clockTable the clock table for timestamp decoding
	 * @param in the input stream
	 * @return the decoded node
	 * @throws IOException if an I/O error occurs
	 * @throws IllegalArgumentException if the node type is not supported
	 */
	default Node decodeNode(org.sagebionetworks.repo.model.grid.patch.LogicalTimestamp nodeId, ClockTable clockTable, InputStream in) throws IOException {
		TypeAndLength typeAndLength = readNodeTypeAndLength(in);
		// Re-wrap with the type/length already consumed
		return decodeNodeWithTypeAndLength(nodeId, typeAndLength, clockTable, in);
	}

	/**
	 * Decode a node when the type and length have already been read.
	 *
	 * @param nodeId the ID of the node being decoded
	 * @param typeAndLength the already-read type and length
	 * @param clockTable the clock table for timestamp decoding
	 * @param in the input stream (positioned after type/length bytes)
	 * @return the decoded node
	 * @throws IOException if an I/O error occurs
	 * @throws IllegalArgumentException if the node type is not supported
	 */
	default Node decodeNodeWithTypeAndLength(org.sagebionetworks.repo.model.grid.patch.LogicalTimestamp nodeId, TypeAndLength typeAndLength, ClockTable clockTable, InputStream in) throws IOException {
		switch (typeAndLength.getNodeType()) {
			case 0: // con (constant)
				return decodeConstantNodeBody(nodeId, typeAndLength.getLength(), clockTable, in);
			case 2: // obj (object)
				return decodeObjectNodeBody(nodeId, typeAndLength.getLength(), clockTable, in);
			case 3: // vec (vector)
				return decodeVectorNodeBody(nodeId, typeAndLength.getLength(), clockTable, in);
			case 6: // arr (array)
				return decodeArrayNodeBody(nodeId, typeAndLength.getLength(), clockTable, in);
			default:
				throw new IllegalArgumentException("Unsupported node type: " + typeAndLength.getNodeType());
		}
	}

	/**
	 * Decode the body of a ConstantNode (after type/length header has been read).
	 */
	ConstantNode decodeConstantNodeBody(org.sagebionetworks.repo.model.grid.patch.LogicalTimestamp nodeId, long length, ClockTable clockTable, InputStream in) throws IOException;

	/**
	 * Decode the body of an ArrayNode (after type/length header has been read).
	 */
	ArrayNode decodeArrayNodeBody(org.sagebionetworks.repo.model.grid.patch.LogicalTimestamp nodeId, long length, ClockTable clockTable, InputStream in) throws IOException;

	/**
	 * Decode the body of an ObjectNode (after type/length header has been read).
	 */
	ObjectNode decodeObjectNodeBody(org.sagebionetworks.repo.model.grid.patch.LogicalTimestamp nodeId, long length, ClockTable clockTable, InputStream in) throws IOException;

	/**
	 * Decode the body of a VectorNode (after type/length header has been read).
	 */
	VectorNode decodeVectorNodeBody(org.sagebionetworks.repo.model.grid.patch.LogicalTimestamp nodeId, long length, ClockTable clockTable, InputStream in) throws IOException;

}
