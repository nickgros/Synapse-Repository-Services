package org.sagebionetworks.repo.model.grid.encoding;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sagebionetworks.repo.model.grid.ClockTable;
import org.sagebionetworks.repo.model.grid.EncodingUtils;
import org.sagebionetworks.repo.model.grid.encoding.NodeDecoder.TypeAndLength;
import org.sagebionetworks.repo.model.grid.node.ArrayNode;
import org.sagebionetworks.repo.model.grid.node.ConstantNode;
import org.sagebionetworks.repo.model.grid.node.RGANode;
import org.sagebionetworks.repo.model.grid.patch.ConType;
import org.sagebionetworks.repo.model.grid.patch.LogicalTimestamp;

public class IndexedDecoderTest {

	private IndexedDecoder decoder;
	private ClockTable clockTable;

	@BeforeEach
	public void setUp() {
		decoder = new IndexedDecoder();
		// Create a clock table with a single session
		clockTable = new ClockTable(List.of(
			new LogicalTimestamp().setReplicaId(100L).setSequenceNumber(50L)
		));
	}

	// ==================== readNodeTypeAndLength Tests ====================

	@Test
	public void testReadNodeTypeAndLength_SmallLength() throws IOException {
		// Type 0, length 5: 0x05
		byte[] data = new byte[] { 0x05 };
		ByteArrayInputStream in = new ByteArrayInputStream(data);

		TypeAndLength result = decoder.readNodeTypeAndLength(in);

		assertEquals(0, result.getNodeType());
		assertEquals(5, result.getLength());
	}

	@Test
	public void testReadNodeTypeAndLength_Type2_Length10() throws IOException {
		// Type 2 << 5 = 0x40, length 10 = 0x0A -> 0x4A
		byte[] data = new byte[] { 0x4A };
		ByteArrayInputStream in = new ByteArrayInputStream(data);

		TypeAndLength result = decoder.readNodeTypeAndLength(in);

		assertEquals(2, result.getNodeType());
		assertEquals(10, result.getLength());
	}

	@Test
	public void testReadNodeTypeAndLength_LargeLength() throws IOException {
		// Type 3 << 5 = 0x60, length extension = 0x1F -> 0x7F
		// Followed by vu57 encoding of 100
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		baos.write(0x7F);
		baos.write(EncodingUtils.encodeVu57(100L));
		byte[] data = baos.toByteArray();
		ByteArrayInputStream in = new ByteArrayInputStream(data);

		TypeAndLength result = decoder.readNodeTypeAndLength(in);

		assertEquals(3, result.getNodeType());
		assertEquals(100, result.getLength());
	}

	@Test
	public void testReadNodeTypeAndLength_EndOfStream() {
		byte[] data = new byte[] {};
		ByteArrayInputStream in = new ByteArrayInputStream(data);

		assertThrows(IOException.class, () -> {
			decoder.readNodeTypeAndLength(in);
		});
	}

	// ==================== ConstantNode Decode Tests ====================

	@Test
	public void testDecodeConstantNode_CborBoolean() throws IOException {
		// Type 0, length 0: 0x00
		// CBOR true: 0xF5
		byte[] data = new byte[] { 0x00, (byte) 0xF5 };
		ByteArrayInputStream in = new ByteArrayInputStream(data);
		LogicalTimestamp nodeId = new LogicalTimestamp().setReplicaId(100L).setSequenceNumber(50L);

		ConstantNode result = decoder.decodeConstantNode(nodeId, clockTable, in);

		assertNotNull(result);
		assertEquals(nodeId, result.getId());
		assertEquals(ConType.BOOLEAN, result.getConValue().getType());
		assertEquals(true, result.getConValue().getValue());
	}

	@Test
	public void testDecodeConstantNode_CborNull() throws IOException {
		// Type 0, length 0: 0x00
		// CBOR null: 0xF6
		byte[] data = new byte[] { 0x00, (byte) 0xF6 };
		ByteArrayInputStream in = new ByteArrayInputStream(data);
		LogicalTimestamp nodeId = new LogicalTimestamp().setReplicaId(100L).setSequenceNumber(50L);

		ConstantNode result = decoder.decodeConstantNode(nodeId, clockTable, in);

		assertNotNull(result);
		assertEquals(ConType.NULL, result.getConValue().getType());
	}

