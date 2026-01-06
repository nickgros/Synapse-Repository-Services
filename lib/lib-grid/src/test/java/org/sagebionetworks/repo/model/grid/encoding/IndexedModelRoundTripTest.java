package org.sagebionetworks.repo.model.grid.encoding;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.sagebionetworks.repo.model.grid.ClockTable;
import org.sagebionetworks.repo.model.grid.encoding.IndexedModelDecoder.DecodedModel;
import org.sagebionetworks.repo.model.grid.encoding.IndexedModelDecoder.DecodedNode;
import org.sagebionetworks.repo.model.grid.node.ArrayNode;
import org.sagebionetworks.repo.model.grid.node.ConstantNode;
import org.sagebionetworks.repo.model.grid.node.Node;
import org.sagebionetworks.repo.model.grid.node.RGANode;
import org.sagebionetworks.repo.model.grid.patch.ConType;
import org.sagebionetworks.repo.model.grid.patch.ConValue;
import org.sagebionetworks.repo.model.grid.patch.LogicalTimestamp;

/**
 * Round-trip tests for the indexed model encoder/decoder.
 * These tests verify that encoding followed by decoding produces the original data.
 */
public class IndexedModelRoundTripTest {

	// ==================== Clock Table Round-Trip ====================

	@Test
	public void testClockTableRoundTrip_SingleClock() throws IOException {
		ClockTable original = new ClockTable(List.of(
			new LogicalTimestamp().setReplicaId(100L).setSequenceNumber(50L)
		));
		LogicalTimestamp rootNodeId = new LogicalTimestamp().setReplicaId(100L).setSequenceNumber(1L);

		byte[] encoded = IndexedModelEncoder.encodeModel(original, rootNodeId, List.of());
		DecodedModel decoded = IndexedModelDecoder.decodeModel(encoded);

		assertEquals(1, decoded.getClockTable().getClocks().size());
		assertEquals(100L, decoded.getClockTable().getClocks().get(0).getReplicaId());
		assertEquals(50L, decoded.getClockTable().getClocks().get(0).getSequenceNumber());
	}

	@Test
	public void testClockTableRoundTrip_MultipleClocks() throws IOException {
		ClockTable original = new ClockTable(List.of(
			new LogicalTimestamp().setReplicaId(100L).setSequenceNumber(50L),
			new LogicalTimestamp().setReplicaId(200L).setSequenceNumber(100L),
			new LogicalTimestamp().setReplicaId(300L).setSequenceNumber(150L)
		));
		LogicalTimestamp rootNodeId = new LogicalTimestamp().setReplicaId(100L).setSequenceNumber(1L);

		byte[] encoded = IndexedModelEncoder.encodeModel(original, rootNodeId, List.of());
		DecodedModel decoded = IndexedModelDecoder.decodeModel(encoded);

		assertEquals(3, decoded.getClockTable().getClocks().size());
		for (int i = 0; i < 3; i++) {
			assertEquals(original.getClocks().get(i).getReplicaId(),
				decoded.getClockTable().getClocks().get(i).getReplicaId());
			assertEquals(original.getClocks().get(i).getSequenceNumber(),
				decoded.getClockTable().getClocks().get(i).getSequenceNumber());
		}
	}

	// ==================== Root Node ID Round-Trip ====================

	@Test
	public void testRootNodeIdRoundTrip() throws IOException {
		ClockTable clockTable = new ClockTable(List.of(
			new LogicalTimestamp().setReplicaId(100L).setSequenceNumber(50L)
		));
		LogicalTimestamp rootNodeId = new LogicalTimestamp().setReplicaId(100L).setSequenceNumber(25L);

		byte[] encoded = IndexedModelEncoder.encodeModel(clockTable, rootNodeId, List.of());
		DecodedModel decoded = IndexedModelDecoder.decodeModel(encoded);

		assertEquals(rootNodeId.getReplicaId(), decoded.getRootNodeId().getReplicaId());
		assertEquals(rootNodeId.getSequenceNumber(), decoded.getRootNodeId().getSequenceNumber());
	}

