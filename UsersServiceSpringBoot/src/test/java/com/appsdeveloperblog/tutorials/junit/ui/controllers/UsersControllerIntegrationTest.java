package com.appsdeveloperblog.tutorials.junit.ui.controllers;

import com.appsdeveloperblog.tutorials.junit.security.SecurityConstants;
import com.appsdeveloperblog.tutorials.junit.ui.response.UserRest;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;


// Without any specific WebEnvironment the default is WebEnvironment.MOCK
// with this Spring App Context will only create
// the beans of the web layer and mock everything else!
// This means a mocked servlet environment!
// So we would need to use mockmvc in this case again!
// If you want use/start an embedded server on a specific/random
// port then you need a different webEnvironment.
//
// When working with a defined port you can
// use the port defined in the application.properties (server.port)
// or you can override the port via properties="server.port=8081".
// In case you want to override more than one property
// you have to use curly-brackets and separate the properties with a comma.
// Alternative you can use a different property file e. g. application-test.properties
// you can do this via the
// @TestPropertySource annotation. But it applies the following priority:
// application.properties < application-test.properties  < properties=...

//To avoid port number conflicts use WebEnvironment.RANDOM_PORT.
//The injected serverPort will be the property server.property
//defined in application.properties and this will be set to 0 (why?).
// To see the actually port
// you have to use the @LocalServerPort annotation!

/*@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT,
properties = {"server.port=8081", "hostname=192.168.0.2"})*/
//@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
//@TestPropertySource(locations = "/application-test.properties",
//properties = "server.port=8081")

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
// Since there is by default a test class per method
// this would mean that authorizationToken is null in
// testGetUsers_whenValidJWTProvided_returnsUsers.
// So we have to change this behavior to TestInstance per Class!
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class UsersControllerIntegrationTest {

    //inject property server.port
    @Value("${server.port}")
    private int serverPort;

    @LocalServerPort
    private int localServerPort;

    //Easier to use for user authentication in contrast to RestTemplate
    @Autowired
    private TestRestTemplate testRestTemplate;

    private String authorizationToken;

//    @Test
//    void contextLoads() {
//        System.out.println("server.port=" + serverPort);
//        System.out.println("local server port =" + localServerPort);
//    }

    @Test
    @DisplayName("User can be created")
    @Order(1)
    void testCreateUser_whenValidDetailsIsProvided_returnsUserDetails() throws JSONException {
        //given
        JSONObject userDetailsRequestJson = new JSONObject();
        userDetailsRequestJson.put("firstName", "TestFirstName");
        userDetailsRequestJson.put("lastName", "TestLastName");
        userDetailsRequestJson.put("email", "test@gmail.com");
        userDetailsRequestJson.put("password", "12345678");
        userDetailsRequestJson.put("repeatPassword", "12345678");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(Arrays.asList(MediaType.APPLICATION_JSON));

        HttpEntity<String> request = new HttpEntity<>(userDetailsRequestJson.toString(), headers);


        //when
        ResponseEntity<UserRest> createdUserDetailsEntity = testRestTemplate.postForEntity("/users", request,
                UserRest.class);

        UserRest createdUserDetails = createdUserDetailsEntity.getBody();

        //then
        assertEquals(HttpStatus.OK, createdUserDetailsEntity.getStatusCode());
        assertEquals(userDetailsRequestJson.getString("firstName"),
                createdUserDetails.getFirstName(),
                "Returned user's first name seems to be incorrect!"
        );
        assertEquals(userDetailsRequestJson.getString("lastName"),
                createdUserDetails.getLastName(),
                "Returned user's last name seems to be incorrect!"
        );
        assertEquals(userDetailsRequestJson.getString("email"),
                createdUserDetails.getEmail(),
                "Returned user's email seems to be incorrect!"
        );
        assertEquals(userDetailsRequestJson.getString("email"),
                createdUserDetails.getEmail(),
                "Returned user's email seems to be incorrect!"
        );
        assertFalse(createdUserDetails.getUserId().trim().isEmpty(),
                "User id should not be empty!");

    }

    @Test
    @DisplayName("GET /users requires JWT")
    @Order(2)
    void testGetUsers_whenMissingJWT_returns403() {
        //given
        HttpHeaders headers = new HttpHeaders();
        headers.set("Accept", "application/json");

        HttpEntity<Object> requestEntity = new HttpEntity<>(null, headers);


        //when
        //exchange used to send a get request and get back a list of objects
        //TODO: why exchange method???
        ResponseEntity<List<UserRest>> response = testRestTemplate.exchange("/users",
                HttpMethod.GET,
                requestEntity,
                new ParameterizedTypeReference<List<UserRest>>() {
                });

        //then
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode(), "HTTP Status code 403" +
                "should have been returned!");
    }

    @Test
    @DisplayName("/login works")
    @Order(3)
    void testUserLogin_whenValidCredentialsProvided_returnsJWTinAuthorizationHeader() throws JSONException {
        //given
        JSONObject loginCredentials = new JSONObject();
        loginCredentials.put("email", "test@gmail.com");
        loginCredentials.put("password", "12345678");

        HttpEntity<String> request = new HttpEntity<>(loginCredentials.toString());

        //when
        ResponseEntity<Object> response = testRestTemplate.postForEntity("/users/login", request, null);

        authorizationToken = response.getHeaders().getValuesAsList(SecurityConstants.HEADER_STRING).get(0);
        //then
        assertEquals(HttpStatus.OK, response.getStatusCode(), "http status code should be 200");
        assertNotNull(authorizationToken,
                "Response should contain Authorization header with JWT");
        assertNotNull(response.getHeaders().getValuesAsList("UserID").get(0),
                "Response should contain user id in a response header");
    }

    @Test
    @DisplayName("GET /users works")
    @Order(4)
    void testGetUsers_whenValidJWTProvided_returnsUsers() {
        //given
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(Arrays.asList(MediaType.APPLICATION_JSON));
        headers.setBearerAuth(authorizationToken);

        HttpEntity<Object> requestEntity = new HttpEntity<>(headers);

        //when
        ResponseEntity<List<UserRest>> response = testRestTemplate.exchange("/users",
                HttpMethod.GET,
                requestEntity,
                new ParameterizedTypeReference<List<UserRest>>() {
                });

        //then
        assertEquals(HttpStatus.OK, response.getStatusCode(), "HTTP Status code should be 200");
        assertTrue(response.getBody().size() == 1, "There should be exactly one user in the list");
    }
}
