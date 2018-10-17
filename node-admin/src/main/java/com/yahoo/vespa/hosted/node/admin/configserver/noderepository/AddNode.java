// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.configserver.noderepository;

import com.yahoo.config.provision.HostName;
import com.yahoo.config.provision.NodeType;

import java.util.Collections;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * @author freva
 */
public class AddNode {

    public final HostName hostname;
    public final Optional<HostName> parentHostname;
    public final String nodeFlavor;
    public final NodeType nodeType;
    public final Set<String> ipAddresses;
    public final Set<String> additionalIpAddresses;

    /**
     * Constructor for a host node (has no parent)
     */
    public AddNode(HostName hostname, String nodeFlavor, NodeType nodeType, Set<String> ipAddresses, Set<String> additionalIpAddresses) {
        this(hostname, Optional.empty(), nodeFlavor, nodeType, ipAddresses, additionalIpAddresses);
    }

    /**
     * Constructor for a child node (Must set parentHostname, no additionalIpAddresses)
     */
    public AddNode(HostName hostname, HostName parentHostname, String nodeFlavor, NodeType nodeType, Set<String> ipAddresses) {
        this(hostname, Optional.of(parentHostname), nodeFlavor, nodeType, ipAddresses, Collections.emptySet());
    }

    public AddNode(HostName hostname, Optional<HostName> parentHostname, String nodeFlavor, NodeType nodeType, Set<String> ipAddresses, Set<String> additionalIpAddresses) {
        this.hostname = hostname;
        this.parentHostname = parentHostname;
        this.nodeFlavor = nodeFlavor;
        this.nodeType = nodeType;
        this.ipAddresses = ipAddresses;
        this.additionalIpAddresses = additionalIpAddresses;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AddNode addNode = (AddNode) o;
        return Objects.equals(hostname, addNode.hostname) &&
                Objects.equals(parentHostname, addNode.parentHostname) &&
                Objects.equals(nodeFlavor, addNode.nodeFlavor) &&
                nodeType == addNode.nodeType &&
                Objects.equals(ipAddresses, addNode.ipAddresses) &&
                Objects.equals(additionalIpAddresses, addNode.additionalIpAddresses);
    }

    @Override
    public int hashCode() {
        return Objects.hash(hostname, parentHostname, nodeFlavor, nodeType, ipAddresses, additionalIpAddresses);
    }

    @Override
    public String toString() {
        return "AddNode{" +
                "hostname='" + hostname + '\'' +
                ", parentHostname=" + parentHostname +
                ", nodeFlavor='" + nodeFlavor + '\'' +
                ", nodeType=" + nodeType +
                ", ipAddresses=" + ipAddresses +
                ", additionalIpAddresses=" + additionalIpAddresses +
                '}';
    }
}
