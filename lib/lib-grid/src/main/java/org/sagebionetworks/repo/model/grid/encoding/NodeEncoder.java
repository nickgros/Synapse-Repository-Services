package org.sagebionetworks.repo.model.grid.encoding;

import java.io.IOException;
import java.io.OutputStream;

import org.sagebionetworks.repo.model.grid.ClockTable;
import org.sagebionetworks.repo.model.grid.node.ArrayNode;
import org.sagebionetworks.repo.model.grid.node.RGANode;
import org.sagebionetworks.repo.model.grid.node.ConstantNode;
import org.sagebionetworks.repo.model.grid.node.Node;
import org.sagebionetworks.repo.model.grid.node.ObjectNode;
import org.sagebionetworks.repo.model.grid.node.VectorNode;

/**
 * Interface for encoding nodes in various serialization formats.
 * Implementations should handle format-specific encoding logic for each node type.
 * Encoding options (such as whether to include node IDs) are determined internally
 * by each encoder implementation based on the format requirements.
 *
 * Supported formats include:
 * - Binary Structural (binary format with node IDs)
 * - Indexed Encoding (binary format without node IDs for compact representation)
 */
public interface NodeEncoder {

	/**
	 * Encode a ConstantNode to the output stream.
	 *
	 * @param node the node to encode
	 * @param out the output stream
	 * @return the number of bytes written
	 * @throws IOException if an I/O error occurs
	 */
	int encode(ConstantNode node, ClockTable clockTable, OutputStream out) throws IOException;

	/**
	 * Encode an ArrayNode to the output stream.
	 *
	 * @param node the node to encode
	 * @param out the output stream
	 * @return the number of bytes written
	 * @throws IOException if an I/O error occurs
	 */
	int encode(ArrayNode node, ClockTable clockTable, OutputStream out) throws IOException;

	/**
	 * Encode an ObjectNode to the output stream.
	 *
	 * @param node the node to encode
	 * @param out the output stream
	 * @return the number of bytes written
	 * @throws IOException if an I/O error occurs
	 */
	int encode(ObjectNode node, ClockTable clockTable, OutputStream out) throws IOException;

	/**
	 * Encode a VectorNode to the output stream.
	 *
	 * @param node the node to encode
	 * @param out the output stream
	 * @return the number of bytes written
	 * @throws IOException if an I/O error occurs
	 */
	int encode(VectorNode node, ClockTable clockTable, OutputStream out) throws IOException;

	/**
	 * Generic encode method that dispatches to the appropriate type-specific method.
	 * This uses double dispatch to handle different node types.
	 *
	 * @param node the node to encode
	 * @param out the output stream
	 * @return the number of bytes written
	 * @throws IOException if an I/O error occurs
	 * @throws IllegalArgumentException if the node type is not supported
	 */
	default int encodeNode(Node node, ClockTable clockTable, OutputStream out) throws IOException {
		if (node instanceof ConstantNode) {
			return encode((ConstantNode) node, clockTable, out);
		} else if (node instanceof ArrayNode) {
			return encode((ArrayNode) node, clockTable, out);
		} else if (node instanceof ObjectNode) {
			return encode((ObjectNode) node, clockTable, out);
		} else if (node instanceof VectorNode) {
			return encode((VectorNode) node, clockTable, out);
		} else {
			throw new IllegalArgumentException("Unsupported node type: " + node.getClass().getName());
		}
	}

}


