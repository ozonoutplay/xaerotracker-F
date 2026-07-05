package info.infinf.xaerotracker.util;

import io.netty.buffer.ByteBufOutputStream;
import io.netty.buffer.Unpooled;
import net.minecraft.server.level.ServerPlayer;

import java.io.IOException;

public class MessageUtil {

    public static byte[] getLevelIdMessage(int levelId) {
        var buf = Unpooled.buffer(5);
        buf.writeByte(0); buf.writeInt(levelId);
        buf.capacity(buf.writerIndex());
        return buf.array();
    }

    public static byte[] getHandshakeMessage() {
        var buf = Unpooled.buffer(5);
        buf.writeByte(1); buf.writeInt(3);
        buf.capacity(buf.writerIndex());
        return buf.array();
    }

    public static byte[] getTrackPlayerMessage(ServerPlayer player) {
        var buf = Unpooled.buffer(93);
        buf.writeByte(2);
        try (var out = new ByteBufOutputStream(buf)) {
            var uuidArray = UUIDUtil.uuidToIntArray(player.getUUID());
            var pos = player.position();
            // In 26.1, ResourceKey.toString() gives "minecraft:overworld" etc.
            var dimKey = player.level().dimension().toString().replaceAll("ResourceKey\\[.*? / (.*?)\\]", "$1");

            out.writeByte(10);
            out.writeByte(1); out.writeUTF("r"); out.writeByte(0);
            out.writeByte(11); out.writeUTF("i"); out.writeInt(uuidArray.length);
            for (int v : uuidArray) out.writeInt(v);
            out.writeByte(6); out.writeUTF("x"); out.writeDouble(pos.x);
            out.writeByte(6); out.writeUTF("y"); out.writeDouble(pos.y);
            out.writeByte(6); out.writeUTF("z"); out.writeDouble(pos.z);
            out.writeByte(8); out.writeUTF("d"); out.writeUTF(dimKey);
            out.writeByte(0);
        } catch (IOException ignored) {}
        buf.capacity(buf.writerIndex());
        return buf.array();
    }

    public static byte[] getUntrackPlayerMessage(ServerPlayer player) {
        var buf = Unpooled.buffer(31);
        buf.writeByte(2);
        try (var out = new ByteBufOutputStream(buf)) {
            var uuidArray = UUIDUtil.uuidToIntArray(player.getUUID());
            out.writeByte(10);
            out.writeByte(1); out.writeUTF("r"); out.writeByte(1);
            out.writeByte(11); out.writeUTF("i"); out.writeInt(uuidArray.length);
            for (int v : uuidArray) out.writeInt(v);
            out.writeByte(0);
        } catch (IOException ignored) {}
        buf.capacity(buf.writerIndex());
        return buf.array();
    }

    public static byte[] getTrackResetMessage() {
        var buf = Unpooled.buffer(1);
        buf.writeByte(3);
        buf.capacity(buf.writerIndex());
        return buf.array();
    }
}
