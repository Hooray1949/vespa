// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.handler.test;

import com.yahoo.container.Container;
import com.yahoo.container.core.config.testutil.HandlersConfigurerTestWrapper;
import com.yahoo.container.jdisc.HttpRequest;

import com.yahoo.container.jdisc.RequestHandlerTestDriver;
import com.yahoo.container.protect.Error;
import com.yahoo.io.IOUtils;
import com.yahoo.net.HostName;
import com.yahoo.search.handler.SearchHandler;
import com.yahoo.search.searchchain.config.test.SearchChainConfigurerTestCase;
import com.yahoo.slime.Inspector;
import com.yahoo.vespa.config.SlimeUtils;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.HashMap;

import static com.yahoo.jdisc.http.HttpRequest.Method.GET;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.*;


public class JSONSearchHandlerTestCase {

    private static final String testDir = "src/test/java/com/yahoo/search/handler/test/config";
    private static final String myHostnameHeader = "my-hostname-header";
    private static final String selfHostname = HostName.getLocalhost();

    private static String tempDir = "";
    private static String configId = null;
    private static final String uri = "http://localhost?";
    private static final String JSON_CONTENT_TYPE = "application/json";

    @Rule
    public TemporaryFolder tempfolder = new TemporaryFolder();

    private RequestHandlerTestDriver driver = null;
    private HandlersConfigurerTestWrapper configurer = null;
    private SearchHandler searchHandler;

    @Before
    public void startUp() throws IOException {
        File cfgDir = tempfolder.newFolder("SearchHandlerTestCase");
        tempDir = cfgDir.getAbsolutePath();
        configId = "dir:" + tempDir;

        IOUtils.copyDirectory(new File(testDir), cfgDir, 1); // make configs active
        generateComponentsConfigForActive();

        configurer = new HandlersConfigurerTestWrapper(new Container(), configId);
        searchHandler = (SearchHandler)configurer.getRequestHandlerRegistry().getComponent(SearchHandler.class.getName());
        driver = new RequestHandlerTestDriver(searchHandler);
    }

    @After
    public void shutDown() {
        if (configurer != null) configurer.shutdown();
        if (driver != null) driver.close();
    }

    private void generateComponentsConfigForActive() throws IOException {
        File activeConfig = new File(tempDir);
        SearchChainConfigurerTestCase.
                createComponentsConfig(new File(activeConfig, "chains.cfg").getPath(),
                        new File(activeConfig, "handlers.cfg").getPath(),
                        new File(activeConfig, "components.cfg").getPath());
    }

    private SearchHandler fetchSearchHandler(HandlersConfigurerTestWrapper configurer) {
        return (SearchHandler) configurer.getRequestHandlerRegistry().getComponent(SearchHandler.class.getName());
    }

    @Test
    public void testBadJSON() throws Exception{
        String json = "Not a valid JSON-string";
        RequestHandlerTestDriver.MockResponseHandler responseHandler = driver.sendRequest(uri, com.yahoo.jdisc.http.HttpRequest.Method.POST, json, JSON_CONTENT_TYPE);
        String response = responseHandler.readAll();
        assertThat(responseHandler.getStatus(), is(400));
        assertThat(response, containsString("errors"));
        assertThat(response, containsString("\"code\":" + Error.ILLEGAL_QUERY.code));
    }

    @Test
    public void testFailing() throws Exception {
        JSONObject json = new JSONObject();
        json.put("query", "test");
        json.put("searchChain", "classLoadingError");
        assertTrue(driver.sendRequest(uri, com.yahoo.jdisc.http.HttpRequest.Method.POST, json.toString(), JSON_CONTENT_TYPE).readAll().contains("NoClassDefFoundError"));
    }


    @Test
    public synchronized void testPluginError() throws Exception {
        JSONObject json = new JSONObject();
        json.put("query", "test");
        json.put("searchChain", "exceptionInPlugin");
        assertTrue(driver.sendRequest(uri, com.yahoo.jdisc.http.HttpRequest.Method.POST, json.toString(), JSON_CONTENT_TYPE).readAll().contains("NullPointerException"));
    }

