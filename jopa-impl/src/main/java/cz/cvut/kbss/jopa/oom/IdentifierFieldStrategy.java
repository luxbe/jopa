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
package cz.cvut.kbss.jopa.oom;

import cz.cvut.kbss.jopa.model.descriptors.Descriptor;
import cz.cvut.kbss.jopa.model.metamodel.EntityType;
import cz.cvut.kbss.jopa.model.metamodel.Identifier;
import cz.cvut.kbss.ontodriver.model.Assertion;
import cz.cvut.kbss.ontodriver.model.Axiom;
import cz.cvut.kbss.ontodriver.model.Value;

class IdentifierFieldStrategy<X> extends FieldStrategy<Identifier<? super X, ?>, X> {

    IdentifierFieldStrategy(EntityType<X> et, Identifier<? super X, ?> att, Descriptor attributeDescriptor,
                            EntityMappingHelper mapper) {
        super(et, att, attributeDescriptor, mapper);
    }

    @Override
    void addValueFromAxiom(Axiom<?> ax) {
        // Do nothing
    }

    @Override
    void buildInstanceFieldValue(Object instance) throws IllegalAccessException {
        // Do nothing
    }

    @Override
    void buildAxiomValuesFromInstance(X instance, AxiomValueGatherer valueBuilder) throws IllegalAccessException {
        valueBuilder.addValue(createAssertion(), new Value<>(et.getIRI().toURI()), attributeDescriptor.getContext());
    }

    @Override
    Assertion createAssertion() {
        assert !attribute.isInferred();
        return Assertion.createClassAssertion(attribute.isInferred());
    }
}