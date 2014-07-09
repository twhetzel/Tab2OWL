import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.io.OWLXMLOntologyFormat;
import org.semanticweb.owlapi.io.StreamDocumentTarget;
import org.semanticweb.owlapi.model.AddAxiom;
import org.semanticweb.owlapi.model.AddImport;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLAnnotation;
import org.semanticweb.owlapi.model.OWLAnnotationProperty;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLClassExpression;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLDeclarationAxiom;
import org.semanticweb.owlapi.model.OWLImportsDeclaration;
import org.semanticweb.owlapi.model.OWLIndividual;
import org.semanticweb.owlapi.model.OWLObjectProperty;
import org.semanticweb.owlapi.model.OWLObjectPropertyAssertionAxiom;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLOntologyID;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.model.OWLOntologyStorageException;
import org.semanticweb.owlapi.model.OWLSubClassOfAxiom;
import org.semanticweb.owlapi.model.PrefixManager;
import org.semanticweb.owlapi.model.SetOntologyID;
import org.semanticweb.owlapi.util.DefaultPrefixManager;
import org.semanticweb.owlapi.util.SimpleIRIMapper;

import org.wikiutils.*;

import org.wikipedia.*;
import org.wikipedia.tools.*;
import java.util.*;
import info.bliki.api.query.*;
import info.bliki.api.*;

import java.util.*;
import java.net.*;
import java.io.*;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.logging.*;

import javax.security.auth.login.*;


public class ConvertFile {

	/**
	 * @param args
	 * @throws OWLOntologyStorageException 
	 * @throws IOException 
	 * @throws OWLOntologyCreationException 
	 */
	public static void main(String[] args) throws OWLOntologyStorageException, IOException, OWLOntologyCreationException {
		/**
		 * Read in tab delimited file and convert 
		 * to stand-alone OWL file 
		 * 
		 */

		Map<String, ArrayList> termsAndProperties = processTermFile();	

		// Create Hashtable for label->ID for classes
		Hashtable<String,String> classIDHashtable = new Hashtable<String,String>();
		classIDHashtable = createClassIDLabelHash(termsAndProperties);

		File owlFile = createOWLFile();
		buildClassTree(termsAndProperties, owlFile, classIDHashtable);

		addClassRestrictions(termsAndProperties, owlFile, classIDHashtable);
		//addAnnotations(termsAndProperties, owlFile);
	}



	/**
	 * Read in file and generate data structure of terms and their properties 
	 * @return
	 */
	private static Map<String, ArrayList> processTermFile() {
		System.out.println("\n** processTermFile method **");
		int lineCount = 0;
		BufferedReader br = null;
		Map<String, ArrayList> terms = new HashMap<String, ArrayList>();
		ArrayList<String> list = new ArrayList<String>();


		try {
			String sCurrentLine;
			br = new BufferedReader(new FileReader("/Users/whetzel/Documents/UCSD/NeuroLex/NeuroLex-ElectrophysiologyCategories.txt"));

			while ((sCurrentLine = br.readLine()) != null) {
				lineCount++;
				if (lineCount > 1 ) {
					//System.out.println("**NEW-LINE: "+sCurrentLine);
					// Parse file columns
					String[] values = sCurrentLine.split("\t", -1); // Do not truncate line on empty values

					// Handle null values in spreadsheet
					for (int index = 0; index < values.length; index++) {		
						if (values[index].length() > 0) { // Check that the array index contains a value
							//System.out.print("\'"+values[index]+"\'"+"\n");
							list.add(values[index]);
						}
						else {
							values[index] = "NO VALUE"; // use this later to decide whether to add a class restriction/annotation to a class
							//System.out.print("NULL-"+values[index]+"\t");
							list.add(values[index]);
						}
						// Create a copy of the ArrayList to keep values, but not references so it can be cleared before reading the next line 
						ArrayList<String> copy = new ArrayList<String>();
						copy.addAll(list);
						// Put values in a HashMap keyed on the ID, value[5] OR label, value[1]
						terms.put(values[1], copy );

					}		
					//System.out.println("ARR-SIZE: "+list.size());
					//System.out.println("MAP: "+values[1]+"\tLIST: "+list);

					// Clear initial ArrayList to prepare for next line in file
					list.clear();

					//System.out.println("TERMS:"+terms); //Values are null because they were cleared
					//System.out.println();
				}
				else {
					//System.out.println("Skipping header line....");
				}
			}
			} catch (IOException e) {
				e.printStackTrace();
			} finally {
				try {
					if (br != null)br.close();
				} catch (IOException ex) {
					ex.printStackTrace();
				}
			}
		return terms;
	}

