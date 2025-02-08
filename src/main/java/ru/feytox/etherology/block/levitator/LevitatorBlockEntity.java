package ru.feytox.etherology.block.levitator;

import net.minecraft.block.BlockState;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.jetbrains.annotations.Nullable;
import ru.feytox.etherology.magic.ether.EtherStorage;
import ru.feytox.etherology.util.misc.TickableBlockEntity;

import static net.minecraft.state.property.Properties.POWER;
import static ru.feytox.etherology.registry.block.EBlocks.LEVITATOR_BLOCK_ENTITY;

public class LevitatorBlockEntity extends TickableBlockEntity implements EtherStorage {
    private static final int FUEL_TIME = 100;
    private int fuel = 0;
    private float storedEther = 0;

    public LevitatorBlockEntity(BlockPos pos, BlockState state) {
        super(LEVITATOR_BLOCK_ENTITY, pos, state);
    }

    @Override
    public void serverTick(ServerWorld world, BlockPos blockPos, BlockState state) {
        tickFuel(world, blockPos, state);
        markDirty();
    }

    private void tickFuel(ServerWorld world, BlockPos blockPos, BlockState state) {
        fuel = Math.max(0, fuel - 1);
        if (fuel > 0) return;
        int power = state.get(POWER);
        if (power <= 0) return;

        boolean wasFueled = state.get(LevitatorBlock.WITH_FUEL);
        if (storedEther > 0) {
            decrement(1);
            fuel = FUEL_TIME;
            if (!wasFueled) world.setBlockState(blockPos, state.with(LevitatorBlock.WITH_FUEL, true));
            return;
        }

        world.setBlockState(blockPos, state.with(LevitatorBlock.WITH_FUEL, false));
    }

    @Override
    public float getMaxEther() {
        return 1;
    }

    @Override
    public float getStoredEther() {
        return storedEther;
    }

    @Override
    public float getTransferSize() {
        return 1;
    }

    @Override
    public void setStoredEther(float value) {
        storedEther = value;
    }

    @Override
    public boolean isInputSide(Direction side) {
        return true;
    }

    @Nullable
    @Override
    public Direction getOutputSide() {
        return null;
    }

    @Override
    public BlockPos getStoragePos() {
        return pos;
    }

    @Override
    public void transferTick(ServerWorld world) {}

    @Override
    public boolean isActivated() {
        return false;
    }

    @Override
    protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
        nbt.putInt("fuel", fuel);
        nbt.putFloat("stored_ether", storedEther);

        super.writeNbt(nbt, registryLookup);
    }

    @Override
    public void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
        super.readNbt(nbt, registryLookup);

        fuel = nbt.getInt("fuel");
        storedEther = nbt.getFloat("stored_ether");
    }
}
