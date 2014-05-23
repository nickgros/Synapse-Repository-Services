package org.sagebionetworks.change.workers;

import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.sagebionetworks.repo.manager.message.RepositoryMessagePublisher;
import org.sagebionetworks.repo.model.dbo.dao.DBOChangeDAO;
import org.sagebionetworks.repo.model.message.ChangeMessage;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * This worker will synchronize the changes table with the sent message table.
 * 
 * It will find all messages that have not been sent and send them. This worker
 * will persist a high-water mark to avoid re-synchronizing change numbers that
 * have already been synchronized.
 * 
 * If the last synched change number (high-water marke) no longer exists in the
 * sent table then one of two events could have occurred:
 * <ol>
 * <li>Migration deleted changes for events that occurred on staging that did
 * not occur in production.</li>
 * <li>The object associated with a change message changed again and was issued
 * a new, larger, change number.</li>
 * </ol>
 * The second case would not cause this process to miss a messages that still
 * needs to be sent, but the first could. Therefore, whenever the last synched
 * message no longer exists we assume the worst case scenario and use the maximum
 * change number that still exists and is less than or equal to the
 * lastSynchedChangeNumber as the starting point for the synchronization
 * process.
 * 
 * @author jmhill
 * 
 */
public class ChangeSentMessageSynchWorker implements Runnable {
	
	static private Logger log = LogManager.getLogger(ChangeSentMessageSynchWorker.class);

	@Autowired
	DBOChangeDAO changeDao;
	@Autowired
	RepositoryMessagePublisher repositoryMessagePublisher;
	// Start with a default batch size of 10K
	private int batchSize = 10 * 1000;

	private Object monitor = new Object();
	
	private int runCount = 0;
	/**
	 * Override the batch size.
	 * 
	 * @param batchSize
	 */
	public void setBatchSize(int batchSize) {
		this.batchSize = batchSize;
	}

	@Override
	public void run() {
		long maxChangeNumber = changeDao.getCurrentChangeNumber();
		log.info("Max change number: " + maxChangeNumber);
		long lastSychedChangeNumberStart = changeDao
				.getLastSynchedChangeNumber();
		log.info("Max lastSychedChangeNumberStart: "
				+ lastSychedChangeNumberStart);
		// Use the max sent change number that still exits that is less than or
		// equal to the last sent change message as the starting point for this
		// synchronization run. This ensures we never miss a message that still
		// needs to be sent.
		long synchStart = changeDao
				.getMaxSentChangeNumber(lastSychedChangeNumberStart);
		log.info("synchStart: " + synchStart);
		// List any unsent messages from this start
		long upperBounds = Math.min(synchStart + batchSize, maxChangeNumber);
		log.info("upperBounds: " + upperBounds);
		List<ChangeMessage> toBeSent = changeDao.listUnsentMessages(synchStart,
				upperBounds);
		log.info("toBeSent size: " + toBeSent.size());
		// Send
		for (ChangeMessage toSend : toBeSent) {
			repositoryMessagePublisher.publishToTopic(toSend);
			log.info("message sent: " + toSend);
		}
		// the new Last synched change number is the max sent change number that
		// exists
		// that is less than or equal to the upper bounds for this round.
		long newLastSynchedChangeNumber = changeDao
				.getMaxSentChangeNumber(upperBounds);
		log.info("newLastSynchedChangeNumber: " + newLastSynchedChangeNumber);
		// Set the new high-water-mark if it has changed
		if (newLastSynchedChangeNumber > lastSychedChangeNumberStart) {
			boolean changed = changeDao.setLastSynchedChangeNunber(
					lastSychedChangeNumberStart, newLastSynchedChangeNumber);
			if (changed) {
				log.info("setLastSynchedChangeNunber: "
						+ newLastSynchedChangeNumber);
			} else {
				log.info("did not set setLastSynchedChangeNunber: "
						+ newLastSynchedChangeNumber);
			}
		}
		log.info("Run count: " + runCount++);
		synchronized(monitor){
			monitor.notifyAll();
		}
	}
	
	/**
	 * Caller can use use the monitor to wait for the worker to run at least once.
	 * @return
	 */
	public Object getMonitor(){
		return this.monitor;
	}
}
