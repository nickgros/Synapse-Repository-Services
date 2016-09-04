package org.sagebionetworks.repo.web.filter;

import static org.junit.Assert.*;
import static org.mockito.Mockito.verify;
import static org.sagebionetworks.repo.web.filter.ThrottleUtils.generateCloudwatchProfiledata;
import static org.sagebionetworks.repo.web.filter.ThrottleUtils.setResponseError;
import static org.sagebionetworks.repo.web.filter.ThrottleUtils.isMigrationAdmin;

import java.io.IOException;
import java.io.PrintWriter;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;
import org.sagebionetworks.cloudwatch.ProfileData;
import org.sagebionetworks.repo.model.AuthorizationConstants;
import javax.servlet.http.HttpServletResponse;

@RunWith(MockitoJUnitRunner.class)
public class ThrottleUtilTest {
	
	private static final String eventName = "Event Name";
	private static final String reason = "Why? Because I can.";
	private static final Long userId = 123456L;
	private static final String namespace = "ecapseman";
	private static final int httpError = 420;
	
	@Mock
	HttpServletResponse mockResponse;
	
	@Mock
	PrintWriter mockWriter;
	
	////////////////////////////////
	// reportLockAcquireError() Tests
	////////////////////////////////
	
	@Test (expected = IllegalArgumentException.class)
	public void testReportLockAcquireErrorNullUserId(){
		generateCloudwatchProfiledata(null, eventName, namespace);
	}
	
	@Test (expected = IllegalArgumentException.class)
	public void testReportLockAcquireErrorNullEventName(){
		generateCloudwatchProfiledata(userId.toString(), null, namespace);
	}
	
	@Test (expected = IllegalArgumentException.class)
	public void testReportLockAcquireErrorNullFilterClass(){
		generateCloudwatchProfiledata(userId.toString(), eventName, null);
	}
	
	@Test
	public void testReportLockAcquireError(){
		ProfileData report = generateCloudwatchProfiledata(userId.toString(), eventName, namespace);
			
		assertEquals(eventName, report.getName());
		assertEquals(namespace, report.getNamespace());
		assertEquals(userId.toString(), report.getDimension().get("UserId"));
		assertEquals(1.0, report.getValue(), 1e-15);
		assertEquals("Count", report.getUnit());
	}
	
	////////////////////////////////////////////
	// httpTooManyRequestsErrorResponse() Tests
	////////////////////////////////////////////
	
	@Test (expected = IllegalArgumentException.class)
	public void testHttpTooManyRequestsErrorResponseNullResponse() throws IOException{
		setResponseError(null, httpError, reason);
	}
	
	@Test (expected = IllegalArgumentException.class)
	public void testHttpTooManyRequestsErrorResponseNullReason() throws IOException{
		setResponseError(mockResponse, httpError, null);
	}
	
	@Test
	public void testHttpTooManyRequestsErrorResponse() throws IOException{
		Mockito.when(mockResponse.getWriter()).thenReturn(mockWriter);
		
		setResponseError(mockResponse, httpError, reason);
		//TODO: Switch to 429 http code once clients have been implemented to expect that code
		verify(mockResponse).setStatus(httpError);
		verify(mockWriter).println(reason);
	}
	
	////////////////////////////
	// isMigrationAdmin() Tests
	////////////////////////////
	
	@Test
	public void testIsMigrationAdminWhenIsAdmin(){
		assertTrue(isMigrationAdmin(AuthorizationConstants.BOOTSTRAP_PRINCIPAL.THE_ADMIN_USER.getPrincipalId()));
	}
	
	@Test
	public void testIsMigrationAdminWhenIsNotAdmin(){
		assertFalse(isMigrationAdmin(userId));
	}
}