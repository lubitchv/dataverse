package edu.harvard.iq.dataverse.api;

import edu.harvard.iq.dataverse.*;
import edu.harvard.iq.dataverse.api.imports.ImportGenericServiceBean;
import edu.harvard.iq.dataverse.authorization.AuthenticationProvider;
import edu.harvard.iq.dataverse.authorization.Permission;
import edu.harvard.iq.dataverse.authorization.exceptions.AuthorizationSetupException;
import edu.harvard.iq.dataverse.authorization.providers.AuthenticationProviderRow;
import edu.harvard.iq.dataverse.authorization.users.User;
import edu.harvard.iq.dataverse.datavariable.*;
import edu.harvard.iq.dataverse.engine.command.Command;
import edu.harvard.iq.dataverse.engine.command.DataverseRequest;
import edu.harvard.iq.dataverse.engine.command.exception.CommandException;
import edu.harvard.iq.dataverse.engine.command.impl.UpdateDatasetVersionCommand;
import edu.harvard.iq.dataverse.search.IndexServiceBean;
import edu.harvard.iq.dataverse.settings.SettingsServiceBean;
import edu.harvard.iq.dataverse.util.BundleUtil;
import edu.harvard.iq.dataverse.util.SystemConfig;

import javax.ejb.EJB;
import javax.ejb.EJBException;
import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.faces.application.FacesMessage;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.Query;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.*;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.UriInfo;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamReader;
import java.io.InputStream;

import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.sql.Timestamp;

import static edu.harvard.iq.dataverse.util.JsfHelper.JH;
import static edu.harvard.iq.dataverse.util.json.JsonPrinter.json;
import static javax.ejb.TransactionAttributeType.REQUIRES_NEW;

@Stateless
@Path("edit")
public class EditDDI  extends AbstractApiBean {
    private static final Logger logger = Logger.getLogger(Access.class.getCanonicalName());

    //private static final String API_KEY_HEADER = "X-Dataverse-key";

    @PersistenceContext(unitName = "VDCNet-ejbPU")
    private EntityManager em;

    @EJB
    PermissionServiceBean permissionService;

    @EJB
    ImportGenericServiceBean importGenericService;

    @EJB
    VariableServiceBean variableService;

    @EJB
    EjbDataverseEngine commandEngine;

    @EJB
    IndexServiceBean indexService;
    @Inject
    DataverseRequestServiceBean dvRequestService;

    private List<FileMetadata> filesToBeDeleted = new ArrayList<>();

