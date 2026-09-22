package io.aetheris.users;

import io.aetheris.users.dto.CreateUserRequest;
import io.aetheris.users.events.UserEventPublisher;
import org.junit.jupiter.api.Test;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class UserServiceCacheTest {

    @Test
    void listReadsUseCacheAndCreateEvictsCachedList() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(TestConfig.class)) {
            UserRepository repository = context.getBean(UserRepository.class);
            UserService service = context.getBean(UserService.class);
            User user = new User("Poojana", "poojana@example.com");
            when(repository.findAll()).thenReturn(List.of(user));
            when(repository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

            service.findAll();
            service.findAll();
            verify(repository, times(1)).findAll();

            service.create(new CreateUserRequest("New User", "NEW@EXAMPLE.COM"));
            service.findAll();

            verify(repository, times(2)).findAll();
            verify(repository).existsByEmail("new@example.com");
        }
    }

    @Test
    void idReadsUseCacheAndDeleteEvictsOnlyDeletedIdentity() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(TestConfig.class)) {
            UserRepository repository = context.getBean(UserRepository.class);
            UserService service = context.getBean(UserService.class);
            User user = new User("Poojana", "poojana@example.com");
            when(repository.findById(7L)).thenReturn(Optional.of(user));

            service.findById(7L);
            service.findById(7L);
            verify(repository, times(1)).findById(7L);

            service.delete(7L);
            service.findById(7L);

            verify(repository, times(3)).findById(7L);
            verify(repository).delete(user);
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableCaching
    static class TestConfig {
        @Bean
        CacheManager cacheManager() {
            return new ConcurrentMapCacheManager("usersList", "userById");
        }

        @Bean
        UserRepository userRepository() {
            return mock(UserRepository.class);
        }

        @Bean
        UserEventPublisher userEventPublisher() {
            return mock(UserEventPublisher.class);
        }

        @Bean
        UserService userService(UserRepository repository, UserEventPublisher publisher) {
            return new UserService(repository, publisher);
        }
    }
}
