package ru.feytox.etherology.client.block.levitator;

import net.minecraft.block.BlockState;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import ru.feytox.etherology.block.levitator.LevitatorBlock;
import ru.feytox.etherology.block.levitator.LevitatorBlockEntity;
import ru.feytox.etherology.client.util.ClientTickableBlock;

public class LevitatorBlockClient extends ClientTickableBlock<LevitatorBlockEntity> {

    public LevitatorBlockClient(LevitatorBlockEntity blockEntity) {
        super(blockEntity);
    }

    @Override
    public void clientTick(ClientWorld world, BlockPos blockPos, BlockState state) {
        if (LevitatorBlock.hasLevitation(state))
            blockEntity.tickLevitation(world, blockPos, state);
    }
}
