package org.sagebionetworks.repo.model.grid.encoding;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sagebionetworks.repo.model.grid.ClockTable;
import org.sagebionetworks.repo.model.grid.encoding.IndexedModelDecoder.DecodedModel;
import org.sagebionetworks.repo.model.grid.encoding.IndexedModelDecoder.DecodedNode;
import org.sagebionetworks.repo.model.grid.node.ArrayNode;
import org.sagebionetworks.repo.model.grid.node.ConstantNode;
import org.sagebionetworks.repo.model.grid.node.Node;
import org.sagebionetworks.repo.model.grid.patch.ConType;
import org.sagebionetworks.repo.model.grid.patch.ConValue;
import org.sagebionetworks.repo.model.grid.patch.LogicalTimestamp;

public class IndexedModelDecoderTest {

	private ClockTable clockTable;
	private LogicalTimestamp rootNodeId;

	@BeforeEach
	public void setUp() {
		clockTable = new ClockTable(List.of(
			new LogicalTimestamp().setReplicaId(100L).setSequenceNumber(50L),
			new LogicalTimestamp().setReplicaId(200L).setSequenceNumber(100L)
		));
		rootNodeId = new LogicalTimestamp().setReplicaId(100L).setSequenceNumber(1L);
	}

	// ==================== Helper Methods ====================

	private byte[] encodeModel(List<Node> nodes) throws IOException {
		return IndexedModelEncoder.encodeModel(clockTable, rootNodeId, nodes);
	}

	// ==================== Basic Decoding Tests ====================

	@Test
	public void testDecodeEmptyModel() throws IOException {
		byte[] encoded = encodeModel(List.of());

		try (IndexedModelDecoder decoder = new IndexedModelDecoder(new ByteArrayInputStream(encoded))) {
			ClockTable decodedClockTable = decoder.getClockTable();
			assertNotNull(decodedClockTable);
			assertEquals(2, decodedClockTable.getClocks().size());

			LogicalTimestamp decodedRootId = decoder.getRootNodeId();
			assertNotNull(decodedRootId);
			assertEquals(rootNodeId.getReplicaId(), decodedRootId.getReplicaId());
			assertEquals(rootNodeId.getSequenceNumber(), decodedRootId.getSequenceNumber());

			// No nodes
			assertNull(decoder.readNode());
		}
	}

	@Test
	public void testDecodeModelWithSingleNode() throws IOException {
		ConstantNode node = new ConstantNode()
			.setId(new LogicalTimestamp().setReplicaId(100L).setSequenceNumber(2L))
			.setValue(new ConValue(ConType.LONG, 42L));

		byte[] encoded = encodeModel(List.of(node));

		try (IndexedModelDecoder decoder = new IndexedModelDecoder(new ByteArrayInputStream(encoded))) {
			// Verify clock table
			assertEquals(2, decoder.getClockTable().getClocks().size());

			// Verify root node ID
			assertEquals(100L, decoder.getRootNodeId().getReplicaId());
			assertEquals(1L, decoder.getRootNodeId().getSequenceNumber());

			// Verify node
			DecodedNode decodedNode = decoder.readNode();
			assertNotNull(decodedNode);
			assertEquals(100L, decodedNode.getNodeId().getReplicaId());
			assertEquals(2L, decodedNode.getNodeId().getSequenceNumber());

			assertTrue(decodedNode.getNode() instanceof ConstantNode);
			ConstantNode decodedConstant = (ConstantNode) decodedNode.getNode();
			assertEquals(ConType.LONG, decodedConstant.getConValue().getType());
			assertEquals(42L, decodedConstant.getConValue().getValue());

			// No more nodes
			assertNull(decoder.readNode());
		}
	}

	@Test
	public void testDecodeModelWithMultipleNodes() throws IOException {
		List<Node> nodes = List.of(
			new ConstantNode()
				.setId(new LogicalTimestamp().setReplicaId(100L).setSequenceNumber(2L))
				.setValue(new ConValue(ConType.LONG, 42L)),
			new ConstantNode()
				.setId(new LogicalTimestamp().setReplicaId(100L).setSequenceNumber(3L))
				.setValue(new ConValue(ConType.STRING, "hello")),
			new ConstantNode()
				.setId(new LogicalTimestamp().setReplicaId(200L).setSequenceNumber(5L))
				.setValue(new ConValue(ConType.BOOLEAN, true))
		);

		byte[] encoded = encodeModel(nodes);

		try (IndexedModelDecoder decoder = new IndexedModelDecoder(new ByteArrayInputStream(encoded))) {
			List<DecodedNode> decodedNodes = new ArrayList<>();
			DecodedNode decodedNode;
			while ((decodedNode = decoder.readNode()) != null) {
				decodedNodes.add(decodedNode);
			}

			assertEquals(3, decodedNodes.size());
		}
	}

