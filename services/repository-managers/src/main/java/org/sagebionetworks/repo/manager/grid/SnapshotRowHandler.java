package org.sagebionetworks.repo.manager.grid;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

import org.json.JSONArray;
import org.json.JSONObject;
import org.sagebionetworks.repo.manager.grid.row.translator.ColumnTypeToConType;
import org.sagebionetworks.repo.manager.grid.row.translator.Translator;
import org.sagebionetworks.repo.model.dao.table.RowHandler;
import org.sagebionetworks.repo.model.grid.ClockTable;
import org.sagebionetworks.repo.model.grid.encoding.IndexedModelEncoder;
import org.sagebionetworks.repo.model.grid.node.ArrayNode;
import org.sagebionetworks.repo.model.grid.node.ConstantNode;
import org.sagebionetworks.repo.model.grid.node.Node;
import org.sagebionetworks.repo.model.grid.node.ObjectNode;
import org.sagebionetworks.repo.model.grid.node.RGANode;
import org.sagebionetworks.repo.model.grid.node.VectorNode;
import org.sagebionetworks.repo.model.grid.patch.ConType;
import org.sagebionetworks.repo.model.grid.patch.ConValue;
import org.sagebionetworks.repo.model.grid.patch.LogicalTimestamp;
import org.sagebionetworks.repo.model.table.ColumnModel;
import org.sagebionetworks.repo.model.table.Row;

/**
 * A handler that can build a snapshot from a table row query.
 */
public class SnapshotRowHandler implements RowHandler {

	private final IndexedModelEncoder encoder;
	private final String sessionId;
	private final Translator[] translators;
	private LogicalTimestamp rootObjectRef;
	private ArrayNode rowsArray;
	private List<RGANode> rowRgaNodes = new ArrayList<>();
	private LogicalTimestamp lastRowRef;
	private final List<Integer> requiredColumnIndices;
	private long nextNodeSequenceNumber = 1;


