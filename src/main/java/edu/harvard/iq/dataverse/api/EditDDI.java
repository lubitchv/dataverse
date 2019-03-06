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

            DatasetVersion latestDatasetVersion = dataFile.getOwner().getLatestVersionForCopy();
            List<FileMetadata> latestFml = latestDatasetVersion.getFileMetadatas();


            if (newDatasetVersion.getId() == null) {
                //for new draft version

                boolean groupUpdate = newGroups(varGroupMap,latestFml.get(0));
                boolean varUpdate = isNewDraftVersion(mapVarToVarMet, latestFml.get(0));
                if (varUpdate || groupUpdate) {

                    Command<Dataset> cmd;
                    try {
                        Timestamp updateTime = new Timestamp(new Date().getTime());

                        newDatasetVersion.setCreateTime(updateTime);
                        dataset.setModificationTime(updateTime);


                        cmd = new UpdateDatasetVersionCommand(dataset, dvRequestService.getDataverseRequest(), filesToBeDeleted);
                        ((UpdateDatasetVersionCommand) cmd).setValidateLenient(true);
                        dataset = commandEngine.submit(cmd);

                        for (VariableMetadata varMet : mapVarToVarMet.values()) {
                                varMet.setFileMetadata(fml.get(0));
                                em.merge(varMet);
                        }

                        //add New groups
                        for (VarGroup varGroup : varGroupMap.values()) {
                                varGroup.setFileMetadata(fml.get(0));
                                varGroup.setId(null);
                                em.merge(varGroup);
                        }


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

                        return null;
                    } catch (CommandException ex) { ;
                        logger.log(Level.INFO, "Couldn''t save dataset: {0}", ex.getMessage());
                        return null;
                    }
                }
            } else {

               ArrayList<VariableMetadata> neededToUpdateVM = checkVariableData(mapVarToVarMet, fml.get(0));

                Timestamp updateTime = new Timestamp(new Date().getTime());

                newDatasetVersion.setLastUpdateTime(updateTime);
                dataset.setModificationTime(updateTime);


                for (int i = 0; i < neededToUpdateVM.size(); i++) {
                    VariableMetadata vm = neededToUpdateVM.get(i);
                    List<VariableMetadata> vml = variableService.findByDataVarIdAndFileMetaId(vm.getDataVariable().getId(), fml.get(0).getId());
                    if (vml.size() > 0) {
                        vm.setId(vml.get(0).getId());
                        if (!vm.isWeighted() && vml.get(0).isWeighted()) { //unweight the variable
                            for (CategoryMetadata cm : vml.get(0).getCategoriesMetadata()) {
                                CategoryMetadata oldCm = em.find(CategoryMetadata.class, cm.getId());
                                em.remove(oldCm);
                            }
                        } else {
                            for (CategoryMetadata cm : vml.get(0).getCategoriesMetadata()) { // update categories
                                List<CategoryMetadata> cms = variableService.findCategoryMetadata(cm.getCategory().getId(), vml.get(0).getId());
                                if (cms.size() > 0) {
                                    for (CategoryMetadata cmNew : vm.getCategoriesMetadata()) {
                                        if (cms.get(0).getCategory().getValue().equals(cmNew.getCategory().getValue())) {
                                            cmNew.setId(cms.get(0).getId());
                                            break;
                                        }
                                    }
                                }
                            }
                        }
                    }

                    vm.setFileMetadata(fml.get(0));
                    em.merge(vm);

                }

                //remove old groups
                List<VarGroup> oldGroups = variableService.findAllGroupsByFileMetadata(fml.get(0).getId());
                if (oldGroups != null) {
                    for (int i = 0; i < oldGroups.size(); i++) {
                        em.remove(oldGroups.get(i));
                    }
                }

                //add new groups
                for (VarGroup varGroup : varGroupMap.values()) {
                    varGroup.setFileMetadata(fml.get(0));
                    varGroup.setId(null);
                    em.merge(varGroup);
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

    private boolean newGroups(Map<Long,VarGroup> varGroupMap, FileMetadata fm) {
        boolean areNewGroups = false;

        List<VarGroup> varGroups = variableService.findAllGroupsByFileMetadata(fm.getId());
        if (varGroups.size() != varGroupMap.size()) {
            return true;
        }

        for (Long id : varGroupMap.keySet()) {
            VarGroup dbVarGroup = em.find(VarGroup.class, id);

            if (dbVarGroup != null){
                dbVarGroup.setFileMetadata(fm);
                if (!dbVarGroup.equals(varGroupMap.get(id))) {
                    return true;
                }
            }
        }

        return areNewGroups;
    }

    private boolean isNewDraftVersion(Map<Long, VariableMetadata> mapVarToVarMet, FileMetadata fm) {
        boolean noUpdate = true;

        for ( VariableMetadata varMet : mapVarToVarMet.values()) {
            noUpdate = checkForUpdateForVar(varMet, fm);

            if (!noUpdate) {
                break;
            }
        }

        if (!noUpdate) {
            for ( VariableMetadata varMet : mapVarToVarMet.values()) {
                Long varId = varMet.getDataVariable().getId();
                DataVariable dv = variableService.find(varId);
                varMet.setDataVariable(dv);

                addCategories(varMet);
            }
        }

        return !noUpdate;
    }

    private void addCategories(VariableMetadata varMet) {

        Collection<CategoryMetadata> cms = varMet.getCategoriesMetadata();
        for (CategoryMetadata cm : cms) {
            String catValue = cm.getCategory().getValue();
            Collection<VariableCategory> vcl = varMet.getDataVariable().getCategories();
            if (vcl != null) {
                for (VariableCategory vc : vcl) {
                    if (catValue.equals(vc.getValue())) {
                        cm.setCategory(vc);
                        break;
                    }
                }
            }
        }

    }

    private boolean checkForUpdateForVar(VariableMetadata varMet, FileMetadata fm) {
        boolean noUpdate = true;

        Long varId = varMet.getDataVariable().getId();
        DataVariable dv = variableService.find(varId);
        varMet.setDataVariable(dv);

        Collection<VariableMetadata> vmList = dv.getVariableMetadatas();
        VariableMetadata prevVm = null;
        for (VariableMetadata varMetDv : vmList) {
            if (varMetDv.getFileMetadata().getId() == fm.getId()) {
                prevVm = varMetDv;
                break;
            }
        }

        if (prevVm != null) {
            noUpdate = compareVarMetadata(prevVm, varMet);
        } else {
            noUpdate = AreDefaultValues(varMet, dv);
        }

        return noUpdate;
    }

    private ArrayList checkVariableData(Map<Long, VariableMetadata> mapVarToVarMet, FileMetadata fm) {

        ArrayList<VariableMetadata> neededToUpdateVM = new ArrayList<VariableMetadata>();

        for ( VariableMetadata varMet : mapVarToVarMet.values()) {
            boolean noUpdate = true;

            noUpdate = checkForUpdateForVar(varMet, fm);

            if (noUpdate) {
                continue;
            }

            addCategories(varMet);
            neededToUpdateVM.add(varMet);
        }

        return neededToUpdateVM;
    }

    private boolean  compareVarMetadata(VariableMetadata vmOld, VariableMetadata vmNew) {
        boolean thesame = true;

        if (checkDiffEmpty(vmOld.getNotes(), vmNew.getNotes())) {
            thesame = false;
        } else if (checkDiffEmpty(vmNew.getNotes(), vmOld.getNotes())) {
            thesame = false;
        }else if (checkDiff(vmOld.getNotes(), vmNew.getNotes())) {
            thesame = false;
        } else if ( checkDiffEmpty(vmOld.getUniverse(), vmNew.getUniverse())) {
            thesame = false;
        } else if (checkDiffEmpty(vmNew.getUniverse(), vmOld.getUniverse())) {
                thesame = false;
        } else if (checkDiff(vmOld.getUniverse(),vmNew.getUniverse())) {
            thesame = false;
        } else if (checkDiffEmpty(vmOld.getInterviewinstruction(),vmNew.getInterviewinstruction())) {
            thesame = false;
        } else if ( checkDiffEmpty(vmNew.getInterviewinstruction(),vmOld.getInterviewinstruction())) {
            thesame = false;
        } else if (checkDiff(vmOld.getInterviewinstruction(),vmNew.getInterviewinstruction())) {
            thesame = false;
        } else  if (checkDiffEmpty(vmOld.getLiteralquestion(),vmNew.getLiteralquestion())) {
            thesame = false;
        } else if (checkDiffEmpty(vmNew.getLiteralquestion(),vmOld.getLiteralquestion())) {
            thesame = false;
        } else if (checkDiff(vmOld.getLiteralquestion(),vmNew.getLiteralquestion())) {
            thesame = false;
        } else  if (checkDiffEmpty(vmOld.getLabel(),vmNew.getLabel())) {
            thesame = false;
        } else if  (checkDiffEmpty(vmNew.getLabel(),vmOld.getLabel())) {
            thesame = false;
        } else if (checkDiff(vmOld.getLabel(),vmNew.getLabel())) {
            thesame = false;
        } else if (vmOld.isIsweightvar() != vmNew.isIsweightvar() ) {
            thesame = false;
        } else if (vmOld.isWeighted() != vmNew.isWeighted()) {
            thesame = false;
        } else if (vmOld.isWeighted() == vmNew.isWeighted()) {
            if (vmOld.isWeighted() && vmOld.getWeightvariable().getId() != vmNew.getWeightvariable().getId()) {
                thesame = false;
            }
        }

        return thesame;

    }

    private boolean AreDefaultValues(VariableMetadata varMet, DataVariable dv) {
        boolean thedefault = true;

        if (varMet.getNotes() != null && !varMet.getNotes().trim().equals("")) {
            thedefault = false;
        } else if (varMet.getUniverse() != null && !varMet.getUniverse().trim().equals("") ) {
            thedefault = false;
        } else if (varMet.getInterviewinstruction() != null && !varMet.getInterviewinstruction().trim().equals("")) {
            thedefault = false;
        } else if (varMet.getLiteralquestion() != null && varMet.getLiteralquestion().trim().equals("")) {
            thedefault = false;
        } else if (dv.getLabel() != null && !dv.getLabel().equals(varMet.getLabel())) {
            thedefault = false;
        } else if (varMet.isIsweightvar() != false ) {
            thedefault = false;
        } else if (varMet.isWeighted() != false) {
            thedefault = false;
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

    private boolean checkDiffEmpty(String str1, String str2) {
        if (str1 == null && str2 != null && !str2.trim().equals("")) {
            return true;
        }
        return false;

    }
    private boolean checkDiff(String str1, String str2) {
        if (str1 != null && str2 != null && !str1.equals(str2)) {
            return true;
        }
        return false;
    }
}


