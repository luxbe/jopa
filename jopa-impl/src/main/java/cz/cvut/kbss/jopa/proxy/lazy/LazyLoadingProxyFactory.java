package cz.cvut.kbss.jopa.proxy.lazy;

import cz.cvut.kbss.jopa.exceptions.OWLPersistenceException;
import cz.cvut.kbss.jopa.model.metamodel.FieldSpecification;
import cz.cvut.kbss.jopa.model.metamodel.ListAttribute;
import cz.cvut.kbss.jopa.proxy.lazy.gen.LazyLoadingEntityProxy;
import cz.cvut.kbss.jopa.sessions.UnitOfWork;

import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Creates lazy-loading proxies for entity attributes.
 * <p>
 * Needs a {@link UnitOfWork} to associate the proxies with, so that lazy loading can be executed when triggered.
 */
public class LazyLoadingProxyFactory {

    private final UnitOfWork uow;

    public LazyLoadingProxyFactory(UnitOfWork uow) {
        this.uow = uow;
    }

    /**
     * Creates a lazy loading proxy for the value of the specified attribute.
     *
     * @param entity    Entity whose attribute will be proxied
     * @param fieldSpec Attribute to proxy
     * @param <T>       Entity type
     * @return Lazy loading proxy associated with a persistence context
     */
    public <T> Object createProxy(T entity, FieldSpecification<? super T, ?> fieldSpec) {
        final Class<?> type = fieldSpec.getJavaType();
        if (List.class.isAssignableFrom(type)) {
            return new LazyLoadingListProxy<>(entity, (ListAttribute<T, ?>) fieldSpec, uow);
        } else if (Set.class.isAssignableFrom(type)) {
            return new LazyLoadingSetProxy<>(entity, (FieldSpecification) fieldSpec, uow);
        } else if (Map.class.isAssignableFrom(type)) {
            return new LazyLoadingMapProxy<>(entity, (FieldSpecification) fieldSpec, uow);
        } else if (uow.getMetamodel().isEntityType(type)) {
            try {
                final Class<?> proxyType = uow.getMetamodel().getLazyLoadingProxy(type);
                final LazyLoadingEntityProxy<?> proxy = (LazyLoadingEntityProxy<?>) proxyType.getDeclaredConstructor().newInstance();
                proxy.setOwner(entity);
                proxy.setPersistenceContext(uow);
                proxy.setFieldSpec(fieldSpec);
                return proxy;
            } catch (NoSuchMethodException | InstantiationException | IllegalAccessException | InvocationTargetException e) {
                throw new OWLPersistenceException("Unable to instantiate lazy loading proxy!", e);
            }
        }
        throw new IllegalArgumentException("Unsupported type for lazy proxying.");
    }
}
