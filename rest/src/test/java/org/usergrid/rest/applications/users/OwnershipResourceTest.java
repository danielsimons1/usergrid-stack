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
package org.usergrid.rest.applications.users;

import org.codehaus.jackson.JsonNode;
import org.junit.Test;
import org.usergrid.rest.RestContextTest;
import org.usergrid.rest.test.resource.Connection;
import org.usergrid.rest.test.resource.CustomCollection;
import org.usergrid.rest.test.resource.app.queue.DevicesCollection;
import org.usergrid.rest.test.security.TestAppUser;
import org.usergrid.rest.test.security.TestUser;
import org.usergrid.utils.MapUtils;

import static org.junit.Assert.*;

/**
 * 
 */
public class OwnershipResourceTest extends RestContextTest {

  @Test
  public void meVerify() throws Exception {
    context.clearUser();
    TestUser user1 = new TestAppUser("testuser1@usergrid.org", "password", "testuser1@usergrid.org").create(context)
            .login(context).makeActive(context);
    String token = user1.getToken();
    JsonNode userNode = context.application().users().user("me").get();
    assertNotNull(userNode);
    String uuid = userNode.get("entities").get(0).get("uuid").getTextValue();
    assertNotNull(uuid);
    managementService.revokeAccessTokenForAppUser(token);

    try {
      context.application().users().user("me").get();
      fail();
    } catch(Exception ex) {
      ex.printStackTrace();
      assertTrue(ex.getMessage().contains("401"));
    }

  }

  @Test
  public void contextualPathOwnership() {

    // anonymous user
    context.clearUser();

    TestUser user1 = new TestAppUser("testuser1@usergrid.org", "password", "testuser1@usergrid.org").create(context)
        .login(context).makeActive(context);

    // create device 1 on user1 devices
    context.application().users().user("me").devices()
        .create(MapUtils.hashMap("name", "device1").map("number", "5551112222"));

    // anonymous user
    context.clearUser();

    // create device 2 on user 2
    TestUser user2 = new TestAppUser("testuser2@usergrid.org", "password", "testuser2@usergrid.org").create(context)
        .login(context).makeActive(context);

    context.application().users().user("me").devices()
        .create(MapUtils.hashMap("name", "device2").map("number", "5552223333"));

    // now query on user 1.

    DevicesCollection devices = context.withUser(user1).application().users().user("me").devices();

    JsonNode data = devices.device("device1").get();
    assertNotNull(data);
    assertEquals("device1", getEntity(data, 0).get("name").asText());

    // check we can't see device2
    data = devices.device("device2").get();
    assertNull(data);

    // do a collection load, make sure we're not loading device 2
    data = devices.get();

    assertEquals("device1", getEntity(data, 0).get("name").asText());
    assertNull(getEntity(data, 1));

    // log in as user 2 and check it
    devices = context.withUser(user2).application().users().user("me").devices();

    data = devices.device("device2").get();
    assertNotNull(data);
    assertEquals("device2", getEntity(data, 0).get("name").asText());

    // check we can't see device1
    data = devices.device("device1").get();
    assertNull(data);

    // do a collection load, make sure we're not loading device 1
    data = devices.get();

    assertEquals("device2", getEntity(data, 0).get("name").asText());
    assertNull(getEntity(data, 1));

    // we should see both devices when loaded from the root application

    // test for user 1

    devices = context.withUser(user1).application().devices();
    data = devices.device("device1").get();

    assertNotNull(data);
    assertEquals("device1", getEntity(data, 0).get("name").asText());

    data = devices.device("device2").get();

    assertNotNull(data);
    assertEquals("device2", getEntity(data, 0).get("name").asText());

    // test for user 2
    data = context.withUser(user2).application().devices().device("device1").get();

    assertNotNull(data);
    assertEquals("device1", getEntity(data, 0).get("name").asText());

    data = devices.device("device2").get();

    assertNotNull(data);
    assertEquals("device2", getEntity(data, 0).get("name").asText());

  }

