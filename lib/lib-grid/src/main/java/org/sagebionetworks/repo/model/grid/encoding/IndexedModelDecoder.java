package org.sagebionetworks.repo.model.grid.encoding;

import java.io.ByteArrayInputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.function.Consumer;

import org.sagebionetworks.repo.model.grid.ClockTable;
import org.sagebionetworks.repo.model.grid.EncodingUtils;
import org.sagebionetworks.repo.model.grid.node.Node;
import org.sagebionetworks.repo.model.grid.patch.LogicalTimestamp;
import org.sagebionetworks.util.ValidateArgument;

import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.dataformat.cbor.CBORParser;

/**
 * Streaming decoder for indexed model format.
 *
 * The indexed model format is a CBOR map containing:
 * - "c" — the clock table (binary encoded)
 * - "r" — the root node ID (binary encoded timestamp)
 * - "&lt;sid&gt;_&lt;seq&gt;" — node entries (binary encoded nodes)
 *
 * This decoder supports streaming by providing an iterator over decoded nodes,
 * allowing nodes to be processed one at a time without loading the entire model into memory.
 *
 * Usage:
 * <pre>
 * try (IndexedModelDecoder decoder = new IndexedModelDecoder(inputStream)) {
 *     ClockTable clockTable = decoder.getClockTable();
 *     LogicalTimestamp rootNodeId = decoder.getRootNodeId();
 *
 *     for (DecodedNode node : decoder) {
 *         // Process node
 *     }
 * }
 * </pre>
 *
 * @see <a href="https://jsonjoy.com/specs/json-crdt/encoding/indexed-encoding">Indexed Encoding</a>
 */
public class IndexedModelDecoder implements Closeable, Iterable<IndexedModelDecoder.DecodedNode> {

	private final CBORParser parser;
	private final NodeDecoder nodeDecoder;
	private ClockTable clockTable;
	private LogicalTimestamp rootNodeId;
	private boolean headerRead = false;
	private boolean closed = false;

	/**
	 * A decoded node with its ID and the node itself.
	 */
	public static class DecodedNode {
		private final LogicalTimestamp nodeId;
		private final Node node;

		public DecodedNode(LogicalTimestamp nodeId, Node node) {
			this.nodeId = nodeId;
			this.node = node;
		}

		public LogicalTimestamp getNodeId() {
			return nodeId;
		}

		public Node getNode() {
			return node;
		}
	}

	/**
	 * Create a new IndexedModelDecoder.
	 *
	 * @param in the input stream to read from
	 * @throws IOException if an I/O error occurs
	 */
	public IndexedModelDecoder(InputStream in) throws IOException {
		ValidateArgument.required(in, "in");

		this.parser = CBORUtils.getCBORFactory().createParser(in);
		this.nodeDecoder = new IndexedDecoder();
	}

	/**
	 * Read the header (clock table and root node ID) if not already read.
	 */
	private void ensureHeaderRead() throws IOException {
		if (headerRead) {
			return;
		}

		// Expect start of object
		JsonToken token = parser.nextToken();
		if (token != JsonToken.START_OBJECT) {
			throw new IOException("Expected CBOR map, got: " + token);
		}

		// Read entries until we find "c" and "r"
		boolean foundClockTable = false;
		boolean foundRootNode = false;

		while (!foundClockTable || !foundRootNode) {
			token = parser.nextToken();
			if (token == null || token == JsonToken.END_OBJECT) {
				break;
			}

			if (token != JsonToken.FIELD_NAME) {
				throw new IOException("Expected field name, got: " + token);
			}

			String fieldName = parser.getCurrentName();
			parser.nextToken(); // Move to value

			if ("c".equals(fieldName)) {
				// Clock table
				byte[] clockTableBytes = parser.getBinaryValue();
				this.clockTable = decodeClockTable(clockTableBytes);
				foundClockTable = true;
			} else if ("r".equals(fieldName)) {
				// Root node ID - need clock table first
				byte[] rootNodeBytes = parser.getBinaryValue();
				// Store bytes temporarily if clock table not yet read
				if (this.clockTable != null) {
					// Use raw timestamp decoding for root reference (not difference encoding)
					this.rootNodeId = this.clockTable.decodeTimestamp(rootNodeBytes);
					foundRootNode = true;
				} else {
					// This shouldn't happen if the model is well-formed (c comes before r)
					throw new IOException("Root node ID found before clock table");
				}
			} else {
				// This is a node entry - push back and stop reading header
				// We can't easily push back with Jackson, so we need to handle this differently
				// For now, assume "c" and "r" come first
				throw new IOException("Expected 'c' or 'r' field, got: " + fieldName);
			}
		}

		if (!foundClockTable) {
			throw new IOException("Clock table ('c') not found in model");
		}
		if (!foundRootNode) {
			throw new IOException("Root node ID ('r') not found in model");
		}

		headerRead = true;
	}

	/**
	 * Decode a clock table from binary format.
	 */
	private ClockTable decodeClockTable(byte[] bytes) throws IOException {
		ByteArrayInputStream in = new ByteArrayInputStream(bytes);

		// First, read the number of clocks
		long numClocks = EncodingUtils.decodeVu57(in);

		List<LogicalTimestamp> clocks = new ArrayList<>((int) numClocks);
		for (int i = 0; i < numClocks; i++) {
			// Each clock is two vu57 integers: session ID and sequence number
			long sessionId = EncodingUtils.decodeVu57(in);
			long sequenceNumber = EncodingUtils.decodeVu57(in);
			clocks.add(new LogicalTimestamp()
				.setReplicaId(sessionId)
				.setSequenceNumber(sequenceNumber));
		}

		return new ClockTable(clocks);
	}

