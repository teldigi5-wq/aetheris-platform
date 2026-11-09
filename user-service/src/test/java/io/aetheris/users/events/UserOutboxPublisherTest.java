package io.aetheris.users.events;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import java.util.UUID;

import static org.mockito.Mockito.*;

class UserOutboxPublisherTest {
    @Test
    void enabledModeRecordsEventButNeverPublishesInsideMutation() {
        RabbitTemplate rabbit = mock(RabbitTemplate.class);
        UserOutboxStore outbox = mock(UserOutboxStore.class);
        var publisher = new UserEventPublisher(rabbit, outbox, true);
        UUID tenant = UUID.fromString("e398c9b9-23ea-473a-8240-8c42a92f7a34");

        publisher.userCreated(101L, "Synthetic", "test@example.test", tenant);
        publisher.userDeleted(101L, "Synthetic", "test@example.test", tenant);
        publisher.userCreated(102L, "Legacy", "legacy@example.test");

        verify(outbox).append("user.created",101L,"Synthetic","test@example.test",tenant);
        verify(outbox).append("user.deleted",101L,"Synthetic","test@example.test",tenant);
        verify(outbox).append("user.created",102L,"Legacy","legacy@example.test",null);
        verifyNoInteractions(rabbit);
    }

    @Test
    void storeFailureNeverFallsBackToDirectBrokerPublish() {
        RabbitTemplate rabbit = mock(RabbitTemplate.class);
        UserOutboxStore outbox = mock(UserOutboxStore.class);
        doThrow(new IllegalStateException("simulated missing writable transaction"))
                .when(outbox).append(anyString(), anyLong(), anyString(), anyString(), any());
        var publisher = new UserEventPublisher(rabbit, outbox, true);
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                () -> publisher.userCreated(7L,"Synthetic","synthetic@example.test"));
        verifyNoInteractions(rabbit);
    }
}