	/**
	 * Build Hashtable of term labels (key) and term Ids (value) 
	 * in order to swap out label for Id in term IRI 
	 */
	private static Hashtable<String, String> createClassIDLabelHash(Map<String, ArrayList> termsAndProperties) {
		System.out.println("\n** createClassIDLabelHash method **");
		// Populate hashtable from termsAndProperies using the label (values[1]) as the key and ID (values[5]) as the value
		Hashtable<String,String> hashtable = new Hashtable<String,String>();

		for (Entry<String, ArrayList> entry : termsAndProperties.entrySet()) {
			String key = entry.getKey(); //Label
			String value = entry.getValue().get(5).toString(); //ID
			hashtable.put(key, value);
		}
		
		// Loop through termsAndProperties to find root terms based on
		// that every Parent label should exist as a key in the hashtable
		System.out.println("** Find Root terms ");
		for (Entry<String, ArrayList> entry : termsAndProperties.entrySet()) {
			String key = entry.getKey(); // Label
			String parentLabel = entry.getValue().get(2).toString(); //Parent label
			System.out.println("\n** TermLabel: "+key+" ParentLabel: "+parentLabel);
			
			//Check if hashtable contains parentLabel as a key
			if (!hashtable.containsKey(parentLabel)) {
				// Format parent label for use in query to Wiki 
				String newParentLabel = parentLabel.replaceAll(" ", "_");
				newParentLabel = "Category:"+parentLabel;
				System.out.println("Null ID Found. Use \""+newParentLabel+"\" to query NeuroLex for ID.");
				// Use WikiAPI to get ID for Parent
				String parentIDFromWiki = getParentIdFromWikiAPI(newParentLabel);
				// Trim whitespace from value
				parentIDFromWiki = parentIDFromWiki.trim();
				System.out.println("** ParentIDFromWiki: "+parentIDFromWiki);
				System.out.println("ParentLabel: "+parentLabel+" ParentID"+parentIDFromWiki);
				hashtable.put(parentLabel, parentIDFromWiki);
			}	
		}
		return hashtable;
	}


	/**
	 * Build OWL file
	 * @param termsAndProperties
	 * @return 
	 * @throws OWLOntologyStorageException 
	 * @throws IOException 
	 */
	private static File createOWLFile() throws OWLOntologyStorageException, IOException {
		System.out.println("\n** createOWLFile method **");
		//Create empty ontology 
		OWLOntology ontology = null;
		File file = new File("owlfiletest.owl"); //ontology file to write to
		OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
		//IRI ontologyIRI = IRI.create("http://neurolex.org/wiki/");
		IRI ontologyIRI = IRI.create("http://uri.neuinfo.org/nif/nifstd/");
		// Specify IRIs for ontology imports
		IRI bfoIRI = IRI.create("http://purl.obolibrary.org/obo/bfo.owl");
		IRI iaoIRI = IRI.create("http://purl.obolibrary.org/obo/iao.owl");

		try {
			ontology = manager.createOntology(ontologyIRI);
			OWLDataFactory factory = manager.getOWLDataFactory();
			// Create the document IRI for our ontology
			IRI documentIRI = IRI.create("/Users/whetzel/git/tab2owl/Tab2OWL/");  //Local Git repo

			// Set up a mapping, which maps the ontology to the document IRI
			SimpleIRIMapper mapper = new SimpleIRIMapper(ontologyIRI, documentIRI);
			manager.addIRIMapper(mapper);
			System.out.println("Created ontology: " + ontology);

			// Set version IRI, use the date the file contents were exported from NeuroLex
			IRI versionIRI = IRI.create(ontologyIRI + "NeuroLexExport06302014");
			OWLOntologyID newOntologyID = new OWLOntologyID(ontologyIRI, versionIRI);
			// Create the change that will set our version IRI
			SetOntologyID setOntologyID = new SetOntologyID(ontology, newOntologyID);
			// Apply the change
			manager.applyChange(setOntologyID);
			System.out.println("Ontology: " + ontology);


			// Add import for BFO ontology
			OWLImportsDeclaration bfoImportDeclaraton =
					factory.getOWLImportsDeclaration(bfoIRI); 
			manager.applyChange(new AddImport(ontology, bfoImportDeclaraton));

			// Add import for IAO ontology
			OWLImportsDeclaration iaoImportDeclaraton =
					factory.getOWLImportsDeclaration(iaoIRI); 
			manager.applyChange(new AddImport(ontology, iaoImportDeclaraton));

			// Now save a local copy of the ontology 
			manager.saveOntology(ontology, IRI.create(file.toURI()));

		} catch (OWLOntologyCreationException e) {
			e.printStackTrace();
		}			
		return file;
	}


