package org.sagebionetworks.repo.model.grid.encoding;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import org.sagebionetworks.repo.model.grid.ClockTable;
import org.sagebionetworks.repo.model.grid.node.Node;
import org.sagebionetworks.repo.model.grid.patch.LogicalTimestamp;
import org.sagebionetworks.util.ValidateArgument;

import com.fasterxml.jackson.dataformat.cbor.CBORGenerator;

/**
 * Encoder for indexed model format.
 *
 * The indexed model format is a CBOR map containing:
 * - "c" — the clock table (binary encoded)
 * - "r" — the root node ID (binary encoded timestamp)
 * - "&lt;sid&gt;_&lt;seq&gt;" — node entries (binary encoded nodes)
 *
 * This encoder buffers nodes and writes a definite-length CBOR map on close,
 * ensuring compatibility with json-joy's indexed encoding format.
 *
 * Usage:
 * <pre>
 * try (IndexedModelEncoder encoder = new IndexedModelEncoder(outputStream, clockTable, rootNodeId)) {
 *     for (Node node : nodes) {
 *         encoder.writeNode(node);
 *     }
 * }
 * </pre>
 *
 * @see <a href="https://jsonjoy.com/specs/json-crdt/encoding/indexed-encoding">Indexed Encoding</a>
 */
public class IndexedModelEncoder implements Closeable {
	private final OutputStream out;
	private final LogicalTimestamp rootNodeId;
	private final NodeEncoder nodeEncoder;
	private final List<NodeEntry> nodeEntries;
    private ClockTable clockTable;
    private boolean closed = false;

	/**
	 * Buffered node entry (key + encoded bytes).
	 */
	private static class NodeEntry {
		final String key;
		final byte[] value;

		NodeEntry(String key, byte[] value) {
			this.key = key;
			this.value = value;
		}
	}

	/**
	 * Create a new IndexedModelEncoder.
	 *
	 * @param out the output stream to write to
	 * @param clockTable the clock table for the model. For the indexed encoder, the initial clock table MUST include
	 *                     all replica IDs in the correct order. However, the clock table does mpt need to have the
	 *                     latest sequence numbers, as timestamps are encoded using raw sequence numbers. To update the
	 *                     clock table, use {@link #setClockTable(ClockTable)}.
	 * @param rootNodeId the ID of the root node
	 * @throws IOException if an I/O error occurs
	 */
	public IndexedModelEncoder(OutputStream out, ClockTable clockTable, LogicalTimestamp rootNodeId) {
		ValidateArgument.required(out, "out");
		ValidateArgument.required(clockTable, "clockTable");
		ValidateArgument.required(rootNodeId, "rootNodeId");

		this.out = out;
		this.clockTable = clockTable;
		this.rootNodeId = rootNodeId;
		this.nodeEncoder = new IndexedEncoder();
		this.nodeEntries = new ArrayList<>();
	}

	/**
	 * Write a node to the model.
	 *
	 * @param node the node to write
	 * @throws IOException if an I/O error occurs
	 * @throws IllegalStateException if the encoder has been closed
	 */
	public void writeNode(Node node) throws IOException {
		if (closed) {
			throw new IllegalStateException("Encoder has been closed");
		}
		ValidateArgument.required(node, "node");
		ValidateArgument.required(node.getId(), "node.id");

		// Generate the node key
		String nodeKey = Base36Utils.encodeNodeKey(node.getId(), clockTable.getClocks());

		// Encode the node to binary
		ByteArrayOutputStream nodeBytes = new ByteArrayOutputStream();
		nodeEncoder.encodeNode(node, clockTable, nodeBytes);

		// Buffer the entry
		nodeEntries.add(new NodeEntry(nodeKey, nodeBytes.toByteArray()));
	}

	/**
	 * Close the encoder, writing the complete CBOR map.
	 * This must be called to produce valid output.
	 *
	 * @throws IOException if an I/O error occurs
	 */
	@Override
	public void close() throws IOException {
		if (!closed) {
			// Total entries: "c", "r", plus all node entries
			int totalEntries = 2 + nodeEntries.size();

			try (CBORGenerator generator = CBORUtils.getCBORFactory().createGenerator(out)) {
				// Write definite-length map header
				generator.writeStartObject(totalEntries);

				// Write clock table ("c")
				generator.writeFieldName("c");
				generator.writeBinary(clockTable.toBinary());

				// Write root node ID ("r") - uses raw sequence number, not difference encoding
				generator.writeFieldName("r");
				generator.writeBinary(clockTable.encodeTimestamp(rootNodeId));

				// Write all buffered node entries
				for (NodeEntry entry : nodeEntries) {
					generator.writeFieldName(entry.key);
					generator.writeBinary(entry.value);
				}

				generator.writeEndObject();
			}

			closed = true;
		}
	}

	/**
	 * Get the clock table being used by this encoder.
	 *
	 * @return the clock table
	 */
	public ClockTable getClockTable() {
		return clockTable;
	}

    /**
     * Sets a new clock table for this encoder. The index of each replica in the clock table is used to encode nodes,
     * so the new clock table must have the same replica IDs in the same order as the existing clock table.
     *
     * This method simplifies instantiating a new CRDT document where the replica ID(s) are known beforehand, but the number
     * of nodes in the document is not known. For example, when instantiating a data grid, you may perform a sequence like:
     *
     * <ol>
     * <li>Create an IndexedModelEncoder with a clock table containing only the internal replica ID.</li>
     * <li>Write nodes to the encoder for an unknown number of rows in the grid. The internal replica is the creator of
     * each node.</li>
     * <li>Once all nodes are written, update the clock table such that it properly captures the sequence number of the
     * internal replica.</li>
     * </ol>
     *
     * @param clockTable
     */
	public void setClockTable(ClockTable clockTable) {
		ValidateArgument.required(clockTable, "clockTable");
		// Verify the clock tables have the same replica IDs in the same order
		if (this.clockTable.getClocks().size() != clockTable.getClocks().size()) {
			throw new IllegalArgumentException("New clock table must have the same replica IDs in the same order. The clock table sizes differ.");
		}
		for (int i = 0; i < this.clockTable.getClocks().size(); i++) {
            if (!this.clockTable.getClocks().get(i).getReplicaId()
                    .equals(clockTable.getClocks().get(i).getReplicaId())) {
                throw new IllegalArgumentException("New clock table must have the same replica IDs in the same order");
            }
        }

        this.clockTable = clockTable;
	}

	/**
	 * Convenience method to encode a complete model in one call.
	 *
	 * @param clockTable the clock table
	 * @param rootNodeId the root node ID
	 * @param nodes the nodes to encode
	 * @return the encoded model as a byte array
	 * @throws IOException if an I/O error occurs
	 */
	public static byte[] encodeModel(ClockTable clockTable, LogicalTimestamp rootNodeId, Iterable<Node> nodes) throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		try (IndexedModelEncoder encoder = new IndexedModelEncoder(out, clockTable, rootNodeId)) {
			for (Node node : nodes) {
				encoder.writeNode(node);
			}
		}
		return out.toByteArray();
	}
}
