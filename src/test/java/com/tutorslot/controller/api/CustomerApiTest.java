package com.tutorslot.controller.api;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// The student side of the JSON API against the real services and PostgreSQL, with real tokens:
// browse, book, list, cancel, the guards around them, and the assistant (switched off, as in CI).
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class CustomerApiTest {

    private static final String DAN = "dan.student@sjsu.edu";
    private static final String ERIN = "erin.student@sjsu.edu";
    private static final String ALICE_TUTOR = "alice.tutor@sjsu.edu";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void anyoneCanBrowseOpenSlotsWithTheFilterOptions() throws Exception {
        Integer slotId = openSlotId(0);

        mockMvc.perform(get("/api/slots"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tutors").isNotEmpty())
                .andExpect(jsonPath("$.subjects").isNotEmpty());
        mockMvc.perform(get("/api/slots/{id}", slotId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slotId").value(slotId))
                .andExpect(jsonPath("$.tutorName").isNotEmpty());
    }

    @Test
    void aStudentBooksSeesTheBookingAndCancelsIt() throws Exception {
        String dan = ApiLogin.bearer(mockMvc, DAN);
        Integer slotId = openSlotId(0);

        String booked = mockMvc.perform(post("/api/customer/bookings").header("Authorization", dan)
                        .contentType(APPLICATION_JSON).content("{\"slotId\":" + slotId + ",\"notes\":\"Chapter 3\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.notes").value("Chapter 3"))
                .andReturn().getResponse().getContentAsString();
        Integer appointmentId = JsonPath.read(booked, "$.appointmentId");

        // Booked: in Dan's upcoming list, and gone from the open slots.
        mockMvc.perform(get("/api/customer/appointments").header("Authorization", dan))
                .andExpect(jsonPath("$.upcoming[*].appointmentId", hasItem(appointmentId)));
        mockMvc.perform(get("/api/slots"))
                .andExpect(jsonPath("$.slots[*].slotId", not(hasItem(slotId))));

        mockMvc.perform(post("/api/customer/appointments/{id}/cancel", appointmentId).header("Authorization", dan))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/customer/appointments").header("Authorization", dan))
                .andExpect(jsonPath("$.upcoming[*].appointmentId", not(hasItem(appointmentId))))
                .andExpect(jsonPath("$.history[?(@.appointmentId == " + appointmentId + ")].status").value("CANCELLED"));
    }

    @Test
    void bookingASlotSomeoneElseJustTookIs409() throws Exception {
        Integer slotId = openSlotId(0);
        mockMvc.perform(post("/api/customer/bookings").header("Authorization", ApiLogin.bearer(mockMvc, ERIN))
                        .contentType(APPLICATION_JSON).content("{\"slotId\":" + slotId + "}"))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/customer/bookings").header("Authorization", ApiLogin.bearer(mockMvc, DAN))
                        .contentType(APPLICATION_JSON).content("{\"slotId\":" + slotId + "}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("This slot is already booked."));
    }

    @Test
    void aStudentCanNeitherSeeNorCancelAnotherStudentsAppointment() throws Exception {
        String erinsList = mockMvc.perform(get("/api/customer/appointments")
                        .header("Authorization", ApiLogin.bearer(mockMvc, ERIN)))
                .andReturn().getResponse().getContentAsString();
        Integer erinsAppointment = JsonPath.read(erinsList, "$.upcoming[0].appointmentId");
        String dan = ApiLogin.bearer(mockMvc, DAN);

        mockMvc.perform(get("/api/customer/appointments/{id}", erinsAppointment).header("Authorization", dan))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/customer/appointments/{id}/cancel", erinsAppointment).header("Authorization", dan))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("You can't do that."));
    }

    @Test
    void studentEndpointsNeedAStudentToken() throws Exception {
        mockMvc.perform(get("/api/customer/appointments"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/customer/bookings").contentType(APPLICATION_JSON).content("{\"slotId\":1}"))
                .andExpect(status().isUnauthorized());
        // A tutor's valid token is still the wrong role.
        mockMvc.perform(get("/api/customer/appointments").header("Authorization", ApiLogin.bearer(mockMvc, ALICE_TUTOR)))
                .andExpect(status().isForbidden());
    }

    @Test
    void theAssistantSaysItIsOffWithoutAKeyAndRejectsABlankMessage() throws Exception {
        String dan = ApiLogin.bearer(mockMvc, DAN);

        mockMvc.perform(get("/api/customer/assistant").header("Authorization", dan))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false));
        mockMvc.perform(post("/api/customer/assistant").header("Authorization", dan)
                        .contentType(APPLICATION_JSON).content("{\"message\":\"Book me a slot\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reply").value("The assistant is not configured."));
        mockMvc.perform(post("/api/customer/assistant").header("Authorization", dan)
                        .contentType(APPLICATION_JSON).content("{\"message\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Type a message."));
        mockMvc.perform(post("/api/customer/assistant/reset").header("Authorization", dan))
                .andExpect(status().isNoContent());
    }

    // The index-th open slot right now, earliest first (booked slots drop out of the list).
    private Integer openSlotId(int index) throws Exception {
        String body = mockMvc.perform(get("/api/slots")).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.slots[" + index + "].slotId");
    }
}