    @Test
    public synchronized void testWorkingReconfiguration() throws Exception {
        JSONObject json = new JSONObject();
        json.put("query", "abc");
        assertJsonResult(json, driver);

        // reconfiguration
        IOUtils.copyDirectory(new File(testDir, "handlers2"), new File(tempDir), 1);
        generateComponentsConfigForActive();
        configurer.reloadConfig();

        // ...and check the resulting config
        SearchHandler newSearchHandler = fetchSearchHandler(configurer);
        assertNotSame("Have a new instance of the search handler", searchHandler, newSearchHandler);
        assertNotNull("Have the new search chain", fetchSearchHandler(configurer).getSearchChainRegistry().getChain("hello"));
        assertNull("Don't have the new search chain", fetchSearchHandler(configurer).getSearchChainRegistry().getChain("classLoadingError"));
        try (RequestHandlerTestDriver newDriver = new RequestHandlerTestDriver(searchHandler)) {
            assertJsonResult(json, newDriver);
        }
    }

    @Test
    public void testInvalidYqlQuery() throws Exception {
        IOUtils.copyDirectory(new File(testDir, "config_yql"), new File(tempDir), 1);
        generateComponentsConfigForActive();
        configurer.reloadConfig();

        SearchHandler newSearchHandler = fetchSearchHandler(configurer);
        assertTrue("Do I have a new instance of the search handler?", searchHandler != newSearchHandler);
        try (RequestHandlerTestDriver newDriver = new RequestHandlerTestDriver(newSearchHandler)) {
            JSONObject json = new JSONObject();
            json.put("yql", "select * from foo where bar > 1453501295");
            RequestHandlerTestDriver.MockResponseHandler responseHandler = newDriver.sendRequest(uri, com.yahoo.jdisc.http.HttpRequest.Method.POST, json.toString(), JSON_CONTENT_TYPE);
            responseHandler.readAll();
            assertThat(responseHandler.getStatus(), is(400));
        }
    }

    // Query handling takes a different code path when a query profile is active, so we test both paths.
    @Test
    public void testInvalidQueryParamWithQueryProfile() throws Exception {
        try (RequestHandlerTestDriver newDriver = driverWithConfig("config_invalid_param")) {
            testInvalidQueryParam(newDriver);
        }
    }

    private void testInvalidQueryParam(final RequestHandlerTestDriver testDriver) throws Exception{
        JSONObject json = new JSONObject();
        json.put("query", "status_code:0");
        json.put("hits", 20);
        json.put("offset", -20);
        RequestHandlerTestDriver.MockResponseHandler responseHandler =
                testDriver.sendRequest(uri, com.yahoo.jdisc.http.HttpRequest.Method.POST, json.toString(), JSON_CONTENT_TYPE);
        String response = responseHandler.readAll();
        assertThat(responseHandler.getStatus(), is(400));
        assertThat(response, containsString("offset"));
        assertThat(response, containsString("\"code\":" + com.yahoo.container.protect.Error.INVALID_QUERY_PARAMETER.code));
    }




    @Test
    public void testNormalResultJsonAliasRendering() throws Exception {
        JSONObject json = new JSONObject();
        json.put("format", "json");
        json.put("query", "abc");
        assertJsonResult(json, driver);
    }



    @Test
    public void testNullQuery() throws Exception {
        JSONObject json = new JSONObject();
        json.put("format", "xml");

        assertEquals("<?xml version=\"1.0\" encoding=\"utf-8\" ?>\n" +
                "<result total-hit-count=\"0\">\n" +
                "  <hit relevancy=\"1.0\">\n" +
                "    <field name=\"relevancy\">1.0</field>\n" +
                "    <field name=\"uri\">testHit</field>\n" +
                "  </hit>\n" +
                "</result>\n", driver.sendRequest(uri, com.yahoo.jdisc.http.HttpRequest.Method.POST, json.toString(), JSON_CONTENT_TYPE).readAll());
    }



