package org.sagebionetworks.repo.model.grid.encoding;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sagebionetworks.repo.model.grid.ClockTable;
import org.sagebionetworks.repo.model.grid.node.ArrayNode;
import org.sagebionetworks.repo.model.grid.node.ConstantNode;
import org.sagebionetworks.repo.model.grid.node.RGANode;
import org.sagebionetworks.repo.model.grid.node.VectorNode;
import org.sagebionetworks.repo.model.grid.patch.ConType;
import org.sagebionetworks.repo.model.grid.patch.ConValue;
import org.sagebionetworks.repo.model.grid.patch.LogicalTimestamp;

/**
 * Round-trip tests for IndexedEncoder and IndexedDecoder.
 * These tests verify that encoding followed by decoding produces the original data.
 */
public class IndexedEncodingRoundTripTest {

	private IndexedEncoder encoder;
	private IndexedDecoder decoder;
	private ClockTable clockTable;

	@BeforeEach
	public void setUp() {
		encoder = new IndexedEncoder();
		decoder = new IndexedDecoder();
		clockTable = new ClockTable(List.of(
			new LogicalTimestamp().setReplicaId(100L).setSequenceNumber(50L),
			new LogicalTimestamp().setReplicaId(200L).setSequenceNumber(100L)
		));
	}

	// ==================== ConstantNode Round-Trip Tests ====================

	@Test
	public void testConstantNode_Boolean_RoundTrip() throws IOException {
		ConstantNode original = new ConstantNode()
			.setId(new LogicalTimestamp().setReplicaId(100L).setSequenceNumber(50L))
			.setValue(new ConValue(ConType.BOOLEAN, true));

		ConstantNode result = encodeAndDecodeConstantNode(original);

		assertEquals(original.getId(), result.getId());
		assertEquals(original.getConValue().getType(), result.getConValue().getType());
		assertEquals(original.getConValue().getValue(), result.getConValue().getValue());
	}

	@Test
	public void testConstantNode_BooleanFalse_RoundTrip() throws IOException {
		ConstantNode original = new ConstantNode()
			.setId(new LogicalTimestamp().setReplicaId(100L).setSequenceNumber(50L))
			.setValue(new ConValue(ConType.BOOLEAN, false));

		ConstantNode result = encodeAndDecodeConstantNode(original);

		assertEquals(ConType.BOOLEAN, result.getConValue().getType());
		assertEquals(false, result.getConValue().getValue());
	}

	@Test
	public void testConstantNode_Null_RoundTrip() throws IOException {
		ConstantNode original = new ConstantNode()
			.setId(new LogicalTimestamp().setReplicaId(100L).setSequenceNumber(50L))
			.setValue(new ConValue(ConType.NULL, null));

		ConstantNode result = encodeAndDecodeConstantNode(original);

		assertEquals(ConType.NULL, result.getConValue().getType());
	}

	@Test
	public void testConstantNode_SmallInteger_RoundTrip() throws IOException {
		ConstantNode original = new ConstantNode()
			.setId(new LogicalTimestamp().setReplicaId(100L).setSequenceNumber(50L))
			.setValue(new ConValue(ConType.LONG, 42L));

		ConstantNode result = encodeAndDecodeConstantNode(original);

		assertEquals(ConType.LONG, result.getConValue().getType());
		assertEquals(42L, result.getConValue().getValue());
	}

	@Test
	public void testConstantNode_LargeInteger_RoundTrip() throws IOException {
		ConstantNode original = new ConstantNode()
			.setId(new LogicalTimestamp().setReplicaId(100L).setSequenceNumber(50L))
			.setValue(new ConValue(ConType.LONG, 1_000_000_000L));

		ConstantNode result = encodeAndDecodeConstantNode(original);

		assertEquals(ConType.LONG, result.getConValue().getType());
		assertEquals(1_000_000_000L, result.getConValue().getValue());
	}

