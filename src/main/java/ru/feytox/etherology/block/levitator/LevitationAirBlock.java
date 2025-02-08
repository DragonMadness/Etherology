package ru.feytox.etherology.block.levitator;

import net.minecraft.block.*;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.minecraft.world.WorldAccess;
import ru.feytox.etherology.mixin.LivingEntityAccessor;
import ru.feytox.etherology.particle.effects.LightParticleEffect;
import ru.feytox.etherology.particle.subtype.LightSubtype;
import ru.feytox.etherology.registry.block.EBlocks;
import ru.feytox.etherology.registry.particle.EtherParticleTypes;
import ru.feytox.etherology.util.deprecated.EVec3d;
import ru.feytox.etherology.util.misc.PlayerUtil;

public class LevitationAirBlock extends Block {

    public static final BooleanProperty PUSHING = LevitatorBlock.PUSHING;
    // movement direction in if pushing
    public static final DirectionProperty DIRECTION = LevitatorBlock.FACING;

    private static final double MAX_SPEED = 0.35;
    private static final double DELTA_SPEED = 0.06;
    private static final double RESISTANCE = 0.2;
    private static final double SNEAKING_MULTIPLIER = 0.2;
    private static final double JUMPING_MULTIPLIER = 0.5;
    private static final double FALL_REDUCTION_MULTIPLIER = 0.8;
    private static final double REDUCTION_END = 0.1;

    public LevitationAirBlock() {
        super(Settings.create().replaceable().noCollision().dropsNothing().sounds(BlockSoundGroup.INTENTIONALLY_EMPTY));

        setDefaultState(getDefaultState()
                .with(PUSHING, true)
                .with(DIRECTION, Direction.UP));
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(PUSHING, DIRECTION);
    }

    @Override
    public void randomDisplayTick(BlockState state, World world, BlockPos pos, Random random) {
        var lightType = state.get(PUSHING) ? LightSubtype.PUSHING : LightSubtype.ATTRACT;
        var centerPos = pos.toCenterPos();
        var moveVec = pos.add(state.get(DIRECTION).getVector()).toCenterPos().subtract(centerPos);

        var effect = new LightParticleEffect(EtherParticleTypes.LIGHT, lightType, moveVec);
        effect.spawnParticles(world, 1, 0.5, centerPos);
    }

    @Override
    protected BlockState getStateForNeighborUpdate(BlockState state, Direction direction, BlockState neighborState, WorldAccess world, BlockPos pos, BlockPos neighborPos) {
        var levitationDirection = state.get(DIRECTION);
        if (levitationDirection.equals(direction) && neighborState.isAir()) {
            updateLevitator(world, pos, direction.getOpposite());
            return state;
        }

        if (!levitationDirection.equals(direction.getOpposite()))
            return state;

        if (neighborState.isOf(EBlocks.LEVITATION_AIR))
            return getStateForLevitationAirUpdate(state, neighborState, levitationDirection);

        if (canSkipBlock(world, neighborState, neighborPos))
            return state;

        if (!neighborState.isOf(EBlocks.LEVITATOR))
            return Blocks.AIR.getDefaultState();

        if (LevitatorBlock.hasLevitation(neighborState))
            return state;

        return Blocks.AIR.getDefaultState();
    }

    @Override
    protected void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
        updateNextBlocks(world, newState, state.get(DIRECTION), pos);
        super.onStateReplaced(state, world, pos, newState, moved);
    }

    private void updateNextBlocks(World world, BlockState newState, Direction direction, BlockPos pos) {
        var currentPos = pos.mutableCopy().move(direction);
        var currentState = world.getBlockState(currentPos);

        // handled in getStateForNeighborUpdate
        if (currentState.isOf(EBlocks.LEVITATION_AIR))
            return;

        while (canSkipBlock(world, currentState, currentPos)) {
            currentPos.move(direction);
            currentState = world.getBlockState(currentPos);
            if (!currentState.isOf(EBlocks.LEVITATION_AIR))
                continue;

            world.setBlockState(currentPos, getStateForLevitationAirUpdate(currentState, newState, direction));
            return;
        }
    }

    private static boolean canSkipBlock(WorldAccess world, BlockState state, BlockPos pos) {
        return !state.isAir() && !state.isSolidBlock(world, pos);
    }

    private static void updateLevitator(WorldAccess world, BlockPos airPos, Direction levitatorDirection) {
        var mutablePos = airPos.mutableCopy();

        var currentState = world.getBlockState(mutablePos);
        while (canSkipBlock(world, currentState, mutablePos) || currentState.isOf(EBlocks.LEVITATION_AIR)) {
            mutablePos.move(levitatorDirection);
            currentState = world.getBlockState(mutablePos);
            if (!currentState.isOf(EBlocks.LEVITATOR))
                continue;

            LevitatorBlock.updateLevitation(world, currentState, mutablePos);
            break;
        }
    }

    private static BlockState getStateForLevitationAirUpdate(BlockState state, BlockState neighborState, Direction levitationDirection) {
        if (!neighborState.isOf(state.getBlock()) || !neighborState.get(DIRECTION).equals(levitationDirection))
            return Blocks.AIR.getDefaultState();

        var isPushing = state.get(PUSHING);
        var neighborPushing = neighborState.get(PUSHING);
        if (isPushing != neighborPushing)
            return state.with(PUSHING, neighborPushing);

        return state;
    }

    // TODO: 08.02.2025 refactor
    @Override
    protected void onEntityCollision(BlockState state, World world, BlockPos pos, Entity entity) {
        var isPushing = state.get(PUSHING);
        var isSneaking = entity.isSneaking();
        var isJumping = entity instanceof LivingEntity livingEntity && ((LivingEntityAccessor) livingEntity).getJumping();
        var direction = state.get(DIRECTION);
        direction = isPushing ? direction : direction.getOpposite();

        var isVertical = direction.getHorizontal() == -1;
        var directionVec = Vec3d.of(direction.getVector());
        var absDirectionVec = EVec3d.abs(directionVec);
        var velocity = entity.getVelocity();
        var maxSpeed = MAX_SPEED;

        if (isVertical && (isSneaking || isJumping)) {
            var directionMultiplier = isSneaking && isJumping
                    ? 0
                    : getDirectionMultiplier(direction, isSneaking, Direction.DOWN) * getDirectionMultiplier(direction, isJumping, Direction.UP);
            var maxSpeedMultiplier = getMaxSpeedMultiplier(direction, isSneaking, SNEAKING_MULTIPLIER, Direction.DOWN) + getMaxSpeedMultiplier(direction, isJumping, JUMPING_MULTIPLIER, Direction.UP);
            directionVec = directionVec.multiply(directionMultiplier);
            maxSpeed *= Math.abs(maxSpeedMultiplier);
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

    private static int getDirectionMultiplier(Direction direction, boolean flag, Direction actionDirection) {
        return flag && !direction.equals(actionDirection) ? -1 : 1;
    }

    private static double getMaxSpeedMultiplier(Direction direction, boolean flag, double multiplier, Direction actionDirection) {
        if (!flag)
            return 0;

        return direction.equals(actionDirection) ? 1 + multiplier : -multiplier;
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

    @Override
    protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return VoxelShapes.empty();
    }

    protected BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.INVISIBLE;
    }
}
