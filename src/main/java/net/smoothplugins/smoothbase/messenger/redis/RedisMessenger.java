package net.smoothplugins.smoothbase.messenger.redis;

import net.smoothplugins.smoothbase.connection.RedisConnection;
import net.smoothplugins.smoothbase.messenger.Message;
import net.smoothplugins.smoothbase.messenger.MessageConsumer;
import net.smoothplugins.smoothbase.messenger.Messenger;
import net.smoothplugins.smoothbase.messenger.Response;
import net.smoothplugins.smoothbase.serializer.Serializer;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPubSub;

import java.util.HashMap;
import java.util.UUID;

public class RedisMessenger implements Messenger {

    private final Plugin plugin;
    private final RedisConnection connection;
    private final MessageConsumer consumer;
    private final Serializer serializer;
    private Subscriber subscriber;
    private String CHANNEL;
    private boolean closing = false;

    private final HashMap<UUID, Response> pendingResponses = new HashMap<>();

    public RedisMessenger(Plugin plugin, RedisConnection connection, MessageConsumer consumer, Serializer serializer) {
        this.plugin = plugin;
        this.connection = connection;
        this.consumer = consumer;
        this.serializer = serializer;
    }

    @Override
    public void send(String JSON) {
        try (Jedis jedis = connection.getPool().getResource()) {
            Message finalMessage = new Message(Message.MessageType.NORMAL, null, JSON);
            jedis.publish(CHANNEL, serializer.serialize(finalMessage));
        }
    }

    @Override
    public void sendRequest(String JSON, Response response, long timeout) {
        try (Jedis jedis = connection.getPool().getResource()) {
            UUID identifier = UUID.randomUUID();
            Message finalMessage = new Message(Message.MessageType.REQUEST, identifier, JSON);
            jedis.publish(CHANNEL, serializer.serialize(finalMessage));
            pendingResponses.put(identifier, response);

            Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, () -> {
                if (pendingResponses.containsKey(identifier)) {
                    pendingResponses.get(identifier).onFail(CHANNEL);
                    pendingResponses.remove(identifier);
                }
            }, (int) ((timeout / 1000F) * 20L));
        }
    }

    @Override
    public void sendResponse(String JSON, UUID identifier) {
        try (Jedis jedis = connection.getPool().getResource()) {
            Message finalMessage = new Message(Message.MessageType.RESPONSE, identifier, JSON);
            jedis.publish(CHANNEL, serializer.serialize(finalMessage));
        }
    }

    @Override
    public void onMessage(Message message) {
        if (message.getType() == Message.MessageType.RESPONSE) {
            if (!pendingResponses.containsKey(message.getIdentifier())) return;

            Response response = pendingResponses.get(message.getIdentifier());
            response.onSuccess(CHANNEL, message.getJSON());
            pendingResponses.remove(message.getIdentifier());
        } else {
            consumer.consume(message.getJSON(), message.getIdentifier());
        }
    }

    @Override
    public void register() {
        CHANNEL = connection.getCluster() + "messenger:";
        subscriber = new Subscriber();
        new Thread(subscriber).start();
    }

    @Override
    public void unregister() {
        closing = true;
        subscriber.unsubscribe();
    }

    private class Subscriber extends JedisPubSub implements Runnable {

        @Override
        public void run() {
            boolean first = true;

            while (!closing && !Thread.interrupted() && !connection.getPool().isClosed()) {
                try (Jedis jedis = connection.getPool().getResource()) {
                    if (first) {
                        first = false;
                    } else {
                        plugin.getLogger().info("Reconnected to Redis");
                    }

                    jedis.subscribe(this, CHANNEL);
                } catch (Exception e) {
                    if (closing) {
                        return;
                    }

                    plugin.getLogger().warning("Lost connection to Redis" + e);
                    try {
                        unsubscribe();
                    } catch (Exception e2) {
                        e2.printStackTrace();
                    }

                    // Sleep for 5 seconds to prevent massive spam in console
                    try {
                        Thread.sleep(5000);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                    }

                    e.printStackTrace();
                }
            }
        }

        @Override
        public void onMessage(String channel, String message) {
            if (channel.equals(CHANNEL)) {
                Message finalMessage = serializer.deserialize(message, Message.class);
                RedisMessenger.this.onMessage(finalMessage);
            }
        }
    }
}