	@Test
	public void testConstantNode_NegativeInteger_RoundTrip() throws IOException {
		ConstantNode original = new ConstantNode()
			.setId(new LogicalTimestamp().setReplicaId(100L).setSequenceNumber(50L))
			.setValue(new ConValue(ConType.LONG, -42L));

		ConstantNode result = encodeAndDecodeConstantNode(original);

		assertEquals(ConType.LONG, result.getConValue().getType());
		assertEquals(-42L, result.getConValue().getValue());
	}

	@Test
	public void testConstantNode_Double_RoundTrip() throws IOException {
		ConstantNode original = new ConstantNode()
			.setId(new LogicalTimestamp().setReplicaId(100L).setSequenceNumber(50L))
			.setValue(new ConValue(ConType.DOUBLE, 3.14159));

		ConstantNode result = encodeAndDecodeConstantNode(original);

		assertEquals(ConType.DOUBLE, result.getConValue().getType());
		assertEquals(3.14159, (Double) result.getConValue().getValue(), 0.0001);
	}

	@Test
	public void testConstantNode_String_RoundTrip() throws IOException {
		ConstantNode original = new ConstantNode()
			.setId(new LogicalTimestamp().setReplicaId(100L).setSequenceNumber(50L))
			.setValue(new ConValue(ConType.STRING, "Hello, World!"));

		ConstantNode result = encodeAndDecodeConstantNode(original);

		assertEquals(ConType.STRING, result.getConValue().getType());
		assertEquals("Hello, World!", result.getConValue().getValue());
	}

	@Test
	public void testConstantNode_EmptyString_RoundTrip() throws IOException {
		ConstantNode original = new ConstantNode()
			.setId(new LogicalTimestamp().setReplicaId(100L).setSequenceNumber(50L))
			.setValue(new ConValue(ConType.STRING, ""));

		ConstantNode result = encodeAndDecodeConstantNode(original);

		assertEquals(ConType.STRING, result.getConValue().getType());
		assertEquals("", result.getConValue().getValue());
	}

	@Test
	public void testConstantNode_Timestamp_RoundTrip() throws IOException {
		LogicalTimestamp timestampValue = new LogicalTimestamp().setReplicaId(100L).setSequenceNumber(45L);
		ConstantNode original = new ConstantNode()
			.setId(new LogicalTimestamp().setReplicaId(100L).setSequenceNumber(50L))
			.setValue(new ConValue(ConType.TIMESTAMP, timestampValue));

		ConstantNode result = encodeAndDecodeConstantNode(original);

		assertEquals(ConType.TIMESTAMP, result.getConValue().getType());
		LogicalTimestamp resultTimestamp = (LogicalTimestamp) result.getConValue().getValue();
		assertEquals(timestampValue.getReplicaId(), resultTimestamp.getReplicaId());
		assertEquals(timestampValue.getSequenceNumber(), resultTimestamp.getSequenceNumber());
	}

	@Test
	public void testConstantNode_Timestamp_MultiSession_RoundTrip() throws IOException {
		// Timestamp from session 1
		LogicalTimestamp timestampValue = new LogicalTimestamp().setReplicaId(200L).setSequenceNumber(95L);
		ConstantNode original = new ConstantNode()
			.setId(new LogicalTimestamp().setReplicaId(100L).setSequenceNumber(50L))
			.setValue(new ConValue(ConType.TIMESTAMP, timestampValue));

		ConstantNode result = encodeAndDecodeConstantNode(original);

		assertEquals(ConType.TIMESTAMP, result.getConValue().getType());
		LogicalTimestamp resultTimestamp = (LogicalTimestamp) result.getConValue().getValue();
		assertEquals(timestampValue.getReplicaId(), resultTimestamp.getReplicaId());
		assertEquals(timestampValue.getSequenceNumber(), resultTimestamp.getSequenceNumber());
	}

	// ==================== ArrayNode Round-Trip Tests ====================

