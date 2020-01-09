/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.sling.servlets.resolver.it;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.servlethelpers.MockSlingHttpServletRequest;
import org.apache.sling.servlethelpers.MockSlingHttpServletResponse;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerClass;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;

@RunWith(PaxExam.class)
@ExamReactorStrategy(PerClass.class)
public class ServletSelectionIT extends ServletResolverTestSupport {

    private final static int STARTUP_WAIT_SECONDS = 30;

    @Inject
    private BundleContext bundleContext;

    @Inject
    private ResourceResolverFactory resourceResolverFactory;

    private MockSlingHttpServletResponse executeRequest(String path, int expectedStatus) throws Exception {
        final ResourceResolver resourceResolver = resourceResolverFactory.getAdministrativeResourceResolver(null);
        assertNotNull("Expecting ResourceResolver", resourceResolver);
        final MockSlingHttpServletRequest request = new MockSlingHttpServletRequest(resourceResolver);
        request.setPathInfo(path);
        final MockSlingHttpServletResponse response = new MockSlingHttpServletResponse();

        // Get SlingRequestProcessor.processRequest method and execute request
        // This module depends on an older version of the sling.engine module and I don't want
        // to change it just for these tests, so using reflection to get the processor, as we're
        // running with a more recent version of sling.engine in the pax exam environment
        final String slingRequestProcessorClassName = "org.apache.sling.engine.SlingRequestProcessor";
        final ServiceReference<?> ref = bundleContext.getServiceReference(slingRequestProcessorClassName);
        assertNotNull("Expecting service:" + slingRequestProcessorClassName, ref);

        final Object processor = bundleContext.getService(ref);
        try {
            // void processRequest(javax.servlet.http.HttpServletRequest request, javax.servlet.http.HttpServletResponse resource, ResourceResolver resourceResolver)
            final Method processMethod = processor.getClass().getMethod(
                "processRequest", 
                HttpServletRequest.class, HttpServletResponse.class, ResourceResolver.class);
            assertNotNull("Expecting processRequest method", processMethod);
            processMethod.invoke(processor, request, response, resourceResolver);
        } finally {
            bundleContext.ungetService(ref);
        }

        if(expectedStatus > 0) {
            assertEquals("Expected status " + expectedStatus + " at " + path, expectedStatus, response.getStatus());
        }

        return response;
    }

    @Before
    public void waitForStableSling() throws Exception {
        final int expectedStatus = 200;
        final List<Integer> statuses = new ArrayList<>();
        final String path = "/.json";
        final long endTime = System.currentTimeMillis() + STARTUP_WAIT_SECONDS * 1000;
        while(System.currentTimeMillis() < endTime) {
            final int status = executeRequest(path, -1).getStatus();
            statuses.add(status);
            if(status == expectedStatus) {
                return;
            }
            Thread.sleep(250);
        }
        fail("Did not get a 200 status at " + path + " got " + statuses);
    }

    @Test
    public void testDefaultJsonServlet() throws Exception {
        final MockSlingHttpServletResponse response = executeRequest("/.json", 200);
        final String content = response.getOutputAsString();
        final String [] expected = {
            "jcr:primaryType\":\"rep:root",
            "jcr:mixinTypes\":[\"rep:AccessControllable\"]"
        };
        for(String s : expected) {
            assertTrue("Expecting in output: " + s + ", got " + content, content.contains(s));
        }

    }

}