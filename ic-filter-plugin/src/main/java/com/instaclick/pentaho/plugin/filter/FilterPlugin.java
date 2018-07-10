package com.instaclick.pentaho.plugin.filter;

import java.net.URI;
import com.instaclick.filter.Data;
import com.instaclick.filter.DataFilter;
import com.instaclick.filter.FilterConfig;
import com.instaclick.filter.FilterFactory;
import com.instaclick.filter.ProviderType;
import com.instaclick.filter.FilterType;
import com.instaclick.filter.HashFunctionType;

import org.pentaho.di.core.exception.KettleException;
import org.pentaho.di.core.exception.KettleStepException;
import org.pentaho.di.core.row.RowMetaInterface;
import org.pentaho.di.trans.Trans;
import org.pentaho.di.trans.TransMeta;
import org.pentaho.di.trans.step.BaseStep;
import org.pentaho.di.trans.step.StepDataInterface;
import org.pentaho.di.trans.step.StepInterface;
import org.pentaho.di.trans.step.StepMeta;
import org.pentaho.di.trans.step.StepMetaInterface;
import static com.instaclick.pentaho.plugin.filter.Messages.getString;
import org.pentaho.di.core.Const;
import org.pentaho.di.core.row.RowDataUtil;
import org.pentaho.di.trans.TransListener;

/**
 * Pentaho filter plugin
 *
 * @author Fabio B. Silva <fabio.bat.silva@gmail.com>
 */
public class FilterPlugin extends BaseStep implements StepInterface
{
    /**
     * Filter data
     */
    private FilterPluginData data;

    /**
     * Filter meta data
     */
    private FilterPluginMeta meta;

    /**
     * Data filter
     */
    private DataFilter filter;

    /**
     * Transformation Listener
     */
    private TransListener transListener = new TransListener() {
        /**
        * {@inheritDoc}
        */
        @Override
        public void transFinished(Trans trans) throws KettleException
        {
            if ( ! data.isTransactional) {
                return;
            }

            if (trans.getErrors() > 0) {
                logMinimal(String.format("Transformation failure, ignoring filter changes", trans.getErrors()));

                return;
            }

            flushFilter();
        }

        @Override
        public void transActive(Trans arg0) {
            // TODO Auto-generated method stub

        }

        @Override
        public void transStarted(Trans arg0) {
            // TODO Auto-generated method stub

        }
    };

