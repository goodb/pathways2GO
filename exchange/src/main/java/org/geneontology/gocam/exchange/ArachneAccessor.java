/**
 * 
 */
package org.geneontology.gocam.exchange;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.reasoner.rulesys.Rule;
import org.geneontology.jena.OWLtoRules;
import org.geneontology.jena.SesameJena;
import org.geneontology.rules.engine.Explanation;
import org.geneontology.rules.engine.RuleEngine;
import org.geneontology.rules.engine.Triple;
import org.geneontology.rules.engine.WorkingMemory;
import org.geneontology.rules.util.Bridge;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.model.OWLOntologyStorageException;
import org.semanticweb.owlapi.model.parameters.Imports;

import scala.collection.JavaConverters;

/**
 * Wrapper around Arachne from @author balhoff 
 * @author bgood
 *
 */
public class ArachneAccessor {

	OWLOntology tbox_ontology;
	RuleEngine ruleEngine;

	/**
	 * Arachne needs a tbox (defined classes from ontology) to get started.
	 */
	public ArachneAccessor(OWLOntology tbox) {
		tbox_ontology = tbox;
		ruleEngine = initializeRuleEngine(tbox);
	}


	/**
	 * Takes the tbox ontology an converts it into a set of Jena Rules.
	 * These rules are how inference is accomplished
	 * @param tbox
	 * @return
	 */
	RuleEngine initializeRuleEngine(OWLOntology tbox) {
		Set<Rule> rules = new HashSet<Rule>();
		try {
			App.writeOntology("/Users/bgood/Desktop/test/test123.ttl", tbox);
		} catch (OWLOntologyStorageException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		rules.addAll(JavaConverters.setAsJavaSetConverter(OWLtoRules.translate(tbox, Imports.INCLUDED, true, true, true, true)).asJava());
		//indirect rules add statements like this ?pr <http://arachne.geneontology.org/indirect_type> ?pr_type
		//when an inferred type is added to link an instance to a superclass of one of its direct types
		rules.addAll(JavaConverters.setAsJavaSetConverter(OWLtoRules.indirectRules(tbox)).asJava());
		return new RuleEngine(Bridge.rulesFromJena(JavaConverters.asScalaSetConverter(rules).asScala()), true);
	}

	/**
	 * Return an Arachne working memory model including the provided abox, additional inferred edges, and optionally the
	 * triples representing the property definitions (rbox) and class definitions of the tbox 
	 * @param abox_ontology
	 * @param add_property_definitions
	 * @param add_class_definitions
	 * @return
	 */
	WorkingMemory createInferredModel(OWLOntology abox_ontology, boolean add_property_definitions, boolean add_class_definitions) {
		Set<Statement> statements = JavaConverters.setAsJavaSetConverter(SesameJena.ontologyAsTriples(abox_ontology)).asJava();		
		Set<Triple> triples = statements.stream().map(s -> Bridge.tripleFromJena(s.asTriple())).collect(Collectors.toSet());
		try {
			if(add_property_definitions) {
				OWLOntology propOntology = OWLManager.createOWLOntologyManager().createOntology(tbox_ontology.getRBoxAxioms(Imports.INCLUDED));
				Set<Statement> propStatements = JavaConverters.setAsJavaSetConverter(SesameJena.ontologyAsTriples(propOntology)).asJava();
				triples.addAll(propStatements.stream().map(s -> Bridge.tripleFromJena(s.asTriple())).collect(Collectors.toSet()));
			}
			if(add_class_definitions) {
				//just adding class definitions, not property defs.. 
				OWLOntology tboxOntology = OWLManager.createOWLOntologyManager().createOntology(tbox_ontology.getTBoxAxioms(Imports.INCLUDED));
				Set<Statement> tboxStatements = JavaConverters.setAsJavaSetConverter(SesameJena.ontologyAsTriples(tboxOntology)).asJava();
				triples.addAll(tboxStatements.stream().map(s -> Bridge.tripleFromJena(s.asTriple())).collect(Collectors.toSet()));
			}
		} catch (OWLOntologyCreationException e) {
			System.out.println("Couldn't add rbox or tbox statements to triples.");
			System.out.println(e);
		}
	//	System.out.println("triples before reasoning: "+triples.size());
		WorkingMemory wm = ruleEngine.processTriples(JavaConverters.asScalaSetConverter(triples).asScala());
	//	System.out.println("triples after reasoning: "+wm.facts().size());
		return wm; 
	}

	void printFactsExplanations(WorkingMemory wm) {
		int n = 0;
		scala.collection.Iterator<Triple> i = wm.facts().iterator();		
		while(i.hasNext()) {	
			Triple fact = i.next();
			if(!wm.asserted().contains(fact)) {
				n++;
				System.out.println(n+"\t"+fact.o().toString());
				scala.collection.immutable.Set<Explanation> e = wm.explain(fact);
				System.out.println("\t\t"+e);
			}
		}
		return;
	}
	
	/**
	 * Given a folder of .ttl files representing aboxes (OWL Individual declarations), 
	 * use Arachne to apply the rules it extracted during its construction to add inferred edges to the Abox
	 * Save these expanded graphs into new files in output folder.
	 * @param input_folder
	 * @param output_folder
	 * @param add_property_definitions
	 * @param add_class_definitions
	 * @throws OWLOntologyCreationException
	 * @throws FileNotFoundException
	 */
	void reasonAllInFolder(String input_folder, String output_folder, boolean add_property_definitions, boolean add_class_definitions) throws OWLOntologyCreationException, FileNotFoundException {
		OWLOntologyManager aman = OWLManager.createOWLOntologyManager();
		File dir = new File(input_folder);
		File[] directoryListing = dir.listFiles();
		if (directoryListing != null) {
			int n = 0;
			for (File abox_file : directoryListing) {
				if(abox_file.getName().endsWith(".ttl")) {
					n++;
					System.out.println("Adding inferences for "+abox_file.getName()+" "+n+" of "+directoryListing.length);
					OWLOntology abox = aman.loadOntologyFromOntologyDocument(abox_file);	
					WorkingMemory wm = createInferredModel(abox, add_property_definitions, add_class_definitions);
					//for convenient export of the wm model..
					Model jena = makeJenaModel(wm);
					String filename = output_folder+"/expanded_"+abox_file.getName();
					FileOutputStream o = new FileOutputStream(filename);
					jena.write(o, "TURTLE");
					jena.close();
				}else {
					System.out.println("Skipping: "+abox_file.getName());
				}
			}
		}
		return;
	}

	Model makeJenaModel(WorkingMemory wm) {
		Model model = ModelFactory.createDefaultModel();
		model.add(JavaConverters.setAsJavaSetConverter(wm.facts()).asJava().stream()
				.map(t -> model.asStatement(Bridge.jenaFromTriple(t))).collect(Collectors.toList()));
		return model;
	}
	
	/**
	 * Given a directory of ttl files, merge them into one OWLOntology
	 * @param input_folder
	 * @return
	 * @throws OWLOntologyCreationException
	 */
	public static OWLOntology makeOneOntologyFromDirectory(String input_folder) throws OWLOntologyCreationException {
		OWLOntologyManager aman = OWLManager.createOWLOntologyManager();
		OWLOntology metaont = aman.createOntology();
		File dir = new File(input_folder);
		File[] directoryListing = dir.listFiles();
		if (directoryListing != null) {
			for (File abox_file : directoryListing) {
				if(abox_file.getName().endsWith(".ttl")) {
					OWLOntology box = aman.loadOntologyFromOntologyDocument(abox_file);	
					Set<OWLAxiom> axioms = box.getAxioms();
					for(OWLAxiom axiom : axioms) {
						aman.addAxiom(metaont, axiom);
					}
					System.out.println("Added "+abox_file.getName()+" Abox axioms now count: "+metaont.getAxioms().size());
				}else {
					System.out.println("Skipping: "+abox_file.getName());
				}

			}
		}
		return metaont;
	}
	
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		// TODO Auto-generated method stub

	}

}
