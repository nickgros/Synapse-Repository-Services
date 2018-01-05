package org.sagebionetworks.search;

import java.io.IOException;
import java.util.List;
import java.util.Set;

import com.amazonaws.services.cloudsearchdomain.model.SearchRequest;
import com.amazonaws.services.cloudsearchdomain.model.SearchResult;
import org.apache.http.client.ClientProtocolException;
import org.sagebionetworks.repo.model.search.Document;
import org.sagebionetworks.repo.model.search.SearchResults;
import org.sagebionetworks.repo.web.ServiceUnavailableException;

/**
 * Abstraction for interacting with the search index.
 * 
 * @author jmhill
 *
 */
public interface SearchDao {

	 /**
	 * Create a new search document.
	 * 
	 * @param toCreate
	 */
	void createOrUpdateSearchDocument(Document toCreate);
	 
	 /**
	 * Create or update a batch of search documents
	 * 
	 * @param batch
	 */
	void createOrUpdateSearchDocument(List<Document> batch);
	 
	 /**
	 * Delete a document using its id.
	 * 
	 * @param docIdToDelete
	 */
	void deleteDocument(String docIdToDelete);
	 
	 /**
	 * Delete all documents with the passed set of document ids.
	 * 
	 * @param docIdsToDelete
	 */
	void deleteDocuments(Set<String> docIdsToDelete);
	 
	 /**
	 * Execute a query.
	 * 
	 * @param search
	 * @return
	 * @throws CloudSearchClientException 
	 */
	SearchResult executeSearch(SearchRequest search) throws CloudSearchClientException;


	/**
	 * Does a document already exist with the given id and etag?
	 * 
	 * @param id
	 * @param etag
	 * @return
	 * @throws CloudSearchClientException 
	 */
	boolean doesDocumentExist(String id, String etag) throws CloudSearchClientException;
	 
	 /**
	 * List all documents in the search index.
	 * 
	 * @param limit
	 * @param offset
	 * @return
	 * @throws CloudSearchClientException 
	 */
	SearchResult listSearchDocuments(long limit, long offset) throws CloudSearchClientException;
	 
	 /**
	 * Clear all data in the search index.
	 *
	 * @throws InterruptedException
	 * @throws CloudSearchClientException
	 */
	void deleteAllDocuments() throws InterruptedException, CloudSearchClientException;

}
