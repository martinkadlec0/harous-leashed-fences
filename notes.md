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
- Investiage: When having mob connection (but not having lead in hand) and interacting with fence or another mob, what code handles that?
    - It is Entity.interact
- Fix: Shears should remove knot connection
- Fix: Leashing knot to another knot that already hos mobs attached
- Fix: Leashing knot to a happy ghost using shift+click
- Fix: Respect drop rule for leads:
    world.getGameRules().getBoolean(GameRules.DO_ENTITY_DROPS)
- Investigate: Knot removal when fence is destroyed (Vanilla does something I don't know about)
    - Maybe Leashable.tickLeash?
- Investigate: Can we handle LeadItem interaction on fence/entity?
- Investigate: Does $ in mixin field names have special funcionality or is it just a wayto indicate?
- Refactor: Clearer var. names - Player holding lead item vs Player having lead connection
- Refactor: LeashData isKnotConnection
- Feature: Mod Menu droop config

- What about ability for player to hold multiple knots without tying them together?