	// ==================== ConstantNode Round-Trip ====================

	@Test
	public void testConstantNodeRoundTrip_Long() throws IOException {
		ClockTable clockTable = new ClockTable(List.of(
			new LogicalTimestamp().setReplicaId(100L).setSequenceNumber(50L)
		));
		LogicalTimestamp rootNodeId = new LogicalTimestamp().setReplicaId(100L).setSequenceNumber(1L);

		ConstantNode original = new ConstantNode()
			.setId(new LogicalTimestamp().setReplicaId(100L).setSequenceNumber(2L))
			.setValue(new ConValue(ConType.LONG, 42L));

		byte[] encoded = IndexedModelEncoder.encodeModel(clockTable, rootNodeId, List.of(original));
		DecodedModel decoded = IndexedModelDecoder.decodeModel(encoded);

		assertEquals(1, decoded.getNodes().size());
		DecodedNode decodedNode = decoded.getNodes().get(0);

		assertEquals(100L, decodedNode.getNodeId().getReplicaId());
		assertEquals(2L, decodedNode.getNodeId().getSequenceNumber());

		assertTrue(decodedNode.getNode() instanceof ConstantNode);
		ConstantNode decodedConstant = (ConstantNode) decodedNode.getNode();
		assertEquals(ConType.LONG, decodedConstant.getConValue().getType());
		assertEquals(42L, decodedConstant.getConValue().getValue());
	}

	@Test
	public void testConstantNodeRoundTrip_String() throws IOException {
		ClockTable clockTable = new ClockTable(List.of(
			new LogicalTimestamp().setReplicaId(100L).setSequenceNumber(50L)
		));
		LogicalTimestamp rootNodeId = new LogicalTimestamp().setReplicaId(100L).setSequenceNumber(1L);

		ConstantNode original = new ConstantNode()
			.setId(new LogicalTimestamp().setReplicaId(100L).setSequenceNumber(2L))
			.setValue(new ConValue(ConType.STRING, "hello world"));

		byte[] encoded = IndexedModelEncoder.encodeModel(clockTable, rootNodeId, List.of(original));
		DecodedModel decoded = IndexedModelDecoder.decodeModel(encoded);

		assertEquals(1, decoded.getNodes().size());
		ConstantNode decodedConstant = (ConstantNode) decoded.getNodes().get(0).getNode();
		assertEquals(ConType.STRING, decodedConstant.getConValue().getType());
		assertEquals("hello world", decodedConstant.getConValue().getValue());
	}

	@Test
	public void testConstantNodeRoundTrip_Boolean() throws IOException {
		ClockTable clockTable = new ClockTable(List.of(
			new LogicalTimestamp().setReplicaId(100L).setSequenceNumber(50L)
		));
		LogicalTimestamp rootNodeId = new LogicalTimestamp().setReplicaId(100L).setSequenceNumber(1L);

		ConstantNode trueNode = new ConstantNode()
			.setId(new LogicalTimestamp().setReplicaId(100L).setSequenceNumber(2L))
			.setValue(new ConValue(ConType.BOOLEAN, true));
		ConstantNode falseNode = new ConstantNode()
			.setId(new LogicalTimestamp().setReplicaId(100L).setSequenceNumber(3L))
			.setValue(new ConValue(ConType.BOOLEAN, false));

		byte[] encoded = IndexedModelEncoder.encodeModel(clockTable, rootNodeId, List.of(trueNode, falseNode));
		DecodedModel decoded = IndexedModelDecoder.decodeModel(encoded);

		assertEquals(2, decoded.getNodes().size());

		// Build a map by sequence number for easier verification
		Map<Long, ConstantNode> nodesBySeq = new HashMap<>();
		for (DecodedNode node : decoded.getNodes()) {
			nodesBySeq.put(node.getNodeId().getSequenceNumber(), (ConstantNode) node.getNode());
		}

		assertEquals(true, nodesBySeq.get(2L).getConValue().getValue());
		assertEquals(false, nodesBySeq.get(3L).getConValue().getValue());
	}

