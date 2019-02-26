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
    @Path("{fileId}")
    public Response edit (InputStream body, @PathParam("fileId") String fileId) {
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

        //DatasetVersion newDatasetVersion = dataFile.getOwner().getEditVersion();
        //fileMetadata = newDatasetVersion.getFileMetadatas().get(0);


        Map<Long, VariableMetadata> mapVarToVarMet = new HashMap<Long, VariableMetadata>();
        Map<Long,VarGroup> varGroupMap = new HashMap<Long, VarGroup>();
        try {
            readXML(body, mapVarToVarMet,varGroupMap);
        } catch (XMLStreamException e) {
            logger.warning(e.getMessage());
            return error(Response.Status.NOT_ACCEPTABLE, "bad xml file" );
        }



        if (mapVarToVarMet != null && mapVarToVarMet.size() > 0) {

            //create new version
            Dataset dataset = dataFile.getOwner();
            DatasetVersion newDatasetVersion = dataFile.getOwner().getEditVersion();
            List<FileMetadata> fml = newDatasetVersion.getFileMetadatas();

            DatasetVersion latestDatasetVersion = dataFile.getOwner().getLatestVersion();
            List<FileMetadata> latestFml = latestDatasetVersion.getFileMetadatas();


            ArrayList<VariableMetadata> neededToUpdateVM = checkVariableData(mapVarToVarMet, latestFml.get(0) );

            if (neededToUpdateVM.size() > 0) {

                if (newDatasetVersion.getId() == null) {
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



                    /*StringBuilder saveError = new StringBuilder();

                    FileMetadata fileMet = fml.get(0);


                    try {
                        FileMetadata newFileMetadata = em.merge(fileMet);

                        logger.fine("Successfully saved DataFile " + fileMet.getLabel() + " in the database.");
                    } catch (EJBException ex) {
                        saveError.append(ex).append(" ");
                        saveError.append(ex.getMessage()).append(" ");
                        Throwable cause = ex;
                        while (cause.getCause() != null) {
                            cause = cause.getCause();
                            saveError.append(cause).append(" ");
                            saveError.append(cause.getMessage()).append(" ");
                        }
                    }*/
                }
                for (int i = 0; i < neededToUpdateVM.size();i++) {
                    VariableMetadata vm = neededToUpdateVM.get(i);
                    List<VariableMetadata> vml = variableService.findByDataVarIdAndFileMetaId(vm.getDataVariable().getId(), fml.get(0).getId());
                    if (vml.size() > 0) {
                        vm.setId(vml.get(0).getId());

                        for (CategoryMetadata cm : vm.getWfreq()) {
                            List<CategoryMetadata> cms = variableService.findCategoryMetadata(cm.getCategory().getId(), vm.getId());
                            if (cms.size() > 0) {
                                cm.setId(cms.get(0).getId());
                            }

                        }
                    }
                    vm.setFileMetadata(fml.get(0));
                    em.merge(vm);
                }
            }
        }

        return ok("Updated");
    }



    private  void readXML(InputStream body, Map<Long,VariableMetadata> mapVarToVarMet, Map<Long,VarGroup> varGroupMap) throws XMLStreamException {

        XMLInputFactory factory=XMLInputFactory.newInstance();
        XMLStreamReader xmlr=factory.createXMLStreamReader(body);
        VariableMetadataDDIParser vmdp = new VariableMetadataDDIParser();

        vmdp.processDataDscr(xmlr,mapVarToVarMet, varGroupMap);

    }

    private ArrayList checkVariableData(Map<Long, VariableMetadata> mapVarToVarMet, FileMetadata fm) {


        ArrayList<VariableMetadata> neededToUpdateVM = new ArrayList<VariableMetadata>();

        for ( VariableMetadata varMet : mapVarToVarMet.values()) {
            boolean noUpdate = true;

            Long varId = varMet.getDataVariable().getId();
            DataVariable dv = variableService.find(varId);
            List<VariableMetadata> vml = variableService.findByDataVarIdAndFileMetaId(varMet.getDataVariable().getId(), fm.getId());
            if (vml.size() > 0) {
                noUpdate = compareVarMetadata(vml.get(0), varMet);
            } else {
                noUpdate = AreNotDefaultValues(varMet,dv);
            }

            if (noUpdate) continue;

            varMet.setDataVariable(dv);
            Collection<CategoryMetadata> cms = varMet.getWfreq();
            for (CategoryMetadata cm : cms) {
                String catValue = cm.getCategory().getValue();
                VariableCategory vc = variableService.findCategory(varId,catValue).get(0);
                cm.setCategory(vc);
            }
            neededToUpdateVM.add(varMet);
        }

        return neededToUpdateVM;
    }

    private boolean  compareVarMetadata(VariableMetadata vmOld, VariableMetadata vmNew) {
        boolean thesame = true;

        if (vmOld.getNotes() == null && vmNew.getNotes() != null && !vmNew.getNotes().trim().equals("")) {
            thesame = false;
        } else if (vmNew.getNotes() == null && vmOld.getNotes() != null && !vmOld.getNotes().trim().equals("")) {
            thesame = false;
        }else if (vmOld.getNotes() != null && vmNew.getNotes() != null && !vmOld.getNotes().equals(vmNew.getNotes())) {
            thesame = false;
        } else if ( vmOld.getUniverse() == null && vmNew.getUniverse() != null && !vmNew.getUniverse().trim().equals("")) {
            thesame = false;
        } else if (vmNew.getUniverse() == null && vmOld.getUniverse() != null && !vmOld.getUniverse().trim().equals("")) {
                thesame = false;
        } else if (vmOld.getUniverse() != null && vmNew.getUniverse() != null && !vmOld.getUniverse().equals(vmNew.getUniverse())) {
            thesame = false;
        } else if (vmOld.getInterviewinstruction() == null && vmNew.getInterviewinstruction() != null && !vmNew.getInterviewinstruction().trim().equals("")) {
            thesame = false;
        } else if (  vmNew.getInterviewinstruction() == null && vmOld.getInterviewinstruction() != null && !vmOld.getInterviewinstruction().trim().equals("")) {
            thesame = false;
        } else if (vmOld.getInterviewinstruction() != null && vmNew.getInterviewinstruction() != null && !vmOld.getInterviewinstruction().equals(vmNew.getInterviewinstruction())) {
            thesame = false;
        } else  if (vmOld.getLiteralquestion() == null && vmNew.getLiteralquestion() != null && !vmNew.getLiteralquestion().trim().equals("")) {
            thesame = false;
        } else if (vmNew.getLiteralquestion() == null && vmOld.getLiteralquestion() != null && !vmOld.getLiteralquestion().trim().equals("")) {
            thesame = false;
        } else if (vmNew.getLiteralquestion() != null && vmOld.getLiteralquestion() != null && !vmOld.getLiteralquestion().equals(vmNew.getLiteralquestion())) {
            thesame = false;
        } else  if (vmOld.getLabel() == null && vmNew.getLabel() != null && !vmNew.getLabel().trim().equals("")) {
            thesame = false;
        } else if  (vmNew.getLabel() == null && vmOld.getLabel() != null && !vmOld.getLabel().trim().equals("")) {
            thesame = false;
        } else if (vmNew.getLabel() != null && vmOld.getLabel() != null && !vmOld.getLabel().equals(vmNew.getLabel())) {
            thesame = false;
        } else if (vmOld.isIsweightvar() != vmNew.isIsweightvar() ) {
            thesame = false;
        } else if (vmOld.isWeighted() != vmOld.isWeighted()) {
            thesame = false;
        } else if (vmOld.isWeighted() == vmOld.isWeighted()) {
            if (vmOld.isWeighted() && vmOld.getWeightvariable().getId() != vmNew.getWeightvariable().getId()) {
                thesame = false;
            }
        } else {
            ArrayList<CategoryMetadata> cmsOld = (ArrayList) vmOld.getWfreq();
            ArrayList<CategoryMetadata> cmsNew = (ArrayList) vmNew.getWfreq();


            if (cmsOld.size() != cmsNew.size())
                return false;
            else {
                Collections.sort((ArrayList) cmsOld);
                Collections.sort((ArrayList) cmsNew);
                for (int i=0; i< cmsOld.size(); i++) {
                    if (cmsOld.get(i) != cmsOld.get(i))
                        return false;
                }
            }
        }

        return thesame;

    }

    private boolean AreNotDefaultValues(VariableMetadata varMet, DataVariable dv) {
        boolean thedefault = true;

        if (varMet.getNotes() != null && !varMet.getNotes().equals("")) {
            thedefault = false;
        } else if (varMet.getUniverse() != null && !varMet.getUniverse().equals("") ) {
            thedefault = false;
        } else if (varMet.getInterviewinstruction() != null && !varMet.getInterviewinstruction().equals("")) {
            thedefault = false;
        } else if (varMet.getLiteralquestion() != null && varMet.getLiteralquestion().equals("")) {
            thedefault = false;
        } else if (dv.getLabel() != null && !dv.getLabel().equals(varMet.getLabel())) {
            thedefault = false;
        } else if (varMet.isIsweightvar() != false ) {
            thedefault = false;
        } else if (varMet.isWeighted() != false) {
            thedefault = false;
        } else {

        }

        ArrayList<CategoryMetadata> cms = (ArrayList) varMet.getWfreq();

        if (cms != null && cms.size()>0) {
            thedefault = true;
        }

        return thedefault;
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

    private boolean StringUtilisEmpty(String str) {
        if (str == null || str.trim().equals("")) {
            return true;
        }
        return false;
    }




}


