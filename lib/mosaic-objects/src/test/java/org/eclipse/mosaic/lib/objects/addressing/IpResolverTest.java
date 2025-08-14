/*
 * Copyright (c) 2020 Fraunhofer FOKUS and others. All rights reserved.
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contact: mosaic@fokus.fraunhofer.de
 */

package org.eclipse.mosaic.lib.objects.addressing;

import static org.junit.Assert.assertEquals;

import org.eclipse.mosaic.lib.junit.IpResolverRule;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;

import java.net.Inet4Address;
import java.net.UnknownHostException;

public class IpResolverTest {

    private final String testConfig = """
            {
                "netMask": "255.255.0.0",
                "vehicleNet": "10.1.0.0",
                "rsuNet": "10.2.0.0",
                "tlNet": "10.3.0.0",
                "csNet": "10.4.0.0",
                "agentNet": "10.10.0.0"
            }
            """;

    @Rule
    public IpResolverRule ipResolverRule = new IpResolverRule(testConfig);

    @Test
    public void testCorrectSetup() {
        assertEquals(65023, IpResolver.getSingleton().getMaxRange());
    }

    @Test
    public void testRoundTripVEHs() {
        for (int i = 0; i < IpResolver.getSingleton().getMaxRange(); ++i) {
            final String name = "veh_" + i;
            Inet4Address nameToIp = IpResolver.getSingleton().nameToIp(name);
            String ipToName = IpResolver.getSingleton().ipToName(nameToIp);
            assertEquals(name, ipToName);
        }
    }

    @Test
    public void testRoundTripRSUs() {
        for (int i = 0; i < IpResolver.getSingleton().getMaxRange(); ++i) {
            final String name = "rsu_" + i;
            Inet4Address nameToIp = IpResolver.getSingleton().nameToIp(name);
            String ipToName = IpResolver.getSingleton().ipToName(nameToIp);
            assertEquals(name, ipToName);
        }
    }

    @Test
    public void testRoundTripTLs() {
        for (int i = 0; i < IpResolver.getSingleton().getMaxRange(); ++i) {
            final String name = "rsu_" + i;
            Inet4Address nameToIp = IpResolver.getSingleton().nameToIp(name);
            String ipToName = IpResolver.getSingleton().ipToName(nameToIp);
            assertEquals(name, ipToName);
        }
    }

    @Test
    public void testRoundTripCSs() {
        for (int i = 0; i < IpResolver.getSingleton().getMaxRange(); ++i) {
            final String name = "cs_" + i;
            Inet4Address nameToIp = IpResolver.getSingleton().nameToIp(name);
            String ipToName = IpResolver.getSingleton().ipToName(nameToIp);
            assertEquals(name, ipToName);
        }
    }

