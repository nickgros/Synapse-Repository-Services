package org.sagebionetworks.repo.manager;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.sagebionetworks.repo.manager.file.FileHandleManager;
import org.sagebionetworks.repo.model.Annotations;
import org.sagebionetworks.repo.model.AuthorizationConstants.BOOTSTRAP_PRINCIPAL;
import org.sagebionetworks.repo.model.Data;
import org.sagebionetworks.repo.model.Entity;
import org.sagebionetworks.repo.model.EntityType;
import org.sagebionetworks.repo.model.FileEntity;
import org.sagebionetworks.repo.model.Folder;
import org.sagebionetworks.repo.model.LocationData;
import org.sagebionetworks.repo.model.LocationTypeNames;
import org.sagebionetworks.repo.model.Project;
import org.sagebionetworks.repo.model.S3Token;
import org.sagebionetworks.repo.model.Study;
import org.sagebionetworks.repo.model.UserInfo;
import org.sagebionetworks.repo.model.auth.NewUser;
import org.sagebionetworks.repo.model.file.ExternalFileHandle;
import org.sagebionetworks.repo.model.file.FileHandle;
import org.sagebionetworks.repo.model.file.S3FileHandle;
import org.sagebionetworks.repo.web.NotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = { "classpath:test-context.xml" })
public class EntityTypeConverterImplAutowireTest {

	@Autowired
	private S3TokenManager s3TokenManager;
	@Autowired
	private EntityManager entityManager;
	@Autowired
	private UserManager userManager;
	@Autowired
	private FileHandleManager fileHandleManager;
	@Autowired
	private EntityTypeConverter entityTypeConverter;
	
	private List<String> toDelete;
	private List<String> fileHandlesToDelete;
	
	private UserInfo adminUserInfo;
	private UserInfo userInfo;
	private Long userId;
	String sampleMD5;
	String externalPath;
	private Project project;
	
	@Before
	public void before() throws Exception{
		adminUserInfo = userManager.getUserInfo(BOOTSTRAP_PRINCIPAL.THE_ADMIN_USER.getPrincipalId());
		NewUser nu = new NewUser();
		nu.setUserName("test");
		nu.setEmail("just.a.test@sagebase.org");
		userId = userManager.createUser(nu);
		userInfo = userManager.getUserInfo(userId);
		userInfo.getGroups().add(BOOTSTRAP_PRINCIPAL.CERTIFIED_USERS.getPrincipalId());
		toDelete = new ArrayList<String>();
		fileHandlesToDelete = new ArrayList<String>();
		
		project = new Project();
		project.setName(UUID.randomUUID().toString());
		String projectId = entityManager.createEntity(userInfo, project, null);
		toDelete.add(projectId);
		project = entityManager.getEntity(userInfo, projectId, Project.class);
		
		sampleMD5 = "8743b52063cd84097a65d1633f5c74f5";
		externalPath = "http://www.google.com";
	}
	
	@After
	public void after() throws Exception {
		if(entityManager != null && toDelete != null){
			for(String id: toDelete){
				try{
					entityManager.deleteEntity(adminUserInfo, id);
				}catch(Exception e){}
			}
		}
		if(fileHandleManager != null && fileHandlesToDelete != null){
			for(String id: fileHandlesToDelete){
				try{
					fileHandleManager.deleteFileHandle(adminUserInfo, id);
				}catch(Exception e){}
			}
		}
		if (userId!=null) {
			userManager.deletePrincipal(adminUserInfo, userId);
		}
	}
	
