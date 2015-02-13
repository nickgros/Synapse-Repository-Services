package org.sagebionetworks.evaluation.dao;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;
import static org.sagebionetworks.evaluation.model.SubmissionStatusEnum.REJECTED;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.sagebionetworks.evaluation.dbo.SubmissionDBO;
import org.sagebionetworks.evaluation.model.Evaluation;
import org.sagebionetworks.evaluation.model.EvaluationStatus;
import org.sagebionetworks.evaluation.model.Submission;
import org.sagebionetworks.evaluation.model.SubmissionContributor;
import org.sagebionetworks.evaluation.model.SubmissionStatus;
import org.sagebionetworks.evaluation.model.SubmissionStatusEnum;
import org.sagebionetworks.repo.model.AuthorizationConstants.BOOTSTRAP_PRINCIPAL;
import org.sagebionetworks.repo.model.DatastoreException;
import org.sagebionetworks.repo.model.GroupMembersDAO;
import org.sagebionetworks.repo.model.InvalidModelException;
import org.sagebionetworks.repo.model.Node;
import org.sagebionetworks.repo.model.NodeDAO;
import org.sagebionetworks.repo.model.Team;
import org.sagebionetworks.repo.model.TeamDAO;
import org.sagebionetworks.repo.model.UserGroup;
import org.sagebionetworks.repo.model.UserGroupDAO;
import org.sagebionetworks.repo.model.dao.FileHandleDao;
import org.sagebionetworks.repo.model.evaluation.EvaluationDAO;
import org.sagebionetworks.repo.model.evaluation.SubmissionDAO;
import org.sagebionetworks.repo.model.evaluation.SubmissionStatusDAO;
import org.sagebionetworks.repo.model.file.PreviewFileHandle;
import org.sagebionetworks.repo.model.jdo.NodeTestUtils;
import org.sagebionetworks.repo.web.NotFoundException;
import org.sagebionetworks.schema.adapter.JSONObjectAdapterException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = { "classpath:jdomodels-test-context.xml" })
public class SubmissionDAOImplTest {
 
    @Autowired
    private SubmissionDAO submissionDAO;
    
    @Autowired
    private SubmissionStatusDAO submissionStatusDAO;
    
    @Autowired
    private EvaluationDAO evaluationDAO;
	
    @Autowired
	private NodeDAO nodeDAO;
	
    @Autowired
	private FileHandleDao fileHandleDAO;
 
	@Autowired
	UserGroupDAO userGroupDAO;
	
	@Autowired
	GroupMembersDAO groupMembersDAO;
	
	@Autowired
	TeamDAO teamDAO;
	
    private static final String SUBMISSION_ID = "206";
    private static final String SUBMISSION_2_ID = "307";
    private static final String SUBMISSION_3_ID = "408";
    private static final String USERID_DOES_NOT_EXIST = "2";
    private static final String EVALID_DOES_NOT_EXIST = "456";
    private static final String SUBMISSION_NAME = "test submission";
    private static final Long VERSION_NUMBER = 1L;
    private static final long CREATION_TIME_STAMP = System.currentTimeMillis();

    private String nodeId;
	private String userId;
	private String userId2;
	
    private String evalId;
    private String fileHandleId;
    private Submission submission;
    private Submission submission2;
    private Submission submission3;
    private Team submissionTeam;
    
    // create a team and add the given ID as a member
	private Team createTeam(String ownerId) throws NotFoundException {
		Team team = new Team();
		UserGroup ug = new UserGroup();
		ug.setIsIndividual(false);
		Long id = userGroupDAO.create(ug);
		
		team.setId(id.toString());
		team.setCreatedOn(new Date());
		team.setCreatedBy(ownerId);
		team.setModifiedOn(new Date());
		team.setModifiedBy(ownerId);
		Team created = teamDAO.create(team);
		try {
			groupMembersDAO.addMembers(id.toString(), Arrays.asList(new String[]{ownerId}));
		} catch (NotFoundException e) {
			throw new RuntimeException(e);
		}
			
		return created;
	}
	
