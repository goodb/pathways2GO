/**
 * 
 */
package org.geneontology.gocam.exchange;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.io.IOUtils;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.geneontology.jena.SesameJena;
import org.geneontology.rules.engine.WorkingMemory;
import org.geneontology.rules.util.Bridge;
import org.semanticweb.owlapi.model.OWLOntology;

import scala.collection.JavaConverters;

/**
 * Provide access to the Jena SPARQL engine over OWLOntology graphs.  
 * Optionally employ the Arachne reasoner to expand the graphs prior to query.
 * @author bgood
 *
 */
public class QRunner {
	Model jena;
	ArachneAccessor arachne;
	WorkingMemory wm;
	/**
	 * 
	 */
	public QRunner(OWLOntology tbox, OWLOntology abox, boolean add_inferences, boolean add_property_definitions, boolean add_class_definitions) {
		if(add_inferences) {
			System.out.println("Setting up Arachne reasoner, extracting rules from tbox");
			arachne = new ArachneAccessor(tbox);
			System.out.println("Applying rules to expand the abox graph");
			wm = arachne.createInferredModel(abox, add_property_definitions, add_class_definitions);			
			System.out.println("Making Jena model from inferred graph");
			jena = makeJenaModel(wm);
		}else {
			System.out.println("Making Jena model (no inferred relations, no tbox)");
			jena = makeJenaModel(abox, null);
		}
	}

	public QRunner(OWLOntology abox) {
		System.out.println("Setting up Jena model for query.  Only including Abox ontology, no reasoning");
		jena = makeJenaModel(abox, null);
	}

	Model makeJenaModel(WorkingMemory wm) {
		Model model = ModelFactory.createDefaultModel();
		model.add(JavaConverters.setAsJavaSetConverter(wm.facts()).asJava().stream()
				.map(t -> model.asStatement(Bridge.jenaFromTriple(t))).collect(Collectors.toList()));
		return model;
	}

	Model makeJenaModel(OWLOntology abox, OWLOntology tbox) {
		Model model = ModelFactory.createDefaultModel();
		Set<Statement> a_statements = JavaConverters.setAsJavaSetConverter(SesameJena.ontologyAsTriples(abox)).asJava();		
		for(Statement s : a_statements) {
			model.add(s);
		}
		if(tbox!=null) {
			Set<Statement> t_statements = JavaConverters.setAsJavaSetConverter(SesameJena.ontologyAsTriples(tbox)).asJava();
			for(Statement s : t_statements) {
				model.add(s);
			}
		}	
		return model;
	}
	
	int nTriples() {
		int n = 0;
		String q = null;
		try {
			q = IOUtils.toString(App.class.getResourceAsStream("triple_count.rq"), StandardCharsets.UTF_8);
		} catch (IOException e) {
			System.out.println("Could not load SPARQL query from jar \n"+e);
		}
		QueryExecution qe = QueryExecutionFactory.create(q, jena);
		ResultSet results = qe.execSelect();
		if (results.hasNext()) {
			QuerySolution qs = results.next();
			Literal s = qs.getLiteral("triples");
			System.out.println(s);
			n = s.getInt();
		}
		qe.close();
		return n;
	}

	boolean isConsistent() {
		boolean consistent = true;
		String q = null;
		try {
			q = IOUtils.toString(App.class.getResourceAsStream("consistency_check.rq"), StandardCharsets.UTF_8);
		} catch (IOException e) {
			System.out.println("Could not load SPARQL query from jar \n"+e);
		}
		QueryExecution qe = QueryExecutionFactory.create(q, jena);
		ResultSet results = qe.execSelect();
		if (results.hasNext()) {
			QuerySolution qs = results.next();
			Literal s = qs.getLiteral("triples");
			if(s.getInt()==0) {
				consistent = true;
			}else{
				consistent = false;
			}
		}
		qe.close();
		return consistent;
	}

	Set<String> getUnreasonableEntities() {
		Set<String> unreasonable = new HashSet<String>();
		String q = null;
		try {
			q = IOUtils.toString(App.class.getResourceAsStream("unreasonable_query.rq"), StandardCharsets.UTF_8);
		} catch (IOException e) {
			System.out.println("Could not load SPARQL query from jar \n"+e);
		}
		QueryExecution qe = QueryExecutionFactory.create(q, jena);
		ResultSet results = qe.execSelect();
		while (results.hasNext()) {
			QuerySolution qs = results.next();
			Resource r = qs.getResource("s");
			unreasonable.add(r.getURI());
		}
		qe.close();
		return unreasonable;
	}

	/**
	 * Writes whatever is currently in the jena model to a file
	 * @param filename
	 * @param format
	 * @throws FileNotFoundException
	 */
	void dumpModel(String filename, String format) throws FileNotFoundException {
		FileOutputStream o = new FileOutputStream(filename);
		jena.write(o, format);
	}

}