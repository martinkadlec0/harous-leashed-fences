package harou.leashed_fences.api;

import harou.leashed_fences.util.KnotConnectionManager;

/**
 * Interface to access the custom connection manager from LeashKnotEntity.
 * Implemented via mixin.
 */
public interface KnotConnectionAccess {
    KnotConnectionManager leashedFences$getConnectionManager();
}

