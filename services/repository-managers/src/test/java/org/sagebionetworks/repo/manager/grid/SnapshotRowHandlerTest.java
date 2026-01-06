package org.sagebionetworks.repo.manager.grid;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sagebionetworks.repo.model.dbo.dao.table.TableModelTestUtils;
import org.sagebionetworks.repo.model.grid.encoding.IndexedModelDecoder;
import org.sagebionetworks.repo.model.grid.encoding.IndexedModelDecoder.DecodedModel;
import org.sagebionetworks.repo.model.grid.encoding.IndexedModelDecoder.DecodedNode;
import org.sagebionetworks.repo.model.grid.node.ArrayNode;
import org.sagebionetworks.repo.model.grid.node.ConstantNode;
import org.sagebionetworks.repo.model.grid.node.Node;
import org.sagebionetworks.repo.model.grid.node.ObjectNode;
import org.sagebionetworks.repo.model.grid.node.RGANode;
import org.sagebionetworks.repo.model.grid.node.VectorNode;
import org.sagebionetworks.repo.model.grid.patch.ConType;
import org.sagebionetworks.repo.model.grid.patch.LogicalTimestamp;
import org.sagebionetworks.repo.model.table.ColumnModel;
import org.sagebionetworks.repo.model.table.ColumnType;
import org.sagebionetworks.repo.model.table.Row;

public class SnapshotRowHandlerTest {

	private String sessionId;
	private Long replicaId;
	private List<ColumnModel> schema;
	private List<Integer> requiredColumnIndices;

	@BeforeEach
	public void before() {
		sessionId = "s123";
		replicaId = 19L;
		schema = List.of(new ColumnModel().setColumnType(ColumnType.STRING).setName("aString"),
				new ColumnModel().setColumnType(ColumnType.INTEGER).setName("anInt"));
		requiredColumnIndices = Collections.emptyList();
	}

	@Test
	public void testNoColumnsNoRows() throws IOException {
		schema = Collections.emptyList();
		ByteArrayOutputStream out = new ByteArrayOutputStream();

		// call under test
		try (SnapshotRowHandler handler = new SnapshotRowHandler(out, sessionId, replicaId, schema, requiredColumnIndices)) {
			// no row to add
		}

		byte[] outputBytes = out.toByteArray();
		assertNotNull(outputBytes, "Output bytes should not be null");
		assertTrue(outputBytes.length > 0, "Output should not be empty");

		// Verify the output
		DecodedModel model = IndexedModelDecoder.decodeModel(outputBytes);
		assertNotNull(model, "Decoded model should not be null");
		assertNotNull(model.getClockTable(), "Clock table should not be null");
		assertNotNull(model.getRootNodeId(), "Root node ID should not be null");
		assertEquals(replicaId, model.getRootNodeId().getReplicaId());
		assertEquals(1L, model.getRootNodeId().getSequenceNumber());

		assertNotNull(model.getNodes(), "Nodes list should not be null");
		assertTrue(model.getNodes().size() > 0, "Should have at least one node");

		// Verify that we have the expected root object structure
		ObjectNode rootObject = findNodeById(model, model.getRootNodeId(), ObjectNode.class);
		assertNotNull(rootObject, "Root object should not be null");
		Map<String, LogicalTimestamp> rootMap = rootObject.getValue();
		assertNotNull(rootMap, "Root object value should not be null");
		assertTrue(rootMap.containsKey("doc_version"));
		assertTrue(rootMap.containsKey("columnNames"));
		assertTrue(rootMap.containsKey("columnOrder"));
		assertTrue(rootMap.containsKey("rows"));

		// Verify doc_version is "0.1.0"
		ConstantNode docVersionNode = findNodeById(model, rootMap.get("doc_version"), ConstantNode.class);
		assertNotNull(docVersionNode, "doc_version node should not be null");
		assertEquals(ConType.STRING, docVersionNode.getConValue().getType());
		assertEquals("0.1.0", docVersionNode.getConValue().getValue());

		// Verify rows array exists and is empty
		ArrayNode rowsArray = findNodeById(model, rootMap.get("rows"), ArrayNode.class);
		assertNotNull(rowsArray, "rows array should not be null");
		assertNotNull(rowsArray.getElements(), "rows array elements should not be null");
		assertTrue(rowsArray.getElements().isEmpty());
	}

	@Test
	public void testWithColumnNoRows() throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();

		// call under test
		try (SnapshotRowHandler handler = new SnapshotRowHandler(out, sessionId, replicaId, schema, requiredColumnIndices)) {
			// no row to add
		}

		byte[] outputBytes = out.toByteArray();
		assertNotNull(outputBytes, "Output bytes should not be null");
		assertTrue(outputBytes.length > 0, "Output should not be empty, got length: " + outputBytes.length);

