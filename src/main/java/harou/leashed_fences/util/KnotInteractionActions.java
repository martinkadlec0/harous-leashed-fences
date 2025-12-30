package harou.leashed_fences.util;

import harou.leashed_fences.util.KnotInteractionHelper.HeldEntities;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Leashable;
import net.minecraft.world.entity.decoration.LeashFenceKnotEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.gameevent.GameEvent;

public class KnotInteractionActions {
    public static InteractionResult connectKnotToPlayer(Player player, LeashFenceKnotEntity knot) {
        double distance = player.distanceToSqr(knot);
        if (distance <= 100.0) { // 10 blocks squared (same as vanilla)
            KnotInteractionHelper.consumeLead(player);
            ((Leashable)knot).setLeashedTo(player, true);
            knot.gameEvent(GameEvent.BLOCK_ATTACH, player);
            knot.playSound(SoundEvents.LEAD_TIED);
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }

    public static InteractionResult dropKnotToPlayerConnection(Player player, LeashFenceKnotEntity knot) {
        ((Leashable)knot).dropLeash();
        knot.gameEvent(GameEvent.BLOCK_DETACH, player);
        knot.playSound(SoundEvents.LEAD_UNTIED);
        return InteractionResult.SUCCESS_SERVER;
    }

    public static InteractionResult passLeadsFromPlayerToKnot(Player player, LeashFenceKnotEntity knot, boolean playSound) {
        HeldEntities held = new HeldEntities(player);

        boolean newCustomConnection = KnotInteractionHelper.createCustomConnections(
            held, knot, player
        );
        boolean newVanillaConnection = KnotInteractionHelper.createVanillaConnections(
            held, knot, player
        );

        if (newVanillaConnection || newCustomConnection) {
            knot.gameEvent(GameEvent.BLOCK_ATTACH, player);
            if (playSound) knot.playSound(SoundEvents.LEAD_TIED);
        }
        
        KnotConnectionManager.getManager(knot).checkDistance(knot);
        
        return InteractionResult.SUCCESS_SERVER;
    }

    public static InteractionResult passMobsFromKnotToPlayer(Player player, LeashFenceKnotEntity knot) {
        HeldEntities held = new HeldEntities(knot);

        for (Leashable leashable : held.mobs) {
            if (leashable.canHaveALeashAttachedTo(player)) {
                leashable.setLeashedTo(player, true);
            }
        }

        knot.playSound(SoundEvents.LEAD_UNTIED);
        return InteractionResult.SUCCESS_SERVER;
    }

    public static InteractionResult passKnotsFromKnotToPlayer(Player player, LeashFenceKnotEntity knot) {
        if (!player.isShiftKeyDown()) {
            KnotInteractionHelper.pickupCustomConnections(knot, player);
            knot.playSound(SoundEvents.LEAD_UNTIED);
        } else {
            KnotInteractionHelper.discardCustomConnections(knot, player);
            knot.playSound(SoundEvents.LEAD_UNTIED);
        }
        return InteractionResult.SUCCESS_SERVER;
    }
}
