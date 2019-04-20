package br.ufsc.ppgcc.experion.model.support;

import com.google.cloud.translate.Translate;
import com.google.cloud.translate.TranslateOptions;
import com.google.cloud.translate.Translation;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.core.StopFilter;
import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.apache.lucene.analysis.pt.PortugueseAnalyzer;
import org.apache.lucene.analysis.standard.StandardTokenizer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.util.CharArraySet;
import org.apache.lucene.util.Version;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.JDOMException;
import org.jdom2.Namespace;
import org.jdom2.filter.Filters;
import org.jdom2.input.SAXBuilder;
import org.jdom2.xpath.XPathExpression;
import org.jdom2.xpath.XPathFactory;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.traverse.DepthFirstIterator;
import org.tartarus.snowball.ext.PorterStemmer;
import org.tartarus.snowball.ext.PortugueseStemmer;

import java.io.*;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Loads the ACM Classification from the XML file
 *
 * @author Rodrigo Gonçalves
 * @version 2019-03-05 - First Version
 *
 */
public class ACMClassification {

    Translate translate;
    private ACMClassificationNode root;
    private String language;

    private boolean debug = false;

    public boolean isDebug() {
        return debug;
    }

    public void setDebug(boolean debug) {
        this.debug = debug;
    }

    public void printLabels() {
        for (ACMClassificationNode node : this.graph.vertexSet()) {
            System.out.println(node);
        }

    }

    public static class ACMClassificationNode implements Serializable {
        private DefaultDirectedGraph<ACMClassificationNode, DefaultEdge> graph;
        private String id;
        private String trueLabel;
        private String fullTrueLabel;
        private String label;
        private Set<String> labels = new HashSet<>();
        private Set<String> fullLabels;
        private Integer level = null;

        public int getLevel() {
            if (level == null) {
                calculateLabels();
            }
            return level;
        }



        public String toString() {
            return String.format("(%s) [%s] => %s ( %s )", this.trueLabel, this.getFullTrueLabel(), StringUtils.join(this.fullLabels, ","), StringUtils.join(this.labels, ","));
        }

        public ACMClassificationNode(DefaultDirectedGraph<ACMClassificationNode, DefaultEdge> graph, String id, String trueLabel, Collection<String> labels) {
            this.graph = graph;
            this.id = id;
            this.trueLabel = trueLabel;
            this.labels.addAll(labels);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ACMClassificationNode that = (ACMClassificationNode) o;
            return Objects.equals(id, that.id);
        }

        @Override
        public int hashCode() {
            return Objects.hash(id);
        }

        public Set<String> getFullWords() {
            if (fullLabels == null) {
                calculateLabels();
            }

            return fullLabels;
        }

        public String getFullTrueLabel() {
            if (fullLabels == null) {
                calculateLabels();
            }
            return fullTrueLabel;
        }

        public Set<String> getFullLabels() {
            if (fullLabels == null) {
                calculateLabels();
            }
            return fullLabels;
        }

        private void calculateLabels() {
            fullTrueLabel = this.trueLabel;
            fullLabels = new HashSet<>();
            ACMClassificationNode node = this;
            level = 1;

            while (node != null) {
                fullLabels.addAll(node.labels);
                Set<DefaultEdge> parentNodes = graph.incomingEdgesOf(node);
                if (parentNodes.isEmpty()) {
                    break;
                } else {
                    node = graph.getEdgeSource(parentNodes.iterator().next());
                    level++;
                    fullTrueLabel = node.trueLabel + " -> " + fullTrueLabel;
                }
            }
        }
    }

    private InputStream classificationXML = this.getClass().getResourceAsStream("/acm.xml");
    private DefaultDirectedGraph<ACMClassificationNode, DefaultEdge> graph = new DefaultDirectedGraph<>(DefaultEdge.class);