	private void deleteTeam(Team team) throws NotFoundException {
		teamDAO.delete(team.getId());
		userGroupDAO.delete(team.getId());
	}
	
	private static Random random = new Random();
	
	private Submission newSubmission(String submissionId, String userId, Date createdDate) {
		Submission submission = new Submission();
        submission.setCreatedOn(createdDate);
        submission.setId(submissionId);
        submission.setName(SUBMISSION_NAME+"_"+random.nextInt());
        submission.setEvaluationId(evalId);
        submission.setEntityId(nodeId);
        submission.setVersionNumber(VERSION_NUMBER);
        submission.setUserId(userId);
        submission.setSubmitterAlias("Team Awesome_"+random.nextInt());
        submission.setEntityBundleJSON("some bundle"+random.nextInt());
        return submission;
	}

    @Before
    public void setUp() throws DatastoreException, InvalidModelException, NotFoundException {
    	userId = BOOTSTRAP_PRINCIPAL.THE_ADMIN_USER.getPrincipalId().toString();
    	userId2 = BOOTSTRAP_PRINCIPAL.ANONYMOUS_USER.getPrincipalId().toString();
    	
    	// create a file handle
		PreviewFileHandle meta = new PreviewFileHandle();
		meta.setBucketName("bucketName");
		meta.setKey("key");
		meta.setContentType("content type");
		meta.setContentSize(123l);
		meta.setContentMd5("md5");
		meta.setCreatedBy("" + userId);
		meta.setFileName("preview.jpg");
		fileHandleId = fileHandleDAO.createFile(meta).getId();
		
    	// create a node
  		Node toCreate = NodeTestUtils.createNew(SUBMISSION_NAME, Long.parseLong(userId));
    	toCreate.setVersionComment("This is the first version of the first node ever!");
    	toCreate.setVersionLabel("0.0.1");
    	toCreate.setFileHandleId(fileHandleId);
    	nodeId = nodeDAO.createNew(toCreate);
    	
    	// create an Evaluation
        Evaluation evaluation = new Evaluation();
        evaluation.setId("1234");
        evaluation.setEtag("etag");
        evaluation.setName("name");
        evaluation.setOwnerId(userId);
        evaluation.setCreatedOn(new Date());
        evaluation.setContentSource(nodeId);
        evaluation.setStatus(EvaluationStatus.PLANNED);
        evalId = evaluationDAO.create(evaluation, Long.parseLong(userId));
        
        // Initialize Submissions
        // submission has no team and no contributors
        submission = newSubmission(SUBMISSION_ID, userId, new Date(CREATION_TIME_STAMP));
        
        // submission2 is a Team submission with two contributors, userId and userId2
        submission2 = newSubmission(SUBMISSION_2_ID, userId2, new Date(CREATION_TIME_STAMP));
        submissionTeam = createTeam(userId2);
        submission2.setTeamId(submissionTeam.getId());
        SubmissionContributor submissionContributor = new SubmissionContributor();
        submissionContributor.setPrincipalId(userId2);
        Set<SubmissionContributor> scs = new HashSet<SubmissionContributor>();
        submission2.setContributors(scs);
        scs.add(submissionContributor);
		groupMembersDAO.addMembers(submissionTeam.getId(), Arrays.asList(new String[]{userId}));
		submissionContributor = new SubmissionContributor();
        submissionContributor.setPrincipalId(userId);
        scs.add(submissionContributor);
        
        // submission3 is a Team submission with one contributor, userId2
        submission3 = newSubmission(SUBMISSION_3_ID, userId2, new Date(CREATION_TIME_STAMP));
        submission3.setTeamId(submissionTeam.getId());
        submissionContributor = new SubmissionContributor();
        submissionContributor.setPrincipalId(userId2);
        scs = new HashSet<SubmissionContributor>();
        submission3.setContributors(scs);
        scs.add(submissionContributor);
        
        
        
    }
    