	public SnapshotRowHandler(OutputStream out, String sessionId, Long replicaId, List<ColumnModel> schema,
							  List<Integer> requiredColumnIndices) {
		super();
		this.sessionId = sessionId;
		this.requiredColumnIndices = requiredColumnIndices;

		List<Node> initialDocumentNodes = new ArrayList<>();

		// initialize an empty document
		ObjectNode rootObjectNode = new ObjectNode().setId(new LogicalTimestamp().setReplicaId(replicaId).setSequenceNumber(getNextSequenceNumber()));
		ClockTable clockTable = new ClockTable(List.of(new LogicalTimestamp().setReplicaId(replicaId).setSequenceNumber(0L)));
		this.encoder = new IndexedModelEncoder(out, clockTable, rootObjectNode.getId());

		this.rootObjectRef = rootObjectNode.getId();
		ConstantNode documentVersionNode = new ConstantNode()
				.setId(new LogicalTimestamp().setReplicaId(replicaId).setSequenceNumber(getNextSequenceNumber()))
				.setValue(new ConValue(ConType.STRING, "0.1.0"));
		VectorNode columnNamesNode = new VectorNode()
				.setId(new LogicalTimestamp().setReplicaId(replicaId).setSequenceNumber(getNextSequenceNumber()))
				.setValues(new LinkedHashMap<>());
		ArrayNode columnOrderNode = new ArrayNode()
				.setId(new LogicalTimestamp().setReplicaId(replicaId).setSequenceNumber(getNextSequenceNumber()))
				.setLength(0L)
				.setElements(new ArrayList<>());
		ArrayNode rowsNode = new ArrayNode()
				.setId(new LogicalTimestamp().setReplicaId(replicaId).setSequenceNumber(getNextSequenceNumber()))
				.setLength(0L)
				.setElements(new ArrayList<>());
		rowsArray = rowsNode;
		lastRowRef = rowsNode.getId();
		Map<String, LogicalTimestamp> objectMap = new LinkedHashMap<>();
		objectMap.put("doc_version", documentVersionNode.getId());
		objectMap.put("columnNames", columnNamesNode.getId());
		objectMap.put("columnOrder", columnOrderNode.getId());
		objectMap.put("rows", rowsNode.getId());

		rootObjectNode.setValue(objectMap);

		initialDocumentNodes.add(rootObjectNode);
		initialDocumentNodes.add(documentVersionNode);
		initialDocumentNodes.add(columnNamesNode);
		initialDocumentNodes.add(columnOrderNode);
		// do not write the rows node until the end, since all of its elements are not yet included


		if (!schema.isEmpty()) {
			translators = new Translator[schema.size()];
			// build the column names from the schema
			Map<Integer, ConstantNode> columnNameMap = new LinkedHashMap<>();
			List<RGANode> indexArrayNodes = new ArrayList<>();
			LogicalTimestamp previousRgaNodeId = columnOrderNode.getId();
			for (int i = 0; i < schema.size(); i++) {
				ColumnModel cm = schema.get(i);
				// column name
				ConstantNode nameConstNode = new ConstantNode()
						.setId(new LogicalTimestamp().setReplicaId(replicaId).setSequenceNumber(getNextSequenceNumber()))
						.setValue(new ConValue(ConType.STRING, cm.getName()));
				columnNameMap.put(i, nameConstNode);
				initialDocumentNodes.add(nameConstNode);

				// column index
				ConstantNode columnIndexNode = new ConstantNode()
						.setId(new LogicalTimestamp().setReplicaId(replicaId).setSequenceNumber(getNextSequenceNumber()))
						.setValue(new ConValue(ConType.LONG, i));
				RGANode rgaNode = new RGANode()
						.setNodeId(columnOrderNode.getId())
						.setDataId(columnIndexNode.getId())
						.setRefId(previousRgaNodeId)
						.setIsDeleted(false);
				previousRgaNodeId = rgaNode.getDataId();
				indexArrayNodes.add(rgaNode);

				initialDocumentNodes.add(columnIndexNode);

				translators[i] = ColumnTypeToConType.lookUpType(cm.getColumnType()).getTranslator();

			}

			columnNamesNode.setValues(columnNameMap);
			columnOrderNode.setElements(indexArrayNodes).setLength((long) indexArrayNodes.size());
		} else {
			translators = new Translator[0];
		}

		try {
			for (Node n : initialDocumentNodes) {
				encoder.writeNode(n);
			}
		} catch (IOException e) {
			throw new RuntimeException("Failed to write node to snapshot file", e);
		}
	}

	private long getNextSequenceNumber() {
		return nextNodeSequenceNumber++;
	}


	/**
	 * Adds the RowMetadata object to the patch. The row metadata has the following pseudo-schema. Fields that can be
	 * undefined are not guaranteed to be present.
	 * 
	 * ```
	 * obj({
	 *     rowValidation: s.const(json_object) | undefined
	 *     synapseRow: s.const(json_array) | undefined
	 * })
	 * ```
	 * The rowValidation metadata is not included during this boostrap phase.
	 * The synapseRow metadata is a constant with a serialized JSON array that contains 3 values in order:
	 * 
	 * [<rowId>, <versionNumber>, <etag>]
	 *
	 * @param row the table query Row for which metadata should be extracted
	 * @return a reference to the object node containing the row metadata if metadata is present, an empty Optional otherwise.
	 */
	private Optional<ObjectNode> getRowMetadata(Row row, Consumer<Node> nodeConsumer) {
		
		// The synapse row information is the only metadata that might be included during this bootstrap phase.
		// The validation state is computed later on when the patches are applied
		if (row.getRowId() == null && row.getVersionNumber() == null && row.getEtag() == null) {
			return Optional.empty();
		}

		ObjectNode metadataObject = new ObjectNode()
				.setId(new LogicalTimestamp().setReplicaId(rootObjectRef.getReplicaId()).setSequenceNumber(getNextSequenceNumber()));
		nodeConsumer.accept(metadataObject);

		// Create the "synapseRow" JSON_ARRAY constant
		// Note: Use explicit null handling to ensure the JSONArray is properly populated
		JSONArray synapseRowArray = new JSONArray();
		synapseRowArray.put(row.getRowId() != null ? row.getRowId() : JSONObject.NULL);
		synapseRowArray.put(row.getVersionNumber() != null ? row.getVersionNumber() : JSONObject.NULL);
		synapseRowArray.put(row.getEtag() != null ? row.getEtag() : JSONObject.NULL);
		ConstantNode synapseRowMetadata = new ConstantNode()
				.setId(new LogicalTimestamp().setReplicaId(rootObjectRef.getReplicaId()).setSequenceNumber(getNextSequenceNumber()))
				.setValue(new ConValue(ConType.JSON_ARRAY, synapseRowArray));
		nodeConsumer.accept(synapseRowMetadata);

		// Attach the "synapseRow" constant to the row metadata map
		metadataObject.setValue(Collections.singletonMap("synapseRow", synapseRowMetadata.getId()));
		return Optional.of(metadataObject);
	}

