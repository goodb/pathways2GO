/**
 * 
 */
package org.geneontology.garage;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.geneontology.gocam.exchange.Helper;
import org.geneontology.gocam.exchange.rhea.RheaConverter;
import org.geneontology.gocam.exchange.rhea.RheaConverter.rheaReaction;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLAnnotation;
import org.semanticweb.owlapi.model.OWLAnnotationAssertionAxiom;
import org.semanticweb.owlapi.model.OWLAnnotationProperty;
import org.semanticweb.owlapi.model.OWLAnnotationValue;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.model.OWLOntologyStorageException;
import org.semanticweb.owlapi.search.EntitySearcher;
import org.semanticweb.owlapi.vocab.OWLRDFVocabulary;

/**
 * @author bgood
 *
 */
public class XrefUpdater {

	/**
	 * 
	 */
	public XrefUpdater() {
		// TODO Auto-generated constructor stub
	}
	class MappingResult {
		String go_term;
		String old_id;
		String action;
		String replaced_by;
		String reason;
		String property;
		OWLAxiom new_xref_axiom;
		OWLAxiom delete_xref_axiom;
	}
	/**
	 * @param args
	 * @throws IOException 
	 * @throws OWLOntologyStorageException 
	 * @throws OWLOntologyCreationException 
	 */
	public static void main(String[] args) throws OWLOntologyCreationException, OWLOntologyStorageException, IOException {
		XrefUpdater u = new XrefUpdater();
		//u.updateRheaXrefs();
		//u.updateReactomeXrefs();
		u.dropReactomeXrefs();
	}

