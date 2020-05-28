package org.sagebionetworks.table.worker;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.sagebionetworks.AsynchronousJobWorkerHelper;
import org.sagebionetworks.evaluation.manager.SubmissionManager;
import org.sagebionetworks.evaluation.model.Evaluation;
import org.sagebionetworks.evaluation.model.SubmissionBundle;
import org.sagebionetworks.evaluation.model.SubmissionStatus;
import org.sagebionetworks.evaluation.model.SubmissionStatusEnum;
import org.sagebionetworks.repo.manager.table.ColumnModelManager;
import org.sagebionetworks.repo.model.Entity;
import org.sagebionetworks.repo.model.Folder;
import org.sagebionetworks.repo.model.Project;
import org.sagebionetworks.repo.model.Team;
import org.sagebionetworks.repo.model.UserInfo;
import org.sagebionetworks.repo.model.annotation.v2.Annotations;
import org.sagebionetworks.repo.model.annotation.v2.AnnotationsV2TestUtils;
import org.sagebionetworks.repo.model.annotation.v2.AnnotationsV2Utils;
import org.sagebionetworks.repo.model.annotation.v2.AnnotationsValueType;
import org.sagebionetworks.repo.model.dao.table.TableRowTruthDAO;
import org.sagebionetworks.repo.model.dbo.dao.table.TableModelTestUtils;
import org.sagebionetworks.repo.model.entity.IdAndVersion;
import org.sagebionetworks.repo.model.jdo.KeyFactory;
import org.sagebionetworks.repo.model.table.ColumnModel;
import org.sagebionetworks.repo.model.table.ColumnType;
import org.sagebionetworks.repo.model.table.QueryResultBundle;
import org.sagebionetworks.repo.model.table.Row;
import org.sagebionetworks.repo.model.table.SubmissionView;
import org.sagebionetworks.table.cluster.TableIndexDAO;
import org.sagebionetworks.table.cluster.utils.TableModelUtils;
import org.sagebionetworks.worker.TestHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import com.google.common.collect.ImmutableList;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(locations = { "classpath:test-context.xml" })
@ActiveProfiles("test-view-workers")
public class SubmissionViewIntegrationTest {

	private static final int MAX_WAIT = 2 * 60 * 1000;
	
	@Autowired
	private SubmissionManager submissionManager;
	
	@Autowired
	private AsynchronousJobWorkerHelper asyncHelper;
	
	@Autowired
	private TestHelper testHelper;
	
	@Autowired
	private TableIndexDAO indexDao;
	
	@Autowired
	private ColumnModelManager modelManager;

	@Autowired
	private TableRowTruthDAO tableRowTruthDao;
	
	private UserInfo evaluationOwner;

	private UserInfo submitter1;
	private UserInfo submitter2;
	
	private Team submitterTeam;

	private Project evaluationProject;
	private Project submitter1Project;
	private Project submitter2Project;
	
	private Evaluation evaluation;
	
	private SubmissionView view;
	
	@BeforeEach
	public void before() throws Exception {
		
		tableRowTruthDao.truncateAllRowData();
		
		testHelper.before();
		
		evaluationOwner = testHelper.createUser();
		
		submitter1 = testHelper.createUser();
		submitter2 = testHelper.createUser();
		
		submitterTeam = testHelper.createTeam(submitter1);
		// Share one team for submissions
		submitter2.getGroups().add(Long.valueOf(submitterTeam.getId()));
		
		submitter1Project = testHelper.createProject(submitter1);
		submitter2Project = testHelper.createProject(submitter2);
				
		evaluationProject = testHelper.createProject(evaluationOwner);
		evaluation = testHelper.createEvaluation(evaluationOwner, evaluationProject);

		testHelper.setEvaluationACLForSubmission(evaluationOwner, evaluation, submitterTeam);
	}

	@AfterEach
	public void after() {

		tableRowTruthDao.truncateAllRowData();
		
		testHelper.cleanup();
		
		indexDao.deleteTable(IdAndVersion.parse(view.getId()));
	}
	
	@Test
	public void testSubmissionView() throws Exception {
		Folder entity1 = testHelper.createFolder(submitter1, submitter1Project);
		Folder entity2 = testHelper.createFolder(submitter2, submitter2Project);
		
		SubmissionBundle submission1 = testHelper.createSubmission(submitter1, evaluation, entity1);
		SubmissionBundle submission2 = testHelper.createSubmission(submitter2, evaluation, entity2);
		
		view = createView(evaluationOwner, evaluationProject, evaluation);
		
		List<SubmissionBundle> submissions = ImmutableList.of(submission1, submission2);		
		
		Predicate<QueryResultBundle> submissionMatcher = idAndEtagMatcher(submissions);
		
		// Wait for the results
		QueryResultBundle result = asyncHelper.waitForConsistentQuery(evaluationOwner, "select * from " + view.getId() + " order by id", submissionMatcher, MAX_WAIT);

		List<Row> expectedRows = mapSubmissions(evaluation, submissions);
		
		assertEquals(expectedRows, result.getQueryResult().getQueryResults().getRows());
	}
	
