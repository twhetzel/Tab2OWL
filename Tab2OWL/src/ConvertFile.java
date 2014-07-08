import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
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
		File owlFile = createOWLFile();
		buildClassTree(termsAndProperties, owlFile);
		addClassRestrictions(termsAndProperties, owlFile);
		//addAnnotations(termsAndProperties, owlFile);
	}



	/**
	 * Read in file and generate data structure of terms and their properties 
	 * @return
	 */
	private static Map<String, ArrayList> processTermFile() {
		System.out.println("\n** processTermFile method **");
		BufferedReader br = null;
		Map<String, ArrayList> terms = new HashMap<String, ArrayList>();
		ArrayList<String> list = new ArrayList<String>();

		try {
			String sCurrentLine;
			br = new BufferedReader(new FileReader("/Users/whetzel/Documents/UCSD/NeuroLex/NeuroLex-ElectrophysiologyCategories.txt"));

			while ((sCurrentLine = br.readLine()) != null) {
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
						values[index] = "NO VALUE";
						//System.out.print("NULL-"+values[index]+"\t");
						list.add(values[index]);
					}
					// Create copy to keep values, but not references
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
		IRI ontologyIRI = IRI.create("http://neurolex.org/wiki/");
		// Specify IRIs for ontology imports
		IRI bfoIRI = IRI.create("http://purl.obolibrary.org/obo/bfo.owl");
		IRI iaoIRI = IRI.create("http://purl.obolibrary.org/obo/iao.owl");

		try {
			ontology = manager.createOntology(ontologyIRI);
			OWLDataFactory factory = manager.getOWLDataFactory();
			// Create the document IRI for our ontology
			//IRI documentIRI = IRI.create("/Users/whetzel/Documents/workspace/Tab2OWL/");
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

			// Now save a local copy of the ontology. (
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
	 * @throws OWLOntologyCreationException 
	 * @throws OWLOntologyStorageException 
	 */
	private static void buildClassTree(Map<String, ArrayList> termsAndProperties,
			File owlFile) throws OWLOntologyCreationException, OWLOntologyStorageException {
		System.out.println("\n** buildClassTree method **");
		
		// Open ontology file
		OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
		OWLOntology ontology = manager.loadOntologyFromOntologyDocument(owlFile);
		OWLDataFactory factory = manager.getOWLDataFactory();
		
		// Populate ontology with class hierarchy from termsAndProperties 
		for (Entry<String, ArrayList> entry : termsAndProperties.entrySet()) {
			String key = entry.getKey();	
			//System.out.println("K:"+key+"\tV:"+entry.getValue());

			String prefix = "http://neurolex.org/wiki/Special:ExportRDF/Category:";
			String newKey = key.replaceAll(" ", "_");
			IRI iri = IRI.create(prefix+newKey);
			// Now we create the Class, NOTE: this does not add the Class to the ontology, but creates the Class object
			OWLClass clsAMethodA = factory.getOWLClass(iri);

			// Add parent to Class, value[2] from termsAndProperties Map object
			String parent = entry.getValue().get(2).toString();
			//System.err.println("PARENT: "+parent);
			String newParent = parent.replaceAll(" ", "_");
			OWLClass clsB = factory.getOWLClass(IRI.create(prefix + newParent));
			OWLAxiom axiom = factory.getOWLSubClassOfAxiom(clsAMethodA, clsB);
			AddAxiom addAxiom = new AddAxiom(ontology, axiom);
			// We now use the manager to apply the change
			manager.applyChange(addAxiom);


			/**
			 * Add class restrictions and annotations
			 */
			/* THIS WORKS - TRYING ANOTHER OPTION 
			// Try to add Has Role (object property) as a restriction on a Class
			String IAO = "http://purl.obolibrary.org/obo/";  
			// Create Object Property
			OWLObjectProperty hasRoleProperty = factory.getOWLObjectProperty(IRI.create(IAO
					+ "BFO_0000087"));
			System.err.println("hasRoleProperty: "+hasRoleProperty);

	        // Obtain a reference values for Has Role, values[3]
	        String hasRoleObject = entry.getValue().get(3).toString();
	        System.err.println("HasRoleObject: "+hasRoleObject);
	        String newHasRoleObject = hasRoleObject.replace(":Category:","");
	        newHasRoleObject = newHasRoleObject.replaceAll("\"", "");
	        System.err.println("HasRoleObject-MOD: "+newHasRoleObject);

	        if (!newHasRoleObject.equals("NO VALUE")) {
				System.err.println("VALUE FOUND: "+newHasRoleObject);
				//NOTE: newHasRoleObject may have more than 1 object value
				String[] hasRoleObjectValues = newHasRoleObject.split(",");
				// Add each hasRoleObect value as a property restriction
				for (String s : hasRoleObjectValues ) {
					s = s.replaceAll(" ", "_");
					System.err.println("hasRoleObjectValues: "+s);

					OWLClass roleObject = factory.getOWLClass(IRI.create(prefix + s));
					OWLClassExpression hasPartSomeRole = factory.getOWLObjectSomeValuesFrom(hasRoleProperty,
							roleObject); 
					OWLSubClassOfAxiom ax = factory.getOWLSubClassOfAxiom(clsAMethodA, hasPartSomeRole);	
					System.out.println("RoleObject: "+roleObject+"\nAxiom: "+ax);

					// Add the axiom to our ontology 
					AddAxiom addAx = new AddAxiom(ontology, ax);
					manager.applyChange(addAx);
				}
	        }
			 */

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
	 * 
	 * @param termsAndProperties
	 * @param owlFile
	 * @throws OWLOntologyCreationException 
	 * @throws OWLOntologyStorageException 
	 */
	private static void addClassRestrictions(
			Map<String, ArrayList> termsAndProperties, File owlFile) throws OWLOntologyCreationException, OWLOntologyStorageException {
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

		// Has Role -> http://purl.obolibrary.org/obo/BFO_0000087 (object property)
		// Is Part Of -> http://purl.obolibrary.org/obo/BFO_0000050 (object property, transitive Has Part), see Brain in Uberon for example usage 
		// Has Part -> http://purl.obolibrary.org/obo/BFO_0000051 (object property, transitive Is Part Of)
	

		// Iterate through termsAndProperties
		for (Entry<String, ArrayList> entry : termsAndProperties.entrySet()) {
			String key = entry.getKey();	
			System.out.println("\nK:"+key+"\tV:"+entry.getValue());

			PrefixManager pm = new DefaultPrefixManager(
					"http://neurolex.org/wiki/Special:ExportRDF/Category:");
			String newKey = key.replaceAll(" ", "_");
			OWLClass clsAMethodB = factory.getOWLClass(newKey, pm);
			System.err.println("classAMethodB: "+clsAMethodB);

			
			/*
			 * Add hasRoleProperty to Class
			 */
			// Obtain a reference to values for Has Role, values[3]
			String hasRoleObject = entry.getValue().get(3).toString();
			System.err.println("HasRoleObject: "+hasRoleObject);
			String newHasRoleObject = hasRoleObject.replace(":Category:","");
			newHasRoleObject = newHasRoleObject.replaceAll("\"", "");
			//System.err.println("HasRoleObject-MOD: "+newHasRoleObject);

			if (!newHasRoleObject.equals("NO VALUE")) {
				//System.err.println("VALUE FOUND: "+newHasRoleObject);
				//NOTE: newHasRoleObject may have more than 1 object value
				String[] hasRoleObjectValues = newHasRoleObject.split(",");
				// Add each hasRoleObect value as a property restriction
				for (String s : hasRoleObjectValues ) {
					s = s.replaceAll(" ", "_");
					System.err.println("hasRoleObjectValues: "+s);

					OWLClass roleObject = factory.getOWLClass(s, pm); 
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





