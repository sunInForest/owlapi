package org.semanticweb.owlapi6.factplusplusad;

import java.util.Iterator;
import java.util.concurrent.atomic.AtomicInteger;

import org.semanticweb.owlapi6.atomicdecomposition.Signature;
import org.semanticweb.owlapi6.model.HasOperands;
import org.semanticweb.owlapi6.model.OWLDataComplementOf;
import org.semanticweb.owlapi6.model.OWLDataHasValue;
import org.semanticweb.owlapi6.model.OWLDataIntersectionOf;
import org.semanticweb.owlapi6.model.OWLDataOneOf;
import org.semanticweb.owlapi6.model.OWLDataUnionOf;
import org.semanticweb.owlapi6.model.OWLDatatype;
import org.semanticweb.owlapi6.model.OWLDatatypeRestriction;
import org.semanticweb.owlapi6.model.OWLEntity;
import org.semanticweb.owlapi6.model.OWLLiteral;
import org.semanticweb.owlapi6.model.OWLObject;
import org.semanticweb.owlapi6.model.OWLObjectComplementOf;
import org.semanticweb.owlapi6.model.OWLObjectHasSelf;
import org.semanticweb.owlapi6.model.OWLObjectHasValue;
import org.semanticweb.owlapi6.model.OWLObjectIntersectionOf;
import org.semanticweb.owlapi6.model.OWLObjectInverseOf;
import org.semanticweb.owlapi6.model.OWLObjectOneOf;
import org.semanticweb.owlapi6.model.OWLObjectPropertyExpression;
import org.semanticweb.owlapi6.model.OWLObjectUnionOf;
import org.semanticweb.owlapi6.model.OWLPropertyExpression;
import org.semanticweb.owlapi6.model.OWLPropertyRange;
import org.semanticweb.owlapi6.model.OWLSubPropertyChainOfAxiom;

/**
 * Determine how many instances can a complement of expression have. All methods return minimal n
 * such that {@code expr\in CC^{<= n}, n >= 0}
 */
class UpperBoundComplementEvaluator extends CardinalityEvaluatorBase {

    /**
     * init c'tor
     *
     * @param s signature
     */
    UpperBoundComplementEvaluator(Signature s) {
        super(s);
    }

    /**
     * helper for entities TODO: checks only C top-locality, not R
     */
    @Override
    int getEntityValue(OWLEntity entity) {
        if (entity.isTopEntity()) {
            return anyUpperValue;
        }
        if (entity.isBottomEntity()) {
            return noUpperValue;
        }
        return getAllNoneUpper(topCLocal() && nc(entity));
    }

    /**
     * helper for All
     */
    @Override
    int getForallValue(OWLPropertyExpression r, OWLPropertyRange c) {
        return getAllNoneUpper(isBotEquivalent(r) || isUpperLE(getUpperBoundComplement(c), 0));
    }

    @Override
    int getMinValue(int m, OWLPropertyExpression r, OWLPropertyRange c) {
        // m == 0 or...
        if (m == 0) {
            return anyUpperValue;
        }
        // R = \top and...
        if (!isTopEquivalent(r)) {
            return noUpperValue;
        }
        /** {@code C \in C^{>= m}} */
        return getAllNoneUpper(isLowerGE(getLowerBoundDirect(c), m));
    }

    @Override
    int getMaxValue(int m, OWLPropertyExpression r, OWLPropertyRange c) {
        // R = \bot or...
        if (isBotEquivalent(r)) {
            return anyUpperValue;
        }
        /** {@code C\in C^{<= m}} */
        return getAllNoneUpper(isUpperLE(getUpperBoundDirect(c), m));
    }

    /**
     * helper for things like = m R.C
     */
    @Override
    int getExactValue(int m, OWLPropertyExpression r, OWLPropertyRange c) {
        // here the minimal value between Mix and Max is an answer. The -1 case
        // will be dealt with automagically
        return Math.min(getMinValue(m, r, c), getMaxValue(m, r, c));
    }

    <C extends OWLObject> int getAndValue(HasOperands<C> expr) {
        int sum = 0;
        Iterator<C> it = expr.operands().iterator();
        while (it.hasNext()) {
            C p = it.next();
            int n = getUpperBoundComplement(p);
            if (n == noUpperValue) {
                return noUpperValue;
            }
            sum += n;
        }
        return sum;
    }

    <C extends OWLObject> int getOrValue(HasOperands<C> expr) {
        // noUpperValue is a maximal element
        AtomicInteger min = new AtomicInteger(noUpperValue);
        // we are looking for the minimal value here, use an appropriate helper
        expr.operands().forEach(p -> min.set(minUpperValue(min.get(), getUpperBoundDirect(p))));
        return min.get();
    }

    // concept expressions
    @Override
    public void visit(OWLObjectComplementOf expr) {
        value = getUpperBoundDirect(expr.getOperand());
    }

    @Override
    public void visit(OWLObjectIntersectionOf expr) {
        value = getAndValue(expr);
    }

    @Override
    public void visit(OWLObjectUnionOf expr) {
        value = getOrValue(expr);
    }

    @Override
    public void visit(OWLObjectOneOf o) {
        value = noUpperValue;
    }

    @Override
    public void visit(OWLObjectHasSelf expr) {
        value = getAllNoneUpper(isTopEquivalent(expr.getProperty()));
    }

    @Override
    public void visit(OWLObjectHasValue expr) {
        value = getAllNoneUpper(isTopEquivalent(expr.getProperty()));
    }

    @Override
    public void visit(OWLDataHasValue expr) {
        value = getAllNoneUpper(isTopEquivalent(expr.getProperty()));
    }

    // object role expressions
    @Override
    public void visit(OWLObjectInverseOf expr) {
        value = getUpperBoundComplement(expr.getInverseProperty());
    }

    @Override
    public void visit(OWLSubPropertyChainOfAxiom expr) {
        for (OWLObjectPropertyExpression p : expr.getPropertyChain()) {
            if (!isTopEquivalent(p)) {
                value = noUpperValue;
                return;
            }
        }
        value = anyUpperValue;
    }

    // negated datatype is a union of all other DTs that are infinite
    @Override
    public void visit(OWLDatatype o) {
        value = noUpperValue;
    }

    // negated restriction include negated DT
    @Override
    public void visit(OWLDatatypeRestriction o) {
        value = noUpperValue;
    }

    @Override
    public void visit(OWLLiteral o) {
        value = noUpperValue;
    }

    @Override
    public void visit(OWLDataComplementOf expr) {
        value = getUpperBoundDirect(expr.getDataRange());
    }

    @Override
    public void visit(OWLDataIntersectionOf expr) {
        value = getAndValue(expr);
    }

    @Override
    public void visit(OWLDataUnionOf expr) {
        value = getOrValue(expr);
    }

    @Override
    public void visit(OWLDataOneOf o) {
        value = noUpperValue;
    }
}
