package me.landon.companion.session;

import java.util.Locale;
import me.landon.companion.config.CompanionConfig;
import me.landon.companion.protocol.ProtocolConstants;
import me.landon.companion.protocol.ProtocolMessage;

public final class ConnectionGateState {
    public enum EnableResult {
        ENABLED(true),
        ALREADY_ENABLED(true),
        PROTOCOL_MISMATCH(false),
        SERVER_NOT_ALLOWED(false),
        SIGNATURE_INVALID(false);

        private final boolean enabled;

        EnableResult(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean enabled() {
            return enabled;
        }
    }

    @FunctionalInterface
    public interface SignatureCheck {
        boolean verify(ProtocolMessage.ServerHelloS2C serverHello, CompanionConfig config);
    }

    private boolean enabled;

    public boolean isEnabled() {
        return enabled;
    }

    public void reset() {
        enabled = false;
    }

    public void enableTrustedSession() {
        enabled = true;
    }

    public boolean shouldProcessIncoming(ProtocolMessage message) {
        if (enabled) {
            return true;
        }

        return message instanceof ProtocolMessage.ServerHelloS2C;
    }

    public EnableResult tryEnable(
            int protocolVersion,
            ProtocolMessage.ServerHelloS2C serverHello,
            CompanionConfig config,
            SignatureCheck signatureCheck) {
        if (enabled) {
            return EnableResult.ALREADY_ENABLED;
        }

        if (protocolVersion != ProtocolConstants.PROTOCOL_VERSION) {
            return EnableResult.PROTOCOL_MISMATCH;
        }

        String serverId = normalizeServerId(serverHello.serverId());
        boolean serverAllowed =
                config.allowedServerIds.stream()
                        .map(ConnectionGateState::normalizeServerId)
                        .anyMatch(serverId::equals);

        if (!serverAllowed) {
            return EnableResult.SERVER_NOT_ALLOWED;
        }

        if (!signatureCheck.verify(serverHello, config)) {
            return EnableResult.SIGNATURE_INVALID;
        }

        enabled = true;
        return EnableResult.ENABLED;
    }

    private static String normalizeServerId(String value) {
        if (value == null) {
            return "";
        }

        return value.trim().toLowerCase(Locale.ROOT);
    }
}
