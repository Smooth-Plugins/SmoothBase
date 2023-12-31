package net.smoothplugins.smoothbase.messenger;

import java.util.UUID;

public interface Messenger {

    /**
     * Send a message to a channel, without expecting a response
     * @param JSON
     */
    void send(String JSON);

    /**
     * Send a message to a channel, expecting a response.
     * @param JSON
     * @param response
     * @param timeout
     */
    void sendRequest(String JSON, Response response, long timeout);

    /**
     * Send a response to a request.
     * @param JSON
     * @param identifier
     */
    void sendResponse(String JSON, UUID identifier);

    /**
     * Called when a message is received.
     * @param message
     */
    void onMessage(Message message);

    void register();

    void unregister();
}
