/*
 * The Apache Software License, Version 1.1
 *
 *
 * Copyright (c) 2001 The Apache Software Foundation.  All rights
 * reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in
 *    the documentation and/or other materials provided with the
 *    distribution.
 *
 * 3. The end-user documentation included with the redistribution,
 *    if any, must include the following acknowledgment:
 *       "This product includes software developed by the
 *        Apache Software Foundation (http://www.apache.org/)."
 *    Alternately, this acknowledgment may appear in the software itself,
 *    if and wherever such third-party acknowledgments normally appear.
 *
 * 4. The names "Axis" and "Apache Software Foundation" must
 *    not be used to endorse or promote products derived from this
 *    software without prior written permission. For written
 *    permission, please contact apache@apache.org.
 *
 * 5. Products derived from this software may not be called "Apache",
 *    nor may "Apache" appear in their name, without prior written
 *    permission of the Apache Software Foundation.
 *
 * THIS SOFTWARE IS PROVIDED ``AS IS'' AND ANY EXPRESSED OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED.  IN NO EVENT SHALL THE APACHE SOFTWARE FOUNDATION OR
 * ITS CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF
 * USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT
 * OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF
 * SUCH DAMAGE.
 * ====================================================================
 *
 * This software consists of voluntary contributions made by many
 * individuals on behalf of the Apache Software Foundation.  For more
 * information on the Apache Software Foundation, please see
 * <http://www.apache.org/>.
 */

package org.apache.axis.wsdl.symbolTable;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

import java.net.MalformedURLException;
import java.net.URL;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Vector;

import javax.wsdl.Binding;
import javax.wsdl.BindingFault;
import javax.wsdl.BindingOperation;
import javax.wsdl.Definition;
import javax.wsdl.Fault;
import javax.wsdl.Import;
import javax.wsdl.Input;
import javax.wsdl.Message;
import javax.wsdl.Operation;
import javax.wsdl.Output;
import javax.wsdl.Part;
import javax.wsdl.Port;
import javax.wsdl.PortType;
import javax.xml.namespace.QName;
import javax.wsdl.Service;
import javax.wsdl.WSDLException;

import javax.wsdl.factory.WSDLFactory;

import javax.wsdl.xml.WSDLReader;

import javax.wsdl.extensions.http.HTTPBinding;
import javax.wsdl.extensions.soap.SOAPBinding;
import javax.wsdl.extensions.soap.SOAPBody;

import javax.xml.rpc.holders.BooleanHolder;
import javax.xml.rpc.holders.IntHolder;

import org.apache.axis.Constants;

import org.apache.axis.utils.JavaUtils;
import org.apache.axis.utils.XMLUtils;

import org.apache.axis.wsdl.gen.Generator;

import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
* This class represents a table of all of the top-level symbols from a set of WSDL Definitions and
* DOM Documents:  XML types; WSDL messages, portTypes, bindings, and services.
*
* This symbolTable contains entries of the form <key, value> where key is of type QName and value is
* of type Vector.  The Vector's elements are all of the objects that have the given QName.  This is
* necessary since names aren't unique among the WSDL types.  message, portType, binding, service,
* could all have the same QName and are differentiated merely by type.  SymbolTable contains
* type-specific getters to bypass the Vector layer:
*   public PortTypeEntry getPortTypeEntry(QName name), etc.
*/
public class SymbolTable {
    // Should the contents of imported files be added to the symbol table?
    private boolean addImports;

    // The actual symbol table.  This symbolTable contains entries of the form
    // <key, value> where key is of type QName and value is of type Vector.  The
    // Vector's elements are all of the objects that have the given QName.  This
    // is necessary since names aren't unique among the WSDL types.  message,
    // portType, binding, service, could all have the same QName and are
    // differentiated merely by type.  SymbolTable contains type-specific
    // getters to bypass the Vector layer:
    // public PortTypeEntry getPortTypeEntry(QName name), etc.

    private HashMap symbolTable = new HashMap();

    // A list of the TypeEntry elements in the symbol table
    private Vector types = new Vector();

    private boolean verbose;

    private boolean debug = false;

    private BaseTypeMapping btm = null;

    // should we attempt to treat document/literal WSDL as "rpc-style"
    private boolean nowrap;
    // Did we encounter wraped mode WSDL
    private boolean wrapped = false;

    public static final String ANON_TOKEN = ">";

    private Definition def = null;
    private Document   doc = null;
    private String     wsdlURI = null;

    /**
     * Construct a symbol table with the given Namespaces.
     */
    public SymbolTable(BaseTypeMapping btm, boolean addImports,
            boolean verbose, boolean debug, boolean nowrap) {
        this.btm = btm;
        this.addImports = addImports;
        this.verbose = verbose;
        this.debug = debug;
        this.nowrap = nowrap;
    } // ctor

    /**
     * Get the raw symbol table HashMap.
     */
    public HashMap getHashMap() {
        return symbolTable;
    } // getHashMap

    /**
     * Get the list of entries with the given QName.  Since symbols can share QNames, this list is
     * necessary.  This list will not contain any more than one element of any given SymTabEntry.
     */
    public Vector getSymbols(QName qname) {
        return (Vector) symbolTable.get(qname);
    } // get

    /**
     * Get the entry with the given QName of the given class.  If it does not exist, return null.
     */
    public SymTabEntry get(QName qname, Class cls) {
        Vector v = (Vector) symbolTable.get(qname);
        if (v == null) {
            return null;
        }
        else {
            for (int i = 0; i < v.size(); ++i) {
                SymTabEntry entry = (SymTabEntry) v.elementAt(i);
                if (cls.isInstance(entry)) {
                    return entry;
                }
            }
            return null;
        }
    } // get
    

    /**
     * Get the type entry for the given qname.
     * @param qname
     * @param wantElementType boolean that indicates type or element (for type= or ref=)
     */
    public TypeEntry getTypeEntry(QName qname, boolean wantElementType) {
        if (wantElementType) {
            return getElement(qname);
        } else
            return getType(qname);
    } // getTypeEntry

    /**
     * Get the Type TypeEntry with the given QName.  If it doesn't exist, return null.
     */
    public Type getType(QName qname) {
        for (int i = 0; i < types.size(); ++i) {
            TypeEntry type = (TypeEntry) types.get(i);
            if (type.getQName().equals(qname)
                    && (type instanceof Type)) {
                return (Type) type;
            }
        }
        return null;
    } // getType

    /**
     * Get the Element TypeEntry with the given QName.  If it doesn't exist, return null.
     */
    public Element getElement(QName qname) {
        for (int i = 0; i < types.size(); ++i) {
            TypeEntry type = (TypeEntry) types.get(i);
            if (type.getQName().equals(qname) && type instanceof Element) {
                return (Element) type;
            }
        }
        return null;
    } // getElement

    /**
     * Get the MessageEntry with the given QName.  If it doesn't exist, return null.
     */
    public MessageEntry getMessageEntry(QName qname) {
        return (MessageEntry) get(qname, MessageEntry.class);
    } // getMessageEntry

    /**
     * Get the PortTypeEntry with the given QName.  If it doesn't exist, return null.
     */
    public PortTypeEntry getPortTypeEntry(QName qname) {
        return (PortTypeEntry) get(qname, PortTypeEntry.class);
    } // getPortTypeEntry

    /**
     * Get the BindingEntry with the given QName.  If it doesn't exist, return null.
     */
    public BindingEntry getBindingEntry(QName qname) {
        return (BindingEntry) get(qname, BindingEntry.class);
    } // getBindingEntry

