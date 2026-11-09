package io.aetheris.users;

import io.aetheris.users.dto.CreateUserRequest;
import io.aetheris.users.events.UserEventPublisher;
import io.aetheris.users.exception.UserNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@ExtendWith(MockitoExtension.class)
class UserTenantRepositoryBoundaryTest {
    private static final UUID TENANT_A = UUID.fromString("e398c9b9-23ea-473a-8240-8c42a92f7a34");
    private static final UUID TENANT_B = UUID.fromString("75eb1c4c-954a-4071-95fa-6f7bef3f5ae1");
    @Mock UserRepository repository;
    @Mock UserEventPublisher publisher;

    @Test
    void readByIdAlwaysIncludesTenantPredicate() {
        var service = new UserService(repository, publisher);
        User owned = new User("Owner", "owner@example.test", TENANT_A);
        when(repository.findByIdAndTenantId(91L, TENANT_A)).thenReturn(Optional.of(owned));
        assertEquals("owner@example.test", service.findByIdForTenant(91L, TENANT_A).email());
        verify(repository).findByIdAndTenantId(91L, TENANT_A);
        verify(repository, never()).findById(anyLong());
    }

    @Test
    void otherTenantCannotReadOrDeleteKnownObjectId() {
        var service = new UserService(repository, publisher);
        when(repository.findByIdAndTenantId(91L, TENANT_B)).thenReturn(Optional.empty());
        assertThrows(UserNotFoundException.class, () -> service.findByIdForTenant(91L, TENANT_B));
        assertThrows(UserNotFoundException.class, () -> service.deleteForTenant(91L, TENANT_B));
        verify(repository, times(2)).findByIdAndTenantId(91L, TENANT_B);
        verify(repository, never()).delete(any());
        verifyNoInteractions(publisher);
    }

    @Test
    void listUsesBoundTenantPredicateWithoutGlobalCachedFindAll() {
        var service = new UserService(repository, publisher);
        when(repository.findAllByTenantId(TENANT_B)).thenReturn(List.of());
        assertTrue(service.findAllForTenant(TENANT_B).isEmpty());
        verify(repository).findAllByTenantId(TENANT_B);
        verify(repository, never()).findAll();
    }

    @Test
    void creationPersistsVerifiedTenantNotCallerSuppliedOwnership() {
        var service = new UserService(repository, publisher);
        when(repository.existsByEmail("tenant@example.test")).thenReturn(false);
        when(repository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        service.createForTenant(new CreateUserRequest(" Tenant ", " TENANT@example.test "), TENANT_A);
        var captor = org.mockito.ArgumentCaptor.forClass(User.class);
        verify(repository).save(captor.capture());
        assertEquals(TENANT_A, captor.getValue().getTenantId());
        assertEquals("tenant@example.test", captor.getValue().getEmail());
    }

    @Test
    void nullTenantFailsBeforeAnyRepositoryCall() {
        var service = new UserService(repository, publisher);
        assertThrows(NullPointerException.class, () -> service.findAllForTenant(null));
        assertThrows(NullPointerException.class, () -> service.findByIdForTenant(11L, null));
        assertThrows(NullPointerException.class, () -> service.createForTenant(
                new CreateUserRequest("NotAllowed", "x@example.test"), null));
        assertThrows(NullPointerException.class, () -> service.deleteForTenant(11L, null));
        verifyNoInteractions(repository, publisher);
    }

    @Test
    void newOwnedRecordDoesNotRewriteLegacyGlobalRecord() {
        assertNull(new User("Legacy", "legacy@example.test").getTenantId());
        assertEquals(TENANT_A, new User("Scoped", "scoped@example.test", TENANT_A).getTenantId());
    }
}
