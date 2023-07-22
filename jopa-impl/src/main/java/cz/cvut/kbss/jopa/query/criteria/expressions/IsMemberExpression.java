package cz.cvut.kbss.jopa.query.criteria.expressions;

import cz.cvut.kbss.jopa.model.query.criteria.Expression;
import cz.cvut.kbss.jopa.model.query.criteria.Predicate;
import cz.cvut.kbss.jopa.query.criteria.AbstractPredicate;
import cz.cvut.kbss.jopa.query.criteria.CriteriaParameterFiller;
import cz.cvut.kbss.jopa.query.soql.SoqlConstants;
import cz.cvut.kbss.jopa.sessions.CriteriaBuilder;

import java.util.Collection;
import java.util.List;

public class IsMemberExpression<Y> extends AbstractPredicate {

    private final Expression<? extends Collection<Y>> collectionExpression;
    private final Y value;

    public IsMemberExpression(Y value, Expression<? extends Collection<Y>> collectionExpression, CriteriaBuilder cb) {
        super(BooleanOperator.AND, cb);
        this.value = value;
        this.collectionExpression = collectionExpression;
    }

    @Override
    public Predicate not() {
        super.negate();
        return this;
    }

    @Override
    public List<Expression<Boolean>> getExpressions() {
        return List.of(this);
    }

    @Override
    public void setExpressionToQuery(StringBuilder query, CriteriaParameterFiller parameterFiller) {
        final AbstractExpression<?> param = (AbstractExpression<?>) cb.literal(value);
        param.setExpressionToQuery(query, parameterFiller);
        if (negated) {
            query.append(' ').append(SoqlConstants.NOT);
        }
        query.append(' ').append(SoqlConstants.MEMBER_OF).append(' ');
        ((AbstractExpression<?>) collectionExpression).setExpressionToQuery(query, parameterFiller);
    }
}