package ru.feytox.etherology.util.misc;

import net.minecraft.entity.Entity;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;

public class PlayerUtil {

    public static void addAndUpdateVelocity(Entity entity, Vec3d deltaVelocity) {
        var oldVelocity = entity.getVelocity();
        entity.addVelocity(deltaVelocity);
        updateVelocity(entity, oldVelocity);
    }

    public static void updateVelocity(Entity entity, Vec3d oldVelocity) {
        if (!entity.velocityModified || !(entity instanceof ServerPlayerEntity player))
            return;

        player.networkHandler.sendPacket(new EntityVelocityUpdateS2CPacket(player));
        player.velocityModified = false;
        player.setVelocity(oldVelocity);
    }
}
