/**
 * Copyright (C) 2022 Czech Technical University in Prague
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
package cz.cvut.kbss.ontodriver.sesame;

import cz.cvut.kbss.ontodriver.Properties;
import cz.cvut.kbss.ontodriver.exception.OntoDriverException;
import cz.cvut.kbss.ontodriver.model.Assertion;
import cz.cvut.kbss.ontodriver.model.Axiom;
import cz.cvut.kbss.ontodriver.model.NamedResource;
import cz.cvut.kbss.ontodriver.model.Value;
import cz.cvut.kbss.ontodriver.sesame.config.RuntimeConfiguration;
import cz.cvut.kbss.ontodriver.sesame.connector.Connector;
import cz.cvut.kbss.ontodriver.sesame.exceptions.SesameDriverException;

import java.net.URI;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

class SesameProperties implements Properties {

    private final Connector connector;
    private final RuntimeConfiguration config;

    private final Procedure beforeCallback;
    private final Procedure afterChangeCallback;

    public SesameProperties(SesameAdapter adapter, Procedure beforeCallback, Procedure afterChangeCallback) {
        this.connector = adapter.getConnector();
        this.config = adapter.getConfig();
        this.beforeCallback = beforeCallback;
        this.afterChangeCallback = afterChangeCallback;
    }

    @Override
    public Collection<Axiom<?>> getProperties(NamedResource individual, URI context, boolean includeInferred)
            throws SesameDriverException {
        beforeCallback.execute();
        return new AxiomLoader(connector, config).loadAxioms(individual, includeInferred, context);
    }

    @Override
    public void addProperties(NamedResource individual, URI context, Map<Assertion, Set<Value<?>>> properties)
            throws OntoDriverException {
        beforeCallback.execute();
        new AxiomSaver(connector).persistAxioms(individual, properties, context);
        afterChangeCallback.execute();
    }

    @Override
    public void removeProperties(NamedResource individual, URI context, Map<Assertion, Set<Value<?>>> properties)
            throws OntoDriverException {
        new EpistemicAxiomRemover(connector, connector.getValueFactory()).remove(individual, properties, context);
        afterChangeCallback.execute();
    }
}