	@Test
	public void testSubmissionViewWithTeam() throws Exception {
		Folder entity1 = testHelper.createFolder(submitter1, submitter1Project);
		
		SubmissionBundle submission1 = testHelper.createSubmission(submitter1, evaluation, entity1, submitterTeam);
		
		view = createView(evaluationOwner, evaluationProject, evaluation);
		
		List<SubmissionBundle> submissions = ImmutableList.of(submission1);
		
		Predicate<QueryResultBundle> submissionMatcher = idAndEtagMatcher(submissions);
		
		// Wait for the results
		QueryResultBundle result = asyncHelper.waitForConsistentQuery(evaluationOwner, "select * from " + view.getId() + " order by id", submissionMatcher, MAX_WAIT);

		List<Row> expectedRows = mapSubmissions(evaluation, submissions);
		
		assertEquals(expectedRows, result.getQueryResult().getQueryResults().getRows());
	}
	
	@Test
	public void testSubmissionViewWithNewSubmmision() throws Exception {
		
		Folder entity1 = testHelper.createFolder(submitter1, submitter1Project);
		
		SubmissionBundle submission1 = testHelper.createSubmission(submitter1, evaluation, entity1);
		
		view = createView(evaluationOwner, evaluationProject, evaluation);
		
		List<SubmissionBundle> submissions = ImmutableList.of(submission1);		
		
		Predicate<QueryResultBundle> submissionMatcher = idAndEtagMatcher(submissions);
		
		// Wait for the results
		QueryResultBundle result = asyncHelper.waitForConsistentQuery(evaluationOwner, "select * from " + view.getId() + " order by id", submissionMatcher, MAX_WAIT);

		List<Row> expectedRows = mapSubmissions(evaluation, submissions);
		
		assertEquals(expectedRows, result.getQueryResult().getQueryResults().getRows());

		// Add another submission
		Folder entity2 = testHelper.createFolder(submitter2, submitter2Project);
		SubmissionBundle submission2 = testHelper.createSubmission(submitter2, evaluation, entity2);
		
		submissions = ImmutableList.of(submission1, submission2);
		
		submissionMatcher = idAndEtagMatcher(submissions);
		
		// Wait for the results
		result = asyncHelper.waitForConsistentQuery(evaluationOwner, "select * from " + view.getId() + " order by id", submissionMatcher, MAX_WAIT);

		expectedRows = mapSubmissions(evaluation, submissions);
		
		assertEquals(expectedRows, result.getQueryResult().getQueryResults().getRows());
	}
	
	@Test
	public void testSubmissionViewWithStatusUpdate() throws Exception {
		
		Folder entity1 = testHelper.createFolder(submitter1, submitter1Project);
		Folder entity2 = testHelper.createFolder(submitter2, submitter2Project);

		SubmissionBundle submission1 = testHelper.createSubmission(submitter1, evaluation, entity1);
		SubmissionBundle submission2 = testHelper.createSubmission(submitter2, evaluation, entity2);
		
		view = createView(evaluationOwner, evaluationProject, evaluation);
		
		List<SubmissionBundle> submissions = ImmutableList.of(submission1, submission2);		
		
		Predicate<QueryResultBundle> submissionMatcher = idAndEtagMatcher(submissions);
		
		// Wait for the results
		QueryResultBundle result = asyncHelper.waitForConsistentQuery(evaluationOwner, "select * from " + view.getId() + " order by id", submissionMatcher, MAX_WAIT);

		List<Row> expectedRows = mapSubmissions(evaluation, submissions);
		
		assertEquals(expectedRows, result.getQueryResult().getQueryResults().getRows());

		// Updates the status of the 1st submission
		SubmissionStatus status = submissionManager.getSubmissionStatus(evaluationOwner, submission1.getSubmission().getId());
		
		status.setStatus(SubmissionStatusEnum.ACCEPTED);
		
		status = submissionManager.updateSubmissionStatus(evaluationOwner, status);
		
		submission1.setSubmissionStatus(status);
		
		// Wait for the updated results
		result = asyncHelper.waitForConsistentQuery(evaluationOwner, "select * from " + view.getId() + " order by id", submissionMatcher, MAX_WAIT);
		
		expectedRows = mapSubmissions(evaluation, submissions);
		
		assertEquals(expectedRows, result.getQueryResult().getQueryResults().getRows());

	}
	