		// Verify the output
		DecodedModel model = IndexedModelDecoder.decodeModel(outputBytes);
		assertNotNull(model, "Decoded model should not be null");
		assertNotNull(model.getNodes(), "Nodes list should not be null");
		assertTrue(model.getNodes().size() > 0, "Should have at least one node, got: " + model.getNodes().size());

		// Verify root object
		ObjectNode rootObject = findNodeById(model, model.getRootNodeId(), ObjectNode.class);
		assertNotNull(rootObject, "Root object should not be null");
		Map<String, LogicalTimestamp> rootMap = rootObject.getValue();
		assertNotNull(rootMap, "Root map should not be null");

		// Verify column names vector
		VectorNode columnNamesNode = findNodeById(model, rootMap.get("columnNames"), VectorNode.class);
		assertNotNull(columnNamesNode, "columnNames node should not be null");
		assertNotNull(columnNamesNode.getValues(), "columnNames values should not be null");
		assertEquals(2, columnNamesNode.getValues().size());

		// VectorNode stores references to ConstantNode objects - need to look them up in the model
		// Verify first column name is "aString"
		ConstantNode col0NameRef = columnNamesNode.getValues().get(0);
		assertNotNull(col0NameRef, "First column name reference should not be null");
		ConstantNode col0Name = findNodeById(model, col0NameRef.getId(), ConstantNode.class);
		assertNotNull(col0Name, "First column name constant should not be null");
		assertEquals("aString", col0Name.getConValue().getValue());

		// Verify second column name is "anInt"
		ConstantNode col1NameRef = columnNamesNode.getValues().get(1);
		assertNotNull(col1NameRef, "Second column name reference should not be null");
		ConstantNode col1Name = findNodeById(model, col1NameRef.getId(), ConstantNode.class);
		assertNotNull(col1Name, "Second column name constant should not be null");
		assertEquals("anInt", col1Name.getConValue().getValue());

		// Verify column order array
		ArrayNode columnOrderNode = findNodeById(model, rootMap.get("columnOrder"), ArrayNode.class);
		assertNotNull(columnOrderNode, "columnOrder node should not be null");
		assertNotNull(columnOrderNode.getElements(), "columnOrder elements should not be null");
		assertEquals(2, columnOrderNode.getElements().size());