    @After
    public void tearDown() throws DatastoreException, NotFoundException  {
    	for (String id : new String[]{SUBMISSION_ID, SUBMISSION_2_ID, SUBMISSION_3_ID}) {
    		try {
    			submissionDAO.delete(id);
    		} catch (NotFoundException e)  {};
    	}
			
		deleteTeam(submissionTeam);
		submissionTeam=null;
		
		try {
			evaluationDAO.delete(evalId);
		} catch (NotFoundException e) {};
    	try {
    		nodeDAO.delete(nodeId);
    	} catch (NotFoundException e) {};
		fileHandleDAO.delete(fileHandleId);
    }
    
    @Test
    public void testCRD() throws Exception{
        long initialCount = submissionDAO.getCount();
 
        // create Submission
        String returnedId = submissionDAO.create(submission);
        assertEquals(SUBMISSION_ID, returnedId); 
        
        // we create a second submission with contributors to test
        // that retrieval of the first submission doesn't accidentally
        // pick up the contributors to some other submission
        String returnedId2 = submissionDAO.create(submission2);
        assertEquals(SUBMISSION_2_ID, returnedId2);   
        
        // fetch it
        Submission clone = submissionDAO.get(SUBMISSION_ID);
        assertNotNull(clone);
        assertEquals(initialCount + 2, submissionDAO.getCount());
        assertEquals(submission, clone);
        
        // delete it
        submissionDAO.delete(SUBMISSION_ID);
        try {
        	clone = submissionDAO.get(SUBMISSION_ID);
            fail("Failed to delete Submission");
       } catch (NotFoundException e) {
        	// expected
        }
    	assertEquals(initialCount+1, submissionDAO.getCount());
    }
    
    @Test
    public void testCRDWithContributors() throws Exception{
        long initialCount = submissionDAO.getCount();
 
        // create Submission
        String returnedId = submissionDAO.create(submission2);
        assertEquals(SUBMISSION_2_ID, returnedId);   
        
        // fetch it
        Submission clone = submissionDAO.get(SUBMISSION_2_ID);
        assertNotNull(clone);
        // need to nullify the contributor createdOn dates to be able to compare to 'submission2'
        Set<SubmissionContributor> nullifiedDates = new HashSet<SubmissionContributor>();
        for (SubmissionContributor sc : clone.getContributors()) {
        	assertNotNull(sc.getCreatedOn());
        	sc.setCreatedOn(null);
        	nullifiedDates.add(sc);
        }
        clone.setContributors(nullifiedDates);
        assertEquals(initialCount + 1, submissionDAO.getCount());
        assertEquals(submission2, clone);
        
        // delete it
        submissionDAO.delete(SUBMISSION_2_ID);
        try {
        	clone = submissionDAO.get(SUBMISSION_2_ID);
            fail("Failed to delete Submission");
       } catch (NotFoundException e) {
        	// expected
        }
    	assertEquals(initialCount, submissionDAO.getCount());
    }
    
    @Test
    public void testAddContributor() throws Exception{
        // create Submission
        String returnedId = submissionDAO.create(submission);
        assertEquals(SUBMISSION_ID, returnedId); 
        
        SubmissionContributor sc = new SubmissionContributor();
        sc.setPrincipalId(userId);
        sc.setCreatedOn(new Date());
        submissionDAO.addSubmissionContributor(SUBMISSION_ID, sc);
                
        // fetch it
        Submission clone = submissionDAO.get(SUBMISSION_ID);
        assertNotNull(clone);
        assertEquals(1, clone.getContributors().size());
        assertEquals(sc, clone.getContributors().iterator().next());
    }
    
    @Test
    public void testGetAllByUser() throws DatastoreException, NotFoundException, JSONObjectAdapterException {
    	submissionDAO.create(submission);
    	
    	// userId should have submissions
    	List<Submission> subs = submissionDAO.getAllByUser(userId, 10, 0);
    	assertEquals(1, subs.size());
    	assertEquals(subs.size(), submissionDAO.getCountByUser(userId));
       	Submission retrieved = subs.get(0);
       	submission.setCreatedOn(retrieved.getCreatedOn());
    	assertEquals(retrieved, submission);
    	
    	// userId_does_not_exist should not have any submissions
    	subs = submissionDAO.getAllByUser(USERID_DOES_NOT_EXIST, 10, 0);
    	assertEquals(0, subs.size());
    }
    
