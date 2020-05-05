package org.sagebionetworks.repo.manager.table.metadata;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.sagebionetworks.repo.model.ObjectType;
import org.sagebionetworks.table.cluster.metadata.MetadataIndexProvider;
import org.sagebionetworks.table.cluster.metadata.MetadataIndexProviderFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(locations = { "classpath:test-context.xml" })
public class EntityMetadataIndexProviderTest {

	@Autowired
	private EntityMetadataIndexProvider entityProvider;
	
	@Autowired
	private MetadataIndexProviderFactory factory;
	
	@Test
	public void testWiring() {
		MetadataIndexProvider provider = factory.getMetadataIndexProvider(ObjectType.ENTITY);
		
		assertEquals(entityProvider, provider);
	}
	
}
