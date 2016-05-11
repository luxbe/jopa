/**
 * Copyright (C) 2016 Czech Technical University in Prague
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

import com.sun.codemodel.*;
import cz.cvut.kbss.jopa.CommonVocabulary;
import cz.cvut.kbss.jopa.model.SequencesVocabulary;
import cz.cvut.kbss.jopa.model.annotations.*;
import cz.cvut.kbss.jopa.model.annotations.OWLAnnotationProperty;
import cz.cvut.kbss.jopa.model.annotations.OWLDataProperty;
import cz.cvut.kbss.jopa.model.annotations.OWLObjectProperty;
import cz.cvut.kbss.jopa.model.annotations.Properties;
import cz.cvut.kbss.jopa.model.ic.DataParticipationConstraint;
import cz.cvut.kbss.jopa.model.ic.ObjectParticipationConstraint;
import cz.cvut.kbss.jopa.owl2java.IntegrityConstraintParserImpl.ClassDataPropertyComputer;
import cz.cvut.kbss.jopa.owl2java.IntegrityConstraintParserImpl.ClassObjectPropertyComputer;
import cz.cvut.kbss.jopa.owlapi.DatatypeTransformer;
import cz.cvut.kbss.jopa.util.MappingFileParser;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.util.OWLOntologyMerger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.*;

import static cz.cvut.kbss.jopa.owl2java.Constants.*;

class ContextDefinition {
    private final String name;
    final Set<OWLClass> classes = new HashSet<>();
    final Set<org.semanticweb.owlapi.model.OWLObjectProperty> objectProperties = new HashSet<>();
    final Set<org.semanticweb.owlapi.model.OWLDataProperty> dataProperties = new HashSet<>();
    final Set<org.semanticweb.owlapi.model.OWLAnnotationProperty> annotationProperties = new HashSet<>();

    ContextDefinition(String name) {
        this.name = name;
    }

    final Set<OWLAxiom> axioms = new HashSet<>();

    final IntegrityConstraintParserImpl parser = new IntegrityConstraintParserImpl(
            OWLManager.getOWLDataFactory(), this);
}

public class OWL2JavaTransformer {

    private static final Logger LOG = LoggerFactory.getLogger(OWL2JavaTransformer.class);

    private static final List<IRI> skipped = Arrays
            .asList(IRI.create(SequencesVocabulary.c_Collection), IRI.create(SequencesVocabulary.c_List),
                    IRI.create(SequencesVocabulary.c_OWLSimpleList),
                    IRI.create(SequencesVocabulary.c_OWLReferencedList));

    private OWLDataFactory f;

    private OWLOntology merged;

    private Set<OWLOntology> imports;

    private Map<OWLClass, JDefinedClass> classes = new HashMap<>();

    private Map<OWLEntity, JFieldRef> entities = new HashMap<>();

    // private Map<JFieldRef, JAnnotationArrayMember> constraints = new
    // HashMap<JFieldRef, JAnnotationArrayMember>();

    private JDefinedClass voc;

    private Map<String, ContextDefinition> contexts = new HashMap<>();

    public Collection<String> listContexts() {
        return contexts.keySet();
    }

    private class ValidContextAnnotationValueVisitor implements OWLAnnotationValueVisitor {
        private String name = null;

        String getName() {
            return name;
        }

        public void visit(IRI iri) {
        }

        public void visit(OWLAnonymousIndividual individual) {
        }

        public void visit(OWLLiteral literal) {
            name = literal.getLiteral();
        }
    }

    private final ValidContextAnnotationValueVisitor v = new ValidContextAnnotationValueVisitor();

    public void setOntology(final OWLOntology merged, final Set<OWLOntology> imports, boolean includeImports) {

        f = merged.getOWLOntologyManager().getOWLDataFactory();

        this.imports = imports;

        LOG.info("Parsing integrity constraints");
        // final IntegrityConstraintParserImpl icp = new
        // IntegrityConstraintParserImpl();

        // final Set<OWLAxiom> ics = new HashSet<OWLAxiom>();

        for (final OWLAxiom a : merged.getAxioms()) {
            for (final OWLAnnotation p : a.getAnnotations()) {
                p.getValue().accept(v);
                final String icContextName = v.getName();
                if (icContextName == null) {
                    continue;
                }

                ContextDefinition ctx = contexts.get(icContextName);

                if (ctx == null) {
                    ctx = new ContextDefinition(icContextName);
                    contexts.put(icContextName, ctx);
                }

                LOG.debug("Found IC {} for context {}", a, icContextName);

                for (final OWLEntity e : a.getSignature()) {
                    if (e.isOWLClass() && !skipped.contains(e.getIRI())) {
                        ctx.classes.add(e.asOWLClass());
                    }
                    if (e.isOWLObjectProperty() && !skipped.contains(e.getIRI())) {
                        ctx.objectProperties.add(e.asOWLObjectProperty());
                    }
                    if (e.isOWLDataProperty() && !skipped.contains(e.getIRI())) {
                        ctx.dataProperties.add(e.asOWLDataProperty());
                    }
                    if (e.isOWLAnnotationProperty() && !skipped.contains(e.getIRI())) {
                        ctx.annotationProperties.add(e.asOWLAnnotationProperty());
                    }
                }
                ctx.axioms.add(a);
                // ics.add(a);
            }
        }

        for (final ContextDefinition ctx : contexts.values()) {
            ctx.parser.parse();
        }

        LOG.info("Integrity constraints successfully parsed.");
    }

    public void setOntology(final String owlOntologyName,
                            final String mappingFile, boolean includeImports) {
        // reader
        final OWLOntologyManager m = OWLManager.createOWLOntologyManager();

        if (mappingFile != null) {
            LOG.info("Using mapping file '{}'.", mappingFile);

            final Map<URI, URI> map = MappingFileParser.getMappings(new File(mappingFile));
            m.addIRIMapper(ontologyIRI -> {
                final URI value = map.get(ontologyIRI.toURI());

                if (value == null) {
                    return null;
                } else {
                    return IRI.create(value);
                }
            });
            LOG.info("Mapping file successfully parsed.");
        }

        LOG.info("Loading ontology {} ... ", owlOntologyName);
        m.setSilentMissingImportsHandling(false);

        try {
            m.loadOntology(org.semanticweb.owlapi.model.IRI.create(owlOntologyName));
            merged = new OWLOntologyMerger(m)
                    .createMergedOntology(m, org.semanticweb.owlapi.model.IRI.create(owlOntologyName + "-generated"));
        } catch (OWLOntologyCreationException e) {
            LOG.error(e.getMessage(), e);
            throw new IllegalArgumentException("Unable to load ontology " + owlOntologyName, e);
        }

        setOntology(merged, m.getOntologies(), includeImports);
    }

    private JFieldVar addField(final String name, final JDefinedClass cls,
                               final JType fieldType) {
        String newName = name;

        int i = 0;
        while (cls.fields().containsKey(newName)) {
            newName = name + "" + (++i);
        }

        final JFieldVar fvId = cls.field(JMod.PROTECTED, fieldType, newName);
        final String fieldName = fvId.name().substring(0, 1).toUpperCase() + fvId.name().substring(1);
        final JMethod mSetId = cls.method(JMod.PUBLIC, void.class, "set" + fieldName);
        final JVar v = mSetId.param(fieldType, fvId.name());
        mSetId.body().assign(JExpr._this().ref(fvId), v);
        final JMethod mGetId = cls.method(JMod.PUBLIC, fieldType, "get" + fieldName);
        mGetId.body()._return(fvId);
        return fvId;
    }

    private JDefinedClass ensureCreated(final ContextDefinition ctx,
                                        final String pkg, final JCodeModel cm, final OWLClass clazz) {
        if (classes.containsKey(clazz)) {
            return classes.get(clazz);
        }

        JDefinedClass cls;

        String name = pkg + javaClassId(clazz, ctx);

        try {
            cls = cm._class(name);

            cls.annotate(
                    cz.cvut.kbss.jopa.model.annotations.OWLClass.class)
               .param("iri", entities.get(clazz));

            final JDocComment dc = cls.javadoc();
            dc.add("This class was generated by the OWL2Java tool version " + VERSION);

            // if (clazz.equals(f.getOWLThing())) {
            // RDFS label
            final JClass ftLabel = cm.ref(String.class);
            final JFieldVar fvLabel = addField("name", cls, ftLabel);
            fvLabel.annotate(OWLAnnotationProperty.class).param("iri",
                    cm.ref(CommonVocabulary.class).staticRef("RDFS_LABEL"));

            // DC description
            final JClass ftDescription = cm.ref(String.class);
            final JFieldVar fvDescription = addField("description", cls, ftDescription);
            fvDescription.annotate(OWLAnnotationProperty.class).param("iri",
                    cm.ref(CommonVocabulary.class).staticRef("DC_DESCRIPTION"));

            // @Types Set<String> types;
            final JClass ftTypes = cm.ref(Set.class).narrow(String.class);
            final JFieldVar fvTypes = addField("types", cls, ftTypes);
            fvTypes.annotate(Types.class);

            // @Id public final String id;
            final JClass ftId = cm.ref(String.class);
            final JFieldVar fvId = addField("id", cls, ftId);
            JAnnotationUse a = fvId.annotate(Id.class);

            a.param("generated", true);

            // @Properties public final Map<String,Set<String>> properties;
            final JClass ftProperties = cm.ref(Map.class).narrow(
                    cm.ref(String.class),
                    cm.ref(Set.class).narrow(String.class));
            final JFieldVar fvProperties = addField("properties", cls,
                    ftProperties);
            fvProperties.annotate(Properties.class);
            // }

            // // public final Map<Object,Set<Object>> other;
            // final JClass cSetN = cm.ref(Set.class).narrow(Object.class);
            // final JClass cMapN =
            // cm.ref(Map.class).narrow(cm.ref(Object.class),
            // cSetN);
            // final JFieldVar fv = cls.field(JMod.PRIVATE, cMapN, "others");
            //
            // // getOther()
            // final JMethod m = cls.method(JMod.PUBLIC, cSetN, "findOther");
            // final JVar p = m.param(Object.class, "property");
            // m.body()._return(fv.invoke("get").arg(p));

            // for (OWLClass cx : classes.keySet()) {
            // JDefinedClass cxc = classes.get(cx);
            // if (r.isEntailed(OWLManager.getOWLDataFactory()
            // .getOWLSubClassOfAxiom(cx, c))) {
            // cxc = cxc._extends(cls);
            // } else if (r.isEntailed(OWLManager.getOWLDataFactory()
            // .getOWLSubClassOfAxiom(c, cx))) {
            // cls = cls._extends(cxc);
            // }
            // }

            // TODO superClasses
            // final OWLClass superClass = ctx.parser.getSuperClass(c);
            // if ( superClass != null ) {
            // ensureCreated(ctx, pkg, cm, superClass);
            // cls._extends(classes.get(superClass));
            // }
        } catch (JClassAlreadyExistsException e) {
            cls = cm._getClass(name);
        }
        classes.put(clazz, cls);

        return cls;
    }

    private void generateVocabulary(final JCodeModel cm, boolean withOWLAPI) {
        LOG.debug("Generating vocabulary...");

        final Collection<OWLEntity> col = new HashSet<>();
        col.add(f.getOWLThing());
        col.addAll(merged.getSignature());

        for (final OWLOntology s : imports) {
            IRI iri = s.getOntologyID().getOntologyIRI();
            voc.field(JMod.PUBLIC | JMod.STATIC
                            | JMod.FINAL, String.class, "ONTOLOGY_IRI_" + validJavaIDForIRI(iri),
                    JExpr.lit(iri.toString()));
        }

        for (final OWLEntity c : col) {
            String prefix = "";

            if (c.isOWLClass()) {
                prefix = "c_";
            } else if (c.isOWLDatatype()) {
                prefix = "d_";
            } else if (c.isOWLDataProperty() || c.isOWLObjectProperty()
                    || c.isOWLAnnotationProperty()) {
                prefix = "p_";
            } else if (c.isOWLNamedIndividual()) {
                prefix = "i_";
            }

            String id = prefix + validJavaIDForIRI(c.getIRI());

            while (voc.fields().keySet().contains("s_" + id)) {
                id += "_A";
            }

            final String sFieldName = "s_" + id;

            final JFieldVar fv1 = voc.field(JMod.PUBLIC | JMod.STATIC
                            | JMod.FINAL, String.class, sFieldName,
                    JExpr.lit(c.getIRI().toString()));
            if (withOWLAPI) {
                voc.field(JMod.PUBLIC | JMod.STATIC | JMod.FINAL, IRI.class, id, cm
                        .ref(IRI.class).staticInvoke("create").arg(fv1));
            }

            entities.put(c, voc.staticRef(fv1));
        }
    }

    private static String validJavaIDForIRI(final IRI iri) {
        if (iri.getFragment() != null) {
            return validJavaID(iri.getFragment());
        } else {
            int x = iri.toString().lastIndexOf("/");
            return validJavaID(iri.toString().substring(x + 1));
        }
    }

    private String javaClassId(OWLClass owlClass, ContextDefinition ctx) {
        final Set<OWLAnnotation> annotations = owlClass.getAnnotations(merged);
        for (OWLAnnotation a : annotations) {
            if (isValidJavaClassName(a, ctx)) {
                if (a.getValue() instanceof OWLLiteral) {
                    return ((OWLLiteral) a.getValue()).getLiteral();
                }
            }
        }
        return validJavaIDForIRI(owlClass.getIRI());
    }

    private boolean isValidJavaClassName(OWLAnnotation a, ContextDefinition ctx) {
        // TODO Replace this hardcoded stuff with a configurable solution
        return a.getProperty().getIRI()
                .equals(IRI.create("http://krizik.felk.cvut.cz/ontologies/2009/ic.owl#javaClassName"));
        // Annotation of annotation is currently not supported
//        for (OWLAnnotation ctxAnn : a.getAnnotations()) {
//            ctxAnn.getValue().accept(v);
//            final String icContextName = v.getName();
//            System.out.println("Context: " + icContextName);
//            if (icContextName != null && icContextName.equals(ctx.name)) {
//                return true;
//            }
//        }
    }

    private static final String[] keywords = {"abstract",
            "assert",
            "boolean",
            "break",
            "byte",
            "case",
            "catch",
            "char",
            "class",
            "const",
            "continue",
            "default",
            "do",
            "double",
            "else",
            "enum",
            "extends",
            "final",
            "finally",
            "float",
            "for",
            "goto",
            "if",
            "implements",
            "import",
            "instanceof",
            "int",
            "interface",
            "long",
            "native",
            "new",
            "package",
            "private",
            "protected",
            "public",
            "return",
            "short",
            "static",
            "strictfp",
            "super",
            "switch",
            "synchronized",
            "this",
            "throw",
            "throws",
            "transient",
            "try",
            "void",
            "volatile",
            "while"};

    private static String validJavaID(final String s) {
        String res = s.trim().replace("-", "_").replace("'", "_quote_").replace(".", "_dot_");
        if (Arrays.binarySearch(keywords, res) >= 0) {
            res = "_" + res;
        }
        return res;
    }

    // class MaxICRestrictor implements IntegrityConstraintVisitor {
    //
    // final OWLClass s;
    // final OWLProperty<?, ?> p;
    // final OWLObject o;
    // int max;
    // boolean valid = false;
    // String pkg;
    //
    // MaxICRestrictor(final OWLClass s, final OWLProperty<?, ?> po,
    // final OWLObject oc, int max) {
    // this.s = s;
    // this.p = po;
    // this.o = oc;
    // this.max = max;
    // }
    //
    //
    // public void visit(ObjectParticipationConstraint cpc) {
    // // if (!r.isEntailed(f.getOWLSubClassOfAxiom(s, cpc.getSubject()))
    // // || !r.isEntailed(f.getOWLSubObjectPropertyOfAxiom(po, cpc
    // // .getPredicate()))
    // // || !r.isEntailed(f.getOWLSubClassOfAxiom(oc, cpc
    // // .getObject()))) {
    // // return;
    // // }
    // if (!s.equals(cpc.getSubject()) || !p.equals(cpc.getPredicate())
    // || !o.equals(cpc.getObject())) {
    // return;
    // }
    //
    // valid = true;
    //
    // if (cpc.getMax() >= 0) {
    // max = Math.min(cpc.getMax(), max);
    // }
    // }
    //
    //
    // public void visit(DataParticipationConstraint cpc) {
    // // if (!r.isEntailed(f.getOWLSubClassOfAxiom(s, cpc.getSubject()))
    // // || !r.isEntailed(f.getOWLSubDataPropertyOfAxiom(pd, cpc
    // // .getPredicate())) || !od.equals(cpc.getObject())) {
    // // return;
    // // }
    // if (!s.equals(cpc.getSubject()) || !p.equals(cpc.getPredicate())
    // || !o.equals(cpc.getObject())) {
    // return;
    // }
    //
    // valid = true;
    //
    // if (cpc.getMax() >= 0) {
    // max = Math.min(cpc.getMax(), max);
    // }
    // }
    // }
    //
    // private void generateAttribute(final String pkg, final OWLClass s,
    // final org.semanticweb.owlapi.model.OWLObjectProperty p,
    // final OWLClass o, final Collection<IntegrityConstraint> ics,
    // final JCodeModel cm) {
    // final JDefinedClass subj = ensureCreated(pkg, cm, s);
    // final JDefinedClass obj = ensureCreated(pkg, cm, o);
    //
    // Set<ObjectParticipationConstraint> annotations = new
    // HashSet<ObjectParticipationConstraint>();
    //
    // int max = Integer.MAX_VALUE;
    //
    // for (final IntegrityConstraint c : ics) {
    // final MaxICRestrictor r = new MaxICRestrictor(s, p, o, max);
    // c.accept(r);
    // max = Math.min(max, r.max);
    // if (r.valid)
    // annotations.add((ObjectParticipationConstraint) c);
    // }
    //
    // final String fieldName = validJavaID(p.getIRI().getFragment());
    //
    // if (r.isEntailed(f.getOWLSubClassOfAxiom(s, f
    // .getOWLObjectMaxCardinality(1, p)))) {
    // max = Math.min(max, 1);
    // }
    //
    // final JFieldVar fv;
    //
    // if (max > 1) {
    // fv = addField(fieldName, subj, cm.ref(java.util.Set.class).narrow(
    // obj));
    // } else {
    // fv = addField(fieldName, subj, obj);
    // }
    //
    // fv.annotate(OWLObjectProperty.class)
    // .param("iri", entities.get(p)).param("fillerIri",
    // entities.get(o));
    //
    // if (!annotations.isEmpty()) {
    // JAnnotationArrayMember use = constraints.get(subj);
    //
    // if (use == null) {
    // use = subj.annotate(ParticipationConstraints.class).paramArray("value");
    // constraints.put(subj, use);
    // }
    //
    // for (ObjectParticipationConstraint ic : annotations) {
    // use.annotate(ParticipationConstraint.class).param(
    // "owlClassIRI", ic.getSubject().getIRI().toString())
    // .param("owlPropertyIRI",
    // ic.getPredicate().getIRI().toString()).param(
    // "owlObjectIRI",
    // ic.getObject().getIRI().toString()).param(
    // "min", ic.getMin()).param("max", ic.getMax());
    // }
    // }
    // }
    //
    // private void generateAttribute(final String pkg, final OWLClass s,
    // final org.semanticweb.owlapi.model.OWLDataProperty p,
    // final OWLDatatype o, final Collection<IntegrityConstraint> ics,
    // final JCodeModel cm) {
    // final JDefinedClass subj = ensureCreated(pkg, cm, s);
    // final JType obj = cm._ref(DatatypeTransformer.transformOWLType(o
    // .asOWLDatatype()));
    //
    // Set<DataParticipationConstraint> annotations = new
    // HashSet<DataParticipationConstraint>();
    //
    // int max = Integer.MAX_VALUE;
    //
    // for (final IntegrityConstraint c : ics) {
    // final MaxICRestrictor r = new MaxICRestrictor(s, p, o, max);
    // c.accept(r);
    // max = Math.min(max, r.max);
    // if (r.valid)
    // annotations.add((DataParticipationConstraint) c);
    // }
    //
    // final String fieldName = validJavaID(p.getIRI().getFragment());
    //
    // if (r.isEntailed(f.getOWLSubClassOfAxiom(s, f.getOWLDataMaxCardinality(
    // 1, p)))) {
    // max = Math.min(max, 1);
    // }
    //
    // JFieldVar fv;
    //
    // if (max > 1) {
    // fv = addField(fieldName, subj, cm.ref(java.util.Set.class).narrow(
    // obj));
    // } else {
    // fv = addField(fieldName, subj, obj);
    // }
    //
    // fv.annotate(OWLDataProperty.class).param("iri", p.getIRI().toString())
    // .param("fillerIri", o.getIRI().toString());
    //
    // if (!annotations.isEmpty()) {
    // JAnnotationArrayMember use = constraints.get(subj);
    //
    // if (use == null) {
    // use = subj.annotate(ParticipationConstraints.class).paramArray("value");
    // constraints.put(subj, use);
    // }
    //
    // for (DataParticipationConstraint ic : annotations) {
    // use.annotate(ParticipationConstraint.class).param(
    // "owlClassIRI", ic.getSubject().getIRI().toString())
    // .param("owlPropertyIRI",
    // ic.getPredicate().getIRI().toString()).param(
    // "owlObjectIRI",
    // ic.getObject().getIRI().toString()).param(
    // "min", ic.getMin()).param("max", ic.getMax());
    // }
    // }
    //
    // }

    private void generateModel(final JCodeModel cm,
                               final ContextDefinition context, final String pkg) {
        LOG.info("Generating model ...");

        context.classes.add(f.getOWLThing());

        for (final OWLClass clazz : context.classes) {
            LOG.info("  Generating class '{}'.", clazz);
            final JDefinedClass subj = ensureCreated(context, pkg, cm, clazz);

            for (final org.semanticweb.owlapi.model.OWLObjectProperty prop : context.objectProperties) {

                final ClassObjectPropertyComputer comp = context.parser.new ClassObjectPropertyComputer(clazz, prop,
                        merged);

                if (Card.NO.equals(comp.getCard())) {
                    continue;
                }

                JClass filler = ensureCreated(context, pkg, cm,
                        comp.getObject());
                final String fieldName = validJavaIDForIRI(prop.getIRI());

                switch (comp.getCard()) {
                    case ONE:
                        break;
                    case MULTIPLE:
                        filler = cm.ref(java.util.Set.class).narrow(filler);
                        break;
                    case SIMPLELIST:
                    case LIST:
                        filler = cm.ref(java.util.List.class).narrow(filler);
                        break;
                }

                final JFieldVar fv = addField(fieldName, subj, filler);

                if (comp.getCard().equals(Card.SIMPLELIST)) {
                    fv.annotate(Sequence.class)
                      .param("type", SequenceType.simple);
                }


                fv.annotate(OWLObjectProperty.class).param("iri",
                        entities.get(prop));

                JAnnotationArrayMember use = null;
                for (ObjectParticipationConstraint ic : comp
                        .getParticipationConstraints()) {
                    if (use == null) {
                        use = fv.annotate(ParticipationConstraints.class)
                                .paramArray("value");
                    }
                    JAnnotationUse u = use.annotate(
                            ParticipationConstraint.class).param(
                            // "owlClassIRI",
                            // ic.getSubject().getIRI().toString()).param(
                            // "owlPropertyIRI",
                            // ic.getPredicate().getIRI().toString()).param(
                            "owlObjectIRI", entities.get(ic.getObject()));
                    if (ic.getMin() != 0) {
                        u.param("min", ic.getMin());
                    }

                    if (ic.getMax() != -1) {
                        u.param("max", ic.getMax());
                    }
                }
            }

            for (org.semanticweb.owlapi.model.OWLDataProperty prop : context.dataProperties) {
                final ClassDataPropertyComputer comp = context.parser
                        .getClassDataPropertyComputer(clazz, prop, merged);

                if (Card.NO.equals(comp.getCard())) {
                    continue;
                }

                final JType obj = cm._ref(DatatypeTransformer
                        .transformOWLType(comp.getFiller()));

                final String fieldName = validJavaIDForIRI(
                        prop.getIRI());

                JFieldVar fv;

                if (Card.MULTIPLE.equals(comp.getCard())) {
                    fv = addField(fieldName, subj, cm.ref(java.util.Set.class)
                                                     .narrow(obj));
                } else if (Card.ONE.equals(comp.getCard())) {
                    fv = addField(fieldName, subj, obj);
                } else {
                    assert false : "Unknown cardinality type";
                    continue;
                }

                fv.annotate(OWLDataProperty.class).param("iri",
                        entities.get(prop));

                JAnnotationArrayMember use = null;
                for (DataParticipationConstraint ic : comp
                        .getParticipationConstraints()) {
                    if (use == null) {
                        use = fv.annotate(ParticipationConstraints.class)
                                .paramArray("value");
                    }
                    JAnnotationUse u = use.annotate(
                            ParticipationConstraint.class).param(
                            // "owlClassIRI",
                            // ic.getSubject().getIRI().toString()).param(
                            // "owlPropertyIRI",
                            // ic.getPredicate().getIRI().toString()).param(
                            "owlObjectIRI", entities.get(ic.getObject()));
                    if (ic.getMin() != 0) {
                        u = u.param("min", ic.getMin());
                    }

                    if (ic.getMax() != -1) {
                        u = u.param("max", ic.getMax());
                    }
                }
            }
        }
    }

    enum Card {
        NO, ONE, MULTIPLE, LIST, SIMPLELIST, REFERENCEDLIST
    }

    public void transform(String context, String p, String dir, boolean withOWLAPI) {
        LOG.info("Transforming context '{}'.", context);

        verifyContextExistence(context);

        final JCodeModel cm = new JCodeModel();

        try {
            voc = cm._class(p + PACKAGE_SEPARATOR + VOCABULARY_CLASS);

            generateVocabulary(cm, withOWLAPI);
            generateModel(cm, contexts.get(context), p + PACKAGE_SEPARATOR + MODEL_PACKAGE + PACKAGE_SEPARATOR);

            writeOutModel(cm, dir);
            LOG.info("Transformation SUCCESSFUL.");
        } catch (JClassAlreadyExistsException e1) {
            LOG.error("Transformation FAILED.", e1);
        } catch (IOException e) {
            LOG.error("File generation FAILED.", e);
        }
    }

    private void verifyContextExistence(String context) {
        if (!contexts.containsKey(context)) {
            throw new IllegalArgumentException(
                    "Context " + context + " not found. Existing contexts: " + listContexts());
        }
    }

    private void writeOutModel(JCodeModel cm, String targetDir) throws IOException {
        final File file = new File(targetDir);
        file.mkdirs();
        cm.build(file);
    }

    /**
     * Generates only vocabulary of the loaded ontology.
     *
     * @param context    Integrity constraints context
     * @param targetDir  Directory into which the vocabulary file will be generated
     * @param withOwlapi Whether OWLAPI-based IRIs of the generated vocabulary items should be created as well
     */
    public void generateVocabulary(String context, String targetDir, boolean withOwlapi) {
        LOG.info("Generating vocabulary for context '{}'.", context);

        verifyContextExistence(context);

        final JCodeModel cm = new JCodeModel();
        try {
            this.voc = cm._class(VOCABULARY_CLASS);
            generateVocabulary(cm, withOwlapi);
            writeOutModel(cm, targetDir);
        } catch (JClassAlreadyExistsException e) {
            LOG.error("Vocabulary generation FAILED, because the Vocabulary class already exists.", e);
        } catch (IOException e) {
            LOG.error("Vocabulary file generation FAILED.", e);
        }
    }
}