	@Test
	public void testArrayNode_Empty_RoundTrip() throws IOException {
		ArrayNode original = new ArrayNode()
			.setId(new LogicalTimestamp().setReplicaId(100L).setSequenceNumber(50L))
			.setLength(0L)
			.setElements(new ArrayList<>());

		ArrayNode result = encodeAndDecodeArrayNode(original);

		assertEquals(original.getId(), result.getId());
		assertEquals(original.getLength(), result.getLength());
		assertTrue(result.getElements().isEmpty());
	}

	@Test
	public void testArrayNode_SingleElement_RoundTrip() throws IOException {
		RGANode element = new RGANode()
			.setNodeId(new LogicalTimestamp().setReplicaId(100L).setSequenceNumber(50L)) // parent array
			.setDataId(new LogicalTimestamp().setReplicaId(100L).setSequenceNumber(46L))   // chunk ID
			.setDataId(new LogicalTimestamp().setReplicaId(100L).setSequenceNumber(45L))   // content ID
			.setIsDeleted(false);

		ArrayNode original = new ArrayNode()
			.setId(new LogicalTimestamp().setReplicaId(100L).setSequenceNumber(50L))
			.setLength(1L)
			.setElements(List.of(element));

		ArrayNode result = encodeAndDecodeArrayNode(original);

		assertEquals(1L, result.getLength());
		assertEquals(1, result.getElements().size());
		assertFalse(result.getElements().get(0).getIsDeleted());
		assertEquals(46L, result.getElements().get(0).getDataId().getSequenceNumber());
		assertEquals(45L, result.getElements().get(0).getDataId().getSequenceNumber());
	}

	@Test
	public void testArrayNode_DeletedElement_RoundTrip() throws IOException {
		RGANode deletedElement = new RGANode()
			.setNodeId(new LogicalTimestamp().setReplicaId(100L).setSequenceNumber(50L)) // parent array
			.setDataId(new LogicalTimestamp().setReplicaId(100L).setSequenceNumber(46L))   // chunk ID
			// No dataId for deleted elements
			.setIsDeleted(true);

		ArrayNode original = new ArrayNode()
			.setId(new LogicalTimestamp().setReplicaId(100L).setSequenceNumber(50L))
			.setLength(1L)
			.setElements(List.of(deletedElement));

		ArrayNode result = encodeAndDecodeArrayNode(original);

		assertEquals(1L, result.getLength());
		assertEquals(1, result.getElements().size());
		assertTrue(result.getElements().get(0).getIsDeleted());
		assertEquals(46L, result.getElements().get(0).getDataId().getSequenceNumber());
	}

	@Test
	public void testArrayNode_MixedElements_RoundTrip() throws IOException {
		List<RGANode> elements = new ArrayList<>();
		elements.add(new RGANode()
			.setNodeId(new LogicalTimestamp().setReplicaId(100L).setSequenceNumber(50L)) // parent
			.setDataId(new LogicalTimestamp().setReplicaId(100L).setSequenceNumber(46L))   // chunk ID
			.setDataId(new LogicalTimestamp().setReplicaId(100L).setSequenceNumber(45L))   // content ID
			.setIsDeleted(false));
		elements.add(new RGANode()
			.setNodeId(new LogicalTimestamp().setReplicaId(100L).setSequenceNumber(50L)) // parent
			.setDataId(new LogicalTimestamp().setReplicaId(100L).setSequenceNumber(48L))   // chunk ID
			// No dataId for deleted elements
			.setIsDeleted(true));
		elements.add(new RGANode()
			.setNodeId(new LogicalTimestamp().setReplicaId(100L).setSequenceNumber(50L)) // parent
			.setDataId(new LogicalTimestamp().setReplicaId(100L).setSequenceNumber(49L))   // chunk ID
			.setDataId(new LogicalTimestamp().setReplicaId(100L).setSequenceNumber(47L))   // content ID
			.setIsDeleted(false));

		ArrayNode original = new ArrayNode()
			.setId(new LogicalTimestamp().setReplicaId(100L).setSequenceNumber(50L))
			.setLength(3L)
			.setElements(elements);

		ArrayNode result = encodeAndDecodeArrayNode(original);

		assertEquals(3L, result.getLength());
		assertEquals(3, result.getElements().size());
		assertFalse(result.getElements().get(0).getIsDeleted());
		assertTrue(result.getElements().get(1).getIsDeleted());
		assertFalse(result.getElements().get(2).getIsDeleted());
	}

