import br.ufsc.ppgcc.experion.model.support.ACMClassification;
import org.apache.commons.lang3.StringUtils;
import org.jdom2.JDOMException;
import org.junit.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

public class TestACMClassification {

    @Test
    public void testLoadXML() throws JDOMException, IOException {
        ACMClassification acm = new ACMClassification();
        acm.loadXML();
    }

    @Test
    public void testGetTrueLabel() throws JDOMException, IOException {
        ACMClassification acm = new ACMClassification();
        acm.loadXML();
        Set<String> words = new HashSet<>();
        words.add("system");
        words.add("database");
        System.out.println(acm.getTrueLabel(words));
    }

    @Test
    public void testTunningClassification() throws JDOMException, IOException, ClassNotFoundException {
        ACMClassification acmPT = new ACMClassification();
//        acmPT.setDebug(true);
        acmPT.loadXMLInPTBR();
        String[] testes = new String[] {
                "por,study,records,web,similaridade,ufsc,removal,processing,womb,consulta"
        };
        Arrays.stream(testes).forEach(words -> acmPT.getTrueLabel(
                Arrays.stream(StringUtils.split(words, ",")).collect(Collectors.toSet())
        ).stream().forEach(concept -> System.out.println(words + "\n\t" + concept.getFullTrueLabel())));


        ACMClassification acmEN = new ACMClassification();
        acmEN.setDebug(true);
        acmEN.loadXML();
        String[] testesEN = new String[] {
                "template,path,improving,study,pages,method,segmentation,ranking,tag,extraction",
//                "heat,based,identification,consumptions,context,thermal,model,pumps,house,smart",
//                "engines,xml,rank,incremental,accelerated,filtering,queries,extended,temporal,url",
//                "questionnaires,search,qsmatching,interpretation,similarity,resources,handling,ranking,vector,calculate",
//                "schema,databases,paper,topics,xml,aspects,industry,hard,summarizes,papers"
        };
        Arrays.stream(testesEN).forEach(words -> acmEN.getTrueLabel(
                Arrays.stream(StringUtils.split(words, ",")).collect(Collectors.toSet())
        ).stream().forEach(concept -> System.out.println(words + "\n\t" + concept.getFullTrueLabel())));






    }
}