    @Test
    public void testGetAllByUserWithContributors() throws DatastoreException, NotFoundException, JSONObjectAdapterException {
    	submissionDAO.create(submission2);
    	
    	// userId should have submissions
    	List<Submission> subs = submissionDAO.getAllByUser(userId2, 10, 0);
    	assertEquals(1, subs.size());
    	Submission retrieved = subs.get(0);
    	submission2.setCreatedOn(retrieved.getCreatedOn());
        // need to nullify the contributor createdOn dates to be able to compare to 'submission2'
    	nullifyContributorCreatedOn(retrieved);
    	assertEquals(retrieved, submission2);
    }
    
    private void nullifyContributorCreatedOn(Submission retrieved) {
        Set<SubmissionContributor> nullifiedDates = new HashSet<SubmissionContributor>();
        for (SubmissionContributor sc : retrieved.getContributors()) {
        	assertNotNull(sc.getCreatedOn());
        	sc.setCreatedOn(null);
        	nullifiedDates.add(sc);
        }
        // the hash set returned by .getContributors() is invalid 
        // since we've messed with the set's contents.  So we use
        // the new one instead
        retrieved.setContributors(nullifiedDates);
    }
    
    @Test
    public void testGetAllByEvaluation() throws DatastoreException, NotFoundException, JSONObjectAdapterException {
    	submissionDAO.create(submission);
    	
    	// evalId should have submissions
    	List<Submission> subs = submissionDAO.getAllByEvaluation(evalId, 10, 0);
    	assertEquals(1, subs.size());
    	assertEquals(subs.size(), submissionDAO.getCountByEvaluation(evalId));
    	Submission retrieved = subs.get(0);
    	submission.setCreatedOn(retrieved.getCreatedOn());
    	assertEquals(retrieved, submission);
    	
    	// evalId_does_not_exist should not have any submissions
    	subs = submissionDAO.getAllByEvaluation(EVALID_DOES_NOT_EXIST, 10, 0);
    	assertEquals(0, subs.size());
    	assertEquals(0, submissionDAO.getCountByEvaluation(EVALID_DOES_NOT_EXIST));
    }
    
    @Test
    public void testGetAllByEvaluationWithContributors() throws DatastoreException, NotFoundException, JSONObjectAdapterException {
    	submissionDAO.create(submission2);
    	
    	// evalId should have submissions
    	List<Submission> subs = submissionDAO.getAllByEvaluation(evalId, 10, 0);
    	assertEquals(1, subs.size());
    	assertEquals(subs.size(), submissionDAO.getCountByEvaluation(evalId));
    	Submission retrieved = subs.get(0);
    	submission2.setCreatedOn(retrieved.getCreatedOn());
        // need to nullify the contributor createdOn dates to be able to compare to 'submission2'
     	nullifyContributorCreatedOn(retrieved);
    	assertEquals(retrieved, submission2);
    }
    
    private void createSubmissionStatus(String id, SubmissionStatusEnum status) {
    	// create a SubmissionStatus object
    	SubmissionStatus subStatus = new SubmissionStatus();
    	subStatus.setId(id);
    	subStatus.setStatus(status);
    	subStatus.setModifiedOn(new Date());
    	submissionStatusDAO.create(subStatus);
    }
    
