package com.polybezev.currencybot.repository;

import com.polybezev.currencybot.entity.User;
import com.polybezev.currencybot.entity.UserSubscription;
import com.polybezev.currencybot.model.Tier;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for {@link UserSubscription} entities.
 */
@Repository
public interface UserSubscriptionRepository extends JpaRepository<UserSubscription, Long> {

    /**
     * Finds the currently active subscription for a user.
     * Returns empty if the user has no active subscription (e.g. FREE tier or expired).
     *
     * @param user the user whose subscription to look up
     * @return the active subscription wrapped in {@link Optional}, or empty if none
     */
    Optional<UserSubscription> findByUserAndActiveTrue(User user);

    /**
     * Returns all active paid subscriptions with their users eagerly loaded.
     * Used by {@code MorningDigestScheduler} to build the recipient list for the daily digest.
     * <p>
     * {@code join fetch s.user} avoids N+1 queries when the scheduler iterates the results.
     * The literal {@code 'FREE'} matches {@link Tier#FREE}{@code .name()} — if that enum constant
     * is ever renamed, this query must be updated to match.
     *
     * @return list of active non-free subscriptions with users populated
     */
    @Query("select s from UserSubscription s join fetch s.user where s.active = true and s.tier <> 'FREE'")
    List<UserSubscription> findAllActivePaid();

    /**
     * Returns all active TIER 2+ subscriptions with their users eagerly loaded.
     * Used by {@code SignalScheduler} to send change-based TA alerts only to eligible subscribers.
     * <p>
     * The tier literals must match {@link Tier#TIER_2}{@code .name()} and {@link Tier#TIER_3}{@code .name()}.
     *
     * @return list of active TIER 2 and TIER 3 subscriptions with users populated
     */
    @Query("select s from UserSubscription s join fetch s.user where s.active = true and s.tier in ('TIER_2', 'TIER_3')")
    List<UserSubscription> findAllActiveTier2Plus();
}