    /**
     * {@inheritDoc}
     */
    public FilterPlugin(StepMeta stepMeta, StepDataInterface stepDataInterface, int copyNr, TransMeta transMeta, Trans trans)
    {
        super(stepMeta, stepDataInterface, copyNr, transMeta, trans);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean processRow(StepMetaInterface smi, StepDataInterface sdi) throws KettleException
    {
        meta = (FilterPluginMeta) smi;
        data = (FilterPluginData) sdi;

        Object[] r = getRow();

        if (r == null) {
            setOutputDone();

            return false;
        }

        if (first) {
            first = false;

            initFilter();
        }

        if (r.length < data.timeFieldIndex || r[data.timeFieldIndex] == null) {
            String putErrorMessage = getLinesRead() + " - Ignore invalid timestamp row";

            if (isDebug()) {
                logDebug(putErrorMessage);
            }

            putError(getInputRowMeta(), r, 1, putErrorMessage, null, "ICFilterPlugin001");

            return true;
        }

        StringBuilder hashBuffer = new StringBuilder();
        int lastIndex            = data.fieldsIndex.length > 0
            ? data.fieldsIndex[data.fieldsIndex.length-1]
            : 0;

        for (Integer fieldIndex : data.fieldsIndex) {

            if (r.length < fieldIndex) {
                String putErrorMessage = getLinesRead() + " - Ignore invalid unique row";

                if (isDebug()) {
                    logDebug(putErrorMessage);
                }

                putError(getInputRowMeta(), r, 1, putErrorMessage, null, "ICFilterPlugin000");

                return true;
            }

            hashBuffer.append(String.valueOf(r[fieldIndex]));

            if (fieldIndex != lastIndex) {
                hashBuffer.append("_");
            }
        }

        data.hashValue  = hashBuffer.toString();
        data.timeValue  = Long.parseLong(String.valueOf(r[data.timeFieldIndex]));
        data.filterData = new Data(data.hashValue, data.timeValue);
        data.isUnique   = 1L;
        
        if ( ! filter.add(data.filterData)) {

            if (isDebug()) {
                logDebug("Non unique row : " + data.hashValue);
            }

            if ( ! data.isAlwaysPassRow) {
                return true;
            }

            data.isUnique = 0L;
        }

        //Add unique counter
        data.uniqueCount += data.isUnique;

        if (data.isUnique == 1L) {
            logDebug("Unique row : " + data.hashValue);
        }

        if (data.isAlwaysPassRow) {
            // safely add the unique field at the end of the output row
            r = RowDataUtil.addValueData(r, data.outputRowMeta.size() - 1, data.isUnique);
        }

        // put the row to the output row stream
        putRow(data.outputRowMeta, r);

        // log progress
        if (checkFeedback(getLinesRead())) {
            logBasic(String.format("linenr %s - unique %s", getLinesRead(), data.uniqueCount));
        }

        return true;
    }

    /**
     * Flush filter files
     */
    private void flushFilter()
    {
        logMinimal("Flush filters invoked");

        if (data.isCheckOnly) {
            logMinimal("Check only enabled, Ignoring changes.");

            return;
        }

        if (filter != null) {
            filter.flush();
            logMinimal("Flush filters complete");
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean init(StepMetaInterface smi, StepDataInterface sdi)
    {
        meta = (FilterPluginMeta) smi;
        data = (FilterPluginData) sdi;

        return super.init(smi, sdi);
    }

    /**
     * Initialize filter
     */
    private void initFilter() throws KettleStepException, FilterException
    {
        Integer elements        = Integer.parseInt(environmentSubstitute(meta.getElements()));
        Integer lookups         = Integer.parseInt(environmentSubstitute(meta.getLookups()));
        Integer division        = Integer.parseInt(environmentSubstitute(meta.getDivision()));
        Float probability       = Float.parseFloat(environmentSubstitute(meta.getProbability()));
        String singleFileName   = environmentSubstitute(meta.getSingleFilterFile());
        String timeFieldName    = environmentSubstitute(meta.getTime());
        String uriStr           = environmentSubstitute(meta.getUri());
        HashFunctionType hType  = HashFunctionType.NONE;
        ProviderType pType      = ProviderType.VFS;
        FilterType fType;

        if (timeFieldName == null) {
            throw new FilterException("Unable to retrieve timestamp field name");
        }

        if (uriStr == null) {
            throw new FilterException("Unable to retrieve filter uri");
        }

        if ( ! Const.isEmpty(meta.getHashFunction())) {
            hType = HashFunctionType.valueOf(meta.getHashFunction().trim());
        }

        try {
            URI.create(uriStr);
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage(), e);
        }

        try {
            fType = FilterType.valueOf(meta.getFilter());
        } catch (Exception e) {
            fType = FilterType.BLOOM;
            logError(e.getMessage());
        }

        FilterConfig config = FilterConfig.create()
            .withFalsePositiveProbability(probability)
            .withExpectedNumberOfElements(elements)
            .withFilterFileName(singleFileName)
            .withNumberOfLookups(lookups)
            .withHashFunctionType(hType)
            .withTimeDivision(division)
            .withProvider(pType)
            .withFilter(fType)
            .withURI(uriStr);

        filter = FilterFactory.createFilter(config);

        // clone the input row structure and place it in our data object
        data.outputRowMeta = (RowMetaInterface) getInputRowMeta().clone();
        // use meta.getFields() to change it, so it reflects the output row structure
        meta.getFields(data.outputRowMeta, getStepname(), null, null, this);

        // get field index
        data.fieldsIndex      = new Integer[meta.getUniqueFieldsName().length];
        data.timeFieldIndex   = data.outputRowMeta.indexOfValue(timeFieldName);
        data.isAlwaysPassRow  = meta.isAlwaysPassRow();
        data.isTransactional  = meta.isTransactional();
        data.isCheckOnly      = meta.isCheckOnly();
        data.uniqueCount      = 0L;

        if (data.timeFieldIndex < 0) {
            throw new FilterException("Unable to retrieve time field : " + timeFieldName);
        }

        for (int i = 0; i < meta.getUniqueFieldsName().length; i++) {

            String fieldName = environmentSubstitute(meta.getUniqueFieldsName()[i]);
            int fieldIndex   = data.outputRowMeta.indexOfValue(fieldName);

            if (fieldIndex < 0) {
                throw new FilterException("Unable to retrieve hash field : " + fieldName);
            }

            data.fieldsIndex[i] = fieldIndex;
        }

        logMinimal(getString("FilterPlugin.Fields.Label")       + " : " + meta.getUniqueFieldsNameString());
        logMinimal(getString("FilterPlugin.Time.Label")         + " : " + timeFieldName);
        logMinimal(getString("FilterPlugin.Uri.Label")          + " : " + config.getURI());
        logMinimal(getString("FilterPlugin.FilterType.Label")   + " : " + config.getFilter());
        logMinimal(getString("FilterPlugin.ProviderType.Label") + " : " + config.getProvider());
        logMinimal(getString("FilterPlugin.HashFunction.Label") + " : " + config.getHashFunctionType());

        if (fType == FilterType.BLOOM) {
            logMinimal(getString("FilterPlugin.Elements.Label")    + " : " + config.getExpectedNumberOfElements());
            logMinimal(getString("FilterPlugin.Probability.Label") + " : " + String.format("%.3f%n", config.getFalsePositiveProbability()));
        }

        logMinimal(getString("FilterPlugin.Transactional.Label") +  " : " + data.isTransactional);
        logMinimal(getString("FilterPlugin.AlwaysPassRow.Label") +  " : " + data.isAlwaysPassRow);
        logMinimal(getString("FilterPlugin.CheckOnly.Label")     +  " : " + data.isCheckOnly);
        logMinimal(getString("FilterPlugin.Division.Label")      +  " : " + config.getTimeDivision());
        logMinimal(getString("FilterPlugin.Lookups.Label")       +  " : " + config.getNumberOfLookups());
        logMinimal(getString("FilterPlugin.UniqueField.Label")   +  " : " + meta.getIsUniqueFieldName());

        if (data.isTransactional) {
            getTrans().addTransListener(transListener);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void dispose(StepMetaInterface smi, StepDataInterface sdi)
    {
        meta = (FilterPluginMeta) smi;
        data = (FilterPluginData) sdi;

        if ( ! data.isTransactional) {
            flushFilter();
        }

        super.dispose(smi, sdi);
    }
}