package ru.feytox.etherology.block.etherealChannel;

import it.unimi.dsi.fastutil.Pair;
import net.minecraft.block.BlockState;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.util.function.BooleanBiFunction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import org.jetbrains.annotations.Nullable;
import ru.feytox.etherology.block.etherealFork.EtherealForkBlock;
import ru.feytox.etherology.enums.PipeSide;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static net.minecraft.block.Block.createCuboidShape;

public class ChannelShapes {


    public static final EnumProperty<PipeSide> NORTH = EnumProperty.of("north", PipeSide.class);
    public static final EnumProperty<PipeSide> EAST = EnumProperty.of("east", PipeSide.class);
    public static final EnumProperty<PipeSide> WEST = EnumProperty.of("west", PipeSide.class);
    public static final EnumProperty<PipeSide> SOUTH = EnumProperty.of("south", PipeSide.class);
    public static final EnumProperty<PipeSide> UP = EnumProperty.of("up", PipeSide.class);
    public static final EnumProperty<PipeSide> DOWN = EnumProperty.of("down", PipeSide.class);

    public static final VoxelShape NORTH_SHAPE;
    public static final VoxelShape SOUTH_SHAPE;
    public static final VoxelShape EAST_SHAPE;
    public static final VoxelShape WEST_SHAPE;
    public static final VoxelShape DOWN_SHAPE;
    public static final VoxelShape UP_SHAPE;

    private static final List<EnumProperty<PipeSide>> SIDES;
    private static final Map<EnumProperty<PipeSide>, VoxelShape> SIDE_TO_SHAPE_MAP;
    private static final CompletableFuture<Map<OutlineKey, VoxelShape>> FUTURE_SHAPE_CACHES;

    @Nullable
    private static Map<OutlineKey, VoxelShape> shapeCaches = null;

    public static void cacheAll() {
    }

    public static VoxelShape getShape(VoxelShape baseShape, BlockState state) {
        var caches = getCaches();
        return caches.get(new OutlineKey(baseShape, ShapeKey.of(state)));
    }

    private static Map<OutlineKey, VoxelShape> getCaches() {
        if (shapeCaches != null)
            return shapeCaches;

        try {
            shapeCaches = FUTURE_SHAPE_CACHES.get();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        return shapeCaches;
    }

    private static CompletableFuture<Map<OutlineKey, VoxelShape>> cacheShapes(VoxelShape... baseShapes) {
        var combinations = generateCombinations();

        var streamOfFutures = combinations.map(ChannelShapes::generateShape)
                .flatMap(future -> Arrays.stream(baseShapes).map(baseShape ->
                        future.thenApplyAsync(keyToShape -> {
                            var key = new OutlineKey(baseShape, keyToShape.key());
                            var shape = VoxelShapes.combineAndSimplify(baseShape, keyToShape.value(), BooleanBiFunction.OR);
                            return Pair.of(key, shape);
                        })));

        return convert(streamOfFutures).get()
                .thenApplyAsync(stream -> stream.collect(Collectors.toUnmodifiableMap(Pair::left, Pair::right)));
    }

    private static Stream<ShapeKey> generateCombinations() {
        var totalCombinations = 1 << SIDES.size();
        return IntStream.range(0, totalCombinations).mapToObj(ShapeKey::new);
    }

    private static CompletableFuture<Pair<ShapeKey, VoxelShape>> generateShape(ShapeKey shapeKey) {
        return CompletableFuture.supplyAsync(shapeKey::toSides)
                .thenApplyAsync(sides -> sides.map(SIDE_TO_SHAPE_MAP::get))
                .thenApplyAsync(shapes -> shapes.reduce((one, two) -> VoxelShapes.combine(one, two, BooleanBiFunction.OR)))
                .thenApplyAsync(optionalShape -> optionalShape.orElse(VoxelShapes.empty()))
                .thenApplyAsync(shape -> Pair.of(shapeKey, shape));
    }

    private static <T> Optional<CompletableFuture<Stream<T>>> convert(Stream<CompletableFuture<T>> stream) {
        return stream.map(future -> future.thenApplyAsync(Stream::of))
                .reduce((one, two) ->
                        one.thenComposeAsync(dataOne ->
                                two.thenApplyAsync(dataTwo -> Stream.concat(dataOne, dataTwo))));
    }

    public static BlockState addSidesToState(BlockState state) {
        for (var side : SIDES) {
            state = state.with(side, PipeSide.EMPTY);
        }

        return state;
    }

    static {
        NORTH_SHAPE = createCuboidShape(5, 5, 0, 11, 11, 5);
        SOUTH_SHAPE = createCuboidShape(5, 5, 11, 11, 11, 16);
        WEST_SHAPE = createCuboidShape(0, 5, 5, 5, 11, 11);
        EAST_SHAPE = createCuboidShape(11, 5, 5, 16, 11, 11);
        UP_SHAPE = createCuboidShape(5, 11, 5, 11, 16, 11);
        DOWN_SHAPE = createCuboidShape(5, 0, 5, 11, 5, 11);

        SIDES = List.of(NORTH, SOUTH, EAST, WEST, DOWN, UP);
        SIDE_TO_SHAPE_MAP = Map.of(NORTH, NORTH_SHAPE, SOUTH, SOUTH_SHAPE, EAST, EAST_SHAPE, WEST, WEST_SHAPE, DOWN, DOWN_SHAPE, UP, UP_SHAPE);
        FUTURE_SHAPE_CACHES = cacheShapes(EtherealChannel.CENTER_SHAPE, EtherealForkBlock.CENTER_SHAPE);
    }

    private record ShapeKey(int key) {

        public static ShapeKey of(BlockState state) {
            var key = 0;
            for (int i = 0; i < SIDES.size(); i++) {
                var side = SIDES.get(i);
                if (state.get(side).isEmpty())
                    continue;
                key += 1 << i;
            }
            return new ShapeKey(key);
        }

        public Stream<EnumProperty<PipeSide>> toSides() {
            return IntStream.range(0, SIDES.size())
                    .mapToObj(i -> (key & (1 << i)) != 0 ? SIDES.get(i) : null)
                    .filter(Objects::nonNull);
        }
    }

    private record OutlineKey(VoxelShape baseShape, ShapeKey shapeKey) {
    }
}