	@Test
	public void testConstantNodeRoundTrip_Double() throws IOException {
		ClockTable clockTable = new ClockTable(List.of(
			new LogicalTimestamp().setReplicaId(100L).setSequenceNumber(50L)
		));
		LogicalTimestamp rootNodeId = new LogicalTimestamp().setReplicaId(100L).setSequenceNumber(1L);

		ConstantNode original = new ConstantNode()
			.setId(new LogicalTimestamp().setReplicaId(100L).setSequenceNumber(2L))
			.setValue(new ConValue(ConType.DOUBLE, 3.14159));

		byte[] encoded = IndexedModelEncoder.encodeModel(clockTable, rootNodeId, List.of(original));
		DecodedModel decoded = IndexedModelDecoder.decodeModel(encoded);

		assertEquals(1, decoded.getNodes().size());
		ConstantNode decodedConstant = (ConstantNode) decoded.getNodes().get(0).getNode();
		assertEquals(ConType.DOUBLE, decodedConstant.getConValue().getType());
		assertEquals(3.14159, (Double) decodedConstant.getConValue().getValue(), 0.00001);
	}

	@Test
	public void testConstantNodeRoundTrip_Null() throws IOException {
		ClockTable clockTable = new ClockTable(List.of(
			new LogicalTimestamp().setReplicaId(100L).setSequenceNumber(50L)
		));
		LogicalTimestamp rootNodeId = new LogicalTimestamp().setReplicaId(100L).setSequenceNumber(1L);

		ConstantNode original = new ConstantNode()
			.setId(new LogicalTimestamp().setReplicaId(100L).setSequenceNumber(2L))
			.setValue(new ConValue(ConType.NULL, null));

		byte[] encoded = IndexedModelEncoder.encodeModel(clockTable, rootNodeId, List.of(original));
		DecodedModel decoded = IndexedModelDecoder.decodeModel(encoded);

		assertEquals(1, decoded.getNodes().size());
		ConstantNode decodedConstant = (ConstantNode) decoded.getNodes().get(0).getNode();
		assertEquals(ConType.NULL, decodedConstant.getConValue().getType());
	}

	// ==================== ArrayNode Round-Trip ====================

	@Test
	public void testArrayNodeRoundTrip_Empty() throws IOException {
		ClockTable clockTable = new ClockTable(List.of(
			new LogicalTimestamp().setReplicaId(100L).setSequenceNumber(50L)
		));
		LogicalTimestamp rootNodeId = new LogicalTimestamp().setReplicaId(100L).setSequenceNumber(1L);

		ArrayNode original = new ArrayNode()
			.setId(new LogicalTimestamp().setReplicaId(100L).setSequenceNumber(5L))
			.setLength(0L)
			.setElements(new ArrayList<>());

		byte[] encoded = IndexedModelEncoder.encodeModel(clockTable, rootNodeId, List.of(original));
		DecodedModel decoded = IndexedModelDecoder.decodeModel(encoded);

		assertEquals(1, decoded.getNodes().size());
		assertTrue(decoded.getNodes().get(0).getNode() instanceof ArrayNode);
		ArrayNode decodedArray = (ArrayNode) decoded.getNodes().get(0).getNode();
		assertEquals(0L, decodedArray.getLength());
	}