    @Test
    public void testWebServiceStatus() throws Exception {
        JSONObject json = new JSONObject();
        json.put("query", "web_service_status_code");
        RequestHandlerTestDriver.MockResponseHandler responseHandler =
                driver.sendRequest(uri, com.yahoo.jdisc.http.HttpRequest.Method.POST, json.toString(), JSON_CONTENT_TYPE);
        String response = responseHandler.readAll();
        assertThat(responseHandler.getStatus(), is(406));
        assertThat(response, containsString("\"code\":" + 406));
    }

    @Test
    public void testNormalResultImplicitDefaultRendering() throws Exception {
        JSONObject json = new JSONObject();
        json.put("query", "abc");
        assertJsonResult(json, driver);
    }

    @Test
    public void testNormalResultExplicitDefaultRendering() throws Exception {
        JSONObject json = new JSONObject();
        json.put("query", "abc");
        json.put("format", "default");
        assertJsonResult(json, driver);
    }

    @Test
    public void testNormalResultXmlAliasRendering() throws Exception {
        JSONObject json = new JSONObject();
        json.put("query", "abc");
        json.put("format", "xml");
        assertXmlResult(json, driver);
    }


    @Test
    public void testNormalResultExplicitDefaultRenderingFullRendererName1() throws Exception {
        JSONObject json = new JSONObject();
        json.put("query", "abc");
        json.put("format", "XmlRenderer");
        assertXmlResult(json, driver);
    }

    @Test
    public void testNormalResultExplicitDefaultRenderingFullRendererName2() throws Exception {
        JSONObject json = new JSONObject();
        json.put("query", "abc");
        json.put("format", "JsonRenderer");
        assertJsonResult(json, driver);
    }

    @Test
    public void testResultLegacyTiledFormat() throws Exception {
        JSONObject json = new JSONObject();
        json.put("query", "abc");
        json.put("format", "tiled");
        assertTiledResult(json, driver);
    }

    @Test
    public void testResultLegacyPageFormat() throws Exception {
        JSONObject json = new JSONObject();
        json.put("query", "abc");
        json.put("format", "page");
        assertPageResult(json, driver);
    }


    private static final String xmlResult =
            "<?xml version=\"1.0\" encoding=\"utf-8\" ?>\n" +
                    "<result total-hit-count=\"0\">\n" +
                    "  <hit relevancy=\"1.0\">\n" +
                    "    <field name=\"relevancy\">1.0</field>\n" +
                    "    <field name=\"uri\">testHit</field>\n" +
                    "  </hit>\n" +
                    "</result>\n";

    private void assertXmlResult(JSONObject json, RequestHandlerTestDriver driver) throws Exception {
        assertOkResult(driver.sendRequest(uri, com.yahoo.jdisc.http.HttpRequest.Method.POST, json.toString(), JSON_CONTENT_TYPE), xmlResult);
    }


    private static final String jsonResult = "{\"root\":{"
            + "\"id\":\"toplevel\",\"relevance\":1.0,\"fields\":{\"totalCount\":0},"
            + "\"children\":["
            + "{\"id\":\"testHit\",\"relevance\":1.0,\"fields\":{\"uri\":\"testHit\"}}"
            + "]}}";

    private void assertJsonResult(JSONObject json, RequestHandlerTestDriver driver) throws Exception {
        assertOkResult(driver.sendRequest(uri, com.yahoo.jdisc.http.HttpRequest.Method.POST, json.toString(), JSON_CONTENT_TYPE), jsonResult);

    }

    private static final String tiledResult =
            "<?xml version=\"1.0\" encoding=\"utf-8\" ?>\n" +
                    "<result version=\"1.0\">\n" +
                    "\n" +
                    "  <hit relevance=\"1.0\">\n" +
                    "    <id>testHit</id>\n" +
                    "    <uri>testHit</uri>\n" +
                    "  </hit>\n" +
                    "\n" +
                    "</result>\n";

    private void assertTiledResult(JSONObject json, RequestHandlerTestDriver driver) throws Exception {
        assertOkResult(driver.sendRequest(uri, com.yahoo.jdisc.http.HttpRequest.Method.POST, json.toString(), JSON_CONTENT_TYPE), tiledResult);
    }