	@Test
	public void testCreateFileHandleForForEachVersion() throws Exception {
		Data data = createDataWithMultipleVersions();
		// Create the files handles for all versions of this study
		List<VersionData> pairs = entityTypeConverter.createFileHandleForForEachVersion(userInfo, data);
		assertNotNull(pairs);
		assertEquals(2, pairs.size());
		// First version is external
		VersionData v1 = pairs.get(1);
		assertEquals(new Long(1), v1.getVersionNumber());
		assertNotNull(v1.getFileHandle());
		assertTrue(v1.getFileHandle() instanceof ExternalFileHandle);
		ExternalFileHandle v1FileHandle = (ExternalFileHandle) v1.getFileHandle();
		fileHandlesToDelete.add(v1FileHandle.getId());
		assertEquals("CreatedBy should match the modifiedBy of the original version, not the caller.",data.getModifiedBy(), v1FileHandle.getCreatedBy());
		assertEquals(externalPath, v1FileHandle.getExternalURL());
		assertNotNull(v1FileHandle.getId());
		// Second version is s3
		VersionData v2 = pairs.get(0);
		assertEquals(new Long(2), v2.getVersionNumber());
		assertNotNull(v2.getFileHandle());
		assertTrue(v2.getFileHandle() instanceof S3FileHandle);
		S3FileHandle v2FileHandle = (S3FileHandle) v2.getFileHandle();
		fileHandlesToDelete.add(v2FileHandle.getId());
		assertEquals("CreatedBy should match the modifiedBy of the original version, not the caller.",data.getModifiedBy(), v2FileHandle.getCreatedBy());
		assertEquals(data.getMd5(), v2FileHandle.getContentMd5());
		assertEquals(data.getContentType(), v2FileHandle.getContentType());
		assertNotNull(v2FileHandle.getId());
	}
	
	@Test
	public void testConvertDataToFile() throws Exception{
		Data data = createDataWithMultipleVersions();
		Entity changed = entityTypeConverter.convertOldTypeToNew(userInfo, data);
		assertTrue(changed instanceof FileEntity);
		FileEntity file = (FileEntity) changed;
		assertEquals(data.getId(), file.getId());
		assertFalse("The etag should have changed",data.getEtag().equals(file.getEtag()));
		assertEquals(data.getCreatedBy(), file.getCreatedBy());
		assertEquals(data.getCreatedOn().getTime(), file.getCreatedOn().getTime());
		assertEquals(data.getModifiedBy(), file.getModifiedBy());
		assertEquals(data.getModifiedOn().getTime(), file.getModifiedOn().getTime());
		assertNotNull(file.getDataFileHandleId());
		// All of the old fields should now be in annotations
		Annotations annos = entityManager.getAnnotations(userInfo, file.getId());
		assertEquals(data.getDisease(), annos.getSingleValue("disease"));
		assertEquals(data.getNumSamples(), annos.getSingleValue("numSamples"));
		assertEquals(data.getTissueType(), annos.getSingleValue("tissueType"));
		assertEquals(data.getPlatform(), annos.getSingleValue("platform"));
		assertEquals(data.getSpecies(), annos.getSingleValue("species"));
		// Plus the annotations
		assertEquals("stringValue", annos.getSingleValue("stringKey"));
		assertEquals(new Long(555), annos.getSingleValue("longKey"));
		
		// get the file handle
		FileHandle handle = fileHandleManager.getRawFileHandle(adminUserInfo, file.getDataFileHandleId());
		assertNotNull(handle);
		assertTrue("The current version should be an S3 file handle",handle instanceof S3FileHandle);
		fileHandlesToDelete.add(handle.getId());
		
		// Check the previous version
		FileEntity fileV1 = entityManager.getEntityForVersion(userInfo, file.getId(), 1L, FileEntity.class);
		assertNotNull(fileV1);
		assertEquals(data.getId(), fileV1.getId());
		handle = fileHandleManager.getRawFileHandle(adminUserInfo, fileV1.getDataFileHandleId());
		assertNotNull(handle);
		assertTrue("The second version should be an external file handle",handle instanceof ExternalFileHandle);
		fileHandlesToDelete.add(handle.getId());
		
		// get the annotations for the second version
		annos = entityManager.getAnnotationsForVersion(adminUserInfo, data.getId(), 1L);
		assertEquals(data.getDisease(), annos.getSingleValue("disease"));
		assertEquals(data.getNumSamples(), annos.getSingleValue("numSamples"));
		assertEquals(data.getTissueType(), annos.getSingleValue("tissueType"));
		assertEquals(data.getPlatform(), annos.getSingleValue("platform"));
		assertEquals(data.getSpecies(), annos.getSingleValue("species"));
		// Plus the annotations
		assertEquals("stringValue", annos.getSingleValue("stringKey"));
		assertEquals(new Long(555), annos.getSingleValue("longKey"));
	}
	