	/**
	 * Creates and returns a NewVector containing the values for the row.
	 *
	 * @param row the table query Row for which Synapse Row metadata should be created
	 * @return a reference to the vector node containing the row values.
	 */
	private VectorNode getRowData(Row row, Consumer<Node> nodeConsumer) {
		VectorNode rowVector = new VectorNode()
				.setId(new LogicalTimestamp().setReplicaId(rootObjectRef.getReplicaId()).setSequenceNumber(getNextSequenceNumber()));
		nodeConsumer.accept(rowVector);

		Map<Integer, ConstantNode> cellValues = new LinkedHashMap<>();
		for (int i = 0; i < row.getValues().size(); i++) {
			String cellValue = row.getValues().get(i);
			ConstantNode valueConstantNode = new ConstantNode()
					.setId(new LogicalTimestamp().setReplicaId(rootObjectRef.getReplicaId()).setSequenceNumber(getNextSequenceNumber()))
					.setValue(translators[i].translateNullable(cellValue, requiredColumnIndices.contains(i)));
			nodeConsumer.accept(valueConstantNode);
			cellValues.put(i, valueConstantNode);
		}
		if (!cellValues.isEmpty()) {
			rowVector.setValues(cellValues);
		}
		return rowVector;
	}

	@Override
	public void nextRow(Row row) {
		List<Node> newNodes = new ArrayList<>();
		ObjectNode rowObject = new ObjectNode()
				.setId(new LogicalTimestamp().setReplicaId(rootObjectRef.getReplicaId()).setSequenceNumber(getNextSequenceNumber()));
		newNodes.add(rowObject);


        VectorNode rowDataNode = getRowData(row, newNodes::add);

		Map<String, LogicalTimestamp> rowObjectMap = new LinkedHashMap<>();

		rowObjectMap.put("data", rowDataNode.getId());

		getRowMetadata(row, newNodes::add).ifPresent(rowMetadata -> rowObjectMap.put("metadata", rowMetadata.getId()));

		rowObject.setValue(rowObjectMap);

		// Create a new RGA node for the row object
		RGANode rowRgaNode = new RGANode()
				.setDataId(new LogicalTimestamp().setReplicaId(rootObjectRef.getReplicaId()).setSequenceNumber(getNextSequenceNumber()))
				.setNodeId(rowsArray.getId())
				.setDataId(rowObject.getId())
				.setRefId(lastRowRef)
				.setIsDeleted(false);
		rowRgaNodes.add(rowRgaNode);

		lastRowRef = rowRgaNode.getDataId();

		// flush to the encoder
		try {
			for (Node node : newNodes) {
				encoder.writeNode(node);
			}
		} catch (IOException e) {
			throw new RuntimeException("Failed to write node to snapshot file", e);
		}

	}

	@Override
	public void close() throws IOException {

		rowsArray.setElements(rowRgaNodes).setLength((long) rowRgaNodes.size());
		encoder.writeNode(rowsArray);

		// update the clock table
		encoder.setClockTable(new ClockTable(List.of(new LogicalTimestamp()
				.setReplicaId(rootObjectRef.getReplicaId())
				.setSequenceNumber(nextNodeSequenceNumber))));

		encoder.close();
	}


}