    private static final String pageResult =
            "<?xml version=\"1.0\" encoding=\"utf-8\" ?>\n" +
                    "<page version=\"1.0\">\n" +
                    "\n" +
                    "  <content>\n" +
                    "    <hit relevance=\"1.0\">\n" +
                    "      <id>testHit</id>\n" +
                    "      <uri>testHit</uri>\n" +
                    "    </hit>\n" +
                    "  </content>\n" +
                    "\n" +
                    "</page>\n";

    private void assertPageResult(JSONObject json, RequestHandlerTestDriver driver) throws Exception {
        assertOkResult(driver.sendRequest(uri, com.yahoo.jdisc.http.HttpRequest.Method.POST, json.toString(), JSON_CONTENT_TYPE), pageResult);
    }

    private void assertOkResult(RequestHandlerTestDriver.MockResponseHandler response, String expected) {
        assertEquals(expected, response.readAll());
        assertEquals(200, response.getStatus());
        assertEquals(selfHostname, response.getResponse().headers().get(myHostnameHeader).get(0));
    }


    private RequestHandlerTestDriver driverWithConfig(String configDirectory) throws Exception {
        IOUtils.copyDirectory(new File(testDir, configDirectory), new File(tempDir), 1);
        generateComponentsConfigForActive();
        configurer.reloadConfig();

        SearchHandler newSearchHandler = fetchSearchHandler(configurer);
        assertTrue("Do I have a new instance of the search handler?", searchHandler != newSearchHandler);
        return new RequestHandlerTestDriver(newSearchHandler);
    }


    @Test
    public void testSelectParameter() throws Exception {
        JSONObject json = new JSONObject();

        JSONObject select = new JSONObject();

            JSONObject where = new JSONObject();
            where.put("where", "where");

            JSONObject grouping = new JSONObject();
            grouping.put("grouping", "grouping");

        select.put("where", where);
        select.put("grouping", grouping);

        json.put("select", select);


        // Create mapping
        Inspector inspector = SlimeUtils.jsonToSlime(json.toString().getBytes("utf-8")).get();
        Map<String, String> map = new HashMap<>();
        searchHandler.createRequestMapping(inspector, map, "");

        JSONObject processedWhere = new JSONObject(map.get("select.where"));
        assertEquals(where.toString(), processedWhere.toString());

        JSONObject processedGrouping = new JSONObject(map.get("select.grouping"));
        assertEquals(grouping.toString(), processedGrouping.toString());
    }