	/**
	 * 
	 * @param termsAndProperties
	 * @param owlFile
	 * @param classIDHashtable 
	 * @throws OWLOntologyCreationException 
	 * @throws OWLOntologyStorageException 
	 */
	private static void buildClassTree(Map<String, ArrayList> termsAndProperties,
			File owlFile, Hashtable<String, String> classIDHashtable) throws OWLOntologyCreationException, OWLOntologyStorageException {
		System.out.println("\n** buildClassTree method **");

		// Open ontology file
		OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
		OWLOntology ontology = manager.loadOntologyFromOntologyDocument(owlFile);
		OWLDataFactory factory = manager.getOWLDataFactory();

		// Populate ontology with class hierarchy from termsAndProperties 
		for (Entry<String, ArrayList> entry : termsAndProperties.entrySet()) {
			String key = entry.getKey();	
			System.out.println("\nK:"+key+"\tV:"+entry.getValue());

			//String prefix = "http://neurolex.org/wiki/Special:ExportRDF/Category:";  // Prefix if using Term Label 
			String prefix = "http://uri.neuinfo.org/nif/nifstd/";
			//String newKey = key.replaceAll(" ", "_"); // Remove since using ID in IRI 

			//Use classIDHashtable to get ID instead of label to create Class IRI
			String id = classIDHashtable.get(key);
			System.out.println("** ID: "+id
					+"\t** Label: "+key);

			IRI iri = IRI.create(prefix+id);
			// Now we create the Class, NOTE: this does not add the Class to the ontology, but creates the Class object
			OWLClass clsAMethodA = factory.getOWLClass(iri);

			// Add Parent to Class, value[2] from termsAndProperties Map object
			String parent = entry.getValue().get(2).toString();
			// Get ID for parent from hashtable
			String parentId = classIDHashtable.get(parent);
			System.out.println("** ParentID: "+parentId
					+"\t** Parent Label: "+parent);

			
			// TODO Remove after more testing that moving this test to createClassIDLabelHash is working
			if (parentId == null) {
				parentId = parent.replaceAll(" ", "_");
				parentId = "Category:"+parentId;
				System.out.println("Null ID Found. Use \""+parentId+"\" to query NeuroLex for ID.");
				// Use WikiAPI to get ID for Parent
				String parentIDFromWiki = getParentIdFromWikiAPI(parentId);
				// Trim whitespace from value 
				parentId = parentIDFromWiki.trim();
				System.out.println("** ParentIDFromWiki: "+parentId);
				// Update null value in hashtable
				classIDHashtable.put(parent, parentId);  //Then add key and value back to hashtable 
			}

			OWLClass clsB = factory.getOWLClass(IRI.create(prefix + parentId));
			OWLAxiom axiom = factory.getOWLSubClassOfAxiom(clsAMethodA, clsB);
			AddAxiom addAxiom = new AddAxiom(ontology, axiom);
			System.out.println(addAxiom);
			// We now use the manager to apply the change
			manager.applyChange(addAxiom);

			// We can add a declaration axiom to the ontology, that essentially adds
			// the class to the signature of our ontology. 
			OWLDeclarationAxiom declarationAxiom = factory
					.getOWLDeclarationAxiom(clsAMethodA);
			manager.addAxiom(ontology, declarationAxiom);
			
			// Save ontology 
			manager.saveOntology(ontology);	
		}		
	}



