package harou.leashed_fences.api;

/**
 * Interface for entities that want to add custom tick logic after BlockAttachedEntity.tick() runs.
 * This is implemented via mixin to avoid polluting the parent class with mod-specific logic.
 */
public interface CustomTickHandler {
    /**
     * Called at the end of tick() for entities that implement this interface.
     */
    void onCustomTick();
}

