package com.polybezev.currencybot.service;

import com.polybezev.currencybot.model.UserConversationData;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory store for per-user FSM conversation state.
 * <p>
 * State is keyed by Telegram chat ID and held in a {@link ConcurrentHashMap} —
 * thread-safe for concurrent updates from the bot's update dispatcher.
 * <p>
 * Data is lost on application restart (intentional — FSM state is transient).
 * There is no TTL or eviction; for the current user scale this is acceptable.
 * If the user base grows large, consider a bounded cache with eviction.
 */
@Service
public class UserStateService {

    private final Map<Long, UserConversationData> session = new ConcurrentHashMap<>();

    /**
     * Returns the existing conversation state for {@code chatId},
     * creating a fresh default-state entry if none exists.
     *
     * @param chatId Telegram chat ID
     * @return existing or newly created {@link UserConversationData}; never {@code null}
     */
    public UserConversationData getOrCreate(long chatId) {
        return session.computeIfAbsent(chatId, id -> new UserConversationData());
    }

    /**
     * Replaces the conversation state for {@code chatId} with a fresh default instance.
     * Used to abort any active FSM flow and return the user to a clean state.
     *
     * @param chatId Telegram chat ID
     * @return the newly created {@link UserConversationData}
     */
    public UserConversationData reset(long chatId) {
        UserConversationData fresh = new UserConversationData();
        session.put(chatId, fresh);
        return fresh;
    }
}
