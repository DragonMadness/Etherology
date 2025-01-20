package ru.feytox.etherology.client.particle;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.particle.SpriteProvider;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import ru.feytox.etherology.client.particle.utility.MovingParticle;
import ru.feytox.etherology.item.OculusItem;
import ru.feytox.etherology.particle.effects.SealParticleEffect;

public class SealParticle extends MovingParticle<SealParticleEffect> {

    private final Vec3d endPos;

    public SealParticle(ClientWorld clientWorld, double x, double y, double z, SealParticleEffect parameters, SpriteProvider spriteProvider) {
        super(clientWorld, x, y, z, parameters, spriteProvider);
        endPos = parameters.getEndPos();
        setSpriteForAge();
        maxAge = 20 * random.nextBetween(1, 2);
        scale(0.15f);

        var zoneType = parameters.getZoneType();
        setRandomColor(zoneType.getStartColor(), zoneType.getEndColor());
    }

    @Override
    public void tick() {
        setSpriteForAge();
        acceleratedMovingTick(0.2f, 0.3f, true, endPos);

        var player = MinecraftClient.getInstance().player;
        if (player == null) return;
        float alpha = MathHelper.lerp(0.25f, getAlpha(), OculusItem.isInHands(player) ? 1.0f : 0.0f);
        setAlpha(alpha);
    }
}
