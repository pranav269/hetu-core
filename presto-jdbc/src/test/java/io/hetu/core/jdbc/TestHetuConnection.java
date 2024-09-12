/*
 * Copyright (C) 2018-2021. Huawei Technologies Co., Ltd. All rights reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.hetu.core.jdbc;

import com.google.common.collect.ImmutableMap;
import io.airlift.log.Logging;
import io.hetu.core.common.filesystem.TempFolder;
import io.hetu.core.filesystem.HetuFileSystemClientPlugin;
import io.hetu.core.metastore.HetuMetastorePlugin;
import io.prestosql.plugin.memory.MemoryPlugin;
import io.prestosql.server.testing.TestingPrestoServer;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;

import static java.lang.String.format;
import static org.assertj.core.api.Assertions.assertThat;

public class TestHetuConnection
{
    private TestingPrestoServer server;

    @BeforeClass
    public void setupServer()
            throws Exception
    {
        Logging.initialize();
        TempFolder folder = new TempFolder().create();
        Runtime.getRuntime().addShutdownHook(new Thread(folder::close));
        HashMap<String, String> metastoreConfig = new HashMap<>();
        metastoreConfig.put("hetu.metastore.type", "hetufilesystem");
        metastoreConfig.put("hetu.metastore.hetufilesystem.profile-name", "default");
        metastoreConfig.put("hetu.metastore.hetufilesystem.path", folder.newFolder("metastore").getAbsolutePath());
        server = new TestingPrestoServer();
        server.installPlugin(new HetuFileSystemClientPlugin());
        server.installPlugin(new HetuMetastorePlugin());
        server.installPlugin(new MemoryPlugin());
        server.loadMetastore(metastoreConfig);
        server.createCatalog("memory", "memory",
                ImmutableMap.of("memory.spill-path", folder.newFolder("memory-connector").getAbsolutePath()));

        try (Connection connection = createConnection();
                Statement statement = connection.createStatement()) {
            statement.execute("CREATE SCHEMA testschema");
        }
    }

    @Test
    public void testUse()
            throws SQLException
    {
        try (Connection connection = createConnection()) {
            assertThat(connection.getCatalog()).isEqualTo("memory");

            // change schema
            try (Statement statement = connection.createStatement()) {
                statement.execute("USE testschema");
            }

            // change catalog and schema
            try (Statement statement = connection.createStatement()) {
                statement.execute("USE system.runtime");
            }

            assertThat(connection.getCatalog()).isEqualTo("system");
            assertThat(connection.getSchema()).isEqualTo("runtime");
        }
    }

    private Connection createConnection()
            throws SQLException
    {
        try {
            return createConnection("");
        }
        catch (ClassNotFoundException e) {
            System.out.println("Create connection failed");
            return null;
        }
    }

    private Connection createConnection(String extra)
            throws SQLException, ClassNotFoundException
    {
        Class.forName("io.hetu.core.jdbc.OpenLooKengDriver");
        String url = format("jdbc:lk://%s/memory/default?%s", server.getAddress(), extra);
        return DriverManager.getConnection(url, "admin", null);
    }
}
