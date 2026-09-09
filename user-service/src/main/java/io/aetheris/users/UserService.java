package io.aetheris.users;

import io.aetheris.users.dto.CreateUserRequest;
import io.aetheris.users.dto.UserResponse;
import io.aetheris.users.exception.UserNotFoundException;
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

    public List<UserResponse> findAll() {
        return repository.findAll().stream().map(UserService::toResponse).toList();
    }

    public UserResponse findById(Long id) {
        return repository.findById(id).map(UserService::toResponse)
                .orElseThrow(() -> new UserNotFoundException(id));
    }

    @Transactional
    public UserResponse create(CreateUserRequest request) {
        String email = request.email().trim().toLowerCase();
        if (repository.existsByEmail(email)) {
            throw new IllegalArgumentException("Email already exists");
        }
        User saved = repository.save(new User(request.name().trim(), email));
        return toResponse(saved);
    }

    @Transactional
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
