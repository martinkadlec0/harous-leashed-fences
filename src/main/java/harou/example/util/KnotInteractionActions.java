package harou.example.util;

import harou.example.util.KnotInteractionHelper.HeldEntities;
import net.minecraft.entity.Leashable;
import net.minecraft.entity.decoration.LeashKnotEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.world.event.GameEvent;

public class KnotInteractionActions {
    public static ActionResult connectKnotToPlayer(PlayerEntity player, LeashKnotEntity knot) {
        double distance = player.squaredDistanceTo(knot);
        if (distance <= 100.0) { // 10 blocks squared (same as vanilla)
            KnotInteractionHelper.consumeLead(player);
            ((Leashable)knot).attachLeash(player, true);
            knot.emitGameEvent(GameEvent.BLOCK_ATTACH, player);
            knot.playSoundIfNotSilent(SoundEvents.ITEM_LEAD_TIED);
            return ActionResult.SUCCESS;
        }
        return ActionResult.PASS;
    }

    public static ActionResult dropKnotToPlayerConnection(PlayerEntity player, LeashKnotEntity knot) {
        ((Leashable)knot).detachLeash();
        knot.emitGameEvent(GameEvent.BLOCK_DETACH, player);
        knot.playSoundIfNotSilent(SoundEvents.ITEM_LEAD_UNTIED);
        return ActionResult.SUCCESS_SERVER;
    }

    public static ActionResult passLeadsFromPlayerToKnot(PlayerEntity player, LeashKnotEntity knot, boolean playSound) {
        HeldEntities held = new HeldEntities(player);

        boolean newCustomConnection = KnotInteractionHelper.createCustomConnections(
            held, knot, player
        );
        boolean newVanillaConnection = KnotInteractionHelper.createVanillaConnections(
            held, knot, player
        );

        if (newVanillaConnection || newCustomConnection) {
            knot.emitGameEvent(GameEvent.BLOCK_ATTACH, player);
            if (playSound) knot.playSoundIfNotSilent(SoundEvents.ITEM_LEAD_TIED);
        }
        
        return ActionResult.SUCCESS_SERVER;
    }

    public static ActionResult passMobsFromKnotToPlayer(PlayerEntity player, LeashKnotEntity knot) {
        HeldEntities held = new HeldEntities(knot);

        for (Leashable leashable : held.mobs) {
            if (leashable.canBeLeashedTo(player)) {
                leashable.attachLeash(player, true);
            }
        }

        knot.playSoundIfNotSilent(SoundEvents.ITEM_LEAD_UNTIED);
        return ActionResult.SUCCESS_SERVER;
    }

    public static ActionResult passKnotsFromKnotToPlayer(PlayerEntity player, LeashKnotEntity knot) {
        if (!player.isSneaking()) {
            KnotInteractionHelper.pickupCustomConnections(knot, player);
            knot.playSoundIfNotSilent(SoundEvents.ITEM_LEAD_UNTIED);
        } else {
            KnotInteractionHelper.discardCustomConnections(knot, player);;
            knot.playSoundIfNotSilent(SoundEvents.ITEM_LEAD_UNTIED);
        }
        return ActionResult.SUCCESS_SERVER;
    }
}
