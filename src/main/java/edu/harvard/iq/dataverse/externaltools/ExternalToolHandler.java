package edu.harvard.iq.dataverse.externaltools;

import edu.harvard.iq.dataverse.DataFile;
import edu.harvard.iq.dataverse.Dataset;
import edu.harvard.iq.dataverse.DatasetVersion;
import edu.harvard.iq.dataverse.FileMetadata;
import edu.harvard.iq.dataverse.authorization.users.ApiToken;
import edu.harvard.iq.dataverse.externaltools.ExternalTool.ReservedWord;
import edu.harvard.iq.dataverse.util.SystemConfig;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonReader;

/**
 * Handles an operation on a specific file. Requires a file id in order to be
 * instantiated. Applies logic based on an {@link ExternalTool} specification,
 * such as constructing a URL to access that file.
 */
public class ExternalToolHandler {

    private static final Logger logger = Logger.getLogger(ExternalToolHandler.class.getCanonicalName());

    private final ExternalTool externalTool;
    private final DataFile dataFile;
    private final Dataset dataset;

    private final FileMetadata fileMetadata;

    private final ApiToken apiToken;

    /**
     * @param externalTool The database entity.
     * @param dataFile Required.
     * @param apiToken The apiToken can be null because "explore" tools can be
     * used anonymously.
     */
    public ExternalToolHandler(ExternalTool externalTool, DataFile dataFile, ApiToken apiToken, FileMetadata fileMetadata) {
        this.externalTool = externalTool;
        if (dataFile == null) {
            String error = "A DataFile is required.";
            logger.warning("Error in ExternalToolHandler constructor: " + error);
            throw new IllegalArgumentException(error);
        }
        this.dataFile = dataFile;
        this.apiToken = apiToken;
        this.fileMetadata = fileMetadata;
        dataset = getDataFile().getFileMetadata().getDatasetVersion().getDataset();
    }

    public DataFile getDataFile() {
        return dataFile;
    }

    public ApiToken getApiToken() {
        return apiToken;
    }

    public FileMetadata getFileMetadata() {return fileMetadata;}

    // TODO: rename to handleRequest() to someday handle sending headers as well as query parameters.
    public String getQueryParametersForUrl() {
        String toolParameters = externalTool.getToolParameters();
        JsonReader jsonReader = Json.createReader(new StringReader(toolParameters));
        JsonObject obj = jsonReader.readObject();
        JsonArray queryParams = obj.getJsonArray("queryParameters");
        if (queryParams == null || queryParams.isEmpty()) {
            return "";
        }
        List<String> params = new ArrayList<>();
        queryParams.getValuesAs(JsonObject.class).forEach((queryParam) -> {
            queryParam.keySet().forEach((key) -> {
                String value = queryParam.getString(key);
                String param = getQueryParam(key, value);
                if (param != null && !param.isEmpty()) {
                    params.add(param);
                }
            });
        });
        return "?" + String.join("&", params);
    }

    private String getQueryParam(String key, String value) {
        ReservedWord reservedWord = ReservedWord.fromString(value);
        switch (reservedWord) {
            case FILE_ID:
                // getDataFile is never null because of the constructor
                return key + "=" + getDataFile().getId();
            case SITE_URL:
                return key + "=" + SystemConfig.getDataverseSiteUrlStatic();
            case API_TOKEN:
                String apiTokenString = null;
                ApiToken theApiToken = getApiToken();
                if (theApiToken != null) {
                    apiTokenString = theApiToken.getTokenString();
                    return key + "=" + apiTokenString;
                }
                break;
            case META_ID:
                Long fmId = 0L;
                FileMetadata fm = getFileMetadata ();
                if (fm != null) {
                    fmId = fm.getId();
                    return key + "=" + fmId;
                }
                break;
            case DATASET_ID:
                return key + "=" + dataset.getId();
            case DATASET_VERSION:
                String version = null;
                if (getApiToken() != null) {
                    version = dataset.getLatestVersion().getFriendlyVersionNumber();
                } else {
                    version = dataset.getLatestVersionForCopy().getFriendlyVersionNumber();
                }
                if (("DRAFT").equals(version)) {
                    version = ":draft"; // send the token needed in api calls that can be substituted for a numeric
                                        // version.
                }
                return key + "=" + version;
            default:
                break;
        }
        return null;
    }

    public String getToolUrlWithQueryParams() {
        return externalTool.getToolUrl() + getQueryParametersForUrl();
    }

    public ExternalTool getExternalTool() {
        return externalTool;
    }

}
