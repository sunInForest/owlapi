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
package org.semanticweb.owlapi.util;

import static org.semanticweb.owlapi.util.CollectionFactory.createMap;
import static org.semanticweb.owlapi.util.OWLAPIPreconditions.checkNotNull;
import static org.semanticweb.owlapi.util.OWLAPIPreconditions.verifyNotNull;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.Nullable;

import org.semanticweb.owlapi.annotations.HasPriority;
import org.semanticweb.owlapi.io.DocumentSources;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLOntologyIRIMapper;
import org.semanticweb.owlapi.vocab.Namespaces;
import org.semanticweb.owlapi.vocab.OWLXMLVocabulary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * A mapper which given a root folder attempts to automatically discover and map files to
 * ontologies. The mapper is capable of mapping ontologies in RDF/XML, OWL/XML, Manchester OWL
 * Syntax, Functional Syntax and OBO (other serialisations are not supported). Zip and jar files
 * containing ontologies are supported, either as main argument to the constructor or as content of
 * the root folder.
 *
 * @author Matthew Horridge, The University Of Manchester, Bio-Health Informatics Group
 * @since 2.0.0
 */
@HasPriority(1)
public class AutoIRIMapper extends DefaultHandler implements OWLOntologyIRIMapper, Serializable {

    private static final String ONTOLOGY_ELEMENT_FOUND_PARSING_COMPLETE =
        "Ontology element found, parsing complete.";
    static final Pattern pattern = Pattern.compile("Ontology\\(<([^>]+)>");
    static final Pattern manPattern = Pattern.compile("Ontology:[\r\n ]*<([^>]+)>");
    private static final Logger LOGGER = LoggerFactory.getLogger(AutoIRIMapper.class);
    private final Set<String> fileExtensions =
        new HashSet<>(Arrays.asList(".owl", ".xml", ".rdf", ".omn", ".ofn"));
    private final boolean recursive;
    private final Map<String, OntologyRootElementHandler> handlerMap = createMap();
    private final Map<IRI, IRI> ontologyIRI2PhysicalURIMap = createMap();
    private final Map<String, IRI> oboFileMap = createMap();
    private final String directoryPath;
    private boolean mapped;
    @Nullable
    private transient File currentFile;

    /**
     * Creates an auto-mapper which examines ontologies that reside in the specified root folder
     * (and possibly sub-folders).
     *
     * @param rootDirectory The root directory which should be searched for ontologies; this can
     *                      also be a zip/jar file containing ontologies. If root is actually a
     *                      folder, zip/jar files included in the folder are parsed for ontologies.
     *                      The zip parsing is delegated to ZipIRIMapper.
     * @param recursive     Sub directories will be searched recursively if {@code true}.
     */
    public AutoIRIMapper(File rootDirectory, boolean recursive) {
        directoryPath =
            checkNotNull(rootDirectory, "rootDirectory cannot be null").getAbsolutePath();
        this.recursive = recursive;
        mapped = false;
        /**
         * A handler to handle RDF/XML files. The xml:base (if present) is taken to be the ontology
         * URI of the ontology document being parsed.
         */
        handlerMap.put(Namespaces.RDF + "RDF", this::baseIRI);
        /**
         * A handler that can handle OWL/XML files as well as RDF/XML with an owl:Ontology element
         * is defined with a non empty rdf:about.
         */
        handlerMap.put(OWLXMLVocabulary.ONTOLOGY.toString(), this::ontologyIRI);
    }

    @Nullable
    protected IRI ontologyIRI(Attributes attributes) {
        String ontURI = attributes.getValue(Namespaces.OWL.toString(), "ontologyIRI");
        if (ontURI == null) {
            ontURI = attributes.getValue("ontologyIRI");
        }
        if (ontURI == null) {
            ontURI = attributes.getValue(Namespaces.RDF.toString(), "about");
        }
        if (ontURI == null) {
            return null;
        }
        return IRI.create(ontURI);
    }

    @Nullable
    protected IRI baseIRI(Attributes attributes) {
        String baseValue = attributes.getValue(Namespaces.XML.toString(), "base");
        if (baseValue == null) {
            return null;
        }
        return IRI.create(baseValue);
    }

