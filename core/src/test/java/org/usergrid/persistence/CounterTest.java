/*******************************************************************************
 * Copyright 2012 Apigee Corporation
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/
package org.usergrid.persistence;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.usergrid.persistence.cassandra.CassandraService.MANAGEMENT_APPLICATION_ID;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.junit.Test;
import org.usergrid.persistence.entities.Event;
import org.usergrid.persistence.entities.Group;
import org.usergrid.utils.JsonUtils;

public class CounterTest extends AbstractPersistenceTest {

	private static final Logger logger = LoggerFactory.getLogger(CounterTest.class);

  long ts = System.currentTimeMillis() - (24 * 60 * 60 * 1000);

	public CounterTest() {
		super();
	}

	@Test
	public void testCounters() throws Exception {
		logger.info("CounterTest.testCounters");

		UUID applicationId = createApplication("testCounters");
		assertNotNull(applicationId);

		EntityManager em = emf.getEntityManager(applicationId);
		assertNotNull(em);


		UUID user1 = UUID.randomUUID();
		UUID user2 = UUID.randomUUID();
		// UUID groupId = UUID.randomUUID();

		Event event = null;

		for (int i = 0; i < 1000; i++) {
			event = new Event();
			event.setTimestamp(ts + (i * 60 * 1000));
			event.addCounter("visits", 1);
			event.setUser(user1);
			em.create(event);

			event = new Event();
			event.setTimestamp(ts + (i * 60 * 1000));
			event.addCounter("visits", 1);
			event.setUser(user2);
			em.create(event);
		}

		Results r = em.getAggregateCounters(null, null, null, "visits",
				CounterResolution.SIX_HOUR, ts, System.currentTimeMillis(),
				false);
		logger.info(JsonUtils.mapToJsonString(r.getCounters()));

		r = em.getAggregateCounters(user1, null, null, "visits",
				CounterResolution.SIX_HOUR, ts, System.currentTimeMillis(),
				false);
		logger.info(JsonUtils.mapToJsonString(r.getCounters()));

		r = em.getAggregateCounters(user1, null, null, "visits",
				CounterResolution.SIX_HOUR, ts, System.currentTimeMillis(),
				true);
		logger.info(JsonUtils.mapToJsonString(r.getCounters()));

		r = em.getAggregateCounters(user1, null, null, "visits",
				CounterResolution.ALL, ts, System.currentTimeMillis(), false);
		logger.info(JsonUtils.mapToJsonString(r.getCounters()));

		for (int i = 0; i < 10; i++) {
			event = new Event();
			event.setTimestamp(ts + (i * 60 * 60 * 1000));
			event.addCounter("clicks", 1);
			em.create(event);
		}

		r = em.getAggregateCounters(null, null, null, "clicks",
				CounterResolution.HALF_HOUR, ts, System.currentTimeMillis(),
				true);
		logger.info(JsonUtils.mapToJsonString(r.getCounters()));

		Query query = new Query();
		query.addCounterFilter("clicks:*:*:*");
		query.addCounterFilter("visits:*:*:*");
		query.setStartTime(ts);
		query.setFinishTime(System.currentTimeMillis());
		query.setResolution(CounterResolution.SIX_HOUR);
		query.setPad(true);
		r = em.getAggregateCounters(query);
		logger.info(JsonUtils.mapToJsonString(r.getCounters()));

		logger.info(JsonUtils.mapToJsonString(em.getCounterNames()));

		Map<String, Long> counts = em.getApplicationCounters();
		logger.info(JsonUtils.mapToJsonString(counts));

		assertEquals(new Long(10), counts.get("clicks"));
		assertEquals(new Long(2000), counts.get("visits"));
		assertEquals(new Long(2010),
				counts.get("application.collection.events"));
	}

  @Test
  public void testCommunityCounters() throws Exception {
    EntityManager em = emf.getEntityManager(MANAGEMENT_APPLICATION_ID);

    Group organizationEntity = new Group();
    organizationEntity.setPath("tst-counter");
    organizationEntity.setProperty("name","test counter");
    organizationEntity = em.create(organizationEntity);


    UUID applicationId = emf.createApplication("testEntityCounters");

    Map<String,Object> properties = new LinkedHashMap<String,Object>();
    properties.put("name", "testEntityCounters");
    Entity applicationEntity = em.create(applicationId, "application_info",
            properties);

    em.createConnection(new SimpleEntityRef("group", organizationEntity.getUuid()),
            "owns", new SimpleEntityRef("application_info", applicationId));

    Event event = new Event();
    event.setTimestamp(System.currentTimeMillis());
    event.addCounter("admin_logins", 1);
    event.setGroup(organizationEntity.getUuid());
    em.create(event);


    Map<String, Long> counts = em.getApplicationCounters();
    logger.info(JsonUtils.mapToJsonString(counts));
    assertNotNull(counts.get("admin_logins"));
    assertEquals(1,counts.get("admin_logins").longValue());


    Results r = em.getAggregateCounters(null, null, null, "admin_logins",
            CounterResolution.FIVE_MINUTES, ts, System.currentTimeMillis(),
            false);
    logger.info(JsonUtils.mapToJsonString(r.getCounters()));
    assertEquals(1, r.getCounters().get(0).getValues().get(0).getValue());
    //counts = em.getEntityCounters(organizationEntity.getUuid());
    //logger.info(JsonUtils.mapToJsonString(counts));
    Query query = new Query();
    query.addCounterFilter("admin_logins:*:*:*");
    query.setStartTime(ts);
    query.setFinishTime(System.currentTimeMillis());
    query.setResolution(CounterResolution.SIX_HOUR);
    //query.setPad(true);
    r = em.getAggregateCounters(query);
    logger.info(JsonUtils.mapToJsonString(r.getCounters()));
    assertEquals(1, r.getCounters().get(0).getValues().get(0).getValue());

  }
}
