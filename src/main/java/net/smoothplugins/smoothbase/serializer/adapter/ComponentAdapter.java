package net.smoothplugins.smoothbase.serializer.adapter;

import com.google.gson.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.lang.reflect.Type;

public class ComponentAdapter implements JsonSerializer<Component>, JsonDeserializer<Component> {

    @Override
    public Component deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        return LegacyComponentSerializer.legacySection().deserialize(json.getAsString());
    }

    @Override
    public JsonElement serialize(Component src, Type typeOfSrc, JsonSerializationContext context) {
        return new JsonPrimitive(LegacyComponentSerializer.legacySection().serialize(src));
    }
}
