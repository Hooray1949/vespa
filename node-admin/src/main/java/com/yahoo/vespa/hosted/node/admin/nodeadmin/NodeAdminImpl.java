// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.nodeadmin;

import com.yahoo.concurrent.ThreadFactoryFactory;
import com.yahoo.config.provision.HostName;
import com.yahoo.vespa.hosted.dockerapi.metrics.CounterWrapper;
import com.yahoo.vespa.hosted.dockerapi.metrics.Dimensions;
import com.yahoo.vespa.hosted.dockerapi.metrics.GaugeWrapper;
import com.yahoo.vespa.hosted.dockerapi.metrics.MetricReceiverWrapper;
import com.yahoo.vespa.hosted.node.admin.configserver.noderepository.NodeSpec;
import com.yahoo.vespa.hosted.node.admin.maintenance.acl.AclMaintainer;
import com.yahoo.vespa.hosted.node.admin.nodeagent.NodeAgent;
import com.yahoo.vespa.hosted.node.admin.util.PrefixLogger;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Administers a host (for now only docker hosts) and its nodes (docker containers nodes).
 *
 * @author stiankri
 */
public class NodeAdminImpl implements NodeAdmin {
    private static final PrefixLogger logger = PrefixLogger.getNodeAdminLogger(NodeAdmin.class);
    private final ScheduledExecutorService aclScheduler =
            Executors.newScheduledThreadPool(1, ThreadFactoryFactory.getDaemonThreadFactory("aclscheduler"));
    private final ScheduledExecutorService metricsScheduler =
            Executors.newScheduledThreadPool(1, ThreadFactoryFactory.getDaemonThreadFactory("metricsscheduler"));

    private final Function<HostName, NodeAgent> nodeAgentFactory;
    private final Optional<AclMaintainer> aclMaintainer;

    private final Clock clock;
    private boolean previousWantFrozen;
    private boolean isFrozen;
    private Instant startOfFreezeConvergence;

    private final Map<HostName, NodeAgent> nodeAgentsByHostname = new ConcurrentHashMap<>();

    private final GaugeWrapper numberOfContainersInLoadImageState;
    private final CounterWrapper numberOfUnhandledExceptionsInNodeAgent;

    public NodeAdminImpl(Function<HostName, NodeAgent> nodeAgentFactory,
                         Optional<AclMaintainer> aclMaintainer,
                         MetricReceiverWrapper metricReceiver,
                         Clock clock) {
        this.nodeAgentFactory = nodeAgentFactory;
        this.aclMaintainer = aclMaintainer;

        this.clock = clock;
        this.previousWantFrozen = true;
        this.isFrozen = true;
        this.startOfFreezeConvergence = clock.instant();

        Dimensions dimensions = new Dimensions.Builder().add("role", "docker").build();
        this.numberOfContainersInLoadImageState = metricReceiver.declareGauge(MetricReceiverWrapper.APPLICATION_DOCKER, dimensions, "nodes.image.loading");
        this.numberOfUnhandledExceptionsInNodeAgent = metricReceiver.declareCounter(MetricReceiverWrapper.APPLICATION_DOCKER, dimensions, "nodes.unhandled_exceptions");
    }

    @Override
    public void refreshContainersToRun(List<NodeSpec> containersToRun) {
        final Set<HostName> hostnamesOfContainersToRun = containersToRun.stream()
                .map(NodeSpec::getHostname)
                .collect(Collectors.toSet());

        synchronizeNodesToNodeAgents(hostnamesOfContainersToRun);

        updateNodeAgentMetrics();
    }

    private void updateNodeAgentMetrics() {
        int numberContainersWaitingImage = 0;
        int numberOfNewUnhandledExceptions = 0;

        for (NodeAgent nodeAgent : nodeAgentsByHostname.values()) {
            if (nodeAgent.isDownloadingImage()) numberContainersWaitingImage++;
            numberOfNewUnhandledExceptions += nodeAgent.getAndResetNumberOfUnhandledExceptions();
        }

        numberOfContainersInLoadImageState.sample(numberContainersWaitingImage);
        numberOfUnhandledExceptionsInNodeAgent.add(numberOfNewUnhandledExceptions);
    }

