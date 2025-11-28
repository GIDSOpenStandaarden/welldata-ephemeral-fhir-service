package nl.gidsopenstandaarden.welldata.fhir.service;

import nl.gidsopenstandaarden.welldata.fhir.context.AccessTokenContext;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Manages per-session (per-access-token) resource stores.
 *
 * Each session has its own isolated set of FHIR resources.
 * Sessions are identified by the JWT token ID (jti) or a hash of the token.
 */
@Service
public class SessionManager {

    private static final Logger LOG = LoggerFactory.getLogger(SessionManager.class);

    private final Map<String, Session> sessions = new ConcurrentHashMap<>();

    /**
     * Get or create a session for the given session key.
     */
    public Session getOrCreateSession(String sessionKey) {
        return sessions.computeIfAbsent(sessionKey, key -> {
            LOG.info("Creating new session: {}", key);
            return new Session(key);
        });
    }

    /**
     * Get the session for the current request context.
     * Returns null if no access token context is set.
     */
    public Session getCurrentSession() {
        AccessTokenContext context = AccessTokenContext.get();
        if (context == null) {
            return null;
        }
        return sessions.get(context.getSessionKey());
    }

    /**
     * Get a session by its key.
     */
    public Session getSession(String sessionKey) {
        return sessions.get(sessionKey);
    }

    /**
     * Remove a session.
     */
    public void removeSession(String sessionKey) {
        Session removed = sessions.remove(sessionKey);
        if (removed != null) {
            LOG.info("Removed session: {}", sessionKey);
        }
    }

    /**
     * Get all active session keys.
     */
    public Set<String> getActiveSessionKeys() {
        return new HashSet<>(sessions.keySet());
    }

    /**
     * Cleanup expired sessions (runs every 5 minutes).
     */
    @Scheduled(fixedRate = 300000)
    public void cleanupExpiredSessions() {
        Instant now = Instant.now();
        List<String> expiredKeys = new ArrayList<>();

        for (Map.Entry<String, Session> entry : sessions.entrySet()) {
            if (entry.getValue().isExpired(now)) {
                expiredKeys.add(entry.getKey());
            }
        }

        for (String key : expiredKeys) {
            removeSession(key);
            LOG.info("Cleaned up expired session: {}", key);
        }

        if (!expiredKeys.isEmpty()) {
            LOG.info("Cleaned up {} expired sessions", expiredKeys.size());
        }
    }

    /**
     * Represents a user session with isolated resource storage.
     */
    public static class Session {
        private final String sessionKey;
        private final Instant createdAt;
        private Instant expiry;
        private boolean dataLoaded = false;

        // Per-resource-type storage: ResourceType -> (ID -> (Version -> Resource))
        private final Map<String, Map<String, TreeMap<Long, IBaseResource>>> resourceStore = new ConcurrentHashMap<>();
        private final Map<String, Set<String>> deletedResources = new ConcurrentHashMap<>();
        private final Map<String, AtomicLong> nextIds = new ConcurrentHashMap<>();

        public Session(String sessionKey) {
            this.sessionKey = sessionKey;
            this.createdAt = Instant.now();
        }

        public String getSessionKey() {
            return sessionKey;
        }

        public Instant getCreatedAt() {
            return createdAt;
        }

        public void setExpiry(Instant expiry) {
            this.expiry = expiry;
        }

        public Instant getExpiry() {
            return expiry;
        }

        public boolean isExpired(Instant now) {
            return expiry != null && now.isAfter(expiry);
        }

        public boolean isDataLoaded() {
            return dataLoaded;
        }

        public void setDataLoaded(boolean dataLoaded) {
            this.dataLoaded = dataLoaded;
        }

        // Resource storage methods

        public Map<String, TreeMap<Long, IBaseResource>> getResourceMap(String resourceType) {
            return resourceStore.computeIfAbsent(resourceType, k -> new ConcurrentHashMap<>());
        }

        public Set<String> getDeletedIds(String resourceType) {
            return deletedResources.computeIfAbsent(resourceType, k -> ConcurrentHashMap.newKeySet());
        }

        public AtomicLong getNextId(String resourceType) {
            return nextIds.computeIfAbsent(resourceType, k -> new AtomicLong(1));
        }

        /**
         * Store a resource in this session.
         */
        @SuppressWarnings("unchecked")
        public <T extends IBaseResource> void storeResource(String resourceType, String id, long version, T resource) {
            Map<String, TreeMap<Long, IBaseResource>> typeMap = getResourceMap(resourceType);
            typeMap.computeIfAbsent(id, k -> new TreeMap<>()).put(version, resource);
            getDeletedIds(resourceType).remove(id);
        }

        /**
         * Get a resource from this session.
         */
        @SuppressWarnings("unchecked")
        public <T extends IBaseResource> T getResource(String resourceType, String id, Long version) {
            Map<String, TreeMap<Long, IBaseResource>> typeMap = getResourceMap(resourceType);
            TreeMap<Long, IBaseResource> versions = typeMap.get(id);
            if (versions == null || versions.isEmpty()) {
                return null;
            }
            if (version != null) {
                return (T) versions.get(version);
            }
            return (T) versions.lastEntry().getValue();
        }

        /**
         * Get all resources of a type from this session.
         */
        @SuppressWarnings("unchecked")
        public <T extends IBaseResource> List<T> getAllResources(String resourceType) {
            Map<String, TreeMap<Long, IBaseResource>> typeMap = getResourceMap(resourceType);
            Set<String> deleted = getDeletedIds(resourceType);
            List<T> results = new ArrayList<>();

            for (Map.Entry<String, TreeMap<Long, IBaseResource>> entry : typeMap.entrySet()) {
                if (!deleted.contains(entry.getKey()) && !entry.getValue().isEmpty()) {
                    results.add((T) entry.getValue().lastEntry().getValue());
                }
            }

            return results;
        }

        /**
         * Mark a resource as deleted.
         */
        public void deleteResource(String resourceType, String id) {
            getDeletedIds(resourceType).add(id);
        }

        /**
         * Check if a resource exists.
         */
        public boolean resourceExists(String resourceType, String id) {
            Map<String, TreeMap<Long, IBaseResource>> typeMap = getResourceMap(resourceType);
            return typeMap.containsKey(id) && !getDeletedIds(resourceType).contains(id);
        }

        /**
         * Check if a resource is deleted.
         */
        public boolean isDeleted(String resourceType, String id) {
            return getDeletedIds(resourceType).contains(id);
        }

        /**
         * Get the next ID for a resource type.
         */
        public long getAndIncrementNextId(String resourceType) {
            return getNextId(resourceType).getAndIncrement();
        }

        /**
         * Clear all resources in this session.
         */
        public void clear() {
            resourceStore.clear();
            deletedResources.clear();
            nextIds.clear();
            dataLoaded = false;
        }
    }
}
