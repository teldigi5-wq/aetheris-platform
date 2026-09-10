package io.aetheris.users;

import io.aetheris.users.dto.CreateUserRequest;
import io.aetheris.users.dto.UserResponse;
import io.aetheris.users.exception.UserNotFoundException;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class UserService {
    private final UserRepository repository;

    public UserService(UserRepository repository) {
        this.repository = repository;
    }

    @Cacheable(cacheNames = "usersList", key = "'all'")
    public List<UserResponse> findAll() {
        return repository.findAll().stream().map(UserService::toResponse).toList();
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
        return toResponse(saved);
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(cacheNames = "usersList", allEntries = true),
            @CacheEvict(cacheNames = "userById", key = "#id")
    })
    public void delete(Long id) {
        if (!repository.existsById(id)) {
            throw new UserNotFoundException(id);
        }
        repository.deleteById(id);
    }

    private static UserResponse toResponse(User user) {
        return new UserResponse(user.getId(), user.getName(), user.getEmail());
    }
}