	@Test
	public void testSubmissionViewWithAnnotations() throws Exception {
		
		Folder entity1 = testHelper.createFolder(submitter1, submitter1Project);

		SubmissionBundle submission1 = testHelper.createSubmission(submitter1, evaluation, entity1);
		
		view = createView(evaluationOwner, evaluationProject, evaluation);
		
		List<SubmissionBundle> submissions = ImmutableList.of(submission1);
		
		Predicate<QueryResultBundle> submissionMatcher = idAndEtagMatcher(submissions);
		
		// Wait for the results
		QueryResultBundle result = asyncHelper.waitForConsistentQuery(evaluationOwner, "select * from " + view.getId() + " order by id", submissionMatcher, MAX_WAIT);

		List<Row> expectedRows = mapSubmissions(evaluation, submissions);
		
		assertEquals(expectedRows, result.getQueryResult().getQueryResults().getRows());
		
		// Now add the "foo" column to the model
		List<ColumnModel> columnModels = result.getColumnModels();
		
		ColumnModel fooColumnModel = modelManager.createColumnModel(evaluationOwner, TableModelTestUtils.createColumn(null, "foo", ColumnType.STRING));

		columnModels.add(fooColumnModel);
		
		// Updates the schema
		asyncHelper.setTableSchema(evaluationOwner, TableModelUtils.getIds(columnModels), view.getId(), MAX_WAIT);

		// Updates the status of the 1st submission adding the foo annotation
		SubmissionStatus status = submissionManager.getSubmissionStatus(evaluationOwner, submission1.getSubmission().getId());
		
		Annotations annotations = AnnotationsV2Utils.emptyAnnotations();
		AnnotationsV2TestUtils.putAnnotations(annotations, "foo", "bar", AnnotationsValueType.STRING);
		
		status.setSubmissionAnnotations(annotations);
		status.setStatus(SubmissionStatusEnum.ACCEPTED);
		
		status = submissionManager.updateSubmissionStatus(evaluationOwner, status);
		
		submission1.setSubmissionStatus(status);
		
		// Wait for the updated results
		result = asyncHelper.waitForConsistentQuery(evaluationOwner, "select * from " + view.getId() + " order by id", submissionMatcher, MAX_WAIT);
		
		expectedRows = mapSubmissions(evaluation, submissions);
		
		assertEquals(expectedRows, result.getQueryResult().getQueryResults().getRows());

	}
	
	private static List<Row> mapSubmissions(Evaluation evaluation, List<SubmissionBundle> submissions) {
		return submissions.stream().map((bundle) -> mapSubmission(evaluation, bundle)).collect(Collectors.toList());
	}
	
	private static Row mapSubmission(Evaluation evaluation, SubmissionBundle bundle) {
		Row row = new Row();
		
		Long id = KeyFactory.stringToKey(bundle.getSubmission().getId());
		String eTag = bundle.getSubmissionStatus().getEtag();
		Long evaluationId = KeyFactory.stringToKey(bundle.getSubmission().getEvaluationId());
		Long versionNumber = bundle.getSubmissionStatus().getStatusVersion();
		
		
		row.setRowId(id);
		row.setEtag(eTag);
		row.setVersionNumber(versionNumber);
		
		List<String> values = new ArrayList<>();
		
		// Default object fields
		values.add(id.toString());
		values.add(bundle.getSubmission().getName());
		values.add(String.valueOf(bundle.getSubmission().getCreatedOn().getTime()));
		values.add(bundle.getSubmission().getUserId());
		values.add(eTag);
		values.add(String.valueOf(bundle.getSubmissionStatus().getModifiedOn().getTime()));
		values.add(evaluation.getContentSource());
		
		// Custom submission fields
		values.add(bundle.getSubmissionStatus().getStatus().name());
		values.add(evaluationId.toString());
		values.add(bundle.getSubmission().getTeamId() == null ? bundle.getSubmission().getUserId() : bundle.getSubmission().getTeamId());
		values.add(bundle.getSubmission().getSubmitterAlias());
		values.add(bundle.getSubmission().getEntityId());
		values.add(bundle.getSubmission().getVersionNumber().toString());
		values.add(bundle.getSubmission().getDockerRepositoryName());
		values.add(bundle.getSubmission().getDockerDigest());
		
		
		Annotations annotations = bundle.getSubmissionStatus().getSubmissionAnnotations();
		
		if (annotations != null) {
			annotations.getAnnotations().entrySet().forEach((entry) -> {
				List<String> annotationValues = entry.getValue().getValue();
				
				values.add(annotationValues == null || annotationValues.isEmpty() ? null : annotationValues.iterator().next());
			});
		}
		
		row.setValues(values);
		
		return row;
	}
	
	private static Predicate<QueryResultBundle> idAndEtagMatcher(final List<SubmissionBundle> submissions) {
		return (QueryResultBundle result) -> {
			List<Row> rows = result.getQueryResult().getQueryResults().getRows();
			
			if (rows.size() != submissions.size()) {
				return false;
			}
			
			for (int i=0; i<rows.size(); i++) {
				Row row = rows.get(i);
				SubmissionStatus status = submissions.get(i).getSubmissionStatus();
				Long id = KeyFactory.stringToKey(status.getId());
				String etag = status.getEtag();
				
				if (row.getRowId().equals(id) && row.getEtag().equals(etag)) {
					continue;
				}
				
				return false;
			}
			
			return true;
		};
	}
	
	private SubmissionView createView(UserInfo user, Entity parent, Evaluation evaluation) {
		List<String> scope = ImmutableList.of(evaluation.getId());
		return asyncHelper.createSubmissionView(user, UUID.randomUUID().toString(), parent.getId(), scope);
	}
	
}
