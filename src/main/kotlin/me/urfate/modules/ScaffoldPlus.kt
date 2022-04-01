package me.urfate.modules

import com.lambda.client.event.SafeClientEvent
import com.lambda.client.manager.managers.HotbarManager.spoofHotbar
import com.lambda.client.manager.managers.PlayerPacketManager.sendPlayerPacket
import com.lambda.client.manager.managers.TimerManager.modifyTimer
import com.lambda.client.module.Category
import com.lambda.client.plugin.api.PluginModule
import com.lambda.client.util.MovementUtils.isMoving
import com.lambda.client.util.items.block
import com.lambda.client.util.math.RotationUtils.getRotationTo
import com.lambda.client.util.math.Vec2f
import com.lambda.client.util.math.VectorUtils.toBlockPos
import com.lambda.client.util.threads.defaultScope
import com.lambda.client.util.threads.safeListener
import com.lambda.client.util.world.getNeighbour
import com.lambda.client.util.world.isReplaceable
import kotlinx.coroutines.launch
import me.urfate.ScaffoldPlusPlugin
import me.urfate.event.events.SafewalkEvent
import me.urfate.event.events.SpoofSneakEvent
import me.urfate.util.world.placeBlock
import net.minecraft.init.Blocks
import net.minecraft.util.EnumFacing
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d
import net.minecraftforge.client.event.InputUpdateEvent
import net.minecraftforge.fml.common.gameevent.TickEvent.ClientTickEvent
import kotlin.math.floor

/**
 * This is a module. First set properties then settings then add listener.
 * **/
