package dev.danvega.content_calendar.repository;
import dev.danvega.content_calendar.model.User;
import org.springframework.data.repository.ListCrudRepository;
import java.util.Optional;


public interface UserRepository extends ListCrudRepository<User, Integer> {

    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
}