   @Test
    public void testGetAllByEvaluationAndStatus() throws DatastoreException, NotFoundException, JSONObjectAdapterException {
    	submissionDAO.create(submission);
    	createSubmissionStatus(SUBMISSION_ID, SubmissionStatusEnum.RECEIVED);
    	
    	// hit evalId and hit status => should find 1 submission
    	List<Submission> subs = submissionDAO.getAllByEvaluationAndStatus(evalId, SubmissionStatusEnum.RECEIVED, 10, 0);
    	assertEquals(1, subs.size());
    	assertEquals(subs.size(), submissionDAO.getCountByEvaluationAndStatus(evalId, SubmissionStatusEnum.RECEIVED));
       	Submission retrieved = subs.get(0);
       	submission.setCreatedOn(retrieved.getCreatedOn());
    	assertEquals(retrieved, submission);
    	
    	// miss evalId and hit status => should find 0 submissions
    	subs = submissionDAO.getAllByEvaluationAndStatus(EVALID_DOES_NOT_EXIST, SubmissionStatusEnum.RECEIVED, 10, 0);
    	assertEquals(0, subs.size());
    	assertEquals(subs.size(), submissionDAO.getCountByEvaluationAndStatus(EVALID_DOES_NOT_EXIST, SubmissionStatusEnum.RECEIVED));
    	
    	// hit evalId and miss status => should find 0 submissions
    	subs = submissionDAO.getAllByEvaluationAndStatus(evalId, SubmissionStatusEnum.SCORED, 10, 0);
    	assertEquals(0, subs.size());
    	assertEquals(subs.size(), submissionDAO.getCountByEvaluationAndStatus(evalId, SubmissionStatusEnum.SCORED));
    }
    
   @Test
   public void testGetAllByEvaluationAndStatusWithContributors() throws DatastoreException, NotFoundException, JSONObjectAdapterException {
   	submissionDAO.create(submission2);
   	
   	// create a SubmissionStatus object
   	createSubmissionStatus(SUBMISSION_2_ID, SubmissionStatusEnum.RECEIVED);
       	
   	// hit evalId and hit status => should find 1 submission
   	List<Submission> subs = submissionDAO.getAllByEvaluationAndStatus(evalId, SubmissionStatusEnum.RECEIVED, 10, 0);
   	assertEquals(1, subs.size());
   	assertEquals(subs.size(), submissionDAO.getCountByEvaluationAndStatus(evalId, SubmissionStatusEnum.RECEIVED));
    Submission retrieved = subs.get(0);
    submission2.setCreatedOn(retrieved.getCreatedOn());
    // need to nullify the contributor createdOn dates to be able to compare to 'submission2'
 	nullifyContributorCreatedOn(retrieved);
   	assertEquals(retrieved, submission2);
   	
   }
   
    @Test
    public void testGetAllByEvaluationAndUser() throws DatastoreException, NotFoundException, JSONObjectAdapterException {
    	submissionDAO.create(submission);
    	
    	// hit evalId and hit user => should find 1 submission
    	List<Submission> subs = submissionDAO.getAllByEvaluationAndUser(evalId, userId, 10, 0);
    	assertEquals(1, subs.size());
    	assertEquals(subs.size(), submissionDAO.getCountByEvaluationAndUser(evalId, userId));
      	Submission retrieved = subs.get(0);
      	submission.setCreatedOn(retrieved.getCreatedOn());
    	assertEquals(retrieved, submission);
    	
    	// miss evalId and hit user => should find 0 submissions
    	subs = submissionDAO.getAllByEvaluationAndUser(EVALID_DOES_NOT_EXIST, userId, 10, 0);
    	assertEquals(0, subs.size());
    	assertEquals(subs.size(), submissionDAO.getCountByEvaluationAndUser(EVALID_DOES_NOT_EXIST, userId));
    	
    	// hit evalId and miss user => should find 0 submissions
    	subs = submissionDAO.getAllByEvaluationAndUser(evalId, USERID_DOES_NOT_EXIST, 10, 0);
    	assertEquals(0, subs.size());
    	assertEquals(subs.size(), submissionDAO.getCountByEvaluationAndUser(evalId, USERID_DOES_NOT_EXIST));
    }
    
