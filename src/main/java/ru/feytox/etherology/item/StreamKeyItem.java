package ru.feytox.etherology.item;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.item.ToolItem;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.ActionResult;
import net.minecraft.util.ItemScatterer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.jetbrains.annotations.Nullable;
import ru.feytox.etherology.block.etherealChannel.EtherealChannel;
import ru.feytox.etherology.registry.block.EBlocks;
import ru.feytox.etherology.registry.misc.EtherSounds;
import ru.feytox.etherology.registry.misc.EtherToolMaterials;

public class StreamKeyItem extends ToolItem {

    public StreamKeyItem() {
        super(EtherToolMaterials.EBONY, new Settings());
    }

    @Override
    public ActionResult useOnBlock(ItemUsageContext context) {
        var result = tryUseOnChannel(context);
        if (result == null) return super.useOnBlock(context);

        var random = context.getWorld().getRandom();
        context.getWorld().playSound(null, context.getBlockPos(), EtherSounds.RATCHET, SoundCategory.BLOCKS, 0.8f, random.nextFloat()*0.2f+0.9f);
        return result;
    }

    @Nullable
    private ActionResult tryUseOnChannel(ItemUsageContext context) {
        if (!(context.getWorld() instanceof ServerWorld world)) return null;

        var pos = context.getBlockPos();
        var state = world.getBlockState(pos);
        if (!(state.getBlock() instanceof EtherealChannel)) return null;

        var player = context.getPlayer();
        var isPlayer = player != null;
        var isVertical = isPlayer && player.isSneaking();
        var stack = context.getStack();

        if (state.get(EtherealChannel.IN_CASE))
            removeChannelCase(context, world, state, pos);
        else
            rotateChannel(context, world, state, isVertical, pos);

        var hand = context.getHand();
        if (isPlayer) stack.damage(1, player, PlayerEntity.getSlotForHand(hand));
        else stack.damage(1, world, null, item -> {});

        return ActionResult.SUCCESS;
    }

    private static void removeChannelCase(ItemUsageContext context, ServerWorld world, BlockState state, BlockPos pos) {
        state = state.with(EtherealChannel.IN_CASE, false);
        world.setBlockState(pos, state, Block.NOTIFY_ALL);

        var itemPos = pos.add(context.getSide().getVector());
        ItemScatterer.spawn(world, itemPos.getX(), itemPos.getY(), itemPos.getZ(), EBlocks.CHANNEL_CASE.asItem().getDefaultStack());
    }

    private static void rotateChannel(ItemUsageContext context, ServerWorld world, BlockState state, boolean isVertical, BlockPos pos) {
        var pipeFacing = state.get(EtherealChannel.FACING);
        var playerFacing = context.getHorizontalPlayerFacing();
        if (isVertical) {
            pipeFacing = pipeFacing.getOffsetY() == 0 ? Direction.UP : pipeFacing.getOpposite();
        }
        else {
            pipeFacing = pipeFacing.getOffsetY() == 0 ? pipeFacing.rotateYClockwise() : playerFacing;
        }

        state = EBlocks.ETHEREAL_CHANNEL.getChannelState(world, state.with(EtherealChannel.FACING, pipeFacing), pos);
        world.setBlockState(pos, state, Block.NOTIFY_ALL);
    }
}