  @Test
  public void contextualConnectionOwnership() {

    // anonymous user
    context.clearUser();

    TestUser user1 = new TestAppUser("testuser1@usergrid.org", "password", "testuser1@usergrid.org").create(context)
        .login(context).makeActive(context);

    // create a 4peaks restaurant
    JsonNode data = context.application().collection("restaurants").create(MapUtils.hashMap("name", "4peaks"));

    // create our connection
    data = context.application().users().user("me").connection("likes").collection("restaurants").entity("4peaks")
        .post();
    
    String peaksId = getEntity(data, 0).get("uuid").asText();

    // anonymous user
    context.clearUser();

    // create a restaurant and link it to user 2
    TestUser user2 = new TestAppUser("testuser2@usergrid.org", "password", "testuser2@usergrid.org").create(context)
        .login(context).makeActive(context);

    data = context.application().collection("restaurants").create(MapUtils.hashMap("name", "arrogantbutcher"));

    data = context.application().users().user("me").connection("likes").collection("restaurants")
        .entity("arrogantbutcher").post();

    String arrogantButcherId = getEntity(data, 0).get("uuid").asText();
    
    // now query on user 1.

    CustomCollection likeRestaurants = context.withUser(user1).application().users().user("me").connection("likes").collection("restaurants");

    //check we can get it via id
    data = likeRestaurants.entity(peaksId).get();
    assertNotNull(data);
    assertEquals("4peaks", getEntity(data, 0).get("name").asText());
    
    //check we can get it by name
    data = likeRestaurants.entity("4peaks").get();
    assertNotNull(data);
    assertEquals("4peaks", getEntity(data, 0).get("name").asText());

    // check we can't see arrogantbutcher by name or id
    data = likeRestaurants.entity("arrogantbutcher").get();
    assertNull(data);
    
    data = likeRestaurants.entity(arrogantButcherId).get();
    assertNull(data);

    // do a collection load, make sure we're not entities we shouldn't see
    data = likeRestaurants.get();

    assertEquals("4peaks", getEntity(data, 0).get("name").asText());
    assertNull(getEntity(data, 1));

    // log in as user 2 and check it
    likeRestaurants = context.withUser(user2).application().users().user("me").connection("likes").collection("restaurants");

    data = likeRestaurants.entity(arrogantButcherId).get();
    assertNotNull(data);
    assertEquals("arrogantbutcher", getEntity(data, 0).get("name").asText());
    
    data = likeRestaurants.entity("arrogantbutcher").get();
    assertNotNull(data);
    assertEquals("arrogantbutcher", getEntity(data, 0).get("name").asText());

    // check we can't see 4peaks
    data = likeRestaurants.entity("4peaks").get();
    assertNull(data);
    
    data = likeRestaurants.entity(peaksId).get();
    assertNull(data);

    // do a collection load, make sure we're not loading device 1
    data = likeRestaurants.get();

    assertEquals("arrogantbutcher", getEntity(data, 0).get("name").asText());
    assertNull(getEntity(data, 1));

    // we should see both devices when loaded from the root application

    // test for user 1

    CustomCollection restaurants = context.withUser(user1).application().collection("restaurants");
    data = restaurants.entity("4peaks").get();

    assertNotNull(data);
    assertEquals("4peaks", getEntity(data, 0).get("name").asText());

    data = restaurants.entity("arrogantbutcher").get();

    assertNotNull(data);
    assertEquals("arrogantbutcher", getEntity(data, 0).get("name").asText());

    // test for user 2
    restaurants = context.withUser(user1).application().collection("restaurants");
    data = restaurants.entity("4peaks").get();

    assertNotNull(data);
    assertEquals("4peaks", getEntity(data, 0).get("name").asText());

    data = restaurants.entity("arrogantbutcher").get();

    assertNotNull(data);
    assertEquals("arrogantbutcher", getEntity(data, 0).get("name").asText());

  }

  

  @Test
  public void contextualConnectionOwnershipNoCollectionName() {

    // anonymous user
    context.clearUser();

    TestUser user1 = new TestAppUser("testuser1@usergrid.org", "password", "testuser1@usergrid.org").create(context)
        .login(context).makeActive(context);

    // create a 4peaks restaurant
    JsonNode data = context.application().collection("restaurants").create(MapUtils.hashMap("name", "4peaks"));
    
    String peaksId = getEntity(data, 0).get("uuid").asText();


    // create our connection
    data = context.application().users().user("me").connection("likes").entity(peaksId).post();
    
  
    // anonymous user
    context.clearUser();

    // create a restaurant and link it to user 2
    TestUser user2 = new TestAppUser("testuser2@usergrid.org", "password", "testuser2@usergrid.org").create(context)
        .login(context).makeActive(context);

    data = context.application().collection("restaurants").create(MapUtils.hashMap("name", "arrogantbutcher"));
    
    String arrogantButcherId = getEntity(data, 0).get("uuid").asText();
    

    data = context.application().users().user("me").connection("likes").entity(arrogantButcherId).post();

    // now query on user 1.

    Connection likeRestaurants = context.withUser(user1).application().users().user("me").connection("likes");

    //check we can get it via id
    data = likeRestaurants.entity(peaksId).get();
    assertNotNull(data);
    assertEquals("4peaks", getEntity(data, 0).get("name").asText());
    
    data = likeRestaurants.entity(arrogantButcherId).get();
    assertNull(data);

    // do a collection load, make sure we're not entities we shouldn't see
    data = likeRestaurants.get();

    assertEquals("4peaks", getEntity(data, 0).get("name").asText());
    assertNull(getEntity(data, 1));

    // log in as user 2 and check it
    likeRestaurants = context.withUser(user2).application().users().user("me").connection("likes");

    data = likeRestaurants.entity(arrogantButcherId).get();
    assertNotNull(data);
    assertEquals("arrogantbutcher", getEntity(data, 0).get("name").asText());

    
    data = likeRestaurants.entity(peaksId).get();
    assertNull(data);

    // do a collection load, make sure we're not loading device 1
    data = likeRestaurants.get();

    assertEquals("arrogantbutcher", getEntity(data, 0).get("name").asText());
    assertNull(getEntity(data, 1));

    // we should see both devices when loaded from the root application

    // test for user 1

    CustomCollection restaurants = context.withUser(user1).application().collection("restaurants");
    data = restaurants.entity("4peaks").get();

    assertNotNull(data);
    assertEquals("4peaks", getEntity(data, 0).get("name").asText());

    data = restaurants.entity("arrogantbutcher").get();

    assertNotNull(data);
    assertEquals("arrogantbutcher", getEntity(data, 0).get("name").asText());

    // test for user 2
    restaurants = context.withUser(user1).application().collection("restaurants");
    data = restaurants.entity("4peaks").get();

    assertNotNull(data);
    assertEquals("4peaks", getEntity(data, 0).get("name").asText());

    data = restaurants.entity("arrogantbutcher").get();

    assertNotNull(data);
    assertEquals("arrogantbutcher", getEntity(data, 0).get("name").asText());

  }


}