    /**
     * Get the ServiceEntry with the given QName.  If it doesn't exist, return null.
     */
    public ServiceEntry getServiceEntry(QName qname) {
        return (ServiceEntry) get(qname, ServiceEntry.class);
    } // getServiceEntry

    /**
     * Get the list of all the XML schema types in the symbol table.  In other words, all entries
     * that are instances of TypeEntry.
     */
    public Vector getTypes() {
        return types;
    } // getTypes

    /**
     * Get the Definition.  The definition is null until
     * populate is called.
     */
    public Definition getDefinition() {
        return def;
    } // getDefinition

    /**
     * Get the WSDL URI.  The WSDL URI is null until populate
     * is called, and ONLY if a WSDL URI is provided.
     * 
     */
    public String getWSDLURI() {
        return wsdlURI;
    } // getWSDLURI

    /**
     * Are we wrapping literal soap body elements.
     */ 
    public boolean isWrapped() {
        return wrapped;
    }

    /**
     * Turn on/off element wrapping for literal soap body's.
     */ 
    public void setWrapped(boolean wrapped) {
        this.wrapped = wrapped;
    }

    /**
     * Dump the contents of the symbol table.  For debugging purposes only.
     */
    public void dump(java.io.PrintStream out) {
        out.println();
        out.println(JavaUtils.getMessage("symbolTable00"));
        out.println("-----------------------");
        Iterator it = symbolTable.values().iterator();
        while (it.hasNext()) {
            Vector v = (Vector) it.next();
            for (int i = 0; i < v.size(); ++i) {
                out.println(
                        v.elementAt(i).getClass().getName());
                out.println(v.elementAt(i));
            }
        }
        out.println("-----------------------");
    } // dump


    /**
     * Call this method if you have a uri for the WSDL document
     * @param uri wsdlURI the location of the WSDL file.
     */

    public void populate(String uri) throws IOException, WSDLException {
        populate(uri, null, null);
    } // populate

    public void populate(String uri, String username, String password) throws IOException, WSDLException {
        if (verbose)
            System.out.println(JavaUtils.getMessage("parsing00", uri));

        Document doc = XMLUtils.newDocument(uri, username, password);
        if (doc == null) {
            throw new IOException(JavaUtils.getMessage("cantGetDoc00", uri));
        }
        this.wsdlURI = uri;
        populate(uri, doc);
    } // populate

    /**
     * Call this method if your WSDL document has already been parsed as an XML DOM document.
     * @param context context This is directory context for the Document.  If the Document were from file "/x/y/z.wsdl" then the context could be "/x/y" (even "/x/y/z.wsdl" would work).  If context is null, then the context becomes the current directory.
     * @param doc doc This is the XML Document containing the WSDL.
     */
    public void populate(String context, Document doc) throws IOException, WSDLException {
        WSDLReader reader = WSDLFactory.newInstance().newWSDLReader();
        reader.setFeature("javax.wsdl.verbose", verbose);
        this.def = reader.readWSDL(context, doc);
        this.doc = doc;

        add(context, def, doc);
    } // populate

    /**
     * Add the given Definition and Document information to the symbol table (including imported
     * symbols), populating it with SymTabEntries for each of the top-level symbols.  When the
     * symbol table has been populated, iterate through it, setting the isReferenced flag
     * appropriately for each entry.
     */
    private void add(String context, Definition def, Document doc)
            throws IOException {
        URL contextURL = context == null ? null : getURL(null, context);
        populate(contextURL, def, doc, null);
        checkForUndefined();
        populateParameters();
        setReferences(def, doc);  // uses wrapped flag set in populateParameters
    } // add

    /**
     * Scan the Definition for undefined objects and throw an error.
     */ 
    private void checkForUndefined(Definition def, String filename) throws IOException {
        if (def != null) {
            // Bindings
            Iterator ib = def.getBindings().values().iterator();
            while (ib.hasNext()) {
                Binding binding = (Binding) ib.next();
                if (binding.isUndefined()) {
                    if (filename == null) {
                        throw new IOException(
                            JavaUtils.getMessage("emitFailtUndefinedBinding01",
                                    binding.getQName().getLocalPart()));
                    }
                    else {
                        throw new IOException(
                            JavaUtils.getMessage("emitFailtUndefinedBinding02",
                                    binding.getQName().getLocalPart(), filename));
                    }
                }
            }

            // portTypes
            Iterator ip = def.getPortTypes().values().iterator();
            while (ip.hasNext()) {
                PortType portType = (PortType) ip.next();
                if (portType.isUndefined()) {
                    if (filename == null) {
                        throw new IOException(
                            JavaUtils.getMessage("emitFailtUndefinedPort01",
                                    portType.getQName().getLocalPart()));
                    }
                    else {
                        throw new IOException(
                            JavaUtils.getMessage("emitFailtUndefinedPort02",
                                    portType.getQName().getLocalPart(), filename));
                    }
                }
            }
            
/* tomj: This is a bad idea, faults seem to be undefined
// RJB reply:  this MUST be done for those systems that do something with
// messages.  Perhaps we have to do an extra step for faults?  I'll leave
// this commented for now, until someone uses this generator for something
// other than WSDL2Java.
            // Messages
            Iterator i = def.getMessages().values().iterator();
            while (i.hasNext()) {
                Message message = (Message) i.next();
                if (message.isUndefined()) {
                    throw new IOException(
                            JavaUtils.getMessage("emitFailtUndefinedMessage01",
                                    message.getQName().getLocalPart()));
                }
            }
*/
        }
    }

    /**
     * Scan the symbol table for undefined types and throw an exception.
     */
    private void checkForUndefined() throws IOException {
        Iterator it = symbolTable.values().iterator();
        while (it.hasNext()) {
            Vector v = (Vector) it.next();
            for (int i = 0; i < v.size(); ++i) {
                SymTabEntry entry = (SymTabEntry) v.get(i);

                // Check for a undefined XSD Schema Type and throw
                // an unsupported message instead of undefined
                if (entry instanceof UndefinedType && 
                    SchemaUtils.isSimpleSchemaType(entry.getQName())) {
                    throw new IOException(
                            JavaUtils.getMessage("unsupportedSchemaType00",
                                              entry.getQName().getLocalPart()));
                }
                if (entry instanceof Undefined) {
                    throw new IOException(
                            JavaUtils.getMessage("undefined00",
                                                 entry.getQName().toString()));
                }
            }
        }
    } // checkForUndefined

    /**
     * Add the given Definition and Document information to the symbol table (including imported
     * symbols), populating it with SymTabEntries for each of the top-level symbols.
     * NOTE:  filename is used only by checkForUndefined so that it can report which WSDL file
     * has the problem.  If we're on the primary WSDL file, then we don't know the name and
     * filename will be null.  But we know the names of all imported files.
     */
    private HashSet importedFiles = new HashSet();
    private void populate(URL context, Definition def, Document doc,
            String filename) throws IOException {
        if (doc != null) {
            populateTypes(context, doc);

            if (addImports) {
                // Add the symbols from any xsd:import'ed documents.
                lookForImports(context, doc);
            }
        }
        if (def != null) {
            checkForUndefined(def, filename);
            if (addImports) {
                // Add the symbols from the wsdl:import'ed WSDL documents
                Map imports = def.getImports();
                Object[] importKeys = imports.keySet().toArray();
                for (int i = 0; i < importKeys.length; ++i) {
                    Vector v = (Vector) imports.get(importKeys[i]);
                    for (int j = 0; j < v.size(); ++j) {
                        Import imp = (Import) v.get(j);
                        if (!importedFiles.contains(imp.getLocationURI())) {
                            importedFiles.add(imp.getLocationURI());
                            URL url = getURL(context, imp.getLocationURI());
                            populate(url, imp.getDefinition(),
                                    XMLUtils.newDocument(url.toString()),
                                    url.toString());
                        }
                    }
                }
            }
            populateMessages(def);
            populatePortTypes(def);
            populateBindings(def);
            populateServices(def);
        }
    } // populate

