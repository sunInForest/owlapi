package org.semanticweb.owlapi.io;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;

import org.junit.Test;
import org.semanticweb.owlapi.formats.RDFXMLDocumentFormatFactory;
import org.semanticweb.owlapi.model.OWLDocumentFormat;
import org.semanticweb.owlapi.model.OWLDocumentFormatFactory;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OntologyConfigurator;

@SuppressWarnings("javadoc")
public class StreamDocumentSourceBaseTestCase {

    @SuppressWarnings("null")
    @Test
    public void shouldCreateRewindableReaderWithKnownContent() {
        String input =
            "<rdf:RDF xmlns:rdf=\"http://www.w3.org/1999/02/22-rdf-syntax-ns#\" xmlns:owl=\"http://www.w3.org/2002/07/owl#\" xmlns:xsd=\"http://www.w3.org/2001/XMLSchema#\" xmlns:rdfs=\"http://www.w3.org/2000/01/rdf-schema#\">\n"
                + "<owl:Ontology/>\n"
                + "    <owl:Class rdf:about=\"http://example.com/Person\">\n        <owl:hasKey rdf:parseType=\"Collection\">\n            <owl:ObjectProperty rdf:about=\"http://example.com/objectPoperty\"/>\n            <owl:DatatypeProperty rdf:about=\"http://example.com/dataProperty\"/>\n        </owl:hasKey>\n    </owl:Class>\n"
                + "    <owl:ObjectProperty rdf:about=\"http://example.com/objectProperty\"/>\n"
                + "</rdf:RDF>";
        StreamDocumentSourceBase source = new StreamDocumentSourceBase(
            new InputStreamReader(new ByteArrayInputStream(input.getBytes()),
                StandardCharsets.UTF_8),
            "urn:test:test", null, null) {};
        StringWriter w = new StringWriter();
        OWLParser mockParser = new OWLParser() {

            @Override
            public OWLDocumentFormat parse(Reader r, OWLParserParameters p) {
                try {
                    char[] buffer = new char[128];
                    int i = r.read(buffer);
                    while (i > -1) {
                        w.write(buffer, 0, i);
                        i = r.read(buffer);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                    fail(e.getMessage());
                }
                return null;
            }

            @Override
            public OWLDocumentFormatFactory getSupportedFormat() {
                return new RDFXMLDocumentFormatFactory();
            }
        };
        source.acceptParser(mockParser, mock(OWLOntology.class), mock(OntologyConfigurator.class));
        assertEquals(input, w.toString());
    }
}
