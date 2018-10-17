// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.maintenance.acl;

import com.google.common.net.InetAddresses;
import com.yahoo.config.provision.HostName;
import com.yahoo.vespa.hosted.dockerapi.Container;
import com.yahoo.vespa.hosted.node.admin.configserver.noderepository.Acl;
import com.yahoo.vespa.hosted.node.admin.configserver.noderepository.NodeRepository;
import com.yahoo.vespa.hosted.node.admin.docker.DockerOperations;
import com.yahoo.vespa.hosted.node.admin.task.util.network.IPAddresses;
import com.yahoo.vespa.hosted.node.admin.task.util.network.IPVersion;
import com.yahoo.vespa.hosted.node.admin.util.PrefixLogger;

import java.net.InetAddress;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * This class maintains the iptables (ipv4 and ipv6) for all running containers.
 * The filter table is synced with ACLs fetched from the Node repository while the nat table
 * is synched with the proper redirect rule.
 * <p>
 * If an ACL cannot be configured (e.g. iptables process execution fails) we attempted to flush the rules
 * rendering the firewall open.
 * <p>
 * This class currently assumes control over the filter and nat table.
 * <p>
 * The configuration will be retried the next time the maintainer runs.
 *
 * @author mpolden
 * @author smorgrav
 */
public class AclMaintainer {

    private static final PrefixLogger log = PrefixLogger.getNodeAdminLogger(AclMaintainer.class);

    private final DockerOperations dockerOperations;
    private final NodeRepository nodeRepository;
    private final IPAddresses ipAddresses;
    private final HostName hostHostname;

    public AclMaintainer(DockerOperations dockerOperations, NodeRepository nodeRepository,
                         HostName hostHostname, IPAddresses ipAddresses) {
        this.dockerOperations = dockerOperations;
        this.nodeRepository = nodeRepository;
        this.ipAddresses = ipAddresses;
        this.hostHostname = hostHostname;
    }

    private void applyRedirect(Container container, InetAddress address) {
        IPVersion ipVersion = IPVersion.get(address);
        // Necessary to avoid the routing packets destined for the node's own public IP address
        // via the bridge, which is illegal.
        String redirectRule = "-A OUTPUT -d " + InetAddresses.toAddrString(address) + ipVersion.singleHostCidr() + " -j REDIRECT";
        IPTablesEditor.editLogOnError(dockerOperations, container.name, ipVersion, "nat", NatTableLineEditor.from(redirectRule));
    }

    private void apply(Container container, Acl acl) {
        // Apply acl to the filter table
        IPTablesEditor.editFlushOnError(dockerOperations, container.name, IPVersion.IPv6, "filter", FilterTableLineEditor.from(acl, IPVersion.IPv6));
        IPTablesEditor.editFlushOnError(dockerOperations, container.name, IPVersion.IPv4, "filter", FilterTableLineEditor.from(acl, IPVersion.IPv4));

        ipAddresses.getAddress(container.hostname, IPVersion.IPv4).ifPresent(addr -> applyRedirect(container, addr));
        ipAddresses.getAddress(container.hostname, IPVersion.IPv6).ifPresent(addr -> applyRedirect(container, addr));
    }

    private synchronized void configureAcls() {
        log.info("Configuring ACLs"); // Needed to potentially nail down when ACL maintainer stopped working
        Map<HostName, Container> runningContainers = dockerOperations
                .getAllManagedContainers().stream()
                .filter(container -> container.state.isRunning())
                .collect(Collectors.toMap(container -> container.hostname, container -> container));

        nodeRepository.getAcls(hostHostname).entrySet().stream()
                .filter(entry -> runningContainers.containsKey(entry.getKey()))
                .forEach(entry -> apply(runningContainers.get(entry.getKey()), entry.getValue()));
    }

    public void converge() {
        try {
            configureAcls();
        } catch (Throwable t) {
            log.error("Failed to configure ACLs", t);
        }
    }
}
