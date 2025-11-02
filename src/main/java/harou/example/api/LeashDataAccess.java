package harou.example.api;

/**
 * Accessor interface to add custom fields to LeashData.
 * This allows us to mark knot-to-knot connections for special rendering.
 */
public interface LeashDataAccess {
    /**
     * Returns whether this LeashData represents a knot-to-knot connection.
     * @return true if this is a custom knot-to-knot connection, false otherwise
     */
    boolean leashedFences$isKnotToKnot();
    
    /**
     * Sets whether this LeashData represents a knot-to-knot connection.
     * @param isKnotToKnot true if this is a custom knot-to-knot connection
     */
    void leashedFences$setIsKnotToKnot(boolean isKnotToKnot);
}

