package ru.feytox.etherology.block.forestLantern;

import com.mojang.serialization.MapCodec;
import lombok.val;
import net.minecraft.block.*;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.sound.SoundCategory;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.IntProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.ActionResult;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.minecraft.world.WorldAccess;
import net.minecraft.world.WorldView;
import ru.feytox.etherology.registry.block.DecoBlocks;
import ru.feytox.etherology.util.event.PlayerJumpCallback;
import ru.feytox.etherology.util.misc.RegistrableBlock;

import java.util.EnumMap;
import java.util.Map;

/**
 * @see WallTorchBlock
 */
public class ForestLanternBlock extends HorizontalFacingBlock implements RegistrableBlock, Fertilizable {

    private static final MapCodec<ForestLanternBlock> CODEC = MapCodec.unit(ForestLanternBlock::new);
    private static final Map<Direction, VoxelShape[]> SHAPES;
    private static final float BREAK_CHANCE = 0.4f;

    public static final int MAX_AGE = Properties.AGE_4_MAX;
    public static final IntProperty AGE = Properties.AGE_4;
    private static final int GROW_FREQUENCY = 30;

    public ForestLanternBlock() {
        super(Settings.copy(Blocks.BROWN_MUSHROOM_BLOCK).notSolid().sounds(BlockSoundGroup.FUNGUS).luminance(value -> 8).postProcess((a, b, c) -> true).emissiveLighting((a, b, c) -> true));
        setDefaultState(getDefaultState().with(FACING, Direction.NORTH).with(AGE, MAX_AGE));
    }

