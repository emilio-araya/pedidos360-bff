package cl.pedidos360.bff.gateway;

public class GatewayUnavailableException extends GatewayTransportException {

    public GatewayUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
