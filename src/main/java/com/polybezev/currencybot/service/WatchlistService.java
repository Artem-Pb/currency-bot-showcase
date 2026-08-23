package com.polybezev.currencybot.service;

import com.polybezev.currencybot.entity.User;
import com.polybezev.currencybot.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.TreeSet;

/**
 * Manages each user's coin watchlist (favourites) — the set of codes that drives the
 * personalised "Курсы" view and which of the user's assets AI news commentary is matched
 * against ({@code CommandHandler.resolveNewsAssets}).
 * <p>
 * Codes are never validated against a live feed here: callers only ever offer codes from
 * the fixed selection keyboard ({@link com.polybezev.currencybot.formatter.MessageFormatter#buildWatchlistKeyboard}),
 * so an unknown code can only reach this service via a stale/forged callback — toggling it
 * is harmless (it simply won't resolve to a CoinGecko price when the watchlist is rendered).
 */
@Service
@RequiredArgsConstructor
public class WatchlistService {

    private final UserRepository userRepository;

    /**
     * Returns the watchlist codes for the given chat ID, sorted alphabetically for stable
     * keyboard rendering.
     *
     * @param chatId Telegram chat ID
     * @return sorted watchlist codes, or an empty set if the user is not registered
     */
    public Set<String> get(Long chatId) {
        return userRepository.findByChatId(chatId)
                .map(u -> (Set<String>) new TreeSet<>(u.getWatchlist()))
                .orElseGet(TreeSet::new);
    }

    /**
     * Adds {@code code} to the user's watchlist if absent, removes it otherwise, and
     * persists the change immediately.
     *
     * @param chatId Telegram chat ID; must belong to an already-registered user
     * @param code   coin code to toggle (expected already uppercase)
     * @return the watchlist after the toggle, sorted alphabetically
     * @throws IllegalStateException if no user is registered under {@code chatId}
     */
    @Transactional
    public Set<String> toggle(Long chatId, String code) {
        User user = userRepository.findByChatId(chatId)
                .orElseThrow(() -> new IllegalStateException("Unknown user: " + chatId));
        if (!user.getWatchlist().remove(code)) {
            user.getWatchlist().add(code);
        }
        userRepository.save(user);
        return new TreeSet<>(user.getWatchlist());
    }
}