	/**
	 * Use Wiki API to get additional content
	 */
	private static String getParentIdFromWikiAPI(String newParent) {
		//Tester test = new Tester();
		//System.out.println("** Query term: "+newParent);
		String content = getPageContent(newParent);  //test.getPageContent(newParent); 
		//System.out.println(content);

		// Print only line with Term ID (|Id=)
		String[] contentLines = content.split("\\r?\\n");
		System.out.println("ContentLines:\""+contentLines[0]+"\"");
		
		String parentId = null;
		// Account for pages with no content 
		if (contentLines[0] == null | contentLines[0].length() == 0) {
			String parentIdValues[] = newParent.split(":");
			parentId = parentIdValues[1];
		}
		else {
		String termIDPattern = "|Id=";
		for (String line : contentLines) {
			System.out.println("Line: "+line);
			
			if (line.contains(termIDPattern)) {
				System.out.println("TermId: "+line);
				String [] idLine = line.split("=");
				parentId = idLine[1];
				System.out.println("** TermID: "+parentId);
			}
		}
		}
		return parentId;
	}

	@SuppressWarnings("unchecked")
	public static String getPageContent(String title) {
		String content = null;
		String[] listOfTitleStrings = { title }; 
		//System.out.println("Page Title for Query: "+listOfTitleStrings[0]);
		String host =  "http://neurolex.org/w/api.php";

		User user = new User("Whetzel", "neurolex", host);  
		List<Page> listOfPages =  user.queryContent(listOfTitleStrings);  //user.queryCategories(listOfTitleStrings);

		for (Page page : listOfPages) {
			System.out.println("PageTitle: "+page.getTitle());
			content = page.getCurrentContent();
			System.out.println("------------------"+content+"-----------------");
			if(content != null)
				break;
			// Account for pages that do not exist .. this doesn't seem to be called?
			else  {
				System.out.println("Content1: "+content);
				content = "|Id="+page.getTitle();
				System.out.println("Content2: "+content);
			}
		}
		return content;
	}