	@Test
	public void testDecodeConstantNode_CborSmallInteger() throws IOException {
		// Type 0, length 0: 0x00
		// CBOR integer 10: 0x0A
		byte[] data = new byte[] { 0x00, 0x0A };
		ByteArrayInputStream in = new ByteArrayInputStream(data);
		LogicalTimestamp nodeId = new LogicalTimestamp().setReplicaId(100L).setSequenceNumber(50L);

		ConstantNode result = decoder.decodeConstantNode(nodeId, clockTable, in);

		assertNotNull(result);
		assertEquals(ConType.LONG, result.getConValue().getType());
		assertEquals(10L, result.getConValue().getValue());
	}

	@Test
	public void testDecodeConstantNode_Timestamp() throws IOException {
		// Type 0, length 1: 0x01
		// Timestamp: sessionIndex=0, rawSeq=5 -> 0x05
		byte[] data = new byte[] { 0x01, 0x05 };
		ByteArrayInputStream in = new ByteArrayInputStream(data);
		LogicalTimestamp nodeId = new LogicalTimestamp().setReplicaId(100L).setSequenceNumber(50L);

		ConstantNode result = decoder.decodeConstantNode(nodeId, clockTable, in);

		assertNotNull(result);
		assertEquals(ConType.TIMESTAMP, result.getConValue().getType());
		LogicalTimestamp timestampValue = (LogicalTimestamp) result.getConValue().getValue();
		assertEquals(100L, timestampValue.getReplicaId());
		assertEquals(5L, timestampValue.getSequenceNumber()); // raw seq = 5
	}

	@Test
	public void testDecodeConstantNode_WrongType() {
		// Type 2 (object), not constant
		byte[] data = new byte[] { 0x20 };
		ByteArrayInputStream in = new ByteArrayInputStream(data);
		LogicalTimestamp nodeId = new LogicalTimestamp().setReplicaId(100L).setSequenceNumber(50L);

		assertThrows(IllegalArgumentException.class, () -> {
			decoder.decodeConstantNode(nodeId, clockTable, in);
		});
	}

	// ==================== ArrayNode Decode Tests ====================

	@Test
	public void testDecodeArrayNode_Empty() throws IOException {
		// Type 6 << 5 = 0xC0, length 0 -> 0xC0
		byte[] data = new byte[] { (byte) 0xC0 };
		ByteArrayInputStream in = new ByteArrayInputStream(data);
		LogicalTimestamp nodeId = new LogicalTimestamp().setReplicaId(100L).setSequenceNumber(50L);

		ArrayNode result = decoder.decodeArrayNode(nodeId, clockTable, in);

		assertNotNull(result);
		assertEquals(nodeId, result.getId());
		assertEquals(0L, result.getLength());
		assertTrue(result.getElements().isEmpty());
	}

	@Test
	public void testDecodeArrayNode_SingleElement() throws IOException {
		// Type 6 << 5 = 0xC0, length 1 -> 0xC1
		// Chunk ID: sessionIndex=0, rawSeq=4 -> 0x04
		// Chunk header: b1u56 with flag=false (not deleted), value=1 -> 0x01
		// Content ID: sessionIndex=0, rawSeq=5 -> 0x05
		byte[] data = new byte[] { (byte) 0xC1, 0x04, 0x01, 0x05 };
		ByteArrayInputStream in = new ByteArrayInputStream(data);
		LogicalTimestamp nodeId = new LogicalTimestamp().setReplicaId(100L).setSequenceNumber(50L);

		ArrayNode result = decoder.decodeArrayNode(nodeId, clockTable, in);

		assertNotNull(result);
		assertEquals(1L, result.getLength());
		assertEquals(1, result.getElements().size());
		RGANode element = result.getElements().get(0);
		assertFalse(element.getIsDeleted());
		// Chunk ID (nodeId): raw seq = 4
		assertEquals(100L, element.getDataId().getReplicaId());
		assertEquals(4L, element.getDataId().getSequenceNumber());
		// Content ID (dataId): raw seq = 5
		assertEquals(100L, element.getDataId().getReplicaId());
		assertEquals(5L, element.getDataId().getSequenceNumber());
	}

	@Test
	public void testDecodeArrayNode_DeletedElement() throws IOException {
		// Type 6 << 5 = 0xC0, length 1 -> 0xC1
		// Chunk ID: sessionIndex=0, rawSeq=4 -> 0x04
		// Chunk header: b1u56 with flag=true (deleted), value=1 -> 0x81
		// No content ID for deleted elements
		byte[] data = new byte[] { (byte) 0xC1, 0x04, (byte) 0x81 };
		ByteArrayInputStream in = new ByteArrayInputStream(data);
		LogicalTimestamp nodeId = new LogicalTimestamp().setReplicaId(100L).setSequenceNumber(50L);

		ArrayNode result = decoder.decodeArrayNode(nodeId, clockTable, in);

		assertNotNull(result);
		assertEquals(1L, result.getLength());
		assertEquals(1, result.getElements().size());
		RGANode element = result.getElements().get(0);
		assertTrue(element.getIsDeleted());
		// Chunk ID (nodeId): raw seq = 4
		assertEquals(100L, element.getDataId().getReplicaId());
		assertEquals(4L, element.getDataId().getSequenceNumber());
	}

