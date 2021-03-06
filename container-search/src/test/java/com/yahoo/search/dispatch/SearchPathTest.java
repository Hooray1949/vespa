// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.dispatch;

import com.yahoo.search.dispatch.SearchPath.InvalidSearchPathException;
import com.yahoo.search.dispatch.searchcluster.Node;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.util.Collection;
import java.util.stream.Collectors;

import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;

/**
 * @author ollivir
 */
public class SearchPathTest {
    @Test
    public void requreThatSearchPathsAreParsedCorrectly() {
        assertThat(SearchPath.fromString("0/0").get().toString(), equalTo("0/0"));
        assertThat(SearchPath.fromString("1/0").get().toString(), equalTo("1/0"));
        assertThat(SearchPath.fromString("0/1").get().toString(), equalTo("0/1"));

        assertThat(SearchPath.fromString("0,1/2").get().toString(), equalTo("0,1/2"));
        assertThat(SearchPath.fromString("[0,1>/2").get().toString(), equalTo("0/2"));
        assertThat(SearchPath.fromString("[0,2>/2").get().toString(), equalTo("[0,2>/2"));
        assertThat(SearchPath.fromString("[0,1>,1/2").get().toString(), equalTo("0,1/2"));

        assertThat(SearchPath.fromString("*/2").get().toString(), equalTo("/2"));
        assertThat(SearchPath.fromString("1,*/2").get().toString(), equalTo("/2"));

        assertThat(SearchPath.fromString("1").get().toString(), equalTo("1"));
        assertThat(SearchPath.fromString("1/").get().toString(), equalTo("1"));
        assertThat(SearchPath.fromString("1/*").get().toString(), equalTo("1"));
    }

    @Test
    public void requreThatWildcardsAreDetected() {
        assertFalse(SearchPath.fromString("").isPresent());
        assertFalse(SearchPath.fromString("*/*").isPresent());
        assertFalse(SearchPath.fromString("/").isPresent());
        assertFalse(SearchPath.fromString("/*").isPresent());
        assertFalse(SearchPath.fromString("//").isPresent());
    }

    @Rule
    public final ExpectedException exception = ExpectedException.none();

    @Test
    public void invalidRangeMustThrowException() {
        exception.expect(InvalidSearchPathException.class);
        SearchPath.fromString("[p,0>/0");
    }

    @Test
    public void invalidPartMustThrowException() {
        exception.expect(InvalidSearchPathException.class);
        SearchPath.fromString("p/0");
    }

    @Test
    public void invalidRowMustThrowException() {
        exception.expect(InvalidSearchPathException.class);
        SearchPath.fromString("1,2,3/r");
    }

    @Test
    public void searchPathMustFilterNodesBasedOnDefinition() {
        MockSearchCluster cluster = new MockSearchCluster(3, 3);

        assertThat(distKeysAsString(SearchPath.selectNodes("1/1", cluster)), equalTo("5"));
        assertThat(distKeysAsString(SearchPath.selectNodes("/1", cluster)), equalTo("4,5,6"));
        assertThat(distKeysAsString(SearchPath.selectNodes("0,1/2", cluster)), equalTo("7,8"));
        assertThat(distKeysAsString(SearchPath.selectNodes("[1,3>/1", cluster)), equalTo("5,6"));
        assertThat(distKeysAsString(SearchPath.selectNodes("[1,88>/1", cluster)), equalTo("5,6"));
    }

    private static String distKeysAsString(Collection<Node> nodes) {
        return nodes.stream().map(Node::key).map(Object::toString).collect(Collectors.joining(","));
    }
}