	public void updateRheaXrefs() throws IOException, OWLOntologyCreationException, OWLOntologyStorageException {
		Set<MappingResult> results = new HashSet<MappingResult>();
		RheaConverter rc = new RheaConverter();
		Map<String, rheaReaction> rr = rc.getReactionsFromRDF();
		Map<String, String> bi_master = new HashMap<String, String>();
		Map<String, String> master_bi = new HashMap<String, String>();
		for(rheaReaction r : rr.values()) {
			bi_master.put(r.rhea_bidirectional_id, r.rhea_master_id);
			master_bi.put(r.rhea_master_id, r.rhea_bidirectional_id);
		}
		System.out.println("map size = "+bi_master.size());
		String ontf = 
				//"src/main/resources/org/geneontology/gocam/exchange/go.owl";
				"/Users/bgood/Documents/GitHub/go-ontology/src/ontology/go-edit.obo";
		//-edit.obo";
		//"/Users/bgood/git/noctua_exchange/exchange/src/main/resources/org/geneontology/gocam/exchange/go-plus-merged.owl";
		OWLOntologyManager mgr = OWLManager.createOWLOntologyManager();
		OWLDataFactory df = mgr.getOWLDataFactory();
		OWLOntology ont = mgr.loadOntologyFromOntologyDocument(new File(ontf));
		Set<OWLClass> classes = ont.getClassesInSignature();
		OWLAnnotationProperty xref = df.getOWLAnnotationProperty(IRI.create("http://www.geneontology.org/formats/oboInOwl#hasDbXref"));
		OWLAnnotationProperty exact_synonym = df.getOWLAnnotationProperty(IRI.create("http://www.geneontology.org/formats/oboInOwl#hasExactSynonym"));
		OWLAnnotationProperty rdfslabel = df.getOWLAnnotationProperty(OWLRDFVocabulary.RDFS_LABEL.getIRI());
		OWLAnnotationProperty rdfscomment = df.getOWLAnnotationProperty(OWLRDFVocabulary.RDFS_COMMENT.getIRI());
		OWLAnnotationProperty definition = df.getOWLAnnotationProperty(IRI.create("http://purl.obolibrary.org/obo/IAO_0000115"));
		Set<OWLAxiom> new_id_axioms = new HashSet<OWLAxiom>();
		Set<OWLAxiom> delete_axioms = new HashSet<OWLAxiom>();
		for(OWLClass c : classes) {
			Collection<OWLAnnotationAssertionAxiom> aaa = EntitySearcher.getAnnotationAssertionAxioms(c, ont);
			for(OWLAnnotationAssertionAxiom a : aaa) {
				OWLAnnotation anno = a.getAnnotation();
				//top level xref assertions
				if(anno.getProperty().equals(xref)) {
					OWLAnnotationValue v = anno.getValue();
					String xref_id = v.asLiteral().get().getLiteral();
					if(xref_id.contains("RHEA:")) {		
						String new_id = bi_master.get(xref_id);
						if(new_id!=null) {
							OWLAnnotationValue new_value = df.getOWLLiteral(new_id);
							OWLAnnotation new_annotation = df.getOWLAnnotation(a.getProperty(), new_value);
							OWLAnnotationAssertionAxiom a2 = df.getOWLAnnotationAssertionAxiom(a.getSubject(), new_annotation);
							new_id_axioms.add(a2);		
							delete_axioms.add(a);
							MappingResult new_map = new MappingResult();
							new_map.action = "update";
							new_map.delete_xref_axiom = a;
							new_map.go_term = c.getIRI().toString();
							new_map.new_xref_axiom = a2;
							new_map.old_id = xref_id;
							new_map.reason = "direct mapping provided";
							new_map.replaced_by = new_id;
							new_map.property = "direct\txref";
							results.add(new_map);
						}else {
							MappingResult new_map = new MappingResult();
							new_map.action = "ignore";
							new_map.delete_xref_axiom = a;
							new_map.go_term = c.getIRI().toString();
							new_map.new_xref_axiom = null;
							new_map.old_id = xref_id;
							new_map.reason = "no mapping found";
							if(master_bi.get(xref_id)!=null) {
								new_map.reason = "already master";
							}
							new_map.replaced_by = "NA";
							new_map.property = "direct\txref";
							results.add(new_map);
						}
					}
				}
				//xrefs in definitions, synonyms etc.
				//	if(anno.getProperty().equals(definition)||anno.getProperty().equals(exact_synonym)) {
				else {
					Set<OWLAnnotation> anno_annos = a.getAnnotations(xref);
					boolean update = false;
					String new_id = "";
					Set<OWLAnnotation> annos_to_remove = new HashSet<OWLAnnotation>();
					Set<OWLAnnotation> annos_to_add = new HashSet<OWLAnnotation>();
					for(OWLAnnotation anno_anno : anno_annos) {
						String v = anno_anno.getValue().asLiteral().get().getLiteral();
						if(v.contains("RHEA:")) {
							String old_id = v;
							if(v.contains(".")) {
								old_id = v.substring(0, v.indexOf("."));
							}
							new_id = bi_master.get(old_id);
							if(new_id!=null) {
								update  = true;
								annos_to_remove.add(anno_anno);
								OWLAnnotationValue new_xref = df.getOWLLiteral(new_id);
								OWLAnnotation new_annotation = df.getOWLAnnotation(xref, new_xref);
								annos_to_add.add(new_annotation);
								//updating mapping
								MappingResult new_map = new MappingResult();
								new_map.action = "update";
								new_map.delete_xref_axiom = a;
								new_map.go_term = c.getIRI().toString();
								new_map.new_xref_axiom = null;
								new_map.old_id = old_id;
								new_map.reason = "direct mapping provided";
								new_map.replaced_by = new_id;
								new_map.property = "indirect\t"+anno.getProperty().toString();
								results.add(new_map);
							}else {
								//System.out.println("No mapping for\t"+anno.getProperty()+"\t"+old_id+"\t"+c);
								MappingResult no_map = new MappingResult();
								no_map.action = "ignore";
								no_map.delete_xref_axiom = a;
								no_map.go_term = c.getIRI().toString();
								no_map.new_xref_axiom = null;
								no_map.old_id = old_id;
								no_map.reason = "no mapping found";
								if(master_bi.get(old_id)!=null) {
									no_map.reason = "already master";
								}
								no_map.replaced_by = "NA";
								no_map.property = "indirect\t"+anno.getProperty().toString();
								results.add(no_map);
							}
						}
					}
					if(update) {
						anno_annos.removeAll(annos_to_remove);
						anno_annos.addAll(annos_to_add);
						delete_axioms.add(a);						
						OWLAnnotationValue definition_value = a.getValue();
						OWLAnnotation new_definition_annotation = df.getOWLAnnotation(a.getProperty(), definition_value);						
						OWLAnnotationAssertionAxiom a2 = df.getOWLAnnotationAssertionAxiom(a.getSubject(), new_definition_annotation, anno_annos);
						new_id_axioms.add(a2);
					}
				}


			}
		}
		System.out.println("deleting "+delete_axioms.size()+" anno axioms and adding "+new_id_axioms.size());

		//report
		FileWriter w = new FileWriter("/Users/bgood/Desktop/test/tmp/rhea_xref_update_report.txt");
		w.write("r.go_term\tr.old_id\tr.replaced_by\tr.action\tr.reason\tr.property\n");
		for(MappingResult r : results) {
			w.write(r.go_term+"\t"+r.old_id+"\t"+r.replaced_by+"\t"+r.action+"\t"+r.reason+"\t"+r.property+"\n");
		}
		w.close();

		mgr.removeAxioms(ont, delete_axioms);
		mgr.addAxioms(ont, new_id_axioms);
		//Helper.writeOntology("/Users/bgood/Desktop/test/tmp/go-rhea-test.owl", ont);
		Helper.writeOntologyAsObo("/Users/bgood/Documents/GitHub/go-ontology/src/ontology/go-edit.obo", ont);
		//deleting 28781 anno axioms and adding 2421
		//map size = 6365
		//		deleting 4117 anno axioms and adding 4117

	}