	@Test
	public void testDecodeArrayNode_MixedElements() throws IOException {
		// Type 6 << 5 = 0xC0, length 3 -> 0xC3
		// Element 1: chunk ID 0x04 (rawSeq=4), not deleted header 0x01, content ID 0x05 (rawSeq=5)
		// Element 2: chunk ID 0x02 (rawSeq=2), deleted header 0x81 (no content ID)
		// Element 3: chunk ID 0x01 (rawSeq=1), not deleted header 0x01, content ID 0x03 (rawSeq=3)
		byte[] data = new byte[] { (byte) 0xC3,
			0x04, 0x01, 0x05,  // element 1
			0x02, (byte) 0x81, // element 2 (deleted)
			0x01, 0x01, 0x03   // element 3
		};
		ByteArrayInputStream in = new ByteArrayInputStream(data);
		LogicalTimestamp nodeId = new LogicalTimestamp().setReplicaId(100L).setSequenceNumber(50L);

		ArrayNode result = decoder.decodeArrayNode(nodeId, clockTable, in);

		assertNotNull(result);
		assertEquals(3L, result.getLength());
		assertEquals(3, result.getElements().size());

		assertFalse(result.getElements().get(0).getIsDeleted());
		assertEquals(4L, result.getElements().get(0).getDataId().getSequenceNumber()); // raw seq 4
		assertEquals(5L, result.getElements().get(0).getDataId().getSequenceNumber()); // raw seq 5

		assertTrue(result.getElements().get(1).getIsDeleted());
		assertEquals(2L, result.getElements().get(1).getDataId().getSequenceNumber()); // raw seq 2

		assertFalse(result.getElements().get(2).getIsDeleted());
		assertEquals(1L, result.getElements().get(2).getDataId().getSequenceNumber()); // raw seq 1
		assertEquals(3L, result.getElements().get(2).getDataId().getSequenceNumber()); // raw seq 3
	}

	@Test
	public void testDecodeArrayNode_WrongType() {
		// Type 0 (constant), not array
		byte[] data = new byte[] { 0x00 };
		ByteArrayInputStream in = new ByteArrayInputStream(data);
		LogicalTimestamp nodeId = new LogicalTimestamp().setReplicaId(100L).setSequenceNumber(50L);

		assertThrows(IllegalArgumentException.class, () -> {
			decoder.decodeArrayNode(nodeId, clockTable, in);
		});
	}

	// ==================== decodeNode (Generic Dispatch) Tests ====================

	@Test
	public void testDecodeNode_ConstantNode() throws IOException {
		byte[] data = new byte[] { 0x00, (byte) 0xF5 };
		ByteArrayInputStream in = new ByteArrayInputStream(data);
		LogicalTimestamp nodeId = new LogicalTimestamp().setReplicaId(100L).setSequenceNumber(50L);

		var result = decoder.decodeNode(nodeId, clockTable, in);

		assertTrue(result instanceof ConstantNode);
	}

	@Test
	public void testDecodeNode_ArrayNode() throws IOException {
		// Type 6 << 5 = 0xC0, length 0 -> 0xC0
		byte[] data = new byte[] { (byte) 0xC0 };
		ByteArrayInputStream in = new ByteArrayInputStream(data);
		LogicalTimestamp nodeId = new LogicalTimestamp().setReplicaId(100L).setSequenceNumber(50L);

		var result = decoder.decodeNode(nodeId, clockTable, in);

		assertTrue(result instanceof ArrayNode);
	}

	@Test
	public void testDecodeNode_UnsupportedType() {
		// Type 1 is not supported: 1 << 5 = 0x20
		byte[] data = new byte[] { 0x20 };
		ByteArrayInputStream in = new ByteArrayInputStream(data);
		LogicalTimestamp nodeId = new LogicalTimestamp().setReplicaId(100L).setSequenceNumber(50L);

		assertThrows(IllegalArgumentException.class, () -> {
			decoder.decodeNode(nodeId, clockTable, in);
		});
	}
}
