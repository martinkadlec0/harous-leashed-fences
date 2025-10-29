package harou.example.api;

import harou.example.util.KnotConnectionManager;

/**
 * Interface to access the custom connection manager from LeashKnotEntity.
 * Implemented via mixin.
 */
public interface KnotConnectionAccess {
    KnotConnectionManager leashedFences$getConnectionManager();
}