    /**
     * This is essentially a call to "new URL(contextURL, spec)" with extra handling in case spec is
     * a file.
     */
    private static URL getURL(URL contextURL, String spec) throws IOException {
        // First, fix the slashes as windows filenames may have backslashes
        // in them, but the URL class wont do the right thing when we later
        // process this URL as the contextURL.
        String path = spec.replace('\\', '/');
        
        // See if we have a good URL.
        URL url = null;
        try {
            // first, try to treat spec as a full URL
            url = new URL(contextURL, path);
            
            // if we are deail with files in both cases, create a url
            // by using the directory of the context URL.
            if (contextURL != null && 
                    url.getProtocol().equals("file") &&
                    contextURL.getProtocol().equals("file")) {
                url = getFileURL(contextURL, path);
            }
        }
        catch (MalformedURLException me)
        {
            // try treating is as a file pathname
            url = getFileURL(contextURL, path);
        }

        // Everything is OK with this URL, although a file url constructed
        // above may not exist.  This will be caught later when the URL is
        // accessed.
        return url;
    } // getURL

    private static URL getFileURL(URL contextURL, String path)
            throws IOException {
        if (contextURL != null) {
            // get the parent directory of the contextURL, and append
            // the spec string to the end.
            String contextFileName = contextURL.getFile();
            String parentName = new File(contextFileName).getParent();
            if (parentName != null) {
                return new URL("file", "",  parentName + "/" + path);
            } 
        }
        return new URL("file", "", path);
    } // getFileURL