    public void loadXML() throws JDOMException, IOException {
        this.language = "en";
        SAXBuilder jdomBuilder = new SAXBuilder();
        Document jdomDocument = jdomBuilder.build(classificationXML);
        XPathFactory xFactory = XPathFactory.instance();
        XPathExpression<Element> expr = xFactory.compile("//skos:Concept", Filters.element(), null,
                Namespace.getNamespace("skos", "http://www.w3.org/2004/02/skos/core#"));

        List<Element> concepts = expr.evaluate(jdomDocument);
        Map<String, ACMClassificationNode> nodeList = new HashMap<>();

        for (Element linkElement : concepts) { // or skos:altLabel]
            XPathExpression<Element> exprLabels = xFactory.compile("skos:prefLabel", Filters.element(), null,
                    Namespace.getNamespace("skos", "http://www.w3.org/2004/02/skos/core#"));
            List<Element> labels = exprLabels.evaluate(linkElement);
            Set<String> labelList = new HashSet<>();
            String trueLabel = "";
            for (Element label : labels) {
                trueLabel = label.getValue().toLowerCase();
                labelList.addAll(Arrays.asList(label.getValue().toLowerCase().split(" ")));
            }

            exprLabels = xFactory.compile("skos:altLabel", Filters.element(), null,
                    Namespace.getNamespace("skos", "http://www.w3.org/2004/02/skos/core#"));
            labels = exprLabels.evaluate(linkElement);
            for (Element label : labels) {
                labelList.addAll(Arrays.asList(label.getValue().toLowerCase().split(" ")));
            }


            ACMClassificationNode node = new ACMClassificationNode(graph, linkElement.getAttributeValue("about", Namespace.getNamespace("rdf", "http://www.w3.org/1999/02/22-rdf-syntax-ns#")), trueLabel, labelList);
            if (graph.vertexSet().isEmpty()) {
                root = node;
            }
            graph.addVertex(node);
            nodeList.put(node.id, node);
        }

        for (Element linkElement : concepts) { // or skos:altLabel]
            XPathExpression<Element> exprParent = xFactory.compile("skos:broader", Filters.element(), null, Namespace.getNamespace("skos", "http://www.w3.org/2004/02/skos/core#"));
            List<Element> parents = exprParent.evaluate(linkElement);

            for (Element parent : parents) {
                graph.addEdge(nodeList.get(parent.getAttributeValue("resource", Namespace.getNamespace("rdf", "http://www.w3.org/1999/02/22-rdf-syntax-ns#"))), nodeList.get(linkElement.getAttributeValue("about", Namespace.getNamespace("rdf", "http://www.w3.org/1999/02/22-rdf-syntax-ns#"))));
            }
        }

        buildIDF();

    }


    Map<String,Double> idfs = new HashMap<>();

    private void buildIDF() throws IOException {

        List<List<String>> lines = this.graph.vertexSet().stream().
                map(node -> node.getFullLabels()).
                map(words -> {
                    Collection<String> wrds = words.stream().filter(word -> !StringUtils.isBlank(word)).collect(Collectors.toList());
                    wrds = this.language.equals("en") ? removeStopWordsEN(wrds) : removeStopWordsPT(wrds);
                    wrds = this.stemTerms(wrds);
                    return wrds.stream().collect(Collectors.toList());
                }).
                collect(Collectors.toList());

        Set<String> words = new HashSet<>();
        lines.stream().forEach(line -> line.stream().forEach(word -> words.add(word)));

        words.stream().forEach(word -> idfs.put(word, 0.0));

        lines.stream().forEach(line -> line.stream().forEach(word -> idfs.replace(word, idfs.get(word) + 1) ));
        idfs.keySet().stream().forEach(key -> idfs.replace(key, Math.log(lines.size() / idfs.get(key))));

    }

