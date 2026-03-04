package com.monetka.service;

import com.monetka.model.enums.UserState;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stores user FSM states and temporary session data in-memory.
 * No Redis needed for MVP — state is lost on restart (acceptable).
 *
 * Future: replace with Redis for multi-instance deployments.
 */
@Service
public class UserStateService {

    /** telegramId → current FSM state */
    private final Map<Long, UserState> states = new ConcurrentHashMap<>();

    /** telegramId → key-value session data (for multi-step wizards) */
    private final Map<Long, Map<String, String>> sessionData = new ConcurrentHashMap<>();

    // ---- State ----

    public UserState getState(Long telegramId) {
        return states.getOrDefault(telegramId, UserState.IDLE);
    }

    public void setState(Long telegramId, UserState state) {
        states.put(telegramId, state);
    }

    public void reset(Long telegramId) {
        states.put(telegramId, UserState.IDLE);
        sessionData.remove(telegramId);
    }

    // ---- Session data ----

    public void putData(Long telegramId, String key, String value) {
        sessionData.computeIfAbsent(telegramId, id -> new ConcurrentHashMap<>())
                   .put(key, value);
    }

    public String getData(Long telegramId, String key) {
        Map<String, String> data = sessionData.get(telegramId);
        return data != null ? data.get(key) : null;
    }

    public void clearData(Long telegramId) {
        sessionData.remove(telegramId);
    }
}
