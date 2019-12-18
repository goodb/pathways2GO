/**
 * 
 */
package org.geneontology.gocam.exchange;

import static org.junit.Assert.*;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URL;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.jena.rdf.model.Model;
import org.geneontology.rules.engine.WorkingMemory;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.obolibrary.robot.CatalogXmlIRIMapper;
import org.openrdf.query.BindingSet;
import org.openrdf.query.QueryEvaluationException;
import org.openrdf.query.TupleQueryResult;
import org.openrdf.repository.RepositoryException;
import org.openrdf.rio.RDFHandlerException;
import org.openrdf.rio.RDFParseException;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.model.OWLOntologyStorageException;
import org.semanticweb.owlapi.reasoner.OWLReasoner;
import org.semanticweb.owlapi.reasoner.OWLReasonerFactory;
import org.semanticweb.owlapi.reasoner.structural.StructuralReasonerFactory;

import com.google.common.collect.Sets;

/**
 * @author bgood
 *
 */
public class BioPaxtoGOTest {
	//use default values for testing
	static BioPaxtoGO bp2g = new BioPaxtoGO(); 
	//parameters to set
	static String input_biopax = "./src/test/resources/biopax/"; 
	static String output_file_folder = "./src/test/resources/gocam/"; 
	static String output_file_stub = "./src/test/resources/gocam/test-"; 
	static String output_blazegraph_journal = "./src/test/resources/gocam/blazegraph.jnl"; //"/Users/bgood/noctua-config/blazegraph.jnl"; //
	static String tag = ""; //unexpanded
	static String base_title = "title here";//"Will be replaced if a title can be found for the pathway in its annotations
	static String default_contributor = "https://orcid.org/0000-0002-7334-7852"; //
	static String default_provider = "https://reactome.org";//"https://www.wikipathways.org/";//"https://www.pathwaycommons.org/";	
	static String test_pathway_name = null;
	static String empty_catalogue_file = "./src/test/resources/catalog-no-import.xml";
	static String local_catalogue_file = "./src/test/resources/ontology/catalog-for-validation.xml";//  //"/Users/bgood/gocam_ontology/catalog-v001-for-noctua.xml";
	static String go_lego_file = "./src/test/resources/ontology/go-lego-no-neo.owl";
	static String go_plus_url = "http://purl.obolibrary.org/obo/go/extensions/go-plus.owl";
	static String go_plus_file = "./target/go-plus.owl";
	static Blazer blaze;
	static QRunner tbox_qrunner;

