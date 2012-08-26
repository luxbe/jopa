/**
 * Copyright (C) 2011 Czech Technical University in Prague
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

package cz.cvut.kbss.jopa.owlapi;

import java.lang.reflect.Field;
import java.util.logging.Level;
import java.util.logging.Logger;

import cz.cvut.kbss.jopa.model.OWLPersistenceException;
import cz.cvut.kbss.jopa.model.annotations.OWLClass;
import cz.cvut.kbss.jopa.model.annotations.OWLDataProperty;
import cz.cvut.kbss.jopa.model.annotations.OWLObjectProperty;
import cz.cvut.kbss.jopa.model.annotations.Properties;
import cz.cvut.kbss.jopa.model.annotations.Types;
import cz.cvut.kbss.jopa.sessions.CloneBuilderImpl;

public aspect BeanListenerAspect {

	private static final Logger LOG = Logger.getLogger(BeanListenerAspect.class
			.getName());

	pointcut getter() : get( @(OWLObjectProperty || OWLDataProperty || Types || Properties ) * * ) && within(@OWLClass *);

	pointcut setter() : set( @(OWLObjectProperty || OWLDataProperty || Types || Properties ) * * ) && within(@OWLClass *);

	before() : setter() {
		final Object object = thisJoinPoint.getTarget();
		Field field;
		try {
			field = object.getClass().getDeclaredField(
					thisJoinPoint.getSignature().getName());
		} catch (NoSuchFieldException e) {
			LOG.log(Level.SEVERE, e.getMessage(), e);
			throw new OWLPersistenceException();
		} catch (SecurityException e) {
			LOG.log(Level.SEVERE, e.getMessage(), e);
			throw new OWLPersistenceException();
		}
		if (CloneBuilderImpl.isFieldInferred(field)) {
			throw new OWLPersistenceException(
					"Modifying inferred attributes is forbidden.");
		}
	}

	after() returning : setter() {
		final Object entity = thisJoinPoint.getTarget();

		OWLAPIPersistenceProvider.persistEntityChanges(entity);
	}

	before() : getter()  {
		try {
			final Object object = thisJoinPoint.getTarget();
			final Field field = object.getClass().getDeclaredField(
					thisJoinPoint.getSignature().getName());

			field.setAccessible(true);

			if (LOG.isLoggable(Level.CONFIG)) {
				LOG.config("*** Fetching " + field.getName() + " of "
						+ object.getClass() + ":" + object.hashCode());
			}

			OWLAPIPersistenceProvider.loadReference(object, field);
		} catch (NoSuchFieldException e) {
			LOG.log(Level.SEVERE, e.getMessage(), e);
			throw new OWLPersistenceException();
		} catch (IllegalArgumentException e) {
			LOG.log(Level.SEVERE, e.getMessage(), e);
			throw new OWLPersistenceException();
		} catch (IllegalAccessException e) {
			LOG.log(Level.SEVERE, e.getMessage(), e);
			throw new OWLPersistenceException();
		}
	}
}