	@Test
	public void testConvertStudyToFolder() throws Exception{
		Study study = createStudyWithMultipleVersions();
		Entity changed = entityTypeConverter.convertOldTypeToNew(userInfo, study);
		assertTrue(changed instanceof Folder);
		Folder folder = (Folder) changed;
		assertEquals(study.getId(), folder.getId());
		assertFalse("The etag should have changed",study.getEtag().equals(folder.getEtag()));
		assertEquals(study.getCreatedBy(), folder.getCreatedBy());
		assertEquals(study.getCreatedOn().getTime(), folder.getCreatedOn().getTime());
		assertEquals(study.getModifiedBy(), folder.getModifiedBy());
		assertEquals(study.getModifiedOn().getTime(), folder.getModifiedOn().getTime());
		
		// All of the old fields should now be in annotations
		Annotations annos = entityManager.getAnnotations(userInfo, folder.getId());
		assertEquals(study.getDisease(), annos.getSingleValue("disease"));
		assertEquals(study.getNumSamples(), annos.getSingleValue("numSamples"));
		assertEquals(study.getTissueType(), annos.getSingleValue("tissueType"));
		assertEquals(study.getPlatform(), annos.getSingleValue("platform"));
		assertEquals(study.getSpecies(), annos.getSingleValue("species"));
		// Plus the annotations
		assertEquals("stringValue", annos.getSingleValue("stringKey"));
		assertEquals(new Long(555), annos.getSingleValue("longKey"));
				
		// get the annotations for the second version
		annos = entityManager.getAnnotationsForVersion(adminUserInfo, study.getId(), 1L);
		assertEquals(study.getDisease(), annos.getSingleValue("disease"));
		assertEquals(study.getNumSamples(), annos.getSingleValue("numSamples"));
		assertEquals(study.getTissueType(), annos.getSingleValue("tissueType"));
		assertEquals(study.getPlatform(), annos.getSingleValue("platform"));
		assertEquals(study.getSpecies(), annos.getSingleValue("species"));
		// Plus the annotations
		assertEquals("stringValue", annos.getSingleValue("stringKey"));
		assertEquals(new Long(555), annos.getSingleValue("longKey"));
		
		// A file entity should have been created with all of the original location data
		List<FileEntity> children = entityManager.getEntityChildren(adminUserInfo, folder.getId(), FileEntity.class);
		assertNotNull(children);
		assertEquals(1, children.size());
		FileEntity file = children.get(0);
		
		FileHandle handle = fileHandleManager.getRawFileHandle(adminUserInfo, file.getDataFileHandleId());
		assertNotNull(handle);
		assertTrue("The current version should be an S3 file handle",handle instanceof S3FileHandle);
		fileHandlesToDelete.add(handle.getId());
//		assertEquals(study.getVersionComment(), file.getVersionComment());
		assertEquals(study.getVersionLabel(), file.getVersionLabel());
		assertEquals(study.getModifiedBy(), file.getModifiedBy());
		
		// Check the previous version
		FileEntity fileV1 = entityManager.getEntityForVersion(userInfo, file.getId(), 1L, FileEntity.class);
		assertNotNull(fileV1);
		assertFalse(study.getId().equals(fileV1.getId()));
		handle = fileHandleManager.getRawFileHandle(adminUserInfo, fileV1.getDataFileHandleId());
		assertNotNull(handle);
		assertTrue("The second version should be an external file handle",handle instanceof ExternalFileHandle);
		fileHandlesToDelete.add(handle.getId());
	}