	/**
	 * Get the clock table for this model.
	 * This will read the header if not already read.
	 *
	 * @return the clock table
	 * @throws IOException if an I/O error occurs
	 */
	public ClockTable getClockTable() throws IOException {
		ensureHeaderRead();
		return clockTable;
	}

	/**
	 * Get the root node ID for this model.
	 * This will read the header if not already read.
	 *
	 * @return the root node ID
	 * @throws IOException if an I/O error occurs
	 */
	public LogicalTimestamp getRootNodeId() throws IOException {
		ensureHeaderRead();
		return rootNodeId;
	}

	/**
	 * Read the next node from the model.
	 *
	 * @return the next decoded node, or null if no more nodes
	 * @throws IOException if an I/O error occurs
	 */
	public DecodedNode readNode() throws IOException {
		ensureHeaderRead();

		JsonToken token = parser.nextToken();
		if (token == null || token == JsonToken.END_OBJECT) {
			return null;
		}

		if (token != JsonToken.FIELD_NAME) {
			throw new IOException("Expected field name, got: " + token);
		}

		String nodeKey = parser.getCurrentName();
		parser.nextToken(); // Move to value

		// Decode the node key to get the node ID
		LogicalTimestamp nodeId = Base36Utils.decodeNodeKeyToTimestamp(nodeKey, clockTable.getClocks());

		// Read the node binary
		byte[] nodeBytes = parser.getBinaryValue();

		// Decode the node
		ByteArrayInputStream nodeIn = new ByteArrayInputStream(nodeBytes);
		Node node = nodeDecoder.decodeNode(nodeId, clockTable, nodeIn);

		return new DecodedNode(nodeId, node);
	}

	/**
	 * Iterate through all nodes in the model, calling the consumer for each.
	 *
	 * @param consumer the consumer to call for each node
	 * @throws IOException if an I/O error occurs
	 */
	public void forEachNode(Consumer<DecodedNode> consumer) throws IOException {
		DecodedNode node;
		while ((node = readNode()) != null) {
			consumer.accept(node);
		}
	}

	/**
	 * Returns an iterator over the nodes in this model.
	 * Note: This iterator wraps IOExceptions in RuntimeExceptions.
	 *
	 * @return an iterator over decoded nodes
	 */
	@Override
	public Iterator<DecodedNode> iterator() {
		try {
			ensureHeaderRead();
		} catch (IOException e) {
			throw new RuntimeException("Failed to read model header", e);
		}

		return new Iterator<DecodedNode>() {
			private DecodedNode next = null;
			private boolean done = false;

			@Override
			public boolean hasNext() {
				if (done) {
					return false;
				}
				if (next != null) {
					return true;
				}
				try {
					next = readNode();
					if (next == null) {
						done = true;
						return false;
					}
					return true;
				} catch (IOException e) {
					throw new RuntimeException("Failed to read node", e);
				}
			}

			@Override
			public DecodedNode next() {
				if (!hasNext()) {
					throw new NoSuchElementException();
				}
				DecodedNode result = next;
				next = null;
				return result;
			}
		};
	}

	/**
	 * Close the decoder.
	 *
	 * @throws IOException if an I/O error occurs
	 */
	@Override
	public void close() throws IOException {
		if (!closed) {
			parser.close();
			closed = true;
		}
	}

	/**
	 * Convenience method to decode a model and collect all nodes.
	 * Note: This loads all nodes into memory, defeating the streaming purpose.
	 * Use the iterator or forEachNode for streaming.
	 *
	 * @param bytes the encoded model bytes
	 * @return a result containing the clock table, root node ID, and all nodes
	 * @throws IOException if an I/O error occurs
	 */
	public static DecodedModel decodeModel(byte[] bytes) throws IOException {
		ByteArrayInputStream in = new ByteArrayInputStream(bytes);
		try (IndexedModelDecoder decoder = new IndexedModelDecoder(in)) {
			ClockTable clockTable = decoder.getClockTable();
			LogicalTimestamp rootNodeId = decoder.getRootNodeId();
			List<DecodedNode> nodes = new ArrayList<>();
			decoder.forEachNode(nodes::add);
			return new DecodedModel(clockTable, rootNodeId, nodes);
		}
	}

	/**
	 * Result of decoding a complete model.
	 */
	public static class DecodedModel {
		private final ClockTable clockTable;
		private final LogicalTimestamp rootNodeId;
		private final List<DecodedNode> nodes;

		public DecodedModel(ClockTable clockTable, LogicalTimestamp rootNodeId, List<DecodedNode> nodes) {
			this.clockTable = clockTable;
			this.rootNodeId = rootNodeId;
			this.nodes = nodes;
		}

		public ClockTable getClockTable() {
			return clockTable;
		}

		public LogicalTimestamp getRootNodeId() {
			return rootNodeId;
		}

		public List<DecodedNode> getNodes() {
			return nodes;
		}
	}
}