    @Test
    public void testRoundTripAgents() {
        for (int i = 0; i < IpResolver.getSingleton().getMaxRange(); ++i) {
            final String name = "agent_" + i;
            Inet4Address nameToIp = IpResolver.getSingleton().nameToIp(name);
            String ipToName = IpResolver.getSingleton().ipToName(nameToIp);
            assertEquals(name, ipToName);
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void testIPv4AddressExhausted() {
        final String name = "cs_" + IpResolver.getSingleton().getMaxRange() + 1;
        // should throw an exception
        IpResolver.getSingleton().nameToIp(name);
    }

    @Test
    public void testArrayIntConversion() {
        try {
            Inet4Address testAddress = (Inet4Address) Inet4Address.getByName("10.1.0.1");
            byte testArray[] = testAddress.getAddress();
            assertEquals(167837697, IpResolver.getSingleton().addressArrayToFlat(testArray));

            byte testArray2[] = {10, 1, 0, 2};
            Assert.assertArrayEquals(testArray2, IpResolver.getSingleton().addressFlatToArray(167837698));
        } catch (UnknownHostException ex) {}
    }

    @Test
    public void testAddressHelperFunctions() {

        Assert.assertThrows(RuntimeException.class, () -> {
            IpResolver.getSingleton().translateAddressArrayToFlat(new byte[]{10, 2, 0, 0});
        });

        Assert.assertThrows(RuntimeException.class, () -> {
            IpResolver.getSingleton().translateAddressArrayToFlat(new byte[]{10, 2, 0, (byte) 255});
        });

        byte[] array1 = {10, 1, 0, 7};
        byte[] array2 = {10, 2, 0, 1};
        byte[] array3 = {10, 3, 0, 100};
        byte[] array4 = {10, 4, (byte) 3, (byte) 232};
        byte[] array5 = {10, 3, (byte) 255, (byte) 254};
        byte[] array6 = {10, 10, (byte) 5, (byte) 57};

        // addressFlatToArray and addressArrayToFlat do correct back and forth conversion
        Assert.assertArrayEquals(array1, IpResolver.getSingleton().addressFlatToArray(IpResolver.getSingleton().addressArrayToFlat(array1)));
        Assert.assertArrayEquals(array2, IpResolver.getSingleton().addressFlatToArray(IpResolver.getSingleton().addressArrayToFlat(array2)));
        Assert.assertArrayEquals(array3, IpResolver.getSingleton().addressFlatToArray(IpResolver.getSingleton().addressArrayToFlat(array3)));
        Assert.assertArrayEquals(array4, IpResolver.getSingleton().addressFlatToArray(IpResolver.getSingleton().addressArrayToFlat(array4)));
        Assert.assertArrayEquals(array5, IpResolver.getSingleton().addressFlatToArray(IpResolver.getSingleton().addressArrayToFlat(array5)));
        Assert.assertArrayEquals(array6, IpResolver.getSingleton().addressFlatToArray(IpResolver.getSingleton().addressArrayToFlat(array6)));

        // translateAddressFlatToArray and translateAddressArrayToFlat do correct back and forth conversion
        Assert.assertArrayEquals(array1, IpResolver.getSingleton().translateAddressFlatToArray(IpResolver.getSingleton().translateAddressArrayToFlat(array1)));
        Assert.assertArrayEquals(array2, IpResolver.getSingleton().translateAddressFlatToArray(IpResolver.getSingleton().translateAddressArrayToFlat(array2)));
        Assert.assertArrayEquals(array3, IpResolver.getSingleton().translateAddressFlatToArray(IpResolver.getSingleton().translateAddressArrayToFlat(array3)));
        Assert.assertArrayEquals(array4, IpResolver.getSingleton().translateAddressFlatToArray(IpResolver.getSingleton().translateAddressArrayToFlat(array4)));
        Assert.assertArrayEquals(array5, IpResolver.getSingleton().translateAddressFlatToArray(IpResolver.getSingleton().translateAddressArrayToFlat(array5)));
        Assert.assertArrayEquals(array6, IpResolver.getSingleton().translateAddressFlatToArray(IpResolver.getSingleton().translateAddressArrayToFlat(array6)));
    }

    @Test
    public void testAddressAssignment() {
        /* test that .registerHost() will produce expected byte array */
        // test random ips in all given networks
        Assert.assertArrayEquals(
                IpResolver.getSingleton().registerHost("veh_7").getAddress(),
                new byte[]{10, 1, 0, 8}
        );
        Assert.assertArrayEquals(
                IpResolver.getSingleton().registerHost("rsu_0").getAddress(),
                new byte[]{10, 2, 0, 1}
        );
        Assert.assertArrayEquals(
                IpResolver.getSingleton().registerHost("tl_100").getAddress(),
                new byte[]{10, 3, 0, 101}
        );
        Assert.assertArrayEquals(
                IpResolver.getSingleton().registerHost("cs_1000").getAddress(),
                new byte[]{10, 4, 3, (byte) 239}
        );
        Assert.assertArrayEquals(
                IpResolver.getSingleton().registerHost("agent_1337").getAddress(),
                new byte[]{10, 10, (byte) 5, (byte) 68}
        );
        /* test where the LSB flips back to the beginning */
        Assert.assertArrayEquals(
                IpResolver.getSingleton().registerHost("cs_253").getAddress(),
                new byte[]{10, 4, 0, (byte) 254}
        );
        Assert.assertArrayEquals(
                IpResolver.getSingleton().registerHost("cs_254").getAddress(),
                new byte[]{10, 4, 1, 1}
        );
        Assert.assertArrayEquals(
                IpResolver.getSingleton().registerHost("cs_507").getAddress(),
                new byte[]{10, 4, 1, (byte) 254}
        );
        Assert.assertArrayEquals(
                IpResolver.getSingleton().registerHost("cs_508").getAddress(),
                new byte[]{10, 4, 2, 1}
        );
        // Test elements where default config applies
        Assert.assertArrayEquals(
                IpResolver.getSingleton().registerHost("server_254").getAddress(),
                new byte[]{14, 0, 1, 1}
        );
        Assert.assertArrayEquals(
                IpResolver.getSingleton().registerHost("tmc_254").getAddress(),
                new byte[]{15, 0, 1, 1}
        );
        // test when the subnet is exhausted
        Assert.assertArrayEquals(
                IpResolver.getSingleton().registerHost("tl_65023").getAddress(),
                new byte[]{10, 3, (byte) 255, (byte) 254}
        );
        Assert.assertThrows(RuntimeException.class, () -> {
            IpResolver.getSingleton().registerHost("tl_65024");
        });

        /* test .lookup() function */
        Inet4Address ad1 = IpResolver.getSingleton().registerHost("veh_7");
        byte[] array1 = {10, 1, 0, 8};
        Assert.assertArrayEquals(array1, ad1.getAddress());
        Inet4Address lookedUp = IpResolver.getSingleton().lookup("veh_7");
        assertEquals(ad1, lookedUp);
        lookedUp = IpResolver.getSingleton().lookup("veh_10");
        Assert.assertNull(lookedUp);
    }
}