	/**
	 * 
	 * @param termsAndProperties
	 * @param owlFile
	 * @param classIDHashtable 
	 * @throws OWLOntologyCreationException 
	 * @throws OWLOntologyStorageException 
	 */
	private static void addClassRestrictions(
			Map<String, ArrayList> termsAndProperties, File owlFile, Hashtable<String, String> classIDHashtable) throws OWLOntologyCreationException, OWLOntologyStorageException {
		System.out.println("\n** addClassRestrictions method **");

		// Open ontology file
		OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
		OWLOntology ontology = manager.loadOntologyFromOntologyDocument(owlFile);
		OWLDataFactory factory = manager.getOWLDataFactory();
		//System.out.println("Loaded ontology: " + ontology);

		/*
		 * Declare Object Properties
		 */
		String BFO = "http://purl.obolibrary.org/obo/";  
		// Create Object Property
		OWLObjectProperty hasRoleProperty = factory.getOWLObjectProperty(IRI.create(BFO
				+ "BFO_0000087"));
		OWLObjectProperty isPartOfProperty = factory.getOWLObjectProperty(IRI.create(BFO
				+ "BFO_0000050"));
		//System.err.println("hasRoleProperty: "+hasRoleProperty);

		// Iterate through termsAndProperties AND get parent from hashtable 
		for (Entry<String, ArrayList> entry : termsAndProperties.entrySet()) {
			String key = entry.getKey();	
			System.out.println("\nK:"+key+"\tV:"+entry.getValue());

			//PrefixManager pm = new DefaultPrefixManager("http://neurolex.org/wiki/Special:ExportRDF/Category:");
			PrefixManager pm = new DefaultPrefixManager("http://uri.neuinfo.org/nif/nifstd/");
			//String newKey = key.replaceAll(" ", "_");
			// Get ID from hashtable
			String classId = classIDHashtable.get(key);
			OWLClass clsAMethodB = factory.getOWLClass(classId, pm);
			System.err.println("classAMethodB: "+clsAMethodB);


			/*
			 * Add hasRoleProperty to Class
			 */
			// Obtain a reference to values for Has Role, values[3]
			String hasRoleObject = entry.getValue().get(3).toString();
			System.err.println("HasRoleObject: "+hasRoleObject);
			String newHasRoleObject = hasRoleObject.replace(":Category:","");
			newHasRoleObject = newHasRoleObject.replaceAll("\"", "");
			System.err.println("HasRoleObject: "+newHasRoleObject);

			if (!newHasRoleObject.equals("NO VALUE")) {
				//System.err.println("VALUE FOUND: "+newHasRoleObject);
				//NOTE: newHasRoleObject may have more than 1 object value
				String[] hasRoleObjectValues = newHasRoleObject.split(",");
				// Add each hasRoleObect value as a property restriction
				for (String s : hasRoleObjectValues ) {
					String sId = classIDHashtable.get(s);
					
					if (sId == null) {
						s = s.replaceAll(" ", "_");
						s = "Category:"+s;
						System.out.println("Null ID Found. Use \""+s+"\" to query NeuroLex for ID.");
						// Use WikiAPI to get ID for Parent
						String parentIDFromWiki = getParentIdFromWikiAPI(s);
						System.out.println(parentIDFromWiki);
						// Trim whitespace from value 
						sId = parentIDFromWiki.trim();
						System.out.println("** ParentIDFromWiki: "+sId);
						// Update null value in hashtable
						classIDHashtable.put(s, sId);  //Then add key and value back to hashtable 
					}
					System.err.println("hasRoleObjectValue: "+s+" Id: "+sId);

					OWLClass roleObject = factory.getOWLClass(sId, pm); 
					OWLClassExpression hasPartSomeRole = factory.getOWLObjectSomeValuesFrom(hasRoleProperty,
							roleObject); 
					OWLSubClassOfAxiom ax = factory.getOWLSubClassOfAxiom(clsAMethodB, hasPartSomeRole);	
					System.err.println("RoleObject: "+roleObject+"\nAxiom: "+ax);

					// Add the axiom to our ontology 
					AddAxiom addAx = new AddAxiom(ontology, ax);
					manager.applyChange(addAx);
				}
				System.out.println();
			}


			/*
			 * Add isPartOfProperty to Class
			 */
			// Obtain a reference to values for Has Role, values[4]
			String isPartOfPropertyObject = entry.getValue().get(4).toString();
			System.err.println("IsPartOfPropertyObject: "+isPartOfPropertyObject);
			String newIsPartOfPropertyObject = isPartOfPropertyObject.replace(":Category:","");
			newIsPartOfPropertyObject = newIsPartOfPropertyObject.replaceAll("\"", "");
			//System.err.println("newIsPartOfPropertyObject-MOD: "+newIsPartOfPropertyObject);

			if (!newIsPartOfPropertyObject.equals("NO VALUE")) {
				//System.err.println("VALUE FOUND: "+newIsPartOfPropertyObject);
				//NOTE: newIsPartOfPropertyObject may have more than 1 object value
				String[] isPartOfPropertyObjectValues = newIsPartOfPropertyObject.split(",");
				// Add each hasRoleObect value as a property restriction
				for (String s : isPartOfPropertyObjectValues ) {
					s = s.replaceAll(" ", "_");
					System.err.println("isPartOfPropertyObjectValues: "+s);

					OWLClass propertyObject = factory.getOWLClass(s, pm); 
					OWLClassExpression isPartOfSomeRole = factory.getOWLObjectSomeValuesFrom(isPartOfProperty,
							propertyObject); 
					OWLSubClassOfAxiom ax = factory.getOWLSubClassOfAxiom(clsAMethodB, isPartOfSomeRole);	
					System.err.println("RoleObject: "+propertyObject+"\nAxiom: "+ax);

					// Add the axiom to our ontology 
					AddAxiom addAx = new AddAxiom(ontology, ax);
					manager.applyChange(addAx);
				}
				System.out.println();
			}


			// Save ontology 
			manager.saveOntology(ontology);
		}
	}



