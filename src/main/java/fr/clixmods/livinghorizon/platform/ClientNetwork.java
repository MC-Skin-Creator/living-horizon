package fr.clixmods.livinghorizon.platform;

//? if fabric || quilt {
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
//?}

/**
 * The client's side of {@link Network} on Fabric and Quilt, kept apart because the Fabric
 * API's client networking names classes a dedicated server has not. NeoForge and Forge listen
 * on both sides from {@link Network} itself.
 */
public final class ClientNetwork {
    private ClientNetwork() {
    }

    /** From the client's entry point. */
    public static void register() {
        //? if fabric || quilt {
        //? if >=1.20.5 {
        ClientPlayNetworking.registerGlobalReceiver(Network.Payload.TYPE, (payload, context) -> Network.receive(payload.data()));
        //?} else
        /*ClientPlayNetworking.registerGlobalReceiver(Network.ID, (client, handler, buf, sender) -> Network.receive(buf.readByteArray()));*/
        //?}
    }
}
