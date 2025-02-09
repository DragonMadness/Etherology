package ru.feytox.etherology.block.levitator;

import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.*;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import ru.feytox.etherology.magic.ether.EtherStorage;
import ru.feytox.etherology.mixin.LivingEntityAccessor;
import ru.feytox.etherology.particle.effects.LightParticleEffect;
import ru.feytox.etherology.particle.subtype.LightSubtype;
import ru.feytox.etherology.registry.particle.EtherParticleTypes;
import ru.feytox.etherology.util.deprecated.EVec3d;
import ru.feytox.etherology.util.misc.PlayerUtil;
import ru.feytox.etherology.util.misc.TickableBlockEntity;

import java.util.Optional;

import static net.minecraft.state.property.Properties.POWER;
import static ru.feytox.etherology.registry.block.EBlocks.LEVITATOR_BLOCK_ENTITY;

public class LevitatorBlockEntity extends TickableBlockEntity implements EtherStorage {

    private static final double MAX_SPEED = 0.35;
    private static final double DELTA_SPEED = 0.12;
    private static final double RESISTANCE = 0.3;
    private static final double SNEAKING_MULTIPLIER = 0.4;
    private static final double JUMPING_MULTIPLIER = 0.7;
    private static final double FALL_REDUCTION_MULTIPLIER = 0.6;
    private static final double REDUCTION_END = 0.1;

    private static final int FUEL_TIME = 100;
    private int fuel = 0;
    private float storedEther = 0;

    public LevitatorBlockEntity(BlockPos pos, BlockState state) {
        super(LEVITATOR_BLOCK_ENTITY, pos, state);
    }

    @Override
    public void serverTick(ServerWorld world, BlockPos blockPos, BlockState state) {
        if (tickFuel(world, blockPos, state))
            tickLevitation(world, blockPos, state);
        markDirty();
    }

    private boolean tickFuel(ServerWorld world, BlockPos pos, BlockState state) {
        fuel = Math.max(0, fuel - 1);
        if (fuel > 0)
            return true;
        int power = state.get(POWER);
        if (power <= 0)
            return false;

        boolean wasFueled = state.get(LevitatorBlock.WITH_FUEL);
        if (storedEther > 0) {
            decrement(1);
            fuel = FUEL_TIME;
            if (!wasFueled)
                world.setBlockState(pos, state.with(LevitatorBlock.WITH_FUEL, true));
            return true;
        }

        world.setBlockState(pos, state.with(LevitatorBlock.WITH_FUEL, false));
        return false;
    }

    public void tickLevitation(World world, BlockPos pos, BlockState state) {
        var flowDirection = state.get(LevitatorBlock.FACING).getOpposite();
        var levitationLength = state.get(POWER);
        var optionalBox = getLevitationBox(world, pos, flowDirection, levitationLength);
        if (optionalBox.isEmpty())
            return;

        var levitationBlockBox = optionalBox.get();
        var levitationBox = Box.from(levitationBlockBox);

        var isPushing = state.get(LevitatorBlock.PUSHING);
        var direction = isPushing ? flowDirection : flowDirection.getOpposite();
        var directionVec = Vec3d.of(direction.getVector());
        var absDirectionVec = EVec3d.abs(directionVec);
        var isVertical = direction.getHorizontal() == -1;

        if (world.isClient && world.getTime() % 10 == 0)
            tickParticles(world, levitationBlockBox, directionVec, isPushing);

        var entities = world.getNonSpectatingEntities(Entity.class, levitationBox);
        entities.forEach(entity -> tickEntityLevitation(direction, directionVec, absDirectionVec, isVertical, entity));
    }

    // TODO: 08.02.2025 refactor
    private void tickEntityLevitation(Direction direction, Vec3d directionVec, Vec3d absDirectionVec, boolean isVertical, Entity entity) {
        var isSneaking = entity.isSneaking();
        var isJumping = entity instanceof LivingEntity livingEntity && ((LivingEntityAccessor) livingEntity).getJumping();

        var velocity = entity.getVelocity();
        var maxSpeed = MAX_SPEED;

        if (isVertical && (isSneaking ^ isJumping)) {
            var actionDirection = isSneaking ? Direction.DOWN : Direction.UP;
            var actionMultiplier = isSneaking ? SNEAKING_MULTIPLIER : JUMPING_MULTIPLIER;
            if (direction.equals(actionDirection))
                maxSpeed *= (1 + actionMultiplier);
            else {
                directionVec = directionVec.multiply(-1);
                maxSpeed *= actionMultiplier;
            }
        }

        var velocityDirection = velocity.multiply(absDirectionVec);
        var speedDirection = velocity.dotProduct(directionVec);

        var newSpeedDirection = Math.min(maxSpeed, speedDirection + DELTA_SPEED);
        var newVelocityDirection = directionVec.multiply(newSpeedDirection);

        var newVelocity = velocity.subtract(velocityDirection).add(newVelocityDirection);
        newVelocity = applyResistance(absDirectionVec, newVelocity);
        if (!isSneaking && !isVertical)
            newVelocity = applyFallReduction(entity, pos, newVelocity);

        entity.setVelocity(newVelocity);
        PlayerUtil.updateVelocity(entity, velocity);
        entity.onLanding();
    }

    private static Vec3d applyResistance(Vec3d absDirectionVec, Vec3d velocity) {
        var planeVec = new Vec3d(1 - absDirectionVec.x, 1 - absDirectionVec.y, 1 - absDirectionVec.z);
        return velocity.subtract(planeVec.multiply(velocity).multiply(RESISTANCE));
    }

    private static Vec3d applyFallReduction(Entity entity, BlockPos pos, Vec3d velocity) {
        var entityPos = entity.getBoundingBox().getCenter();
        var deltaY = pos.toCenterPos().y - entityPos.y;

        if (velocity.y > REDUCTION_END || deltaY < 0)
            return velocity;

        var newY = MathHelper.lerp(FALL_REDUCTION_MULTIPLIER, velocity.y, REDUCTION_END);

        return new Vec3d(velocity.x, newY, velocity.z);
    }

    private Optional<BlockBox> getLevitationBox(World world, BlockPos pos, Direction flowDirection, int maxLength) {
        var levitationLength = getTrueLevitationLength(world, pos, flowDirection, maxLength);
        if (levitationLength == 0)
            return Optional.empty();

        return Optional.of(BlockBox.create(pos.offset(flowDirection), pos.offset(flowDirection, levitationLength)));
    }

    private int getTrueLevitationLength(World world, BlockPos pos, Direction flowDirection, int maxLength) {
        var endPos = pos.mutableCopy();
        for (int i = 0; i < maxLength; i++) {
            endPos.move(flowDirection);
            var state = world.getBlockState(endPos);
            if (!state.isAir() && state.isSolidBlock(world, endPos))
                return i;
        }
        return maxLength;
    }

    private void tickParticles(World world, BlockBox levitationBox, Vec3d directionVec, boolean isPushing) {
        var lightType = isPushing ? LightSubtype.PUSHING : LightSubtype.ATTRACT;
        var effect = new LightParticleEffect(EtherParticleTypes.LIGHT, lightType, directionVec);

        BlockPos.stream(levitationBox)
                .map(BlockPos::toCenterPos)
                .forEach(pos -> effect.spawnParticles(world, 1, 0.5, pos));
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
    public void transferTick(ServerWorld world) {
    }

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