	public void updateReactomeXrefs() throws OWLOntologyCreationException, IOException, OWLOntologyStorageException {
		Set<MappingResult> results = new HashSet<MappingResult>();
		List<String> ascii_problems = new ArrayList<String>();
		String output_ontology_file = "/Users/bgood/Desktop/test/tmp/test-go-edit.obo";
		//"/Users/bgood/Documents/GitHub/go-ontology/src/ontology/go-edit.obo";
		String ontf = "/Users/bgood/Documents/GitHub/go-ontology/src/ontology/go-edit.obo";
		String report_file = "/Users/bgood/Desktop/test/tmp/xref_update_report.txt";
		//"src/main/resources/org/geneontology/gocam/exchange/go.owl";//-edit.obo";
		//"/Users/bgood/git/noctua_exchange/exchange/src/main/resources/org/geneontology/gocam/exchange/go-plus-merged.owl";
		String mapf = "src/main/resources/org/geneontology/gocam/exchange/StId_OldStId_Mapping_Human_AllObjects_v65.txt";
		//StId_OldStId_Mapping_AllSpecies_AllObjects_v65.txt";
		// StId_OldStId_Mapping_Human_AllObjects_v65.txt";
		// "StId_OldStId_Mapping_Human_Reactions_v65.txt";
		Map<String, String> old_new = new HashMap<String, String>();
		Map<String, String> old_label = new HashMap<String, String>();
		BufferedReader f = new BufferedReader(new FileReader(mapf));
		String line = f.readLine();
		line = f.readLine();//skip header
		while(line!=null) {
			String[] new_old_name_type = line.split("\t");
			old_new.put("Reactome:"+new_old_name_type[1], "Reactome:"+new_old_name_type[0]);
			old_label.put("Reactome:"+new_old_name_type[1], new_old_name_type[2]);
			line = f.readLine();
		}
		f.close();
		OWLOntologyManager mgr = OWLManager.createOWLOntologyManager();
		OWLDataFactory df = mgr.getOWLDataFactory();
		OWLOntology ont = mgr.loadOntologyFromOntologyDocument(new File(ontf));
		Set<OWLClass> classes = ont.getClassesInSignature();
		OWLAnnotationProperty xref = df.getOWLAnnotationProperty(IRI.create("http://www.geneontology.org/formats/oboInOwl#hasDbXref"));
		OWLAnnotationProperty exact_synonym = df.getOWLAnnotationProperty(IRI.create("http://www.geneontology.org/formats/oboInOwl#hasExactSynonym"));
		OWLAnnotationProperty rdfslabel = df.getOWLAnnotationProperty(OWLRDFVocabulary.RDFS_LABEL.getIRI());
		OWLAnnotationProperty rdfscomment = df.getOWLAnnotationProperty(OWLRDFVocabulary.RDFS_COMMENT.getIRI());
		OWLAnnotationProperty definition = df.getOWLAnnotationProperty(IRI.create("http://purl.obolibrary.org/obo/IAO_0000115"));
		Set<OWLAxiom> new_id_axioms = new HashSet<OWLAxiom>();
		Set<OWLAxiom> delete_axioms = new HashSet<OWLAxiom>();
		for(OWLClass c : classes) {
			Collection<OWLAnnotationAssertionAxiom> aaa = EntitySearcher.getAnnotationAssertionAxioms(c, ont);
			for(OWLAnnotationAssertionAxiom a : aaa) {
				OWLAnnotation anno = a.getAnnotation();
				//top level xref assertions
				if(anno.getProperty().equals(xref)) {
					OWLAnnotationValue v = anno.getValue();
					String xref_id = v.asLiteral().get().getLiteral();
					if(xref_id.contains(".")) {
						xref_id = xref_id.substring(0, xref_id.indexOf("."));
					}
					String new_id = old_new.get(xref_id);
					String reactome_xref_label = old_label.get(xref_id);
					Collection<OWLAnnotation> anno_annos = a.getAnnotations(rdfslabel);
					if(xref_id.contains("REACT_")) {
						//delete all old and all non-human annotations
						//some will be replaced if there is a mapping
						delete_axioms.add(a);
						String xref_label = null;
						for(OWLAnnotation anno_anno : anno_annos) {
							if(anno_anno.getProperty().equals(rdfslabel)) {
								OWLAnnotationValue vv = anno_anno.getValue();
								xref_label = vv.asLiteral().get().getLiteral();
								if(!xref_label.contains("Homo sapiens")&&new_id==null) {
									MappingResult no_map = new MappingResult();
									no_map.action = "delete";
									no_map.delete_xref_axiom = a;
									no_map.go_term = c.getIRI().toString();
									no_map.new_xref_axiom = null;
									no_map.old_id = xref_id;
									no_map.reason = "non-human";
									no_map.replaced_by = "NA";
									no_map.property = "direct\txref";
									results.add(no_map);
								}else {
									if(new_id==null) {
										System.out.println("No mapping for\txref\t"+xref_id+"\t"+c);
										MappingResult no_map = new MappingResult();
										no_map.action = "delete";
										no_map.delete_xref_axiom = a;
										no_map.go_term = c.getIRI().toString();
										no_map.new_xref_axiom = null;
										no_map.old_id = xref_id;
										no_map.reason = "no mapping provided";
										no_map.replaced_by = "NA";
										no_map.property = "direct\txref";
										results.add(no_map);
									}
								}
							}
						}
						if(new_id!=null) {
							//update annotation							
							//make annotation annotations..
							Set<OWLAnnotation> n_anno_annos = new HashSet<OWLAnnotation>();
							//OWLAnnotationValue new_comment_value = df.getOWLLiteral("Xref updated programmatically via Reactome-provided mapping file, see https://github.com/geneontology/go-ontology/issues/12518 ");
							//OWLAnnotation new_anno_comment = df.getOWLAnnotation(rdfscomment, new_comment_value);
							//n_anno_annos.add(new_anno_comment);
							if(reactome_xref_label!=null) {
								boolean is_ascii = Ascii.isPureAscii(reactome_xref_label);
								if(!is_ascii) {
									String replacement = Ascii.simpleGreekMap(reactome_xref_label);
									ascii_problems.add(reactome_xref_label+"\t"+replacement+"\t"+c.getIRI().toString());
									reactome_xref_label = replacement;
								}
								OWLAnnotationValue new_label_value = df.getOWLLiteral(reactome_xref_label);
								OWLAnnotation new_anno_label = df.getOWLAnnotation(rdfslabel, new_label_value);
								n_anno_annos.add(new_anno_label); 
							} 

							OWLAnnotationValue new_value = df.getOWLLiteral(new_id);
							OWLAnnotation new_annotation = df.getOWLAnnotation(a.getProperty(), new_value);

							OWLAnnotationAssertionAxiom a2 = df.getOWLAnnotationAssertionAxiom(a.getSubject(), new_annotation, n_anno_annos);
							new_id_axioms.add(a2);		

							MappingResult new_map = new MappingResult();
							new_map.action = "update";
							new_map.delete_xref_axiom = a;
							new_map.go_term = c.getIRI().toString();
							new_map.new_xref_axiom = a2;
							new_map.old_id = xref_id;
							new_map.reason = "direct mapping provided";
							new_map.replaced_by = new_id;
							new_map.property = "direct\txref";
							results.add(new_map);
						}
					}
				}
				//xrefs in definitions, synonyms etc.
				//	if(anno.getProperty().equals(definition)||anno.getProperty().equals(exact_synonym)) {
				else {
					Set<OWLAnnotation> anno_annos = a.getAnnotations(xref);
					boolean update = false;
					String new_reactome_id = "";
					Set<OWLAnnotation> annos_to_remove = new HashSet<OWLAnnotation>();
					Set<OWLAnnotation> annos_to_add = new HashSet<OWLAnnotation>();
					for(OWLAnnotation anno_anno : anno_annos) {
						String v = anno_anno.getValue().asLiteral().get().getLiteral();
						if(v.contains("REACT_")) {
							update  = true;
							//as above, all old reactome ids get removed.  
							//if there are mappings, they are replaced
							annos_to_remove.add(anno_anno);
							String old_id = v;
							if(v.contains(".")) {
								old_id = v.substring(0, v.indexOf("."));
							}
							new_reactome_id = old_new.get(old_id);
							if(new_reactome_id!=null) {
								OWLAnnotationValue new_xref = df.getOWLLiteral(new_reactome_id);
								OWLAnnotation new_annotation = df.getOWLAnnotation(xref, new_xref);
								annos_to_add.add(new_annotation);
								//								OWLAnnotationValue new_comment_value = df.getOWLLiteral("Reactome Xref(s) updated programmatically via Reactome-provided mapping file, see https://github.com/geneontology/go-ontology/issues/12518 ");
								//								OWLAnnotation new_anno_comment = df.getOWLAnnotation(rdfscomment, new_comment_value);
								//								annos_to_add.add(new_anno_comment);
								//updating mapping
								MappingResult new_map = new MappingResult();
								new_map.action = "update";
								new_map.delete_xref_axiom = a;
								new_map.go_term = c.getIRI().toString();
								new_map.new_xref_axiom = null;
								new_map.old_id = old_id;
								new_map.reason = "direct mapping provided";
								new_map.replaced_by = new_reactome_id;
								new_map.property = "indirect\t"+anno.getProperty().toString();
								results.add(new_map);
							}else {
								//System.out.println("No mapping for\t"+anno.getProperty()+"\t"+old_id+"\t"+c);
								MappingResult no_map = new MappingResult();
								no_map.action = "delete";
								no_map.delete_xref_axiom = a;
								no_map.go_term = c.getIRI().toString();
								no_map.new_xref_axiom = null;
								no_map.old_id = old_id;
								no_map.reason = "no mapping provided";
								no_map.replaced_by = "NA";
								no_map.property = "indirect\t"+anno.getProperty().toString();
								results.add(no_map);
							}
						}
					}
					if(update) {
						anno_annos.removeAll(annos_to_remove);
						anno_annos.addAll(annos_to_add);
						delete_axioms.add(a);						
						OWLAnnotationValue definition_value = a.getValue();
						OWLAnnotation new_definition_annotation = df.getOWLAnnotation(a.getProperty(), definition_value);						
						OWLAnnotationAssertionAxiom a2 = df.getOWLAnnotationAssertionAxiom(a.getSubject(), new_definition_annotation, anno_annos);
						new_id_axioms.add(a2);
					}
				}


			}
		}
		System.out.println("deleting "+delete_axioms.size()+" anno axioms and adding "+new_id_axioms.size());

		//report
		FileWriter w = new FileWriter(report_file);
		w.write("r.go_term\tr.old_react_id\tr.replaced_by\tr.action\tr.reason\tr.property\n");
		for(MappingResult r : results) {
			w.write(r.go_term+"\t"+r.old_id+"\t"+r.replaced_by+"\t"+r.action+"\t"+r.reason+"\t"+r.property+"\n");
		}
		w.close();

		mgr.removeAxioms(ont, delete_axioms);
		mgr.addAxioms(ont, new_id_axioms);
		Helper.writeOntologyAsObo(output_ontology_file, ont);
		//deleting 28781 anno axioms and adding 2421
		//deleting 28831 anno axioms and adding 2956
		//deleting 28837 anno axioms and adding 2962
		//deleting 28837 anno axioms and adding 2972

		System.out.println("Ascii problems "+ascii_problems.size());
		for(String a : ascii_problems) {
			System.out.println(a);
		}
	}

