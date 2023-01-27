package pl.skidam.automodpack.networking.fabric;

import com.mojang.authlib.GameProfile;
import net.fabricmc.fabric.api.client.networking.v1.ClientLoginNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.s2c.login.LoginDisconnectS2CPacket;
import net.minecraft.text.Text;
import net.minecraft.util.Util;
import pl.skidam.automodpack.AutoModpack;
import pl.skidam.automodpack.Platform;
import pl.skidam.automodpack.mixin.ServerLoginNetworkHandlerAccessor;
import pl.skidam.automodpack.networking.packet.LinkC2SPacket;
import pl.skidam.automodpack.networking.packet.LinkS2CPacket;
import pl.skidam.automodpack.networking.packet.LoginC2SPacket;
import pl.skidam.automodpack.networking.packet.LoginS2CPacket;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.FutureTask;

import static pl.skidam.automodpack.networking.ModPackets.HANDSHAKE;
import static pl.skidam.automodpack.networking.ModPackets.LINK;

public class ModPacketsImpl {

    public static Map<UUID, Boolean> acceptLogin = new HashMap<>();


    public static void registerC2SPackets() {
        // Client
        ClientLoginNetworking.registerGlobalReceiver(HANDSHAKE, LoginC2SPacket::receive);
        ClientPlayNetworking.registerGlobalReceiver(HANDSHAKE, LoginC2SPacket::receive);
        ClientLoginNetworking.registerGlobalReceiver(LINK, LinkC2SPacket::receive);
        ClientPlayNetworking.registerGlobalReceiver(LINK, LinkC2SPacket::receive);
    }

    public static void registerS2CPackets() {
        // Server

        // Packets
        if (!AutoModpack.serverConfig.velocitySupport) {
            ServerLoginNetworking.registerGlobalReceiver(HANDSHAKE, LoginS2CPacket::receive);
            ServerLoginNetworking.registerGlobalReceiver(LINK, LinkS2CPacket::receive);

            ServerLoginConnectionEvents.QUERY_START.register((handler, server, sender, sync) -> {

                GameProfile profile = ((ServerLoginNetworkHandlerAccessor) handler).getGameProfile();
                UUID uniqueId = profile.getId();

                FutureTask<?> future = new FutureTask<>(() -> {
                    for (int i = 0; i <= 301; i++) {
                        Thread.sleep(50);
//                        AutoModpack.LOGGER.error("Delaying login for " + profile.getName() + " (" + uniqueId + ") for " + i + "ms");

                        if (acceptLogin.containsKey(uniqueId)) {
                            if (!acceptLogin.get(uniqueId)) {
//                                AutoModpack.LOGGER.error("Disconnecting login for " + profile.getName() + " (" + uniqueId + ")");
                                Text reason = Text.literal("Modpack is not the same as on server");
                                handler.connection.send(new LoginDisconnectS2CPacket(reason));
                                handler.connection.disconnect(reason);
                            }
//                            AutoModpack.LOGGER.error("Login for " + profile.getName() + " (" + uniqueId + ") accepted");
                            acceptLogin.remove(uniqueId);
                            break;
                        }

                        if (i == 300) {
                            AutoModpack.LOGGER.error("Timeout login for " + profile.getName() + " (" + uniqueId + ")");
                            Text reason = Text.literal("AutoModpack - timeout");
                            handler.connection.send(new LoginDisconnectS2CPacket(reason));
                            handler.connection.disconnect(reason);
                        }
                    }

                    return null;
                });

                // Execute the task on a worker thread as not to block the server thread
                Util.getMainWorkerExecutor().execute(future);

                PacketByteBuf buf = PacketByteBufs.create();
                String correctResponse = AutoModpack.VERSION + "-" + Platform.getPlatformType().toString().toLowerCase();
                buf.writeString(correctResponse);
                sender.sendPacket(HANDSHAKE, buf);

                sync.waitFor(future);
            });



        }

        // For velocity support, velocity doest support login packets
        if (AutoModpack.serverConfig.velocitySupport)  {
            ServerPlayNetworking.registerGlobalReceiver(HANDSHAKE, LoginS2CPacket::receive);
            ServerPlayNetworking.registerGlobalReceiver(LINK, LinkS2CPacket::receive);

            ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
                PacketByteBuf buf = PacketByteBufs.create();
                String correctResponse = AutoModpack.VERSION + "-" + Platform.getPlatformType().toString().toLowerCase();
                buf.writeString(correctResponse);
                sender.sendPacket(HANDSHAKE, buf);
            });
        }
    }
}