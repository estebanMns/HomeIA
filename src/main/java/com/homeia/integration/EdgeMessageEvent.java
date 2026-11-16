package com.homeia.integration;

/** Mensaje recibido en {edgeRoot}/{edgeId}/{kind}. Aún sin verificar: lo valida EdgeService. */
public record EdgeMessageEvent(String edgeId, String kind, String payload) {
}
