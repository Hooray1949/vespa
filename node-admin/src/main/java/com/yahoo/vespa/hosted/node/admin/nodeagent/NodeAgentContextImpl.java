package com.yahoo.vespa.hosted.node.admin.nodeagent;

import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.HostName;
import com.yahoo.config.provision.NodeType;
import com.yahoo.config.provision.RegionName;
import com.yahoo.config.provision.SystemName;
import com.yahoo.vespa.athenz.api.AthenzService;
import com.yahoo.vespa.hosted.dockerapi.ContainerName;
import com.yahoo.vespa.hosted.node.admin.component.ZoneId;
import com.yahoo.vespa.hosted.node.admin.docker.DockerNetworking;

import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author freva
 */
public class NodeAgentContextImpl implements NodeAgentContext {
    private static final Path ROOT = Paths.get("/");

    private final String logPrefix;
    private final ContainerName containerName;
    private final HostName hostName;
    private final NodeType nodeType;
    private final AthenzService identity;
    private final DockerNetworking dockerNetworking;
    private final ZoneId zoneId;
    private final Path pathToNodeRootOnHost;
    private final Path pathToVespaHome;

    public NodeAgentContextImpl(HostName hostname, NodeType nodeType, AthenzService identity,
                                DockerNetworking dockerNetworking, ZoneId zoneId,
                                Path pathToContainerStorage, Path pathToVespaHome) {
        this.hostName = Objects.requireNonNull(hostname);
        this.containerName = ContainerName.fromHostname(hostname);
        this.nodeType = Objects.requireNonNull(nodeType);
        this.identity = Objects.requireNonNull(identity);
        this.dockerNetworking = Objects.requireNonNull(dockerNetworking);
        this.zoneId = Objects.requireNonNull(zoneId);
        this.pathToNodeRootOnHost = Objects.requireNonNull(pathToContainerStorage).resolve(containerName.asString());
        this.pathToVespaHome = Objects.requireNonNull(pathToVespaHome);
        this.logPrefix = containerName.asString() + ": ";
    }

    @Override
    public ContainerName containerName() {
        return containerName;
    }

    @Override
    public HostName hostname() {
        return hostName;
    }

    @Override
    public NodeType nodeType() {
        return nodeType;
    }

    @Override
    public AthenzService identity() {
        return identity;
    }

    @Override
    public DockerNetworking dockerNetworking() {
        return dockerNetworking;
    }

    @Override
    public ZoneId zone() {
        return zoneId;
    }

    @Override
    public Path pathOnHostFromPathInNode(Path pathInNode) {
        if (! pathInNode.isAbsolute())
            throw new IllegalArgumentException("Expected an absolute path in the container, got: " + pathInNode);

        return pathToNodeRootOnHost.resolve(ROOT.relativize(pathInNode).toString());
    }

    @Override
    public Path pathInNodeFromPathOnHost(Path pathOnHost) {
        if (! pathOnHost.isAbsolute())
            throw new IllegalArgumentException("Expected an absolute path on the host, got: " + pathOnHost);

        if (!pathOnHost.startsWith(pathToNodeRootOnHost))
            throw new IllegalArgumentException("Path " + pathOnHost + " does not exist in the container");

        return ROOT.resolve(pathToNodeRootOnHost.relativize(pathOnHost).toString());
    }

    @Override
    public Path pathInNodeUnderVespaHome(Path relativePath) {
        if (relativePath.isAbsolute())
            throw new IllegalArgumentException("Expected a relative path to the Vespa home, got: " + relativePath);

        return pathToVespaHome.resolve(relativePath);
    }

    @Override
    public void recordSystemModification(Logger logger, String message) {
        log(logger, message);
    }

    @Override
    public void log(Logger logger, Level level, String message) {
        logger.log(level, logPrefix + message);
    }

    @Override
    public void log(Logger logger, Level level, String message, Throwable throwable) {
        logger.log(level, logPrefix + message, throwable);
    }
    

    /** For testing only! */
    public static class Builder {
        private final HostName hostname;
        private NodeType nodeType;
        private AthenzService identity;
        private DockerNetworking dockerNetworking;
        private ZoneId zoneId;
        private Path pathToContainerStorage;
        private Path pathToVespaHome;

        public Builder(String hostname) {
            this(HostName.from(hostname));
        }
        
        public Builder(HostName hostname) {
            this.hostname = hostname;
        }

        public Builder nodeType(NodeType nodeType) {
            this.nodeType = nodeType;
            return this;
        }

        public Builder identity(AthenzService identity) {
            this.identity = identity;
            return this;
        }

        public Builder dockerNetworking(DockerNetworking dockerNetworking) {
            this.dockerNetworking = dockerNetworking;
            return this;
        }

        public Builder zoneId(ZoneId zoneId) {
            this.zoneId = zoneId;
            return this;
        }

        public Builder pathToContainerStorage(Path pathToContainerStorage) {
            this.pathToContainerStorage = pathToContainerStorage;
            return this;
        }

        public Builder pathToVespaHome(Path pathToVespaHome) {
            this.pathToVespaHome = pathToVespaHome;
            return this;
        }

        public Builder fileSystem(FileSystem fileSystem) {
            return pathToContainerStorage(fileSystem.getPath("/home/docker"));
        }

        public NodeAgentContextImpl build() {
            return new NodeAgentContextImpl(
                    hostname,
                    Optional.ofNullable(nodeType).orElse(NodeType.tenant),
                    Optional.ofNullable(identity).orElseGet(() -> new AthenzService("domain", "service")),
                    Optional.ofNullable(dockerNetworking).orElse(DockerNetworking.HOST_NETWORK),
                    Optional.ofNullable(zoneId).orElseGet(() -> new ZoneId(SystemName.dev, Environment.dev, RegionName.defaultName())),
                    Optional.ofNullable(pathToContainerStorage).orElseGet(() -> Paths.get("/home/docker")),
                    Optional.ofNullable(pathToVespaHome).orElseGet(() -> Paths.get("/opt/vespa"))
            );
        }
    }
}
