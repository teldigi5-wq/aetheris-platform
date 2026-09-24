package io.aetheris.users;

import io.aetheris.users.dto.CreateUserRequest;
import io.aetheris.users.dto.UserResponse;
import io.aetheris.users.exception.GlobalExceptionHandler;
import io.aetheris.users.exception.UserNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class UserControllerTest {

    private UserService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(UserService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new UserController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void validCreateReturnsCreatedResponseAndDelegatesValidatedPayload() throws Exception {
        when(service.create(any(CreateUserRequest.class)))
                .thenReturn(new UserResponse(42L, "Poojana", "poojana@example.com"));

        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Poojana\",\"email\":\"poojana@example.com\"}"))
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(42))
                .andExpect(jsonPath("$.name").value("Poojana"))
                .andExpect(jsonPath("$.email").value("poojana@example.com"));

        verify(service).create(new CreateUserRequest("Poojana", "poojana@example.com"));
    }

    @Test
    void invalidCreateReturnsStructuredBadRequestWithoutCallingService() throws Exception {
        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"email\":\"not-an-email\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.path").value("/api/users"))
                .andExpect(jsonPath("$.fields.name").exists())
                .andExpect(jsonPath("$.fields.email").exists());

        verifyNoInteractions(service);
    }

    @Test
    void missingUserIsMappedToStructuredNotFoundResponse() throws Exception {
        when(service.findById(404L)).thenThrow(new UserNotFoundException(404L));

        mockMvc.perform(get("/api/users/404"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("User not found: 404"))
                .andExpect(jsonPath("$.path").value("/api/users/404"));
    }

    @Test
    void deleteReturnsNoContentAndDelegatesExactIdentity() throws Exception {
        mockMvc.perform(delete("/api/users/7"))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        verify(service).delete(7L);
    }
}
