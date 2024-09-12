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
package io.prestosql;

import io.prestosql.execution.DriverPipelineTaskId;
import io.prestosql.metadata.SessionPropertyManager;
import io.prestosql.spi.connector.CatalogName;
import io.prestosql.spi.security.ConnectorIdentity;

import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * This class is for connectors, which needs current taskId information.
 */
public class PerTaskFullConnectorSession
        extends FullConnectorSession
{
    private final Optional<DriverPipelineTaskId> driverPipelineTaskId;

    public PerTaskFullConnectorSession(Session session,
                                       ConnectorIdentity identity,
                                       Map<String, String> properties,
                                       CatalogName catalogName,
                                       String catalog,
                                       SessionPropertyManager sessionPropertyManager,
                                       Optional<DriverPipelineTaskId> taskId)
    {
        super(session, identity, properties, catalogName, catalog, sessionPropertyManager);
        this.driverPipelineTaskId = taskId;
    }

    public OptionalInt getTaskId()
    {
        if (driverPipelineTaskId.isPresent() && driverPipelineTaskId.get().getTaskId().isPresent()) {
            return OptionalInt.of(driverPipelineTaskId.get().getTaskId().get().getId());
        }
        return OptionalInt.empty();
    }

    @Override
    public OptionalInt getPipelineId()
    {
        if (driverPipelineTaskId.isPresent()) {
            return OptionalInt.of(driverPipelineTaskId.get().getPipelineId());
        }
        return OptionalInt.empty();
    }

    @Override
    public OptionalInt getDriverId()
    {
        if (driverPipelineTaskId.isPresent()) {
            return OptionalInt.of(driverPipelineTaskId.get().getDriverId());
        }
        return OptionalInt.empty();
    }
}
