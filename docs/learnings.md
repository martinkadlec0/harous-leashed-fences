# Game events (block detached/attached)
    - Use e.g. by sculk sensors
# mixins at("TAIL") vs at("RETURN")
    - Tail is called only for last return in the function
    - Return is called for every return in the function
# Add existing lead connection to a mob
    - Happens in Entity.interact
# Does $ in mixin field names have special funcionality or is it just a way to indicate?
    - https://wiki.fabricmc.net/tutorial:mixin_accessors
# Maven vs Gradle
    - Maven is used for repository management
    - Gradle is used as build tool
# Publications
    - Artifact - Output file produced by build
    - Publication - meta file (pom/sha) + artifacts (main jar, source jar, docs jar)
    - Coordinates - group:artifact:version