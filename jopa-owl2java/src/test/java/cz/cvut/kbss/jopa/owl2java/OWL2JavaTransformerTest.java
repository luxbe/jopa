/**
 * Copyright (C) 2019 Czech Technical University in Prague
 *
 * This program is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any
 * later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more
 * details. You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package cz.cvut.kbss.jopa.owl2java;

import cz.cvut.kbss.jopa.owl2java.cli.PropertiesType;
import cz.cvut.kbss.jopa.owl2java.config.TransformationConfiguration;
import cz.cvut.kbss.jopa.owl2java.exception.OWL2JavaException;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.vocab.XSDVocabulary;
import uk.ac.manchester.cs.owl.owlapi.OWLDataFactoryImpl;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import static cz.cvut.kbss.jopa.owl2java.TestUtils.*;
import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.*;

public class OWL2JavaTransformerTest {

    private static final String PACKAGE = "cz.cvut.kbss";

    // Thing is always generated by OWL2Java
    private static final List<String> KNOWN_CLASSES = Arrays
            .asList("Agent", "Person", "Organization", "Answer", "Question", "Report", "Thing");

    private String mappingFilePath;

    private OWLDataFactory dataFactory;

    private OWL2JavaTransformer transformer;

    private File targetDir;

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Before
    public void setUp() {
        this.mappingFilePath = resolveMappingFilePath();
        this.dataFactory = new OWLDataFactoryImpl();
        this.transformer = new OWL2JavaTransformer();
    }

    private String resolveMappingFilePath() {
        final File mf = new File(getClass().getClassLoader().getResource(MAPPING_FILE_NAME).getFile());
        return mf.getAbsolutePath();
    }

    @After
    public void tearDown() throws Exception {
        if (targetDir != null) {
            TestUtils.recursivelyDeleteDirectory(targetDir);
        }
    }

    @Test
    public void listContextsShowsContextsInICFile() {
        transformer.setOntology(IC_ONTOLOGY_IRI, mappingFilePath);
        final Collection<String> contexts = transformer.listContexts();
        assertEquals(1, contexts.size());
        assertEquals(CONTEXT, contexts.iterator().next());
    }

    @Test
    public void transformGeneratesJavaClassesFromIntegrityConstraints() throws Exception {
        this.targetDir = getTempDirectory();
        assertEquals(0, targetDir.listFiles().length);
        transformer.setOntology(IC_ONTOLOGY_IRI, mappingFilePath);
        transformer.transform(config(CONTEXT, "", targetDir.getAbsolutePath(), true).build());
        verifyGeneratedModel(targetDir);
    }

    private TransformationConfiguration.TransformationConfigurationBuilder config(String context, String packageName,
                                                                                  String targetDir,
                                                                                  boolean withOwlapiIris) {
        return TransformationConfiguration.builder().context(context).packageName(packageName).targetDir(targetDir)
                                          .addOwlapiIris(withOwlapiIris)
                                          .propertiesType(PropertiesType.string);
    }

    @Test
    public void transformGeneratesJavaClassesInPackage() throws Exception {
        final String packageName = "cz.cvut.kbss.jopa.owl2java";
        this.targetDir = getTempDirectory();
        assertEquals(0, targetDir.listFiles().length);
        transformer.setOntology(IC_ONTOLOGY_IRI, mappingFilePath);
        transformer.transform(config(CONTEXT, packageName, targetDir.getAbsolutePath(), true).build());
        verifyGeneratedTree(packageName, targetDir);
    }

    private void verifyGeneratedTree(String packageName, File parentDir) {
        File currentDir = parentDir;
        for (String p : packageName.split("\\.")) {
            final List<String> files = Arrays.asList(currentDir.list());
            assertTrue(files.contains(p));
            currentDir = new File(currentDir.getAbsolutePath() + File.separator + p);
        }
        verifyVocabularyFileExistence(currentDir);
        verifyGeneratedModel(currentDir);
    }

    private void verifyGeneratedModel(File currentDir) {
        currentDir = new File(currentDir + File.separator + Constants.MODEL_PACKAGE);
        final List<String> classNames = Arrays.stream(currentDir.list())
                                              .map(fn -> fn.substring(0, fn.indexOf('.'))).collect(
                        Collectors.toList());
        assertTrue(classNames.containsAll(KNOWN_CLASSES));
    }

    @Test
    public void transformGeneratesVocabularyFile() throws Exception {
        this.targetDir = getTempDirectory();
        transformer.setOntology(IC_ONTOLOGY_IRI, mappingFilePath);
        transformer.transform(config(CONTEXT, "", targetDir.getAbsolutePath(), true).build());
        verifyVocabularyFileExistence(targetDir);
    }

    private void verifyVocabularyFileExistence(File targetDir) {
        final List<String> fileNames = Arrays.asList(targetDir.list());
        assertTrue(fileNames.contains(VOCABULARY_FILE));
    }

    @Test
    public void transformGeneratesVocabularyFileForTheWholeFile() throws Exception {
        this.targetDir = getTempDirectory();
        transformer.setOntology(IC_ONTOLOGY_IRI, mappingFilePath);
        transformer.transform(config(null, "", targetDir.getAbsolutePath(), true).build());
        verifyVocabularyFileExistence(targetDir);
    }

    @Test
    public void transformThrowsIllegalArgumentForUnknownContext() throws Exception {
        final String unknownContext = "someUnknownContext";
        thrown.expect(IllegalArgumentException.class);
        thrown.expectMessage("Context " + unknownContext + " not found.");
        final File targetDir = getTempDirectory();
        transformer.setOntology(IC_ONTOLOGY_IRI, mappingFilePath);
        transformer.transform(config(unknownContext, "", targetDir.getAbsolutePath(), true).build());
    }

    @Test
    public void setUnknownOntologyIriThrowsOWL2JavaException() {
        final String unknownOntoIri = "http://krizik.felk.cvut.cz/ontologies/an-unknown-ontology.owl";
        thrown.expect(OWL2JavaException.class);
        thrown.expectMessage("Unable to load ontology " + unknownOntoIri);
        transformer.setOntology(unknownOntoIri, mappingFilePath);
    }

    @Test
    public void setOntologyWithUnknownMappingFileThrowsIllegalArgument() {
        final String unknownMappingFile = "/tmp/unknown-mapping-file";
        thrown.expect(IllegalArgumentException.class);
        thrown.expectMessage("Mapping file " + unknownMappingFile + " not found.");
        transformer.setOntology(IC_ONTOLOGY_IRI, unknownMappingFile);
    }

    @Test
    public void generateVocabularyGeneratesOnlyVocabularyFile() throws Exception {
        this.targetDir = getTempDirectory();
        transformer.setOntology(IC_ONTOLOGY_IRI, mappingFilePath);
        transformer.generateVocabulary(config(CONTEXT, "", targetDir.getAbsolutePath(), false).build());
        final List<String> fileNames = Arrays.asList(targetDir.list());
        assertEquals(1, fileNames.size());
        assertEquals(VOCABULARY_FILE, fileNames.get(0));
    }

    @Test
    public void generateVocabularyGeneratesOnlyVocabularyFileForTheWholeFile() throws Exception {
        this.targetDir = getTempDirectory();
        transformer.setOntology(IC_ONTOLOGY_IRI, mappingFilePath);
        transformer.generateVocabulary(config(null, "", targetDir.getAbsolutePath(), false).build());
        final List<String> fileNames = Arrays.asList(targetDir.list());
        assertEquals(1, fileNames.size());
        assertEquals(VOCABULARY_FILE, fileNames.get(0));
    }

    @Test
    public void generateVocabularyTransformsInvalidCharactersInIrisToValid() throws Exception {
        this.targetDir = getTempDirectory();
        transformer.setOntology(IC_ONTOLOGY_IRI, mappingFilePath);
        // contains a ',', which will result in invalid Java identifier
        final String invalidIri = "http://onto.fel.cvut.cz/ontologies/aviation-safety/accident,_incident_or_emergency";
        final OWLAxiom axiom = dataFactory.getOWLDeclarationAxiom(dataFactory.getOWLClass(IRI.create(invalidIri)));
        TestUtils.addAxiom(axiom, transformer);

        transformer.generateVocabulary(config(null, "", targetDir.getAbsolutePath(), false).build());
        final File vocabularyFile = targetDir.listFiles()[0];
        final String fileContents = readFile(vocabularyFile);
        assertFalse(fileContents.contains(invalidIri.substring(invalidIri.lastIndexOf('/') + 1) + " ="));
        assertTrue(fileContents.contains(invalidIri.substring(invalidIri.lastIndexOf(',') + 1) + " ="));
    }

    private String readFile(File file) throws IOException {
        final StringBuilder sb = new StringBuilder();
        try (final BufferedReader in = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = in.readLine()) != null) {
                sb.append(line).append('\n');
            }
        }
        return sb.toString();
    }

    @Test
    public void transformGeneratesDPParticipationConstraintWithCorrectDatatypeIri() throws Exception {
        this.targetDir = getTempDirectory();
        assertEquals(0, targetDir.listFiles().length);
        transformer.setOntology(IC_ONTOLOGY_IRI, mappingFilePath);
        transformer.transform(config(CONTEXT, PACKAGE, targetDir.getAbsolutePath(), true).build());
        final List<String> generatedClass = getGeneratedClass(targetDir, "Answer");

        String fieldDeclaration = getFieldDeclaration(generatedClass, "hasValue");
        assertTrue(fieldDeclaration.contains(
                "@ParticipationConstraint(owlObjectIRI = \"" + XSDVocabulary.STRING.getIRI().toString() + "\""));
    }

    @Test
    public void transformGeneratesSubClass() throws Exception {
        this.targetDir = getTempDirectory();
        assertEquals(0, targetDir.listFiles().length);
        transformer.setOntology(IC_ONTOLOGY_IRI, mappingFilePath);
        transformer.transform(config(CONTEXT, PACKAGE, targetDir.getAbsolutePath(), true).build());
        final List<String> generatedClass = getGeneratedClass(targetDir, "Organization");

        final String classDeclaration = getExtendsClassDeclaration(generatedClass);
        assertTrue(classDeclaration.contains("extends Agent"));
    }

    private String getExtendsClassDeclaration(List<String> classFileLines) {
        int i;
        for (i = 0; i < classFileLines.size(); i++) {
            if (classFileLines.get(i).startsWith("public class")) {
                break;
            }
        }
        return classFileLines.get(i + 1);
    }

    @Test
    public void transformationFailsWhenImportCannotBeResolved() throws Exception {
        thrown.expect(OWL2JavaException.class);
        thrown.expectMessage(containsString("Unable to load ontology"));
        final File targetDir = getTempDirectory();
        transformer.setOntology(BAD_IMPORT_ONTOLOGY_IRI, mappingFilePath);
        transformer.generateVocabulary(config(null, "", targetDir.getAbsolutePath(), true).build());
    }

    @Test
    public void transformationIgnoresMissingImportWhenConfiguredTo() throws Exception {
        this.targetDir = getTempDirectory();
        transformer.ignoreMissingImports(true);
        transformer.setOntology(BAD_IMPORT_ONTOLOGY_IRI, mappingFilePath);
        transformer.generateVocabulary(config(null, "", targetDir.getAbsolutePath(), true).build());
        verifyVocabularyFileExistence(targetDir);
    }

    private List<String> getGeneratedClass(File directory, String className) throws Exception {
        final File path = new File(
                directory.getAbsolutePath() + File.separator + PACKAGE.replace(".", File.separator) + File.separator +
                        "model" + File.separator + className + ".java");
        return Files.readAllLines(path.toPath());
    }

    private String getFieldDeclaration(List<String> classFileLines, String fieldName) {
        int i;
        for (i = 0; i < classFileLines.size(); i++) {
            if (classFileLines.get(i).endsWith(fieldName + ";")) {
                break;
            }
        }
        int start = i - 1;
        while (start > 0 && !classFileLines.get(start).trim().startsWith("protected")) {
            start--;
        }
        final List<String> declaration = classFileLines.subList(start + 1, i + 1);
        return declaration.stream().reduce((a, b) -> a + b).get();
    }

    @Test
    public void generateVocabularyEliminatesDuplicateConstructs() throws Exception {
        this.targetDir = getTempDirectory();
        transformer.setOntology("http://krizik.felk.cvut.cz/ontologies/onto-with-same-property-in-import.owl",
                mappingFilePath);
        transformer.generateVocabulary(config(null, "", targetDir.getAbsolutePath(), false).build());
        final File vocabularyFile = targetDir.listFiles()[0];
        final String fileContents = readFile(vocabularyFile);
        final String property = "http://krizik.felk.cvut.cz/ontologies/owl2java-onto.owl#createdBy";
        verifyIriOccursOnce(fileContents, property);
    }

    private void verifyIriOccursOnce(String fileContents, String iri) {
        int count = 0;
        int startInd = 0;
        while ((startInd = fileContents.indexOf(iri, startInd)) != -1) {
            startInd++;
            count++;
        }
        assertEquals(1, count);
    }

    @Test
    public void generateVocabularyDoesNotDuplicateRdfProperties() throws Exception {
        this.targetDir = getTempDirectory();
        transformer.setOntology("http://spinrdf.org/sp", mappingFilePath);
        transformer.generateVocabulary(config(null, "", targetDir.getAbsolutePath(), false).build());
        final File vocabularyFile = targetDir.listFiles()[0];
        final String fileContents = readFile(vocabularyFile);
        final String property = "http://spinrdf.org/sp#predicate";
        verifyIriOccursOnce(fileContents, property);
    }

    @Test
    public void generateVocabularyHandlesDuplicateLocalNameWhenAddingOntologyIri() throws Exception {
        this.targetDir = getTempDirectory();
        transformer.setOntology("http://krizik.felk.cvut.cz/ontologies/model",
                mappingFilePath);
        transformer.generateVocabulary(config(null, "", targetDir.getAbsolutePath(), true).build());
        final File vocabularyFile = targetDir.listFiles()[0];
        final String fileContents = readFile(vocabularyFile);
        assertTrue(fileContents.contains("ONTOLOGY_IRI_model"));
        assertTrue(fileContents.contains("ONTOLOGY_IRI_model_A"));
    }

    @Test
    public void setOntologyAddsDeclaredClassesIntoContextDefinition() throws Exception {
        this.targetDir = getTempDirectory();
        transformer.setOntology("http://krizik.felk.cvut.cz/ontologies/owl2java-ics.owl",
                mappingFilePath);
        final ContextDefinition context = getContext("owl2java-ic");
        assertTrue(context.classes
                .contains(dataFactory
                        .getOWLClass("http://krizik.felk.cvut.cz/ontologies/owl2java-onto.owl#UnusedClass")));
    }

    private ContextDefinition getContext(String name) throws Exception {
        final Method method = OWL2JavaTransformer.class.getDeclaredMethod("getContextDefinition", String.class);
        method.setAccessible(true);
        return (ContextDefinition) method.invoke(transformer, name);
    }

    @Test
    public void setOntologyAddsDeclaredObjectPropertiesIntoContextDefinition() throws Exception {
        this.targetDir = getTempDirectory();
        transformer.setOntology("http://krizik.felk.cvut.cz/ontologies/owl2java-ics.owl",
                mappingFilePath);
        final ContextDefinition context = getContext("owl2java-ic");
        assertTrue(context.objectProperties.contains(dataFactory
                .getOWLObjectProperty("http://krizik.felk.cvut.cz/ontologies/owl2java-onto.owl#unusedObjectProperty")));
    }

    @Test
    public void setOntologyAddsDeclaredDataPropertiesIntoContextDefinition() throws Exception {
        this.targetDir = getTempDirectory();
        transformer.setOntology("http://krizik.felk.cvut.cz/ontologies/owl2java-ics.owl",
                mappingFilePath);
        final ContextDefinition context = getContext("owl2java-ic");
        assertTrue(context.dataProperties.contains(dataFactory
                .getOWLDataProperty("http://krizik.felk.cvut.cz/ontologies/owl2java-onto.owl#unusedDataProperty")));
    }

    @Test
    public void setOntologyAddsDeclaredAnnotationPropertiesIntoContextDefinition() throws Exception {
        this.targetDir = getTempDirectory();
        transformer.setOntology("http://krizik.felk.cvut.cz/ontologies/owl2java-ics.owl",
                mappingFilePath);
        final ContextDefinition context = getContext("owl2java-ic");
        assertTrue(context.annotationProperties.contains(dataFactory.getOWLAnnotationProperty(
                "http://krizik.felk.cvut.cz/ontologies/owl2java-onto.owl#unusedAnnotationProperty")));
    }

    @Test
    public void generateVocabularyGeneratesJavadocFromRdfsComments() throws Exception {
        this.targetDir = getTempDirectory();
        transformer.setOntology(IC_ONTOLOGY_IRI, mappingFilePath);
        transformer.generateVocabulary(config(null, "", targetDir.getAbsolutePath(), false).build());
        final File vocabFile = new File(targetDir.getAbsolutePath() + File.separator + VOCABULARY_FILE);
        assertTrue(readFile(vocabFile).contains("Connects artifact to its author."));
    }

    @Test
    public void transformGeneratesJavadocOnAttributesFromRdfsComments() throws Exception {
        this.targetDir = getTempDirectory();
        assertEquals(0, targetDir.listFiles().length);
        transformer.setOntology(IC_ONTOLOGY_IRI, mappingFilePath);
        transformer.transform(config(CONTEXT, "", targetDir.getAbsolutePath(), false).build());
        final File reportClass = new File(
                targetDir + File.separator + Constants.MODEL_PACKAGE + File.separator + "Report.java");
        final List<String> lines = Files.readAllLines(reportClass.toPath());
        assertTrue(readFile(reportClass).contains("Connects artifact to its author."));
    }

    @Test
    public void transformGeneratesJavadocOnClassFromRdfsComments() throws Exception {
        this.targetDir = getTempDirectory();
        assertEquals(0, targetDir.listFiles().length);
        transformer.setOntology(IC_ONTOLOGY_IRI, mappingFilePath);
        transformer.transform(config(CONTEXT, "", targetDir.getAbsolutePath(), false).build());
        final File reportClass = new File(
                targetDir + File.separator + Constants.MODEL_PACKAGE + File.separator + "Report.java");
        final List<String> lines = Files.readAllLines(reportClass.toPath());
        assertTrue(readFile(reportClass).contains("Represents a logical report filed by a person."));
    }

    @Test
    public void transformDoesNotGenerateJavadocWhenConfiguredNotTo() throws Exception {
        this.targetDir = getTempDirectory();
        transformer.setOntology(IC_ONTOLOGY_IRI, mappingFilePath);
        transformer.transform(
                config(null, "", targetDir.getAbsolutePath(), false).generateJavadoc(false).build());
        final File vocabFile = new File(targetDir.getAbsolutePath() + File.separator + VOCABULARY_FILE);
        assertFalse(readFile(vocabFile).contains("Connects artifact to its author."));
    }

    @Test
    public void transformWithEmptyPackageNameGeneratesCorrectPackageNameForEntityClasses() throws Exception {
        this.targetDir = getTempDirectory();
        transformer.setOntology(IC_ONTOLOGY_IRI, mappingFilePath);
        transformer.transform(config(CONTEXT, "", targetDir.getAbsolutePath(), false).build());
        final File modelFolder = new File(targetDir + File.separator + Constants.MODEL_PACKAGE);
        for (File entity : modelFolder.listFiles()) {
            final List<String> lines = Files.readAllLines(entity.toPath());
            lines.removeIf(line -> line.length() == 0);
            assertThat(lines.get(0), containsString("package " + Constants.MODEL_PACKAGE));
        }
    }

    @Test
    public void transformGeneratesSerializableEntities() throws Exception {
        this.targetDir = getTempDirectory();
        transformer.setOntology(IC_ONTOLOGY_IRI, mappingFilePath);
        transformer.transform(config(CONTEXT, "", targetDir.getAbsolutePath(), false).build());
        final File modelFolder = new File(targetDir + File.separator + Constants.MODEL_PACKAGE);
        for (File entityClass : modelFolder.listFiles()) {
            assertThat(readFile(entityClass), containsString("implements Serializable"));
        }
    }
}