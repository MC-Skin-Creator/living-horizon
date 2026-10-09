package fr.clixmods.livinghorizon.platform;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

import java.util.function.Consumer;

//? if fabric || quilt {
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
//?} elif neoforge {
/*import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
*///?} elif >=1.20.2 {
/*import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.ChannelBuilder;
import net.minecraftforge.network.EventNetworkChannel;
import net.minecraftforge.network.PacketDistributor;
*///?} else {
/*import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundCustomPayloadPacket;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.event.EventNetworkChannel;
*///?}
//? if (fabric || quilt || neoforge) && >=1.20.5 {
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
//?} elif fabric || quilt {
/*import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.network.FriendlyByteBuf;
*///?}

/**
 * The mod's one channel, from a server running it to the clients running it. What travels
 * on it is a plain array of bytes, written and read by {@code share/Protocol}: the loaders
 * only carry it, each in its own way, and this is the only place that knows those ways.
 *
 * <p>The channel is optional both ways. A vanilla client joins a server running the mod, and
 * a client with the mod joins a vanilla server: neither ever sees the channel. Nothing goes
 * from a client to the server.
 *
 * <p>Common code: nothing here may touch a class that only exists on the client. The
 * client tells {@link #listen} what to do with what arrives; Fabric and Quilt register
 * their client receiver in {@code Events}, where the client classes are.
 */
public final class Network {
    public static final Identifier CHANNEL = Identifier.fromNamespaceAndPath("livinghorizon", "sync");

    private static volatile Consumer<byte[]> receiver = data -> { };

    private Network() {
    }

    //? if (fabric || quilt || neoforge) && >=1.20.5 {
    /** What the channel carries from 1.20.5, where a custom payload is a registered type. */
    public record Payload(byte[] data) implements CustomPacketPayload {
        public static final Type<Payload> TYPE = new Type<>(CHANNEL);
        static final StreamCodec<FriendlyByteBuf, Payload> CODEC = StreamCodec.of(
                (buf, payload) -> buf.writeByteArray(payload.data), buf -> new Payload(buf.readByteArray()));

        @Override
        public Type<Payload> type() {
            return TYPE;
        }
    }
    //?}

    //? if forge {
    /*private static EventNetworkChannel channel;
    *///?}

    /** What the client does with a message from the server, on the game's thread. */
    public static void listen(Consumer<byte[]> listener) {
        receiver = listener;
    }

    /** Hands a message over to the client. Called on the game's thread. */
    public static void received(byte[] data) {
        receiver.accept(data);
    }

    //? if neoforge {
    /*/^* NeoForge registers payloads on the mod's bus, once it has handed it over. ^/
    public static void register(IEventBus modBus) {
        modBus.addListener(RegisterPayloadHandlersEvent.class, event -> event.registrar("1").optional()
                .playToClient(Payload.TYPE, Payload.CODEC, (payload, context) -> received(payload.data())));
    }
    *///?} else {
    /** Declares the channel, on both sides, while the mod is being set up. */
    public static void register() {
        //? if (fabric || quilt) && >=26.1 {
        /*net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.clientboundPlay().register(Payload.TYPE, Payload.CODEC);
        *///?} elif (fabric || quilt) && >=1.20.5 {
        net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.playS2C().register(Payload.TYPE, Payload.CODEC);
        //?} elif forge && >=1.20.2 {
        /*channel = ChannelBuilder.named(CHANNEL).optional().eventNetworkChannel();
        channel.addListener(event -> {
            FriendlyByteBuf payload = event.getPayload();
            if (payload != null && event.getSource().isClientSide()) {
                byte[] data = bytes(payload);
                event.getSource().enqueueWork(() -> received(data));
            }
            event.getSource().setPacketHandled(true);
        });
        *///?} elif forge {
        /*// Accepted when the other side has the same version, or no mod at all.
        channel = NetworkRegistry.newEventChannel(CHANNEL, () -> "1",
                NetworkRegistry.acceptMissingOr("1"), NetworkRegistry.acceptMissingOr("1"));
        // Fired on the client for what the server sends.
        channel.addListener((NetworkEvent.ServerCustomPayloadEvent event) -> {
            byte[] data = bytes(event.getPayload());
            event.getSource().get().enqueueWork(() -> received(data));
            event.getSource().get().setPacketHandled(true);
        });
        *///?}
    }
    //?}

    /** Whether this player's client listens on the channel: whether it runs the mod. */
    public static boolean canSend(ServerPlayer player) {
        //? if (fabric || quilt) && >=1.20.5 {
        return ServerPlayNetworking.canSend(player, Payload.TYPE);
        //?} elif fabric || quilt {
        /*return ServerPlayNetworking.canSend(player, CHANNEL);
        *///?} elif neoforge {
        /*return player.connection.hasChannel(Payload.TYPE);
        *///?} elif >=1.20.2 {
        /*return channel.isRemotePresent(player.connection.getConnection());
        *///?} else {
        /*return channel.isRemotePresent(player.connection.connection);
        *///?}
    }

    /** Sends one message to one player, who must {@link #canSend listen}. */
    public static void send(ServerPlayer player, byte[] data) {
        //? if (fabric || quilt) && >=1.20.5 {
        ServerPlayNetworking.send(player, new Payload(data));
        //?} elif fabric || quilt {
        /*FriendlyByteBuf buf = PacketByteBufs.create();
        buf.writeBytes(data);
        ServerPlayNetworking.send(player, CHANNEL, buf);
        *///?} elif neoforge {
        /*PacketDistributor.sendToPlayer(player, new Payload(data));
        *///?} elif >=1.20.2 {
        /*FriendlyByteBuf buf = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer(data.length));
        buf.writeBytes(data);
        channel.send(buf, PacketDistributor.PLAYER.with(player));
        *///?} else {
        /*player.connection.send(new ClientboundCustomPayloadPacket(CHANNEL, new FriendlyByteBuf(Unpooled.wrappedBuffer(data))));
        *///?}
    }

    //? if forge {
    /*private static byte[] bytes(FriendlyByteBuf payload) {
        byte[] data = new byte[payload.readableBytes()];
        payload.readBytes(data);
        return data;
    }
    *///?}
}
