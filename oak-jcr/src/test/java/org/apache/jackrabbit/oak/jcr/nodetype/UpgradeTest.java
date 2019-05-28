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
package org.apache.jackrabbit.oak.jcr.nodetype;

import java.io.File;
import java.io.FileOutputStream;
import java.io.StringReader;

import javax.jcr.Node;
import javax.jcr.Repository;
import javax.jcr.Session;
import javax.jcr.SimpleCredentials;
import javax.jcr.nodetype.NodeType;
import javax.jcr.nodetype.NodeTypeManager;
import javax.jcr.nodetype.PropertyDefinition;

import com.google.common.collect.Iterators;

import org.apache.commons.io.IOUtils;
import org.apache.jackrabbit.api.JackrabbitRepository;
import org.apache.jackrabbit.commons.cnd.CndImporter;
import org.apache.jackrabbit.oak.jcr.Jcr;
import org.apache.jackrabbit.oak.plugins.segment.SegmentNodeStore;
import org.apache.jackrabbit.oak.plugins.segment.SegmentStore;
import org.junit.Test;

import net.lingala.zip4j.core.ZipFile;

import static org.apache.jackrabbit.oak.plugins.segment.file.FileStore.newFileStore;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class UpgradeTest {

    @Test
    public void upgradeFrom10() throws Exception {
        File testFolder = new File(new File("target"), UpgradeTest.class.getSimpleName());
        File repoHome = new File(testFolder, "test-repo-1.0");
        repoHome.delete();
        File tmpZip = File.createTempFile("test-repo", null);
        IOUtils.copy(NodeTypeTest.class.getResourceAsStream("test-repo-1.0.zip"),
                new FileOutputStream(tmpZip));
        ZipFile repoZip = new ZipFile(tmpZip);
        repoZip.extractAll(testFolder.getPath());
        tmpZip.delete();

        SegmentStore store = newFileStore(repoHome).create();
        Repository repo = new Jcr(new SegmentNodeStore(store)).createRepository();
        Session s = repo.login(new SimpleCredentials("admin", "admin".toCharArray()));

        Node myType = s.getNode("/jcr:system/jcr:nodeTypes/test:MyType");
        assertEquals(2, Iterators.size(myType.getNodes("jcr:propertyDefinition")));

        NodeTypeManager ntMgr = s.getWorkspace().getNodeTypeManager();
        assertTrue(ntMgr.hasNodeType("test:MyType"));
        NodeType nt = ntMgr.getNodeType("test:MyType");
        PropertyDefinition[] pDefs = nt.getDeclaredPropertyDefinitions();
        assertEquals(2, pDefs.length);
        for (PropertyDefinition pd : pDefs) {
            String name = pd.getName();
            if (name.equals("test:mandatory")) {
                assertTrue(pd.isMandatory());
            } else if (name.equals("test:optional")) {
                assertFalse(pd.isMandatory());
            } else {
                fail("Unexpected property definition: " + name);
            }
        }

        // flip mandatory flag for test:mandatory
        String cnd = "<'test'='http://www.apache.org/jackrabbit/test'>\n" +
                "[test:MyType] > nt:unstructured\n" +
                " - test:mandatory (string)\n" +
                " - test:optional (string)";

        CndImporter.registerNodeTypes(new StringReader(cnd), s, true);

        myType = s.getNode("/jcr:system/jcr:nodeTypes/test:MyType");
        assertEquals(2, Iterators.size(myType.getNodes("jcr:propertyDefinition")));

        nt = ntMgr.getNodeType("test:MyType");
        pDefs = nt.getDeclaredPropertyDefinitions();
        assertEquals(2, pDefs.length);
        for (PropertyDefinition pd : pDefs) {
            String name = pd.getName();
            if (name.equals("test:mandatory")) {
                assertFalse(pd.isMandatory());
            } else if (name.equals("test:optional")) {
                assertFalse(pd.isMandatory());
            } else {
                fail("Unexpected property definition: " + name);
            }
        }

        s.logout();
        if (repo instanceof JackrabbitRepository) {
            ((JackrabbitRepository) repo).shutdown();
        }
        store.close();
    }
}