    @Test
    public void testGetAllByEvaluationAndUserWithContributors() throws DatastoreException, NotFoundException, JSONObjectAdapterException {
    	submissionDAO.create(submission2);
    	
    	// hit evalId and hit user => should find 1 submission
    	List<Submission> subs = submissionDAO.getAllByEvaluationAndUser(evalId, userId2, 10, 0);
    	assertEquals(1, subs.size());
    	assertEquals(subs.size(), submissionDAO.getCountByEvaluationAndUser(evalId, userId2));
      	Submission retrieved = subs.get(0);
      	submission2.setCreatedOn(retrieved.getCreatedOn());
        // need to nullify the contributor createdOn dates to be able to compare to 'submission2'
     	nullifyContributorCreatedOn(retrieved);
   	assertEquals(retrieved, submission2);
    }
    
    @Test
    public void testDtoToDbo() {
    	Submission subDTO = new Submission();
    	Submission subDTOclone = new Submission();
    	SubmissionDBO subDBO = new SubmissionDBO();
    	SubmissionDBO subDBOclone = new SubmissionDBO();
    	
    	subDTO.setEvaluationId("123");
    	subDTO.setCreatedOn(new Date());
    	subDTO.setEntityId("syn456");
    	subDTO.setId("789");
    	subDTO.setName("name");
    	subDTO.setUserId("42");
    	subDTO.setSubmitterAlias("Team Awesome");
    	subDTO.setVersionNumber(1L);
    	subDTO.setEntityBundleJSON("foo");
    	    	
    	SubmissionUtils.copyDtoToDbo(subDTO, subDBO);
    	SubmissionUtils.copyDboToDto(subDBO, subDTOclone);
    	SubmissionUtils.copyDtoToDbo(subDTOclone, subDBOclone);
    	
    	assertEquals(subDTO, subDTOclone);
    	assertEquals(subDBO, subDBOclone);
    }
    
    @Test
    public void testDtoToDboNullColumn() {
    	Submission subDTO = new Submission();
    	Submission subDTOclone = new Submission();
    	SubmissionDBO subDBO = new SubmissionDBO();
    	SubmissionDBO subDBOclone = new SubmissionDBO();
    	
    	subDTO.setEvaluationId("123");
    	subDTO.setCreatedOn(new Date());
    	subDTO.setEntityId("syn456");
    	subDTO.setId("789");
    	subDTO.setName("name");
    	subDTO.setUserId("42");
    	subDTO.setSubmitterAlias("Team Awesome");
    	subDTO.setVersionNumber(1L);
    	// null EntityBundle
    	    	
    	SubmissionUtils.copyDtoToDbo(subDTO, subDBO);
    	SubmissionUtils.copyDboToDto(subDBO, subDTOclone);
    	SubmissionUtils.copyDtoToDbo(subDTOclone, subDBOclone);
    	
    	assertEquals(subDTO, subDTOclone);
    	assertEquals(subDBO, subDBOclone);
    	assertNull(subDTOclone.getEntityBundleJSON());
    	assertNull(subDBOclone.getEntityBundle());
    }
    
    @Test(expected=IllegalArgumentException.class)
    public void testMissingVersionNumber() throws DatastoreException, JSONObjectAdapterException {
        submission.setVersionNumber(null);
        submissionDAO.create(submission);
    }
    
    // Should be able to have null entity bundle
    @Test
    public void testPLFM1859() {
    	submission.setEntityBundleJSON(null);
    	String id = submissionDAO.create(submission);
    	assertNotNull(id);
    }
    
