package edu.harvard.iq.dataverse.datavariable;

import edu.harvard.iq.dataverse.DataFileServiceBean;
import edu.harvard.iq.dataverse.DataTable;
import edu.harvard.iq.dataverse.FileMetadata;
import org.apache.xalan.xsltc.runtime.Hashtable;

import javax.ejb.EJB;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DataVariableImportDDI {
    @EJB
    private VariableServiceBean variableService;

    public static final String LEVEL_VARIABLE = "variable";

    public DataVariableImportDDI (VariableServiceBean variableService) {
        this.variableService = variableService;
    }

    public Map<Long, VariableMetadata> processDataDscr(XMLStreamReader xmlr, FileMetadata fileMetadata) throws XMLStreamException {

         Map mapVarToVarMet = new HashMap();

        for (int event = xmlr.next(); event != XMLStreamConstants.END_DOCUMENT; event = xmlr.next()) {
            if (event == XMLStreamConstants.START_ELEMENT) {
                if (xmlr.getLocalName().equals("var")) {
                    processVar(xmlr, mapVarToVarMet, fileMetadata);
                }
            }
        }

        return mapVarToVarMet;

    }

    private void processVar(XMLStreamReader xmlr,  Map<Long, VariableMetadata> mapVarToVarMet, FileMetadata fileMetadata  ) throws XMLStreamException {

        String _id_v = xmlr.getAttributeValue(null, "ID");
        String _id = _id_v.replace("v", "");

        long id = Long.parseLong(_id);
        boolean newvarmetadata = false;

        DataVariable dv = variableService.find( id);
        VariableMetadata vm = null;
        List<VariableMetadata> vmList = variableService.findByIdAndFileMetadataId(id, fileMetadata.getId());
        if (vmList != null && vmList.size() > 0) {
            vm = vmList.get(0);
        }

        VariableMetadata newVM = new VariableMetadata();

        String wgt =  xmlr.getAttributeValue(null, "wgt");
        if (wgt != null && wgt.equals("wgt")) {
            newVM.setIsweightvar(true);
            if (vm != null && !vm.getIsweightvar()) {
                newvarmetadata = true;
            }
        } else {
            newVM.setIsweightvar(false);
            if (vm != null && vm.getIsweightvar()) {
                newvarmetadata = true;
            }
        }

        String wgt_var =  xmlr.getAttributeValue(null, "wgt-var");
        if (wgt_var != null && wgt_var.startsWith("v")) {
            long wgt_id = Long.parseLong(wgt_var.replace("v", ""));
            newVM.setWeightvariable_id(wgt_id);
            if (vm != null && !vm.getIsweightvar()) {
                newvarmetadata = true;
            }
        } else {
            newVM.setWeightvariable_id(-1);
            if (vm != null && vm.getWeightvariable_id() != -1) {
                newvarmetadata = true;
            }
        }

        for (int event = xmlr.next(); event != XMLStreamConstants.END_DOCUMENT; event = xmlr.next()) {
            if (event == XMLStreamConstants.START_ELEMENT) {
                if (xmlr.getLocalName().equals("labl")) {
                    newvarmetadata = processLabel(xmlr, newVM, vm, newvarmetadata);
                } else if (xmlr.getLocalName().equals("universe")) {
                    newvarmetadata = processUniverse(xmlr, newVM, vm, newvarmetadata);
                } else if (xmlr.getLocalName().equals("notes")) {
                    newvarmetadata = processNote(xmlr, newVM, vm, newvarmetadata);
                } else if (xmlr.getLocalName().equals("qstn")) {
                    newvarmetadata = processQstn(xmlr, newVM, vm, newvarmetadata);
                }

            } else if (event == XMLStreamConstants.END_ELEMENT) {
                if (xmlr.getLocalName().equals("var")) {
                    if (newvarmetadata) {
                        newVM.setDataVariable(dv);
                        mapVarToVarMet.put(id,newVM);
                    }
                    return;
                }
            }
        }

        if (newvarmetadata) {
            newVM.setDataVariable(dv);
            mapVarToVarMet.put(id,newVM);
        }

    }

    private boolean processLabel (XMLStreamReader xmlr, VariableMetadata newVM, VariableMetadata vm, boolean newvarmetadata) throws XMLStreamException {

        if (LEVEL_VARIABLE.equalsIgnoreCase( xmlr.getAttributeValue(null, "level") ) ) {
            String lable = parseText(xmlr, false);
            if (lable != null && !lable.isEmpty()) {
                newVM.setLabel(lable);
            }
            if (vm == null || (vm.getLabel() == null && lable != null) ||  !vm.getLabel().equals(lable)) {
                newvarmetadata = true;
            }
        }

        return newvarmetadata;
    }

    private boolean processQstn(XMLStreamReader xmlr, VariableMetadata newVM, VariableMetadata vm, boolean newvarmetadata) throws XMLStreamException {

        for (int event = xmlr.next(); event != XMLStreamConstants.END_DOCUMENT; event = xmlr.next()) {
            if (event == XMLStreamConstants.START_ELEMENT) {
                if (xmlr.getLocalName().equals("qstnLit")) {
                    String text = parseText(xmlr, false);
                    newVM.setLiteralquestion(text);
                    if (vm == null || (vm.getLiteralquestion() == null && text != null) || !vm.getLiteralquestion().equals(text)) {
                        newvarmetadata = true;
                    }
                } else if (xmlr.getLocalName().equals("ivuInstr")) {
                    String text = parseText(xmlr, false);
                    newVM.setInterviewinstruction(text);
                    if (vm == null ||  (vm.getInterviewinstruction() == null && text != null) || !vm.getInterviewinstruction().equals(text)) {
                        newvarmetadata = true;
                    }
                }
            } else if (event == XMLStreamConstants.END_ELEMENT) {
                if (xmlr.getLocalName().equals("qstn")) return newvarmetadata;
            }
        }

        return newvarmetadata;

    }

    private boolean processUniverse (XMLStreamReader xmlr, VariableMetadata newVM,  VariableMetadata vm, boolean newvarmetadata ) throws XMLStreamException {
        String universe = parseText(xmlr);
        newVM.setUniverse(universe);
        if (vm == null ||  (vm.getUniverse() == null && universe != null) || !vm.getUniverse().equals(universe)) {
            newvarmetadata = true;
        }
        return newvarmetadata;
    }

    private boolean processNote (XMLStreamReader xmlr,  VariableMetadata newVM, VariableMetadata vm, boolean newvarmetadata) throws XMLStreamException {

        String unf_type =  xmlr.getAttributeValue(null, "type");
        String note = parseText(xmlr,false);

        if (unf_type == null )  {
            newVM.setNotes(note);
            if (vm == null ||  (vm.getNotes() == null && note != null) || !vm.getNotes().equals(note)) {
                newvarmetadata = true;
            }
        }
        return newvarmetadata;
    }

    private String parseText(XMLStreamReader xmlr) throws XMLStreamException {
        return parseText(xmlr,true);
    }

    private String parseText(XMLStreamReader xmlr, boolean scrubText) throws XMLStreamException {
        String tempString = getElementText(xmlr);
        if (scrubText) {
            tempString = tempString.trim().replace('\n',' ');
        }
        return tempString;
    }

    private String getElementText(XMLStreamReader xmlr) throws XMLStreamException {
        if(xmlr.getEventType() != XMLStreamConstants.START_ELEMENT) {
            throw new XMLStreamException("parser must be on START_ELEMENT to read next text", xmlr.getLocation());
        }
        int eventType = xmlr.next();
        StringBuilder content = new StringBuilder();
        while(eventType != XMLStreamConstants.END_ELEMENT ) {
            if(eventType == XMLStreamConstants.CHARACTERS
                    || eventType == XMLStreamConstants.CDATA
                    || eventType == XMLStreamConstants.SPACE) {
                content.append(xmlr.getText());
            } else if(eventType == XMLStreamConstants.PROCESSING_INSTRUCTION
                    || eventType == XMLStreamConstants.COMMENT
                    || eventType == XMLStreamConstants.ENTITY_REFERENCE) {
                // skipping
            } else if(eventType == XMLStreamConstants.END_DOCUMENT) {
                throw new XMLStreamException("unexpected end of document when reading element text content");
            } else if(eventType == XMLStreamConstants.START_ELEMENT) {
                throw new XMLStreamException("element text content may not contain START_ELEMENT", xmlr.getLocation());
            } else {
                throw new XMLStreamException("Unexpected event type "+eventType, xmlr.getLocation());
            }
            eventType = xmlr.next();
        }
        return content.toString();
    }


}
