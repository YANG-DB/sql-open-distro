package com.amazon.opendistroforelasticsearch.sql.common.utils;

import org.elasticsearch.index.mapper.IgnoredFieldMapper;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public abstract class MappingService {
    public static final Set<String> META_FIELDS_BEFORE_7DOT8 =
            Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
                    "_id", IgnoredFieldMapper.NAME, "_index", "_routing", "_size", "_timestamp", "_ttl", "_type")));
}
