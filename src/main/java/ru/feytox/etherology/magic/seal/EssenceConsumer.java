package ru.feytox.etherology.magic.seal;

import net.minecraft.block.entity.BlockEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.Optional;

public interface EssenceConsumer {

    int getRadius();

    float getConsumingValue();

    void incrementEssence(float value);

    Optional<EssenceSupplier> getCachedSeal();

    void setCachedSeal(EssenceSupplier seal);

    boolean isSearchingStopped();

    void setSearchingStopped(boolean value);

    default Optional<EssenceSupplier> getAndCacheSeal(World world, BlockPos pos, SealType blockType) {
        if (getCachedSeal().filter(EssenceSupplier::isAlive).isPresent())
            return getCachedSeal();

        if (isSearchingStopped())
            return Optional.empty();

        int consumerRadius = getRadius();
        Optional<EssenceSupplier> sealOptional = BlockPos.findClosest(pos, consumerRadius, consumerRadius, blockPos -> {
                    if (!(world.getBlockEntity(blockPos) instanceof EssenceSupplier essenceSupplier)) return false;
                    if (!essenceSupplier.isAlive()) return false;
                    return !blockType.isSeal() || essenceSupplier.getSealType().equals(blockType);
                })
                .map(blockPos -> (EssenceSupplier) world.getBlockEntity(blockPos))
                .filter(essenceSupplier -> essenceSupplier.getSupplierPos().isWithinDistance(pos, essenceSupplier.getRadius()));

        sealOptional.ifPresent(this::setCachedSeal);
        return sealOptional;
    }

    default Optional<SealType> tickConsuming(ServerWorld world, BlockPos pos, SealType blockType) {
        var seal = getAndCacheSeal(world, pos, blockType).orElse(null);
        if (seal == null) {
            setSearchingStopped(true);
            return Optional.empty();
        }

        var sealType = seal.getSealType();
        float consumedPoints = seal.decrement(world, getConsumingValue());
        incrementEssence(consumedPoints);
        return Optional.of(sealType);
    }

    static <T extends BlockEntity & EssenceConsumer> void activateSearching(World world, BlockPos pos, Class<T> consumerClass) {
        var blockEntity = world.getBlockEntity(pos);
        if (consumerClass.isInstance(blockEntity))
            consumerClass.cast(blockEntity).setSearchingStopped(false);
    }
}
