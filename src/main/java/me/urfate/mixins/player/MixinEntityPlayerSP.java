package me.urfate.mixins.player;

import com.lambda.client.event.LambdaEventBus;
import me.urfate.event.events.SpoofSneakEvent;
import net.minecraft.client.entity.EntityPlayerSP;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = EntityPlayerSP.class, priority = Integer.MAX_VALUE)
public abstract class MixinEntityPlayerSP {
    @Redirect(method = "onUpdateWalkingPlayer", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/entity/EntityPlayerSP;isSneaking()Z"))
    public boolean isSneaking(EntityPlayerSP instance) {

        SpoofSneakEvent event = new SpoofSneakEvent(instance.isSneaking());
        LambdaEventBus.INSTANCE.post(event);

        return event.getSneaking();

    }
}
