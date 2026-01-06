package org.sagebionetworks.repo.model.grid.encoding;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sagebionetworks.repo.model.grid.ClockTable;
import org.sagebionetworks.repo.model.grid.node.ArrayNode;
import org.sagebionetworks.repo.model.grid.node.ConstantNode;
import org.sagebionetworks.repo.model.grid.node.Node;
import org.sagebionetworks.repo.model.grid.node.ObjectNode;
import org.sagebionetworks.repo.model.grid.patch.ConType;
import org.sagebionetworks.repo.model.grid.patch.ConValue;
import org.sagebionetworks.repo.model.grid.patch.LogicalTimestamp;

public class IndexedModelEncoderTest {

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

	// ==================== Basic Encoding Tests ====================

	@Test
	public void testEncodeEmptyModel() throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		try (IndexedModelEncoder encoder = new IndexedModelEncoder(out, clockTable, rootNodeId)) {
			// No nodes added
		}

		byte[] result = out.toByteArray();
		assertNotNull(result);
		assertTrue(result.length > 0, "Encoded model should not be empty");
	}

	@Test
	public void testEncodeModelWithSingleNode() throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();

		ConstantNode node = new ConstantNode()
			.setId(new LogicalTimestamp().setReplicaId(100L).setSequenceNumber(2L))
			.setValue(new ConValue(ConType.LONG, 42L));

		try (IndexedModelEncoder encoder = new IndexedModelEncoder(out, clockTable, rootNodeId)) {
			encoder.writeNode(node);
		}

		byte[] result = out.toByteArray();
		assertNotNull(result);
		assertTrue(result.length > 0);
	}

	@Test
	public void testEncodeModelWithMultipleNodes() throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();

		List<Node> nodes = new ArrayList<>();
		nodes.add(new ConstantNode()
			.setId(new LogicalTimestamp().setReplicaId(100L).setSequenceNumber(2L))
			.setValue(new ConValue(ConType.LONG, 42L)));
		nodes.add(new ConstantNode()
			.setId(new LogicalTimestamp().setReplicaId(100L).setSequenceNumber(3L))
			.setValue(new ConValue(ConType.STRING, "hello")));
		nodes.add(new ConstantNode()
			.setId(new LogicalTimestamp().setReplicaId(200L).setSequenceNumber(5L))
			.setValue(new ConValue(ConType.BOOLEAN, true)));

		try (IndexedModelEncoder encoder = new IndexedModelEncoder(out, clockTable, rootNodeId)) {
			for (Node node : nodes) {
				encoder.writeNode(node);
			}
		}

		byte[] result = out.toByteArray();
		assertNotNull(result);
		assertTrue(result.length > 0);
	}

	// ==================== Validation Tests ====================

	@Test
	public void testEncodeNullNode() throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();

		try (IndexedModelEncoder encoder = new IndexedModelEncoder(out, clockTable, rootNodeId)) {
			assertThrows(IllegalArgumentException.class, () -> {
				encoder.writeNode(null);
			});
		}
	}

	@Test
	public void testEncodeNodeWithNullId() throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();

		ConstantNode nodeWithoutId = new ConstantNode()
			.setValue(new ConValue(ConType.LONG, 42L));

		try (IndexedModelEncoder encoder = new IndexedModelEncoder(out, clockTable, rootNodeId)) {
			assertThrows(IllegalArgumentException.class, () -> {
				encoder.writeNode(nodeWithoutId);
			});
		}
	}

	@Test
	public void testEncodeNodeWithUnknownReplicaId() throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();

		ConstantNode nodeWithUnknownReplica = new ConstantNode()
			.setId(new LogicalTimestamp().setReplicaId(999L).setSequenceNumber(1L))
			.setValue(new ConValue(ConType.LONG, 42L));

		try (IndexedModelEncoder encoder = new IndexedModelEncoder(out, clockTable, rootNodeId)) {
			assertThrows(IllegalArgumentException.class, () -> {
				encoder.writeNode(nodeWithUnknownReplica);
			});
		}
	}

	@Test
	public void testWriteAfterClose() throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		IndexedModelEncoder encoder = new IndexedModelEncoder(out, clockTable, rootNodeId);
		encoder.close();

		ConstantNode node = new ConstantNode()
			.setId(new LogicalTimestamp().setReplicaId(100L).setSequenceNumber(2L))
			.setValue(new ConValue(ConType.LONG, 42L));

		assertThrows(IllegalStateException.class, () -> {
			encoder.writeNode(node);
		});
	}

	// ==================== Convenience Method Tests ====================

	@Test
	public void testEncodeModelConvenienceMethod() throws IOException {
		List<Node> nodes = new ArrayList<>();
		nodes.add(new ConstantNode()
			.setId(new LogicalTimestamp().setReplicaId(100L).setSequenceNumber(2L))
			.setValue(new ConValue(ConType.LONG, 42L)));
		nodes.add(new ConstantNode()
			.setId(new LogicalTimestamp().setReplicaId(100L).setSequenceNumber(3L))
			.setValue(new ConValue(ConType.STRING, "hello")));

		byte[] result = IndexedModelEncoder.encodeModel(clockTable, rootNodeId, nodes);

		assertNotNull(result);
		assertTrue(result.length > 0);
	}

	// ==================== Clock Table Access ====================

	@Test
	public void testGetClockTable() throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		try (IndexedModelEncoder encoder = new IndexedModelEncoder(out, clockTable, rootNodeId)) {
			assertEquals(clockTable, encoder.getClockTable());
		}
	}

	// ==================== Streaming Verification ====================

	@Test
	public void testStreamingBehavior() throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();

		try (IndexedModelEncoder encoder = new IndexedModelEncoder(out, clockTable, rootNodeId)) {
			// Add first node
			encoder.writeNode(new ConstantNode()
				.setId(new LogicalTimestamp().setReplicaId(100L).setSequenceNumber(2L))
				.setValue(new ConValue(ConType.LONG, 42L)));

			// Add second node
			encoder.writeNode(new ConstantNode()
				.setId(new LogicalTimestamp().setReplicaId(100L).setSequenceNumber(3L))
				.setValue(new ConValue(ConType.STRING, "hello")));
		}

		// After close, we should have meaningful output
		byte[] result = out.toByteArray();
		assertTrue(result.length > 0, "Encoded model should have content after close");
	}

	// ==================== Different Node Types ====================

	@Test
	public void testEncodeArrayNode() throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();

		ArrayNode arrayNode = new ArrayNode()
			.setId(new LogicalTimestamp().setReplicaId(100L).setSequenceNumber(5L))
			.setLength(0L)
			.setElements(new ArrayList<>());

		try (IndexedModelEncoder encoder = new IndexedModelEncoder(out, clockTable, rootNodeId)) {
			encoder.writeNode(arrayNode);
		}

		byte[] result = out.toByteArray();
		assertNotNull(result);
		assertTrue(result.length > 0);
	}

	// ==================== File Output for json-joy Testing ====================

	@Test
	public void testWriteModelToFile() throws IOException {
		// Create a model with various node types for testing with json-joy

		// Single session for simplicity (like json-joy example)
		ClockTable testClockTable = new ClockTable(List.of(
				new LogicalTimestamp().setReplicaId(1L).setSequenceNumber(7L)
		));

		// Root node at sequence 1
		LogicalTimestamp testRootNodeId = new LogicalTimestamp().setReplicaId(1L).setSequenceNumber(1L);

		List<Node> nodes = new ArrayList<>();


		// Root ObjectNode with 4 keys pointing to child nodes
		Map<String, LogicalTimestamp> rootValue = new HashMap<>();
		rootValue.put("str", new LogicalTimestamp().setReplicaId(1L).setSequenceNumber(2L));
		rootValue.put("num", new LogicalTimestamp().setReplicaId(1L).setSequenceNumber(3L));
		rootValue.put("bool", new LogicalTimestamp().setReplicaId(1L).setSequenceNumber(4L));
		rootValue.put("nil", new LogicalTimestamp().setReplicaId(1L).setSequenceNumber(5L));

		nodes.add(new ObjectNode()
				.setId(new LogicalTimestamp().setReplicaId(1L).setSequenceNumber(1L))
				.setValue(rootValue)
		);

		// Child constant nodes
		nodes.add(new ConstantNode()
			.setId(new LogicalTimestamp().setReplicaId(1L).setSequenceNumber(2L))
			.setValue(new ConValue(ConType.STRING, "Hello, json-joy!")));

		nodes.add(new ConstantNode()
			.setId(new LogicalTimestamp().setReplicaId(1L).setSequenceNumber(3L))
			.setValue(new ConValue(ConType.LONG, 42L)));

		nodes.add(new ConstantNode()
			.setId(new LogicalTimestamp().setReplicaId(1L).setSequenceNumber(4L))
			.setValue(new ConValue(ConType.BOOLEAN, true)));

		nodes.add(new ConstantNode()
			.setId(new LogicalTimestamp().setReplicaId(1L).setSequenceNumber(5L))
			.setValue(new ConValue(ConType.NULL, null)));


		// Encode the model
		byte[] encoded = IndexedModelEncoder.encodeModel(testClockTable, testRootNodeId, nodes);

		// Write to test resources directory
		Path resourcesDir = Paths.get("src/test/resources/indexed-model");
		Files.createDirectories(resourcesDir);
		Path outputFile = resourcesDir.resolve("test-model.cbor");

		try (FileOutputStream fos = new FileOutputStream(outputFile.toFile())) {
			fos.write(encoded);
		}

		System.out.println("Wrote indexed model to: " + outputFile.toAbsolutePath());
		System.out.println("Model size: " + encoded.length + " bytes");

		// Verify the file was written
		assertTrue(Files.exists(outputFile));
		assertTrue(Files.size(outputFile) > 0);
	}
}
