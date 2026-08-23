package com.polybezev.currencybot.service;

import com.polybezev.currencybot.entity.User;
import com.polybezev.currencybot.entity.UserSubscription;
import com.polybezev.currencybot.model.PaymentProvider;
import com.polybezev.currencybot.model.Tier;
import com.polybezev.currencybot.repository.UserRepository;
import com.polybezev.currencybot.repository.UserSubscriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Core subscription lifecycle service: user registration, tier resolution,
 * payment activation, admin grants, and banning.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SubscriptionService {

    private final UserSubscriptionRepository userSubscriptionRepository;
    private final UserRepository userRepository;

    /** Returned by {@link #getOrCreateUser} to distinguish new registrations from returning users. */
    public record UserRegistration(User user, boolean isNew) {}

    /**
     * Finds an existing user by {@code chatId} or creates and persists a new one.
     *
     * @param chatId    Telegram chat ID
     * @param username  Telegram username (may be {@code null})
     * @param firstName first name from Telegram (falls back to {@code "User"} if {@code null})
     * @return registration result with the user entity and an {@code isNew} flag
     */
    public UserRegistration getOrCreateUser(Long chatId, String username, String firstName) {
        Optional<User> existing = userRepository.findByChatId(chatId);
        if (existing.isPresent()) return new UserRegistration(existing.get(), false);

        User user = new User();
        user.setChatId(chatId);
        user.setUsername(username);
        user.setFirstName(firstName != null ? firstName : "User");
        user.setCreatedAt(LocalDateTime.now());
        return new UserRegistration(userRepository.save(user), true);
    }

    /**
     * Returns the active {@link Tier} for the given chat ID.
     * <p>
     * Returns {@link Tier#FREE} if the user has no subscription, the subscription is inactive,
     * or the subscription has expired.
     *
     * @param chatId Telegram chat ID
     * @return active tier, never {@code null}
     */
    public Tier getActiveTier(Long chatId) {
        return userRepository.findByChatId(chatId)
                .flatMap(userSubscriptionRepository::findByUserAndActiveTrue)
                .filter(sub -> sub.getExpiresAt().isAfter(LocalDateTime.now()))
                .map(UserSubscription::getTier)
                .orElse(Tier.FREE);
    }

    /**
     * Returns {@code true} if the user's active tier satisfies {@code required}.
     * <p>
     * Delegates to {@link Tier#hasAccess(Tier)} to avoid scattered ordinal comparisons.
     *
     * @param chatId   Telegram chat ID
     * @param required minimum tier needed
     * @return {@code true} if the user's tier is equal to or higher than {@code required}
     */
    public boolean hasAccess(Long chatId, Tier required) {
        return getActiveTier(chatId).hasAccess(required);
    }

    /**
     * Activates a paid subscription after a successful Telegram Stars payment.
     * <p>
     * Any existing active subscription is deactivated first.
     * Provider is always {@link PaymentProvider#STARS}.
     *
     * @param chatId      Telegram chat ID of the paying user
     * @param tier        tier to activate
     * @param amountStars number of Telegram Stars charged
     */
    @Transactional
    public void activateSubscription(Long chatId, Tier tier, int amountStars) {
        User user = getOrCreateUser(chatId, null, "User").user();
        deactivateCurrent(user);

        UserSubscription sub = newSubscription(user, tier, PaymentProvider.STARS, BigDecimal.valueOf(amountStars));
        userSubscriptionRepository.save(sub);
        log.info("Stars subscription activated: chatId={} tier={} stars={}", chatId, tier, amountStars);
    }

    /**
     * Grants a subscription to a user from the admin panel without charging Stars.
     * <p>
     * Any existing active subscription is deactivated first.
     * Provider is {@link PaymentProvider#MANUAL} and {@code amountPaid} is zero.
     *
     * @param chatId Telegram chat ID of the recipient
     * @param tier   tier to grant
     */
    @Transactional
    public void grantSubscription(Long chatId, Tier tier) {
        User user = getOrCreateUser(chatId, null, "User").user();
        deactivateCurrent(user);

        UserSubscription sub = newSubscription(user, tier, PaymentProvider.MANUAL, BigDecimal.ZERO);
        userSubscriptionRepository.save(sub);
        log.info("Admin grant activated: chatId={} tier={}", chatId, tier);
    }

    /**
     * Bans a user by setting {@code banned = true} on their entity.
     * Banned users are blocked at the bot router level before any handler is invoked.
     *
     * @param chatId Telegram chat ID to ban
     */
    @Transactional
    public void banUser(Long chatId) {
        userRepository.findByChatId(chatId).ifPresent(u -> {
            u.setBanned(true);
            userRepository.save(u);
            log.info("User banned: chatId={}", chatId);
        });
    }

    /**
     * Removes the ban on a previously banned user.
     *
     * @param chatId Telegram chat ID to unban
     */
    @Transactional
    public void unbanUser(Long chatId) {
        userRepository.findByChatId(chatId).ifPresent(u -> {
            u.setBanned(false);
            userRepository.save(u);
            log.info("User unbanned: chatId={}", chatId);
        });
    }

    /**
     * Returns the current active, non-expired subscription for the user, if any.
     * Used by the LK balance screen to display subscription details.
     *
     * @param chatId Telegram chat ID
     * @return active subscription, or empty if none
     */
    public Optional<UserSubscription> getActiveSubscription(Long chatId) {
        return userRepository.findByChatId(chatId)
                .flatMap(userSubscriptionRepository::findByUserAndActiveTrue)
                .filter(sub -> sub.getExpiresAt().isAfter(LocalDateTime.now()));
    }

    // ==================== HELPERS ====================

    private void deactivateCurrent(User user) {
        userSubscriptionRepository.findByUserAndActiveTrue(user).ifPresent(sub -> {
            sub.setActive(false);
            userSubscriptionRepository.save(sub);
        });
    }

    private UserSubscription newSubscription(User user, Tier tier, PaymentProvider provider, BigDecimal amount) {
        UserSubscription sub = new UserSubscription();
        sub.setUser(user);
        sub.setTier(tier);
        sub.setPaymentProvider(provider);
        sub.setAmountPaid(amount);
        sub.setStartedAt(LocalDateTime.now());
        sub.setExpiresAt(LocalDateTime.now().plusDays(30));
        sub.setActive(true);
        return sub;
    }
}