    @Test
    public void testQueryTeamSubmissions() throws Exception {
    	submissionDAO.create(submission);
     	createSubmissionStatus(SUBMISSION_ID, SubmissionStatusEnum.SCORED);
     	submissionDAO.create(submission2);
      	createSubmissionStatus(SUBMISSION_2_ID, SubmissionStatusEnum.SCORED);
      	     	
     	Date startDateIncl = new Date(CREATION_TIME_STAMP-1000L);
     	Date endDateExcl = new Date(CREATION_TIME_STAMP+1000L);
     	Set<SubmissionStatusEnum> statuses = new HashSet<SubmissionStatusEnum>();
     	statuses.add(SubmissionStatusEnum.SCORED);
     	
      	// happy case
     	assertEquals(1L, submissionDAO.countSubmissionsByTeam(Long.parseLong(evalId), 
      			Long.parseLong(submissionTeam.getId()), startDateIncl, endDateExcl, statuses));
      	// also works if start, end or status filters are omitted
       	assertEquals(1L, submissionDAO.countSubmissionsByTeam(Long.parseLong(evalId), 
      			Long.parseLong(submissionTeam.getId()), null, null, null));
    	assertEquals(1L, submissionDAO.countSubmissionsByTeam(Long.parseLong(evalId), 
      			Long.parseLong(submissionTeam.getId()), startDateIncl, null, null));
    	assertEquals(1L, submissionDAO.countSubmissionsByTeam(Long.parseLong(evalId), 
      			Long.parseLong(submissionTeam.getId()), null, endDateExcl, null));
    	assertEquals(1L, submissionDAO.countSubmissionsByTeam(Long.parseLong(evalId), 
      			Long.parseLong(submissionTeam.getId()), startDateIncl, endDateExcl, null));
      	assertEquals(1L, submissionDAO.countSubmissionsByTeam(Long.parseLong(evalId), 
      			Long.parseLong(submissionTeam.getId()), null, null, statuses));
      	
       	// ok if right at start of time range
    	assertEquals(1L, submissionDAO.countSubmissionsByTeam(Long.parseLong(evalId), 
      			Long.parseLong(submissionTeam.getId()), new Date(CREATION_TIME_STAMP), endDateExcl, statuses));
      	// fails if outside time range
     	assertEquals(0L, submissionDAO.countSubmissionsByTeam(Long.parseLong(evalId), 
      			Long.parseLong(submissionTeam.getId()), new Date(CREATION_TIME_STAMP+10L), endDateExcl, statuses));
     	assertEquals(0L, submissionDAO.countSubmissionsByTeam(Long.parseLong(evalId), 
      			Long.parseLong(submissionTeam.getId()), startDateIncl, new Date(CREATION_TIME_STAMP), statuses));
     	// fails if we look for a different status
    	assertEquals(0L, submissionDAO.countSubmissionsByTeam(Long.parseLong(evalId), 
      			Long.parseLong(submissionTeam.getId()), null, null, Collections.singleton(REJECTED)));
    	// fails if we look for a different Team
       	assertEquals(0L, submissionDAO.countSubmissionsByTeam(Long.parseLong(evalId), 
      			Long.parseLong(submissionTeam.getId())-100L, null, null, null));
       	// fails if we look for a different evaluation
       	assertEquals(0L, submissionDAO.countSubmissionsByTeam(Long.parseLong(evalId)-100L, 
      			Long.parseLong(submissionTeam.getId()), null, null, null));
    	
    }
    
    @Test
    public void testCountSubmissionsByTeamMembers() throws Exception {
    	submissionDAO.create(submission);
     	createSubmissionStatus(SUBMISSION_ID, SubmissionStatusEnum.SCORED);
     	submissionDAO.create(submission2);
     	createSubmissionStatus(SUBMISSION_2_ID, SubmissionStatusEnum.SCORED);
    	submissionDAO.create(submission3);
     	createSubmissionStatus(SUBMISSION_3_ID, SubmissionStatusEnum.SCORED);
      	     	
     	Date startDateIncl = new Date(CREATION_TIME_STAMP-1000L);
     	Date endDateExcl = new Date(CREATION_TIME_STAMP+1000L);
     	Set<SubmissionStatusEnum> statuses = new HashSet<SubmissionStatusEnum>();
     	statuses.add(SubmissionStatusEnum.SCORED);
     	
     	// happy case
     	Map<Long,Long> expected = new HashMap<Long,Long>();
    	expected.put(Long.parseLong(userId), 1L); // userId contributed to submission2
    	expected.put(Long.parseLong(userId2), 2L); // userId2 contributed to submission2, submission3
     	assertEquals(expected, submissionDAO.countSubmissionsByTeamMembers(Long.parseLong(evalId), 
			Long.parseLong(submissionTeam.getId()), null, null, null));
     	assertEquals(expected, submissionDAO.countSubmissionsByTeamMembers(Long.parseLong(evalId), 
			Long.parseLong(submissionTeam.getId()), startDateIncl, endDateExcl, statuses));
     	
     	// what if one submission doesn't match our criteria?
     	SubmissionStatus sub3Status = submissionStatusDAO.get(SUBMISSION_3_ID);
     	sub3Status.setStatus(REJECTED);
     	submissionStatusDAO.update(Collections.singletonList(sub3Status));
     	assertEquals(expected, submissionDAO.countSubmissionsByTeamMembers(Long.parseLong(evalId), 
			Long.parseLong(submissionTeam.getId()), null, null, null));
       	expected.put(Long.parseLong(userId2), 1L);
    	assertEquals(expected, submissionDAO.countSubmissionsByTeamMembers(Long.parseLong(evalId), 
			Long.parseLong(submissionTeam.getId()), startDateIncl, endDateExcl, statuses));
     }
    
