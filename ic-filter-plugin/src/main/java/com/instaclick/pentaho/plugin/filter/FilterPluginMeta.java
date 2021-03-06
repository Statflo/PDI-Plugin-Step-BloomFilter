package com.instaclick.pentaho.plugin.filter;

import com.google.common.base.Joiner;
import java.util.List;
import java.util.Map;

import org.eclipse.swt.widgets.Shell;
import org.pentaho.di.core.CheckResult;
import org.pentaho.di.core.CheckResultInterface;
import org.pentaho.di.core.Const;
import org.pentaho.di.core.Counter;
import org.pentaho.di.core.database.DatabaseMeta;
import org.pentaho.di.core.exception.KettleDatabaseException;
import org.pentaho.di.core.exception.KettleException;
import org.pentaho.di.core.exception.KettleStepException;
import org.pentaho.di.core.exception.KettleXMLException;
import org.pentaho.di.core.row.RowMetaInterface;
import org.pentaho.di.core.row.ValueMeta;
import org.pentaho.di.core.row.ValueMetaInterface;
import org.pentaho.di.core.variables.VariableSpace;
import org.pentaho.di.core.xml.XMLHandler;
import org.pentaho.di.repository.ObjectId;
import org.pentaho.di.repository.Repository;
import org.pentaho.di.trans.Trans;
import org.pentaho.di.trans.TransMeta;
import org.pentaho.di.trans.step.BaseStepMeta;
import org.pentaho.di.trans.step.StepDataInterface;
import org.pentaho.di.trans.step.StepDialogInterface;
import org.pentaho.di.trans.step.StepInterface;
import org.pentaho.di.trans.step.StepMeta;
import org.pentaho.di.trans.step.StepMetaInterface;
import org.w3c.dom.Node;

/**
 * Pentaho filter plugin meta
 *
 * @author Fabio B. Silva <fabio.bat.silva@gmail.com>
 */
public class FilterPluginMeta extends BaseStepMeta implements StepMetaInterface
{
    private static final String FIELD_IS_UNIQUE_FIELD_NAME = "is_unique_field_name";
    private static final String FIELD_ALWAYS_PASS_ROW      = "always_pass_row";
    private static final String FIELD_HASH_FUNCTION        = "hash_function";
    private static final String FIELD_PROBABLILITY         = "probability";
    private static final String FIELD_TRANSACTIONAL        = "transactional";
    private static final String FIELD_CHECK_ONLY           = "check_only";
    private static final String FIELD_UNIQUE_FIELDS_NAME   = "field_names";
    private static final String FIELD_SINGE_FILTER_FILE    = "filter_file";
    private static final String FIELD_ELEMENTS             = "elements";
    private static final String FIELD_LOOKUPS              = "lookups";
    private static final String FIELD_DIVISION             = "division";
    private static final String FIELD_FILTER               = "filter";
    private static final String FIELD_TIME                 = "time";
    private static final String FIELD_URI                  = "uri";

    private String elements;
    private String probability;
    private String uri;
    private String hashFunction;
    private String uniqueFieldName;
    private boolean alwaysPassRow;
    private boolean transactional;
    private boolean checkOnly;
    private String division;
    private String lookups;
    private String time;
    private String filter;
    private String singleFilterFile;

    private String[] uniqueFieldsName;
    
    public FilterPluginMeta() {
        super();
    }

