/* This file is part of the OWL API.
 * The contents of this file are subject to the LGPL License, Version 3.0.
 * Copyright 2014, The University of Manchester
 * 
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License along with this program.  If not, see http://www.gnu.org/licenses/.
 *
 * Alternatively, the contents of this file may be used under the terms of the Apache License, Version 2.0 in which case, the provisions of the Apache License Version 2.0 are applicable instead of those above.
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License. */
package org.semanticweb.owlapi6.api.test.ontology;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.semanticweb.owlapi6.apibinding.OWLFunctionalSyntaxFactory.Class;
import static org.semanticweb.owlapi6.apibinding.OWLFunctionalSyntaxFactory.SubClassOf;

import java.util.HashSet;
import java.util.Set;

import org.junit.Test;
import org.semanticweb.owlapi6.api.test.baseclasses.TestBase;
import org.semanticweb.owlapi6.model.OWLAxiom;
import org.semanticweb.owlapi6.model.OWLClass;
import org.semanticweb.owlapi6.model.OWLOntology;
import org.semanticweb.owlapi6.model.OWLOntologyChange;
import org.semanticweb.owlapi6.model.OWLSubClassOfAxiom;

/**
 * @author Matthew Horridge, The University of Manchester, Bio-Health Informatics Group
 * @since 3.1.0
 */
public class OntologyChangeListenerTestCase extends TestBase {

    @Test
    public void testOntologyChangeListener() {
        OWLOntology ont = getOWLOntology();
        OWLClass clsA = Class(iri("ClsA"));
        OWLClass clsB = Class(iri("ClsB"));
        OWLSubClassOfAxiom ax = SubClassOf(clsA, clsB);
        final Set<OWLAxiom> impendingAdditions = new HashSet<>();
        final Set<OWLAxiom> impendingRemovals = new HashSet<>();
        final Set<OWLAxiom> additions = new HashSet<>();
        final Set<OWLAxiom> removals = new HashSet<>();
        ont.getOWLOntologyManager().addImpendingOntologyChangeListener(impendingChanges -> {
            for (OWLOntologyChange change : impendingChanges) {
                if (change.isAddAxiom()) {
                    impendingAdditions.add(change.getAxiom());
                } else if (change.isRemoveAxiom()) {
                    impendingRemovals.add(change.getAxiom());
                }
            }
        });
        ont.getOWLOntologyManager().addOntologyChangeListener(changes -> {
            for (OWLOntologyChange change : changes) {
                if (change.isAddAxiom()) {
                    additions.add(change.getAxiom());
                } else if (change.isRemoveAxiom()) {
                    removals.add(change.getAxiom());
                }
            }
        });
        ont.addAxiom(ax);
        assertTrue(additions.contains(ax));
        assertTrue(impendingAdditions.contains(ax));
        ont.remove(ax);
        assertTrue(removals.contains(ax));
        assertTrue(impendingRemovals.contains(ax));
        // test that no op changes are not broadcasted
        removals.clear();
        ont.remove(ax);
        assertFalse(removals.contains(ax));
        assertTrue(impendingRemovals.contains(ax));
    }
}