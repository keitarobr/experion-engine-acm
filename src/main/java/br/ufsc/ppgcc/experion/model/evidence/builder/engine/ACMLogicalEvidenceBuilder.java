package br.ufsc.ppgcc.experion.model.evidence.builder.engine;

import br.ufsc.ppgcc.experion.extractor.evidence.PhysicalEvidence;
import br.ufsc.ppgcc.experion.model.evidence.LogicalEvidence;
import br.ufsc.ppgcc.experion.model.support.ACMClassification;
import org.jdom2.JDOMException;
import org.tartarus.snowball.ext.PorterStemmer;

import java.io.IOException;
import java.util.*;

/**
 * Evidence builder using the ACM classification system
 *
 * @author Rodrigo Gonçalves
 * @version 2019-03-05 - First version
 */
public class ACMLogicalEvidenceBuilder implements LogicalEvidenceBuilderEngine {

    private ACMClassification classifier = new ACMClassification();
    private ACMClassification classifierPT = new ACMClassification();



    public  Map<ACMClassification.ACMClassificationNode, List<PhysicalEvidence>> buildFor(Set<PhysicalEvidence> evidences) {

        Map<ACMClassification.ACMClassificationNode, List<PhysicalEvidence>> map = new HashMap<>();

        for (PhysicalEvidence physicalEvidence : evidences) {

            List<ACMClassification.ACMClassificationNode> concepts;

            if (physicalEvidence.getLanguage().equals("pt")) {
                concepts = classifierPT.getTrueLabel(physicalEvidence.getKeywords());
            } else {
                concepts = classifier.getTrueLabel(physicalEvidence.getKeywords());
            }

            for (ACMClassification.ACMClassificationNode concept : concepts) {
                List<PhysicalEvidence> associatedEvidences = map.get(concept);
                if (associatedEvidences == null) {
                    associatedEvidences = new LinkedList<PhysicalEvidence>();
                    map.put(concept, associatedEvidences);
                }
                associatedEvidences.add(physicalEvidence);
            }
        }

        return map;
    }

    public ACMLogicalEvidenceBuilder() {
        try {
            classifier.loadXML();
            classifierPT.loadXMLInPTBR();
        } catch (JDOMException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

    public Set<LogicalEvidence> getLogicalEvidences(Set<PhysicalEvidence> physicalEvidences) {
        Map<ACMClassification.ACMClassificationNode, List<PhysicalEvidence>> map = this.buildFor(physicalEvidences);

        Set<LogicalEvidence> logicalEvidences = new HashSet<>();

        for (ACMClassification.ACMClassificationNode node : map.keySet()) {
            LogicalEvidence logicalEvidence = new LogicalEvidence(node.getFullTrueLabel());
            for (PhysicalEvidence physicalEvidence : map.get(node)) {
                logicalEvidence.getPhysicalEvidences().add(physicalEvidence);
            }
            logicalEvidences.add(logicalEvidence);
        }

        return logicalEvidences;
    }

}
