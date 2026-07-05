package info.infinf.xaerotracker.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import info.infinf.xaerotracker.XaeroTrackerMod;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public class XaeroTrackerCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher,
                                CommandBuildContext buildContext,
                                Commands.CommandSelection selection) {
        dispatcher.register(
            literal("xt")
                .then(literal("toggleTracked")
                    .executes(ctx -> toggleTracked(ctx, null))
                    .then(argument("player", StringArgumentType.word())
                        .executes(ctx -> toggleTracked(ctx, StringArgumentType.getString(ctx, "player")))
                    )
                )
                .then(literal("toggleTrackEveryone")
                    .executes(ctx -> toggleTrackEveryone(ctx, null))
                    .then(argument("player", StringArgumentType.word())
                        .executes(ctx -> toggleTrackEveryone(ctx, StringArgumentType.getString(ctx, "player")))
                    )
                )
        );
    }

    private static int toggleTracked(CommandContext<CommandSourceStack> ctx, String targetName) {
        var src = ctx.getSource();
        var mod = XaeroTrackerMod.INSTANCE;
        mod.getTrackerThread().submit(() -> {
            if (targetName == null) {
                var self = src.getPlayer();
                if (self == null) { src.sendFailure(Component.literal("Must be run as a player")); return; }
                boolean nowIgnored = mod.getTrackIgnoreList().toggle(self.getName().getString());
                src.sendSuccess(() -> Component.literal(nowIgnored ? "You are now hidden" : "You are now visible"), false);
                var data = mod.getPlayerData().get(self.getUUID());
                if (data != null) mod.track(self, data);
            } else {
                boolean nowIgnored = mod.getTrackIgnoreList().toggle(targetName);
                src.sendSuccess(() -> Component.literal(nowIgnored ? targetName + " is now hidden" : targetName + " is now visible"), true);
                var pl = src.getServer().getPlayerList().getPlayerByName(targetName);
                if (pl != null) { var data = mod.getPlayerData().get(pl.getUUID()); if (data != null) mod.track(pl, data); }
            }
        });
        return 1;
    }

    private static int toggleTrackEveryone(CommandContext<CommandSourceStack> ctx, String targetName) {
        var src = ctx.getSource();
        var mod = XaeroTrackerMod.INSTANCE;
        mod.getTrackerThread().submit(() -> {
            if (targetName == null) {
                var self = src.getPlayer();
                if (self == null) { src.sendFailure(Component.literal("Must be run as a player")); return; }
                boolean canSeeAll = mod.getTrackBypassList().toggle(self.getName().getString());
                src.sendSuccess(() -> Component.literal(canSeeAll ? "You can now see all players" : "Reverted"), false);
                var data = mod.getPlayerData().get(self.getUUID());
                if (canSeeAll) { if (data != null && data.channel != null) mod.trackOthers(self, data.channel); }
                else mod.hideUntracked(self);
            } else {
                boolean canSeeAll = mod.getTrackBypassList().toggle(targetName);
                src.sendSuccess(() -> Component.literal(canSeeAll ? targetName + " can now see all" : "Reverted for " + targetName), true);
                var pl = src.getServer().getPlayerList().getPlayerByName(targetName);
                if (pl != null) {
                    var data = mod.getPlayerData().get(pl.getUUID());
                    if (canSeeAll) { if (data != null && data.channel != null) mod.trackOthers(pl, data.channel); }
                    else mod.hideUntracked(pl);
                }
            }
        });
        return 1;
    }
}