	// ==================== Iterator Tests ====================

	@Test
	public void testIterator() throws IOException {
		List<Node> nodes = List.of(
			new ConstantNode()
				.setId(new LogicalTimestamp().setReplicaId(100L).setSequenceNumber(2L))
				.setValue(new ConValue(ConType.LONG, 1L)),
			new ConstantNode()
				.setId(new LogicalTimestamp().setReplicaId(100L).setSequenceNumber(3L))
				.setValue(new ConValue(ConType.LONG, 2L))
		);

		byte[] encoded = encodeModel(nodes);

		try (IndexedModelDecoder decoder = new IndexedModelDecoder(new ByteArrayInputStream(encoded))) {
			int count = 0;
			for (DecodedNode node : decoder) {
				assertNotNull(node);
				count++;
			}
			assertEquals(2, count);
		}
	}

	@Test
	public void testIteratorHasNextIdempotent() throws IOException {
		List<Node> nodes = List.of(
			new ConstantNode()
				.setId(new LogicalTimestamp().setReplicaId(100L).setSequenceNumber(2L))
				.setValue(new ConValue(ConType.LONG, 42L))
		);

		byte[] encoded = encodeModel(nodes);

		try (IndexedModelDecoder decoder = new IndexedModelDecoder(new ByteArrayInputStream(encoded))) {
			Iterator<DecodedNode> iter = decoder.iterator();

			// Multiple hasNext calls should return same result
			assertTrue(iter.hasNext());
			assertTrue(iter.hasNext());
			assertTrue(iter.hasNext());

			// Get the node
			DecodedNode node = iter.next();
			assertNotNull(node);

			// No more elements
			assertTrue(!iter.hasNext());
			assertTrue(!iter.hasNext());
		}
	}

	@Test
	public void testIteratorNoSuchElement() throws IOException {
		byte[] encoded = encodeModel(List.of());

		try (IndexedModelDecoder decoder = new IndexedModelDecoder(new ByteArrayInputStream(encoded))) {
			Iterator<DecodedNode> iter = decoder.iterator();
			assertTrue(!iter.hasNext());
			assertThrows(NoSuchElementException.class, () -> iter.next());
		}
	}

	// ==================== ForEachNode Tests ====================

	@Test
	public void testForEachNode() throws IOException {
		List<Node> nodes = List.of(
			new ConstantNode()
				.setId(new LogicalTimestamp().setReplicaId(100L).setSequenceNumber(2L))
				.setValue(new ConValue(ConType.LONG, 1L)),
			new ConstantNode()
				.setId(new LogicalTimestamp().setReplicaId(100L).setSequenceNumber(3L))
				.setValue(new ConValue(ConType.LONG, 2L)),
			new ConstantNode()
				.setId(new LogicalTimestamp().setReplicaId(100L).setSequenceNumber(4L))
				.setValue(new ConValue(ConType.LONG, 3L))
		);

		byte[] encoded = encodeModel(nodes);

		try (IndexedModelDecoder decoder = new IndexedModelDecoder(new ByteArrayInputStream(encoded))) {
			List<DecodedNode> collected = new ArrayList<>();
			decoder.forEachNode(collected::add);
			assertEquals(3, collected.size());
		}
	}

	// ==================== Static decodeModel Tests ====================

	@Test
	public void testDecodeModelStatic() throws IOException {
		List<Node> nodes = List.of(
			new ConstantNode()
				.setId(new LogicalTimestamp().setReplicaId(100L).setSequenceNumber(2L))
				.setValue(new ConValue(ConType.LONG, 42L)),
			new ConstantNode()
				.setId(new LogicalTimestamp().setReplicaId(200L).setSequenceNumber(5L))
				.setValue(new ConValue(ConType.STRING, "test"))
		);

		byte[] encoded = encodeModel(nodes);

		DecodedModel result = IndexedModelDecoder.decodeModel(encoded);

		assertNotNull(result);
		assertNotNull(result.getClockTable());
		assertEquals(2, result.getClockTable().getClocks().size());
		assertNotNull(result.getRootNodeId());
		assertEquals(100L, result.getRootNodeId().getReplicaId());
		assertEquals(1L, result.getRootNodeId().getSequenceNumber());
		assertEquals(2, result.getNodes().size());
	}

