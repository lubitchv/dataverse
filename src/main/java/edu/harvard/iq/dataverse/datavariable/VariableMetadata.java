package edu.harvard.iq.dataverse.datavariable;

import java.io.Serializable;
import java.util.Collection;
import javax.persistence.CascadeType;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.OneToOne;

import edu.harvard.iq.dataverse.FileMetadata;
import edu.harvard.iq.dataverse.util.AlphaNumericComparator;
import org.hibernate.validator.constraints.NotBlank;
import java.util.ArrayList;
import java.util.List;
import javax.persistence.Column;
import javax.persistence.Index;
import javax.persistence.OrderBy;
import javax.persistence.Table;

@Entity
@Table(indexes = {@Index(columnList="datavariable_id"), @Index(columnList="filemetadata_id")})
public class VariableMetadata implements Serializable  {


    private static AlphaNumericComparator alphaNumericComparator = new AlphaNumericComparator();

    private static final long serialVersionUID = 1L;
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(nullable=false)
    private DataVariable dataVariable;

    @ManyToOne
    @JoinColumn(nullable=false)
    private FileMetadata fileMetadata;

    @Column(columnDefinition="TEXT")
    private String label;

    @Column(columnDefinition="TEXT")
    private String literalquestion;

    @Column(columnDefinition="TEXT")
    private String interviewinstruction;

    private String universe;

    @Column(columnDefinition="TEXT")
    private String notes;

    private boolean isweightvar = false;

    private long weightvariable_id = -1;

    public VariableMetadata () {

    }

    public VariableMetadata(DataVariable dv, FileMetadata mv) {
        dataVariable = dv;
        fileMetadata = mv;
    }

    /*
     * Getter and Setter functions:
     */
    public DataVariable getDataVariable() {
        return this.dataVariable;
    }

    public void setDataVariable(DataVariable dataVariable) {
        this.dataVariable = dataVariable;
    }

    public FileMetadata getFileMetadata() {
        return this.fileMetadata;
    }

    public void setFileMetadata(FileMetadata fileMetadata) {
        this.fileMetadata = fileMetadata;
    }

    public Long getId() {
        return this.id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getLabel() {
        return this.label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getLiteralquestion() {
        return this.literalquestion;
    }

    public void setLiteralquestion(String literalquestion) {
        this.literalquestion = literalquestion;
    }

    public String getInterviewinstruction() {
        return this.interviewinstruction;
    }

    public void setInterviewinstruction(String interviewinstruction) {
        this.interviewinstruction = interviewinstruction;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public String getNotes() { return notes; }

    public String getUniverse() { return this.universe; }

    public void setUniverse(String universe) {
        this.universe = universe;
    }

    public boolean getIsweightvar() { return this.isweightvar; }

    public void setIsweightvar(boolean isweightvar) {this.isweightvar = isweightvar; }

    public long getWeightvariable_id() { return this.weightvariable_id; }

    public void setWeightvariable_id(long weightvariable_id) { this.weightvariable_id = weightvariable_id; }

    @Override
    public int hashCode() {
        int hash = 0;
        hash += (this.id != null ? this.id.hashCode() : 0);
        return hash;
    }

    @Override
    public boolean equals(Object object) {
        if (!(object instanceof VariableMetadata)) {
            return false;
        }

        VariableMetadata other = (VariableMetadata)object;
        if (this.id != other.id) {
            if (this.id == null || !this.id.equals(other.id)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public String toString() {
        return "edu.harvard.iq.dataverse.datavariable.VariableMetadata[ id=" + id + " ]";
    }


}
