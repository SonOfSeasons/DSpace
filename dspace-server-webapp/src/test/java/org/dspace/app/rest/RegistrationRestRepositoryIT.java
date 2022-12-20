/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.rest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import javax.servlet.http.HttpServletResponse;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;
import org.dspace.app.rest.matcher.RegistrationMatcher;
import org.dspace.app.rest.model.RegistrationRest;
import org.dspace.app.rest.repository.RegistrationRestRepository;
import org.dspace.app.rest.test.AbstractControllerIntegrationTest;
import org.dspace.eperson.CaptchaServiceImpl;
import org.dspace.builder.GroupBuilder;
import org.dspace.eperson.Group;
import org.dspace.eperson.InvalidReCaptchaException;
import org.dspace.eperson.RegistrationData;
import org.dspace.eperson.dao.RegistrationDataDAO;
import org.dspace.eperson.service.CaptchaService;
import org.dspace.services.ConfigurationService;
import org.hamcrest.Matchers;
import org.junit.Ignore;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class RegistrationRestRepositoryIT extends AbstractControllerIntegrationTest {

    @Autowired
    private CaptchaServiceImpl captchaService;
    @Autowired
    private RegistrationDataDAO registrationDataDAO;
    @Autowired
    private ConfigurationService configurationService;
    @Autowired
    private RegistrationRestRepository registrationRestRepository;

    @Test
    public void findByTokenTestExistingUserTest() throws Exception {
        String email = eperson.getEmail();
        createTokenForEmail(email);
        RegistrationData registrationData = registrationDataDAO.findByEmail(context, email);

        try {
            getClient().perform(get("/api/eperson/registrations/search/findByToken")
                                    .param("token", registrationData.getToken()))
                       .andExpect(status().isOk())
                       .andExpect(
                           jsonPath("$", Matchers.is(RegistrationMatcher.matchRegistration(email, eperson.getID()))));

            registrationDataDAO.delete(context, registrationData);

            email = "newUser@testnewuser.com";
            createTokenForEmail(email);
            registrationData = registrationDataDAO.findByEmail(context, email);

            getClient().perform(get("/api/eperson/registrations/search/findByToken")
                                    .param("token", registrationData.getToken()))
                       .andExpect(status().isOk())
                       .andExpect(
                           jsonPath("$", Matchers.is(RegistrationMatcher.matchRegistration(email, null))));
        } finally {
            registrationDataDAO.delete(context, registrationData);
        }


    }

    @Test
    public void findByTokenTestNewUserTest() throws Exception {
        String email = "newUser@testnewuser.com";
        createTokenForEmail(email);
        RegistrationData registrationData = registrationDataDAO.findByEmail(context, email);

        try {
            getClient().perform(get("/api/eperson/registrations/search/findByToken")
                                    .param("token", registrationData.getToken()))
                       .andExpect(status().isOk())
                       .andExpect(
                           jsonPath("$", Matchers.is(RegistrationMatcher.matchRegistration(email, null))));
        } finally {
            registrationDataDAO.delete(context, registrationData);
        }

    }

    @Test
    public void findByTokenNotExistingTokenTest() throws Exception {
        getClient().perform(get("/api/eperson/registration/search/findByToken")
                                .param("token", "ThisTokenDoesNotExist"))
                   .andExpect(status().isNotFound());
    }

    private void createTokenForEmail(String email) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        RegistrationRest registrationRest = new RegistrationRest();
        registrationRest.setEmail(email);
        getClient().perform(post("/api/eperson/registrations")
                                .content(mapper.writeValueAsBytes(registrationRest))
                                .contentType(contentType))
                   .andExpect(status().isCreated());
    }
    private void createTokenWithGroupsForEmail(String email) throws Exception {
        List<RegistrationData> registrationDatas;
        ObjectMapper mapper = new ObjectMapper();
        RegistrationRest registrationRest = new RegistrationRest();
        registrationRest.setEmail(email);
        context.turnOffAuthorisationSystem();
        Group firstGroup = GroupBuilder.createGroup(context).withName("firstGroup").build();
        List<UUID> groupList = new ArrayList<>();
        groupList.add(firstGroup.getID());
        registrationRest.setGroups(groupList);
        context.restoreAuthSystemState();
        String token = getAuthToken(admin.getEmail(), password);
        getClient(token).perform(post("/api/eperson/registrations")
                        .content(mapper.writeValueAsBytes(registrationRest))
                        .contentType(contentType))
                .andExpect(status().isCreated());
    }
    @Test
    public void registrationFlowTest() throws Exception {
        List<RegistrationData> registrationDataList = registrationDataDAO.findAll(context, RegistrationData.class);
        assertEquals(0, registrationDataList.size());

        ObjectMapper mapper = new ObjectMapper();
        RegistrationRest registrationRest = new RegistrationRest();
        registrationRest.setEmail(eperson.getEmail());

        try {
            getClient().perform(post("/api/eperson/registrations")
                                    .content(mapper.writeValueAsBytes(registrationRest))
                                    .contentType(contentType))
                       .andExpect(status().isCreated());
            registrationDataList = registrationDataDAO.findAll(context, RegistrationData.class);
            assertEquals(1, registrationDataList.size());
            assertTrue(StringUtils.equalsIgnoreCase(registrationDataList.get(0).getEmail(), eperson.getEmail()));

            String newEmail = "newEPersonTest@gmail.com";
            registrationRest.setEmail(newEmail);
            getClient().perform(post("/api/eperson/registrations")
                                    .content(mapper.writeValueAsBytes(registrationRest))
                                    .contentType(contentType))
                       .andExpect(status().isCreated());
            registrationDataList = registrationDataDAO.findAll(context, RegistrationData.class);
            assertTrue(registrationDataList.size() == 2);
            assertTrue(StringUtils.equalsIgnoreCase(registrationDataList.get(0).getEmail(), newEmail) ||
                           StringUtils.equalsIgnoreCase(registrationDataList.get(1).getEmail(), newEmail));
            configurationService.setProperty("user.registration", false);

            newEmail = "newEPersonTestTwo@gmail.com";
            registrationRest.setEmail(newEmail);
            getClient().perform(post("/api/eperson/registrations")
                                    .content(mapper.writeValueAsBytes(registrationRest))
                                    .contentType(contentType))
                       .andExpect(status().is(HttpServletResponse.SC_UNAUTHORIZED));

            assertEquals(2, registrationDataList.size());
            assertTrue(!StringUtils.equalsIgnoreCase(registrationDataList.get(0).getEmail(), newEmail) &&
                           !StringUtils.equalsIgnoreCase(registrationDataList.get(1).getEmail(), newEmail));
        } finally {
            Iterator<RegistrationData> iterator = registrationDataList.iterator();
            while (iterator.hasNext()) {
                RegistrationData registrationData = iterator.next();
                registrationDataDAO.delete(context, registrationData);
            }
        }
    }

    @Test
    @Ignore
    public void forgotPasswordTest() throws Exception {
        configurationService.setProperty("user.registration", false);

        List<RegistrationData> registrationDataList = registrationDataDAO.findAll(context, RegistrationData.class);
        try {
            assertEquals(0, registrationDataList.size());

            ObjectMapper mapper = new ObjectMapper();
            RegistrationRest registrationRest = new RegistrationRest();
            registrationRest.setEmail(eperson.getEmail());
            getClient().perform(post("/api/eperson/registrations")
                                    .content(mapper.writeValueAsBytes(registrationRest))
                                    .contentType(contentType))
                       .andExpect(status().isCreated());
            registrationDataList = registrationDataDAO.findAll(context, RegistrationData.class);
            assertEquals(1, registrationDataList.size());
            assertTrue(StringUtils.equalsIgnoreCase(registrationDataList.get(0).getEmail(), eperson.getEmail()));
        } finally {
            Iterator<RegistrationData> iterator = registrationDataList.iterator();
            while (iterator.hasNext()) {
                RegistrationData registrationData = iterator.next();
                registrationDataDAO.delete(context, registrationData);
            }
        }
    }
    @Test
    public void findByTokenTestExistingUserWithGroupsTest() throws Exception {
        String email = eperson.getEmail();
        createTokenWithGroupsForEmail("albaTest@yahoo.com");
        RegistrationData registrationData = registrationDataDAO.findByEmail(context, "albaTest@yahoo.com");

        try {
            getClient().perform(get("/api/eperson/registrations/search/findByToken")
                            .param("token", registrationData.getToken()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.groupNames", Matchers.hasSize(1)))
                    .andExpect(jsonPath("$.email", Matchers.is("albaTest@yahoo.com")))
                    .andExpect(jsonPath("$.groups", Matchers.hasSize(1)))
                    .andExpect(jsonPath("$.groupNames[0]", Matchers.is("firstGroup")));
            registrationDataDAO.delete(context, registrationData);


            email = "newUser@testnewuser.com";
            createTokenForEmail(email);
            registrationData = registrationDataDAO.findByEmail(context, email);
            getClient().perform(get("/api/eperson/registrations/search/findByToken")
                            .param("token", registrationData.getToken()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", Matchers.is(RegistrationMatcher.matchRegistration(email, null))));

        } finally {
            registrationDataDAO.delete(context, registrationData);
        }
    }


    @Test
    public void registrationFlowWithNoHeaderCaptchaTokenTest() throws Exception {
        String originVerification = configurationService.getProperty("registration.verification.enabled");
        String originSecret = configurationService.getProperty("google.recaptcha.key.secret");
        String originVresion = configurationService.getProperty("google.recaptcha.version");
        reloadCaptchaProperties("true", "test-secret", "v2");

        ObjectMapper mapper = new ObjectMapper();
        RegistrationRest registrationRest = new RegistrationRest();
        registrationRest.setEmail(eperson.getEmail());

        // when reCAPTCHA enabled and request doesn't contain "X-Recaptcha-Token” header
        getClient().perform(post("/api/eperson/registrations")
                   .content(mapper.writeValueAsBytes(registrationRest))
                   .contentType(contentType))
                   .andExpect(status().isForbidden());

        reloadCaptchaProperties(originVerification, originSecret, originVresion);
    }

    @Test
    public void registrationFlowWithInvalidCaptchaTokenTest() throws Exception {
        String originVerification = configurationService.getProperty("registration.verification.enabled");
        String originSecret = configurationService.getProperty("google.recaptcha.key.secret");
        String originVresion = configurationService.getProperty("google.recaptcha.version");
        reloadCaptchaProperties("true", "test-secret", "v2");

        ObjectMapper mapper = new ObjectMapper();
        RegistrationRest registrationRest = new RegistrationRest();
        registrationRest.setEmail(eperson.getEmail());

        String captchaToken = "invalid-captcha-Token";
        // when reCAPTCHA enabled and request contains Invalid "X-Recaptcha-Token” header
        getClient().perform(post("/api/eperson/registrations")
                   .header("X-Recaptcha-Token", captchaToken)
                   .content(mapper.writeValueAsBytes(registrationRest))
                   .contentType(contentType))
                   .andExpect(status().isForbidden());

        reloadCaptchaProperties(originVerification, originSecret, originVresion);
    }

    @Test
    public void registrationFlowWithValidCaptchaTokenTest() throws Exception {
        String originVerification = configurationService.getProperty("registration.verification.enabled");
        String originSecret = configurationService.getProperty("google.recaptcha.key.secret");
        String originVresion = configurationService.getProperty("google.recaptcha.version");
        reloadCaptchaProperties("true", "test-secret", "v2");

        String captchaToken = "123456";
        String captchaToken1 = "12345676866";

        CaptchaService captchaServiceMock = mock(CaptchaService.class);

        registrationRestRepository.setCaptchaService(captchaServiceMock);

        doThrow(new InvalidReCaptchaException("Invalid captcha token"))
            .when(captchaServiceMock).processResponse(any(), any());

        doNothing().when(captchaServiceMock).processResponse(eq(captchaToken), eq("register_email"));

        List<RegistrationData> registrationDataList = registrationDataDAO.findAll(context, RegistrationData.class);
        assertEquals(0, registrationDataList.size());

        ObjectMapper mapper = new ObjectMapper();
        RegistrationRest registrationRest = new RegistrationRest();
        registrationRest.setEmail(eperson.getEmail());
        try {
            // will throw InvalidReCaptchaException because 'X-Recaptcha-Token' not equal captchaToken
            getClient().perform(post("/api/eperson/registrations")
                       .header("X-Recaptcha-Token", captchaToken1)
                       .content(mapper.writeValueAsBytes(registrationRest))
                       .contentType(contentType))
                       .andExpect(status().isForbidden());

            getClient().perform(post("/api/eperson/registrations")
                       .header("X-Recaptcha-Token", captchaToken)
                       .content(mapper.writeValueAsBytes(registrationRest))
                       .contentType(contentType))
                       .andExpect(status().isCreated());

            registrationDataList = registrationDataDAO.findAll(context, RegistrationData.class);
            assertEquals(1, registrationDataList.size());
            assertTrue(StringUtils.equalsIgnoreCase(registrationDataList.get(0).getEmail(), eperson.getEmail()));

            String newEmail = "newEPersonTest@gmail.com";
            registrationRest.setEmail(newEmail);
            getClient().perform(post("/api/eperson/registrations")
                       .header("X-Recaptcha-Token", captchaToken)
                       .content(mapper.writeValueAsBytes(registrationRest))
                       .contentType(contentType))
                       .andExpect(status().isCreated());

            registrationDataList = registrationDataDAO.findAll(context, RegistrationData.class);
            assertTrue(registrationDataList.size() == 2);
            assertTrue(StringUtils.equalsIgnoreCase(registrationDataList.get(0).getEmail(), newEmail) ||
                       StringUtils.equalsIgnoreCase(registrationDataList.get(1).getEmail(), newEmail));

            configurationService.setProperty("user.registration", false);

            newEmail = "newEPersonTestTwo@gmail.com";
            registrationRest.setEmail(newEmail);
            getClient().perform(post("/api/eperson/registrations")
                       .header("X-Recaptcha-Token", captchaToken)
                       .content(mapper.writeValueAsBytes(registrationRest))
                       .contentType(contentType))
                       .andExpect(status().is(HttpServletResponse.SC_UNAUTHORIZED));

            assertEquals(2, registrationDataList.size());
            assertTrue(!StringUtils.equalsIgnoreCase(registrationDataList.get(0).getEmail(), newEmail) &&
                       !StringUtils.equalsIgnoreCase(registrationDataList.get(1).getEmail(), newEmail));
        } finally {
            registrationRestRepository.setCaptchaService(captchaService);
            Iterator<RegistrationData> iterator = registrationDataList.iterator();
            while (iterator.hasNext()) {
                RegistrationData registrationData = iterator.next();
                registrationDataDAO.delete(context, registrationData);
            }
            reloadCaptchaProperties(originVerification, originSecret, originVresion);
        }
    }

    private void reloadCaptchaProperties(String verification, String secret, String version) {
        configurationService.setProperty("registration.verification.enabled", verification);
        configurationService.setProperty("google.recaptcha.key.secret", secret);
        configurationService.setProperty("google.recaptcha.version", version);
        captchaService.init();
    }

}