    /**
     * Recursively find all xsd:import'ed objects and call populate for each one.
     */
    private void lookForImports(URL context, Node node) throws IOException {
        NodeList children = node.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if ("import".equals(child.getLocalName())) {
                NamedNodeMap attributes = child.getAttributes();
                Node namespace = attributes.getNamedItem("namespace");
                // skip XSD import of soap encoding
                if (namespace != null && 
                        Constants.isSOAP_ENC(namespace.getNodeValue())) {
                    continue;
                }
                Node importFile = attributes.getNamedItem("schemaLocation");
                if (importFile != null) {
                    URL url = getURL(context,
                            importFile.getNodeValue());
                    if (!importedFiles.contains(url)) {
                        importedFiles.add(url);
                        String filename = url.toString();
                        populate(context, null,
                                XMLUtils.newDocument(filename), filename);
                    }
                }
            }
            lookForImports(context, child);
        }
    } // lookForImports

    /**
     * Populate the symbol table with all of the Types from the Document.
     */
    private void populateTypes(URL context, Document doc) throws IOException {
        addTypes(context, doc, ABOVE_SCHEMA_LEVEL);
    } // populateTypes

    /**
     * Utility method which walks the Document and creates Type objects for
     * each complexType, simpleType, or element referenced or defined.
     *
     * What goes into the symbol table?  In general, only the top-level types (ie., those just below
     * the schema tag).  But base types and references can appear below the top level.  So anything
     * at the top level is added to the symbol table, plus non-Element types (ie, base and refd)
     * that appear deep within other types.
     */
    private static final int ABOVE_SCHEMA_LEVEL = -1;
    private static final int SCHEMA_LEVEL = 0;
    private void addTypes(URL context, Node node, int level) throws IOException {
        if (node == null) {
            return;
        }
        // Get the kind of node (complexType, wsdl:part, etc.)
        QName nodeKind = Utils.getNodeQName(node);

        if (nodeKind != null) {
            String localPart = nodeKind.getLocalPart();
            boolean isXSD = Constants.isSchemaXSD(nodeKind.getNamespaceURI());
            if ((isXSD && localPart.equals("complexType") ||
                 localPart.equals("simpleType"))) {

                // If an extension or restriction is present,
                // create a type for the reference
                Node re = SchemaUtils.getRestrictionOrExtensionNode(node);
                if (re != null  &&
                    Utils.getAttribute(re, "base") != null) {
                    createTypeFromRef(re);
                }

                // This is a definition of a complex type.
                // Create a Type.
                createTypeFromDef(node, false, false);
            }
            else if (isXSD && localPart.equals("element")) {
                // If the element has a type/ref attribute, create
                // a Type representing the referenced type.
                if (Utils.getNodeTypeRefQName(node, "type") != null ||
                    Utils.getNodeTypeRefQName(node, "ref") != null) {
                    createTypeFromRef(node);
                }

                // If an extension or restriction is present,
                // create a type for the reference
                Node re = SchemaUtils.getRestrictionOrExtensionNode(node);
                if (re != null  &&
                    Utils.getAttribute(re, "base") != null) {
                    createTypeFromRef(re);
                }

                // Create a type representing an element.  (This may
                // seem like overkill, but is necessary to support ref=
                // and element=.
                createTypeFromDef(node, true, level > SCHEMA_LEVEL);
            }
            else if (isXSD && localPart.equals("attribute")) {
                // If the attribute has a type/ref attribute, create
                // a Type representing the referenced type.
                if (Utils.getNodeTypeRefQName(node, "type") != null) {
                    createTypeFromRef(node);
                }

                // Get the symbol table entry and make sure it is a simple 
                // type
                QName refQName = Utils.getNodeTypeRefQName(node, "type");
                if (refQName != null) {
                    TypeEntry refType = getTypeEntry(refQName, false);
                    if (refType != null &&
                        refType instanceof Undefined) {
                        // Don't know what the type is.
                        // It better be simple so set it as simple
                        refType.setSimpleType(true);
                    } else if (refType == null ||
                               (!(refType instanceof BaseType) &&
                                !refType.isSimpleType())) {
                        // Problem if not simple
                        throw new IOException(
                                              JavaUtils.getMessage("AttrNotSimpleType01",
                                                                   refQName.toString()));
                    }
                }
            }
            else if (isXSD && localPart.equals("any")) {
                // Map xsd:any element to any xsd:any so that
                // it gets serialized using the ElementSerializer.
                QName anyQName = Constants.XSD_ANY;
                if (getType(anyQName) == null) {
                    symbolTablePut(new BaseType(anyQName));
                }
            }
            else if (localPart.equals("part") &&
                     Constants.isWSDL(nodeKind.getNamespaceURI())) {
                
                // This is a wsdl part.  Create an TypeEntry representing the reference
                createTypeFromRef(node);
            }
            else if (isXSD && localPart.equals("include")) {
                String includeName = Utils.getAttribute(node, "schemaLocation");
                if (includeName != null) {
                    URL url = getURL(context, includeName);
                    Document includeDoc = XMLUtils.newDocument(url.toString());
                    populate(url, (Definition) null, includeDoc, url.toString());
                }
            }
        }

        if (level == ABOVE_SCHEMA_LEVEL) {
            if (nodeKind != null && nodeKind.getLocalPart().equals("schema")) {
                level = SCHEMA_LEVEL;
            }
        }
        else {
            ++level;
        }

        // Recurse through children nodes
        NodeList children = node.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            addTypes(context, children.item(i), level);
        }
    } // addTypes

    /**
     * Create a TypeEntry from the indicated node, which defines a type
     * that represents a complexType, simpleType or element (for ref=).
     */
    private void createTypeFromDef(Node node, boolean isElement,
            boolean belowSchemaLevel) throws IOException {
        // Get the QName of the node's name attribute value
        QName qName = Utils.getNodeNameQName(node);
        if (qName != null) {

            // If the qname is already registered as a base type,
            // don't create a defining type/element.
            if (!isElement && btm.getBaseName(qName)!=null) {
                return;
            }

            // If the node has a type or ref attribute, get the 
            // qname representing the type
            BooleanHolder forElement = new BooleanHolder();
            QName refQName = Utils.getNodeTypeRefQName(node, forElement);

            if (refQName != null) {
                // Now get the TypeEntry
                TypeEntry refType = getTypeEntry(refQName, forElement.value);

                if (!belowSchemaLevel) {
                    symbolTablePut(new DefinedElement(qName, refType, node, ""));
                }
            }   
            else {
                // Flow to here indicates no type= or ref= attribute.
                
                // See if this is an array or simple type definition.
                IntHolder numDims = new IntHolder();
                numDims.value = 0;
                QName arrayEQName = SchemaUtils.getArrayElementQName(node, numDims);

                if (arrayEQName != null) {
                    // Get the TypeEntry for the array element type
                    refQName = arrayEQName;
                    TypeEntry refType = getTypeEntry(refQName, false);
                    if (refType == null) {
                        // Not defined yet, add one
                        String baseName = btm.getBaseName(refQName);
                        if (baseName != null)
                            refType = new BaseType(refQName);
                        else
                            refType = new UndefinedType(refQName);
                        symbolTablePut(refType);
                    }

                    // Create a defined type or element that references refType
                    String dims = "";
                    while (numDims.value > 0) {
                        dims += "[]";
                        numDims.value--;
                    }

                    TypeEntry defType = null;
                    if (isElement) {
                        if (!belowSchemaLevel) { 
                            defType = new DefinedElement(qName, refType, node, dims);
                        }
                    } else {
                        defType = new DefinedType(qName, refType, node, dims);
                    }
                    if (defType != null) {
                        symbolTablePut(defType);
                    }
                }
                else {

                    // Create a TypeEntry representing this  type/element
                    String baseName = btm.getBaseName(qName);
                    if (baseName != null) {
                        symbolTablePut(new BaseType(qName));
                    }
                    else {

                        // Create a type entry, set whether it should
                        // be mapped as a simple type, and put it in the 
                        // symbol table.
                        TypeEntry te = null;
                        if (!isElement) {
                            te = new DefinedType(qName, node);
                            
                            // check if we are an anonymous type underneath
                            // an element.  If so, we point the refType of the
                            // element to us (the real type).
                            if (qName.getLocalPart().indexOf(ANON_TOKEN) >= 0 ) {
                                Node parent = node.getParentNode();
                                QName parentQName = Utils.getNodeNameQName(parent);
                                TypeEntry parentType = getElement(parentQName);
                                if (parentType != null) {
                                    parentType.setRefType(te);
                                }
                            }
                            
                        } else {
                            if (!belowSchemaLevel) {
                                te = new DefinedElement(qName, node);
                            }
                        }
                        if (te != null) {
                            if (SchemaUtils.isSimpleTypeOrSimpleContent(node)) {
                                te.setSimpleType(true);
                            }
                            symbolTablePut(te);
                        }
                    }
                }
            }
        }
    } // createTypeFromDef
    
    /**
     * Node may contain a reference (via type=, ref=, or element= attributes) to 
     * another type.  Create a Type object representing this referenced type.
     */
    private void createTypeFromRef(Node node) throws IOException {
        // Get the QName of the node's type attribute value
        BooleanHolder forElement = new BooleanHolder();
        QName qName = Utils.getNodeTypeRefQName(node, forElement);
        if (qName != null) {
            
            // Get Type or Element depending on whether type attr was used.
            TypeEntry type = getTypeEntry(qName, forElement.value);
            
            // A symbol table entry is created if the TypeEntry is not found    
            if (type == null) {
                // See if this is a special QName for collections
                if (qName.getLocalPart().indexOf("[") > 0) {
                    // Get the TypeEntry for the collection element
                    QName typeAttr = Utils.getNodeTypeRefQName(node, "type");
                    TypeEntry collEl = getTypeEntry(typeAttr, false);
                    if (collEl == null) {
                        // Collection Element Type not defined yet, add one.
                        String baseName = btm.getBaseName(typeAttr);
                        if (baseName != null) {
                            collEl = new BaseType(typeAttr);
                        } else {
                            collEl = new UndefinedType(typeAttr);
                        }
                        symbolTablePut(collEl);
                    }
                    symbolTablePut(new CollectionType(qName, collEl, node, "[]"));
                } else {
                    // Add a BaseType or Undefined Type/Element
                    String baseName = btm.getBaseName(qName);
                    if (baseName != null)
                        symbolTablePut(new BaseType(qName));
                    else if (forElement.value == false)
                        symbolTablePut(new UndefinedType(qName));
                    else
                        symbolTablePut(new UndefinedElement(qName));
                }
            }
        }
    } // createTypeFromRef

    /**
     * Populate the symbol table with all of the MessageEntry's from the Definition.
     */
    private void populateMessages(Definition def) throws IOException {
        Iterator i = def.getMessages().values().iterator();
        while (i.hasNext()) {
            Message message = (Message) i.next();
            MessageEntry mEntry = new MessageEntry(message);
            symbolTablePut(mEntry);
        }
    } // populateMessages

    /**
     * Populate the symbol table with all of the PortTypeEntry's from the Definition.
     */
    private void populatePortTypes(Definition def) throws IOException {
        Iterator i = def.getPortTypes().values().iterator();
        while (i.hasNext()) {
            PortType portType = (PortType) i.next();

            // If the portType is undefined, then we're parsing a Definition
            // that didn't contain a portType, merely a binding that referred
            // to a non-existent port type.  Don't bother with it.
            if (!portType.isUndefined()) {
                PortTypeEntry ptEntry = new PortTypeEntry(portType);
                symbolTablePut(ptEntry);
            }
        }
    } // populatePortTypes

    /**
     * Create the parameters and store them in the bindingEntry.
     */ 
    private void populateParameters() throws IOException {
        Iterator it = symbolTable.values().iterator();
        while (it.hasNext()) {
            Vector v = (Vector) it.next();
            for (int i = 0; i < v.size(); ++i) {
                if (v.get(i) instanceof BindingEntry) {
                    BindingEntry bEntry = (BindingEntry) v.get(i);
                    
                    Binding binding = bEntry.getBinding();
                    PortType portType = binding.getPortType();
                    
                    HashMap parameters = new HashMap();
                    Iterator operations = portType.getOperations().iterator();
                    
                    // get parameters
                    while(operations.hasNext()) {
                        Operation operation = (Operation) operations.next();
                        String namespace = portType.getQName().getNamespaceURI();
                        Parameters parms = getOperationParameters(operation, 
                                                                  namespace, 
                                                                  bEntry);
                        parameters.put(operation, parms);
                    }
                    bEntry.setParameters(parameters);
                }
            }
        }
    } // populate Parameters
    
    /**
     * For the given operation, this method returns the parameter info conveniently collated.
     * There is a bit of processing that is needed to write the interface, stub, and skeleton.
     * Rather than do that processing 3 times, it is done once, here, and stored in the
     * Parameters object.
     */
    public Parameters getOperationParameters(Operation operation,
                                              String namespace, 
                                              BindingEntry bindingEntry) throws IOException {
        Parameters parameters = new Parameters();

        // The input and output Vectors of Parameters
        Vector inputs = new Vector();
        Vector outputs = new Vector();

        List parameterOrder = operation.getParameterOrdering();

        // Handle parameterOrder="", which is techinically illegal
        if (parameterOrder != null && parameterOrder.isEmpty()) {
            parameterOrder = null;
        }

        // All input parts MUST be in the parameterOrder list.  It is an error otherwise.
        if (parameterOrder != null) {
            Input input = operation.getInput();
            if (input != null) {
                Message inputMsg = input.getMessage();
                Map allInputs = inputMsg.getParts();
                Collection orderedInputs = inputMsg.getOrderedParts(parameterOrder);
                if (allInputs.size() != orderedInputs.size()) {
                    throw new IOException(JavaUtils.getMessage("emitFail00", operation.getName()));
                }
            }
        }

        boolean literalInput = false;
        boolean literalOutput = false;
        String bindingName = "unknown";
        if (bindingEntry != null) {
            literalInput = (bindingEntry.getInputBodyType(operation) == BindingEntry.USE_LITERAL);
            literalOutput = (bindingEntry.getOutputBodyType(operation) == BindingEntry.USE_LITERAL);
            bindingName = bindingEntry.getBinding().getQName().toString();
        }
        
        // Collect all the input parameters
        Input input = operation.getInput();
        if (input != null) {
            getParametersFromParts(inputs,
                        input.getMessage().getOrderedParts(null), 
                        literalInput, operation.getName(), bindingName);
        }

        // Collect all the output parameters
        Output output = operation.getOutput();
        if (output != null) {
            getParametersFromParts(outputs,
                        output.getMessage().getOrderedParts(null), 
                        literalOutput, operation.getName(), bindingName);
        }

        if (parameterOrder != null) {
            // Construct a list of the parameters in the parameterOrder list, determining the
            // mode of each parameter and preserving the parameterOrder list.
            for (int i = 0; i < parameterOrder.size(); ++i) {
                String name = (String) parameterOrder.get(i);

                // index in the inputs Vector of the given name, -1 if it doesn't exist.
                int index = getPartIndex(name, inputs);

                // index in the outputs Vector of the given name, -1 if it doesn't exist.
                int outdex = getPartIndex(name, outputs);

                if (index >= 0) {
                    // The mode of this parameter is either in or inout
                    addInishParm(inputs, outputs, index, outdex, parameters, true);
                }
                else if (outdex >= 0) {
                    addOutParm(outputs, outdex, parameters, true);
                }
                else {
                    System.err.println(JavaUtils.getMessage("noPart00", name));
                }
            }
        }

        // Get the mode info about those parts that aren't in the 
        // parameterOrder list. Since they're not in the parameterOrder list,
        // the order is, first all in (and inout) parameters, then all out
        // parameters, in the order they appear in the messages.
        for (int i = 0; i < inputs.size(); i++) {
            Parameter p = (Parameter)inputs.get(i);
            int outdex = getPartIndex(p.getName(), outputs);
            addInishParm(inputs, outputs, i, outdex, parameters, false);
        }

        // Now that the remaining in and inout parameters are collected,
        // determine the status of outputs.  If there is only 1, then it
        // is the return value.  If there are more than 1, then they are 
        // out parameters.
        if (outputs.size() == 1) {
            Parameter returnParam = (Parameter)outputs.get(0);
            parameters.returnType = returnParam.getType();
            if (parameters.returnType instanceof DefinedElement) {
                parameters.returnName = 
                        ((DefinedElement)parameters.returnType).getQName();
            } else {
                parameters.returnName = returnParam.getQName(); 
            }
            ++parameters.outputs;
        }
        else {
            for (int i = 0; i < outputs.size(); i++) {
                addOutParm(outputs, i, parameters, false);
            }
        }
        parameters.faults = operation.getFaults();

        return parameters;
    } // parameters

    /**
     * Return the index of the given name in the given Vector, -1 if it doesn't exist.
     */
    private int getPartIndex(String name, Vector v) {
        for (int i = 0; i < v.size(); i++) {
            if (name.equals(((Parameter)v.get(i)).getName())) {
                return i;
            }
        }
        return -1;
    } // getPartIndex

    /**
     * Add an in or inout parameter to the parameters object.
     */
    private void addInishParm(Vector inputs, 
                              Vector outputs, 
                              int index, 
                              int outdex, 
                              Parameters parameters, 
                              boolean trimInput) {        
        Parameter p = (Parameter)inputs.get(index);
        // If this is an element, we want the XML to reflect the element name
        // not the part name.
        if (p.getType() instanceof DefinedElement) {
            DefinedElement de = (DefinedElement)p.getType();
            p.setQName(de.getQName());
        }

        // Should we remove the given parameter type/name entries from the Vector?
        if (trimInput) {
            inputs.remove(index);
        }

        // At this point we know the name and type of the parameter, and that it's at least an
        // in parameter.  Now check to see whether it's also in the outputs Vector.  If it is,
        // then it's an inout parameter.
        if (outdex >= 0) {
            Parameter outParam = (Parameter)outputs.get(outdex);
            if (p.getType().equals(outParam.getType())) {
                outputs.remove(outdex);
                p.setMode(Parameter.INOUT);
                ++parameters.inouts;
            } else {
                // If we're here, we have both an input and an output
                // part with the same name but different types.... guess
                // it's not really an inout....
                ++parameters.inputs;  // Is this OK??
            }
        } else {
            ++parameters.inputs;
        }
        
        parameters.list.add(p);
    } // addInishParm

    /**
     * Add an output parameter to the parameters object.
     */
    private void addOutParm(Vector outputs, 
                            int outdex, 
                            Parameters parameters, 
                            boolean trim) {
        Parameter p = (Parameter)outputs.get(outdex);

        if (p.getType() instanceof DefinedElement) {
            DefinedElement de = (DefinedElement)p.getType();
            p.setQName(de.getQName());
        }

        if (trim) {
            outputs.remove(outdex);
        }

        p.setMode(Parameter.OUT);
        ++parameters.outputs;
        parameters.list.add(p);
    } // addOutParm

    /**
     * This method returns a vector containing Parameters which represent
     * each Part (shouldn't we call these "Parts" or something?)
     */
    public void getParametersFromParts(Vector v, 
                                          Collection parts, 
                                          boolean literal, 
                                          String opName, 
                                          String bindingName) 
            throws IOException {
        Iterator i = parts.iterator();

        while (i.hasNext()) {
            Parameter param = new Parameter();
            Part part = (Part) i.next();
            QName elementName = part.getElementName();
            QName typeName = part.getTypeName();
            String partName = part.getName();

            // Hack alert - Try to sense "wrapped" document literal mode
            // if we haven't been told not to.
            // Criteria:
            //  - If there is a single part, 
            //  - That part is an element
            //  - That element has the same name as the operation
            //  - That element has no attributes (check done below)
            if (!nowrap &&
                    literal && 
                    !i.hasNext() &&
                    elementName != null && 
                    elementName.getLocalPart().equals(opName)) {
                
                wrapped = true;
            }
            
            if (!literal || !wrapped) {
                // We're either RPC or literal + not wrapped.
                
                param.setName(partName);

                // Add this type or element name
                if (typeName != null) {
                    param.setType(getType(typeName));
                } else if (elementName != null) {
                    // Just an FYI: The WSDL spec says that for use=encoded
                    // that parts reference an abstract type using the type attr
                    // but we kinda do the right thing here, so let it go.
                    // if (!literal)
                    //   error...
                    param.setType(getElement(elementName));
                } else {
                    // no type or element
                    throw new IOException(
                            JavaUtils.getMessage("noTypeOrElement00", 
                                                 new String[] {partName, 
                                                               opName}));
                }
                                
                v.add(param);
                
                continue;   // next part
            }
            
            // flow to here means literal + wrapped!
                
            // See if we can map all the XML types to java(?) types
            // if we can, we use these as the types
            Node node = null;
            if (typeName != null) {
                // Since we can't (yet?) make the Axis engine generate the right
                // XML for literal parts that specify the type attribute,
                // abort processing with an error if we encounter this case
                //
                // node = getTypeEntry(typeName, false).getNode();
                throw new IOException(
                        JavaUtils.getMessage("literalTypePart00", 
                                             new String[] {partName, 
                                                           opName,  
                                                           bindingName}));
            }
            
            if (elementName == null) {
                throw new IOException(
                        JavaUtils.getMessage("noElemOrType", 
                                             partName, 
                                             opName));                
            }
            
            // Get the node which corresponds to the type entry for this
            // element.  i.e.:
            //  <part name="part" element="foo:bar"/>
            //  ...
            //  <schema targetNamespace="foo">
            //    <element name="bar"...>  <--- This one
            node = getTypeEntry(elementName, true).getNode();
            
            // Check if this element is of the form:
            //    <element name="foo" type="tns:foo_type"/> 
            QName type = Utils.getNodeTypeRefQName(node, "type");
            if (type != null) {
                // If in fact we have such a type, go get the node that
                // corresponds to THAT definition.
                node = getTypeEntry(type, false).getNode();
            }
            
            // If we have nothing at this point, we're in trouble.
            if (node == null) {
                throw new IOException(
                        JavaUtils.getMessage("badTypeNode", 
                                             new String[] {
                                                 partName, 
                                                 opName,  
                                                 elementName.toString()}));                
            }

            // check for attributes
            Vector vAttrs = SchemaUtils.getContainedAttributeTypes(node, this);
            if (vAttrs != null) {
                // can't do wrapped mode
                wrapped = false;
            }
            
            // Get the nested type entries.
            // TODO - If we are unable to represent any of the types in the
            // element, we need to use SOAPElement/SOAPBodyElement.
            // I don't believe getContainedElementDecl does the right thing yet.
            Vector vTypes =
                    SchemaUtils.getContainedElementDeclarations(node, this);

            // if we got the types entries and we didn't find attributes
            // use the tings is this element as the parameters
            if (vTypes != null && wrapped) {
                // add the elements in this list
                for (int j = 0; j < vTypes.size(); j++) {
                    ElementDecl elem = (ElementDecl) vTypes.elementAt(j);
                    Parameter p = new Parameter();
                    p.setQName(elem.getName());
                    p.setType(elem.getType());
                    v.add(p);
                }
            } else {
                // we were unable to get the types, or we found attributes so
                // we can't use wrapped mode.
                Parameter p = new Parameter();
                p.setName(partName);
                
                if (typeName != null) {
                    p.setType(getType(typeName));
                } else if (elementName != null) {
                    p.setType(getElement(elementName));
                }
                
                v.add(p);
            }
        } // while
        
    } // partStrings

    /**
     * Populate the symbol table with all of the BindingEntry's from the Definition.
     */
    private void populateBindings(Definition def) throws IOException {
        Iterator i = def.getBindings().values().iterator();
        while (i.hasNext()) {
            int bindingStyle = BindingEntry.STYLE_DOCUMENT;
            int bindingType = BindingEntry.TYPE_UNKNOWN;
            Binding binding = (Binding) i.next();
            Iterator extensibilityElementsIterator = binding.getExtensibilityElements().iterator();
            while (extensibilityElementsIterator.hasNext()) {
                Object obj = extensibilityElementsIterator.next();
                if (obj instanceof SOAPBinding) {
                    bindingType = BindingEntry.TYPE_SOAP;
                    SOAPBinding sb = (SOAPBinding) obj;
                    String style = sb.getStyle();
                    if ("rpc".equalsIgnoreCase(style)) {
                        bindingStyle = BindingEntry.STYLE_RPC;
                    }
                }
                else if (obj instanceof HTTPBinding) {
                    HTTPBinding hb = (HTTPBinding) obj;
                    if (hb.getVerb().equalsIgnoreCase("post")) {
                        bindingType = BindingEntry.TYPE_HTTP_POST;
                    }
                    else {
                        bindingType = BindingEntry.TYPE_HTTP_GET;
                    }
                }
            }

            // Check the Binding Operations for use="literal"
            boolean hasLiteral = false;
            HashMap attributes = new HashMap();
            List bindList = binding.getBindingOperations();
            for (Iterator opIterator = bindList.iterator(); opIterator.hasNext();) {
                int inputBodyType = BindingEntry.USE_ENCODED;
                int outputBodyType = BindingEntry.USE_ENCODED;
                BindingOperation bindOp = (BindingOperation) opIterator.next();

                // input
                if (bindOp.getBindingInput() != null) {
                    if (bindOp.getBindingInput().getExtensibilityElements() != null) {
                        Iterator inIter = bindOp.getBindingInput().getExtensibilityElements().iterator();
                        for (; inIter.hasNext();) {
                            Object obj = inIter.next();
                            if (obj instanceof SOAPBody) {
                                String use = ((SOAPBody) obj).getUse();
                                if (use == null) {
                                    throw new IOException(JavaUtils.getMessage(
                                            "noUse", bindOp.getName()));
                                }
                                if (use.equalsIgnoreCase("literal")) {
                                    inputBodyType = BindingEntry.USE_LITERAL;
                                }
                                break;
                            }
                        }
                    }
                }

                // output
                if (bindOp.getBindingOutput() != null) {
                    if (bindOp.getBindingOutput().getExtensibilityElements() != null) {
                        Iterator outIter = bindOp.getBindingOutput().getExtensibilityElements().iterator();
                        for (; outIter.hasNext();) {
                            Object obj = outIter.next();
                            if (obj instanceof SOAPBody) {
                                String use = ((SOAPBody) obj).getUse();
                                if (use == null) {
                                    throw new IOException(JavaUtils.getMessage(
                                            "noUse", bindOp.getName()));
                                }
                                if (use.equalsIgnoreCase("literal")) {
                                    outputBodyType = BindingEntry.USE_LITERAL;
                                }
                                break;
                            }
                        }
                    }
                }

                // faults
                HashMap faultMap = new HashMap();
                Iterator faultMapIter = bindOp.getBindingFaults().values().iterator();
                for (; faultMapIter.hasNext(); ) {
                    BindingFault bFault = (BindingFault)faultMapIter.next();

                    // Set default entry for this fault
                    String faultName = bFault.getName();
                    int faultBodyType = BindingEntry.USE_ENCODED;

                    Iterator faultIter =
                            bFault.getExtensibilityElements().iterator();
                    for (; faultIter.hasNext();) {
                        Object obj = faultIter.next();
                        if (obj instanceof SOAPBody) {
                            String use = ((SOAPBody) obj).getUse();
                            if (use == null) {
                                throw new IOException(JavaUtils.getMessage(
                                        "noUse", bindOp.getName()));
                            }
                            if (use.equalsIgnoreCase("literal")) {
                                faultBodyType = BindingEntry.USE_LITERAL;
                            }
                            break;
                        }
                    }
                    // Add this fault name and bodyType to the map
                    faultMap.put(faultName, new Integer(faultBodyType));
                }
                // Associate the portType operation that goes with this binding
                // with the body types.
                attributes.put(bindOp.getOperation(),
                        new BindingEntry.OperationAttr(inputBodyType, outputBodyType, faultMap));

                // If the input or output body uses literal, flag the binding as using literal.
                // NOTE:  should I include faultBodyType in this check?
                if (inputBodyType == BindingEntry.USE_LITERAL ||
                        outputBodyType == BindingEntry.USE_LITERAL) {
                    hasLiteral = true;
                }
            } // binding operations

            BindingEntry bEntry = new BindingEntry(binding, bindingType, bindingStyle, hasLiteral, attributes);
            symbolTablePut(bEntry);
        }
    } // populateBindings

    /**
     * Populate the symbol table with all of the ServiceEntry's from the Definition.
     */
    private void populateServices(Definition def) throws IOException {
        Iterator i = def.getServices().values().iterator();
        while (i.hasNext()) {
            Service service = (Service) i.next();

            // do a bit of name validation
            if (service.getQName() == null ||
                service.getQName().getLocalPart() == null || 
                service.getQName().getLocalPart().equals("")) {
                throw new IOException(JavaUtils.getMessage("BadServiceName00"));
            }
            
            ServiceEntry sEntry = new ServiceEntry(service);
            symbolTablePut(sEntry);
        }
    } // populateServices

    /**
     * Set each SymTabEntry's isReferenced flag.  The default is false.  If no other symbol
     * references this symbol, then leave it false, otherwise set it to true.
     * (An exception to the rule is that derived types are set as referenced if 
     * their base type is referenced.  This is necessary to support generation and
     * registration of derived types.)
     */
    private void setReferences(Definition def, Document doc) {
        Map stuff = def.getServices();
        if (stuff.isEmpty()) {
            stuff = def.getBindings();
            if (stuff.isEmpty()) {
                stuff = def.getPortTypes();
                if (stuff.isEmpty()) {
                    stuff = def.getMessages();
                    if (stuff.isEmpty()) {
                        for (int i = 0; i < types.size(); ++i) {
                            TypeEntry type = (TypeEntry) types.get(i);
                            setTypeReferences(type, doc, false);
                        }
                    }
                    else {
                        Iterator i = stuff.values().iterator();
                        while (i.hasNext()) {
                            Message message = (Message) i.next();
                            MessageEntry mEntry =
                                    getMessageEntry(message.getQName());
                            setMessageReferences(mEntry, def, doc, false);
                        }
                    }
                }
                else {
                    Iterator i = stuff.values().iterator();
                    while (i.hasNext()) {
                        PortType portType = (PortType) i.next();
                        PortTypeEntry ptEntry =
                                getPortTypeEntry(portType.getQName());
                        setPortTypeReferences(ptEntry, null, def, doc);
                    }
                }
            }
            else {
                Iterator i = stuff.values().iterator();
                while (i.hasNext()) {
                    Binding binding = (Binding) i.next();
                    BindingEntry bEntry = getBindingEntry(binding.getQName());
                    setBindingReferences(bEntry, def, doc);
                }
            }
        }
        else {
            Iterator i = stuff.values().iterator();
            while (i.hasNext()) {
                Service service = (Service) i.next();
                ServiceEntry sEntry = getServiceEntry(service.getQName());
                setServiceReferences(sEntry, def, doc);
            }
        }
    } // setReferences

    /**
     * Set the isReferenced flag to true on the given TypeEntry and all
     * SymTabEntries that it refers to.
     */
    private void setTypeReferences(TypeEntry entry, Document doc,
            boolean literal) {

        // Check to see if already processed.
        if ((entry.isReferenced() && !literal) ||
            (entry.isOnlyLiteralReferenced() && literal)) {
            return;
        }

        if (wrapped) {
            // If this type is ONLY referenced from a literal usage in a binding,
            // then isOnlyLiteralReferenced should return true.
            if (!entry.isReferenced() && literal) {
                entry.setOnlyLiteralReference(true);
            }
            // If this type was previously only referenced as a literal type,
            // but now it is referenced in a non-literal manner, turn off the
            // onlyLiteralReference flag.
            else if (entry.isOnlyLiteralReferenced() && !literal) {
                entry.setOnlyLiteralReference(false);
            }
        }


        // If we don't want to emit stuff from imported files, only set the
        // isReferenced flag if this entry exists in the immediate WSDL file.
        Node node = entry.getNode();
        if (addImports || node == null || node.getOwnerDocument() == doc) {
            entry.setIsReferenced(true);
            if (entry instanceof DefinedElement) {
                BooleanHolder forElement = new BooleanHolder();
                QName referentName = Utils.getNodeTypeRefQName(node, forElement);
                if (referentName != null) {
                    TypeEntry referent = getTypeEntry(referentName, forElement.value);
                    if (referent != null) {
                        setTypeReferences(referent, doc, literal);
                    }
                }
                // If the Defined Element has an anonymous type, 
                // process it with the current literal flag setting.
                QName anonQName = SchemaUtils.getElementAnonQName(entry.getNode());
                if (anonQName != null) {
                    TypeEntry anonType = getType(anonQName);
                    if (anonType != null) {
                        setTypeReferences(anonType, doc, literal);
                        return;
                    }
                }
            }
        }

        HashSet nestedTypes = Utils.getNestedTypes(entry, this, true);
        Iterator it = nestedTypes.iterator();
        while (it.hasNext()) {
            TypeEntry nestedType = (TypeEntry) it.next();
            if (!nestedType.isReferenced()) {
                //setTypeReferences(nestedType, doc, literal);
                setTypeReferences(nestedType, doc, false);
            }
        }
    } // setTypeReferences

    /**
     * Set the isReferenced flag to true on the given MessageEntry and all
     * SymTabEntries that it refers to.
     */
    private void setMessageReferences(
            MessageEntry entry, Definition def, Document doc, boolean literal) {
        // If we don't want to emit stuff from imported files, only set the
        // isReferenced flag if this entry exists in the immediate WSDL file.
        Message message = entry.getMessage();
        if (addImports) {
            entry.setIsReferenced(true);
        }
        else {
            // NOTE:  I thought I could have simply done:
            // if (def.getMessage(message.getQName()) != null)
            // but that method traces through all imported messages.
            Map messages = def.getMessages();
            if (messages.containsValue(message)) {
                entry.setIsReferenced(true);
            }
        }

        // Set all the message's types
        Iterator parts = message.getParts().values().iterator();
        while (parts.hasNext()) {
            Part part = (Part) parts.next();
            TypeEntry type = getType(part.getTypeName());
            if (type != null) {
                setTypeReferences(type, doc, literal);
            }
            type = getElement(part.getElementName());
            if (type != null) {
                setTypeReferences(type, doc, literal);
                //QName ref = Utils.getNodeTypeRefQName(type.getNode(), "type");
                TypeEntry refType = type.getRefType();
                if (refType != null) {
                  setTypeReferences(refType, doc, literal);
                }
            }
        }
    } // setMessageReference

    /**
     * Set the isReferenced flag to true on the given PortTypeEntry and all
     * SymTabEntries that it refers to.
     */
    private void setPortTypeReferences(
            PortTypeEntry entry, BindingEntry bEntry,
            Definition def, Document doc) {
        // If we don't want to emit stuff from imported files, only set the
        // isReferenced flag if this entry exists in the immediate WSDL file.
        PortType portType = entry.getPortType();
        if (addImports) {
            entry.setIsReferenced(true);
        }
        else {
            // NOTE:  I thought I could have simply done:
            // if (def.getPortType(portType.getQName()) != null)
            // but that method traces through all imported portTypes.
            Map portTypes = def.getPortTypes();
            if (portTypes.containsValue(portType)) {
                entry.setIsReferenced(true);
            }
        }

        // Set all the portType's messages
        Iterator operations = portType.getOperations().iterator();

        // For each operation, query its input, output, and fault messages
        while (operations.hasNext()) {
            Operation operation = (Operation) operations.next();

            Input input = operation.getInput();
            Output output = operation.getOutput();

            // Find out if this reference is a literal reference or not.
            boolean literalInput = false;
            boolean literalOutput = false;
            if (bEntry != null) {
                literalInput = bEntry.getInputBodyType(operation) ==
                        BindingEntry.USE_LITERAL;
                literalOutput = bEntry.getOutputBodyType(operation) ==
                        BindingEntry.USE_LITERAL;
            }

            // Query the input message
            if (input != null) {
                Message message = input.getMessage();
                if (message != null) {
                    MessageEntry mEntry = getMessageEntry(message.getQName());
                    if (mEntry != null) {
                        setMessageReferences(mEntry, def, doc, literalInput);
                    }
                }
            }

            // Query the output message
            if (output != null) {
                Message message = output.getMessage();
                if (message != null) {
                    MessageEntry mEntry = getMessageEntry(message.getQName());
                    if (mEntry != null) {
                        setMessageReferences(mEntry, def, doc, literalOutput);
                    }
                }
            }

            // Query the fault messages
            Iterator faults =
              operation.getFaults().values().iterator();
            while (faults.hasNext()) {
                Message message = ((Fault) faults.next()).getMessage();
                if (message != null) {
                    MessageEntry mEntry = getMessageEntry(message.getQName());
                    if (mEntry != null) {
                        setMessageReferences(mEntry, def, doc, false);
                    }
                }
            }
        }
    } // setPortTypeReferences

    /**
     * Set the isReferenced flag to true on the given BindingEntry and all
     * SymTabEntries that it refers to ONLY if this binding is a SOAP binding.
     */
    private void setBindingReferences(
            BindingEntry entry, Definition def, Document doc) {

        if (entry.getBindingType() == BindingEntry.TYPE_SOAP) {
            // If we don't want to emit stuff from imported files, only set the
            // isReferenced flag if this entry exists in the immediate WSDL file.
            Binding binding = entry.getBinding();
            if (addImports) {
                entry.setIsReferenced(true);
            }
            else {
                // NOTE:  I thought I could have simply done:
                // if (def.getBindng(binding.getQName()) != null)
                // but that method traces through all imported bindings.
                Map bindings = def.getBindings();
                if (bindings.containsValue(binding)) {
                    entry.setIsReferenced(true);
                }
            }

            // Set all the binding's portTypes
            PortType portType = binding.getPortType();
            PortTypeEntry ptEntry = getPortTypeEntry(portType.getQName());
            if (ptEntry != null) {
                setPortTypeReferences(ptEntry, entry, def, doc);
            }
        }
    } // setBindingReferences

    /**
     * Set the isReferenced flag to true on the given ServiceEntry and all
     * SymTabEntries that it refers to.
     */
    private void setServiceReferences(
            ServiceEntry entry, Definition def, Document doc) {
        // If we don't want to emit stuff from imported files, only set the
        // isReferenced flag if this entry exists in the immediate WSDL file.
        Service service = entry.getService();
        if (addImports) {
            entry.setIsReferenced(true);
        }
        else {
            // NOTE:  I thought I could have simply done:
            // if (def.getService(service.getQName()) != null)
            // but that method traces through all imported services.
            Map services = def.getServices();
            if (services.containsValue(service)) {
                entry.setIsReferenced(true);
            }
        }

        // Set all the service's bindings
        Iterator ports = service.getPorts().values().iterator();
        while (ports.hasNext()) {
            Port port = (Port) ports.next();
            Binding binding = (Binding) port.getBinding();
            BindingEntry bEntry = getBindingEntry(binding.getQName());
            if (bEntry != null) {
                setBindingReferences(bEntry, def, doc);
            }
        }
    } // setServiceReferences

    /**
     * Put the given SymTabEntry into the symbol table, if appropriate.  
     */
    private void symbolTablePut(SymTabEntry entry) throws IOException {
        QName name = entry.getQName();
        if (get(name, entry.getClass()) == null) {
            // An entry of the given qname of the given type doesn't exist yet.
            if (entry instanceof Type && 
                get(name, UndefinedType.class) != null) {

                // A undefined type exists in the symbol table, which means
                // that the type is used, but we don't yet have a definition for
                // the type.  Now we DO have a definition for the type, so
                // replace the existing undefined type with the real type.

                if (((TypeEntry)get(name, UndefinedType.class)).isSimpleType() &&
                    !((TypeEntry)entry).isSimpleType()) {
                    // Problem if the undefined type was used in a 
                    // simple type context.
                    throw new IOException(
                                          JavaUtils.getMessage("AttrNotSimpleType01",
                                                               name.toString()));

                }
                Vector v = (Vector) symbolTable.get(name);
                for (int i = 0; i < v.size(); ++i) {
                    Object oldEntry = v.elementAt(i);
                    if (oldEntry instanceof UndefinedType) {

                        // Replace it in the symbol table
                        v.setElementAt(entry, i);

                        // Replace it in the types Vector
                        for (int j = 0; j < types.size(); ++j) {
                            if (types.elementAt(j) == oldEntry) {
                                types.setElementAt(entry, j);
                            }
                        }
                        
                        // Update all of the entries that refer to the unknown type
                        ((UndefinedType)oldEntry).update((Type)entry);
                    }
                }
            } else if (entry instanceof Element && 
                get(name, UndefinedElement.class) != null) {
                // A undefined element exists in the symbol table, which means
                // that the element is used, but we don't yet have a definition for
                // the element.  Now we DO have a definition for the element, so
                // replace the existing undefined element with the real element.
                Vector v = (Vector) symbolTable.get(name);
                for (int i = 0; i < v.size(); ++i) {
                    Object oldEntry = v.elementAt(i);
                    if (oldEntry instanceof UndefinedElement) {

                        // Replace it in the symbol table
                        v.setElementAt(entry, i);

                        // Replace it in the types Vector
                        for (int j = 0; j < types.size(); ++j) {
                            if (types.elementAt(j) == oldEntry) {
                                types.setElementAt(entry, j);
                            }
                        }
                        
                        // Update all of the entries that refer to the unknown type
                        ((Undefined)oldEntry).update((Element)entry);
                    }
                }
            }
            else {
                // Add this entry to the symbol table
                Vector v = (Vector) symbolTable.get(name);
                if (v == null) {
                    v = new Vector();
                    symbolTable.put(name, v);
                }
                v.add(entry);
                if (entry instanceof TypeEntry) {
                    types.add(entry);
                }
            }
        }
        else {
            throw new IOException(
                    JavaUtils.getMessage("alreadyExists00", "" + name));
        }
    } // symbolTablePut

} // class SymbolTable