    @PUT
    @Consumes("application/xml")
    @Path("{fileId}/{metaId}")
    public Response edit (InputStream body, @PathParam("fileId") String fileId, @PathParam("metaId") String metaId) {
        DataFile dataFile = null;
        FileMetadata fileMetadata = null;
        try {
            dataFile = findDataFileOrDie(fileId);
            System.out.println("Hi");
        } catch (WrappedResponse ex) {
            return ex.getResponse();
        }

        if (!checkAuth(dataFile)) {
            return unauthorized("Cannot edit metadata, access denied" );
        }

        if (metaId != null && !metaId.equals("")) {
            try {
                fileMetadata = findMetadataOrDie(metaId);
            } catch (WrappedResponse ex) {
                return ex.getResponse();
            }
        } else {
            DatasetVersion newDatasetVersion = dataFile.getOwner().getEditVersion();
            fileMetadata = newDatasetVersion.getFileMetadatas().get(0);
        }

        Map<Long, VariableMetadata> mapVarToVarMet = null;
        try {
            mapVarToVarMet  = readXML(body, fileMetadata);
        } catch (XMLStreamException e) {
            logger.warning(e.getMessage());
            return error(Response.Status.NOT_ACCEPTABLE, "bad xml file" );
        }

        if (mapVarToVarMet != null && mapVarToVarMet.size() > 0) {

            //create new version
            Dataset dataset = dataFile.getOwner();
            DatasetVersion newDatasetVersion = dataFile.getOwner().getEditVersion();
            //newDatasetVersion.setCreateTime(new Timestamp(new Date().getTime()));
            //newDatasetVersion.setLastUpdateTime(new Timestamp(new Date().getTime()));

            List<FileMetadata> fml = newDatasetVersion.getFileMetadatas();

            if (newDatasetVersion.getId() == null ) {
                //for new draft version
                Command<Dataset> cmd;
                try {
                    cmd = new UpdateDatasetVersionCommand(dataset, dvRequestService.getDataverseRequest(), filesToBeDeleted);
                    ((UpdateDatasetVersionCommand) cmd).setValidateLenient(true);
                    dataset = commandEngine.submit(cmd);

                } catch (EJBException ex) {
                    StringBuilder error = new StringBuilder();
                    error.append(ex).append(" ");
                    error.append(ex.getMessage()).append(" ");
                    Throwable cause = ex;
                    while (cause.getCause() != null) {
                        cause = cause.getCause();
                        error.append(cause).append(" ");
                        error.append(cause.getMessage()).append(" ");
                    }
                    logger.log(Level.INFO, "Couldn''t save dataset: {0}", error.toString());
                    populateDatasetUpdateFailureMessage();
                    return null;
                } catch (CommandException ex) {
                    //FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(FacesMessage.SEVERITY_ERROR, "Dataset Save Failed", " - " + ex.toString()));
                    logger.log(Level.INFO, "Couldn''t save dataset: {0}", ex.getMessage());
                    populateDatasetUpdateFailureMessage();
                    return null;
                }
            } else {

                Timestamp updateTime = new Timestamp(new Date().getTime());

                newDatasetVersion.setLastUpdateTime(updateTime);
                dataset.setModificationTime(updateTime);

                StringBuilder saveError = new StringBuilder();

                FileMetadata fileMet = fml.get(0);


                try {
                        //DataFile savedDatafile = datafileService.save(fileMetadata.getDataFile());
                        FileMetadata newFileMetadata = em.merge(fileMet);
                        em.flush();

                        logger.fine("Successfully saved DataFile "+fileMet.getLabel()+" in the database.");
                } catch (EJBException ex) {
                        saveError.append(ex).append(" ");
                        saveError.append(ex.getMessage()).append(" ");
                        Throwable cause = ex;
                        while (cause.getCause() != null) {
                            cause = cause.getCause();
                            saveError.append(cause).append(" ");
                            saveError.append(cause.getMessage()).append(" ");
                        }
                }
            }

            for ( VariableMetadata value : mapVarToVarMet.values()) {
                value.setFileMetadata(fml.get(0));
                em.merge(value);
            }

        }


        return ok("Updated");
    }

    private  Map<Long, VariableMetadata> readXML(InputStream body, FileMetadata fileMetadata) throws XMLStreamException {

        XMLInputFactory factory=XMLInputFactory.newInstance();
        XMLStreamReader xmlr=factory.createXMLStreamReader(body);
        DataVariableImportDDI dti = new DataVariableImportDDI(variableService);
        Map<Long, VariableMetadata> vm = dti.processDataDscr(xmlr, fileMetadata);

        return vm;
    }


    private boolean checkAuth(DataFile dataFile) {

        boolean auth = false;

        if (permissionService.on(dataFile.getOwner()).has(Permission.EditDataset)) {
            //return permissionsWrapper.notAuthorized();
            auth = true;
        } else {

            User apiTokenUser = null;
            String apiToken = getRequestApiKey();

            if ((apiToken != null) && (apiToken.length() != 64)) {

                try {
                    apiTokenUser = findUserOrDie();
                } catch (WrappedResponse wr) {
                    auth = false;
                    logger.log(Level.FINE, "Message from findUserOrDie(): {0}", wr.getMessage());
                }

                if (apiTokenUser != null) {
                    // used in an API context
                    if (permissionService.requestOn(createDataverseRequest(apiTokenUser), dataFile.getOwner()).has(Permission.EditDataset)) {
                        auth = true;
                    }
                }
            }
        }
        return auth;

    }

    private void populateDatasetUpdateFailureMessage(){

        JH.addMessage(FacesMessage.SEVERITY_FATAL,  BundleUtil.getStringFromBundle("dataset.message.datasetversionfailure"));
    }




}