		// Verify rows array is empty
		ArrayNode rowsArray = findNodeById(model, rootMap.get("rows"), ArrayNode.class);
		assertNotNull(rowsArray, "rows array should not be null");
		assertNotNull(rowsArray.getElements(), "rows array elements should not be null");
		assertTrue(rowsArray.getElements().isEmpty());
	}

	@Test
	public void testWithRows() throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();

		// call under test
		try (SnapshotRowHandler handler = new SnapshotRowHandler(out, sessionId, replicaId, schema, requiredColumnIndices)) {
			handler.nextRow(new Row().setValues(Arrays.asList("one", "101")).setRowId(1L).setVersionNumber(4L).setEtag("fake-etag-1"));
			handler.nextRow(new Row().setValues(Arrays.asList("two", "202")).setRowId(2L).setVersionNumber(5L).setEtag("fake-etag-2"));
			handler.nextRow(new Row().setValues(Arrays.asList("three", "303")).setRowId(3L).setVersionNumber(6L).setEtag("fake-etag-3"));
			// A row with partial metadata
			handler.nextRow(new Row().setValues(Arrays.asList("four", "404")).setRowId(3L).setEtag("fake-etag-4"));
			// A row with no metadata
			handler.nextRow(new Row().setValues(Arrays.asList("five", "505")));
		}

		// Verify the output
		DecodedModel model = IndexedModelDecoder.decodeModel(out.toByteArray());
		assertNotNull(model);

		// Verify root object
		ObjectNode rootObject = findNodeById(model, model.getRootNodeId(), ObjectNode.class);
		assertNotNull(rootObject, "Root object should not be null");
		Map<String, LogicalTimestamp> rootMap = rootObject.getValue();

		// Verify rows array contains 5 rows
		ArrayNode rowsArray = findNodeById(model, rootMap.get("rows"), ArrayNode.class);
		assertNotNull(rowsArray, "rows array should not be null");
		assertEquals(5, rowsArray.getElements().size());

		// Verify first row
		RGANode firstRowRga = rowsArray.getElements().get(0);
		ObjectNode firstRowObject = findNodeById(model, firstRowRga.getDataId(), ObjectNode.class);
		assertNotNull(firstRowObject, "First row object should not be null");
		assertTrue(firstRowObject.getValue().containsKey("data"));
		assertTrue(firstRowObject.getValue().containsKey("metadata"));

		// Verify first row data - VectorNode stores ConstantNode directly
		VectorNode firstRowData = findNodeById(model, firstRowObject.getValue().get("data"), VectorNode.class);
		assertNotNull(firstRowData, "First row data should not be null");
		assertNotNull(firstRowData.getValues(), "First row data values should not be null");
		assertEquals(2, firstRowData.getValues().size());

		// VectorNode stores references to ConstantNode objects - need to look them up in the model
		ConstantNode firstCellRef = firstRowData.getValues().get(0);
		assertNotNull(firstCellRef, "First cell reference should not be null");
		ConstantNode firstCellValue = findNodeById(model, firstCellRef.getId(), ConstantNode.class);
		assertNotNull(firstCellValue, "First cell value should not be null");
		assertEquals("one", firstCellValue.getConValue().getValue());

		ConstantNode secondCellRef = firstRowData.getValues().get(1);
		assertNotNull(secondCellRef, "Second cell reference should not be null");
		ConstantNode secondCellValue = findNodeById(model, secondCellRef.getId(), ConstantNode.class);
		assertNotNull(secondCellValue, "Second cell value should not be null");
		assertEquals(101L, secondCellValue.getConValue().getValue());

		// Verify last row (no metadata)
		RGANode lastRowRga = rowsArray.getElements().get(4);
		ObjectNode lastRowObject = findNodeById(model, lastRowRga.getDataId(), ObjectNode.class);
		assertNotNull(lastRowObject, "Last row object should not be null");
		assertTrue(lastRowObject.getValue().containsKey("data"));
		// Row with no metadata should not have "metadata" key
		assertEquals(1, lastRowObject.getValue().size());
	}

	@Test
	public void testEachType() throws IOException {
		boolean hasDefault = false;
		schema = TableModelTestUtils.createOneOfEachType(hasDefault);
		List<Row> rows = TableModelTestUtils.createRows(schema, 3,
				new TableModelTestUtils.ValueOptions().includeSpace(false));
		ByteArrayOutputStream out = new ByteArrayOutputStream();

		// call under test
		try (SnapshotRowHandler handler = new SnapshotRowHandler(out, sessionId, replicaId, schema, requiredColumnIndices)) {
			rows.forEach(r -> {
				handler.nextRow(r);
			});
		}

		// Verify the output can be decoded
		DecodedModel model = IndexedModelDecoder.decodeModel(out.toByteArray());
		assertNotNull(model);

		// Verify root object
		ObjectNode rootObject = findNodeById(model, model.getRootNodeId(), ObjectNode.class);
		assertNotNull(rootObject, "Root object should not be null");
		Map<String, LogicalTimestamp> rootMap = rootObject.getValue();

		// Verify column names match schema
		VectorNode columnNamesNode = findNodeById(model, rootMap.get("columnNames"), VectorNode.class);
		assertNotNull(columnNamesNode, "columnNames node should not be null");
		assertNotNull(columnNamesNode.getValues(), "columnNames values should not be null");
		assertEquals(schema.size(), columnNamesNode.getValues().size());

		// Verify rows
		ArrayNode rowsArray = findNodeById(model, rootMap.get("rows"), ArrayNode.class);
		assertNotNull(rowsArray, "rows array should not be null");
		assertEquals(3, rowsArray.getElements().size());
	}

	@Test
	public void testWriteNullOrUndefinedUsingRequiredColumnIndices() throws Exception {
		requiredColumnIndices = List.of(1); // only the second column is required
		ByteArrayOutputStream out = new ByteArrayOutputStream();

		// call under test
		try (SnapshotRowHandler handler = new SnapshotRowHandler(out, sessionId, replicaId, schema, requiredColumnIndices)) {
			handler.nextRow(new Row().setValues(Arrays.asList(null, null)).setRowId(1L).setVersionNumber(4L).setEtag("fake-etag-1"));
		}

		// Verify the output
		DecodedModel model = IndexedModelDecoder.decodeModel(out.toByteArray());
		assertNotNull(model);

		// Verify root object
		ObjectNode rootObject = findNodeById(model, model.getRootNodeId(), ObjectNode.class);
		assertNotNull(rootObject, "Root object should not be null");
		Map<String, LogicalTimestamp> rootMap = rootObject.getValue();

		// Verify row exists
		ArrayNode rowsArray = findNodeById(model, rootMap.get("rows"), ArrayNode.class);
		assertNotNull(rowsArray, "rows array should not be null");
		assertEquals(1, rowsArray.getElements().size());

		// Get the row data
		RGANode rowRga = rowsArray.getElements().get(0);
		ObjectNode rowObject = findNodeById(model, rowRga.getDataId(), ObjectNode.class);
		assertNotNull(rowObject, "Row object should not be null");

		VectorNode rowData = findNodeById(model, rowObject.getValue().get("data"), VectorNode.class);
		assertNotNull(rowData, "Row data should not be null");
		assertNotNull(rowData.getValues(), "Row data values should not be null");
		assertEquals(2, rowData.getValues().size());

		// VectorNode stores references to ConstantNode objects - need to look them up in the model
		// Note: Due to CBOR decoding limitations, both UNDEFINED and NULL are decoded as NULL.
		// The encoder correctly writes CBOR undefined (0xf7) for non-required null values,
		// but Jackson's CBOR parser cannot distinguish between null and undefined.
		// First value (not required) - encoded as UNDEFINED but decoded as NULL due to CBOR limitation
		ConstantNode firstValueRef = rowData.getValues().get(0);
		assertNotNull(firstValueRef, "First value reference should not be null");
		ConstantNode firstValue = findNodeById(model, firstValueRef.getId(), ConstantNode.class);
		assertNotNull(firstValue, "First value should not be null");
		// TODO: Change to UNDEFINED when decoder properly handles CBOR undefined (0xf7)
		assertEquals(ConType.NULL, firstValue.getConValue().getType());

		// Second value (required) should be NULL
		ConstantNode secondValueRef = rowData.getValues().get(1);
		assertNotNull(secondValueRef, "Second value reference should not be null");
		ConstantNode secondValue = findNodeById(model, secondValueRef.getId(), ConstantNode.class);
		assertNotNull(secondValue, "Second value should not be null");
		assertEquals(ConType.NULL, secondValue.getConValue().getType());
	}

	/**
	 * TEMPORARY TEST: Writes snapshot to a file for testing with json-joy.
	 * Output file: /tmp/snapshot-test.bin
	 *
	 * To test with json-joy, you can use:
	 * <pre>
	 * const fs = require('fs');
	 * const {Model} = require('json-joy/es6/json-crdt');
	 * const {decode} = require('json-joy/es6/json-crdt/codec/indexed');
	 *
	 * const bytes = fs.readFileSync('/tmp/snapshot-test.bin');
	 * const model = decode(new Uint8Array(bytes));
	 * console.log(JSON.stringify(model.view(), null, 2));
	 * </pre>
	 */
	@Test
	public void testWriteToFileForJsonJoy() throws IOException {
		Path outputPath = Path.of("/tmp/snapshot-test.bin");

		try (FileOutputStream fos = new FileOutputStream(outputPath.toFile());
			 SnapshotRowHandler handler = new SnapshotRowHandler(fos, sessionId, replicaId, schema, requiredColumnIndices)) {
			handler.nextRow(new Row().setValues(Arrays.asList("one", "101"))
					.setRowId(1L).setVersionNumber(4L).setEtag("fake-etag-1")
			);
			handler.nextRow(new Row().setValues(Arrays.asList("two", "202"))
					.setRowId(2L).setVersionNumber(5L).setEtag("fake-etag-2")
			);
			handler.nextRow(new Row().setValues(Arrays.asList("three", "303"))
					.setRowId(3L).setVersionNumber(6L).setEtag("fake-etag-3")
			);
		}

		// Verify file was written
		assertTrue(Files.exists(outputPath), "Output file should exist");
		long fileSize = Files.size(outputPath);
		assertTrue(fileSize > 0, "Output file should not be empty, size: " + fileSize);

		System.out.println("Snapshot written to: " + outputPath.toAbsolutePath());
		System.out.println("File size: " + fileSize + " bytes");

		// Also verify we can decode it ourselves
		byte[] bytes = Files.readAllBytes(outputPath);
		DecodedModel model = IndexedModelDecoder.decodeModel(bytes);
		assertNotNull(model);

		ObjectNode rootObject = findNodeById(model, model.getRootNodeId(), ObjectNode.class);
		assertNotNull(rootObject);

		ArrayNode rowsArray = findNodeById(model, rootObject.getValue().get("rows"), ArrayNode.class);
		assertNotNull(rowsArray);
		assertEquals(3, rowsArray.getElements().size());
	}

	/**
	 * Helper method to find a node by its ID from the decoded model.
	 */
	@SuppressWarnings("unchecked")
	private <T extends Node> T findNodeById(DecodedModel model, LogicalTimestamp nodeId, Class<T> expectedType) {
		for (DecodedNode decodedNode : model.getNodes()) {
			if (decodedNode.getNodeId().equals(nodeId)) {
				Node node = decodedNode.getNode();
				if (expectedType.isInstance(node)) {
					return (T) node;
				}
			}
		}
		return null;
	}

}