	@Test
	public void testArrayNode_MultipleElements_RoundTrip() throws IOException {
		// Using 15 elements to test multiple entries
		List<RGANode> elements = new ArrayList<>();
		for (int i = 0; i < 15; i++) {
			boolean isDeleted = i % 5 == 0; // Every 5th element is deleted
			RGANode element = new RGANode()
				.setNodeId(new LogicalTimestamp().setReplicaId(100L).setSequenceNumber(50L)) // parent
				.setDataId(new LogicalTimestamp().setReplicaId(100L).setSequenceNumber((long) (30 + i))) // chunk ID
				.setIsDeleted(isDeleted);
			if (!isDeleted) {
				element.setDataId(new LogicalTimestamp().setReplicaId(100L).setSequenceNumber((long) i)); // content ID
			}
			elements.add(element);
		}

		ArrayNode original = new ArrayNode()
			.setId(new LogicalTimestamp().setReplicaId(100L).setSequenceNumber(50L))
			.setLength(15L)
			.setElements(elements);

		ArrayNode result = encodeAndDecodeArrayNode(original);

		assertEquals(15L, result.getLength());
		assertEquals(15, result.getElements().size());
		for (int i = 0; i < 15; i++) {
			assertEquals(i % 5 == 0, result.getElements().get(i).getIsDeleted(),
				"Element " + i + " should be " + (i % 5 == 0 ? "deleted" : "not deleted"));
		}
	}

	@Test
	public void testArrayNode_MultiSession_RoundTrip() throws IOException {
		// Test array with elements from different replicas
		List<RGANode> elements = new ArrayList<>();
		elements.add(new RGANode()
			.setNodeId(new LogicalTimestamp().setReplicaId(100L).setSequenceNumber(50L))
			.setDataId(new LogicalTimestamp().setReplicaId(100L).setSequenceNumber(10L))
			.setDataId(new LogicalTimestamp().setReplicaId(200L).setSequenceNumber(5L))  // data from different replica
			.setIsDeleted(false));
		elements.add(new RGANode()
			.setNodeId(new LogicalTimestamp().setReplicaId(100L).setSequenceNumber(50L))
			.setDataId(new LogicalTimestamp().setReplicaId(200L).setSequenceNumber(15L))  // chunk from different replica
			.setDataId(new LogicalTimestamp().setReplicaId(100L).setSequenceNumber(20L))
			.setIsDeleted(false));

		ArrayNode original = new ArrayNode()
			.setId(new LogicalTimestamp().setReplicaId(100L).setSequenceNumber(50L))
			.setLength(2L)
			.setElements(elements);

		ArrayNode result = encodeAndDecodeArrayNode(original);

		assertEquals(2L, result.getLength());
		assertEquals(2, result.getElements().size());
		assertEquals(100L, result.getElements().get(0).getDataId().getReplicaId());
		assertEquals(200L, result.getElements().get(0).getDataId().getReplicaId());
		assertEquals(200L, result.getElements().get(1).getDataId().getReplicaId());
		assertEquals(100L, result.getElements().get(1).getDataId().getReplicaId());
	}

	// ==================== VectorNode Round-Trip Tests ====================