	public void dropReactomeXrefs() throws OWLOntologyCreationException, IOException, OWLOntologyStorageException {
		Set<MappingResult> results = new HashSet<MappingResult>();
		List<String> ascii_problems = new ArrayList<String>();
		String output_ontology_file = "/Users/bgood/Desktop/test/tmp/test-go-edit.obo";
		//"/Users/bgood/Documents/GitHub/go-ontology/src/ontology/go-edit.obo";
		String ontf = "/Users/bgood/Documents/GitHub/go-ontology/src/ontology/go-edit.obo";
		String report_file = "/Users/bgood/Desktop/test/tmp/xref_update_report.txt";
		//"src/main/resources/org/geneontology/gocam/exchange/go.owl";//-edit.obo";
		//"/Users/bgood/git/noctua_exchange/exchange/src/main/resources/org/geneontology/gocam/exchange/go-plus-merged.owl";

		OWLOntologyManager mgr = OWLManager.createOWLOntologyManager();
		OWLDataFactory df = mgr.getOWLDataFactory();
		OWLOntology ont = mgr.loadOntologyFromOntologyDocument(new File(ontf));
		Set<OWLClass> classes = ont.getClassesInSignature();
		OWLAnnotationProperty xref = df.getOWLAnnotationProperty(IRI.create("http://www.geneontology.org/formats/oboInOwl#hasDbXref"));
		OWLAnnotationProperty exact_synonym = df.getOWLAnnotationProperty(IRI.create("http://www.geneontology.org/formats/oboInOwl#hasExactSynonym"));
		OWLAnnotationProperty rdfslabel = df.getOWLAnnotationProperty(OWLRDFVocabulary.RDFS_LABEL.getIRI());
		OWLAnnotationProperty rdfscomment = df.getOWLAnnotationProperty(OWLRDFVocabulary.RDFS_COMMENT.getIRI());
		OWLAnnotationProperty definition = df.getOWLAnnotationProperty(IRI.create("http://purl.obolibrary.org/obo/IAO_0000115"));
		Set<OWLAxiom> new_id_axioms = new HashSet<OWLAxiom>();
		Set<OWLAxiom> delete_axioms = new HashSet<OWLAxiom>();
		for(OWLClass c : classes) {
			Collection<OWLAnnotationAssertionAxiom> aaa = EntitySearcher.getAnnotationAssertionAxioms(c, ont);
			for(OWLAnnotationAssertionAxiom a : aaa) {
				OWLAnnotation anno = a.getAnnotation();
				//top level xref assertions
				if(anno.getProperty().equals(xref)) {
					OWLAnnotationValue v = anno.getValue();
					String xref_id = v.asLiteral().get().getLiteral();
					if(xref_id.contains(".")) {
						xref_id = xref_id.substring(0, xref_id.indexOf("."));
					}
					Collection<OWLAnnotation> anno_annos = a.getAnnotations(rdfslabel);
					if(xref_id.contains("REACT_")) {
						//delete all previous Reactome xrefs
						delete_axioms.add(a);
						String xref_label = null;
						for(OWLAnnotation anno_anno : anno_annos) {
							if(anno_anno.getProperty().equals(rdfslabel)) {
								OWLAnnotationValue vv = anno_anno.getValue();
								xref_label = vv.asLiteral().get().getLiteral();
								MappingResult no_map = new MappingResult();
								no_map.action = "delete";
								no_map.delete_xref_axiom = a;
								no_map.go_term = c.getIRI().toString();
								no_map.new_xref_axiom = null;
								no_map.old_id = xref_id;
								no_map.reason = "non-human";
								no_map.replaced_by = "NA";
								no_map.property = "direct\txref";
								results.add(no_map);
							}
						}
					}
				}
				//xrefs in definitions, synonyms etc.
				//	if(anno.getProperty().equals(definition)||anno.getProperty().equals(exact_synonym)) {
				else {
					Set<OWLAnnotation> anno_annos = a.getAnnotations(xref);
					boolean update = false;
					String new_reactome_id = "";
					Set<OWLAnnotation> annos_to_remove = new HashSet<OWLAnnotation>();
					Set<OWLAnnotation> annos_to_add = new HashSet<OWLAnnotation>();
					for(OWLAnnotation anno_anno : anno_annos) {
						String v = anno_anno.getValue().asLiteral().get().getLiteral();
						if(v.contains("REACT_")) {
							update  = true;
							//as above, all old reactome ids get removed.  
							//if there are mappings, they are replaced
							annos_to_remove.add(anno_anno);
							String old_id = v;
							if(v.contains(".")) {
								old_id = v.substring(0, v.indexOf("."));
							}
							MappingResult no_map = new MappingResult();
							no_map.action = "delete";
							no_map.delete_xref_axiom = a;
							no_map.go_term = c.getIRI().toString();
							no_map.new_xref_axiom = null;
							no_map.old_id = old_id;
							no_map.reason = "no mapping provided";
							no_map.replaced_by = "NA";
							no_map.property = "indirect\t"+anno.getProperty().toString();
							results.add(no_map);
						}
					}
					if(update) {
						anno_annos.removeAll(annos_to_remove);
						delete_axioms.add(a);						
					}
				}


			}
		}
		System.out.println("deleting "+delete_axioms.size()+" anno axioms and adding "+new_id_axioms.size());

		//report
		FileWriter w = new FileWriter(report_file);
		w.write("r.go_term\tr.old_react_id\tr.replaced_by\tr.action\tr.reason\tr.property\n");
		for(MappingResult r : results) {
			w.write(r.go_term+"\t"+r.old_id+"\t"+r.replaced_by+"\t"+r.action+"\t"+r.reason+"\t"+r.property+"\n");
		}
		w.close();

		mgr.removeAxioms(ont, delete_axioms);
		mgr.addAxioms(ont, new_id_axioms);
		Helper.writeOntologyAsObo(output_ontology_file, ont);
		//deleting 28781 anno axioms and adding 2421
		//deleting 28831 anno axioms and adding 2956
		//deleting 28837 anno axioms and adding 2962
		//deleting 28837 anno axioms and adding 2972

		System.out.println("Ascii problems "+ascii_problems.size());
		for(String a : ascii_problems) {
			System.out.println(a);
		}
	}

}
