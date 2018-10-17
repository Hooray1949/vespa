// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.task.util.network;

import com.yahoo.config.provision.HostName;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * @author smorgrav
 */
public class IPAddressesImpl implements IPAddresses {

    @Override
    public InetAddress[] getAddresses(HostName hostname) {
        try {
            return InetAddress.getAllByName(hostname.value());
        } catch (UnknownHostException e) {
            throw new RuntimeException(e);
        }
    }
}
