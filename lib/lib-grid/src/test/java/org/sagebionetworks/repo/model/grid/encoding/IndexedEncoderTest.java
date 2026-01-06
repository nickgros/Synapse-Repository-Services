package org.sagebionetworks.repo.model.grid.encoding;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sagebionetworks.repo.model.grid.ClockTable;
import org.sagebionetworks.repo.model.grid.EncodingUtils;
import org.sagebionetworks.repo.model.grid.node.ArrayNode;
import org.sagebionetworks.repo.model.grid.node.ConstantNode;
import org.sagebionetworks.repo.model.grid.node.ObjectNode;
import org.sagebionetworks.repo.model.grid.node.RGANode;
import org.sagebionetworks.repo.model.grid.node.VectorNode;
import org.sagebionetworks.repo.model.grid.patch.ConType;
import org.sagebionetworks.repo.model.grid.patch.ConValue;
import org.sagebionetworks.repo.model.grid.patch.LogicalTimestamp;

public class IndexedEncoderTest {

	private IndexedEncoder encoder;
	private ClockTable clockTable;

	@BeforeEach
	public void setUp() {
		encoder = new IndexedEncoder();
		// Create a clock table with a single session
		clockTable = new ClockTable(List.of(
			new LogicalTimestamp().setReplicaId(100L).setSequenceNumber(50L)
		));
	}

	// ==================== writeNodeTypeAndLength Tests ====================

	@Test
	public void testWriteNodeTypeAndLength_SmallLength() throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();

		int bytesWritten = encoder.writeNodeTypeAndLength(0b000, 5L, out);

