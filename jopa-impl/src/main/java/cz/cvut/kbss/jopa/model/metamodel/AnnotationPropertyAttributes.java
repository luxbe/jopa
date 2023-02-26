/**
 * Copyright (C) 2022 Czech Technical University in Prague
 * <p>
 * This program is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any
 * later version.
 * <p>
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more
 * details. You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package cz.cvut.kbss.jopa.model.metamodel;

import cz.cvut.kbss.jopa.model.IRI;
import cz.cvut.kbss.jopa.model.annotations.OWLAnnotationProperty;
import cz.cvut.kbss.jopa.model.annotations.ParticipationConstraints;

class AnnotationPropertyAttributes extends PropertyAttributes {
    OWLAnnotationProperty oap;

    AnnotationPropertyAttributes(FieldMappingValidator validator, OWLAnnotationProperty owlPropertyAnnotation) {
        super(validator);
        assert owlPropertyAnnotation != null;
        oap = owlPropertyAnnotation;
    }

    @Override
    void resolve(ParticipationConstraints cons, MetamodelBuilder metamodelBuilder, Class<?> fieldValueCls) {
        super.resolve(cons, metamodelBuilder, fieldValueCls);

        this.persistentAttributeType = Attribute.PersistentAttributeType.ANNOTATION;
        this.iri = IRI.create(typeBuilderContext.resolveNamespace(oap.iri()));
        this.fetchType = oap.fetch();
        this.type = BasicTypeImpl.get(fieldValueCls);
        this.lexicalForm = oap.lexicalForm();
        this.simpleLiteral = oap.simpleLiteral();
        this.datatype = typeBuilderContext.resolveNamespace(oap.datatype());
        this.language = resolveLanguage(fieldValueCls);
    }
}
