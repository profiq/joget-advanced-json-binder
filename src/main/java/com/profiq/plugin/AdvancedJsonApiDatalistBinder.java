/*
 * Based on the original JsonApiDatalistBinder class available under the GPLv3 license here:
 * https://github.com/jogetworkflow/jw-community/blob/8.0-RELEASE/wflow-core/src/main/java/org/joget/apps/datalist/lib/JsonApiDatalistBinder.java
 */

package com.profiq.plugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.displaytag.tags.TableTagParameters;
import org.joget.apps.app.service.AppUtil;
import org.joget.apps.app.service.JsonApiUtil;
import org.joget.apps.datalist.model.DataList;
import static org.joget.apps.datalist.model.DataList.CHECKBOX_POSITION_BOTH;
import static org.joget.apps.datalist.model.DataList.CHECKBOX_POSITION_LEFT;
import static org.joget.apps.datalist.model.DataList.ORDER_DESCENDING_VALUE;
import org.joget.apps.datalist.model.DataListBinderDefault;
import org.joget.apps.datalist.model.DataListColumn;
import org.joget.apps.datalist.model.DataListCollection;
import org.joget.apps.datalist.model.DataListFilter;
import org.joget.apps.datalist.model.DataListFilterQueryObject;
import org.joget.apps.datalist.model.DataListFilterTypeDefault;
import org.joget.commons.util.LogUtil;
import org.joget.commons.util.PagingUtils;
import org.joget.plugin.property.service.PropertyUtil;
import org.json.JSONObject;

public class AdvancedJsonApiDatalistBinder extends DataListBinderDefault {

    @Override
    public String getClassName() {
        return this.getClass().getName();
    }

    @Override
    public String getName() {
        return "Advanced JSON API Data Binder";
    }

    @Override
    public String getVersion() {
        return "0.2.0";
    }

    @Override
    public String getDescription() {
        return "Retrieves data rows from a JSON API.";
    }

    @Override
    public String getLabel() {
        return "Advanced JSON API Data Binder";
    }

    @Override
    public String getPropertyOptions() {
        return AppUtil.readPluginResource(getClass().getName(), "/properties/AdvancedJsonApiDatalistBinder.json", null, true, "message/AdvancedJsonApiDatalistBinder");
    }

    @Override
    public DataListColumn[] getColumns() {
        Map<String,Object> results = null;
        if (!getPropertyString("sampleResponse").isEmpty()) {
            String jsonResponse = getPropertyString("sampleResponse").trim();
            if (jsonResponse.startsWith("[") && jsonResponse.endsWith("]")) {
                jsonResponse = "{ \"response\" : " + jsonResponse + " }";
            } else if (!jsonResponse.startsWith("{") && !jsonResponse.endsWith("}")) {
                jsonResponse = "{ \"response\" : \"" + jsonResponse + "\" }";
            }
            if ("true".equalsIgnoreCase(getPropertyString("debugMode"))) {
                LogUtil.info(JsonApiUtil.class.getName(), jsonResponse);
            }
            try {
                results = PropertyUtil.getProperties(new JSONObject(jsonResponse));
            } catch (Exception e) {
                LogUtil.error(getClassName(), e, "");
            }
            if (results == null) {
                results = new HashMap<String, Object>();
            }
            setProperty("jsonResult", results);
        } else {
            results = call(null);
        }
        Map<String, DataListColumn> columns = new HashMap<String, DataListColumn>();

        if (results != null) {
            String multirowBaseObject = getPropertyString("multirowBaseObject");
            multirowBaseObject = multirowBaseObject.replaceAll("\\[\\d?\\]", "");

            Object o = results;
            String prefix = "";
            if (multirowBaseObject.contains(".")) {
                prefix = multirowBaseObject.substring(0, multirowBaseObject.indexOf("."));
                o = results.get(prefix);
            }

            recursiveGetColumns(o, columns, prefix, multirowBaseObject);
        }

        List<DataListColumn> temp = new ArrayList<DataListColumn>(columns.values());
        Collections.sort(temp, new Comparator<DataListColumn>() {
            @Override
            public int compare(DataListColumn a, DataListColumn b)
            {
                return a.getName().compareTo(b.getName());
            }
        });

        return temp.toArray(new DataListColumn[0]);
    }

    protected void recursiveGetColumns(Object o,  Map<String, DataListColumn> columns, String prefix, String base) {
        if (prefix.equals(base)) {
            prefix = "";
        }
        if (o instanceof Object[]) {
            Object[] array = (Object[]) o;
            if (array.length > 0) {
                int max = array.length;  //to prevent empty object, loop a few data
                if (max > 5) {
                    max = 5;
                }
                for (int i = 0; i < max; i++) {
                    recursiveGetColumns(array[i], columns, prefix, base);
                }
            }
        } else if (o instanceof Map) {
            if (!prefix.isEmpty()) {
                prefix += ".";
            }
            Map m = (Map) o;
            for (Object k : m.keySet()) {
                recursiveGetColumns(m.get(k), columns, prefix + k.toString(), base);
            }
        } else {
            columns.put(prefix, new DataListColumn(prefix, prefix, true));
        }
    }