	/**
	 * @throws java.lang.Exception 
	 */
	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
		fullBuild();
		//loadBlazegraph();
	}
	
	public static void loadBlazegraph() {
		bp2g.blazegraph_output_journal = output_blazegraph_journal;
		blaze = new Blazer(bp2g.blazegraph_output_journal);
	}
	
	public static void fullBuild() throws Exception{
		System.out.println("set up before class");
		bp2g.go_lego_file = go_lego_file;
		File goplus_file = new File(go_plus_file);
		if(!goplus_file.exists()) {
			URL goplus_location = new URL(go_plus_url);
			System.out.println("downloading goplus ontology from "+go_plus_url);
			org.apache.commons.io.FileUtils.copyURLToFile(goplus_location, goplus_file);
		}		
		bp2g.goplus = new GOPlus(go_plus_file);
		bp2g.blazegraph_output_journal = output_blazegraph_journal;
		//clean out any prior data in triple store
		FileWriter clean = new FileWriter(bp2g.blazegraph_output_journal, false);
		clean.write("");
		clean.close();
		//open connection to triple store
		blaze = new Blazer(bp2g.blazegraph_output_journal);
		System.out.println("done connecting to blaze, loading axioms");
		//set up for validation
		OWLOntologyManager ontman = OWLManager.createOWLOntologyManager();	
		if(local_catalogue_file!=null) {
			ontman.setIRIMappers(Collections.singleton(new CatalogXmlIRIMapper(local_catalogue_file)));
		}
		OWLOntology tbox = ontman.loadOntologyFromOntologyDocument(new File(go_lego_file));
		Set<OWLOntology> imports = tbox.getImports();
		imports.add(tbox);
		//initialize the rules for inference
		System.out.println("starting tbox build");
		tbox_qrunner = new QRunner(imports, null, true, false, false);
		System.out.println("done building arachne");		
		//run the conversion on all the test biopax files
		System.out.println("running biopaxtogo on all test files");
		File dir = new File(input_biopax);
		File[] directoryListing = dir.listFiles();
		//run through all files
		if (directoryListing != null) {
			for (File biopax : directoryListing) {
				String name = biopax.getName();
				if(name.contains(".owl")) { 
					name = name.replaceAll(".owl", "-");
					String this_output_file_stub = output_file_stub+name;
					try {
						bp2g.convert(biopax.getAbsolutePath(), this_output_file_stub, base_title, default_contributor, default_provider, tag, null, blaze, tbox_qrunner);
					} catch (OWLOntologyCreationException | OWLOntologyStorageException | RepositoryException
							| RDFParseException | RDFHandlerException | IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
			} 
		}else {
			try {
				bp2g.convert(input_biopax, output_file_stub, base_title, default_contributor, default_provider, tag, null, blaze, tbox_qrunner);
			} catch (OWLOntologyCreationException | OWLOntologyStorageException | RepositoryException
					| RDFParseException | RDFHandlerException | IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		System.out.println("done set up before class");
		
	}
	
	/**
	 * @throws java.lang.Exception
	 */
	@AfterClass
	public static void tearDownAfterClass() throws Exception {
		blaze.getRepo().shutDown();
		System.out.println("tear down after class");
	}

	/**
	 * @throws java.lang.Exception
	 */
	@Before
	public void setUp() throws Exception {
		System.out.println("setup");
	}

	/**
	 * @throws java.lang.Exception
	 */
	@After
	public void tearDown() throws Exception {
		System.out.println("tear down ");
	}

	/**
	 * 
	 * 
	 * 		//"Glycolysis"; //"Signaling by BMP"; //"TCF dependent signaling in response to WNT"; //"RAF-independent MAPK1/3 activation";//"Oxidative Stress Induced Senescence"; //"Activation of PUMA and translocation to mitochondria";//"HDR through Single Strand Annealing (SSA)";  //"IRE1alpha activates chaperones"; //"Generation of second messenger molecules";//null;//"Clathrin-mediated endocytosis";
		//next tests: 
		//for continuant problem: Import of palmitoyl-CoA into the mitochondrial matrix 
		//error in rule rule:reg3 NTRK2 activates RAC1
		//
		//(rule:reg3) The relation 'DOCK3 binds FYN associated with NTRK2' 'directly positively regulates' 'DOCK3 activates RAC1' was inferred because: reaction1 has an output that is the enabler of reaction 2.
		//test for active site recognition
		//	test_pathways.add("SCF(Skp2)-mediated degradation of p27/p21");
		//unions
		//			test_pathways.add("GRB2 events in ERBB2 signaling");
		//			test_pathways.add("Elongator complex acetylates replicative histone H3, H4");
		//looks good
		//	test_pathways.add("Attenuation phase");
		//		test_pathways.add("NTRK2 activates RAC1");
		//		test_pathways.add("Unwinding of DNA");
		//		test_pathways.add("Regulation of TNFR1 signaling");
		//		test_pathways.add("SCF(Skp2)-mediated degradation of p27/p21");
		//inconsistent, but not sure how to fix		
		//test_pathways.add("tRNA modification in the nucleus and cytosol");
		//inconsistent
		//test_pathways.add("Apoptosis induced DNA fragmentation");

		//		test_pathways.add("SHC1 events in ERBB4 signaling");
		//looks good.  example of converting binding function to regulatory process template
		//	 test_pathways.add("FRS-mediated FGFR3 signaling");
		//	 test_pathways.add("FRS-mediated FGFR4 signaling");
		//looks good, nice inference for demo	 
		//		 test_pathways.add("Activation of G protein gated Potassium channels");
		//		 test_pathways.add("Regulation of actin dynamics for phagocytic cup formation");
		//		 test_pathways.add("SHC-mediated cascade:FGFR2");
		//		 test_pathways.add("SHC-mediated cascade:FGFR3");
		//check this one for annotations on regulates edges
		//		test_pathways.add("RAF-independent MAPK1/3 activation");
		//great example of why we are not getting a complete data set without inter model linking.  
		//		test_pathways.add("TCF dependent signaling in response to WNT");
		//looks great..
		//looks good 
		//	test_pathways.add("activated TAK1 mediates p38 MAPK activation");
		//check for relations between events that might not be biopax typed chemical reactions - e.g. degradation
		//			test_pathways.add("HDL clearance");
	 * 
	 */



	

	/**
	 * Test that all generated models are consistent.
	 */
//	@Test
	public final void testOWLConsistency() {
		File dir = new File(output_file_folder);
		File[] directoryListing = dir.listFiles();
		for (File abox_file : directoryListing) {
			if(abox_file.getAbsolutePath().endsWith(".ttl")) {
				try {
					GoCAM go_cam = new GoCAM(abox_file.getAbsoluteFile(), empty_catalogue_file);
					go_cam.qrunner = new QRunner(go_cam.go_cam_ont); 		
					WorkingMemory wm_with_tbox = tbox_qrunner.arachne.createInferredModel(go_cam.go_cam_ont,false, false);			
					go_cam.qrunner.jena = go_cam.qrunner.makeJenaModel(wm_with_tbox);
					boolean is_logical = go_cam.validateGoCAM();	
					System.out.println(abox_file.getName()+" owl consistent:"+is_logical);
					assertTrue(abox_file.getName()+" owl consistent:"+is_logical, is_logical);
				} catch (OWLOntologyCreationException | IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
			
		}
	}
	
	/**
	 * Test method for {@link org.geneontology.gocam.exchange.GoCAM#inferTransportProcess()}.
	 * Test that transport processes are:
	 *  correctly typed as localization
	 * 	have the proper starting and ending locations
	 *  have the right number of inputs and outputs
	 *  have an input that is also an output 
	 * Use reaction in Signaling By BMP R-HSA-201451
	 * 	The phospho-R-Smad1/5/8:Co-Smad transfers to the nucleus
	 * 	https://reactome.org/content/detail/R-HSA-201472
	 * Compare to http://noctua-dev.berkeleybop.org/editor/graph/gomodel:R-HSA-201451
	 */
	@Test
	public final void testInferLocalizationProcess() {
		System.out.println("Testing localization inference");
		TupleQueryResult result = null;
		try {
			String query =
					"prefix obo: <http://purl.obolibrary.org/obo/> "
					+ "select ?type (count(distinct ?output) AS ?outputs) (count(distinct ?input) AS ?inputs) " + 
					"where { " + 
					" VALUES ?reaction { <http://model.geneontology.org/R-HSA-201451/R-HSA-201472> } "
					+ " ?reaction rdf:type ?type . " + 
					"  filter(?type != owl:NamedIndividual) "
					+ " ?reaction obo:RO_0002234 ?output . " + 
					" ?reaction obo:RO_0002233 ?input . " + 
					"  ?reaction obo:RO_0002339 ?endlocation . " + 
					"  ?endlocation rdf:type <http://purl.obolibrary.org/obo/GO_0005654> . " + 
					"  ?reaction obo:RO_0002338 ?startlocation . " + 
					"  ?startlocation rdf:type <http://purl.obolibrary.org/obo/GO_0005829> . "
					+ "?input rdf:type ?entityclass . "
					+ "?output rdf:type ?entityclass ." + 
					"}"
				+" group by ?type ";
			result = blaze.runSparqlQuery(query);
			int n = 0; String type = null; int outputs = 0; int inputs = 0;
			while (result.hasNext()) {
				BindingSet bindingSet = result.next();
				type = bindingSet.getValue("type").stringValue();
				outputs = Integer.parseInt(bindingSet.getValue("outputs").stringValue());
				inputs = Integer.parseInt(bindingSet.getValue("inputs").stringValue());
				n++;
			}
			assertTrue(n==1);
			assertTrue(type.equals("http://purl.obolibrary.org/obo/GO_0006810"));
			assertTrue(inputs==1);
			assertTrue(outputs==1);
		} catch (QueryEvaluationException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} finally {
			try {
				result.close();
			} catch (QueryEvaluationException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		System.out.println("Done testing transport inference");
	}
	
	/**
	 * Test method for {@link org.geneontology.gocam.exchange.GoCAM#inferTransportProcess()}.
	 * Test that protein transport processes are:
	 *  correctly typed as protein localization 
	 * 	have the proper starting and ending locations
	 *  have the right number of inputs and outputs
	 *  have an input that is also an output 
	 * Use reaction in TCF dependent signaling in response to WNT R-HSA-201681
	 * Beta-catenin translocates to the nucleus
	 * 	reaction uri http://model.geneontology.org/R-HSA-201681/R-HSA-201669 
	 */
	@Test
	public final void testInferProteinLocalizationProcess() {
		System.out.println("Testing localization inference");
		TupleQueryResult result = null;
		try {
			String query =
					"prefix obo: <http://purl.obolibrary.org/obo/> "
					+ "select ?type (count(distinct ?output) AS ?outputs) (count(distinct ?input) AS ?inputs) " + 
					"where { " + 
					" VALUES ?reaction { <http://model.geneontology.org/R-HSA-201681/R-HSA-201669> } "
					+ " ?reaction rdf:type ?type . " + 
					"  filter(?type != owl:NamedIndividual) "
					+ " ?reaction obo:RO_0002234 ?output . " + 
					" ?reaction obo:RO_0002233 ?input . " + 
					"  ?reaction obo:RO_0002339 ?endlocation . " + 
					"  ?endlocation rdf:type <http://purl.obolibrary.org/obo/GO_0005654> . " + 
					"  ?reaction obo:RO_0002338 ?startlocation . " + 
					"  ?startlocation rdf:type <http://purl.obolibrary.org/obo/GO_0005829> . "
					+ "?input rdf:type ?entityclass . "
					+ "?output rdf:type ?entityclass ." + 
					"}"
				+" group by ?type ";
			result = blaze.runSparqlQuery(query);
			int n = 0; String type = null; int outputs = 0; int inputs = 0;
			while (result.hasNext()) {
				BindingSet bindingSet = result.next();
				type = bindingSet.getValue("type").stringValue();
				outputs = Integer.parseInt(bindingSet.getValue("outputs").stringValue());
				inputs = Integer.parseInt(bindingSet.getValue("inputs").stringValue());
				n++;
			}
			assertTrue(n==1);
			assertTrue("type is "+type, type.equals("http://purl.obolibrary.org/obo/GO_0015031"));
			assertTrue(inputs==1);
			assertTrue(outputs==1);
		} catch (QueryEvaluationException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} finally {
			try {
				result.close();
			} catch (QueryEvaluationException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		System.out.println("Done testing protein transport inference");
	}

	/**
	 * Test method for {@link org.geneontology.gocam.exchange.GoCAM#inferTransportProcess()}.
	 * Test that dissociation processes are:
	 * 	correctly typed: as protein complex disassembly GO_0032984  
	 * Use reaction in Signaling By BMP R-HSA-201451
	 * 	Phospho-R-Smad1/5/8 dissociates from the receptor complex
	 * 	https://reactome.org/content/detail/R-HSA-201453
	 * Compare to http://noctua-dev.berkeleybop.org/editor/graph/gomodel:R-HSA-201451
	 */
	@Test
	public final void testInferDissociationProcess() {
		System.out.println("Testing dissociation inference");
		TupleQueryResult result = null;
		try {
			result = blaze.runSparqlQuery(
				"prefix obo: <http://purl.obolibrary.org/obo/> "
				+ "select ?type " + 
				"where { " + 
				"VALUES ?reaction { <http://model.geneontology.org/R-HSA-201451/R-HSA-201453> }" + 
				"  ?reaction rdf:type ?type .	" + 
				"  filter(?type != owl:NamedIndividual) " + 
				"} ");
			int n = 0; String type = null; 
			while (result.hasNext()) {
				BindingSet bindingSet = result.next();
				type = bindingSet.getValue("type").stringValue();
				n++;
			}
			assertTrue(n==1);
			assertTrue(type.equals("http://purl.obolibrary.org/obo/GO_0032984"));
		} catch (QueryEvaluationException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} finally {
			try {
				result.close();
			} catch (QueryEvaluationException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		System.out.println("Done testing dissociation inference");
	}
	

	/**
	 * Test method for {@link org.geneontology.gocam.exchange.GoCAM#inferOccursInFromEntityLocations()}.
	 * Test that occurs_in statements on reactions are inferred from entity locations
	 * Use reaction in Signaling By BMP R-HSA-201451
	 * 	Phospho-R-Smad1/5/8 forms a complex with Co-Smad
	 * 	https://reactome.org/content/detail/R-HSA-201422
	 * Compare to http://noctua-dev.berkeleybop.org/editor/graph/gomodel:R-HSA-201451
	 */
	@Test
	public final void testOccursInFromEntityLocations() {
		System.out.println("Testing occurs in from entities inference");
		TupleQueryResult result = null;
		try {
			result = blaze.runSparqlQuery(
				"prefix obo: <http://purl.obolibrary.org/obo/> "
				+ "select ?locationclass " + 
				"where { " + 
				"VALUES ?reaction { <http://model.geneontology.org/R-HSA-201451/R-HSA-201422> }" + 
				"  ?reaction obo:BFO_0000066 ?location . "
				+ "?location rdf:type ?locationclass " + 
				"  filter(?locationclass != owl:NamedIndividual)" + 
				"}");
			int n = 0; String location = null;
			while (result.hasNext()) {
				BindingSet bindingSet = result.next();
				location = bindingSet.getValue("locationclass").stringValue();
				n++;
			}
			assertTrue(n==1);
			assertTrue(location.equals("http://purl.obolibrary.org/obo/GO_0005829"));
		} catch (QueryEvaluationException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} finally {
			try {
				result.close();
			} catch (QueryEvaluationException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		System.out.println("Done occurs in from entities inference");
	}
	
	
	/**
	 * Test method for {@link org.geneontology.gocam.exchange.GoCAM#inferOccursInFromEntityLocations()}.
	 * Test that occurs_in statements on reactions are inferred from enabling molecules
	 * Use reaction R-HSA-201425 is Signaling by BMP 
	 * 	Ubiquitin-dependent degradation of the Smad complex terminates BMP2 signalling
	 * 	https://reactome.org/content/detail/R-HSA-201425 
	 * Compare to http://noctua-dev.berkeleybop.org/editor/graph/gomodel:R-HSA-201451
	 */
	@Test
	public final void testOccursInFromEnablerLocation() {
		System.out.println("Testing occurs in from enabler inference");
		TupleQueryResult result = null;
		try {
			result = blaze.runSparqlQuery(
				"prefix obo: <http://purl.obolibrary.org/obo/> "
				+ "select ?locationclass " + 
				"where { " + 
				"VALUES ?reaction { <http://model.geneontology.org/R-HSA-201451/R-HSA-201425> }" + 
				"  ?reaction obo:BFO_0000066 ?location . "
				+ "?location rdf:type ?locationclass " + 
				"  filter(?locationclass != owl:NamedIndividual)" + 
				"}");
			int n = 0; String location = null;
			while (result.hasNext()) {
				BindingSet bindingSet = result.next();
				location = bindingSet.getValue("locationclass").stringValue();
				n++;
			}
			assertTrue(n==1);
			assertTrue(location, location.equals("http://purl.obolibrary.org/obo/GO_0005654"));
		} catch (QueryEvaluationException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} finally {
			try {
				result.close();
			} catch (QueryEvaluationException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		System.out.println("Done testing occurs in from enabler inference");
	}
	
	/**
	 * Test method for {@link org.geneontology.gocam.exchange.GoCAM#inferRegulatesViaOutputRegulates()}.
	 * Test that if reaction1 has_output M and reaction2 is regulated by M then reaction1 regulates reaction2
	 * Use pathway R-HSA-4641262 , reactions R-HSA-201691 regulates R-HSA-201685 
	 * 	Beta-catenin is released from the destruction complex
	 * 	https://reactome.org/content/detail/R-HSA-4641262 
	 * Compare to http://noctua-dev.berkeleybop.org/editor/graph/gomodel:R-HSA-4641262
	 */
	@Test 
	public final void testInferRegulatesViaOutputRegulates() {
		System.out.println("Testing infer regulates via output regulates");
		TupleQueryResult result = null;
		try {
			result = blaze.runSparqlQuery(
				"prefix obo: <http://purl.obolibrary.org/obo/> "
				+ "select ?prop " + 
				"where { " + 
				"VALUES ?reaction1 { <http://model.geneontology.org/R-HSA-4411364/R-HSA-4411351> } ." + 
				"VALUES ?reaction2 { <http://model.geneontology.org/R-HSA-4411364/R-HSA-4411372> } . " + 
				"  ?reaction1 <http://purl.obolibrary.org/obo/RO_0002413> ?binding_reaction ."
				+ "?binding_reaction ?prop ?reaction2 . "+
				"}"); 
			int n = 0; String prop = null;
			while (result.hasNext()) {
				BindingSet bindingSet = result.next();
				prop = bindingSet.getValue("prop").stringValue();
				n++;
			}
			assertTrue("should have been 1, but got n results: "+n, n==1);
			assertTrue("got "+prop, prop.equals("http://purl.obolibrary.org/obo/RO_0002629"));
		} catch (QueryEvaluationException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} finally {
			try {
				result.close();
			} catch (QueryEvaluationException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		System.out.println("Done testing infer regulates via output regulates");
	}
	
	/**
	 * Test method for {@link org.geneontology.gocam.exchange.GoCAM#convertEntityRegulatorsToBindingFunctions()}.
	 * Test that if reaction1 has_output M and reaction2 is regulated by M then reaction1 regulates reaction2
	 * Use pathway Glycolysis R-HSA-70171 , reaction R-HSA-71670  
	 * 	phosphoenolpyruvate + ADP => pyruvate + ATP
entity involved in regulation of function 
Binding has_input E1
Binding has_input E2
Binding +-_regulates R
Binding part_of +-_regulation_of BP
⇐ 	
E1 +- involved_in_regulation_of R
R enabled_by E2
BP has_part R
	 */
	@Test 
	public final void testConvertEntityRegulatorsToBindingFunctions() {
		System.out.println("Testing convert entity regulators to binding functions");
		TupleQueryResult result = null;
		try {
			result = blaze.runSparqlQuery(
				"select distinct ?binding_reaction " + 
				"where { " + 
				"VALUES ?reaction1 { <http://model.geneontology.org/R-HSA-70171/R-HSA-71670> } ."  
				+ " ?binding_reaction <http://purl.obolibrary.org/obo/RO_0002212> ?reaction1 . " //
				+ "?binding_reaction rdf:type <http://purl.obolibrary.org/obo/GO_0005488> . "
				+ "?binding_reaction <http://purl.obolibrary.org/obo/RO_0002233> ?input1 . "
				+ "?binding_reaction <http://purl.obolibrary.org/obo/RO_0002233> ?input2 . "
				+ "filter(?input1 != ?input2) "
				+"}"); 
			int n = 0; 
			while (result.hasNext()) {
				BindingSet bindingSet = result.next();
				String br = bindingSet.getValue("binding_reaction").stringValue();
				n++;
			}
			assertTrue("should have been 3, but got n results: "+n, n==3);
		} catch (QueryEvaluationException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} finally {
			try {
				result.close();
			} catch (QueryEvaluationException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		System.out.println("Done testing infer regulates via output regulates");
	}
	
	/**
	 * Test method for active site handling in {@link org.geneontology.gocam.exchange.BioPaxtoGO#defineReactionEntity}.
	 * When reactome (this is a reactome specific hack) indicates in a Control object that a specific element of a complex
	 * is the active controller (regulator, catalyst), then that part should be pulled out, linked to its parent via has_part and
	 * used as the agent in the reaction.
	 * This is also a test for {@link org.geneontology.gocam.exchange.GoCAM#convertEntityRegulatorsToBindingFunctions}
	 * Use pathway R-HSA-4641262 , reaction = R-HSA-201685 
	 * 	Beta-catenin is released from the destruction complex
	 * 	https://reactome.org/content/detail/R-HSA-4641262 
	 * Compare to http://noctua-dev.berkeleybop.org/editor/graph/gomodel:R-HSA-4641262
	 */
	@Test
	public final void testActiveSiteInController() {
		System.out.println("Testing active sites in controller");
		TupleQueryResult result = null;
		try {
			result = blaze.runSparqlQuery(
				"prefix obo: <http://purl.obolibrary.org/obo/> "
				+ "select ?pathway " + 
				"where { " + 
				"VALUES ?reaction { <http://model.geneontology.org/R-HSA-4641262/R-HSA-201677> } . " 
				+ "?reaction obo:BFO_0000050 ?pathway . "
				+ "?reaction obo:RO_0002333 ?active_part . "
				+ "?larger_thing obo:BFO_0000051 ?active_part "+
				"}");
			int n = 0; String pathway = null;
			while (result.hasNext()) {
				BindingSet bindingSet = result.next();
				pathway = bindingSet.getValue("pathway").stringValue();
				n++;
			}
			assertTrue("expected 1, got "+n, n==1);
			assertTrue("got "+pathway, pathway.equals("http://model.geneontology.org/R-HSA-4641262/R-HSA-4641262"));
		} catch (QueryEvaluationException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} finally {
			try {
				result.close();
			} catch (QueryEvaluationException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		System.out.println("Done testing active sites in controller");
	}
	

	/**
	 * Test method for {@link org.geneontology.gocam.exchange.GoCAM#inferRegulatesViaOutputEnables}.
	 * Use pathway R-HSA-4641262 , reaction1 = R-HSA-1504186 reaction2 = R-HSA-201677
	 * Relation should be RO:0002413 directly positive regulates
	 * 	DVL recruits GSK3beta:AXIN1 to the receptor complex
	 * Phosphorylation of LRP5/6 cytoplasmic domain by membrane-associated GSK3beta
	 * 	https://reactome.org/content/detail/R-HSA-4641262 
	 * Compare to http://noctua-dev.berkeleybop.org/editor/graph/gomodel:R-HSA-4641262
	 * 
	 * Also an active site detection test
	 */
	@Test
	public final void testInferRegulatesViaOutputEnables() {
		System.out.println("Testing regulates via output enables");
		TupleQueryResult result = null;
		try {
			result = blaze.runSparqlQuery(
				"prefix obo: <http://purl.obolibrary.org/obo/> "
				+ "select ?pathway " + 
				"where { " + 
				"VALUES ?reaction1 { <http://model.geneontology.org/R-HSA-4641262/R-HSA-1504186> } . "+ 
				"VALUES ?reaction2 { <http://model.geneontology.org/R-HSA-4641262/R-HSA-201677> } . "+
				" ?reaction1 obo:RO_0002629 ?reaction2 . "
				+ "?reaction2 obo:RO_0002333 ?active_part . "
				+ "?larger_thing obo:BFO_0000051 ?active_part . "
				+ "?reaction1 obo:BFO_0000050 ?pathway "+
				
				"}");
			int n = 0; String pathway = null;
			while (result.hasNext()) {
				BindingSet bindingSet = result.next();
				pathway = bindingSet.getValue("pathway").stringValue();
				n++;
			}
			assertTrue(n==1);
			assertTrue("got "+pathway, pathway.equals("http://model.geneontology.org/R-HSA-4641262/R-HSA-4641262"));
		} catch (QueryEvaluationException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} finally {
			try {
				result.close();
			} catch (QueryEvaluationException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		System.out.println("Done testing regulates via output enables");
	}
	
	//gomodel:R-HSA-4641262/R-HSA-201677 / RO:0002413 / gomodel:R-HSA-4641262/R-HSA-201691
	// #inferProvidesInput	
	/**
	 * Test method for {@link org.geneontology.gocam.exchange.GoCAM#inferProvidesInput}.
	 * Use pathway R-HSA-4641262 , reaction1 = R-HSA-201677 reaction2 = R-HSA-201691
	 * Relation should be RO:0002413 directly positive regulates
	 * Phosphorylation of LRP5/6 cytoplasmic domain by membrane-associated GSK3beta
	 * Phosphorylation of LRP5/6 cytoplasmic domain by CSNKI
	 * 	https://reactome.org/content/detail/R-HSA-4641262 
	 * Compare to http://noctua-dev.berkeleybop.org/editor/graph/gomodel:R-HSA-4641262
	 * 
	 * Also an active site detection test
	 */
	@Test
	public final void testInferProvidesInput() {
		System.out.println("Testing provides input");
		TupleQueryResult result = null;
		try {
			result = blaze.runSparqlQuery(
				"prefix obo: <http://purl.obolibrary.org/obo/> "
				+ "select ?pathway " + 
				"where { " + 
				"VALUES ?reaction1 { <http://model.geneontology.org/R-HSA-4641262/R-HSA-201677> } . "+ 
				"VALUES ?reaction2 { <http://model.geneontology.org/R-HSA-4641262/R-HSA-201691> } . "+
				" ?reaction1 obo:RO_0002413 ?reaction2 . "
				+ "?reaction1 obo:BFO_0000050 ?pathway "+				
				"}");
			int n = 0; String pathway = null;
			while (result.hasNext()) {
				BindingSet bindingSet = result.next();
				pathway = bindingSet.getValue("pathway").stringValue();
				n++;
			}
			assertTrue(n==1);
			assertTrue("got "+pathway, pathway.equals("http://model.geneontology.org/R-HSA-4641262/R-HSA-4641262"));
		} catch (QueryEvaluationException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} finally {
			try {
				result.close();
			} catch (QueryEvaluationException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		System.out.println("Done testing regulates via output enables");
	}
}