    @Override
    public boolean setFrozen(boolean wantFrozen) {
        if (wantFrozen != previousWantFrozen) {
            if (wantFrozen) {
                this.startOfFreezeConvergence = clock.instant();
            } else {
                this.startOfFreezeConvergence = null;
            }

            previousWantFrozen = wantFrozen;
        }

        // Use filter with count instead of allMatch() because allMatch() will short circuit on first non-match
        boolean allNodeAgentsConverged = nodeAgentsByHostname.values().stream()
                .filter(nodeAgent -> !nodeAgent.setFrozen(wantFrozen))
                .count() == 0;

        if (wantFrozen) {
            if (allNodeAgentsConverged) isFrozen = true;
        } else isFrozen = false;

        return allNodeAgentsConverged;
    }

    @Override
    public boolean isFrozen() {
        return isFrozen;
    }

    @Override
    public Duration subsystemFreezeDuration() {
        if (startOfFreezeConvergence == null) {
            return Duration.ofSeconds(0);
        } else {
            return Duration.between(startOfFreezeConvergence, clock.instant());
        }
    }

    @Override
    public void stopNodeAgentServices(List<HostName> hostnames) {
        // Each container may spend 1-1:30 minutes stopping
        hostnames.stream()
                .filter(nodeAgentsByHostname::containsKey)
                .map(nodeAgentsByHostname::get)
                .forEach(nodeAgent -> {
                    nodeAgent.suspend();
                    nodeAgent.stopServices();
                });
    }

    public int getNumberOfNodeAgents() {
        return nodeAgentsByHostname.keySet().size();
    }

    @Override
    public Map<String, Object> debugInfo() {
        Map<String, Object> debug = new LinkedHashMap<>();
        debug.put("isFrozen", isFrozen);

        List<Map<String, Object>> nodeAgentDebugs = nodeAgentsByHostname.values().stream()
                .map(NodeAgent::debugInfo).collect(Collectors.toList());
        debug.put("NodeAgents", nodeAgentDebugs);
        return debug;
    }

    @Override
    public void start() {
        metricsScheduler.scheduleAtFixedRate(() -> {
            try {
                nodeAgentsByHostname.values().forEach(NodeAgent::updateContainerNodeMetrics);
            } catch (Throwable e) {
                logger.warning("Metric fetcher scheduler failed", e);
            }
        }, 10, 55, TimeUnit.SECONDS);

        aclMaintainer.ifPresent(maintainer -> {
            int delay = 120; // WARNING: Reducing this will increase the load on config servers.
            aclScheduler.scheduleWithFixedDelay(() -> {
                if (!isFrozen()) maintainer.converge();
            }, 30, delay, TimeUnit.SECONDS);
        });
    }

    @Override
    public void stop() {
        metricsScheduler.shutdown();
        aclScheduler.shutdown();

        // Stop all node-agents in parallel, will block until the last NodeAgent is stopped
        nodeAgentsByHostname.values().parallelStream().forEach(NodeAgent::stop);

        do {
            try {
                metricsScheduler.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
                aclScheduler.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
            } catch (InterruptedException e) {
                logger.info("Was interrupted while waiting for metricsScheduler and aclScheduler to shutdown");
            }
        } while (!metricsScheduler.isTerminated() || !aclScheduler.isTerminated());
    }

    // Set-difference. Returns minuend minus subtrahend.
    private static <T> Set<T> diff(final Set<T> minuend, final Set<T> subtrahend) {
        final HashSet<T> result = new HashSet<>(minuend);
        result.removeAll(subtrahend);
        return result;
    }

    void synchronizeNodesToNodeAgents(Set<HostName> hostnamesToRun) {
        // Stop and remove NodeAgents that should no longer be running
        diff(nodeAgentsByHostname.keySet(), hostnamesToRun)
                .forEach(hostname -> nodeAgentsByHostname.remove(hostname).stop());

        // Start NodeAgent for hostnames that should be running, but aren't yet
        diff(hostnamesToRun, nodeAgentsByHostname.keySet())
                .forEach(this::startNodeAgent);
    }

    private void startNodeAgent(HostName hostname) {
        if (nodeAgentsByHostname.containsKey(hostname))
            throw new IllegalArgumentException("Attempted to start NodeAgent for hostname " + hostname +
                    ", but one is already running!");

        NodeAgent agent = nodeAgentFactory.apply(hostname);
        agent.start();
        nodeAgentsByHostname.put(hostname, agent);
    }
}
