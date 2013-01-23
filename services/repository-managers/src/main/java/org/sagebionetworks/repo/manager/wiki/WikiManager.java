package org.sagebionetworks.repo.manager.wiki;

import org.sagebionetworks.repo.model.DatastoreException;
import org.sagebionetworks.repo.model.UnauthorizedException;
import org.sagebionetworks.repo.model.UserInfo;
import org.sagebionetworks.repo.model.dao.WikiPageKey;
import org.sagebionetworks.repo.model.message.ObjectType;
import org.sagebionetworks.repo.model.wiki.WikiPage;
import org.sagebionetworks.repo.web.NotFoundException;

/**
 * Abstraction for the Wiki manager.
 * @author John
 *
 */
public interface WikiManager {

	/**
	 * Create a Wiki page for a given object.
	 * @param user
	 * @param objectId
	 * @param objectType
	 * @param toCreate
	 * @return
	 * @throws NotFoundException 
	 */
	WikiPage createWikiPage(UserInfo user, String objectId,	ObjectType objectType, WikiPage toCreate) throws NotFoundException, UnauthorizedException;

	/**
	 * Get a wiki page for a given object.
	 * @param user
	 * @param objectId
	 * @param objectType
	 * @param wikiId
	 * @return
	 * @throws UnauthorizedException 
	 * @throws NotFoundException 
	 */
	WikiPage getWikiPage(UserInfo user, WikiPageKey key) throws NotFoundException, UnauthorizedException;

	/**
	 * Delete a wiki page.
	 * @param user
	 * @param wikiPageKey
	 * @throws NotFoundException 
	 * @throws DatastoreException 
	 */
	void deleteWiki(UserInfo user, WikiPageKey wikiPageKey) throws UnauthorizedException, DatastoreException, NotFoundException;

	/**
	 * Update a wiki page if allowed.
	 * @param user
	 * @param objectId
	 * @param objectType
	 * @param toUpdate
	 * @return
	 * @throws UnauthorizedException 
	 * @throws NotFoundException 
	 */
	WikiPage updateWikiPage(UserInfo user, String objectId,	ObjectType objectType, WikiPage toUpdate) throws NotFoundException, UnauthorizedException;

}
