package io.aetheris.users;

import io.aetheris.users.dto.CreateUserRequest;
import io.aetheris.users.dto.UserResponse;
import io.aetheris.users.events.UserEventPublisher;
import io.aetheris.users.exception.UserNotFoundException;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class UserService {
    private final UserRepository repository;
    private final UserEventPublisher eventPublisher;

    public UserService(UserRepository repository, UserEventPublisher eventPublisher) {
        this.repository = repository;
        this.eventPublisher = eventPublisher;
    }

    @Cacheable(cacheNames = "usersList", key = "'all'")
    public List<UserResponse> findAll() {
        return repository.findAll().stream()
                .map(UserService::toResponse)
                .collect(Collectors.toCollection(ArrayList::new));
    }

    @Cacheable(cacheNames = "userById", key = "#id")
    public UserResponse findById(Long id) {
        return repository.findById(id).map(UserService::toResponse)
                .orElseThrow(() -> new UserNotFoundException(id));
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(cacheNames = "usersList", allEntries = true),
            @CacheEvict(cacheNames = "userById", allEntries = true)
    })
    public UserResponse create(CreateUserRequest request) {
        String email = request.email().trim().toLowerCase();
        if (repository.existsByEmail(email)) {
            throw new IllegalArgumentException("Email already exists");
        }
        User saved = repository.save(new User(request.name().trim(), email));
        eventPublisher.userCreated(saved.getId(), saved.getName(), saved.getEmail());
        return toResponse(saved);
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(cacheNames = "usersList", allEntries = true),
            @CacheEvict(cacheNames = "userById", key = "#id")
    })
    public void delete(Long id) {
        User user = repository.findById(id).orElseThrow(() -> new UserNotFoundException(id));
        repository.delete(user);
        eventPublisher.userDeleted(user.getId(), user.getName(), user.getEmail());
    }

    private static UserResponse toResponse(User user) {
        return new UserResponse(user.getId(), user.getName(), user.getEmail());
    }
}
