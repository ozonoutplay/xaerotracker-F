package info.infinf.xaerotracker.mixin;

import info.infinf.xaerotracker.XaeroTrackerMod;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayer.class)
public abstract class PlayerMoveMixin {

    private double xaerotracker_lastX = Double.NaN;
    private double xaerotracker_lastY = Double.NaN;
    private double xaerotracker_lastZ = Double.NaN;

    @Inject(method = "tick", at = @At("TAIL"))
    private void onTick(CallbackInfo ci) {
        ServerPlayer self = (ServerPlayer)(Object)this;
        double x = self.getX(), y = self.getY(), z = self.getZ();
        if (x != xaerotracker_lastX || y != xaerotracker_lastY || z != xaerotracker_lastZ) {
            xaerotracker_lastX = x; xaerotracker_lastY = y; xaerotracker_lastZ = z;
            if (XaeroTrackerMod.INSTANCE != null) XaeroTrackerMod.INSTANCE.onPlayerMoved(self);
        }
    }
}