    /**
     * {@inheritDoc}
     */
    public StepDialogInterface getDialog(Shell shell, StepMetaInterface meta, TransMeta transMeta, String name)
    {
        return new FilterPluginDialog(shell, meta, transMeta, name);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public StepInterface getStep(StepMeta stepMeta, StepDataInterface stepDataInterface, int cnr, TransMeta transMeta, Trans disp)
    {
        return new FilterPlugin(stepMeta, stepDataInterface, cnr, transMeta, disp);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public StepDataInterface getStepData()
    {
        return new FilterPluginData();
    }

    @Override
    public void getFields(RowMetaInterface inputRowMeta, String name, RowMetaInterface[] info, StepMeta nextStep, VariableSpace space) throws KettleStepException
    {
        if ( ! isAlwaysPassRow()) {
            return;
        }

        // a value meta object contains the meta data for a field
        ValueMetaInterface v = new ValueMeta(getIsUniqueFieldName(), ValueMeta.TYPE_INTEGER);
        // the name of the step that adds this field
        v.setOrigin(name);
        // modify the row structure and add the field this step generates
        inputRowMeta.addValueMeta(v);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void check(List<CheckResultInterface> remarks, TransMeta transmeta, StepMeta stepMeta, RowMetaInterface prev, String[] input, String[] output, RowMetaInterface info)
    {

        CheckResult prevSizeCheck = (prev == null || prev.isEmpty())
            ? new CheckResult(CheckResult.TYPE_RESULT_WARNING, "Not receiving any fields from previous steps!", stepMeta)
            : new CheckResult(CheckResult.TYPE_RESULT_OK, "Step is connected to previous one, receiving " + prev.size() + " fields", stepMeta);

        /// See if we have input streams leading to this step!
        CheckResult inputLengthCheck = (input.length > 0)
            ? new CheckResult(CheckResult.TYPE_RESULT_OK, "Step is receiving info from other steps.", stepMeta)
            : new CheckResult(CheckResult.TYPE_RESULT_ERROR, "No input received from other steps!", stepMeta);

        for (String fieldName : getUniqueFieldsName()) {
            CheckResult hashFieldCheck = ((prev == null) || (prev.indexOfValue(fieldName) < 0))
            ? new CheckResult(CheckResult.TYPE_RESULT_ERROR, "Field " + fieldName + "not found.", stepMeta)
            : new CheckResult(CheckResult.TYPE_RESULT_OK, "Field " + fieldName + "found.", stepMeta);

            remarks.add(hashFieldCheck);
        }

        CheckResult timeFieldCheck = ((prev == null) || (prev.indexOfValue(getTime()) < 0))
            ? new CheckResult(CheckResult.TYPE_RESULT_ERROR, "Timestamp field not found.", stepMeta)
            : new CheckResult(CheckResult.TYPE_RESULT_OK, "Timestamp field found.", stepMeta);

        CheckResult uniqueFieldCheck = isAlwaysPassRow() && Const.isEmpty(getIsUniqueFieldName())
            ? new CheckResult(CheckResult.TYPE_RESULT_ERROR, "Invalid unique field.", stepMeta)
            : new CheckResult(CheckResult.TYPE_RESULT_OK, "Unique field found.", stepMeta);

        remarks.add(prevSizeCheck);
        remarks.add(inputLengthCheck);
        remarks.add(timeFieldCheck);
        remarks.add(uniqueFieldCheck);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getXML()
    {
        final StringBuilder bufer = new StringBuilder();

        bufer.append("   ").append(XMLHandler.addTagValue(FIELD_UNIQUE_FIELDS_NAME, getUniqueFieldsNameString()));
        bufer.append("   ").append(XMLHandler.addTagValue(FIELD_IS_UNIQUE_FIELD_NAME, getIsUniqueFieldName()));
        bufer.append("   ").append(XMLHandler.addTagValue(FIELD_SINGE_FILTER_FILE, getSingleFilterFile()));
        bufer.append("   ").append(XMLHandler.addTagValue(FIELD_ALWAYS_PASS_ROW, isAlwaysPassRow()));
        bufer.append("   ").append(XMLHandler.addTagValue(FIELD_TRANSACTIONAL, isTransactional()));
        bufer.append("   ").append(XMLHandler.addTagValue(FIELD_HASH_FUNCTION, getHashFunction()));
        bufer.append("   ").append(XMLHandler.addTagValue(FIELD_PROBABLILITY, getProbability()));
        bufer.append("   ").append(XMLHandler.addTagValue(FIELD_CHECK_ONLY, isCheckOnly()));
        bufer.append("   ").append(XMLHandler.addTagValue(FIELD_ELEMENTS, getElements()));
        bufer.append("   ").append(XMLHandler.addTagValue(FIELD_DIVISION, getDivision()));
        bufer.append("   ").append(XMLHandler.addTagValue(FIELD_LOOKUPS, getLookups()));
        bufer.append("   ").append(XMLHandler.addTagValue(FIELD_FILTER, getFilter()));
        bufer.append("   ").append(XMLHandler.addTagValue(FIELD_TIME, getTime()));
        bufer.append("   ").append(XMLHandler.addTagValue(FIELD_URI, getUri()));

        return bufer.toString();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void loadXML(Node stepnode, List<DatabaseMeta> databases, Map<String, Counter> counters) throws KettleXMLException
    {
        try {
            setUniqueFieldsNameString(XMLHandler.getTagValue(stepnode, FIELD_UNIQUE_FIELDS_NAME));
            setIsUniqueFieldName(XMLHandler.getTagValue(stepnode, FIELD_IS_UNIQUE_FIELD_NAME));
            setSingleFilterFile(XMLHandler.getTagValue(stepnode, FIELD_SINGE_FILTER_FILE));
            setAlwaysPassRow(XMLHandler.getTagValue(stepnode, FIELD_ALWAYS_PASS_ROW));
            setTransactional(XMLHandler.getTagValue(stepnode, FIELD_TRANSACTIONAL));
            setHashFunction(XMLHandler.getTagValue(stepnode, FIELD_HASH_FUNCTION));
            setProbability(XMLHandler.getTagValue(stepnode, FIELD_PROBABLILITY));
            setCheckOnly(XMLHandler.getTagValue(stepnode, FIELD_CHECK_ONLY));
            setElements(XMLHandler.getTagValue(stepnode, FIELD_ELEMENTS));
            setDivision(XMLHandler.getTagValue(stepnode, FIELD_DIVISION));
            setLookups(XMLHandler.getTagValue(stepnode, FIELD_LOOKUPS));
            setFilter(XMLHandler.getTagValue(stepnode, FIELD_FILTER));
            setTime(XMLHandler.getTagValue(stepnode, FIELD_TIME));
            setUri(XMLHandler.getTagValue(stepnode, FIELD_URI));

        } catch (Exception e) {
            throw new KettleXMLException("Unable to read step info from XML node", e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void readRep(Repository rep, ObjectId idStep, List<DatabaseMeta> databases, Map<String, Counter> counters) throws KettleException
    {
        try {

            setUniqueFieldsNameString(rep.getStepAttributeString(idStep, FIELD_UNIQUE_FIELDS_NAME));
            setIsUniqueFieldName(rep.getStepAttributeString(idStep, FIELD_IS_UNIQUE_FIELD_NAME));
            setSingleFilterFile(rep.getStepAttributeString(idStep, FIELD_SINGE_FILTER_FILE));
            setAlwaysPassRow(rep.getStepAttributeBoolean(idStep, FIELD_ALWAYS_PASS_ROW));
            setTransactional(rep.getStepAttributeString(idStep, FIELD_TRANSACTIONAL));
            setHashFunction(rep.getStepAttributeString(idStep, FIELD_HASH_FUNCTION));
            setProbability(rep.getStepAttributeString(idStep, FIELD_PROBABLILITY));
            setCheckOnly(rep.getStepAttributeString(idStep, FIELD_CHECK_ONLY));
            setElements(rep.getStepAttributeString(idStep, FIELD_ELEMENTS));
            setDivision(rep.getStepAttributeString(idStep, FIELD_DIVISION));
            setLookups(rep.getStepAttributeString(idStep, FIELD_LOOKUPS));
            setFilter(rep.getStepAttributeString(idStep, FIELD_FILTER));
            setTime(rep.getStepAttributeString(idStep, FIELD_TIME));
            setUri(rep.getStepAttributeString(idStep, "uri"));

        } catch (KettleDatabaseException dbe) {
            throw new KettleException("error reading step with id_step=" + idStep + " from the repository", dbe);
        } catch (Exception e) {
            throw new KettleException("Unexpected error reading step with id_step=" + idStep + " from the repository", e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void saveRep(Repository rep, ObjectId idTransformation, ObjectId idStep) throws KettleException
    {
        try {

            rep.saveStepAttribute(idTransformation, idStep, FIELD_UNIQUE_FIELDS_NAME, getUniqueFieldsNameString());
            rep.saveStepAttribute(idTransformation, idStep, FIELD_IS_UNIQUE_FIELD_NAME, getIsUniqueFieldName());
            rep.saveStepAttribute(idTransformation, idStep, FIELD_SINGE_FILTER_FILE, getSingleFilterFile());
            rep.saveStepAttribute(idTransformation, idStep, FIELD_ALWAYS_PASS_ROW, isAlwaysPassRow());
            rep.saveStepAttribute(idTransformation, idStep, FIELD_TRANSACTIONAL, isTransactional());
            rep.saveStepAttribute(idTransformation, idStep, FIELD_HASH_FUNCTION, getHashFunction());
            rep.saveStepAttribute(idTransformation, idStep, FIELD_PROBABLILITY, getProbability());
            rep.saveStepAttribute(idTransformation, idStep, FIELD_CHECK_ONLY, isCheckOnly());
            rep.saveStepAttribute(idTransformation, idStep, FIELD_ELEMENTS, getElements());
            rep.saveStepAttribute(idTransformation, idStep, FIELD_DIVISION, getDivision());
            rep.saveStepAttribute(idTransformation, idStep, FIELD_LOOKUPS, getLookups());
            rep.saveStepAttribute(idTransformation, idStep, FIELD_FILTER, getFilter());
            rep.saveStepAttribute(idTransformation, idStep, FIELD_TIME, getTime());
            rep.saveStepAttribute(idTransformation, idStep, FIELD_URI, getUri());

        } catch (KettleDatabaseException dbe) {
            throw new KettleException("Unable to save step information to the repository, id_step=" + idStep, dbe);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setDefault()
    {
        this.uniqueFieldsName  = new String[] {};
        this.elements    = "1000";
        this.probability = "0.1";

        this.uniqueFieldName = "is_unique";
        this.transactional   = false;
        this.alwaysPassRow   = false;
        this.checkOnly       = false;
        this.hashFunction    = null;

        this.uri        = "tmp://ic-filter/";
        this.division   = "60";
        this.lookups    = "1440";
        this.filter     = "BLOOM";
        this.time       = "timestamp";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean supportsErrorHandling() 
    {
        return true;
    }

    public String getElements()
    {
        return elements;
    }

    public void setElements(String expectedNumberOfElements)
    {
        this.elements = expectedNumberOfElements;
    }

    public String getProbability()
    {
        return probability;
    }

    public void setProbability(String falsePositiveProbability)
    {
        this.probability = falsePositiveProbability;
    }

    public String getUri()
    {
        return uri;
    }

    public void setUri(String uri)
    {
        this.uri = uri;
    }

    public String getDivision()
    {
        return division;
    }

    public void setDivision(String timeDivision)
    {
        this.division = timeDivision;
    }

    public String getLookups()
    {
        return lookups;
    }

    public void setLookups(String numberOfLookups)
    {
        this.lookups = numberOfLookups;
    }

    public String getFilter()
    {
        return filter;
    }

    public void setFilter(String filter)
    {
        this.filter = filter;
    }

    public String getTime()
    {
        return time;
    }

    public void setTime(String timeFieldName)
    {
        this.time = timeFieldName;
    }

    public boolean isTransactional()
    {
        return transactional;
    }

    public void setTransactional(String transactional)
    {
        this.transactional = Boolean.TRUE.toString().equals(transactional) || "Y".equals(transactional);
    }

    public void setTransactional(boolean transactional)
    {
        this.transactional = transactional;
    }

    public boolean isCheckOnly()
    {
        return checkOnly;
    }

    public void setCheckOnly(String flag)
    {
        this.checkOnly = Boolean.TRUE.toString().equals(flag) || "Y".equals(flag);
    }

    public void setCheckOnly(boolean flag)
    {
        this.checkOnly = flag;
    }

    public boolean isAlwaysPassRow()
    {
        return alwaysPassRow;
    }

    public void setAlwaysPassRow(String alwaysGiveRow)
    {
        this.alwaysPassRow = Boolean.TRUE.toString().equals(alwaysGiveRow) || "Y".equals(alwaysGiveRow);
    }

    public void setAlwaysPassRow(boolean alwaysGiveRow)
    {
        this.alwaysPassRow = alwaysGiveRow;
    }

    public String getIsUniqueFieldName()
    {
        if (Const.isEmpty(uniqueFieldName)) {
            uniqueFieldName = "is_unique";
        }

        return uniqueFieldName;
    }

    public void setIsUniqueFieldName(String uniqueRowName)
    {
        this.uniqueFieldName = uniqueRowName;
    }

    public String getHashFunction()
    {
        return hashFunction;
    }

    public void setHashFunction(String hashFunction)
    {
        this.hashFunction = hashFunction;
    }

    public String getSingleFilterFile()
    {
        return singleFilterFile;
    }

    public void setSingleFilterFile(String filterFile)
    {
        this.singleFilterFile = filterFile;
    }

    public void setUniqueFieldsName(String[] fieldsName)
    {
        this.uniqueFieldsName = fieldsName;
    }

    public void setUniqueFieldsNameString(String fieldsName)
    {
        if (fieldsName != null) {
            this.uniqueFieldsName = fieldsName.split(",");
        }
    }

    public String getUniqueFieldsNameString()
    {
        return Joiner.on(",").join(getUniqueFieldsName());
    }

    public String[] getUniqueFieldsName()
    {
        if (this.uniqueFieldsName == null) {
            this.uniqueFieldsName = new String[]{};
        }

        return this.uniqueFieldsName;
    }
}
