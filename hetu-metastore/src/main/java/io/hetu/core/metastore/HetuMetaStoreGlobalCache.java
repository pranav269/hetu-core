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
package io.hetu.core.metastore;

import com.google.inject.Inject;
import io.prestosql.spi.metastore.HetuMetastore;
import io.prestosql.spi.statestore.StateStore;

public class HetuMetaStoreGlobalCache
        extends HetuMetastoreCache
{
    @Inject
    public HetuMetaStoreGlobalCache(@ForHetuMetastoreCache HetuMetastore delegate, StateStore stateStore)
    {
        super(delegate,
                new HetuGlobalCache<>(stateStore, MetaStoreConstants.HETU_META_STORE_CATALOGCACHE_NAME),
                new HetuGlobalCache<>(stateStore, MetaStoreConstants.HETU_META_STORE_CATALOGSCACHE_NAME),
                new HetuGlobalCache<>(stateStore, MetaStoreConstants.HETU_META_STORE_DATABASECACHE_NAME),
                new HetuGlobalCache<>(stateStore, MetaStoreConstants.HETU_META_STORE_DATABASESCACHE_NAME),
                new HetuGlobalCache<>(stateStore, MetaStoreConstants.HETU_META_STORE_TABLECACHE_NAME),
                new HetuGlobalCache<>(stateStore, MetaStoreConstants.HETU_META_STORE_TABLESCACHE_NAME));
    }
}
