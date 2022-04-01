package me.urfate.util.world

import com.lambda.client.event.SafeClientEvent
import com.lambda.client.manager.managers.HotbarManager.serverSideItem
import com.lambda.client.util.world.PlaceInfo
import com.lambda.client.util.world.isPlaceable
import net.minecraft.item.ItemBlock
import net.minecraft.network.play.client.CPacketAnimation
import net.minecraft.network.play.client.CPacketPlayerTryUseItemOnBlock
import net.minecraft.util.EnumHand
import net.minecraft.util.SoundCategory

fun SafeClientEvent.placeBlock(
    placeInfo: PlaceInfo,
    hand: EnumHand = EnumHand.MAIN_HAND,
    silent: Boolean = false,
    swing: Boolean = true
) {
    if (!world.isPlaceable(placeInfo.placedPos)) return

    connection.sendPacket(placeInfo.toPlacePacket(hand))

    if (swing)
        player.swingArm(hand)
    else
        connection.sendPacket(CPacketAnimation(hand))

    if (silent)
        return

    val itemStack = player.serverSideItem
    val block = (itemStack.item as? ItemBlock?)?.block ?: return
    val metaData = itemStack.metadata
    val blockState = block.getStateForPlacement(world, placeInfo.pos, placeInfo.side, placeInfo.hitVecOffset.x.toFloat(), placeInfo.hitVecOffset.y.toFloat(), placeInfo.hitVecOffset.z.toFloat(), metaData, player, EnumHand.MAIN_HAND)
    val soundType = blockState.block.getSoundType(blockState, world, placeInfo.pos, player)
    world.playSound(player, placeInfo.pos, soundType.placeSound, SoundCategory.BLOCKS, (soundType.getVolume() + 1.0f) / 2.0f, soundType.getPitch() * 0.8f)
}

private fun PlaceInfo.toPlacePacket(hand: EnumHand) =
    CPacketPlayerTryUseItemOnBlock(this.pos, this.side, hand, hitVecOffset.x.toFloat(), hitVecOffset.y.toFloat(), hitVecOffset.z.toFloat())