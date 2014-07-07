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
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLClassExpression;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLDeclarationAxiom;
import org.semanticweb.owlapi.model.OWLIndividual;
import org.semanticweb.owlapi.model.OWLObjectProperty;
import org.semanticweb.owlapi.model.OWLObjectPropertyAssertionAxiom;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLOntologyID;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.model.OWLOntologyStorageException;
import org.semanticweb.owlapi.model.OWLSubClassOfAxiom;
import org.semanticweb.owlapi.model.SetOntologyID;
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
		// Add method to add properties to owl File???
		//addClassRestrictions(termsAndProperties, owlFile);
	}

	/**
	 * Read in file and generate data structure of terms and their properties 
	 * @return
	 */
	private static Map<String, ArrayList> processTermFile() {
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
		System.out.println("** createOWLFile method **");
		//Create empty ontology 
		OWLOntology ontology = null;
		File file = new File("owlfiletest.owl");
		OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
		IRI ontologyIRI = IRI.create("http://neurolex.org/wiki/");

		try {
			ontology = manager.createOntology(ontologyIRI);
			// Create the document IRI for our ontology
			IRI documentIRI = IRI.create("/Users/whetzel/Documents/workspace/Tab2OWL/");
			// Set up a mapping, which maps the ontology to the document IRI
			SimpleIRIMapper mapper = new SimpleIRIMapper(ontologyIRI, documentIRI);
			manager.addIRIMapper(mapper);
			System.out.println("Created ontology: " + ontology);
			// Set version IRI, use the date the file contents were exported from NeuroLex
			IRI versionIRI = IRI.create(ontologyIRI + "export06302014");
			OWLOntologyID newOntologyID = new OWLOntologyID(ontologyIRI, versionIRI);
			// Create the change that will set our version IRI
			SetOntologyID setOntologyID = new SetOntologyID(ontology, newOntologyID);
			// Apply the change
			manager.applyChange(setOntologyID);
			System.out.println("Ontology: " + ontology);

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

		// Has Role -> http://purl.obolibrary.org/obo/BFO_0000087 (object property)

		// Open ontology file
		OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
		OWLOntology ontology = manager.loadOntologyFromOntologyDocument(owlFile);
		OWLDataFactory factory = manager.getOWLDataFactory();
		System.out.println("Loaded ontology: " + ontology);

		// Populate ontology with class hierarchy from termsAndProperties 
		for (Entry<String, ArrayList> entry : termsAndProperties.entrySet()) {
			String key = entry.getKey();	
			System.out.println("K:"+key+"\tV:"+entry.getValue());

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

			
			// Try to add Has Role (object property) as a restriction on a Class
			String IAO = "http://purl.obolibrary.org/obo/";  
			// Create Object Property
			OWLObjectProperty hasRoleProperty = factory.getOWLObjectProperty(IRI.create(IAO
					+ "BFO_0000087"));
			System.err.println("hasRoleProperty: "+hasRoleProperty);

	        //OWLClassExpression hasPartSomeRole = factory.getOWLObjectSomeValuesFrom(hasRoleProperty,
	        //		clsAMethodA); 
	        
	        // Obtain a reference to the Head class so that we can specify that Heads have Noses
	        // Get Class object for HasRole, values[3]
	        String hasRoleObject = entry.getValue().get(3).toString();
	        System.err.println("HasRoleObject: "+hasRoleObject);
	        String newHasRoleObject = hasRoleObject.replace(":Category:","");
	        newHasRoleObject = newHasRoleObject.replaceAll("\"", "");
	        System.err.println("HasRoleObject-MOD: "+newHasRoleObject);

	        //OWLClass head = factory.getOWLClass(IRI.create(prefix + newHasRoleObject));
	        // We now want to state that Head is a subclass of hasPart some Nose, to
	        // do this we create a subclass axiom, with head as the subclass and
	        // "hasPart some Nose" as the superclass (remember, restrictions are
	        // also classes - they describe classes of individuals -- they are
	        // anonymous classes).
	        if (!newHasRoleObject.equals("NO VALUE")) {
				System.err.println("VALUE FOUND: "+newHasRoleObject);
				//NOTE: newHasRoleObject may have more than 1 object value
				String[] hasRoleObjectValues = newHasRoleObject.split(",");
				// Add each hasRoleObect value as a property restriction
				for (String s : hasRoleObjectValues ) {
					s = s.replaceAll(" ", "_");
					System.err.println("hasRoleObjectValues: "+s);
					
					OWLClass roleObject = factory.getOWLClass(IRI.create(prefix + s));
					//OWLSubClassOfAxiom ax = factory.getOWLSubClassOfAxiom(roleObject, hasPartSomeRole); //statement is backwards 
					
					OWLClassExpression hasPartSomeRole = factory.getOWLObjectSomeValuesFrom(hasRoleProperty,
							roleObject); 
					OWLSubClassOfAxiom ax = factory.getOWLSubClassOfAxiom(clsAMethodA, hasPartSomeRole);	
					System.out.println("RoleObject: "+roleObject+"\nAxiom: "+ax);
					
					// Add the axiom to our ontology 
					AddAxiom addAx = new AddAxiom(ontology, ax);
					manager.applyChange(addAx);
				}
	        }

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
	 */
	private static void addClassRestrictions(
			Map<String, ArrayList> termsAndProperties, File owlFile) {

		// Add restrictions to the Class 
		// Declare property IRIs
		//String IAO = "http://purl.obolibrary.org/obo/";
		// TODO Create and Add these properties to the ontology
		// Term Label -> rdfs:label
		// Definition -> http://purl.obolibrary.org/obo/IAO_0000115
		// Has Role -> http://purl.obolibrary.org/obo/BFO_0000087 (object property)
		// Is Part Of -> http://purl.obolibrary.org/obo/BFO_0000050 (object property, transitive Has Part), see Brain in Uberon for example usage 
		// Has Part -> http://purl.obolibrary.org/obo/BFO_0000051 (object property, transitive Is Part Of)
		// Synonym ->  http://purl.obolibrary.org/obo/IAO_0000118 
		// Defining Citation (similar to xref) ->  http://purl.obolibrary.org/obo/IAO_0000301      

		/*OWLObjectProperty hasPart = factory.getOWLObjectProperty(IRI.create(IAO
                + "#hasPart"));
        OWLClass nose = factory.getOWLClass(IRI.create(prefix + clsAMethodA));
        // Now create a restriction to describe the class of individuals that
        // have at least one part that is a kind of nose
        OWLClassExpression hasPartSomeNose = factory.getOWLObjectSomeValuesFrom(hasPart,
                nose);
        // Obtain a reference to the Head class so that we can specify that
        // Heads have noses
        OWLClass head = factory.getOWLClass(IRI.create(IAO + "#Head"));
        // We now want to state that Head is a subclass of hasPart some Nose, to
        // do this we create a subclass axiom, with head as the subclass and
        // "hasPart some Nose" as the superclass (remember, restrictions are
        // also classes - they describe classes of individuals -- they are
        // anonymous classes).
        OWLSubClassOfAxiom ax = factory.getOWLSubClassOfAxiom(head, hasPartSomeNose);
        // Add the axiom to our ontology
        AddAxiom addAx = new AddAxiom(ont, ax);
        man.applyChange(addAx);*/


	}


}
