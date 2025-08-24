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

package org.eclipse.mosaic.lib.objects.communication;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import org.eclipse.mosaic.lib.enums.AdHocChannel;

import org.junit.Test;

import java.net.Inet4Address;
import java.net.UnknownHostException;

/**
 * Test class for AdHocConfiguration and InterfaceConfiguration since they are 
 * tightly entangled
 */
public class AdHocConfigurationTest {

    @Test(expected = NullPointerException.class) 
    public void testInstantiationFail1() {
        // RUN and FAIL
        new InterfaceConfiguration.Builder(AdHocChannel.SCH1).power(100.).create();
    }
    
    @Test(expected = NullPointerException.class) 
    public void testInstantiationFail2() throws UnknownHostException {
        Inet4Address addr1 = (Inet4Address)Inet4Address.getByName("10.1.0.50");

        // RUN and FAIL
        new InterfaceConfiguration.Builder(AdHocChannel.SCH1).subnet(addr1).power(100.).create();
    }    
    
    @Test(expected = NullPointerException.class) 
    public void testInstantiationFail3() throws UnknownHostException {
        Inet4Address addr1 = (Inet4Address)Inet4Address.getByName("10.1.0.50");
        Inet4Address sub1 = (Inet4Address)Inet4Address.getByName("255.0.0.0");
        // RUN and FAIL
        new InterfaceConfiguration.Builder(null).ip(addr1).subnet(sub1).power(100.).create();
    }
    
    @Test
    public void testEqualities() throws UnknownHostException {
        // SETUP
        Inet4Address addr1 = (Inet4Address)Inet4Address.getByName("10.1.0.0");
        Inet4Address addr2 = (Inet4Address)Inet4Address.getByName("10.1.0.1");
        Inet4Address sub1 = (Inet4Address)Inet4Address.getByName("255.0.0.0");
        Inet4Address sub2 = (Inet4Address)Inet4Address.getByName("255.255.0.0");

        // RUN
        InterfaceConfiguration ifconf1 = new InterfaceConfiguration.Builder(AdHocChannel.SCH1).ip(addr1).subnet(sub1).power(100.).create();
        InterfaceConfiguration ifconf2 = new InterfaceConfiguration.Builder(AdHocChannel.SCH1).ip(addr1).subnet(sub1).power(100.).create();
        
        InterfaceConfiguration ifconf3 = new InterfaceConfiguration.Builder(AdHocChannel.SCH1).ip(addr2).subnet(sub1).power(100.).create();
        
        InterfaceConfiguration ifconf4 = new InterfaceConfiguration.Builder(AdHocChannel.CCH).ip(addr1).subnet(sub1).power(100.).create();
                
        InterfaceConfiguration ifconf6 = new InterfaceConfiguration.Builder(AdHocChannel.SCH1).ip(addr1).subnet(sub1).power(200.).create();
        
        InterfaceConfiguration ifconf7 = new InterfaceConfiguration.Builder(AdHocChannel.SCH1).ip(addr1).subnet(sub2).power(100.).create();
        
        InterfaceConfiguration ifconf8 = new InterfaceConfiguration.Builder(AdHocChannel.SCH6).ip(addr2).subnet(sub2).power(500.).create();

        //ASSERT
        assertEquals(ifconf1, ifconf2);
        assertNotEquals(ifconf1, ifconf3);
        assertNotEquals(ifconf1, ifconf4);
        assertNotEquals(ifconf1, ifconf6);
        assertNotEquals(ifconf1, ifconf7);
        assertNotEquals(ifconf1, ifconf8);
    }
    
    @Test
    public void testConfigurationCreation() throws UnknownHostException {
        //SETUP
        Inet4Address addr1 = (Inet4Address)Inet4Address.getByName("10.1.0.0");
        Inet4Address addr2 = (Inet4Address)Inet4Address.getByName("10.1.0.1");
        Inet4Address sub = (Inet4Address)Inet4Address.getByName("255.0.0.0");

        // RUN
        InterfaceConfiguration ifconf1 = new InterfaceConfiguration.Builder(AdHocChannel.SCH6).ip(addr1).subnet(sub).power(100.).create();
        InterfaceConfiguration ifconf2 = new InterfaceConfiguration.Builder(AdHocChannel.SCH1).secondChannel(AdHocChannel.CCH).ip(addr2).subnet(sub).power(99999.).create();
        
        AdHocConfiguration aconfig1 = new AdHocConfiguration.Builder("veh_0").create();
        AdHocConfiguration aconfig2 = new AdHocConfiguration.Builder("veh_0").addInterface(ifconf1).create();
        AdHocConfiguration aconfig3 = new AdHocConfiguration.Builder("veh_0").addInterface(ifconf1).addInterface(ifconf2).create();

        // ASSERT
        assertEquals(AdHocConfiguration.RadioMode.OFF, aconfig1.getRadioMode());
        assertEquals(AdHocConfiguration.RadioMode.SINGLE, aconfig2.getRadioMode());
        assertEquals(AdHocConfiguration.RadioMode.DUAL, aconfig3.getRadioMode());
        assertArrayEquals(addr1.getAddress(), aconfig3.getConf0().getIp().getAddress());
        assertArrayEquals(addr2.getAddress(), aconfig3.getConf1().getIp().getAddress());
        assertArrayEquals(sub.getAddress(), aconfig3.getConf0().getSubnet().getAddress());
        assertArrayEquals(sub.getAddress(), aconfig3.getConf1().getSubnet().getAddress());
        assertEquals(100., aconfig3.getConf0().getPower(),1e-7);
        assertEquals(99999., aconfig3.getConf1().getPower(), 1e-7);
        assertEquals(AdHocChannel.SCH6, aconfig2.getConf0().getChannel0());
        assertEquals(AdHocChannel.SCH6, aconfig3.getConf0().getChannel0());
        assertEquals(AdHocChannel.SCH1, aconfig3.getConf1().getChannel0());
        assertEquals(AdHocChannel.CCH, aconfig3.getConf1().getChannel1());
        assertEquals(InterfaceConfiguration.MultiChannelMode.SINGLE, aconfig2.getConf0().getMode());
        assertEquals(InterfaceConfiguration.MultiChannelMode.ALTERNATING, aconfig3.getConf1().getMode());
    }
}