	@Test
	public void testArrayNodeRoundTrip_WithElements() throws IOException {
		ClockTable clockTable = new ClockTable(List.of(
			new LogicalTimestamp().setReplicaId(100L).setSequenceNumber(50L)
		));
		LogicalTimestamp rootNodeId = new LogicalTimestamp().setReplicaId(100L).setSequenceNumber(1L);

		LogicalTimestamp arrayId = new LogicalTimestamp().setReplicaId(100L).setSequenceNumber(5L);

		List<RGANode> elements = new ArrayList<>();
		for (int i = 0; i < 3; i++) {
			elements.add(new RGANode()
				.setNodeId(arrayId)
				.setDataId(new LogicalTimestamp().setReplicaId(100L).setSequenceNumber(10L + i))
				.setDataId(new LogicalTimestamp().setReplicaId(100L).setSequenceNumber(20L + i))
				.setRefId(i == 0 ? null : new LogicalTimestamp().setReplicaId(100L).setSequenceNumber(10L + i - 1))
				.setIsDeleted(false));
		}

		ArrayNode original = new ArrayNode()
			.setId(arrayId)
			.setLength(3L)
			.setElements(elements);

		byte[] encoded = IndexedModelEncoder.encodeModel(clockTable, rootNodeId, List.of(original));
		DecodedModel decoded = IndexedModelDecoder.decodeModel(encoded);

		assertEquals(1, decoded.getNodes().size());
		ArrayNode decodedArray = (ArrayNode) decoded.getNodes().get(0).getNode();
		assertEquals(3L, decodedArray.getLength());
		// Verify elements are present
		assertNotNull(decodedArray.getElements());
		assertEquals(3, decodedArray.getElements().size());
	}

	// ==================== Multiple Node Types Round-Trip ====================

	@Test
	public void testMixedNodeTypesRoundTrip() throws IOException {
		ClockTable clockTable = new ClockTable(List.of(
			new LogicalTimestamp().setReplicaId(100L).setSequenceNumber(50L),
			new LogicalTimestamp().setReplicaId(200L).setSequenceNumber(100L)
		));
		LogicalTimestamp rootNodeId = new LogicalTimestamp().setReplicaId(100L).setSequenceNumber(1L);

		List<Node> nodes = new ArrayList<>();
		nodes.add(new ConstantNode()
			.setId(new LogicalTimestamp().setReplicaId(100L).setSequenceNumber(2L))
			.setValue(new ConValue(ConType.STRING, "test")));
		nodes.add(new ArrayNode()
			.setId(new LogicalTimestamp().setReplicaId(200L).setSequenceNumber(5L))
			.setLength(0L)
			.setElements(new ArrayList<>()));
		nodes.add(new ConstantNode()
			.setId(new LogicalTimestamp().setReplicaId(100L).setSequenceNumber(3L))
			.setValue(new ConValue(ConType.LONG, 123L)));

		byte[] encoded = IndexedModelEncoder.encodeModel(clockTable, rootNodeId, nodes);
		DecodedModel decoded = IndexedModelDecoder.decodeModel(encoded);

		assertEquals(3, decoded.getNodes().size());

		// Count node types
		int constantCount = 0;
		int arrayCount = 0;
		for (DecodedNode node : decoded.getNodes()) {
			if (node.getNode() instanceof ConstantNode) {
				constantCount++;
			} else if (node.getNode() instanceof ArrayNode) {
				arrayCount++;
			}
		}
		assertEquals(2, constantCount);
		assertEquals(1, arrayCount);
	}

	// ==================== Multiple Sessions Round-Trip ====================

