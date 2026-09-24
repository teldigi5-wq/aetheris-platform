package io.aetheris.users;

import io.aetheris.users.dto.CreateUserRequest;
import io.aetheris.users.events.UserEventPublisher;
import io.aetheris.users.exception.UserNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository repository;

    @Mock
    private UserEventPublisher eventPublisher;

    @InjectMocks
    private UserService service;

    @Test
    void createNormalizesInputPersistsNormalizedUserAndPublishesCreatedEvent() {
        when(repository.existsByEmail("poojana@example.com")).thenReturn(false);
        when(repository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.create(new CreateUserRequest("  Poojana Kaveesh  ", "  POOJANA@Example.COM  "));

        ArgumentCaptor<User> savedUser = ArgumentCaptor.forClass(User.class);
        verify(repository).existsByEmail("poojana@example.com");
        verify(repository).save(savedUser.capture());
        assertThat(savedUser.getValue().getName()).isEqualTo("Poojana Kaveesh");
        assertThat(savedUser.getValue().getEmail()).isEqualTo("poojana@example.com");
        assertThat(response.name()).isEqualTo("Poojana Kaveesh");
        assertThat(response.email()).isEqualTo("poojana@example.com");
        verify(eventPublisher).userCreated(null, "Poojana Kaveesh", "poojana@example.com");
    }

    @Test
    void duplicateEmailFailsBeforePersistenceOrEventPublication() {
        when(repository.existsByEmail("duplicate@example.com")).thenReturn(true);

        assertThatThrownBy(() -> service.create(
                new CreateUserRequest("Duplicate", "  DUPLICATE@example.com  ")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Email already exists");

        verify(repository).existsByEmail("duplicate@example.com");
        verify(repository, never()).save(any(User.class));
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void findByIdFailsClosedWhenUserDoesNotExist() {
        when(repository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById(404L))
                .isInstanceOf(UserNotFoundException.class)
                .hasMessage("User not found: 404");

        verify(repository).findById(404L);
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void deleteLoadsUserDeletesItAndPublishesDeletionAfterRepositoryMutation() {
        User user = new User("Poojana", "poojana@example.com");
        when(repository.findById(7L)).thenReturn(Optional.of(user));

        service.delete(7L);

        InOrder order = inOrder(repository, eventPublisher);
        order.verify(repository).findById(7L);
        order.verify(repository).delete(user);
        order.verify(eventPublisher).userDeleted(null, "Poojana", "poojana@example.com");
    }

    @Test
    void deleteMissingUserDoesNotPublishOrDeleteAnything() {
        when(repository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(99L))
                .isInstanceOf(UserNotFoundException.class)
                .hasMessage("User not found: 99");

        verify(repository).findById(99L);
        verify(repository, never()).delete(any(User.class));
        verifyNoInteractions(eventPublisher);
    }
}