	// ==================== Clock Table Decoding Tests ====================

	@Test
	public void testClockTableDecoding() throws IOException {
		byte[] encoded = encodeModel(List.of());

		try (IndexedModelDecoder decoder = new IndexedModelDecoder(new ByteArrayInputStream(encoded))) {
			ClockTable decoded = decoder.getClockTable();

			assertEquals(2, decoded.getClocks().size());
			assertEquals(100L, decoded.getClocks().get(0).getReplicaId());
			assertEquals(50L, decoded.getClocks().get(0).getSequenceNumber());
			assertEquals(200L, decoded.getClocks().get(1).getReplicaId());
			assertEquals(100L, decoded.getClocks().get(1).getSequenceNumber());
		}
	}

	// ==================== Different Node Types ====================

	@Test
	public void testDecodeArrayNode() throws IOException {
		ArrayNode arrayNode = new ArrayNode()
			.setId(new LogicalTimestamp().setReplicaId(100L).setSequenceNumber(5L))
			.setLength(0L)
			.setElements(new ArrayList<>());

		byte[] encoded = encodeModel(List.of(arrayNode));

		try (IndexedModelDecoder decoder = new IndexedModelDecoder(new ByteArrayInputStream(encoded))) {
			DecodedNode decodedNode = decoder.readNode();
			assertNotNull(decodedNode);
			assertTrue(decodedNode.getNode() instanceof ArrayNode);

			ArrayNode decodedArray = (ArrayNode) decodedNode.getNode();
			assertEquals(0L, decodedArray.getLength());
		}
	}

	// ==================== Validation Tests ====================

	@Test
	public void testNullInputStream() {
		assertThrows(IllegalArgumentException.class, () -> {
			new IndexedModelDecoder(null);
		});
	}

	@Test
	public void testInvalidCBORData() {
		byte[] invalidData = new byte[] { 0x00, 0x01, 0x02 };
		assertThrows(IOException.class, () -> {
			try (IndexedModelDecoder decoder = new IndexedModelDecoder(new ByteArrayInputStream(invalidData))) {
				decoder.getClockTable(); // This triggers header read and should fail
			}
		});
	}

	// ==================== DecodedNode Tests ====================

	@Test
	public void testDecodedNodeGetters() throws IOException {
		ConstantNode node = new ConstantNode()
			.setId(new LogicalTimestamp().setReplicaId(100L).setSequenceNumber(2L))
			.setValue(new ConValue(ConType.LONG, 42L));

		byte[] encoded = encodeModel(List.of(node));

		try (IndexedModelDecoder decoder = new IndexedModelDecoder(new ByteArrayInputStream(encoded))) {
			DecodedNode decodedNode = decoder.readNode();
			assertNotNull(decodedNode.getNodeId());
			assertNotNull(decodedNode.getNode());
			assertEquals(100L, decodedNode.getNodeId().getReplicaId());
			assertEquals(2L, decodedNode.getNodeId().getSequenceNumber());
		}
	}

	// ==================== Multiple Sessions Tests ====================

	@Test
	public void testDecodeNodesFromMultipleSessions() throws IOException {
		List<Node> nodes = List.of(
			new ConstantNode()
				.setId(new LogicalTimestamp().setReplicaId(100L).setSequenceNumber(10L))
				.setValue(new ConValue(ConType.LONG, 1L)),
			new ConstantNode()
				.setId(new LogicalTimestamp().setReplicaId(200L).setSequenceNumber(20L))
				.setValue(new ConValue(ConType.LONG, 2L)),
			new ConstantNode()
				.setId(new LogicalTimestamp().setReplicaId(100L).setSequenceNumber(15L))
				.setValue(new ConValue(ConType.LONG, 3L))
		);

		byte[] encoded = encodeModel(nodes);

		DecodedModel result = IndexedModelDecoder.decodeModel(encoded);

		assertEquals(3, result.getNodes().size());

		// Verify nodes have correct replica IDs
		boolean foundReplica100 = false;
		boolean foundReplica200 = false;
		for (DecodedNode node : result.getNodes()) {
			if (node.getNodeId().getReplicaId() == 100L) {
				foundReplica100 = true;
			} else if (node.getNodeId().getReplicaId() == 200L) {
				foundReplica200 = true;
			}
		}
		assertTrue(foundReplica100, "Should have node from replica 100");
		assertTrue(foundReplica200, "Should have node from replica 200");
	}
}