	private static void addAnnotations(
			Map<String, ArrayList> termsAndProperties, File owlFile) throws OWLOntologyCreationException, OWLOntologyStorageException {
		System.out.println("** addAnnotations method **");

		// Declare property IRIs
		// Term Label -> rdfs:label
		// Definition -> http://purl.obolibrary.org/obo/IAO_0000115
		// Synonym ->  http://purl.obolibrary.org/obo/IAO_0000118 
		// Defining Citation (similar to xref) ->  http://purl.obolibrary.org/obo/IAO_0000301 

		// Open ontology file
		OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
		OWLOntology ontology = manager.loadOntologyFromOntologyDocument(owlFile);
		OWLDataFactory factory = manager.getOWLDataFactory();
		System.out.println("Loaded ontology: " + ontology);

		// Iterate through termsAndProperties
		for (Entry<String, ArrayList> entry : termsAndProperties.entrySet()) {
			String key = entry.getKey();	
			System.out.println("K:"+key+"\tV:"+entry.getValue());

			PrefixManager pm = new DefaultPrefixManager(
					"http://neurolex.org/wiki/Special:ExportRDF/Category:");
			//String prefix = "http://neurolex.org/wiki/Special:ExportRDF/Category:";
			String newKey = key.replaceAll(" ", "_");
			OWLClass clsAMethodB = factory.getOWLClass(newKey, pm);
			System.err.println("classAMethodB: "+clsAMethodB);

			/**
			 * Add annotations -> Label, Definition, Synonym, Defining Citation
			 */
			String IAO = "http://purl.obolibrary.org/obo/";  
			// Create Annotation Properties
			OWLAnnotationProperty definitionProperty = factory.getOWLAnnotationProperty((IRI.create(IAO
					+ "IAO_0000115")));
			OWLAnnotationProperty synonymProperty = factory.getOWLAnnotationProperty((IRI.create(IAO
					+ "IAO_0000118")));
			OWLAnnotationProperty citationProperty = factory.getOWLAnnotationProperty((IRI.create(IAO
					+ "IAO_0000301")));
			//System.err.println("Annotation Properties (D,S,C): \n"+definitionProperty+"\n"+synonymProperty+"\n"+citationProperty);


			// Get values for Label from text file, values[1]
			String label = entry.getValue().get(1).toString();
			if (!label.equals("NO VALUE")) {
				System.err.println("VALUE FOUND: "+label);
				OWLAnnotation labelAnnotation = factory.getOWLAnnotation(factory.getRDFSLabel(),factory.getOWLLiteral(label));
				OWLAxiom labelAxiom = factory.getOWLAnnotationAssertionAxiom(clsAMethodB.getIRI(), labelAnnotation);
				manager.applyChange(new AddAxiom(ontology, labelAxiom));
			}


			// Get values for Synonym from text file, values[6] 
			String synonym = entry.getValue().get(6).toString();
			synonym = synonym.replaceAll("\"", "");
			System.err.println("Synonym: "+synonym);
			if (!synonym.equals("NO VALUE")) {
				String[] synonymValues = synonym.split(",");
				System.err.println("VALUE FOUND: "+synonymValues);
				for (String syn : synonymValues ) {
					OWLAnnotation synonymAnnotation = factory.getOWLAnnotation(synonymProperty,factory.getOWLLiteral(syn));
					OWLAxiom synonymAxiom = factory.getOWLAnnotationAssertionAxiom(clsAMethodB.getIRI(), synonymAnnotation);
					System.err.println("Synonym Axiom: "+synonymAxiom);
					manager.applyChange(new AddAxiom(ontology, synonymAxiom)); 
				}
			}

			// Get values for Defining Citation from text file, values[7] 
			String citation = entry.getValue().get(7).toString();
			System.err.println("Citation: "+citation);
			if (!citation.equals("NO VALUE")) {
				System.err.println("VALUE FOUND: "+citation);

				OWLAnnotation citationAnnotation = factory.getOWLAnnotation(citationProperty,factory.getOWLLiteral(citation));
				OWLAxiom citationAxiom = factory.getOWLAnnotationAssertionAxiom(clsAMethodB.getIRI(), citationAnnotation);
				System.err.println("Synonym Axiom: "+citationAxiom);
				manager.applyChange(new AddAxiom(ontology, citationAxiom)); 
			}

			// Get values for Definition from text file, values[8]
			String definition = entry.getValue().get(8).toString();
			System.err.println("Definition: "+definition);	
			if (!definition.equals("NO VALUE")) {
				System.err.println("VALUE FOUND: "+definition);
				OWLAnnotation definitionAnnotation = factory.getOWLAnnotation(definitionProperty,factory.getOWLLiteral(definition));
				OWLAxiom definitionAxiom = factory.getOWLAnnotationAssertionAxiom(clsAMethodB.getIRI(), definitionAnnotation);
				manager.applyChange(new AddAxiom(ontology, definitionAxiom));
			}
		}

		// Save ontology 
		manager.saveOntology(ontology);
	}



}





