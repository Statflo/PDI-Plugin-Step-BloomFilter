package com.instaclick.filter;

import com.google.common.hash.Hashing;
import com.instaclick.filter.provider.FilterProvider;
import com.instaclick.filter.provider.VfsFilterProvider;
import org.apache.commons.vfs2.FileObject;
import org.apache.commons.vfs2.FileSystemException;
import org.apache.commons.vfs2.VFS;
import org.junit.Before;
import org.pentaho.di.core.exception.KettleFileException;

public class SingleBloomDataFilterVfsTest extends BaseFilterTest {

    FilterProvider provider = null;
    FileObject folder = null;

    public SingleBloomDataFilterVfsTest() throws FileSystemException, KettleFileException
    {
        folder   = VFS.getManager().resolveFile(getParameter("provider.uri.vfs", "tmp://ic-filter/") + System.currentTimeMillis());
        provider = new VfsFilterProvider(folder.getURL().toString());

        config.withFilterFileName("single_filter_" + System.currentTimeMillis());
    }

    @Before
    public void setUp() throws FileSystemException
    {
        if (folder != null && folder.exists()) {
            for (FileObject file : folder.getChildren()) {
                file.delete();
            }
        }
    }

    @Override
    protected DataFilter getFilter()
    {
        return new SingleBloomDataFilter(config, provider, Hashing.murmur3_128());
    }
}