    @SuppressWarnings("unchecked")
    public void loadXMLInPTBR() throws JDOMException, IOException, ClassNotFoundException {
        this.language = "pt";
        InputStream cacheFile = this.getClass().getResourceAsStream("/cache_en_ptbr.dat");

        try {
            translate = TranslateOptions.getDefaultInstance().getService();
        } catch (Exception e) {

        }
        boolean saveCache = false;

        Map<String, String> cache = new HashMap<>();
        ObjectInputStream os = new ObjectInputStream(cacheFile);
        try {
            cache = (Map<String, String>) os.readObject();
        } catch (Exception e) {
            // Ignore - invalid cache
        } finally {
            os.close();
        }

        SAXBuilder jdomBuilder = new SAXBuilder();
        Document jdomDocument = jdomBuilder.build(classificationXML);
        XPathFactory xFactory = XPathFactory.instance();
        XPathExpression<Element> expr = xFactory.compile("//skos:Concept", Filters.element(), null,
                Namespace.getNamespace("skos", "http://www.w3.org/2004/02/skos/core#"));

        List<Element> concepts = expr.evaluate(jdomDocument);
        Map<String, ACMClassificationNode> nodeList = new HashMap<>();

        for (Element linkElement : concepts) { // or skos:altLabel]
            XPathExpression<Element> exprLabels = xFactory.compile("skos:prefLabel", Filters.element(), null,
                    Namespace.getNamespace("skos", "http://www.w3.org/2004/02/skos/core#"));
            List<Element> labels = exprLabels.evaluate(linkElement);
            Set<String> labelList = new HashSet<>();
            String trueLabel = "";
            for (Element label : labels) {
                trueLabel = label.getValue();
                if (cache.get(trueLabel) == null) {
                    cache.put(trueLabel, translatePTBR(trueLabel));
                    saveCache = true;
                }
                trueLabel = cache.get(trueLabel);
                trueLabel = trueLabel.toLowerCase();
                labelList.addAll(Arrays.asList(trueLabel.split(" ")));
            }

            exprLabels = xFactory.compile("skos:altLabel", Filters.element(), null,
                    Namespace.getNamespace("skos", "http://www.w3.org/2004/02/skos/core#"));
            labels = exprLabels.evaluate(linkElement);
            for (Element label : labels) {
                String secondLabel = label.getValue();


                if (cache.get(secondLabel) == null) {
                    cache.put(secondLabel, translatePTBR(secondLabel));
                    saveCache = true;
                }
                secondLabel = cache.get(secondLabel);

                labelList.addAll(Arrays.asList(secondLabel.toLowerCase().split(" ")));
            }


            ACMClassificationNode node = new ACMClassificationNode(graph, linkElement.getAttributeValue("about", Namespace.getNamespace("rdf", "http://www.w3.org/1999/02/22-rdf-syntax-ns#")), trueLabel, labelList);
            if (graph.vertexSet().isEmpty()) {
                root = node;
            }

            graph.addVertex(node);
            nodeList.put(node.id, node);
        }

        for (Element linkElement : concepts) { // or skos:altLabel]
            XPathExpression<Element> exprParent = xFactory.compile("skos:broader", Filters.element(), null, Namespace.getNamespace("skos", "http://www.w3.org/2004/02/skos/core#"));
            List<Element> parents = exprParent.evaluate(linkElement);

            for (Element parent : parents) {
                graph.addEdge(nodeList.get(parent.getAttributeValue("resource", Namespace.getNamespace("rdf", "http://www.w3.org/1999/02/22-rdf-syntax-ns#"))), nodeList.get(linkElement.getAttributeValue("about", Namespace.getNamespace("rdf", "http://www.w3.org/1999/02/22-rdf-syntax-ns#"))));
            }
        }

        buildIDF();
    }

    private String translatePTBR(String text) {
        Translation translation =
                translate.translate(
                        text,
                        Translate.TranslateOption.sourceLanguage("en"),
                        Translate.TranslateOption.targetLanguage("pt-br"));

        return translation.getTranslatedText();
    }

    String stemTerm (String term) {
        term = term.toLowerCase();
        PorterStemmer stemmer = new PorterStemmer();
        stemmer.setCurrent(term);
        stemmer.stem();
        return stemmer.getCurrent();
    }

    String stemTermPT (String term) {
        term = term.toLowerCase();
        PortugueseStemmer stemmer = new PortugueseStemmer();
        stemmer.setCurrent(term);
        stemmer.stem();
        return stemmer.getCurrent();
    }


    Collection<String> stemTerms(Collection<String> terms) {
        return terms.stream().map(term -> language.equals("pt") ? stemTermPT(term) : stemTerm(term)).collect(Collectors.toSet());
    }

    public Collection<String> removeStopWordsEN(Collection<String> terms) {
        String textFile = StringUtils.join(terms, " ");
        CharArraySet stopWords = EnglishAnalyzer.getDefaultStopSet();
        TokenStream tokenStream = new StandardTokenizer(Version.LUCENE_45, new StringReader(textFile.trim()));

        tokenStream = new StopFilter(Version.LUCENE_45, tokenStream, stopWords);
        StringBuilder sb = new StringBuilder();
        CharTermAttribute charTermAttribute = tokenStream.addAttribute(CharTermAttribute.class);
        try {
            tokenStream.reset();
        } catch (IOException e) {
            e.printStackTrace();
        }
        while (true) {
            try {
                if (!tokenStream.incrementToken()) break;
            } catch (IOException e) {
                e.printStackTrace();
            }
            String term = charTermAttribute.toString();
            sb.append(term + " ");
        }
        return Arrays.asList(StringUtils.split(sb.toString(), " "));
    }