    @Override
    public String getPrimaryKeyColumnName() {
        return getPropertyString("primaryKey");
    }

    @Override
    public DataListCollection getData(DataList dataList, Map properties, DataListFilterQueryObject[] filterQueryObjects, String sort, Boolean desc, Integer start, Integer rows) {
        DataListCollection resultList = getJsonApiData(dataList);

        if ("true".equals(getPropertyString("handlePaging"))) {
            List newResultList = PagingUtils.sortAndPage(resultList, sort, desc, start, rows);
            resultList = new DataListCollection();
            resultList.addAll(newResultList);
        }

        return resultList;
    }

    protected DataListCollection getJsonApiData(DataList dataList) {
        DataListCollection resultList = new DataListCollection();
        if (!getProperties().containsKey("jsonResultList")) {
            Map<String,Object> results = call(dataList);
            parseResults(results, resultList);

            // Verify that we got the expected number of items when exporting
            if (dataList.getDataListParam(TableTagParameters.PARAMETER_EXPORTING) != null &&
                !"true".equals(getPropertyString("exportNoIterateAllPages"))) {
                if (!getPropertyString("totalRowCountObject").isEmpty()) {
                    Integer count = Integer.MAX_VALUE;

                    try {
                        Object c = JsonApiUtil.getObjectFromMap(getPropertyString("totalRowCountObject"), results);
                        count = Integer.parseInt(c.toString());
                    } catch (Exception e) {
                        LogUtil.error(getClassName(), e, "");
                    }

                    if ("true".equals(getPropertyString("debugMode"))) {
                        LogUtil.info(getClassName(), "Total size: " + count);
                        LogUtil.info(getClassName(), "Returned size: " + resultList.size());
                    }

                    if (resultList.size() < count) {
                        resultList = new DataListCollection();
                        iterateAllPages(dataList, resultList, count);
                    }

                    // Makes sure the entire list is exported
                    dataList.setPageSize(DataList.MAXIMUM_PAGE_SIZE);
                }
            }

            setProperty("jsonResultList", resultList);
        } else {
            resultList = (DataListCollection) getProperty("jsonResultList");
        }
        return resultList;
    }

    private void iterateAllPages(DataList dataList, DataListCollection resultList, int count) {
        // Reset page size to default
        dataList.setPageSize(null);

        Integer pages = (count / dataList.getPageSize());

        if ("true".equals(getPropertyString("debugMode"))) {
            LogUtil.info(getClassName(), "Number of pages: " + pages);
        }

        for (Integer i = 1; i <= pages; i++) {
            setProperty("pageNumber", Integer.toString(i));
            getProperties().remove("jsonResult"); // hack: force re-fetch

            Map<String, Object> results = call(dataList);
            parseResults(results, resultList);
        }
    }

    private void parseResults(Map<String,Object> results, DataListCollection resultList) {
        if (results != null) {
            String multirowBaseObject = getPropertyString("multirowBaseObject");
            multirowBaseObject = multirowBaseObject.replaceAll("\\[\\d?\\]", "");

            Object o = results;
            String prefix = "";
            if (multirowBaseObject.contains(".")) {
                prefix = multirowBaseObject.substring(0, multirowBaseObject.indexOf("."));
                o = results.get(prefix);
            }

            recursiveGetData(o, resultList, new HashMap<String, Object>(), prefix, multirowBaseObject);
        }
    }

    protected void recursiveGetData(Object o, DataListCollection resultList, Map<String, Object> data, String prefix, String base) {
        if (o instanceof Object[]) {
            Object[] array = (Object[]) o;
            if (array.length > 0) {
                for (int i = 0; i < array.length; i++) {
                    recursiveGetData(array[i], resultList, data, prefix, base);
                }
            }
        } else if (o instanceof Map) {
            if (prefix.equals(base)) {
                Map<String, Object> parentData = data;
                data = new HashMap<String, Object>();
                data.putAll(parentData);
                prefix = "";
                resultList.add(data);
            }

            if (!prefix.isEmpty()) {
                prefix += ".";
            }

            Object last = null;
            String lastKey = "";
            Map m = (Map) o;
            for (Object k : m.keySet()) {
                if ((prefix + k.toString()).equals(base) || base.startsWith(prefix + k.toString())) {
                    last = m.get(k);
                    lastKey = k.toString();
                } else {
                    recursiveGetData(m.get(k), resultList, data, prefix + k.toString(), base);
                }
            }
            if (last != null) {
                recursiveGetData(last, resultList, data, prefix + lastKey, base);
            }
        } else {
            data.put(prefix, o);
        }
    }

    @Override
    public int getDataTotalRowCount(DataList dataList, Map properties, DataListFilterQueryObject[] filterQueryObjects) {
        int count = 0;

        if (getPropertyString("totalRowCountObject").isEmpty()) {
            count = getJsonApiData(dataList).size();
        } else {
            Map<String,Object> results = call(dataList);
            Object c = JsonApiUtil.getObjectFromMap(getPropertyString("totalRowCountObject"), results);

            try {
                count = Integer.parseInt(c.toString());
            } catch (Exception e) {
                LogUtil.error(getClassName(), e, "");
            }
        }

        return count;
    }

