package com.homeia.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NetworkAddressPolicyTest {

    @Test
    void aceptaRangosPrivados() {
        for (String ip : new String[]{"10.0.0.5", "172.16.4.1", "172.31.255.254", "192.168.1.40"}) {
            assertTrue(NetworkAddressPolicy.checkPrivateIpv4(ip).isEmpty(), ip);
        }
    }

    @Test
    void rechazaInternetLoopbackNombresYFormatosRaros() {
        for (String ip : new String[]{"8.8.8.8", "127.0.0.1", "169.254.169.254", "172.32.0.1", "192.168.1.255",
                "192.168.1.0", "localhost", "192.168.1.4:80", "http://192.168.1.4", "192.168.01.4", "256.1.1.1", "", null}) {
            assertTrue(NetworkAddressPolicy.checkPrivateIpv4(ip).isPresent(), String.valueOf(ip));
        }
    }

    @Test
    void friendlyNameZigbeeSinComodinesDeTopico() {
        assertTrue(NetworkAddressPolicy.checkZigbeeName("lampara_sala-1").isEmpty());
        for (String name : new String[]{"sala/lampara", "a+b", "#", "", "x".repeat(65)}) {
            assertTrue(NetworkAddressPolicy.checkZigbeeName(name).isPresent(), name);
        }
    }
}
