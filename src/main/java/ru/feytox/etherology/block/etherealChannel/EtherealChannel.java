package ru.feytox.etherology.block.etherealChannel;

import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.fluid.FluidState;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.util.Hand;
import net.minecraft.util.ItemActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.minecraft.world.WorldAccess;
import org.jetbrains.annotations.Nullable;
import ru.feytox.etherology.enums.PipeSide;
import ru.feytox.etherology.magic.ether.EtherStorage;
import ru.feytox.etherology.registry.block.EBlocks;
import ru.feytox.etherology.util.misc.RegistrableBlock;

import java.util.stream.Stream;

import static net.minecraft.state.property.Properties.WATERLOGGED;
import static ru.feytox.etherology.registry.block.EBlocks.ETHEREAL_CHANNEL_BLOCK_ENTITY;

public class EtherealChannel extends Block implements RegistrableBlock, BlockEntityProvider, Waterloggable {
    public static final BooleanProperty ACTIVATED = BooleanProperty.of("activated");
    public static final DirectionProperty FACING = FacingBlock.FACING;
    public static final BooleanProperty IN_CASE = BooleanProperty.of("in_case");
    public static final BooleanProperty IS_CROSS = BooleanProperty.of("is_cross");

    public static final VoxelShape CENTER_SHAPE;

    public EtherealChannel() {
        super(Settings.create().mapColor(MapColor.BROWN).strength(1.0f).nonOpaque().solid());

        var defaultState = getDefaultState()
                .with(ACTIVATED, false)
                .with(FACING, Direction.NORTH)
                .with(IN_CASE, false)
                .with(IS_CROSS, false)
                .with(WATERLOGGED, false);
        setDefaultState(ChannelShapes.addSidesToState(defaultState));
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(FACING, ACTIVATED, IN_CASE, IS_CROSS, ChannelShapes.NORTH, ChannelShapes.SOUTH, ChannelShapes.WEST, ChannelShapes.EAST, ChannelShapes.UP, ChannelShapes.DOWN, WATERLOGGED);
    }

    @Override
    protected ItemActionResult onUseWithItem(ItemStack stack, BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (state.get(IN_CASE)) return super.onUseWithItem(stack, state, world, pos, player, hand, hit);
        if (!stack.isOf(EBlocks.CHANNEL_CASE.getItem()))
            return super.onUseWithItem(stack, state, world, pos, player, hand, hit);

        if (!player.isCreative()) stack.decrement(1);
        world.setBlockState(pos, state.with(IN_CASE, true));
        world.playSound(null, pos, SoundEvents.BLOCK_WOOD_BREAK, SoundCategory.BLOCKS, 1.0f, 1.0f);

        return ItemActionResult.SUCCESS;
    }

    @Override
    public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        if (state.get(IN_CASE)) return VoxelShapes.fullCube();
        return ChannelShapes.getShape(CENTER_SHAPE, state);
    }

    @Nullable
    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        var playerDirection = ctx.getPlayerLookDirection();
        var playerPlacementState = getDefaultState().with(FACING, playerDirection);

        return getChannelState(ctx.getWorld(), playerPlacementState, ctx.getBlockPos());
    }

    @Override
    public BlockState getStateForNeighborUpdate(BlockState state, Direction direction, BlockState neighborState, WorldAccess world, BlockPos pos, BlockPos neighborPos) {
        if (state.get(WATERLOGGED))
            world.scheduleFluidTick(pos, Fluids.WATER, Fluids.WATER.getTickRate(world));
        return super.getStateForNeighborUpdate(state, direction, neighborState, world, pos, neighborPos);
    }

    @Override
    public FluidState getFluidState(BlockState state) {
        return state.get(WATERLOGGED) ? Fluids.WATER.getStill(false) : super.getFluidState(state);
    }

    public BlockState getChannelState(BlockView world, BlockState state, BlockPos pos) {
        var pipeDirection = state.get(FACING);
        var inCase = state.get(IN_CASE);
        var fluid = world.getFluidState(pos).getFluid();
        state = getDefaultState()
                .with(FACING, pipeDirection)
                .with(IN_CASE, inCase)
                .with(WATERLOGGED, fluid == Fluids.WATER);

        var inputSides = Stream.concat(Direction.Type.HORIZONTAL.stream(), Direction.Type.VERTICAL.stream())
                .filter(direction -> isNeighborOutput(world, pos, direction) && !pipeDirection.equals(direction))
                .map(Direction::getOpposite)
                .map(EtherealChannel::getAsIn)
                .toList();

        for (var inputSide : inputSides)
            state = state.with(inputSide, PipeSide.IN);

        return getFacingState(state, inputSides.size());
    }

    public boolean isNeighborOutput(BlockView world, BlockPos pos, Direction direction) {
        var checkPos = pos.add(direction.getVector());
        if (world.getBlockState(checkPos).isOf(Blocks.LEVER))
            return true;
        if (!(world.getBlockEntity(checkPos) instanceof EtherStorage storage))
            return false;

        return storage.isOutputSide(direction.getOpposite());
    }

    public BlockState getFacingState(BlockState state, int inputCount) {
        Direction pipeDirection = state.get(FACING);
        EnumProperty<PipeSide> outSide = getAsOut(pipeDirection);
        BlockState blockState = state.with(outSide, PipeSide.OUT);

        EnumProperty<PipeSide> inSide = getAsIn(pipeDirection);
        if (inputCount == 0) {
            blockState = blockState.with(inSide, PipeSide.IN);
        } else if (inputCount > 1 || !blockState.get(inSide).isInput()) {
            blockState = blockState.with(IS_CROSS, true);
        }

        return blockState;
    }

    public static EnumProperty<PipeSide> getAsIn(Direction direction) {
        switch (direction) {
            case SOUTH -> {
                return ChannelShapes.NORTH;
            }
            case WEST -> {
                return ChannelShapes.EAST;
            }
            case EAST -> {
                return ChannelShapes.WEST;
            }
            case DOWN -> {
                return ChannelShapes.UP;
            }
            case UP -> {
                return ChannelShapes.DOWN;
            }
            default -> {
                return ChannelShapes.SOUTH;
            }
        }
    }

    // TODO: 03.03.2024 rename below and above because they are confusing
    // TODO: 27.10.2024 and combine in one method

    public static EnumProperty<PipeSide> getAsOut(Direction direction) {
        switch (direction) {
            case SOUTH -> {
                return ChannelShapes.SOUTH;
            }
            case WEST -> {
                return ChannelShapes.WEST;
            }
            case EAST -> {
                return ChannelShapes.EAST;
            }
            case DOWN -> {
                return ChannelShapes.DOWN;
            }
            case UP -> {
                return ChannelShapes.UP;
            }
            default -> {
                return ChannelShapes.NORTH;
            }
        }
    }

    @Override
    public void neighborUpdate(BlockState state, World world, BlockPos pos, Block sourceBlock, BlockPos sourcePos, boolean notify) {
        var power = world.getReceivedStrongRedstonePower(pos);
        var powered = power > 0;
        world.setBlockState(pos, getChannelState(world, state, pos).with(ACTIVATED, powered));
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
        if (type != ETHEREAL_CHANNEL_BLOCK_ENTITY) return null;

        return world.isClient ? EtherealChannelBlockEntity::clientTicker : EtherealChannelBlockEntity::serverTicker;
    }

    @Override
    public String getBlockId() {
        return "ethereal_channel";
    }

    @Nullable
    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new EtherealChannelBlockEntity(pos, state);
    }

    static {
        CENTER_SHAPE = createCuboidShape(5, 5, 5, 11, 11, 11);

    }
}