	@Test
	public void testVectorNode_Empty_RoundTrip() throws IOException {
		VectorNode original = new VectorNode()
			.setId(new LogicalTimestamp().setReplicaId(100L).setSequenceNumber(50L))
			.setValues(new LinkedHashMap<>());

		VectorNode result = encodeAndDecodeVectorNode(original);

		assertEquals(original.getId(), result.getId());
		assertTrue(result.getValues().isEmpty());
	}

	@Test
	public void testVectorNode_SingleElement_RoundTrip() throws IOException {
		Map<Integer, ConstantNode> values = new LinkedHashMap<>();
		values.put(0, new ConstantNode()
			.setId(new LogicalTimestamp().setReplicaId(100L).setSequenceNumber(45L))
			.setValue(new ConValue(ConType.LONG, 42L)));

		VectorNode original = new VectorNode()
			.setId(new LogicalTimestamp().setReplicaId(100L).setSequenceNumber(50L))
			.setValues(values);

		VectorNode result = encodeAndDecodeVectorNode(original);

		assertEquals(original.getId(), result.getId());
		assertEquals(1, result.getValues().size());
		assertTrue(result.getValues().containsKey(0));
		assertEquals(45L, result.getValues().get(0).getId().getSequenceNumber());
	}

	@Test
	public void testVectorNode_MultipleElements_RoundTrip() throws IOException {
		Map<Integer, ConstantNode> values = new LinkedHashMap<>();
		values.put(0, new ConstantNode()
			.setId(new LogicalTimestamp().setReplicaId(100L).setSequenceNumber(40L))
			.setValue(new ConValue(ConType.LONG, 1L)));
		values.put(1, new ConstantNode()
			.setId(new LogicalTimestamp().setReplicaId(100L).setSequenceNumber(41L))
			.setValue(new ConValue(ConType.STRING, "hello")));
		values.put(2, new ConstantNode()
			.setId(new LogicalTimestamp().setReplicaId(100L).setSequenceNumber(42L))
			.setValue(new ConValue(ConType.BOOLEAN, true)));

		VectorNode original = new VectorNode()
			.setId(new LogicalTimestamp().setReplicaId(100L).setSequenceNumber(50L))
			.setValues(values);

		VectorNode result = encodeAndDecodeVectorNode(original);

		assertEquals(original.getId(), result.getId());
		assertEquals(3, result.getValues().size());
		assertEquals(40L, result.getValues().get(0).getId().getSequenceNumber());
		assertEquals(41L, result.getValues().get(1).getId().getSequenceNumber());
		assertEquals(42L, result.getValues().get(2).getId().getSequenceNumber());
	}

	@Test
	public void testVectorNode_SparseIndices_RoundTrip() throws IOException {
		// Test vector with gaps in indices (e.g., indices 0 and 2, but not 1)
		Map<Integer, ConstantNode> values = new LinkedHashMap<>();
		values.put(0, new ConstantNode()
			.setId(new LogicalTimestamp().setReplicaId(100L).setSequenceNumber(40L))
			.setValue(new ConValue(ConType.LONG, 100L)));
		values.put(2, new ConstantNode()
			.setId(new LogicalTimestamp().setReplicaId(100L).setSequenceNumber(42L))
			.setValue(new ConValue(ConType.LONG, 300L)));

		VectorNode original = new VectorNode()
			.setId(new LogicalTimestamp().setReplicaId(100L).setSequenceNumber(50L))
			.setValues(values);

		VectorNode result = encodeAndDecodeVectorNode(original);

		assertEquals(original.getId(), result.getId());
		assertEquals(2, result.getValues().size());
		assertTrue(result.getValues().containsKey(0));
		assertTrue(result.getValues().containsKey(2));
		assertFalse(result.getValues().containsKey(1));
		assertEquals(40L, result.getValues().get(0).getId().getSequenceNumber());
		assertEquals(42L, result.getValues().get(2).getId().getSequenceNumber());
	}