    @Test
    public void testCountSubmissionsByContributor() throws Exception {
    	submissionDAO.create(submission);
     	createSubmissionStatus(SUBMISSION_ID, SubmissionStatusEnum.SCORED);
     	submissionDAO.create(submission2);
     	createSubmissionStatus(SUBMISSION_2_ID, SubmissionStatusEnum.SCORED);
    	submissionDAO.create(submission3);
     	createSubmissionStatus(SUBMISSION_3_ID, SubmissionStatusEnum.SCORED);
      	     	
     	Date startDateIncl = new Date(CREATION_TIME_STAMP-1000L);
     	Date endDateExcl = new Date(CREATION_TIME_STAMP+1000L);
     	Set<SubmissionStatusEnum> statuses = new HashSet<SubmissionStatusEnum>();
     	statuses.add(SubmissionStatusEnum.SCORED);

    	assertEquals(1L, submissionDAO.countSubmissionsByContributor(Long.parseLong(evalId), 
			Long.parseLong(userId), startDateIncl, endDateExcl, statuses));
    	assertEquals(2L, submissionDAO.countSubmissionsByContributor(Long.parseLong(evalId), 
			Long.parseLong(userId2), startDateIncl, endDateExcl, statuses));
    }
    
    @Test
    public void testGetTeamMembersSubmittingElsewhere() throws Exception {
    	
    }
    
    @Test
    public void testHasContributedToTeamSubmission() throws Exception {
    	submissionDAO.create(submission);
     	createSubmissionStatus(SUBMISSION_ID, SubmissionStatusEnum.SCORED);
     	submissionDAO.create(submission2);
     	createSubmissionStatus(SUBMISSION_2_ID, SubmissionStatusEnum.SCORED);
    	submissionDAO.create(submission3);
     	createSubmissionStatus(SUBMISSION_3_ID, SubmissionStatusEnum.SCORED);
      	     	
     	Date startDateIncl = new Date(CREATION_TIME_STAMP-1000L);
     	Date endDateExcl = new Date(CREATION_TIME_STAMP+1000L);
     	Set<SubmissionStatusEnum> statuses = new HashSet<SubmissionStatusEnum>();
     	statuses.add(SubmissionStatusEnum.SCORED);

    	assertEquals(true, submissionDAO.hasContributedToTeamSubmission(Long.parseLong(evalId), 
			Long.parseLong(userId), startDateIncl, endDateExcl, statuses));
    	assertEquals(true, submissionDAO.hasContributedToTeamSubmission(Long.parseLong(evalId), 
			Long.parseLong(userId2), startDateIncl, endDateExcl, statuses));
    	
    	submissionDAO.delete(SUBMISSION_2_ID);
       	assertEquals(false, submissionDAO.hasContributedToTeamSubmission(Long.parseLong(evalId), 
    			Long.parseLong(userId), startDateIncl, endDateExcl, statuses));
    }
}
