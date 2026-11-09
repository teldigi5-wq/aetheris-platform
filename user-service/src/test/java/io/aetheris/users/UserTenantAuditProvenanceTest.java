package io.aetheris.users;

import io.aetheris.users.dto.CreateUserRequest;
import io.aetheris.users.events.UserEventPublisher;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class UserTenantAuditProvenanceTest {
    private static final UUID TENANT =
            UUID.fromString("e398c9b9-23ea-473a-8240-8c42a92f7a34");

    @Test
    void scopedCreatePublishesTenantFromPersistedUserNotClient() {
        UserRepository db=mock(UserRepository.class);
        UserEventPublisher bus=mock(UserEventPublisher.class);
        when(db.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        var service=new UserService(db,bus);
        service.createForTenant(new CreateUserRequest("Synthetic","synthetic-owned@example.test"),TENANT);
        verify(db).save(argThat(u -> TENANT.equals(u.getTenantId())));
        verify(bus).userCreated(null,"Synthetic","synthetic-owned@example.test",TENANT);
        verify(bus,never()).userCreated(any(),any(),any());
    }

    @Test
    void scopedDeleteRequiresMatchingStoredTenantBeforePublishing() {
        UserRepository db=mock(UserRepository.class);
        UserEventPublisher bus=mock(UserEventPublisher.class);
        var owned=new User("Synthetic","synthetic-owned@example.test",TENANT);
        when(db.findByIdAndTenantId(7L,TENANT)).thenReturn(Optional.of(owned));
        var service=new UserService(db,bus);
        service.deleteForTenant(7L,TENANT);
        verify(db).delete(owned);
        verify(bus).userDeleted(null,"Synthetic","synthetic-owned@example.test",TENANT);
        verify(bus,never()).userDeleted(any(),any(),any());
    }
}
