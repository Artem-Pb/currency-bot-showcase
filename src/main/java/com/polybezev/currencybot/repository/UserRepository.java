package com.polybezev.currencybot.repository;

import com.polybezev.currencybot.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for {@link User} entities.
 * <p>
 * All derived query methods follow Spring Data naming conventions and are
 * implemented automatically — no SQL or JPQL required.
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * Finds a user by their Telegram chat ID.
     * Used on every incoming message to look up the sender.
     *
     * @param chatId Telegram chat ID (unique per user account)
     * @return the user wrapped in {@link Optional}, or empty if not registered
     */
    Optional<User> findByChatId(Long chatId);

    /**
     * Returns a paginated list of all users ordered by registration date, newest first.
     * Used by the admin panel user list with {@link org.springframework.data.domain.PageRequest}.
     *
     * @param pageable page number and size
     * @return one page of users
     */
    Page<User> findAllByOrderByCreatedAtDesc(Pageable pageable);

    /**
     * Counts users by ban status.
     * Used to show the ban count before fetching the full list.
     *
     * @param banned {@code true} to count banned users, {@code false} for active users
     * @return number of matching users
     */
    long countByBanned(boolean banned);

    /**
     * Returns all users with the given ban status (unbounded).
     * Used by the admin panel banned-users list. Acceptable for the current scale;
     * add a {@link Pageable} overload if the ban list grows large.
     *
     * @param banned {@code true} to fetch banned users, {@code false} for active users
     * @return list of matching users
     */
    List<User> findByBanned(boolean banned);
}
