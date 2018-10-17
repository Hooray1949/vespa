// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.integrationTests;

import com.yahoo.config.provision.HostName;
import com.yahoo.config.provision.NodeType;
import com.yahoo.vespa.hosted.dockerapi.DockerImage;
import com.yahoo.vespa.hosted.node.admin.configserver.noderepository.NodeSpec;
import com.yahoo.vespa.hosted.node.admin.nodeadmin.NodeAdmin;
import com.yahoo.vespa.hosted.node.admin.provider.NodeAdminStateUpdater;
import com.yahoo.vespa.hosted.node.admin.nodeadmin.NodeAdminStateUpdaterImpl;
import com.yahoo.vespa.hosted.provision.Node;
import org.junit.Ignore;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

/**
 * Tests rebooting of Docker host
 *
 * @author musum
 */
public class RebootTest {

    @Test
    @Ignore
    public void test() throws InterruptedException {
        try (DockerTester dockerTester = new DockerTester()) {

            dockerTester.addChildNodeRepositoryNode(createNodeRepositoryNode());

            // Wait for node admin to be notified with node repo state and the docker container has been started
            while (dockerTester.nodeAdmin.getNumberOfNodeAgents() == 0) {
                Thread.sleep(10);
            }

            // Check that the container is started and NodeRepo has received the PATCH update
            dockerTester.callOrderVerifier.assertInOrder(
                    "createContainerCommand with DockerImage { imageId=dockerImage }, HostName: host1.test.yahoo.com, ContainerName { name=host1 }",
                    "updateNodeAttributes with HostName: host1.test.yahoo.com, NodeAttributes{restartGeneration=1, rebootGeneration=null,  dockerImage=dockerImage, vespaVersion='null'}");

            NodeAdminStateUpdaterImpl updater = dockerTester.nodeAdminStateUpdater;
//            assertThat(updater.setResumeStateAndCheckIfResumed(NodeAdminStateUpdater.State.SUSPENDED),
//                       is(Optional.of("Not all node agents are frozen.")));

            updater.setResumeStateAndCheckIfResumed(NodeAdminStateUpdater.State.SUSPENDED);

            NodeAdmin nodeAdmin = dockerTester.nodeAdmin;
            // Wait for node admin to be frozen
            while ( ! nodeAdmin.isFrozen()) {
                System.out.println("Node admin not frozen yet");
                Thread.sleep(10);
            }

            assertTrue(nodeAdmin.setFrozen(false));

            dockerTester.callOrderVerifier.assertInOrder(
                    "executeInContainer with ContainerName { name=host1 }, args: [" + DockerTester.NODE_PROGRAM + ", stop]");
        }
    }

    private NodeSpec createNodeRepositoryNode() {
        return new NodeSpec.Builder()
                .hostname(HostName.from("host1.test.yahoo.com"))
                .wantedDockerImage(new DockerImage("dockerImage"))
                .state(Node.State.active)
                .nodeType(NodeType.tenant)
                .flavor("docker")
                .vespaVersion("6.50.0")
                .wantedRestartGeneration(1L)
                .currentRestartGeneration(1L)
                .build();
    }
}
