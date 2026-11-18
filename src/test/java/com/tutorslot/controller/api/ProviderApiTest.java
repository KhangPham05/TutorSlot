package com.tutorslot.controller.api;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// The tutor side of the JSON API against the real services and PostgreSQL, with real tokens.
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ProviderApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void aTutorAddsASlotSeesItAndRemovesIt() throws Exception {
        String alice = ApiLogin.bearer(mockMvc, "alice.tutor@sjsu.edu");
        String dashboard = mockMvc.perform(get("/api/provider/dashboard").header("Authorization", alice))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Integer serviceId = JsonPath.read(dashboard, "$.subjects[0].serviceId");
        String start = LocalDateTime.now().plusDays(20).truncatedTo(ChronoUnit.HOURS).toString();

        String created = mockMvc.perform(post("/api/provider/slots").header("Authorization", alice)
                        .contentType(APPLICATION_JSON).content(slot(serviceId, start)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Integer slotId = JsonPath.read(created, "$.slotId");
        mockMvc.perform(get("/api/provider/dashboard").header("Authorization", alice))
                .andExpect(jsonPath("$.slots[*].slotId", hasItem(slotId)));

        // The same time again clashes with the slot just added.
        mockMvc.perform(post("/api/provider/slots").header("Authorization", alice)
                        .contentType(APPLICATION_JSON).content(slot(serviceId, start)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("You already have a slot at that time."));

        mockMvc.perform(post("/api/provider/slots/{id}/delete", slotId).header("Authorization", alice))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/provider/dashboard").header("Authorization", alice))
                .andExpect(jsonPath("$.slots[*].slotId", not(hasItem(slotId))));
    }

    @Test
    void thePastIsRefusedAndSoIsAnotherTutorsSlot() throws Exception {
        String alice = ApiLogin.bearer(mockMvc, "alice.tutor@sjsu.edu");
        String bob = ApiLogin.bearer(mockMvc, "bob.tutor@sjsu.edu");
        String alicesDashboard = mockMvc.perform(get("/api/provider/dashboard").header("Authorization", alice))
                .andReturn().getResponse().getContentAsString();
        Integer serviceId = JsonPath.read(alicesDashboard, "$.subjects[0].serviceId");
        Integer alicesSlot = JsonPath.read(alicesDashboard, "$.slots[0].slotId");

        mockMvc.perform(post("/api/provider/slots").header("Authorization", alice)
                        .contentType(APPLICATION_JSON).content(slot(serviceId, LocalDateTime.now().minusDays(1).toString())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("The start time must be in the future."));
        mockMvc.perform(post("/api/provider/slots/{id}/delete", alicesSlot).header("Authorization", bob))
                .andExpect(status().isForbidden());
    }

    @Test
    void tutorEndpointsNeedATutorTokenAndTheReminderButtonReportsItsCount() throws Exception {
        mockMvc.perform(get("/api/provider/dashboard")
                        .header("Authorization", ApiLogin.bearer(mockMvc, "dan.student@sjsu.edu")))
                .andExpect(status().isForbidden());

        String alice = ApiLogin.bearer(mockMvc, "alice.tutor@sjsu.edu");
        mockMvc.perform(get("/api/provider/appointments").header("Authorization", alice))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.upcoming").isArray());
        mockMvc.perform(post("/api/provider/reminders/run").header("Authorization", alice))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sent").isNumber());
    }

    private static String slot(Integer serviceId, String startDateTime) {
        return "{\"serviceId\":" + serviceId + ",\"startDateTime\":\"" + startDateTime + "\"}";
    }
}
