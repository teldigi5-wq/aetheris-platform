package io.aetheris.users;

import io.aetheris.users.dto.CreateUserRequest;
import io.aetheris.users.dto.UserResponse;
import io.aetheris.users.security.ServiceJwtAuthorizationFilter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/users")
@Tag(name = "Users", description = "Aetheris user-management API")
public class UserController {
    private final UserService service;

    public UserController(UserService service) {
        this.service = service;
    }

    // Explicit account-bounded tenant API: UUID is never provided by client.
    // Spring's RequestAttribute is written after independent JWT verification.
    @GetMapping("/tenant")
    public List<UserResponse> tenantAll(
            @RequestAttribute(name = ServiceJwtAuthorizationFilter.VERIFIED_TENANT_ATTRIBUTE) UUID tenantId) {
        return service.findAllForTenant(tenantId);
    }

    @GetMapping("/tenant/{id}")
    public UserResponse tenantOne(@PathVariable Long id,
            @RequestAttribute(name = ServiceJwtAuthorizationFilter.VERIFIED_TENANT_ATTRIBUTE) UUID tenantId) {
        return service.findByIdForTenant(id, tenantId);
    }

    @PostMapping("/tenant")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse tenantCreate(@Valid @RequestBody CreateUserRequest request,
            @RequestAttribute(name = ServiceJwtAuthorizationFilter.VERIFIED_TENANT_ATTRIBUTE) UUID tenantId) {
        return service.createForTenant(request, tenantId);
    }

    @DeleteMapping("/tenant/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void tenantDelete(@PathVariable Long id,
            @RequestAttribute(name = ServiceJwtAuthorizationFilter.VERIFIED_TENANT_ATTRIBUTE) UUID tenantId) {
        service.deleteForTenant(id, tenantId);
    }

    @GetMapping
    @Operation(summary = "List users")
    public List<UserResponse> all() {
        return service.findAll();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a user")
    public UserResponse one(@PathVariable Long id) {
        return service.findById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a user")
    public UserResponse create(@Valid @RequestBody CreateUserRequest request) {
        return service.create(request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a user")
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