    @Override
    public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        var age = state.get(AGE);
        return SHAPES.get(state.get(FACING))[age];
    }

    public static void registerJumpEvent() {
        PlayerJumpCallback.BEFORE_JUMP.register(player -> {
            var world = player.getWorld();
            if (world.isClient)
                return ActionResult.PASS;

            var pos = player.getSteppingPos();
            var state = world.getBlockState(pos);
            if (!state.isOf(DecoBlocks.FOREST_LANTERN))
                return ActionResult.PASS;

            if (world.getRandom().nextFloat() > BREAK_CHANCE)
                return ActionResult.PASS;

            world.playSound(null, pos, DecoBlocks.FOREST_LANTERN.getSoundGroup(state).getBreakSound(), SoundCategory.BLOCKS, 0.7F, 0.9F);
            world.breakBlock(pos, state.get(AGE) == MAX_AGE);
            return ActionResult.PASS;
        });
    }

    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        var state = getDefaultState();
        WorldView worldView = ctx.getWorld();
        var blockPos = ctx.getBlockPos();
        var directions = ctx.getPlacementDirections();

        for (var direction : directions) {
            if (!direction.getAxis().isHorizontal()) continue;

            state = state.with(FACING, direction.getOpposite());
            if (state.canPlaceAt(worldView, blockPos)) return state;
        }

        return null;
    }

    @Override
    protected boolean hasRandomTicks(BlockState state) {
        return state.get(AGE) < MAX_AGE;
    }

    @Override
    protected void randomTick(BlockState state, ServerWorld world, BlockPos pos, Random random) {
        if (random.nextInt(GROW_FREQUENCY) != 0)
            return;

        var currentAge = state.get(AGE);
        if (currentAge < MAX_AGE)
            world.setBlockState(pos, state.with(AGE, currentAge + 1));
    }

    @Override
    public boolean canPlaceAt(BlockState state, WorldView world, BlockPos pos) {
        var direction = state.get(FACING);
        return canPlaceAt(world, pos, direction);
    }

    private static boolean canPlaceAt(WorldView world, BlockPos pos, Direction facing) {
        var logPos = pos.offset(facing.getOpposite());
        var logState = world.getBlockState(logPos);
        return logState.isSideSolidFullSquare(world, logPos, facing);
    }

    @Override
    public BlockState getStateForNeighborUpdate(BlockState state, Direction direction, BlockState neighborState, WorldAccess world, BlockPos pos, BlockPos neighborPos) {
        return direction.getOpposite() == state.get(FACING) && !state.canPlaceAt(world, pos) ? Blocks.AIR.getDefaultState() : state;
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(FACING, AGE);
    }

    @Override
    public boolean isTransparent(BlockState state, BlockView world, BlockPos pos) {
        return true;
    }

    @Override
    protected MapCodec<? extends HorizontalFacingBlock> getCodec() {
        return CODEC;
    }

    public BlockState rotate(BlockState state, BlockRotation rotation) {
        return state.with(FACING, rotation.rotate(state.get(FACING)));
    }

    public BlockState mirror(BlockState state, BlockMirror mirror) {
        return state.rotate(mirror.getRotation(state.get(FACING)));
    }

    @Override
    public String getBlockId() {
        return "forest_lantern";
    }

    static {
        SHAPES = new EnumMap<>(Map.of(
                Direction.NORTH, new VoxelShape[]{
                        Block.createCuboidShape(4, 4, 13, 12, 12, 16),
                        Block.createCuboidShape(5.5, 5, 11, 10.5, 11, 16),
                        Block.createCuboidShape(5, 5, 9, 11, 12, 16),
                        Block.createCuboidShape(4, 5, 6, 12, 14, 16),
                        Block.createCuboidShape(2, 4, 4, 14, 16, 16)
                },
                Direction.SOUTH, new VoxelShape[]{
                        Block.createCuboidShape(4, 4, 0, 12, 12, 3),
                        Block.createCuboidShape(5.5, 5, 0, 10.5, 11, 5),
                        Block.createCuboidShape(5, 5, 0, 11, 12, 7),
                        Block.createCuboidShape(4, 5, 0, 12, 14, 10),
                        Block.createCuboidShape(2, 4, 0, 14, 16, 12)
                },
                Direction.WEST, new VoxelShape[]{
                        Block.createCuboidShape(13, 4, 4, 16, 12, 12),
                        Block.createCuboidShape(11, 5, 5.5, 16, 11, 10.5),
                        Block.createCuboidShape(9, 5, 5, 16, 12, 11),
                        Block.createCuboidShape(6, 5, 4, 16, 14, 12),
                        Block.createCuboidShape(4, 4, 2, 16, 16, 14)
                },
                Direction.EAST, new VoxelShape[]{
                        Block.createCuboidShape(0, 4, 4, 3, 12, 12),
                        Block.createCuboidShape(0, 5, 5.5, 5, 11, 10.5),
                        Block.createCuboidShape(0, 5, 5, 7, 12, 11),
                        Block.createCuboidShape(0, 5, 4, 10, 14, 12),
                        Block.createCuboidShape(0, 4, 2, 12, 16, 14)
                }
        ));
    }

    @Override
    public boolean isFertilizable(WorldView world, BlockPos pos, BlockState state) {
        return state.get(AGE) <= MAX_AGE;
    }

    @Override
    public boolean canGrow(World world, Random random, BlockPos pos, BlockState state) {
        return true;
    }

    @Override
    public void grow(ServerWorld world, Random random, BlockPos pos, BlockState state) {
        var currentAge = state.get(AGE);
        if (currentAge == MAX_AGE) {
            tryPlaceNewLanterns(world, random, pos);
            return;
        }

        if (random.nextBoolean())
            return;

        world.setBlockState(pos, state.with(AGE, currentAge + 1));
    }

    private void tryPlaceNewLanterns(ServerWorld world, Random random, BlockPos centerPos) {
        var poses = BlockPos.iterateRandomly(random, 27, centerPos, 1);
        for (val pos : poses) {
            if (pos.equals(centerPos) || !world.isAir(pos))
                continue;

            if (tryPlaceLantern(world, pos))
                return;
        }
    }

    private boolean tryPlaceLantern(ServerWorld world, BlockPos pos) {
        for (var i = 0; i < 4; i++) {
            var direction = Direction.fromHorizontal(i);
            if (!canPlaceAt(world, pos, direction))
                continue;

            world.setBlockState(pos, getDefaultState().with(AGE, 0).with(FACING, direction));
            return true;
        }

        return false;
    }
}