internal object ScaffoldPlus : PluginModule(
    name = "Scaffold+",
    category = Category.PLAYER,
    description = "Enhanced scaffold",
    modulePriority = 400,
    pluginMain = ScaffoldPlusPlugin
) {
    private val extend by setting("Extend", 3.0, 0.0..25.0, 0.5)
    private val tower by setting("Tower", true, description = "Go up faster by jumping")
    private val bypass by setting("Bypass", 8, 0..30, 1, { tower }, description = "How many blocks to tower before pausing to bypass NCP")
    private val down by setting("Down", true, description = "Sneak to scaffold downward")
    private val keepY by setting("Keep Y", false, description = "Ensure Y level stays the same unless towering")
    private val timer by setting("Timer Boost", 1.0, 1.0..10.0, 0.1, description = "Use timer when towering")
    private val rotate by setting("Rotate", true, description = "Rotate server side to bypass NCP")
    private val keepRotation by setting("Lock Rotations", true, { rotate } , description = "Bypass some rotation checks")

    private var phase: Int = 0
    private var keptY: Int = 0

    private var oldRot: Vec2f = Vec2f(-1f,-1f)
    var placed: HashMap<BlockPos, Long> = HashMap()

    init {

        onEnable {
            phase = 0
            keptY = floor(mc.player.posY).toInt()
        }

        safeListener<ClientTickEvent> {

            if (mc.player == null || mc.world == null) {
                disable()
                return@safeListener
            }

            if (rotate && keepRotation && (mc.player.isMoving || mc.gameSettings.keyBindJump.isKeyDown)) {
                sendPlayerPacket {
                    rotate(oldRot)
                }
            }

            var slot = -1

            for (i in 0..8) {

                val item = mc.player.inventory.getStackInSlot(i).item
                if (mc.player.inventory.getStackInSlot(i).item.block != Blocks.AIR)
                    if (item.block.defaultState.isFullBlock) {
                        slot = i
                        break
                    }

            }

            if (slot < 0)
                return@safeListener

            // set our position (set extend to 1 if we are jumping as otherwise we may not land on the block)
            var pos = BlockPos(
                mc.player.posX + (mc.player.motionX * (if (!mc.gameSettings.keyBindJump.isKeyDown) extend else 1).toInt()),
                mc.player.posY - 1,
                mc.player.posZ + (mc.player.motionZ * (if (!mc.gameSettings.keyBindJump.isKeyDown) extend else 1).toInt())
            )

            // tower
            if (tower && mc.gameSettings.keyBindJump.isKeyDown &&
                !(
                    mc.gameSettings.keyBindLeft.isKeyDown ||
                        mc.gameSettings.keyBindRight.isKeyDown ||
                        mc.gameSettings.keyBindForward.isKeyDown ||
                        mc.gameSettings.keyBindBack.isKeyDown
                    )) {

                // set our XZ motion to 0
                mc.player.motionX = 0.0
                mc.player.motionZ = 0.0

                // it should be * 3 but * 6 makes it bypass blocks until we return to ground
                if (phase - (bypass * 6) > 0 && bypass != 0) {
                    // pause every few blocks to bypass NCP
                    mc.player.motionY = -1.0
                    phase = -1
                } else if (phase > 0) {

                    if (mc.world.isAirBlock(BlockPos(mc.player.positionVector).down()))
                        mc.player.jump()

                    modifyTimer(50f / timer.toFloat())

                }

                pos = BlockPos(mc.player.positionVector).down()

                phase++

                // when towering we want to update our keptY
                keptY = floor(mc.player.posY).toInt() - 1

            } else {
                phase = 0

                if (keepY)
                    pos = BlockPos(pos.x, keptY, pos.z)

                modifyTimer(50f)

            }

            if (mc.player.onGround) {
                keptY = floor(mc.player.posY).toInt() - 1
            }

            if (mc.gameSettings.keyBindSneak.isKeyDown && down)
                pos = pos.down()

            // if we can't place we get a new position to place in
            if (!canPlace(pos)) {

                for (i in EnumFacing.values()) {

                    if (i == EnumFacing.UP)
                        continue

                    if (canPlace(pos.offset(i))) {
                        pos = pos.offset(i)
                        if (i != EnumFacing.DOWN && keepY)
                            pos = BlockPos(pos.x, keptY, pos.z)
                        break
                    }

                }

            }

            // if we still can't place we try the block underneath us to keep us able to move
            if (!canPlace(pos)) {
                if (!canPlace(BlockPos(mc.player.positionVector.toBlockPos().down())))
                    return@safeListener
                else {
                    pos = mc.player.positionVector.toBlockPos().down()
                    if (keepY)
                        pos = BlockPos(pos.x, keptY, pos.z)
                }

                // if the position is not replaceable, don't bother trying to replace it
            } else if (!mc.world.getBlockState(pos).isReplaceable)
                return@safeListener

            spoofHotbar(slot)
            place(pos)
            placed[pos] = System.currentTimeMillis()

            for (position in placed.keys) {

                val time = placed[position]

                // half second for server to place block for us client side while we just assume it was placed
                if (System.currentTimeMillis() - time!! < 500)
                    placed.remove(position)

            }

            if (rotate) {
                sendPlayerPacket {
                    oldRot = getRotationTo(Vec3d(pos))
                    rotate(oldRot)
                }
            }

        }

        safeListener<SafewalkEvent> {

            it.sneak = !(mc.gameSettings.keyBindSneak.isKeyDown && down)

        }

        safeListener<SpoofSneakEvent> {

            it.sneaking = mc.gameSettings.keyBindSneak.isKeyDown && !down

        }

        safeListener<InputUpdateEvent> {

            if (mc.player.isSneaking && down) {
                it.movementInput.moveStrafe *= 5f
                it.movementInput.moveForward *= 5f
            }

        }

    }

    private fun SafeClientEvent.place(pos: BlockPos) = defaultScope.launch {

        getNeighbour(pos, 1, 100f)?.let {
            placeBlock(
                it,
                silent = true,
                swing = false
            )
        }

    }

    private fun canPlace(pos: BlockPos): Boolean {

        for (i in EnumFacing.values()) {

            if (!mc.world.getBlockState(pos.offset(i)).isReplaceable)
                return true

        }

        return false

    }

}