    /**
     * @param tok token
     * @return IRI without quotes (&lt; and &gt;)
     */
    static IRI unquote(String tok) {
        String substring = tok.substring(1, tok.length() - 1);
        assert substring != null;
        return IRI.create(substring);
    }

    protected File getDirectory() {
        return new File(directoryPath);
    }

    /**
     * The mapper only examines files that have specified file extensions. This method returns the
     * file extensions that cause a file to be examined.
     *
     * @return A {@code Set} of file extensions.
     */
    public Set<String> getFileExtensions() {
        return new HashSet<>(fileExtensions);
    }

    /**
     * Sets the extensions of files that are to be examined for ontological content. (By default the
     * extensions are {@code owl}, {@code xml} and {@code rdf}). Only files that have the specified
     * extensions will be examined to see if they contain ontologies.
     *
     * @param extensions the set of extensions
     */
    public void setFileExtensions(Collection<String> extensions) {
        fileExtensions.clear();
        fileExtensions.addAll(extensions);
    }

    /**
     * Gets the set of ontology IRIs that this mapper has found.
     *
     * @return A {@code Set} of ontology (logical) IRIs
     */
    public Set<IRI> getOntologyIRIs() {
        if (!mapped) {
            mapFiles();
        }
        return new HashSet<>(ontologyIRI2PhysicalURIMap.keySet());
    }

    /**
     * update the map.
     */
    public void update() {
        mapFiles();
    }

    @Override
    @Nullable
    public IRI getDocumentIRI(IRI ontologyIRI) {
        if (!mapped) {
            mapFiles();
        }
        if (ontologyIRI.toString().endsWith(".obo")) {
            String path = ontologyIRI.toURI().getPath();
            if (path != null) {
                int lastSepIndex = path.lastIndexOf('/');
                String name = path.substring(lastSepIndex + 1, path.length());
                IRI documentIRI = oboFileMap.get(name);
                if (documentIRI != null) {
                    return documentIRI;
                }
            }
        }
        return ontologyIRI2PhysicalURIMap.get(ontologyIRI);
    }

    private void mapFiles() {
        mapped = true;
        ontologyIRI2PhysicalURIMap.clear();
        processFile(getDirectory());
    }

    private void processFile(File f) {
        if (f.isHidden()) {
            return;
        }
        // if pointed directly at a zip file, map it
        parseIfExtensionSupported(f);
        File[] files = f.listFiles();
        if (files == null) {
            return;
        }
        for (File file : files) {
            if (file.isDirectory() && recursive) {
                processFile(file);
            } else {
                parseIfExtensionSupported(file);
            }
        }
    }

    protected void parseIfExtensionSupported(File file) {
        String name = file.getName();
        int lastIndexOf = name.lastIndexOf('.');
        if (lastIndexOf < 0) {
            // no extension for the file, nothing to do
            return;
        }
        String extension = name.substring(lastIndexOf);
        if (".zip".equalsIgnoreCase(extension) || ".jar".equalsIgnoreCase(extension)) {
            try {
                ZipIRIMapper mapper = new ZipIRIMapper(file, "jar:" + file.toURI() + "!/");
                mapper.oboMappings().forEach(e -> oboFileMap.put(e.getKey(), e.getValue()));
                mapper.iriMappings()
                    .forEach(e -> ontologyIRI2PhysicalURIMap.put(e.getKey(), e.getValue()));
            } catch (IOException e) {
                // if we can't parse a file, then we can't map it
                LOGGER.debug("Exception reading file", e);
            }

        } else if (".obo".equalsIgnoreCase(extension)) {
            oboFileMap.put(name, IRI.create(file));
        } else if (".ofn".equalsIgnoreCase(extension)) {
            parseFSSFile(file);
        } else if (".omn".equalsIgnoreCase(extension)) {
            parseManchesterSyntaxFile(file);
        } else if (fileExtensions.contains(extension.toLowerCase())) {
            parseFile(file);
        }
    }

