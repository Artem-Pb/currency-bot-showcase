package com.polybezev.currencybot.repository;

import com.polybezev.currencybot.entity.User;
import com.polybezev.currencybot.entity.UserExchangeCredentials;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for {@link UserExchangeCredentials} entities.
 */
@Repository
public interface UserExchangeCredentialsRepository extends JpaRepository<UserExchangeCredentials, Long> {

    /**
     * Finds the exchange credentials for a given user, if any have been saved.
     *
     * @param user the user whose credentials to look up
     * @return the credentials wrapped in {@link Optional}, or empty if the user has not connected an exchange
     */
    Optional<UserExchangeCredentials> findByUser(User user);

    /**
     * Returns all credentials where automated trading is enabled, with users eagerly loaded.
     * Used by {@code TradeScheduler} to build the list of users to trade on behalf of.
     * <p>
     * {@code join fetch c.user} avoids N+1 queries when the scheduler iterates the result list.
     *
     * @return list of active trading credentials with users populated
     */
    @Query("select c from UserExchangeCredentials c join fetch c.user where c.tradingEnabled = true")
    List<UserExchangeCredentials> findAllWithTradingEnabled();
}
