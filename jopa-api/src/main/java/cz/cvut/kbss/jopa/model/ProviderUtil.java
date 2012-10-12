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

package cz.cvut.kbss.jopa.model;

/**
 * Utility interface implemented by the persistence provider. This interface is
 * invoked by the PersistenceUtil implementation to determine the load status of
 * an entity or entity attribute.
 */
public interface ProviderUtil {

	/**
	 * If the provider determines that the entity has been provided by itself
	 * and that the state of the specified attribute has been loaded, this
	 * method returns LoadState.LOADED. If the provider determines that the
	 * entity has been provided by itself and that either entity attributes with
	 * FetchType EAGER have not been loaded or that the state of the specified
	 * attribute has not been loaded, this methods returns LoadState.NOT_LOADED.
	 * If the provider cannot determine the load state, this method returns
	 * LoadState.UNKNOWN. The provider's implementation of this method must not
	 * obtain a reference to an attribute value, as this could trigger the
	 * loading of entity state if the entity has been provided by a different
	 * provider.
	 * 
	 * @param entity
	 * @param attributeName
	 *            name of attribute whose load status is to be determined
	 * @return load status of the attribute
	 */
	public LoadState isLoadedWithoutReference(Object entity,
			String attributeName);

	/**
	 * If the provider determines that the entity has been provided by itself
	 * and that the state of the specified attribute has been loaded, this
	 * method returns LoadState.LOADED. If the provider determines that the
	 * entity has been provided by itself and that either entity attributes with
	 * FetchType EAGER have not been loaded or that the state of the specified
	 * attribute has not been loaded, this methods returns LoadState.NOT_LOADED.
	 * If the provider cannot determine the load state, this method returns
	 * LoadState.UNKNOWN. The provider's implementation of this method is
	 * permitted to obtain a reference to the attribute value. (This access is
	 * safe, because providers which might trigger the loading of the attribute
	 * state will have already been determined by isLoadedWithoutReference. )
	 * 
	 * @param entity
	 * @param attributeName
	 *            name of attribute whose load status is to be determined
	 * @return load status of the attribute
	 */
	public LoadState isLoadedWithReference(Object entity, String attributeName);

	public LoadState isLoaded(Object entity);

}
