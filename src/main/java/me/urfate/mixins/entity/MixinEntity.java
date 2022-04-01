package me.urfate.mixins.entity;

import com.lambda.client.event.LambdaEventBus;
import me.urfate.event.events.SafewalkEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = Entity.class, priority = Integer.MAX_VALUE)
public abstract class MixinEntity {


    @Redirect(method = "move", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity;isSneaking()Z"))
    public boolean move_isSneaking(Entity instance) {

        if (instance != Minecraft.getMinecraft().player)
            return instance.isSneaking();

        // allows one to set SafeWalking to true or false
        SafewalkEvent event = new SafewalkEvent(Minecraft.getMinecraft().player.isSneaking());
        LambdaEventBus.INSTANCE.post(event);

        return event.getSneak();
    }
}
