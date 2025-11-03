# Variables to consider when right clicking a fence
- Is the player leading any mobs?
- Are there some mobs already attached to a fence?
- Is the player connected to the fence?
- Is the fence attached to another fecne in either direction?
- Is the player holding lead item in hand?
- Is the player holding modifier key (shift)?
- Is the player interactoin with knot on fence, or the fence itself?
- What about off-hand?


# LeashData
- Entity has leash data if it is being leashed (with infomration about the holder)
- Therefore, an entity can be holder of multiple entities, BUT each entity can have only one holder

# TODO
- Investigate: To make sure we are not missing something
    - Leashable.tickLeash
    - onHeldLeashUpdate
- Refactor: LeashData isKnotConnection
- Feature: Mod Menu droop config