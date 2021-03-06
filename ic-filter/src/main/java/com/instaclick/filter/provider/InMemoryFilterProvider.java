package com.instaclick.filter.provider;

import java.io.IOException;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * In memory provider, valid only for test proposes
 *
 * @author Fabio B. Silva <fabio.bat.silva@gmail.com>
 */
public class InMemoryFilterProvider implements FilterProvider
{
    /**
     * Map of {@link BloomFilter}
     */
    private Map<String, Serializable> map = new HashMap<String, Serializable>();

    /**
     * {@inheritDoc}
     */
    public boolean hasFilter(String name) throws IOException
    {
        return map.containsKey(name);
    }

    /**
     * {@inheritDoc}
     */
    public Serializable loadFilter(String name) throws IOException
    {
        return map.get(name);
    }

    /**
     * {@inheritDoc}
     */
    public void saveFilter(String name, Serializable filter) throws IOException
    {
        map.put(name, filter);
    }
}