	@Test
	public void testNodesFromMultipleSessionsRoundTrip() throws IOException {
		ClockTable clockTable = new ClockTable(List.of(
			new LogicalTimestamp().setReplicaId(100L).setSequenceNumber(50L),
			new LogicalTimestamp().setReplicaId(200L).setSequenceNumber(100L),
			new LogicalTimestamp().setReplicaId(300L).setSequenceNumber(150L)
		));
		LogicalTimestamp rootNodeId = new LogicalTimestamp().setReplicaId(100L).setSequenceNumber(1L);

		List<Node> nodes = new ArrayList<>();
		nodes.add(new ConstantNode()
			.setId(new LogicalTimestamp().setReplicaId(100L).setSequenceNumber(10L))
			.setValue(new ConValue(ConType.LONG, 1L)));
		nodes.add(new ConstantNode()
			.setId(new LogicalTimestamp().setReplicaId(200L).setSequenceNumber(20L))
			.setValue(new ConValue(ConType.LONG, 2L)));
		nodes.add(new ConstantNode()
			.setId(new LogicalTimestamp().setReplicaId(300L).setSequenceNumber(30L))
			.setValue(new ConValue(ConType.LONG, 3L)));

		byte[] encoded = IndexedModelEncoder.encodeModel(clockTable, rootNodeId, nodes);
		DecodedModel decoded = IndexedModelDecoder.decodeModel(encoded);

		assertEquals(3, decoded.getNodes().size());

		// Verify each replica is represented
		Map<Long, DecodedNode> nodesByReplica = new HashMap<>();
		for (DecodedNode node : decoded.getNodes()) {
			nodesByReplica.put(node.getNodeId().getReplicaId(), node);
		}

		assertTrue(nodesByReplica.containsKey(100L));
		assertTrue(nodesByReplica.containsKey(200L));
		assertTrue(nodesByReplica.containsKey(300L));

		assertEquals(10L, nodesByReplica.get(100L).getNodeId().getSequenceNumber());
		assertEquals(20L, nodesByReplica.get(200L).getNodeId().getSequenceNumber());
		assertEquals(30L, nodesByReplica.get(300L).getNodeId().getSequenceNumber());
	}

	// ==================== Streaming Round-Trip ====================

	@Test
	public void testStreamingRoundTrip() throws IOException {
		ClockTable clockTable = new ClockTable(List.of(
			new LogicalTimestamp().setReplicaId(100L).setSequenceNumber(50L)
		));
		LogicalTimestamp rootNodeId = new LogicalTimestamp().setReplicaId(100L).setSequenceNumber(1L);

		// Create nodes
		List<Node> originalNodes = new ArrayList<>();
		for (int i = 0; i < 10; i++) {
			originalNodes.add(new ConstantNode()
				.setId(new LogicalTimestamp().setReplicaId(100L).setSequenceNumber(i + 2L))
				.setValue(new ConValue(ConType.LONG, (long) i * 100)));
		}

		// Encode using streaming
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		try (IndexedModelEncoder encoder = new IndexedModelEncoder(out, clockTable, rootNodeId)) {
			for (Node node : originalNodes) {
				encoder.writeNode(node);
			}
		}

		// Decode using streaming
		List<DecodedNode> decodedNodes = new ArrayList<>();
		try (IndexedModelDecoder decoder = new IndexedModelDecoder(new ByteArrayInputStream(out.toByteArray()))) {
			assertEquals(clockTable.getClocks().get(0).getReplicaId(),
				decoder.getClockTable().getClocks().get(0).getReplicaId());
			assertEquals(rootNodeId.getSequenceNumber(), decoder.getRootNodeId().getSequenceNumber());

			decoder.forEachNode(decodedNodes::add);
		}

		assertEquals(10, decodedNodes.size());

		// Build map for verification
		Map<Long, Long> valueBySeq = new HashMap<>();
		for (DecodedNode node : decodedNodes) {
			ConstantNode constant = (ConstantNode) node.getNode();
			valueBySeq.put(node.getNodeId().getSequenceNumber(), (Long) constant.getConValue().getValue());
		}

		for (int i = 0; i < 10; i++) {
			assertEquals((long) i * 100, valueBySeq.get(i + 2L));
		}
	}

	// ==================== Large Model Round-Trip ====================

	@Test
	public void testLargeModelRoundTrip() throws IOException {
		ClockTable clockTable = new ClockTable(List.of(
			new LogicalTimestamp().setReplicaId(100L).setSequenceNumber(1000L)
		));
		LogicalTimestamp rootNodeId = new LogicalTimestamp().setReplicaId(100L).setSequenceNumber(1L);

		// Create a model with 100 nodes
		List<Node> originalNodes = new ArrayList<>();
		for (int i = 0; i < 100; i++) {
			originalNodes.add(new ConstantNode()
				.setId(new LogicalTimestamp().setReplicaId(100L).setSequenceNumber(i + 2L))
				.setValue(new ConValue(ConType.STRING, "Node " + i)));
		}

		byte[] encoded = IndexedModelEncoder.encodeModel(clockTable, rootNodeId, originalNodes);
		DecodedModel decoded = IndexedModelDecoder.decodeModel(encoded);

		assertEquals(100, decoded.getNodes().size());
	}

