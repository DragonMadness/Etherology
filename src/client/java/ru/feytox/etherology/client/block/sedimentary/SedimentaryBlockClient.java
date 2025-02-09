package ru.feytox.etherology.client.block.sedimentary;

import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import ru.feytox.etherology.block.sedimentary.SedimentaryStone;
import ru.feytox.etherology.block.sedimentary.SedimentaryStoneBlockEntity;
import ru.feytox.etherology.item.OculusItem;
import ru.feytox.etherology.magic.seal.EssenceSupplier;
import ru.feytox.etherology.particle.effects.SealParticleEffect;
import ru.feytox.etherology.registry.particle.EtherParticleTypes;

public class SedimentaryBlockClient {

    private static final int RADIUS = 1;
    private static final float MAX_CHANCE = 1 / 50f;

    public static void clientTick(SedimentaryStoneBlockEntity blockEntity, ClientWorld world, BlockPos blockPos, BlockState state) {
        if (!state.get(SedimentaryStone.POWERED))
            return;

        SedimentaryStone.executeOnStone(state, stone -> {
            var sealType = stone.getSealType();
            var sealOptional = blockEntity.getAndCacheSeal(world, blockPos, sealType);
            if (sealOptional.isEmpty())
                blockEntity.setSearchingStopped(true);
            sealOptional.ifPresent(seal -> tickParticlesAroundBlock(world, seal, blockPos));
        });
    }

    private static void tickParticlesAroundBlock(World world, EssenceSupplier essenceSupplier, BlockPos pos) {
        var player = MinecraftClient.getInstance().player;
        if (player == null)
            return;
        if (!OculusItem.isInHands(player))
            return;

        var zonePercent = essenceSupplier.getFillPercent();
        int count = MathHelper.ceil(5 * zonePercent);
        var effect = new SealParticleEffect(EtherParticleTypes.SEAL, essenceSupplier.getSealType(), pos.toCenterPos());
        var random = world.getRandom();

        for (int dx = -RADIUS; dx <= RADIUS; dx++) {
            for (int dy = -RADIUS; dy <= RADIUS; dy++) {
                for (int dz = -RADIUS; dz <= RADIUS; dz++) {
                    if (random.nextFloat() > zonePercent * MAX_CHANCE)
                        continue;
                    effect.spawnParticles(world, count, 0.5, pos.add(dx, dy, dz).toCenterPos());
                }
            }
        }
    }
}
