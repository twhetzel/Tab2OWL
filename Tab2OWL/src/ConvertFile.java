import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.commons.logging.Log;
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
	 * Converts Tab file to OWL
	 * 
	 * @param args
	 * @throws OWLOntologyStorageException 
	 * @throws IOException 
	 * @throws OWLOntologyCreationException 
	 */
	public static void main(String[] args) throws OWLOntologyStorageException, IOException, OWLOntologyCreationException {
		final Logger LOGGER = Logger.getLogger(ConvertFile.class.getName()); 
		 
		String timeStamp = new SimpleDateFormat("MM-dd-yyyy_HHmmssms").format(new Date());
		String inputFile = "/Users/whetzel/git/tab2owl/Tab2OWL/datafile/registry_02262015.tsv"; //location and name of input file of data to convert to OWL 
		String ontologyFileName = "scicrunch-registry_"+timeStamp+".owl"; //name of ontology file to create
		
		
		Map<String, ArrayList> termsAndProperties = processTermFile(inputFile, timeStamp);	
		// Create Hashtable of label->ID for classes
		Hashtable<String,String> classIDHashtable = new Hashtable<String,String>();
		classIDHashtable = createClassIDLabelHash(termsAndProperties);

		File owlFile = createOWLFile(ontologyFileName);
		buildClassTree(termsAndProperties, owlFile, classIDHashtable);

		//addClassRestrictions(termsAndProperties, owlFile, classIDHashtable);
		//addAnnotations(termsAndProperties, owlFile, classIDHashtable);
	}


	/**
	 * Read in file and generate data structure of terms and their properties 
	 * @return
	 * @throws IOException 
	 */
	private static Map<String, ArrayList> processTermFile(String inputFile, String timeStamp) throws IOException {
		System.out.println("\n** processTermFile method **");		
		int lineCount = 0;
		BufferedReader br = null;
		ArrayList<String> list = new ArrayList<String>();
		Map<String, ArrayList> terms = new HashMap<String, ArrayList>();
		
		//Create error log file
		String errorFileName = "./errorlogs/errorlog_"+timeStamp+".txt";
		File errorFile = new File(errorFileName);
		if (!errorFile.exists()) {
			errorFile.createNewFile();
		}
		FileWriter fw = new FileWriter(errorFile.getAbsoluteFile());
		BufferedWriter bw = new BufferedWriter(fw);
				
		// Process data file 
		try {
			File file = new File(inputFile);
			String sCurrentLine;
			br = new BufferedReader(new FileReader(file));
			
			while ((sCurrentLine = br.readLine()) != null) {
				lineCount++;
				if (lineCount > 1 ) { //skip header line
					System.out.println("**NEW-LINE("+lineCount+"): "+sCurrentLine);
					//Replace any newline breaks so that correct number of columns are found
					sCurrentLine = sCurrentLine.replaceAll("\r\n", "").replaceAll("<br></br>", "");
					
					// Split line on tab separator between columns in spreadsheet
					String[] values = sCurrentLine.split("\t", -1); // The -1 indicates to not truncate line on empty values

					// Check for valid e_uid
					if (checkValues(values)) {
						//Next, check proper number of column
						if (checkColumnCount(values)) {
							//Now add values from properly formatted lines to ArrayList
							for (int index = 0; index < values.length; index++) {
								list.add(values[index]);
						
								// Create a copy of the ArrayList to keep values, but not references so it can be cleared before reading the next line in while loop 
								ArrayList<String> copy = new ArrayList<String>();
								copy.addAll(list);
								// Put values in a HashMap keyed on the ID, value[0] OR    label, value[1]
								terms.put(values[0], copy ); //was copy instead of list 
							}	
						}
						else {
							bw.write("LINECOUNT: "+lineCount+" Incorrect column count for: "+values[0]+" Total columns: "+values.length+"\n");
							/*for (int col = 0; col<values.length; col++) {
								bw.write("Column value("+col+"): "+values[col]+"\n");
							}
							bw.write("\n");*/
						}
					}
					else {
						bw.write("LINECOUNT: "+lineCount+" Invalid e_uid for: "+values[0]+"\n");
					}
					// Clear initial ArrayList to prepare for next line in file within while loop
					list.clear();
				}
				else {
					System.out.println("Skipping header line....");
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			try {
				if (br != null) br.close();
			} catch (IOException ex) {
				ex.printStackTrace();
			}
		}
		bw.close();
		return terms;
	}

	
	/**
	 * Check that the line starts with a valid e_uid
	 * @param values
	 * @return
	 */
	private static boolean checkValues(String[] values) {
		boolean isValidEUID = false;
		if ((values[0].contains("nif")) || (values[0].contains("nlx")) || (values[0].contains("OMICS"))
				|| (values[0].contains("SciEx")) || (values[0].contains("SciRes")) || (values[0].contains("rid"))
				|| (values[0].contains("birnlex")) || (values[0].contains("GAZ")) || (values[0].contains("scr"))
				|| (values[0].contains("Nif"))) { 			
			//System.out.println("Valid Resource ID Found");
			isValidEUID = true;
		}
		else {
			System.err.println("Valid ResourceID not found");
		}	
		return isValidEUID;
	}
	
	/**
	 * Check that line has correct number of columns
	 * @param values
	 * @return
	 * @throws IOException 
	 */
	private static boolean checkColumnCount(String[] values) {
		boolean isCorrectColumnCount = false;	
		ArrayList<String> list = new ArrayList<String>();

		for (int index = 0; index < values.length; index++) {
			if (values[index].length() > 0) { // Check that the array index contains a value
				list.add(values[index]);	
			}
			else {
				System.err.print("No values for: "+values[index]+"\n");
			}
		}

		// Confirm that all rows have expected number of columns
		if (list.size() == 11) { 
			//System.err.println("ARR-SIZE: "+list.size());
			System.out.println("RESOURCE-ID: "+values[0]+"\tLIST: "+list);
			isCorrectColumnCount = true;	
		}
		else {
			System.out.println("INCORRECT COLUMN COUNT FOR RESOURCE-ID: "+values[0]+"\tLIST: "+list);
			isCorrectColumnCount = false;
		}

		return isCorrectColumnCount;
	}


	/**
	 * Build Hashtable of term labels (hash key) and term Ids (hash value) 
	 * in order to swap out label for Id in term IRI 
	 */
	private static Hashtable<String, String> createClassIDLabelHash(Map<String, ArrayList> termsAndProperties) {
		System.out.println("\n** createClassIDLabelHash method **");
		// Populate Term label<->ID hashtable from termsAndProperies using the term label (values[1]) as the key and ID (values[0]) as the value
		Hashtable<String,String> hashtable = new Hashtable<String,String>();
		int count = 0;

		for (Entry<String, ArrayList> entry : termsAndProperties.entrySet()) {
			String key = entry.getValue().get(1).toString(); //add term label as key
			String value = entry.getKey(); //e_uid as value
			hashtable.put(key, value);  
		}
		System.out.println("Size of ClassIDLabel Hash with only values from data file: "+hashtable.size());
		
		
		/* Loop through termsAndProperties to find root terms based on assumption
		 * that every Parent label should exist as a key in the hashtable ... but 'Granting agency' does not exist in data file, assumption is no longer correct 
		 */
		
		//Column resource_type should be parent term, (limit to only university OR government granting agency) -> use entire Registry information now
		Map<String, ArrayList> termsAndPropertiesRootTerms = new HashMap<String,ArrayList>();	
		System.out.println("** Find Root terms ");
		for (Entry<String, ArrayList> entry : termsAndProperties.entrySet()) {
			//TODO Re-do variable mapping for Institution data file???
			String key =  entry.getValue().get(1).toString(); // Term label
			//TODO This assignment parentLabel=get(6) does not work for entries where supercategory=Resource
			//String parentLabel = entry.getValue().get(6).toString();  //Parent label -> resource_type column from DISCO, may contain multiple values, but university and gov. granting are disjoint
			String parentLabel = entry.getValue().get(9).toString(); //Use Supercategory value instead as parent
			
			//TODO Check if this is still needed since now using entire registry information
			if (parentLabel.contains("university")) {
				parentLabel = "university";
			}
			if (parentLabel.contains("government granting agency")) {
				parentLabel = "government granting agency";
			}
			//System.out.println("\n** TermLabel: "+key+" - ParentLabel: "+parentLabel);
			
			
			if (!hashtable.containsKey(parentLabel)) {
				count++;
				// Format parent label for use in query to Wiki 
				String newParentLabel = "Category:"+parentLabel.replaceAll(" ", "_");
				System.out.println("Parent label is Not in Hash. Use \""+newParentLabel+"\" to query NeuroLex for ID.");
				
				//Account for bad Neurolex pages with title Uncurated and NULL
				if (newParentLabel.equals("Category:Uncurated") || newParentLabel.equals("Category:NULL")) {
					System.out.println("Bad page name: "+newParentLabel);
				}
				else {
				// Use WikiAPI to get ID for Parent
				String parentIDFromWiki = getParentIdFromWikiAPI(newParentLabel); //Since data was queried with parents known to be in NLex, a value is always returned
				// Trim whitespace from value
				parentIDFromWiki = parentIDFromWiki.trim();
				//System.out.println("** ParentIDFromWiki: "+parentIDFromWiki);
				System.out.println("ParentLabel: "+parentLabel+" ParentID From Neurolex Wiki: "+parentIDFromWiki+" RootTermCount: "+count);
				hashtable.put(parentLabel, parentIDFromWiki);
				
				// Add Thing to hashtable
				String thingIRI = "http://www.w3.org/2002/07/owl#Thing";
				String thing = "Thing";
				hashtable.put(thing, thing); //new code 02/02/2015
				
				// Using all of Registry, need to get more values from Wiki than only parent ID and label
				ArrayList<String> parentMetadataFromWiki = getAllParentMetadataFromWiki(newParentLabel);
				System.out.println("PID: "+parentMetadataFromWiki); 
				termsAndPropertiesRootTerms.put(parentIDFromWiki, parentMetadataFromWiki);   
				
				
				// Also, try adding parent Term Label and Id to termsAndProperties Map --> Replace with parentMetadataFromWiki 
				/*ArrayList<String> parentMetadata = new ArrayList<String>(Arrays.asList(parentIDFromWiki,parentLabel,"null",
						"null","null","null",thing,"null", "null",thing,"null")); //changed position9 to thing
				System.out.println("PID: "+parentMetadata); //parentMetadata.get(6));
				termsAndPropertiesRootTerms.put(parentIDFromWiki, parentMetadata);*/   
			}
			}
			
			
			//Check if hashtable contains parentLabel as a key, if not && parentLabel is not null use Wiki API to get it
			//IF parentLabel is null then...
			/*if ((!hashtable.containsKey(parentLabel)) && (!parentLabel.equals("(null)"))) {
				System.out.println("Parent label is Not Null");
				count++;
				// Format parent label for use in query to Wiki 
				String newParentLabel = "Category:"+parentLabel.replaceAll(" ", "_");
				System.out.println("Null/No ID Found. Use \""+newParentLabel+"\" to query NeuroLex for ID.");
				// Use WikiAPI to get ID for Parent
				String parentIDFromWiki = getParentIdFromWikiAPI(newParentLabel);
				// Trim whitespace from value
				parentIDFromWiki = parentIDFromWiki.trim();
				//System.out.println("** ParentIDFromWiki: "+parentIDFromWiki);
				System.out.println("ParentLabel: "+parentLabel+" ParentID: "+parentIDFromWiki+" RootTermCount: "+count);
				hashtable.put(parentLabel, parentIDFromWiki);
				
				// Also, try adding parent Term Label and Id to termsAndProperties Map
				String thingIRI = "http://www.w3.org/2002/07/owl#Thing";
				String thing = "Thing";
				
				//TODO Check order of items added in ArrayList since data input file is different
				ArrayList<String> parentMetadata = new ArrayList<String>(Arrays.asList("NO VALUE",parentLabel,thing
							,"NO VALUE","NO VALUE",parentIDFromWiki,"NO VALUE","NO VALUE","NO VALUE")); 
				System.out.println("PID: "+parentMetadata.get(5));
				termsAndPropertiesRootTerms.put(parentLabel, parentMetadata);   
			}
			//Handle case where parentLabel is not in the hashtable AND parentLabel is null -> use resource_type(6) as parentLabel
			else {
				count++;
				String resourceType = entry.getValue().get(6).toString();
				String resourceTypeWikiTerm = "Category:"+entry.getValue().get(6).toString();
				System.out.println("RT:"+resourceType);
				// Use WikiAPI to get ID for Parent
				String resourceTypeIDFromWiki = getParentIdFromWikiAPI(resourceTypeWikiTerm); //OR Hardcode if/else statemnet for University or Government granting agency as 2 possible parent options
				System.out.println("ParentLabel-RT:"+resourceType+" ParentID:"+resourceTypeIDFromWiki+" RootTermCount: "+count);
				hashtable.put(entry.getValue().get(6).toString(), resourceTypeIDFromWiki);
				
				// Also, try adding parent Term Label and Id to termsAndProperties Map ... as above
				
				//TODO Check order of items added in ArrayList since data input file is different
				ArrayList<String> parentMetadata = new ArrayList<String>(Arrays.asList("NO VALUE",resourceType,resourceType
							,"NO VALUE","NO VALUE",resourceTypeIDFromWiki,"NO VALUE","NO VALUE","NO VALUE")); 
				System.out.println("PID: "+parentMetadata.get(5));
				termsAndPropertiesRootTerms.put(parentLabel, parentMetadata); 
			}*/		
		}
		// Add contents of "RootTerms" HashMap to all terms HashMap
		System.out.println("termsAndPropertiesRootTerms "+termsAndPropertiesRootTerms);
		termsAndProperties.putAll(termsAndPropertiesRootTerms);	
		System.out.println("Total size of Hashtable: "+hashtable.size());
		return hashtable;
	}
	
	


	/**
	 * Build OWL file
	 * @param termsAndProperties
	 * @return 
	 * @throws OWLOntologyStorageException 
	 * @throws IOException 
	 */
	private static File createOWLFile(String ontologyFileName) throws OWLOntologyStorageException, IOException {
		System.out.println("\n** createOWLFile method **");
		//Create empty ontology 
		OWLOntology ontology = null;
		//File file = new File("owlfile_allSciCrunch-TESTING.owl"); //ontology file to write to
		File file = new File(ontologyFileName);
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
			IRI documentIRI = IRI.create("/Users/whetzel/git/tab2owl/Tab2OWL/owlfiles");  //Local Git repo

			// Set up a mapping, which maps the ontology to the document IRI
			SimpleIRIMapper mapper = new SimpleIRIMapper(ontologyIRI, documentIRI);
			manager.addIRIMapper(mapper);
			System.out.println("Created ontology: " + ontology);

			// Set version IRI, use the date the file contents were exported from NeuroLex
			//TODO Consider passing version IRI tag (NeuroLexExport01302015) as a variable to this method
			IRI versionIRI = IRI.create(ontologyIRI + "NeuroLexExport01302015");
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
	 * Build Class tree hierarchy 
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
		int classCount = 0;

		// Populate ontology with class hierarchy from termsAndProperties 
		for (Entry<String, ArrayList> entry : termsAndProperties.entrySet()) {
			classCount++;
			System.out.println("Class count: "+classCount);
			
			String key = entry.getKey();	
			String termLabel = entry.getValue().get(1).toString(); // get(1) is resource_name value
			//String termLabel = entry.getValue().get(9).toString(); // get(9) is supercategory value, which is the parent
			System.out.println("\nKey:"+key+"\tLabel:"+termLabel+"\tValues:"+entry.getValue());
 
			String prefix = "http://uri.neuinfo.org/nif/nifstd/";
			String thingPrefix ="http://www.w3.org/2002/07/owl#";
			//String newKey = key.replaceAll(" ", "_"); // Remove since using ID in IRI 

			//Use classIDHashtable to get ID instead of label to create Class IRI
			String id = classIDHashtable.get(termLabel); 
			System.out.println("** ID: "+id
					+"\t** Label: "+termLabel);

			IRI iri = IRI.create(prefix+id);
			// Now we create the Class, NOTE: this does not add the Class to the ontology, but creates the Class object
			OWLClass clsAMethodA = factory.getOWLClass(iri);
			System.out.println("clsA: "+clsAMethodA);
			
			
			// Add Parent to Class, value[2orig] from termsAndProperties Map object
			String parentLabel = entry.getValue().get(9).toString(); //Change to use supercategory, which is the parent 
			//Normalize parent label to only convert to only university OR government granting agency
			
			//These entries are now in the file so might not be needed 
			/*if (parentLabel.contains("university")) {
				parentLabel = "university";
				System.out.println("ParTest:"+parentLabel);
			}
			if (parentLabel.contains("government granting agency")) {
				parentLabel = "government granting agency";
				System.out.println("ParTest:"+parentLabel);
			}
			String childKey = entry.getKey();
			System.out.println("ParentLabel:"+parentLabel+" ChildKey:"+childKey);*/
			
			
			// Get ID for parent from hashtable
			String parentId = classIDHashtable.get(parentLabel); //classIDHashtable.get(parent); //classIDHashtable is now key=termLabel, value-e_uid
			System.out.println("** ParentID: "+parentId+"\t** Parent Label: "+parentLabel);
			//IF parentID is null, then need to get ID ... if Null, then ID was not added to hashtable 
					
			// TODO Remove after more testing that moving this test to createClassIDLabelHash is working
			if (parentId == null) {
				parentId = parentLabel.replaceAll(" ", "_");
				parentId = "Category:"+parentId;
				System.out.println("Null/No ID Found. Use \""+parentId+"\" to query NeuroLex for ID.");
				// Use WikiAPI to get ID for Parent
				String parentIDFromWiki = getParentIdFromWikiAPI(parentId);
				// Trim whitespace from value 
				parentId = parentIDFromWiki.trim();
				System.out.println("** ParentIDFromWiki: "+parentId);
				// Update null value in hashtable
				classIDHashtable.put(parentLabel, parentId);  //Then add key and value back to hashtable 
			}
			
			OWLClass clsB;
			// Check if Parent is owl:Thing
			String thing = "Thing";
			if (parentId.equals(thing)) {
				System.out.println("Parent is owl:Thing for this term");
				clsB = factory.getOWLClass(IRI.create(thingPrefix + parentId));	
			}
			else {
				clsB = factory.getOWLClass(IRI.create(prefix + parentId));
			}
			
			OWLAxiom axiom = factory.getOWLSubClassOfAxiom(clsAMethodA, clsB);
			AddAxiom addAxiom = new AddAxiom(ontology, axiom);
			System.out.println(addAxiom); //TODO Sort out hierarchy error here 
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
		//System.out.println("** Query term: "+newParent);
		String content = getPageContent(newParent);  //test.getPageContent(newParent); 
		//System.out.println("Wiki page content:"+content);

		// Print only line with Term ID (|Id=)
		String[] contentLines = content.split("\\r?\\n");
		//System.out.println("ContentLines:\""+contentLines[0]+"\"");

		String parentId = null;
		// Account for pages with no content 
		//System.out.println(contentLines[0]);
		if (contentLines[0] == null || contentLines[0].length() == 0) { //Add condition to detect a redirect page
			String parentIdValues[] = newParent.split(":");
			parentId = parentIdValues[1];
		}
		else {
			String termIDPattern = "|Id=";
			for (String line : contentLines) {
				//System.out.println("Line: "+line);
				
				// Handle pages that have a Redirect 
				if (line.contains("#REDIRECT")) {
					System.out.println("-> Page was redirected");
					//parse redirect page name and make call again
					String newPageTitle = line.replaceAll("#REDIRECT\\[\\[\\:", "").replaceAll("]]", ""); 
					String contentRedirect = getPageContent(newPageTitle);
					String[] contentLinesRedirect = contentRedirect.split("\\r?\\n");
					for (String lineRedirect : contentLinesRedirect) {
						//System.out.println("LineRedirect: "+lineRedirect);
						
						if (lineRedirect.contains(termIDPattern)) {
							//System.out.println("TermId: "+lineRedirect);
							String[] idLineRedirect = lineRedirect.split("=");
							//System.out.println("TermID:\""+idLineRedirect[1]+"\"");
							parentId = idLineRedirect[1];
							System.out.println("** TermID: "+parentId);
						}
					}
				}			
				if (line.contains(termIDPattern)) {
					//System.out.println("TermId: "+line);
					String [] idLine = line.split("=");
					parentId = idLine[1];
					System.out.println("** TermID: "+parentId);
				}
			}
		}
		return parentId;
	}

	public static String getPageContent(String title) {
		String content = null;
		String[] listOfTitleStrings = { title }; 
		//System.out.println("Page Title for Query: "+listOfTitleStrings[0]);
		String host =  "http://neurolex.org/w/api.php";

		User user = new User("Whetzel", "neurolex", host);  
		List<Page> listOfPages =  user.queryContent(listOfTitleStrings);  //user.queryCategories(listOfTitleStrings);

		for (Page page : listOfPages) {
			//System.out.println("PageTitle: "+page.getTitle());
			content = page.getCurrentContent();
			//System.out.println("------------------"+content+"-----------------");
			if(content != null)
				break;
			// Account for pages that do not exist .. this doesn't seem to be called?
			else  {
				//System.out.println("Content1: "+content);
				content = "|Id="+page.getTitle();
				//System.out.println("Content2: "+content);
			}
		}
		return content;
	}

	
	/**
	 * Get parent term metadata from neurolex wiki 
	 * @param newParentLabel
	 * @return
	 */
	private static ArrayList<String> getAllParentMetadataFromWiki(String newParentLabel) {
		String[] attributes = {"parentId", "resource_name", "abbrev", "definition", "curationstatus", 
				"url", "resource_type", "parent_organization", "date_updated", "supercategory", "synonym"};
		Map<String,String> attributeValueMap = new HashMap<String,String>();		
		ArrayList<String> allParentMetadata = new ArrayList<String>();	
		
		//List fields of interest to collect for parentMetadata -> see Excel data file 
		String parentId = null;
		String termIDPattern = "|Id=";
		
		// Get Neurolex page content
		String content = getAllPageContent(newParentLabel);  
		String[] contentLines = content.split("\\r?\\n");
				
		// Account for pages with no content 
		if (contentLines[0] == null || contentLines[0].length() == 0) { //Add condition to detect a redirect page
			String parentIdValues[] = newParentLabel.split(":");
			parentId = parentIdValues[1]; //For getAllParentMetadata may have to re-think this logic
		}
		else {	
			for (String line : contentLines) {
				System.out.println("Line: "+line);			
				if (line.contains("#REDIRECT")) {  //Handle pages that have a Redirect 
					//Parse redirect page name and query Neurolex again
					String newPageTitle = line.replaceAll("#REDIRECT\\[\\[\\:", "").replaceAll("]]", ""); 
					String contentRedirect = getAllPageContent(newPageTitle);
					String[] contentLinesRedirect = contentRedirect.split("\\r?\\n");
					
					for (String lineRedirect : contentLinesRedirect) {
						//System.out.println("LineRedirect: "+lineRedirect);
						Map<String, String> tempMap = matchContentLines(line);  //Added in matchContentLines() for redirected pages 
						attributeValueMap.putAll(tempMap);	
					}
				}
				Map<String, String> tempMap = matchContentLines(line);
				attributeValueMap.putAll(tempMap);			
			}  
				
			// Add variables to proper place in ArrayList 
			System.out.println("AttrValueMap Size: "+attributeValueMap.size());
			for (int i = 0; i<attributes.length; i++) {
				String value = null;
				boolean valueExists = false;
				System.out.println("VarName: "+attributes[i]);
				for (Entry<String, String> entry : attributeValueMap.entrySet()) {
					if (attributes[i].equals(entry.getKey())) {
						//System.out.println("VarName: "+attributes[i]+" *** Var From Map: "+entry.getKey() +" *** Value From Map: "+entry.getValue()+" ***");
						value = entry.getValue();
						valueExists = true;
						break;
					}
				}
				if (valueExists) {
					allParentMetadata.add(value);
				}
				else {
					allParentMetadata.add("null");
				}
			}
			// DEBUG - Print all values 
			for (String str : allParentMetadata) {
				//System.out.println("Values in allParentMetadata: "+str);
			}
		}
		return allParentMetadata;
	}
	
	
	
	
	/**
	 * Get Neurolex page information for query term
	 * @param newParentLabel
	 * @return
	 */
	private static String getAllPageContent(String newParentLabel) {
		String content = null;
		String[] listOfTitleStrings = { newParentLabel }; 
		//System.out.println("Page Title for Query: "+listOfTitleStrings[0]);
		String host =  "http://neurolex.org/w/api.php";

		User user = new User("Whetzel", "neurolex", host);  
		List<Page> listOfPages =  user.queryContent(listOfTitleStrings);  //user.queryCategories(listOfTitleStrings);

		for (Page page : listOfPages) {
			String pageTitle = page.getTitle();
			System.out.println("PageTitle: "+pageTitle);
			content = page.getCurrentContent();
			content = pageTitle+"\n"+content;  //Preprend pageTitle to contents of page in order to use as term metadata 
			//System.out.println("------------------"+content+"-----------------");
			if(content != null)
				break;
			// Account for pages that do not exist .. this doesn't seem to be called?
			//else  {
				//System.out.println("Content1: "+content);
				//content = "|Id="+page.getTitle();
				//System.out.println("Content2: "+content);
			//}
		}
		return content;
	}
	
	
	/**
	 * Find what variable the line has a match for
	 * @param line
	 * @return
	 */
	private static Map<String,String> matchContentLines(String line) {
		//System.out.println("** matchContentLines() **");
		String match = null;
		String termIDPattern = "|Id=";
		String labelPattern = "Category:";  
		String abbrevPattern = "|ABBREVPATTERN=";  //update with correct pattern
		String definitionPattern = "|Definition=";
		String curationStatusPattern = "|CurationStatus=";
		
		String isPartOfPattern = "|Is part of=";
		
		String resourceTypePattern = "|RESOURCETYPE="; //update with correct pattern
		String parentOrganizationPattern = "|PARENTORGPATTERN=";
		String dateCreatedPattern = "|Created=";
		String superCategoryPattern = "|SuperCategory=";
		String synonymPattern = "|synonym=";
		
		Map<String, String> variableMap = new HashMap<String, String>();
		//e_uid	resource_name	abbrev	description	curationstatus	url	resource_type	parent_organization	date_updated	supercategory	synonym
		
		if (line.contains(termIDPattern)) {
			String [] idLine = line.split("=");
			match = idLine[1];
			variableMap.put("parentId", match);
		}	
		if (line.startsWith(labelPattern)) {  
			String [] idLine = line.split(":");
			match = idLine[1];
			variableMap.put("resource_name", match);	
		}
		if (line.contains(abbrevPattern)) {
			String [] idLine = line.split("=");
			match = idLine[1];
			variableMap.put("abbrev", match);
		}
		if (line.contains(definitionPattern)) {
			String [] idLine = line.split("=");
			match = idLine[1];
			variableMap.put("definition", match);
		}
		if (line.contains(curationStatusPattern)) {
			String [] idLine = line.split("=");
			match = idLine[1];
			variableMap.put("curationstatus", match);
		}
		if (line.contains(resourceTypePattern)) {
			String [] idLine = line.split("=");
			match = idLine[1];
			variableMap.put("resource_type", match);
		}
		if (line.contains(parentOrganizationPattern)) {
			String [] idLine = line.split("=");
			match = idLine[1];
			variableMap.put("parent_organization", match);
		}
		if (line.contains(dateCreatedPattern)) {
			String [] idLine = line.split("=");
			match = idLine[1];
			variableMap.put("date_updated", match);
		}
		if (line.contains(superCategoryPattern)) {
			String [] idLine = line.split("=");
			match = idLine[1];
			variableMap.put("supercategory", match);
		}
		if (line.contains(synonymPattern)) {
			String [] idLine = line.split("=");
			match = idLine[1];
			variableMap.put("synonym", match);
		}
		//DEBUG Print out variableMap
		for (Entry<String, String> entry : variableMap.entrySet()) {
	        String key = entry.getKey().toString();
	        String value = entry.getValue();
	        //System.out.println("Key: " + key + " Value: " + value );
	    }		
		return variableMap;
	}


	/**
	 * Add Class restrictions/Object Properties  
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

			PrefixManager pm = new DefaultPrefixManager("http://uri.neuinfo.org/nif/nifstd/");
			//String newKey = key.replaceAll(" ", "_");
			// Get ID from hashtable
			//String classId = classIDHashtable.get(key);
			String parentLabel = entry.getValue().get(1).toString();
			String classId = classIDHashtable.get(parentLabel);
			OWLClass clsAMethodB = factory.getOWLClass(classId, pm);
			System.err.println("classAMethodB: "+clsAMethodB);


			/*
			 * Add hasRoleProperty to Class
			 */
			// Obtain a reference to values for Has Role, values[3]
			/*String hasRoleObject = entry.getValue().get(3).toString();
			//System.err.println("HasRoleObject: "+hasRoleObject);
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

					// If class Id not in Hashtable, call Wiki API to get ID
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
			}*/


			/*
			 * Add isPartOfProperty to Class
			 */
			// Obtain a reference to values for Parent Organization, values[7]
			String isPartOfPropertyObject = entry.getValue().get(7).toString();
			System.err.println("IsPartOfPropertyObject: "+isPartOfPropertyObject);
			String newIsPartOfPropertyObject = isPartOfPropertyObject.replace(":Category:","");
			newIsPartOfPropertyObject = newIsPartOfPropertyObject.replaceAll("\"", "");
			System.err.println("newIsPartOfPropertyObject-MOD: "+newIsPartOfPropertyObject);

			if (!newIsPartOfPropertyObject.contains("null")) {
				//System.err.println("VALUE FOUND: "+newIsPartOfPropertyObject);
				//NOTE: newIsPartOfPropertyObject may have more than 1 object value
				String[] isPartOfPropertyObjectValues = newIsPartOfPropertyObject.split(","); //Need to trim whitespace
				// Add each hasRoleObect value as a property restriction
				for (String s : isPartOfPropertyObjectValues ) {
					String trimmedS = s.trim();
					String sId = classIDHashtable.get(trimmedS);

					if (sId == null) {
						s = trimmedS.replaceAll(" ", "_");
						s = "Category:"+s;
						System.out.println("Null/No ID Found. Use \""+s+"\" to query NeuroLex for ID.");
						// Use WikiAPI to get ID for Parent
						String parentIDFromWiki = getParentIdFromWikiAPI(s);
						System.out.println(parentIDFromWiki);
						// Trim whitespace from value 
						sId = parentIDFromWiki.trim();
						System.out.println("** ParentIDFromWiki: "+sId);
						// Update null value in hashtable
						classIDHashtable.put(s, sId);  //Then add key and value back to hashtable 

					}
					System.err.println("isPartOfPropertyObjectValue: "+s+"Id: "+sId);

					OWLClass propertyObject = factory.getOWLClass(sId, pm); 
					OWLClassExpression isPartOfSomeRole = factory.getOWLObjectSomeValuesFrom(isPartOfProperty,
							propertyObject); 
					OWLSubClassOfAxiom ax = factory.getOWLSubClassOfAxiom(clsAMethodB, isPartOfSomeRole);	
					System.err.println("Parent Organization: "+propertyObject+"\nAxiom: "+ax);

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


/**
 * Add Annotation Properties 
 * @param termsAndProperties
 * @param owlFile
 * @param classIDHashtable
 * @throws OWLOntologyCreationException
 * @throws OWLOntologyStorageException
 */
	private static void addAnnotations(
			Map<String, ArrayList> termsAndProperties, File owlFile, Hashtable<String, String> classIDHashtable) throws OWLOntologyCreationException, OWLOntologyStorageException {
		System.out.println("\n** addAnnotations method **");

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

			// Use classIDHashtable to get ID for term IRI 
			PrefixManager pm = new DefaultPrefixManager("http://uri.neuinfo.org/nif/nifstd/");
			//String newKey = key.replaceAll(" ", "_");
			
			// Get ID from hashtable
			String termLabel = entry.getValue().get(1).toString();
			if (termLabel.contains("university")) {
				termLabel = "university";
			}
			if (termLabel.contains("government granting agency")) {
				termLabel = "government granting agency";
			}
			if (termLabel.contains("Thing")) {
				termLabel = "Thing";
			}
			
			// TEST - Get owl:Thing Class from ontology file
			String owlId = "Thing";
			PrefixManager thingPrefix = new DefaultPrefixManager("http://www.w3.org/2002/07/owl#");
			OWLClass clsAMethodBOWL = factory.getOWLClass(owlId, thingPrefix);
			//System.out.println("*** classAMethodBOWLThing: "+clsAMethodBOWL);
			
			
			String classId = classIDHashtable.get(termLabel);
			//String classId = classIDHashtable.get(key);
			System.out.println("ParentLabel-"+termLabel+" classId:"+classId);
			OWLClass clsAMethodB = factory.getOWLClass(classId, pm);
			System.out.println("classAMethodB: "+clsAMethodB);
			//String newKey = key.replaceAll(" ", "_");
			//OWLClass clsAMethodB = factory.getOWLClass(newKey, pm);
			//System.err.println("classAMethodB: "+clsAMethodB);

					
			/**
			 * Add annotations -> Label, Definition, Synonym, Defining Citation
			 */
			String IAO = "http://purl.obolibrary.org/obo/"; 
			String oboInOwl = "http://www.geneontology.org/formats/oboInOwl#";
			String OBO_annotation_properties = "http://ontology.neuinfo.org/NIF/Backend/OBO_annotation_properties.owl#"; 
			
			// Create Annotation Properties
			OWLAnnotationProperty definitionProperty = factory.getOWLAnnotationProperty((IRI.create(IAO
					+ "IAO_0000115")));
			//OWLAnnotationProperty synonymProperty = factory.getOWLAnnotationProperty((IRI.create(IAO+ "IAO_0000118")));
			OWLAnnotationProperty synonymProperty = factory.getOWLAnnotationProperty((IRI.create(oboInOwl
					+ "hasExactSynonym")));
			OWLAnnotationProperty abbrevProperty = factory.getOWLAnnotationProperty((IRI.create(OBO_annotation_properties + "abbrev")));
			OWLAnnotationProperty curationStatusProperty = factory.getOWLAnnotationProperty(IRI.create(IAO + "IAO_0000114"));
			OWLAnnotationProperty urlProperty = factory.getOWLAnnotationProperty(IRI.create(oboInOwl + "xref"));
			OWLAnnotationProperty citationProperty = factory.getOWLAnnotationProperty((IRI.create(IAO
					+ "IAO_0000301")));		
			//System.err.println("Annotation Properties (D,S,C): \n"+definitionProperty+"\n"+synonymProperty+"\n"+citationProperty);


			// Get values for Label from text file, values[1]
			String label = entry.getValue().get(1).toString();
			if (!label.equals("NO VALUE")) {
				System.out.println("Label Values: "+label);
				OWLAnnotation labelAnnotation = factory.getOWLAnnotation(factory.getRDFSLabel(),factory.getOWLLiteral(label));
				OWLAxiom labelAxiom = factory.getOWLAnnotationAssertionAxiom(clsAMethodB.getIRI(), labelAnnotation);
				System.out.println("Label Axiom: "+labelAxiom);
				manager.applyChange(new AddAxiom(ontology, labelAxiom));
			}

			//Get values for abbreviation 
			String abbrev = entry.getValue().get(2).toString();
			if (!abbrev.contains("null")) {
				System.out.println("Abbrev Values: "+abbrev);
				OWLAnnotation abbrevAnnotation = factory.getOWLAnnotation(abbrevProperty,factory.getOWLLiteral(abbrev));
				OWLAxiom abbrevAxiom = factory.getOWLAnnotationAssertionAxiom(clsAMethodB.getIRI(), abbrevAnnotation);
				System.out.println("Abbrev Axiom: "+abbrevAxiom);
				manager.applyChange(new AddAxiom(ontology, abbrevAxiom));
			}
			
			// Get values for Definition from text file, values[3]
			String definition = entry.getValue().get(3).toString();
			//System.err.println("Definition: "+definition);	
			if (!definition.contains("null")) {
				System.out.println("Definition Values: "+definition);
				OWLAnnotation definitionAnnotation = factory.getOWLAnnotation(definitionProperty,factory.getOWLLiteral(definition));
				OWLAxiom definitionAxiom = factory.getOWLAnnotationAssertionAxiom(clsAMethodB.getIRI(), definitionAnnotation);
				System.out.println("Definition Axiom: "+definitionAxiom);
				manager.applyChange(new AddAxiom(ontology, definitionAxiom));
			}
			
			//Get values for curation status
			String curationStatus = entry.getValue().get(4).toString();
			if (!curationStatus.contains("null")) {
				System.out.println("Curation status Values: "+curationStatus);
				OWLAnnotation curationStatusAnnotation = factory.getOWLAnnotation(curationStatusProperty, factory.getOWLLiteral(curationStatus));
				OWLAxiom curationStatusAxiom = factory.getOWLAnnotationAssertionAxiom(clsAMethodB.getIRI(), curationStatusAnnotation);
				System.out.println("CurationStatus Axiom: "+curationStatusAxiom);
				manager.applyChange(new AddAxiom(ontology, curationStatusAxiom));
			}
			
			// Get values for URL from text file, values[5]
			String url = entry.getValue().get(5).toString();
			if (!url.contains("null")) {
				System.out.println("URL Values: "+url);
				OWLAnnotation urlAnnotation = factory.getOWLAnnotation(urlProperty,factory.getOWLLiteral(url));
				OWLAxiom urlAxiom = factory.getOWLAnnotationAssertionAxiom(clsAMethodB.getIRI(), urlAnnotation);
				System.out.println("Definition Axiom: "+urlAxiom);
				manager.applyChange(new AddAxiom(ontology, urlAxiom));
			}
			
			// Get values for Date Updated 
			//TODO Check how this should be represented, e.g. annotation property or some data property 
		
			
			// Get values for Synonym from text file, values[10] 
			String synonym = entry.getValue().get(10).toString();
			synonym = synonym.replaceAll("\"", "");
			//System.err.println("Synonym: "+synonym);
			if (!synonym.contains("null")) {
				String[] synonymValues = synonym.split(",");
				System.out.println("Synonym Values: "+synonymValues);
				for (String syn : synonymValues ) {
					OWLAnnotation synonymAnnotation = factory.getOWLAnnotation(synonymProperty,factory.getOWLLiteral(syn));
					OWLAxiom synonymAxiom = factory.getOWLAnnotationAssertionAxiom(clsAMethodB.getIRI(), synonymAnnotation);
					System.out.println("Synonym Axiom: "+synonymAxiom);
					manager.applyChange(new AddAxiom(ontology, synonymAxiom)); 
				}
			}

			
			/*// Get values for Defining Citation from text file, values[5] 
			String citation = entry.getValue().get(5).toString();
			//System.err.println("Citation: "+citation);
			if (!citation.contains("null")) {
				System.out.println("Citation Values: "+citation);
				OWLAnnotation citationAnnotation = factory.getOWLAnnotation(citationProperty,factory.getOWLLiteral(citation));
				OWLAxiom citationAxiom = factory.getOWLAnnotationAssertionAxiom(clsAMethodB.getIRI(), citationAnnotation);
				System.out.println("Citation Axiom: "+citationAxiom);
				manager.applyChange(new AddAxiom(ontology, citationAxiom)); 
			}*/
		
			System.out.println();
		}
		// Save ontology 
		manager.saveOntology(ontology);
	}

}