	/**
	 * Helper to create a data object with multiple versions.
	 * @return
	 * @throws NotFoundException
	 */
	public Data createDataWithMultipleVersions() throws NotFoundException {
		Data data = new Data();
		data.setName("DataFile");
		data.setParentId(project.getId());
		data.setDisease("cancer");
		data.setNumSamples(99L);
		data.setTissueType("skin");
		data.setPlatform("xbox");
		data.setSpecies("people");
		LocationData ld = new LocationData();

		ld.setPath(externalPath);
		ld.setType(LocationTypeNames.external);
		data.setContentType("text/plain");
		data.setMd5(sampleMD5);
		data.setLocations(Arrays.asList(ld));
		String id = entityManager.createEntity(adminUserInfo, data, null);
		data = entityManager.getEntity(adminUserInfo, id, Data.class);
		
		// Add some annotations
		Annotations annos = entityManager.getAnnotations(adminUserInfo, id);
		annos.addAnnotation("stringKey", "stringValue");
		annos.addAnnotation("longKey", 555L);
		entityManager.updateAnnotations(adminUserInfo, id, annos);
		data = entityManager.getEntity(adminUserInfo, id, Data.class);
		
		// now create an S3 location
		S3Token token = new S3Token();
		token.setPath("foo.txt");
		token.setMd5(sampleMD5);
		token = s3TokenManager.createS3Token(adminUserInfo.getId(), data.getId(), token, EntityType.layer);
		
		LocationData s3data = new LocationData();
		s3data.setPath(token.getPath());
		s3data.setType(LocationTypeNames.awss3);
		data.setMd5(token.getMd5());
		data.setLocations(Arrays.asList(s3data));
		
		// add a new version
		data.setVersionLabel("v2");
		entityManager.updateEntity(adminUserInfo, data, true, null);
		return entityManager.getEntity(adminUserInfo, id, Data.class);
	}
	
	/**
	 * Helper to create a study object with multiple versions.
	 * @return
	 * @throws NotFoundException
	 */
	public Study createStudyWithMultipleVersions() throws NotFoundException {
		Study data = new Study();
		data.setName("DataFile");
		data.setParentId(project.getId());
		data.setDisease("cancer");
		data.setNumSamples(99L);
		data.setTissueType("skin");
		data.setPlatform("xbox");
		data.setSpecies("people");
		data.setVersionComment("v1 comments");
		data.setVersionLabel("v1");
		LocationData ld = new LocationData();

		ld.setPath(externalPath);
		ld.setType(LocationTypeNames.external);
		data.setContentType("text/plain");
		data.setMd5(sampleMD5);
		data.setLocations(Arrays.asList(ld));
		String id = entityManager.createEntity(adminUserInfo, data, null);
		data = entityManager.getEntity(adminUserInfo, id, Study.class);
		
		// Add some annotations
		Annotations annos = entityManager.getAnnotations(adminUserInfo, id);
		annos.addAnnotation("stringKey", "stringValue");
		annos.addAnnotation("longKey", 555L);
		entityManager.updateAnnotations(adminUserInfo, id, annos);
		data = entityManager.getEntity(adminUserInfo, id, Study.class);
		
		// now create an S3 location
		S3Token token = new S3Token();
		token.setPath("foo.txt");
		token.setMd5(sampleMD5);
		token = s3TokenManager.createS3Token(adminUserInfo.getId(), data.getId(), token, EntityType.dataset);
		
		LocationData s3data = new LocationData();
		s3data.setPath(token.getPath());
		s3data.setType(LocationTypeNames.awss3);
		data.setMd5(token.getMd5());
		data.setLocations(Arrays.asList(s3data));
		
		// add a new version
		data.setVersionLabel("v2");
		data.setVersionComment("v2 Comments");
		entityManager.updateEntity(adminUserInfo, data, true, null);
		return entityManager.getEntity(adminUserInfo, id, Study.class);
	}
}