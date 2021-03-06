package com.instaclick.filter;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;

import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertTrue;

import org.junit.Test;

public abstract class BaseFilterTest extends BaseTest {

    protected abstract DataFilter getFilter();

    @Test
    public void testAddAndContains() throws ParseException
    {
        DataFilter filter   = getFilter();
        DateFormat df       = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        Data d1             = new Data("1", df.parse("2001-11-12 11:22:31"));
        Data d2             = new Data("1", df.parse("2001-11-12 11:22:32"));

        assertFalse(filter.contains(d1));
        assertFalse(filter.contains(d2));

        assertTrue(filter.add(d1));
        assertFalse(filter.add(d2));

        assertTrue(filter.contains(d1));
        assertTrue(filter.contains(d2));
    }

    @Test
    public void testContainsAfterFlush() throws ParseException
    {
        DataFilter filter   = getFilter();
        DateFormat df       = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        Data d1             = new Data("1", df.parse("2001-11-12 11:22:31"));
        Data d2             = new Data("1", df.parse("2001-11-12 11:22:32"));

        assertFalse(filter.contains(d1));
        assertFalse(filter.contains(d2));

        assertTrue(filter.add(d1));

        filter.flush();

        assertTrue(filter.contains(d1));
        assertTrue(filter.contains(d2));
    }

    @Test
    public void testAddAfterFlush() throws ParseException
    {
        DataFilter filter   = getFilter();
        DateFormat df       = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        Data d1             = new Data("1", df.parse("2001-11-12 11:22:33"));
        Data d2             = new Data("1", df.parse("2001-11-12 11:22:34"));
        Data d3             = new Data("1", df.parse("2001-11-12 11:22:33"));
        Data d4             = new Data("1", df.parse("2001-11-12 11:22:34"));

        assertFalse(filter.contains(d1));
        assertFalse(filter.contains(d2));
        assertFalse(filter.contains(d3));
        assertFalse(filter.contains(d4));

        assertTrue(filter.add(d1));
        assertFalse(filter.add(d2));

        filter.flush();

        assertFalse(filter.add(d3));
        assertFalse(filter.add(d4));

        assertTrue(filter.contains(d1));
        assertTrue(filter.contains(d2));
        assertTrue(filter.contains(d3));
        assertTrue(filter.contains(d4));
    }

    @Test
    public void testAddNewAfterFlush() throws ParseException
    {
        DataFilter filter   = getFilter();
        DateFormat df       = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        Data d1             = new Data("1", df.parse("2001-11-12 11:22:33"));
        Data d2             = new Data("1", df.parse("2001-11-12 11:22:34"));
        Data d3             = new Data("2", df.parse("2001-11-12 11:22:33"));
        Data d4             = new Data("2", df.parse("2001-11-12 11:22:34"));

        assertFalse(filter.contains(d1));
        assertFalse(filter.contains(d2));
        assertFalse(filter.contains(d3));
        assertFalse(filter.contains(d4));

        assertTrue(filter.add(d1));
        assertFalse(filter.add(d2));

        filter.flush();

        assertTrue(filter.add(d3));

        assertFalse(filter.add(d2));
        assertFalse(filter.add(d4));

        assertTrue(filter.contains(d1));
        assertTrue(filter.contains(d2));
        assertTrue(filter.contains(d3));
        assertTrue(filter.contains(d4));
    }

    @Test
    public void testAddNewAfterMultipleFlushs() throws ParseException
    {
        DataFilter filter   = getFilter();
        DateFormat df       = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        Data d1             = new Data("1", df.parse("2001-11-12 11:22:33"));
        Data d2             = new Data("2", df.parse("2001-11-12 12:22:34"));
        Data d3             = new Data("3", df.parse("2001-11-12 13:22:33"));
        Data d4             = new Data("4", df.parse("2001-11-12 14:22:34"));

        assertFalse(filter.contains(d1));
        assertFalse(filter.contains(d2));
        assertFalse(filter.contains(d3));
        assertFalse(filter.contains(d4));

        assertTrue(filter.add(d1));

        filter.flush();

        assertFalse(filter.add(d1));
        assertTrue(filter.add(d2));

        filter.flush();

        assertFalse(filter.add(d1));
        assertFalse(filter.add(d2));
        assertTrue(filter.add(d3));

        filter.flush();

        assertFalse(filter.add(d1));
        assertFalse(filter.add(d2));
        assertFalse(filter.add(d3));
        assertTrue(filter.add(d4));

        assertTrue(filter.contains(d1));
        assertTrue(filter.contains(d2));
        assertTrue(filter.contains(d3));
        assertTrue(filter.contains(d4));
    }

    @Test
    public void testProviderLookup() throws ParseException
    {
        DataFilter filter   = getFilter();
        DateFormat df       = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        Data d1             = new Data("1", df.parse("2001-11-12 00:00:00"));
        Data d2             = new Data("1", df.parse("2001-11-12 00:01:00"));
        Data d3             = new Data("1", df.parse("2001-11-12 00:59:00"));
        Data d4             = new Data("1", df.parse("2001-11-12 12:02:00"));

        assertFalse(filter.contains(d1));
        assertFalse(filter.contains(d2));
        assertFalse(filter.contains(d3));
        assertFalse(filter.contains(d4));

        assertTrue(filter.add(d1));
        assertFalse(filter.add(d2));

        filter.flush();

        assertFalse(filter.add(d3));
        assertFalse(filter.add(d4));

        assertTrue(filter.contains(d1));
        assertTrue(filter.contains(d2));
        assertTrue(filter.contains(d3));
        assertTrue(filter.contains(d4));
    }

    @Test
    public void testShoudNotAddDuringTheSameHour()
    {
        DataFilter filter   = getFilter();
        Long startTime      = 1293861600L;
        Long finalTime      = startTime + 60;
        Data data           = new Data("1", startTime);

        assertFalse(filter.contains(data));
        assertTrue(filter.add(data));
        assertTrue(filter.contains(data));

        while (startTime < finalTime) {
            assertFalse(filter.add(new Data("1", startTime++)));
        }
    }
}