package edu.harvard.iq.dataverse.api;

import edu.harvard.iq.dataverse.*;
import edu.harvard.iq.dataverse.api.imports.ImportGenericServiceBean;
import edu.harvard.iq.dataverse.authorization.AuthenticationProvider;
import edu.harvard.iq.dataverse.authorization.Permission;
import edu.harvard.iq.dataverse.authorization.exceptions.AuthorizationSetupException;
import edu.harvard.iq.dataverse.authorization.providers.AuthenticationProviderRow;
import edu.harvard.iq.dataverse.authorization.users.User;
import edu.harvard.iq.dataverse.datavariable.DataTableImportDDI;
import edu.harvard.iq.dataverse.datavariable.DataVariable;
import edu.harvard.iq.dataverse.engine.command.DataverseRequest;
import edu.harvard.iq.dataverse.engine.command.impl.UpdateDatasetVersionCommand;
import edu.harvard.iq.dataverse.settings.SettingsServiceBean;
import edu.harvard.iq.dataverse.util.SystemConfig;

import javax.ejb.EJB;
import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
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
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

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

    @PUT
    @Consumes("application/xml")
    @Path("/resp")
    public Response edit (String body) {
        return ok("Updated");
    }


    @Path("{fileId}")
    @PUT
    @Consumes("application/xml")
    public Response editDataDscrDDI(InputStream body, @PathParam("fileId") String fileId) {

        System.out.println("Hi");
        DataFile dataFile = null;
        try {
            dataFile = findDataFileOrDie(fileId);
            System.out.println("Hi");
        } catch (WrappedResponse ex) {
            return ex.getResponse();
        }

        if (!checkAuth(dataFile)) {
            return unauthorized("Cannot edit metadata, access denied" );
        }
        logger.info(body.toString());

        DatasetVersion newDatasetVersion = dataFile.getOwner().getEditVersion();
        Map<String, DataTable> mp = null;
        try {
            mp = readXML(body);
        } catch (XMLStreamException e) {
            logger.warning(e.getMessage());
            return error(Response.Status.NOT_ACCEPTABLE, "bad xml file" );
        }

        if (mp != null && mp.size()==1) {
            Map.Entry<String,DataTable> entry = mp.entrySet().iterator().next();
            DataTable dtVars = entry.getValue();
            List<DataVariable> vars = dtVars.getDataVariables();
            //dataFile.setDataTable(dtVars);
            //FileMetadata fm = dataFile.getFileMetadata();
            //fm.setDataFile();

            //dataFile.getOwner().set

            for (int i=0; i< vars.size(); i++) {
                DataVariable var = vars.get(i);
                 {
                     DataVariable v = em.find(DataVariable.class, var.getId());
                     updateDataVariable(v,var);

                     v = em.merge(v);

                }
            }
            //fileService.save(dataFile);
            logger.info("Hi");
        } else {
            return error(Response.Status.NOT_ACCEPTABLE, "bad xml file" );
        }

        //importGenericService.importXML(deposit.getSwordEntry().toString(), foreignFormat, newDatasetVersion);

        //readXML()

        return ok("Metadata updated");
    }

    private void updateDataVariable(DataVariable vBase, DataVariable vNewData) {
        //label
        vBase.setLabel(vNewData.getLabel());
        vBase.setUniverse(vNewData.getUniverse());
        vBase.setWeighted(vNewData.isWeighted());

    }


    private Map<String, DataTable> readXML(InputStream body) throws XMLStreamException {

        XMLInputFactory factory=XMLInputFactory.newInstance();
        XMLStreamReader xmlr=factory.createXMLStreamReader(body);
        DataTableImportDDI dti = new DataTableImportDDI();
        Map<String, DataTable> mp = dti.processDataDscr(xmlr);

        return mp;
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




}