    protected Map<String,Object> call(DataList dataList) {
        Map<String,Object> result = null;
        if (!getProperties().containsKey("jsonResult")) {
            Map<String, String> params = null;
            if (dataList != null) {
                params = getQueryParam(dataList);
            }

            String oldUrl = getPropertyString("jsonUrl");
            setProperty("jsonUrl", getApiUrl(params));

            result = JsonApiUtil.callApi(properties, params);
            setProperty("jsonResult", result);
            setProperty("jsonUrl", oldUrl);
        } else {
            result = (Map<String,Object>) getProperty("jsonResult");
        }
        return result;
    }

    private String getApiUrl(Map<String, String> params) {
        String url = getPropertyString("jsonUrl");
        String queryString = "";

        if (params == null) {
            return url;
        }

        Object columns = getProperty("queryParams");
        if (columns != null) {
            for (Object colObj : (Object[]) columns) {
                Map col = (Map) colObj;
                String name = (String) col.get("name");
                String value = (String) col.get("value");

                if (params.containsKey(value)) {
                    String param = params.get(value);

                    if (!param.isEmpty()) {
                        if (!queryString.isEmpty()) {
                            queryString += "&";
                        }
                        queryString += name + "=" + param;

                        if ("true".equals(getPropertyString("debugMode"))) {
                            LogUtil.info(getClassName(), "Adding query param " + name + "=" + param);
                        }
                    }
                }
            }
        }

        if (!queryString.isEmpty()) {
            if (url.contains("?")) {
                return url + "&" + queryString;
            } else {
                return url + "?" + queryString;
            }
        }

        return url;
    }

    protected Map<String, String> getQueryParam(DataList dataList) {
        Map<String, String> param = new HashMap<String, String>();

        String page = dataList.getDataListParamString(TableTagParameters.PARAMETER_PAGE);
        String order = dataList.getDataListParamString(TableTagParameters.PARAMETER_ORDER);
        String sort = dataList.getDataListParamString(TableTagParameters.PARAMETER_SORT);

        // determine start and size
        Integer recordSize = dataList.getPageSize();
        Integer start = 0;

        if (dataList.getDataListParam(TableTagParameters.PARAMETER_EXPORTTYPE) != null && dataList.getDataListParam(TableTagParameters.PARAMETER_EXPORTING) != null) {
            // exporting, set full list
            String pageNumberProperty = getPropertyString("pageNumber");
            if (pageNumberProperty == null || pageNumberProperty.isEmpty()) {
                page = "1";
                dataList.setPageSize(DataList.MAXIMUM_PAGE_SIZE);
                recordSize = DataList.MAXIMUM_PAGE_SIZE;
            } else {
                page = pageNumberProperty;
            }
        }
        if (recordSize == -1) {
            recordSize = null;
        }
        if (page != null && page.trim().length() > 0 && recordSize != null) {
            start = (Integer.parseInt(page) - 1) * recordSize;
        }

        if ("true".equals(getPropertyString("debugMode"))) {
            LogUtil.info(getClassName(), "Getting page " + page + " with " + recordSize + " records");
        }

        // determine sort column & order direction
        String sortColumn = null;
        Boolean desc = false;
        if (sort != null && !sort.trim().isEmpty()) {
            int sortIndex = Integer.parseInt(sort);

            if (dataList.getCheckboxPosition().equals(CHECKBOX_POSITION_LEFT) || dataList.getCheckboxPosition().equals(CHECKBOX_POSITION_BOTH)) {
                sortIndex = sortIndex - 1;
            }

            DataListColumn[] columns = getColumns();
            if (sortIndex < columns.length) {
                sortColumn = columns[sortIndex].getName();
            }
        } else if (dataList.getDefaultSortColumn() != null && !dataList.getDefaultSortColumn().isEmpty()) {
            sortColumn = dataList.getDefaultSortColumn();
            if (ORDER_DESCENDING_VALUE.equals(dataList.getDefaultOrder())) {
                desc = true;
            }
        }
        if (ORDER_DESCENDING_VALUE.equals(order)) {
            desc = true;
        }

        param.put("sort", sortColumn);
        param.put("desc", Boolean.toString(desc));
        param.put("size", (recordSize == null)?"":Integer.toString(recordSize));
        param.put("rows", (recordSize == null)?"":Integer.toString(recordSize));
        param.put("start", Integer.toString(start));
        param.put("page", (page == null) ? "1" : page);

        //filter param
        DataListFilter[] filterList = dataList.getFilters();
        if (filterList != null) {
            for (int i = 0; i < filterList.length; i++) {
                DataListFilter filter = filterList[i];
                DataListFilterTypeDefault type = (DataListFilterTypeDefault) filter.getType();
                String[] values = type.getValues(dataList, filter.getName(), type.getPropertyString("defaultValue"));
                if (values != null && values.length > 0) {
                    param.put(filter.getName(), StringUtils.join(values, ";"));
                } else {
                    param.put(filter.getName(), "");
                }
            }
        }

        return param;
    }

    @Override
    public String getDeveloperMode() {
        return "advanced";
    }
}