    public Collection<String> removeStopWordsPT(Collection<String> terms) {
        String textFile = StringUtils.join(terms, " ");
        CharArraySet stopWords = PortugueseAnalyzer.getDefaultStopSet();
        TokenStream tokenStream = new StandardTokenizer(Version.LUCENE_45, new StringReader(textFile.trim()));

        tokenStream = new StopFilter(Version.LUCENE_45, tokenStream, stopWords);
        StringBuilder sb = new StringBuilder();
        CharTermAttribute charTermAttribute = tokenStream.addAttribute(CharTermAttribute.class);
        try {
            tokenStream.reset();
        } catch (IOException e) {
            e.printStackTrace();
        }
        while (true) {
            try {
                if (!tokenStream.incrementToken()) break;
            } catch (IOException e) {
                e.printStackTrace();
            }
            String term = charTermAttribute.toString();
            sb.append(term + " ");
        }
        return Arrays.asList(StringUtils.split(sb.toString(), " "));
    }

    public List<ACMClassificationNode> getTrueLabel(Collection<String> words) {

        words = this.language.equals("en") ? removeStopWordsEN(words) : removeStopWordsPT(words);

        Map<ACMClassificationNode, Double> similarities = new HashMap<>();
        Collection<String> stemmedWords = stemTerms(words);
        for (ACMClassificationNode node : graph.vertexSet()) {
            Collection<String> fullWordsStemmed = this.stemTerms(node.getFullLabels());
            if (CollectionUtils.intersection(stemmedWords, fullWordsStemmed).size() > 0) {
                if (this.debug && CollectionUtils.intersection(stemmedWords, fullWordsStemmed).size() > 1) {
                    System.out.println(CollectionUtils.intersection(stemmedWords, fullWordsStemmed) + "=>" + similarityTFIDF(stemmedWords, fullWordsStemmed));
                }
                similarities.put(node, node.getLevel() * CollectionUtils.intersection(stemmedWords, fullWordsStemmed).size() * similarityTFIDF(stemmedWords, fullWordsStemmed));
            }
        }


        List<ACMClassificationNode> topNodes = new LinkedList<>();

        similarities.entrySet()
                .stream()
                .sorted(new Comparator<Map.Entry<ACMClassificationNode, Double>>() {
                    @Override
                    public int compare(Map.Entry<ACMClassificationNode, Double> o1, Map.Entry<ACMClassificationNode, Double> o2) {
                        return o2.getValue().compareTo(o1.getValue());
                    }
                })
                .forEach(new Consumer<Map.Entry<ACMClassificationNode, Double>>() {
                    @Override
                    public void accept(Map.Entry<ACMClassificationNode, Double> acmClassificationNodeIntegerEntry) {
                        if (acmClassificationNodeIntegerEntry.getValue() > 0) {
                            topNodes.add(acmClassificationNodeIntegerEntry.getKey());
                            if (debug) {
                                System.out.println("Similarity for: " + acmClassificationNodeIntegerEntry.getKey() + "=>" + acmClassificationNodeIntegerEntry.getValue());
                            }
                        }
                    }
                });

        if (topNodes.isEmpty()) {
            return topNodes;
        } else {
            return topNodes.subList(0, Math.min(2, topNodes.size() - 1));
        }

    }

    private Double similarityTFIDF(Collection<String> stemmedWords, Collection<String> fullWordsStemmed) {
        Collection<String> common = CollectionUtils.intersection(stemmedWords, fullWordsStemmed);
        return common.stream().mapToDouble(word -> idfs.get(word)).sum() * common.size();
    }

    /**
     * Returns string representation of the classification
     *
     * @return
     */
    public String getTreeView() {
        StringBuilder output = new StringBuilder();


        Set<ACMClassificationNode> roots = new HashSet<>();

        for (ACMClassificationNode node : graph.vertexSet()) {
            if (graph.incomingEdgesOf(node).isEmpty()) {
                roots.add(node);
            }
        }

        for (ACMClassificationNode root : roots) {
            DepthFirstIterator<ACMClassificationNode, DefaultEdge> iterator = new DepthFirstIterator<ACMClassificationNode, DefaultEdge>(graph, root);
            for (; iterator.hasNext(); ) {
                ACMClassificationNode node = iterator.next();
                Integer level = 0;

                Set<DefaultEdge> prior = graph.incomingEdgesOf(node);

                while (!prior.isEmpty()) {
                    level++;
                    prior = graph.incomingEdgesOf(graph.getEdgeSource(prior.iterator().next()));
                }

                output.append(StringUtils.repeat("\t", level) + node.trueLabel + "[" + StringUtils.join(node.labels, ",") + "]");
                output.append("\n");
            }
        }

        return output.toString();
    }
}
