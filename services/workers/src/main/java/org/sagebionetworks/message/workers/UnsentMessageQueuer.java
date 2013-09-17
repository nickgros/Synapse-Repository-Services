package org.sagebionetworks.message.workers;

import java.util.LinkedList;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.sagebionetworks.StackConfiguration;
import org.sagebionetworks.repo.model.dbo.dao.DBOChangeDAO;
import org.sagebionetworks.repo.model.message.UnsentMessageRange;
import org.sagebionetworks.schema.adapter.JSONObjectAdapterException;
import org.sagebionetworks.schema.adapter.org.json.EntityFactory;
import org.springframework.beans.factory.annotation.Autowired;

import com.amazonaws.services.sqs.AmazonSQSClient;
import com.amazonaws.services.sqs.model.CreateQueueRequest;
import com.amazonaws.services.sqs.model.CreateQueueResult;
import com.amazonaws.services.sqs.model.SendMessageBatchRequest;
import com.amazonaws.services.sqs.model.SendMessageBatchRequestEntry;

/**
 * The basic implementation of the RepositoryUnsentMessageQueuer.  
 * This implementation will push a sequential list of ranges to coordinate workers sending unsent messages.  
 */
public class UnsentMessageQueuer implements Runnable {

	static private Log log = LogFactory.getLog(UnsentMessageQueuerTest.class);
	
	@Autowired
	private AmazonSQSClient awsSQSClient;
	
	@Autowired
	private DBOChangeDAO changeDAO;
	
	private Long approxRangeSize;
	private String queueURL;
	
	public void setApproxRangeSize(long approxRangeSize) {
		if (approxRangeSize <= 0) {
			throw new IllegalArgumentException("Range size must be greater than zero");
		}
		this.approxRangeSize = approxRangeSize;
	}
	
	public String getQueueURL() {
		return queueURL;
	}
	
	/**
	 * Called by Spring after bean creation
	 */
	public void initialize() {
		// Make or get the SQS URL
		String queueName = StackConfiguration.getUnsentMessagesQueueName();
		CreateQueueRequest cqRequest = new CreateQueueRequest(queueName);
		CreateQueueResult cqResult = this.awsSQSClient.createQueue(cqRequest);
		queueURL = cqResult.getQueueUrl();
	}
	
	@Override
	public void run() {
		long count = changeDAO.getCount();
		if (count <= 0) {
			return;
		}
		
		long min = changeDAO.getMinimumChangeNumber();
		long max = changeDAO.getCurrentChangeNumber();
		long chunks = 1 + count / approxRangeSize;
		long chunkSize = 1 + (max - min) / chunks;
		List<SendMessageBatchRequestEntry> batch = new LinkedList<SendMessageBatchRequestEntry>();
		
		for (int i = 0; i < chunks; i++) {
			addMessageToBatch(batch, i, min + i * chunkSize, min + (i + 1) * chunkSize);
		}
		addMessageToBatch(batch, chunks, min + chunks * chunkSize, max);
		
		awsSQSClient.sendMessageBatch(new SendMessageBatchRequest(queueURL, batch));
	}
	
	private void addMessageToBatch(List<SendMessageBatchRequestEntry> batch, long index, long lower, long upper) {
		if (lower > upper) {
			throw new IllegalArgumentException("Upper and lower bounds must have the correct numeric relationship (upper >= lower)");
		}
		UnsentMessageRange range = new UnsentMessageRange();
		range.setLowerBound(lower);
		range.setUpperBound(upper);
		SendMessageBatchRequestEntry entry= createEntry(index, range);
		if (entry != null) {
			batch.add(entry);
		}
	}
	
	private SendMessageBatchRequestEntry createEntry(long index, UnsentMessageRange range) {
		try {
			String messageBody = EntityFactory.createJSONStringForEntity(range);
			return new SendMessageBatchRequestEntry(""+index, messageBody);
		} catch (JSONObjectAdapterException e) {
			log.error("Failed to marshal message", e);
			return null;
		}
	}
}