    @Test
    public void testRequestMapping() throws Exception {
        JSONObject json = new JSONObject();
        json.put("yql", "select * from sources * where sddocname contains \"blog_post\" limit 0 | all(group(date) max(3) order(-count())each(output(count())));");
        json.put("hits", 10.0);
        json.put("offset", 5);
        json.put("queryProfile", "foo");
        json.put("nocache", false);
        json.put("groupingSessionCache", false);
        json.put("searchChain", "exceptionInPlugin");
        json.put("timeout", 0);
        json.put("select", "_all");


        JSONObject model = new JSONObject();
        model.put("defaultIndex", 1);
        model.put("encoding", "json");
        model.put("filter", "default");
        model.put("language", "en");
        model.put("queryString", "abc");
        model.put("restrict", "_doc,json,xml");
        model.put("searchPath", "node1");
        model.put("sources", "source1,source2");
        model.put("type", "yql");
        json.put("model", model);

        JSONObject ranking = new JSONObject();
        ranking.put("location", "123789.89123N;128123W");
        ranking.put("features", "none");
        ranking.put("listFeatures", false);
        ranking.put("profile", "1");
        ranking.put("properties", "default");
        ranking.put("sorting", "desc");
        ranking.put("freshness", "0.05");
        ranking.put("queryCache", false);

        JSONObject matchPhase = new JSONObject();
        matchPhase.put("maxHits", "100");
        matchPhase.put("attribute", "title");
        matchPhase.put("ascending", true);

        JSONObject diversity = new JSONObject();
        diversity.put("attribute", "title");
        diversity.put("minGroups", 1);
        matchPhase.put("diversity", diversity);
        ranking.put("matchPhase", matchPhase);
        json.put("ranking", ranking);

        JSONObject presentation = new JSONObject();
        presentation.put("bolding", true);
        presentation.put("format", "json");
        presentation.put("summary", "none");
        presentation.put("template", "json");
        presentation.put("timing", false);
        json.put("presentation", presentation);

        JSONObject collapse = new JSONObject();
        collapse.put("field", "none");
        collapse.put("size", 2);
        collapse.put("summary", "default");
        json.put("collapse", collapse);

        JSONObject trace = new JSONObject();
        trace.put("level", 1);
        trace.put("timestamps", false);
        trace.put("rules", "none");
        json.put("trace", trace);

        JSONObject pos = new JSONObject();
        pos.put("ll", "1263123N;1231.9W");
        pos.put("radius", "71234m");
        pos.put("bb", "1237123W;123218N");
        pos.put("attribute", "default");
        json.put("pos", pos);

        JSONObject streaming = new JSONObject();
        streaming.put("userid", 123);
        streaming.put("groupname", "abc");
        streaming.put("selection", "none");
        streaming.put("priority", 10);
        streaming.put("maxbucketspervisitor", 5);
        json.put("streaming", streaming);

        JSONObject rules = new JSONObject();
        rules.put("off", false);
        rules.put("rulebase", "default");
        json.put("rules", rules);

        JSONObject metrics = new JSONObject();
        metrics.put("ignore", "_all");
        json.put("metrics", metrics);

        json.put("recall", "none");
        json.put("user", 123);
        json.put("nocachewrite", false);
        json.put("hitcountestimate", true);



        // Create mapping
        Inspector inspector = SlimeUtils.jsonToSlime(json.toString().getBytes("utf-8")).get();
        Map<String, String> map = new HashMap<>();
        searchHandler.createRequestMapping(inspector, map, "");

        // Create GET-request with same query
        String url = uri + "&model.sources=source1%2Csource2&select=_all&model.language=en&presentation.timing=false&pos.attribute=default&pos.radius=71234m&model.searchPath=node1&nocachewrite=false&ranking.matchPhase.maxHits=100&presentation.summary=none" +
                "&nocache=false&model.type=yql&collapse.summary=default&ranking.matchPhase.diversity.minGroups=1&ranking.location=123789.89123N%3B128123W&ranking.queryCache=false&offset=5&streaming.groupname=abc&groupingSessionCache=false" +
                "&presentation.template=json&trace.rules=none&rules.off=false&ranking.properties=default&searchChain=exceptionInPlugin&pos.ll=1263123N%3B1231.9W&ranking.sorting=desc&ranking.matchPhase.ascending=true&ranking.features=none&hitcountestimate=true" +
                "&model.filter=default&metrics.ignore=_all&collapse.field=none&ranking.profile=1&rules.rulebase=default&model.defaultIndex=1&trace.level=1&ranking.listFeatures=false&timeout=0&presentation.format=json" +
                "&yql=select+%2A+from+sources+%2A+where+sddocname+contains+%22blog_post%22+limit+0+%7C+all%28group%28date%29+max%283%29+order%28-count%28%29%29each%28output%28count%28%29%29%29%29%3B&recall=none&streaming.maxbucketspervisitor=5" +
                "&queryProfile=foo&presentation.bolding=true&model.encoding=json&model.queryString=abc&streaming.selection=none&trace.timestamps=false&collapse.size=2&streaming.priority=10&ranking.matchPhase.diversity.attribute=title" +
                "&ranking.matchPhase.attribute=title&hits=10&streaming.userid=123&pos.bb=1237123W%3B123218N&model.restrict=_doc%2Cjson%2Cxml&ranking.freshness=0.05&user=123";



        final HttpRequest request = HttpRequest.createTestRequest(url, GET);

        // Get mapping
        Map<String, String> propertyMap = request.propertyMap();
        assertEquals("Should have same mapping for properties", map, propertyMap);
    }



}