    /**
     * Search first 100 lines for FSS style Ontology(&lt;IRI&gt; ...
     *
     * @param file the file to parse
     */
    private void parseFSSFile(File file) {
        try (InputStream input = new FileInputStream(file);
            Reader reader = new InputStreamReader(input, "UTF-8");
            BufferedReader br = new BufferedReader(reader)) {
            String line = "";
            Matcher m = pattern.matcher(line);
            int n = 0;
            while ((line = br.readLine()) != null && n++ < 100) {
                m.reset(line);
                if (m.matches()) {
                    String group = m.group(1);
                    assert group != null;
                    addMapping(IRI.create(group), file);
                    break;
                }
            }
        } catch (IOException e) {
            // if we can't parse a file, then we can't map it
            LOGGER.debug("Exception reading file", e);
        }
    }

    private void parseFile(File file) {
        try (FileInputStream in = new FileInputStream(file);
            BufferedInputStream delegate = new BufferedInputStream(in);
            InputStream is = DocumentSources.wrap(delegate);) {
            currentFile = file;
            // Using the default expansion limit. If the ontology IRI cannot be
            // found before 64000 entities are expanded, the file is too
            // expensive to parse.
            SAXParsers.initParserWithOWLAPIStandards(null, "64000").parse(is, this);
        } catch (SAXException e) {
            // Exceptions thrown to halt parsing early when the ontology IRI is found
            // should not be logged because they are not actual errors, only a performance hack.
            if (!Objects.equals(ONTOLOGY_ELEMENT_FOUND_PARSING_COMPLETE, e.getMessage())) {
                LOGGER.debug("SAX Exception reading file", e);
            }
        } catch (IOException e) {
            // if we can't parse a file, then we can't map it
            LOGGER.debug("IO Exception reading file", e);
        }
    }

    private void parseManchesterSyntaxFile(File file) {
        try (FileInputStream input = new FileInputStream(file);
            InputStreamReader reader = new InputStreamReader(input, StandardCharsets.UTF_8);
            BufferedReader br = new BufferedReader(reader)) {
            // Ontology: <URI>
            String line = br.readLine();
            while (line != null) {
                if (parseManLine(file, line) != null) {
                    return;
                }
                line = br.readLine();
            }
        } catch (IOException e) {
            // if we can't parse a file, then we can't map it
            LOGGER.debug("Exception reading file", e);
        }
    }

    @Nullable
    private IRI parseManLine(File file, String line) {
        Matcher matcher = manPattern.matcher(line);
        if (matcher.matches()) {
            IRI iri = IRI.create(matcher.group(1));
            addMapping(iri, file);
            return iri;
        }
        return null;
    }

    @Override
    public void startElement(@Nullable String uri, @Nullable String localName,
        @Nullable String qName, @Nullable Attributes attributes) throws SAXException {
        String tag = uri + localName;
        OntologyRootElementHandler handler = handlerMap.get(tag);
        if (handler != null) {
            IRI ontologyIRI = handler.handle(checkNotNull(attributes));
            if (ontologyIRI != null && currentFile != null) {
                addMapping(ontologyIRI, verifyNotNull(currentFile));
            }
        }
        if (tag.equals("http://www.w3.org/2002/07/owl#Ontology")) {
            throw new SAXException(ONTOLOGY_ELEMENT_FOUND_PARSING_COMPLETE);
        }
    }

    /**
     * @param ontologyIRI ontology
     * @param file        file
     */
    protected void addMapping(IRI ontologyIRI, File file) {
        ontologyIRI2PhysicalURIMap.put(ontologyIRI, IRI.create(file));
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("AutoIRIMapper: (");
        sb.append(ontologyIRI2PhysicalURIMap.size()).append(" ontologies)\n");
        ontologyIRI2PhysicalURIMap.forEach((k, v) -> sb.append("    ").append(k.toQuotedString())
            .append(" -> ").append(v).append('\n'));
        return sb.toString();
    }

    /**
     * A simple interface which extracts an ontology IRI from a set of element attributes.
     */
    @FunctionalInterface
    private interface OntologyRootElementHandler extends Serializable {

        /**
         * Gets the ontology IRI.
         *
         * @param attributes The attributes which will be examined for the ontology IRI.
         * @return The ontology IRI or {@code null} if no ontology IRI could be found.
         */
        @Nullable
        IRI handle(Attributes attributes);
    }
}
