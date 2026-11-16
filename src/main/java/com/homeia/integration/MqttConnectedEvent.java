package com.homeia.integration;

/** El backend se (re)conectó al broker: momento de resincronizar inventario con los Edge. */
public record MqttConnectedEvent(boolean reconnect) {
}
