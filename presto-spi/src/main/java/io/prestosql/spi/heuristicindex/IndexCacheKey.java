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
package io.prestosql.spi.heuristicindex;

import io.prestosql.spi.connector.CreateIndexMetadata;

import java.util.Objects;

public class IndexCacheKey
{
    public static final long LAST_MODIFIED_TIME_PLACE_HOLDER = 0;

    private final String path;
    private final long lastModifiedTime;
    private final IndexRecord record;
    private final CreateIndexMetadata.Level indexLevel;
    private boolean noCloseFlag; // Indicate that this index should not be closed at removal

    /**
     * @param path path to the file the index files should be read for
     * @param lastModifiedTime lastModifiedTime of the file, used to validate the indexes
     * @param record the index record associated with the cache key
     * @param indexLevel see Index.Level in presto-spi
     */
    public IndexCacheKey(String path, long lastModifiedTime, IndexRecord record, CreateIndexMetadata.Level indexLevel)
    {
        this.path = path;
        this.lastModifiedTime = lastModifiedTime;
        this.record = record;
        this.indexLevel = indexLevel;
    }

    /**
     * Create a cache with a index level it could be Stripe or partition
     *
     * @param path
     * @param lastModifiedTime
     * @param record the index record associated with the cache key
     */
    public IndexCacheKey(String path, long lastModifiedTime, IndexRecord record)
    {
        this(path, lastModifiedTime, record, CreateIndexMetadata.Level.STRIPE);
    }

    /**
     * Create a cache with a index level it could be Stripe or partition
     *
     * @param path
     * @param lastModifiedTime
     */
    public IndexCacheKey(String path, long lastModifiedTime)
    {
        this(path, lastModifiedTime, null, CreateIndexMetadata.Level.STRIPE);
    }

    public String getPath()
    {
        return path;
    }

    public long getLastModifiedTime()
    {
        return lastModifiedTime;
    }

    public IndexRecord getRecord()
    {
        return record;
    }

    public CreateIndexMetadata.Level getIndexLevel()
    {
        return this.indexLevel;
    }

    public void setNoCloseFlag(boolean flag)
    {
        this.noCloseFlag = flag;
    }

    public boolean skipCloseIndex()
    {
        return noCloseFlag;
    }

    // only the path is used as the key
    // the lastModifiedTime time is only used to check if index is valid
    @Override
    public boolean equals(Object o)
    {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        IndexCacheKey that = (IndexCacheKey) o;
        return Objects.equals(path, that.path);
    }

    @Override
    public String toString()
    {
        return "IndexCacheKey{" +
                "path='" + path + '\'' +
                '}';
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(path);
    }
}
