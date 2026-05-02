package md.redstone.mixin;

import md.redstone.Mossy;
import md.redstone.netty.MossyDebug;
import net.minecraft.network.Connection;
//? if >=1.20.5
import net.minecraft.network.DisconnectionDetails;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Connection.class)
public abstract class ConnectionMixin {
    @Unique
    private boolean mossy$disconnectLogged;

    @Inject(method = "channelActive", at = @At("TAIL"))
    private void mossy$onChannelActive(CallbackInfo ci) {
        Connection connection = (Connection) (Object) this;
        if (MossyDebug.isMossyConnection(connection)) {
            MossyDebug.recordEvent("Channel active " + MossyDebug.describeAddress(connection.getRemoteAddress()));
            Mossy.LOGGER.info("MOSS channel active: {}", MossyDebug.describeAddress(connection.getRemoteAddress()));
        }
    }

    @Inject(method = "channelInactive", at = @At("HEAD"))
    private void mossy$onChannelInactive(CallbackInfo ci) {
        Connection connection = (Connection) (Object) this;
        if (MossyDebug.isMossyConnection(connection)) {
            MossyDebug.recordEvent("Channel inactive " + MossyDebug.describeAddress(connection.getRemoteAddress()));
            Mossy.LOGGER.info("MOSS channel inactive: {}", MossyDebug.describeAddress(connection.getRemoteAddress()));
        }
    }

    @Inject(method = "setupCompression", at = @At("HEAD"))
    private void mossy$onSetupCompression(int threshold, boolean validateDecompressed, CallbackInfo ci) {
        Connection connection = (Connection) (Object) this;
        if (MossyDebug.isMossyConnection(connection)) {
            MossyDebug.recordEvent("Compression threshold=" + threshold + " for " + MossyDebug.describeAddress(connection.getRemoteAddress()));
            Mossy.LOGGER.info(
                "MOSS compression configured: threshold={}, validate={}, remote={}",
                threshold,
                validateDecompressed,
                MossyDebug.describeAddress(connection.getRemoteAddress())
            );
        }
    }

    @Inject(method = "disconnect(Lnet/minecraft/network/chat/Component;)V", at = @At("HEAD"))
    private void mossy$onDisconnectComponent(Component reason, CallbackInfo ci) {
        mossy$logDisconnect(reason != null ? reason.getString() : "<null>");
    }

    //? if >=1.20.5 {
    @Inject(method = "disconnect(Lnet/minecraft/network/DisconnectionDetails;)V", at = @At("HEAD"))
    private void mossy$onDisconnectDetails(DisconnectionDetails details, CallbackInfo ci) {
        String reason = details != null && details.reason() != null ? details.reason().getString() : "<null>";
        mossy$logDisconnect(reason);
    }
    //?}

    @Inject(method = "handleDisconnection", at = @At("TAIL"))
    private void mossy$onHandleDisconnection(CallbackInfo ci) {
        if (mossy$disconnectLogged) {
            return;
        }
        Connection connection = (Connection) (Object) this;
        if (!MossyDebug.isMossyConnection(connection)) {
            return;
        }

        //? if >=1.20.5 {
        DisconnectionDetails details = connection.getDisconnectionDetails();
        if (details != null && details.reason() != null) {
            mossy$logDisconnect(details.reason().getString());
        }
        //?}
        //? if <1.20.5
        /*mossy$logDisconnect(MossyDebug.describeAddress(connection.getRemoteAddress()));*/
    }

    @Inject(method = "exceptionCaught", at = @At("HEAD"))
    private void mossy$onExceptionCaught(io.netty.channel.ChannelHandlerContext context, Throwable throwable, CallbackInfo ci) {
        Connection connection = (Connection) (Object) this;
        if (MossyDebug.isMossyConnection(connection)) {
            MossyDebug.recordEvent("Connection exception for " + MossyDebug.describeAddress(connection.getRemoteAddress()));
            Mossy.LOGGER.error(
                "MOSS connection exception for {}",
                MossyDebug.describeAddress(connection.getRemoteAddress()),
                throwable
            );
        }
    }

    @Unique
    private void mossy$logDisconnect(String reason) {
        if (mossy$disconnectLogged) {
            return;
        }

        Connection connection = (Connection) (Object) this;
        if (!MossyDebug.isMossyConnection(connection)) {
            return;
        }

        mossy$disconnectLogged = true;
        MossyDebug.recordEvent("Disconnect " + reason);
        Mossy.LOGGER.warn(
            "MOSS connection disconnect: remote={}, reason={}",
            MossyDebug.describeAddress(connection.getRemoteAddress()),
            reason
        );
    }
}