		assertEquals(1, bytesWritten);
		// Type 000 in upper 4 bits (after shift), length 5 in lower 5 bits
		// 0b0000_0101 = 0x05
		assertEquals(0x05, out.toByteArray()[0] & 0xFF);
	}

	@Test
	public void testWriteNodeTypeAndLength_MaxSmallLength() throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();

		int bytesWritten = encoder.writeNodeTypeAndLength(0b010, 30L, out);

		assertEquals(1, bytesWritten);
		// Type 010 shifted left 5 = 0100_0000 = 0x40, length 30 = 0x1E
		// Combined: 0x40 | 0x1E = 0x5E
		assertEquals(0x5E, out.toByteArray()[0] & 0xFF);
	}

	@Test
	public void testWriteNodeTypeAndLength_LargeLength() throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();

		int bytesWritten = encoder.writeNodeTypeAndLength(0b011, 100L, out);

		// First byte: type in bits 7-5, 0x1F in bits 4-0 (length extension indicator)
		// Type 011 shifted left 5 = 0110_0000 = 0x60, extension = 0x1F
		// Combined: 0x60 | 0x1F = 0x7F
		assertEquals(0x7F, out.toByteArray()[0] & 0xFF);

		// Remaining bytes should be vu57 encoding of 100
		byte[] expectedVu57 = EncodingUtils.encodeVu57(100L);
		assertEquals(1 + expectedVu57.length, bytesWritten);
	}

	// ==================== ConstantNode Encode Tests ====================

	@Test
	public void testEncodeConstantNode_CborInteger() throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ConstantNode node = new ConstantNode()
			.setId(new LogicalTimestamp().setReplicaId(100L).setSequenceNumber(50L))
			.setValue(new ConValue(ConType.LONG, 42L));

		int bytesWritten = encoder.encode(node, clockTable, out);

		byte[] result = out.toByteArray();
		// First byte: type 000, length 0 -> 0x00
		assertEquals(0x00, result[0] & 0xFF);
		// Remaining bytes: CBOR encoding of 42 (should be 0x18, 0x2A for positive int 42)
		assertEquals(bytesWritten, result.length);
	}

	@Test
	public void testEncodeConstantNode_CborString() throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ConstantNode node = new ConstantNode()
			.setId(new LogicalTimestamp().setReplicaId(100L).setSequenceNumber(50L))
			.setValue(new ConValue(ConType.STRING, "hello"));

		int bytesWritten = encoder.encode(node, clockTable, out);

		byte[] result = out.toByteArray();
		// First byte: type 000, length 0 -> 0x00
		assertEquals(0x00, result[0] & 0xFF);
		assertEquals(bytesWritten, result.length);
	}

	@Test
	public void testEncodeConstantNode_CborBoolean() throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ConstantNode node = new ConstantNode()
			.setId(new LogicalTimestamp().setReplicaId(100L).setSequenceNumber(50L))
			.setValue(new ConValue(ConType.BOOLEAN, true));

		int bytesWritten = encoder.encode(node, clockTable, out);

		byte[] result = out.toByteArray();
		// First byte: type 000, length 0 -> 0x00
		assertEquals(0x00, result[0] & 0xFF);
		// CBOR true is 0xF5
		assertEquals(0xF5, result[1] & 0xFF);
		assertEquals(2, bytesWritten);
	}

	@Test
	public void testEncodeConstantNode_CborNull() throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ConstantNode node = new ConstantNode()
			.setId(new LogicalTimestamp().setReplicaId(100L).setSequenceNumber(50L))
			.setValue(new ConValue(ConType.NULL, null));

		int bytesWritten = encoder.encode(node, clockTable, out);

		byte[] result = out.toByteArray();
		// First byte: type 000, length 0 -> 0x00
		assertEquals(0x00, result[0] & 0xFF);
		// CBOR null is 0xF6
		assertEquals(0xF6, result[1] & 0xFF);
		assertEquals(2, bytesWritten);
	}

	@Test
	public void testEncodeConstantNode_Timestamp() throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		// Use sequence number < 16 to test single-byte raw encoding
		LogicalTimestamp timestampValue = new LogicalTimestamp().setReplicaId(100L).setSequenceNumber(5L);
		ConstantNode node = new ConstantNode()
			.setId(new LogicalTimestamp().setReplicaId(100L).setSequenceNumber(10L))
			.setValue(new ConValue(ConType.TIMESTAMP, timestampValue));

		int bytesWritten = encoder.encode(node, clockTable, out);

		byte[] result = out.toByteArray();
		// First byte: type 000, length 1 (timestamp indicator) -> 0x01
		assertEquals(0x01, result[0] & 0xFF);
		// Remaining bytes: encoded timestamp using raw encoding
		// sessionIndex=0, rawSeq=5 -> b1u3u4 encoding: 0b0_000_0101 = 0x05
		assertEquals(0x05, result[1] & 0xFF);
		assertEquals(2, bytesWritten);
	}

	@Test
	public void testEncodeConstantNode_Undefined() throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ConstantNode node = new ConstantNode()
			.setId(new LogicalTimestamp().setReplicaId(100L).setSequenceNumber(50L))
			.setValue(new ConValue(ConType.UNDEFINED, null));

		int bytesWritten = encoder.encode(node, clockTable, out);

		byte[] result = out.toByteArray();
		// First byte: type 000, length 0 -> 0x00
		assertEquals(0x00, result[0] & 0xFF);
		// CBOR undefined is 0xF7
		assertEquals(0xF7, result[1] & 0xFF);
		assertEquals(2, bytesWritten);
	}

	@Test
	public void testEncodeConstantNode_NullNode() {
		ByteArrayOutputStream out = new ByteArrayOutputStream();

		assertThrows(IllegalArgumentException.class, () -> {
			encoder.encode((ConstantNode) null, clockTable, out);
		});
	}

	@Test
	public void testEncodeConstantNode_NullId() {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ConstantNode node = new ConstantNode()
			.setValue(new ConValue(ConType.LONG, 42L));

		assertThrows(IllegalArgumentException.class, () -> {
			encoder.encode(node, clockTable, out);
		});
	}

	// ==================== ArrayNode Encode Tests ====================

	@Test
	public void testEncodeArrayNode_Empty() throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ArrayNode node = new ArrayNode()
			.setId(new LogicalTimestamp().setReplicaId(100L).setSequenceNumber(50L))
			.setLength(0L)
			.setElements(new ArrayList<>());

		int bytesWritten = encoder.encode(node, clockTable, out);

		byte[] result = out.toByteArray();
		// Type 110 (6) shifted left 5 = 1100_0000 = 0xC0, length 0
		assertEquals(0xC0, result[0] & 0xFF);
		assertEquals(1, bytesWritten);
	}

	@Test
	public void testEncodeArrayNode_SingleElement() throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();

		// Element with nodeId (chunk ID) and dataId (content ID)
		// Use sequence numbers < 16 to test single-byte raw encoding
		RGANode element = new RGANode()
			.setNodeId(new LogicalTimestamp().setReplicaId(100L).setSequenceNumber(10L)) // parent array
			.setDataId(new LogicalTimestamp().setReplicaId(100L).setSequenceNumber(4L))    // chunk ID (raw seq 4 -> 0x04)
			.setDataId(new LogicalTimestamp().setReplicaId(100L).setSequenceNumber(5L))    // content ID (raw seq 5 -> 0x05)
			.setIsDeleted(false);

		ArrayNode node = new ArrayNode()
			.setId(new LogicalTimestamp().setReplicaId(100L).setSequenceNumber(10L))
			.setLength(1L)
			.setElements(List.of(element));

		int bytesWritten = encoder.encode(node, clockTable, out);

		byte[] result = out.toByteArray();
		// Type 110 (6) shifted left 5 = 0xC0, length 1 -> 0xC1
		assertEquals(0xC1, result[0] & 0xFF);
		// Chunk ID: sessionIndex=0, rawSeq=4 -> 0x04
		assertEquals(0x04, result[1] & 0xFF);
		// Chunk header: b1u56 with flag=false (not deleted), value=1 (chunk length)
		assertEquals(0x01, result[2] & 0xFF);
		// Content ID: sessionIndex=0, rawSeq=5 -> 0x05
		assertEquals(0x05, result[3] & 0xFF);
		assertEquals(4, bytesWritten);
	}

	@Test
	public void testEncodeArrayNode_DeletedElement() throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();

		// Use sequence numbers < 16 to test single-byte raw encoding
		RGANode deletedElement = new RGANode()
			.setNodeId(new LogicalTimestamp().setReplicaId(100L).setSequenceNumber(10L)) // parent array
			.setDataId(new LogicalTimestamp().setReplicaId(100L).setSequenceNumber(4L))    // chunk ID (raw seq 4 -> 0x04)
			.setIsDeleted(true);
		// No dataId for deleted elements

		ArrayNode node = new ArrayNode()
			.setId(new LogicalTimestamp().setReplicaId(100L).setSequenceNumber(10L))
			.setLength(1L)
			.setElements(List.of(deletedElement));

		int bytesWritten = encoder.encode(node, clockTable, out);

		byte[] result = out.toByteArray();
		// Type 110 (6) shifted left 5 = 0xC0, length 1 -> 0xC1
		assertEquals(0xC1, result[0] & 0xFF);
		// Chunk ID: sessionIndex=0, rawSeq=4 -> 0x04
		assertEquals(0x04, result[1] & 0xFF);
		// Chunk header: b1u56 with flag=true (deleted), value=1 (chunk length)
		// flag bit (0x80) | value 1 = 0x81
		assertEquals(0x81, result[2] & 0xFF);
		// No content ID for deleted elements
		assertEquals(3, bytesWritten);
	}

	@Test
	public void testEncodeArrayNode_MixedElements() throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();

		// Use sequence numbers < 16 to test single-byte raw encoding
		RGANode element1 = new RGANode()
			.setNodeId(new LogicalTimestamp().setReplicaId(100L).setSequenceNumber(10L)) // parent array
			.setDataId(new LogicalTimestamp().setReplicaId(100L).setSequenceNumber(4L))    // chunk ID (raw seq 4 -> 0x04)
			.setDataId(new LogicalTimestamp().setReplicaId(100L).setSequenceNumber(5L))    // content ID (raw seq 5 -> 0x05)
			.setIsDeleted(false);

		RGANode deletedElement = new RGANode()
			.setNodeId(new LogicalTimestamp().setReplicaId(100L).setSequenceNumber(10L)) // parent array
			.setDataId(new LogicalTimestamp().setReplicaId(100L).setSequenceNumber(2L))    // chunk ID (raw seq 2 -> 0x02)
			.setIsDeleted(true);
		// No dataId for deleted elements

		RGANode element2 = new RGANode()
			.setNodeId(new LogicalTimestamp().setReplicaId(100L).setSequenceNumber(10L)) // parent array
			.setDataId(new LogicalTimestamp().setReplicaId(100L).setSequenceNumber(1L))    // chunk ID (raw seq 1 -> 0x01)
			.setDataId(new LogicalTimestamp().setReplicaId(100L).setSequenceNumber(3L))    // content ID (raw seq 3 -> 0x03)
			.setIsDeleted(false);

		ArrayNode node = new ArrayNode()
			.setId(new LogicalTimestamp().setReplicaId(100L).setSequenceNumber(10L))
			.setLength(3L)
			.setElements(List.of(element1, deletedElement, element2));

		int bytesWritten = encoder.encode(node, clockTable, out);

		byte[] result = out.toByteArray();
		// Type 110 (6) shifted left 5 = 0xC0, length 3 -> 0xC3
		assertEquals(0xC3, result[0] & 0xFF);
		// Element 1: chunk ID (rawSeq=4) + header (not deleted, span=1) + content ID (rawSeq=5)
		assertEquals(0x04, result[1] & 0xFF); // chunk ID
		assertEquals(0x01, result[2] & 0xFF); // header: not deleted, span=1
		assertEquals(0x05, result[3] & 0xFF); // content ID
		// Deleted element: chunk ID (rawSeq=2) + header (deleted, span=1)
		assertEquals(0x02, result[4] & 0xFF); // chunk ID
		assertEquals(0x81, result[5] & 0xFF); // header: deleted (0x80) | span=1
		// Element 2: chunk ID (rawSeq=1) + header (not deleted, span=1) + content ID (rawSeq=3)
		assertEquals(0x01, result[6] & 0xFF); // chunk ID
		assertEquals(0x01, result[7] & 0xFF); // header: not deleted, span=1
		assertEquals(0x03, result[8] & 0xFF); // content ID
		assertEquals(9, bytesWritten);
	}

	@Test
	public void testEncodeArrayNode_NullElements() throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ArrayNode node = new ArrayNode()
			.setId(new LogicalTimestamp().setReplicaId(100L).setSequenceNumber(50L))
			.setLength(0L)
			.setElements(null);

		int bytesWritten = encoder.encode(node, clockTable, out);

		// Should handle null elements gracefully
		assertEquals(1, bytesWritten);
	}

	// ==================== ObjectNode Encode Tests ====================

	@Test
	public void testEncodeObjectNode_Empty() throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ObjectNode node = new ObjectNode()
			.setId(new LogicalTimestamp().setReplicaId(100L).setSequenceNumber(50L))
			.setValue(new LinkedHashMap<>());

		int bytesWritten = encoder.encode(node, clockTable, out);

		byte[] result = out.toByteArray();
		// Type 010 (2) shifted left 5 = 0100_0000 = 0x40, length 0 -> 0x40
		assertEquals(0x40, result[0] & 0xFF);
		assertEquals(1, bytesWritten);
	}

	@Test
	public void testEncodeObjectNode_SingleKey() throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();

		Map<String, LogicalTimestamp> map = new LinkedHashMap<>();
		map.put("name", new LogicalTimestamp().setReplicaId(100L).setSequenceNumber(45L));

		ObjectNode node = new ObjectNode()
			.setId(new LogicalTimestamp().setReplicaId(100L).setSequenceNumber(50L))
			.setValue(map);

		int bytesWritten = encoder.encode(node, clockTable, out);

		byte[] result = out.toByteArray();
		// Type 010 (2) shifted left 5 = 0x40, length 1 -> 0x41
		assertEquals(0x41, result[0] & 0xFF);
		// After that: CBOR-encoded "name" followed by encoded timestamp
		assertEquals(bytesWritten, result.length);
	}

	@Test
	public void testEncodeObjectNode_MultipleKeys() throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();

		Map<String, LogicalTimestamp> map = new LinkedHashMap<>();
		map.put("a", new LogicalTimestamp().setReplicaId(100L).setSequenceNumber(45L));
		map.put("b", new LogicalTimestamp().setReplicaId(100L).setSequenceNumber(46L));
		map.put("c", new LogicalTimestamp().setReplicaId(100L).setSequenceNumber(47L));

		ObjectNode node = new ObjectNode()
			.setId(new LogicalTimestamp().setReplicaId(100L).setSequenceNumber(50L))
			.setValue(map);

		int bytesWritten = encoder.encode(node, clockTable, out);

		byte[] result = out.toByteArray();
		// Type 010 (2) shifted left 5 = 0x40, length 3 -> 0x43
		assertEquals(0x43, result[0] & 0xFF);
		assertEquals(bytesWritten, result.length);
	}

	@Test
	public void testEncodeObjectNode_NullValue() {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ObjectNode node = new ObjectNode()
			.setId(new LogicalTimestamp().setReplicaId(100L).setSequenceNumber(50L))
			.setValue(null);

		assertThrows(IllegalArgumentException.class, () -> {
			encoder.encode(node, clockTable, out);
		});
	}

	// ==================== VectorNode Encode Tests ====================

	@Test
	public void testEncodeVectorNode_Empty() throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		VectorNode node = new VectorNode()
			.setId(new LogicalTimestamp().setReplicaId(100L).setSequenceNumber(50L))
			.setValues(new LinkedHashMap<>());

		int bytesWritten = encoder.encode(node, clockTable, out);

		byte[] result = out.toByteArray();
		// Type 011 (3) shifted left 5 = 0110_0000 = 0x60, length 0 -> 0x60
		assertEquals(0x60, result[0] & 0xFF);
		assertEquals(1, bytesWritten);
	}

	@Test
	public void testEncodeVectorNode_SingleElement() throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();

		// Use sequence numbers < 16 to test single-byte raw encoding
		Map<Integer, ConstantNode> values = new LinkedHashMap<>();
		values.put(0, new ConstantNode()
			.setId(new LogicalTimestamp().setReplicaId(100L).setSequenceNumber(5L))  // raw seq 5 -> 0x05
			.setValue(new ConValue(ConType.LONG, 42L)));

		VectorNode node = new VectorNode()
			.setId(new LogicalTimestamp().setReplicaId(100L).setSequenceNumber(10L))
			.setValues(values);

		int bytesWritten = encoder.encode(node, clockTable, out);

		byte[] result = out.toByteArray();
		// Type 011 (3) shifted left 5 = 0x60, length 1 -> 0x61
		assertEquals(0x61, result[0] & 0xFF);
		// After that: encoded node ID at index 0
		// sessionIndex=0, rawSeq=5 -> 0x05
		assertEquals(0x05, result[1] & 0xFF);
		assertEquals(2, bytesWritten);
	}

	@Test
	public void testEncodeVectorNode_SparseIndices() throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();

		// Use sequence numbers < 16 to test single-byte raw encoding
		Map<Integer, ConstantNode> values = new LinkedHashMap<>();
		values.put(0, new ConstantNode()
			.setId(new LogicalTimestamp().setReplicaId(100L).setSequenceNumber(5L))  // raw seq 5 -> 0x05
			.setValue(new ConValue(ConType.LONG, 1L)));
		// Index 1 is missing
		values.put(2, new ConstantNode()
			.setId(new LogicalTimestamp().setReplicaId(100L).setSequenceNumber(4L))  // raw seq 4 -> 0x04
			.setValue(new ConValue(ConType.LONG, 3L)));

		VectorNode node = new VectorNode()
			.setId(new LogicalTimestamp().setReplicaId(100L).setSequenceNumber(10L))
			.setValues(values);

		int bytesWritten = encoder.encode(node, clockTable, out);

		byte[] result = out.toByteArray();
		// Type 011 (3) shifted left 5 = 0x60, length 3 (maxIndex+1) -> 0x63
		assertEquals(0x63, result[0] & 0xFF);
		// Index 0: node ID (rawSeq=5 -> 0x05)
		assertEquals(0x05, result[1] & 0xFF);
		// Index 1: zero byte (missing)
		assertEquals(0x00, result[2] & 0xFF);
		// Index 2: node ID (rawSeq=4 -> 0x04)
		assertEquals(0x04, result[3] & 0xFF);
		assertEquals(4, bytesWritten);
	}

	@Test
	public void testEncodeVectorNode_NullValues() throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		VectorNode node = new VectorNode()
			.setId(new LogicalTimestamp().setReplicaId(100L).setSequenceNumber(50L))
			.setValues(null);

		// The encoder should handle null values - let's see the behavior
		// Looking at the code, it calls node.getValues().size() which will throw NPE
		// This test documents expected behavior
		assertThrows(NullPointerException.class, () -> {
			encoder.encode(node, clockTable, out);
		});
	}

	// ==================== encodeNode (Generic Dispatch) Tests ====================

	@Test
	public void testEncodeNode_ConstantNode() throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ConstantNode node = new ConstantNode()
			.setId(new LogicalTimestamp().setReplicaId(100L).setSequenceNumber(50L))
			.setValue(new ConValue(ConType.LONG, 42L));

		int bytesWritten = encoder.encodeNode(node, clockTable, out);

		assertEquals(bytesWritten, out.toByteArray().length);
	}

	@Test
	public void testEncodeNode_ArrayNode() throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ArrayNode node = new ArrayNode()
			.setId(new LogicalTimestamp().setReplicaId(100L).setSequenceNumber(50L))
			.setLength(0L)
			.setElements(new ArrayList<>());

		int bytesWritten = encoder.encodeNode(node, clockTable, out);

		assertEquals(bytesWritten, out.toByteArray().length);
	}

	@Test
	public void testEncodeNode_ObjectNode() throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ObjectNode node = new ObjectNode()
			.setId(new LogicalTimestamp().setReplicaId(100L).setSequenceNumber(50L))
			.setValue(new LinkedHashMap<>());

		int bytesWritten = encoder.encodeNode(node, clockTable, out);

		assertEquals(bytesWritten, out.toByteArray().length);
	}

	@Test
	public void testEncodeNode_VectorNode() throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		VectorNode node = new VectorNode()
			.setId(new LogicalTimestamp().setReplicaId(100L).setSequenceNumber(50L))
			.setValues(new LinkedHashMap<>());

		int bytesWritten = encoder.encodeNode(node, clockTable, out);

		assertEquals(bytesWritten, out.toByteArray().length);
	}

	// ==================== Multiple ClockTable Sessions Tests ====================

	@Test
	public void testEncodeWithMultipleSessions() throws IOException {
		ClockTable multiSessionClock = new ClockTable(List.of(
			new LogicalTimestamp().setReplicaId(100L).setSequenceNumber(50L),
			new LogicalTimestamp().setReplicaId(200L).setSequenceNumber(100L),
			new LogicalTimestamp().setReplicaId(300L).setSequenceNumber(150L)
		));

		ByteArrayOutputStream out = new ByteArrayOutputStream();

		Map<String, LogicalTimestamp> map = new LinkedHashMap<>();
		map.put("a", new LogicalTimestamp().setReplicaId(100L).setSequenceNumber(45L));
		map.put("b", new LogicalTimestamp().setReplicaId(200L).setSequenceNumber(95L));
		map.put("c", new LogicalTimestamp().setReplicaId(300L).setSequenceNumber(140L));

		ObjectNode node = new ObjectNode()
			.setId(new LogicalTimestamp().setReplicaId(100L).setSequenceNumber(50L))
			.setValue(map);

		int bytesWritten = encoder.encode(node, multiSessionClock, out);

		assertEquals(bytesWritten, out.toByteArray().length);
	}

	@Test
	public void testEncodeTimestampRequiresMultiByteEncoding() throws IOException {
		// Create a clock table where the seqDiff will be >= 16, requiring multi-byte encoding
		ClockTable clockTable = new ClockTable(List.of(
			new LogicalTimestamp().setReplicaId(100L).setSequenceNumber(100L)
		));

		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ConstantNode node = new ConstantNode()
			.setId(new LogicalTimestamp().setReplicaId(100L).setSequenceNumber(100L))
			.setValue(new ConValue(ConType.TIMESTAMP,
				new LogicalTimestamp().setReplicaId(100L).setSequenceNumber(80L))); // seqDiff = 20

		int bytesWritten = encoder.encode(node, clockTable, out);

		byte[] result = out.toByteArray();
		// First byte: type 000, length 1 (timestamp indicator) -> 0x01
		assertEquals(0x01, result[0] & 0xFF);
		// Second byte: b1vu56 with flag=1 for multi-byte timestamp encoding
		assertEquals(0x80, result[1] & 0xFF);
		assertEquals(bytesWritten, result.length);
	}
}