	@Test
	public void testVectorNode_LargeSparseIndex_RoundTrip() throws IOException {
		// Test vector with a larger gap
		Map<Integer, ConstantNode> values = new LinkedHashMap<>();
		values.put(0, new ConstantNode()
			.setId(new LogicalTimestamp().setReplicaId(100L).setSequenceNumber(40L))
			.setValue(new ConValue(ConType.LONG, 1L)));
		values.put(5, new ConstantNode()
			.setId(new LogicalTimestamp().setReplicaId(100L).setSequenceNumber(45L))
			.setValue(new ConValue(ConType.LONG, 6L)));

		VectorNode original = new VectorNode()
			.setId(new LogicalTimestamp().setReplicaId(100L).setSequenceNumber(50L))
			.setValues(values);

		VectorNode result = encodeAndDecodeVectorNode(original);

		assertEquals(original.getId(), result.getId());
		assertEquals(2, result.getValues().size());
		assertTrue(result.getValues().containsKey(0));
		assertTrue(result.getValues().containsKey(5));
		for (int i = 1; i < 5; i++) {
			assertFalse(result.getValues().containsKey(i), "Index " + i + " should not be present");
		}
	}

	@Test
	public void testVectorNode_MultiSession_RoundTrip() throws IOException {
		// Test vector with elements from different replicas
		Map<Integer, ConstantNode> values = new LinkedHashMap<>();
		values.put(0, new ConstantNode()
			.setId(new LogicalTimestamp().setReplicaId(100L).setSequenceNumber(40L))
			.setValue(new ConValue(ConType.LONG, 1L)));
		values.put(1, new ConstantNode()
			.setId(new LogicalTimestamp().setReplicaId(200L).setSequenceNumber(80L))  // from different replica
			.setValue(new ConValue(ConType.LONG, 2L)));

		VectorNode original = new VectorNode()
			.setId(new LogicalTimestamp().setReplicaId(100L).setSequenceNumber(50L))
			.setValues(values);

		VectorNode result = encodeAndDecodeVectorNode(original);

		assertEquals(original.getId(), result.getId());
		assertEquals(2, result.getValues().size());
		assertEquals(100L, result.getValues().get(0).getId().getReplicaId());
		assertEquals(200L, result.getValues().get(1).getId().getReplicaId());
	}

	@Test
	public void testVectorNode_ManyElements_RoundTrip() throws IOException {
		// Test vector with many sequential elements
		Map<Integer, ConstantNode> values = new LinkedHashMap<>();
		for (int i = 0; i < 10; i++) {
			values.put(i, new ConstantNode()
				.setId(new LogicalTimestamp().setReplicaId(100L).setSequenceNumber((long) (30 + i)))
				.setValue(new ConValue(ConType.LONG, (long) (i * 10))));
		}

		VectorNode original = new VectorNode()
			.setId(new LogicalTimestamp().setReplicaId(100L).setSequenceNumber(50L))
			.setValues(values);

		VectorNode result = encodeAndDecodeVectorNode(original);

		assertEquals(original.getId(), result.getId());
		assertEquals(10, result.getValues().size());
		for (int i = 0; i < 10; i++) {
			assertTrue(result.getValues().containsKey(i));
			assertEquals(30 + i, result.getValues().get(i).getId().getSequenceNumber());
		}
	}

	private ConstantNode encodeAndDecodeConstantNode(ConstantNode original) throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		encoder.encode(original, clockTable, out);

		ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());
		return decoder.decodeConstantNode(original.getId(), clockTable, in);
	}

	private ArrayNode encodeAndDecodeArrayNode(ArrayNode original) throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		encoder.encode(original, clockTable, out);

		ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());
		return decoder.decodeArrayNode(original.getId(), clockTable, in);
	}

	private VectorNode encodeAndDecodeVectorNode(VectorNode original) throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		encoder.encode(original, clockTable, out);

		ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());
		return decoder.decodeVectorNode(original.getId(), clockTable, in);
	}
}