	// ==================== Edge Case Round-Trip ====================

	@Test
	public void testLargeSequenceNumbersRoundTrip() throws IOException {
		// Use values that fit within 57 bits (vu57 limit)
		long maxVu57 = (1L << 57) - 1;
		long largeValue = maxVu57 / 2;

		ClockTable clockTable = new ClockTable(List.of(
			new LogicalTimestamp().setReplicaId(largeValue).setSequenceNumber(largeValue)
		));
		LogicalTimestamp rootNodeId = new LogicalTimestamp()
			.setReplicaId(largeValue)
			.setSequenceNumber(largeValue / 2);

		ConstantNode node = new ConstantNode()
			.setId(new LogicalTimestamp()
				.setReplicaId(largeValue)
				.setSequenceNumber(largeValue / 3))
			.setValue(new ConValue(ConType.LONG, Long.MAX_VALUE));

		byte[] encoded = IndexedModelEncoder.encodeModel(clockTable, rootNodeId, List.of(node));
		DecodedModel decoded = IndexedModelDecoder.decodeModel(encoded);

		assertEquals(largeValue, decoded.getClockTable().getClocks().get(0).getReplicaId());
		assertEquals(largeValue, decoded.getClockTable().getClocks().get(0).getSequenceNumber());
		assertEquals(largeValue / 2, decoded.getRootNodeId().getSequenceNumber());

		DecodedNode decodedNode = decoded.getNodes().get(0);
		assertEquals(largeValue / 3, decodedNode.getNodeId().getSequenceNumber());
		assertEquals(Long.MAX_VALUE, ((ConstantNode) decodedNode.getNode()).getConValue().getValue());
	}

	@Test
	public void testEmptyStringRoundTrip() throws IOException {
		ClockTable clockTable = new ClockTable(List.of(
			new LogicalTimestamp().setReplicaId(100L).setSequenceNumber(50L)
		));
		LogicalTimestamp rootNodeId = new LogicalTimestamp().setReplicaId(100L).setSequenceNumber(1L);

		ConstantNode node = new ConstantNode()
			.setId(new LogicalTimestamp().setReplicaId(100L).setSequenceNumber(2L))
			.setValue(new ConValue(ConType.STRING, ""));

		byte[] encoded = IndexedModelEncoder.encodeModel(clockTable, rootNodeId, List.of(node));
		DecodedModel decoded = IndexedModelDecoder.decodeModel(encoded);

		ConstantNode decodedNode = (ConstantNode) decoded.getNodes().get(0).getNode();
		assertEquals("", decodedNode.getConValue().getValue());
	}

	@Test
	public void testSpecialCharactersInStringRoundTrip() throws IOException {
		ClockTable clockTable = new ClockTable(List.of(
			new LogicalTimestamp().setReplicaId(100L).setSequenceNumber(50L)
		));
		LogicalTimestamp rootNodeId = new LogicalTimestamp().setReplicaId(100L).setSequenceNumber(1L);

		String specialString = "Hello\nWorld\t\"Test\"\\Path🎉";
		ConstantNode node = new ConstantNode()
			.setId(new LogicalTimestamp().setReplicaId(100L).setSequenceNumber(2L))
			.setValue(new ConValue(ConType.STRING, specialString));

		byte[] encoded = IndexedModelEncoder.encodeModel(clockTable, rootNodeId, List.of(node));
		DecodedModel decoded = IndexedModelDecoder.decodeModel(encoded);

		ConstantNode decodedNode = (ConstantNode) decoded.getNodes().get(0).getNode();
		assertEquals(specialString, decodedNode.getConValue().getValue());
	}
}
