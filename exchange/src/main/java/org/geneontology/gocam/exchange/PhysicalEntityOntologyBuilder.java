/**
 * 
 */
package org.geneontology.gocam.exchange;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.biopax.paxtools.io.BioPAXIOHandler;
import org.biopax.paxtools.io.SimpleIOHandler;
import org.biopax.paxtools.model.Model;
import org.biopax.paxtools.model.level3.CellularLocationVocabulary;
import org.biopax.paxtools.model.level3.Complex;
import org.biopax.paxtools.model.level3.Dna;
import org.biopax.paxtools.model.level3.DnaRegion;
import org.biopax.paxtools.model.level3.Entity;
import org.biopax.paxtools.model.level3.EntityReference;
import org.biopax.paxtools.model.level3.Pathway;
import org.biopax.paxtools.model.level3.PhysicalEntity;
import org.biopax.paxtools.model.level3.Protein;
import org.biopax.paxtools.model.level3.Rna;
import org.biopax.paxtools.model.level3.RnaRegion;
import org.biopax.paxtools.model.level3.SmallMolecule;
import org.biopax.paxtools.model.level3.UnificationXref;
import org.biopax.paxtools.model.level3.XReferrable;
import org.biopax.paxtools.model.level3.Xref;
import org.geneontology.gocam.exchange.BioPaxtoGO.ImportStrategy;
import org.geneontology.gocam.exchange.idmapping.IdMapper;
import org.openrdf.repository.RepositoryException;
import org.openrdf.rio.RDFHandlerException;
import org.openrdf.rio.RDFParseException;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLClassExpression;
import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.semanticweb.owlapi.model.OWLObjectIntersectionOf;
import org.semanticweb.owlapi.model.OWLObjectUnionOf;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLOntologyStorageException;
import org.semanticweb.owlapi.search.EntitySearcher;

/**
 * Handle the conversion of physical entities proteins, protein complexes, sets, etc. from BioPAX files
 * into an ontology of physical entities suitable for loading into Noctua alongside Neo.  
 * @author bgood
 *
 */
public class PhysicalEntityOntologyBuilder {

	GOPlus goplus;
	String default_namespace_prefix;
	boolean preserve_sets_in_complexes = true;
	Map<String, OWLClassExpression> id_class_map;
	/**
	 * 
	 */
	public PhysicalEntityOntologyBuilder(GOPlus go_plus, String default_namespace_prefix_) {
		goplus = go_plus;
		default_namespace_prefix = default_namespace_prefix_;
		id_class_map = new HashMap<String, OWLClassExpression>();
	}

	/**
	 * @param args
	 * @throws OWLOntologyCreationException 
	 * @throws IOException 
	 * @throws RDFHandlerException 
	 * @throws RDFParseException 
	 * @throws RepositoryException 
	 * @throws OWLOntologyStorageException 
	 */
	public static void main(String[] args) throws OWLOntologyCreationException, IOException, OWLOntologyStorageException, RepositoryException, RDFParseException, RDFHandlerException {

		PhysicalEntityOntologyBuilder converter = new PhysicalEntityOntologyBuilder(new GOPlus(), "Reactome");

		String input_biopax = 
				"/Users/bgood/Desktop/test/biopax/SignalingByERBB2.owl";
		//Homo_sapiens_march25_2019
		String converted = 
				//"/Users/bgood/Desktop/test/go_cams/Wnt_complete_2018-";
				"/Users/bgood/Desktop/test/go_cams/";

		BioPAXIOHandler handler = new SimpleIOHandler();
		FileInputStream f = new FileInputStream(input_biopax);
		Model biopax_model = handler.convertFromOWL(f);

		String base_ont_title = "Reactome Physical Entities";
		String base_contributor = "https://orcid.org/0000-0002-7334-7852";
		String base_provider = "https://reactome.org";
		boolean add_lego_import = false;
		int n = 0;
		for (PhysicalEntity entity : biopax_model.getObjects(PhysicalEntity.class)){
			String model_id = entity.hashCode()+"";
			String iri = "http://model.geneontology.org/"+model_id;
			IRI ont_iri = IRI.create(iri);
			base_ont_title = entity.getDisplayName();
			if(base_ont_title==null) {
				base_ont_title = "no name for "+entity.getUri();
			}
			if(base_ont_title.equals("ERBB2 heterodimers")) {
				GoCAM go_cam = new GoCAM(ont_iri, base_ont_title, base_contributor, null, base_provider, add_lego_import);
				n++;
				//55S ribosome:mRNA:fMet-tRNA
				System.out.println(n+" defining "+base_ont_title+" "+entity.getModelInterface());
				converter.definePhysicalEntity(go_cam, entity, null, model_id);
				go_cam.qrunner = new QRunner(go_cam.go_cam_ont); 
				String name = base_ont_title;
				name = name.replaceAll("/", "-");	
				name = name.replaceAll(" ", "_");
				name = name.replaceAll(",", "_");
				if(name.length()>200) {
					name = name.substring(0, 200);
					name+="---";
				}
				String outfilename = converted+name+"_preserve_sets.ttl";
				go_cam.writeGoCAM_jena(outfilename, false);
			}
		}
		int n_objects = 0;
		for(String id : converter.id_class_map.keySet()) {
			System.out.println(id+" "+(n_objects++)+" "+converter.id_class_map.get(id));
		}
	}

	private OWLClassExpression definePhysicalEntity(GoCAM go_cam, PhysicalEntity entity, IRI this_iri, String model_id) throws IOException {
		String entity_id = BioPaxtoGO.getEntityReferenceId(entity);
		if(id_class_map.containsKey(entity_id)) {
			return id_class_map.get(entity_id);
		}		
		if(this_iri==null&&entity_id!=null) {	
			this_iri = IRI.create(GoCAM.base_iri+entity_id);
		}else if(this_iri==null&&entity_id==null) {			
			this_iri = GoCAM.makeGoCamifiedIRI(model_id, entity_id);
		}
		//add entity to ontology as a class, whatever it is
		OWLClass e = go_cam.df.getOWLClass(this_iri); 					
		//this allows linkage between different entities in the GO-CAM sense that correspond to the same thing in the BioPax sense
		go_cam.addUriAnnotations2Individual(e.getIRI(),GoCAM.skos_exact_match, IRI.create(entity.getUri()));		
		String entity_name = entity.getDisplayName();

		//attempt to localize the class 
		OWLClass go_loc_class = null;
		//OWLClassExpression occurs_in_exp = null;
		CellularLocationVocabulary loc = ((PhysicalEntity) entity).getCellularLocation();
		if(loc!=null) {			
			//dig out the GO cellular location and create a class construct for it
			String location_term = null;
			Set<Xref> xrefs = loc.getXref();

			for(Xref xref : xrefs) {
				if(xref.getModelInterface().equals(UnificationXref.class)) {
					UnificationXref uref = (UnificationXref)xref;	    			
					//here we add the referenced GO class as a type.  
					String db = uref.getDb().toLowerCase();
					if(db.contains("gene ontology")) {
						String uri = GoCAM.obo_iri + uref.getId().replaceAll(":", "_");						
						go_loc_class = goplus.getOboClass(uri, true);
						Set<XReferrable> refs = uref.getXrefOf();							
						for(XReferrable ref : refs) {
							location_term = ref.toString().replaceAll("CellularLocationVocabulary_", "");
							break;
						}
						if(location_term!=null) {							 
							//decide not to express this in the tbox for now.  
							//occurs_in_exp =	go_cam.df.getOWLObjectSomeValuesFrom(GoCAM.located_in, go_loc_class);
							//go_cam.addSubclassAssertion(e, occurs_in_exp, null);
							go_cam.addLiteralAnnotations2Individual(e.getIRI(), GoCAM.rdfs_comment, "located_in "+location_term);
							entity_name = entity_name+" ("+location_term+")";
						}
					}
				}
			}
			go_cam.addLabel(e, entity_name);
			//basically anything can be represented as a set of things
			//check for sets and add them if present
			if(entity.getMemberPhysicalEntity()!=null&&entity.getMemberPhysicalEntity().size()>0) {
				addSet(go_cam, model_id, entity, e);
			}
			
			//now get more specific type information
			//Complex 
			if(entity.getModelInterface().equals(Complex.class)) {
				Complex complex = (Complex)entity;	
				go_cam.addSubClassAssertion(e, GoCAM.go_complex);
				//get all known parts
				Set<PhysicalEntity> known_parts = complex.getComponent();
				Set<OWLClassExpression> owl_parts = new HashSet<OWLClassExpression>();
				//get set (interchangeable) parts
				Set<PhysicalEntity> set_parts = complex.getMemberPhysicalEntity();
				if(set_parts!=null&&set_parts.size()>0) {
					addSet(go_cam, model_id, complex, e);
				}
				for(PhysicalEntity part : known_parts) {
					OWLClassExpression owl_part = definePhysicalEntity(go_cam, part,null, model_id);
					OWLClassExpression has_part_exp = go_cam.df.getOWLObjectSomeValuesFrom(GoCAM.has_part, owl_part);
					owl_parts.add(has_part_exp);
				}
				//if just one, no need for intersection
				if(owl_parts.size()==1) {
					OWLClassExpression p = owl_parts.iterator().next();
					OWLAxiom eq_prot = go_cam.df.getOWLEquivalentClassesAxiom(e, p);
					go_cam.ontman.addAxiom(go_cam.go_cam_ont, eq_prot);
				}else if(owl_parts.size()>1) {					
					OWLObjectIntersectionOf complex_class = go_cam.df.getOWLObjectIntersectionOf(owl_parts);					
					OWLAxiom eq_intersect = go_cam.df.getOWLEquivalentClassesAxiom(e, complex_class);
					go_cam.ontman.addAxiom(go_cam.go_cam_ont, eq_intersect);
				}
			}
			//Protein (or often entity set)
			else if(entity.getModelInterface().equals(Protein.class)||entity.getModelInterface().equals(PhysicalEntity.class)) {
				String id = null;				
				if(entity.getModelInterface().equals(Protein.class)) {
					Protein protein = (Protein)entity;
					id = getUniprotProteinId(protein);
				}			
				if(id!=null) {
					//create the specific protein class
					OWLClass uniprotein_class = go_cam.df.getOWLClass(IRI.create(GoCAM.uniprot_iri + id)); 									
					go_cam.addSubclassAssertion(uniprotein_class, GoCAM.chebi_protein, null);	
					OWLAxiom eq_prot_loc = go_cam.df.getOWLEquivalentClassesAxiom(e, uniprotein_class);
					go_cam.ontman.addAxiom(go_cam.go_cam_ont, eq_prot_loc);
					//go_cam.addSubclassAssertion(e, GoCAM.chebi_protein, null);					
				}
			}
			//Dna (gene)
			else if(entity.getModelInterface().equals(Dna.class)) {
				Dna dna = (Dna)entity;
				go_cam.addSubClassAssertion(e, GoCAM.chebi_dna);	
				EntityReference entity_ref = dna.getEntityReference();	
				if(entity_ref!=null) {
					Set<Xref> p_xrefs = entity_ref.getXref();
					for(Xref xref : p_xrefs) {
						//In GO-CAM we almost always want to talk about proteins
						//if there is a uniprot identifier to use, use that before anything else.
						String db = xref.getDb().toLowerCase();
						String id = xref.getId();
						if(db.contains("uniprot")) {
							OWLClass uniprotein_class = go_cam.df.getOWLClass(IRI.create(GoCAM.uniprot_iri + id)); 
							go_cam.addSubclassAssertion(uniprotein_class, GoCAM.chebi_protein, null);
							go_cam.addSubClassAssertion(e, uniprotein_class);
						}
						//
						else if(xref.getModelInterface().equals(UnificationXref.class)) {
							UnificationXref uref = (UnificationXref)xref;	
							if(uref.getDb().equals("ENSEMBL")) {
								go_cam.addDatabaseXref(e, "ENSEMBL:"+id);
							}
						}
					}
				}
			}
			//rna 
			else if(entity.getModelInterface().equals(Rna.class)) {
				Rna rna = (Rna)entity;
				go_cam.addSubClassAssertion(e, GoCAM.chebi_rna);	
				EntityReference entity_ref = rna.getEntityReference();	
				if(entity_ref!=null) {
					Set<Xref> p_xrefs = entity_ref.getXref();
					for(Xref xref : p_xrefs) {
						//In GO-CAM we almost always want to talk about proteins
						//if there is a uniprot identifier to use, use that before anything else.
						String db = xref.getDb().toLowerCase();
						String id = xref.getId();
						if(db.contains("uniprot")) {
							OWLClass uniprotein_class = go_cam.df.getOWLClass(IRI.create(GoCAM.uniprot_iri + id)); 
							go_cam.addSubclassAssertion(uniprotein_class, GoCAM.chebi_protein, null);
							go_cam.addSubClassAssertion(e, uniprotein_class);
						}
						//
						else if(xref.getModelInterface().equals(UnificationXref.class)) {					
							UnificationXref uref = (UnificationXref)xref;	
							if(uref.getDb().equals("ENSEMBL")) {
								go_cam.addDatabaseXref(e, "ENSEMBL:"+id);
								//TODO if at some point go-cam decides to represent transcripts etc. then we'll update here to use the ensembl etc. ids.  
							}
						}
					}
				}
			}
			//SmallMolecule
			else if(entity.getModelInterface().equals(SmallMolecule.class)) {
				SmallMolecule mlc = (SmallMolecule)entity;
				EntityReference entity_ref = mlc.getEntityReference();	
				if(entity_ref!=null) {
					Set<Xref> p_xrefs = entity_ref.getXref();
					String chebi_id = null;
					//first scan for directly asserted chebis
					for(Xref xref : p_xrefs) {
						//# BioPAX4
						String db = xref.getDb();
						db = db.toLowerCase();
						if(db.contains("chebi")) {
							chebi_id = xref.getId().replace(":", "_");
							break; //TODO just stop at one for now
						}
					}

					//if no chebis look at any other ids and try to convert
					if(chebi_id==null) {
						for(Xref xref : p_xrefs) {
							String database = xref.getDb();
							String id = xref.getId();
							String map = IdMapper.map2chebi(database, id);
							if(map!=null) {
								chebi_id = map;
								break;
							}
						}
					}
					if(chebi_id!=null) {			
						String chebi_uri = GoCAM.obo_iri + chebi_id;
						OWLClass mlc_class = goplus.getOboClass(chebi_uri, true);
						if(goplus.isChebiRole(chebi_uri)) {
							go_cam.addSubclassAssertion(mlc_class, GoCAM.chemical_role, null);
							OWLNamedIndividual rolei = go_cam.makeAnnotatedIndividual(GoCAM.makeGoCamifiedIRI(model_id, entity_id+"_chemical"));
							go_cam.addTypeAssertion(rolei, mlc_class);									
							//assert entity here is a chemical instance
							go_cam.addSubclassAssertion(e, GoCAM.chemical_entity, null);
							//connect it to the role
							//	go_cam.addRefBackedObjectPropertyAssertion(e, GoCAM.has_role, rolei, dbids, GoCAM.eco_imported_auto, default_namespace_prefix, null, model_id);
							OWLClassExpression role_exp = go_cam.df.getOWLObjectSomeValuesFrom(GoCAM.has_role, (OWLClassExpression)mlc);
							go_cam.addSubclassAssertion(e, role_exp, null);
						}else { //presumably its a chemical entity if not a role								
							go_cam.addSubclassAssertion(mlc_class, GoCAM.chemical_entity, null);	
							//assert its a chemical instance
							go_cam.addSubclassAssertion(e, mlc_class, null);
						}
					}else {
						//no chebi so we don't know what it is (for Noctua) aside from being some kind of chemical entity
						go_cam.addSubclassAssertion(e, GoCAM.chemical_entity, null);
					}
				}
			}
		}
		if(entity_id!=null) {
			id_class_map.put(entity_id, e);
		}
		return e;
	}

	private void addSet(GoCAM go_cam, String model_id, PhysicalEntity entity_set, OWLClass e) throws IOException {
		Set<PhysicalEntity> parts_list = entity_set.getMemberPhysicalEntity();
		Set<OWLClassExpression> owl_parts = new HashSet<OWLClassExpression>();
		for(PhysicalEntity part : parts_list) {
			OWLClassExpression part_exp = definePhysicalEntity(go_cam, part, null, model_id);
			owl_parts.add(part_exp);
		}					
		if(owl_parts!=null) {			
			if(owl_parts.size()>1) {
				OWLObjectUnionOf union_exp = go_cam.df.getOWLObjectUnionOf(owl_parts);
				OWLAxiom eq_prot_set = go_cam.df.getOWLEquivalentClassesAxiom(e, union_exp);
				go_cam.ontman.addAxiom(go_cam.go_cam_ont, eq_prot_set);							
			}else if(owl_parts.size()==1){
				OWLClassExpression one_part = owl_parts.iterator().next();
				OWLAxiom eq_prot = go_cam.df.getOWLEquivalentClassesAxiom(e, one_part);
				go_cam.ontman.addAxiom(go_cam.go_cam_ont, eq_prot);
			}
		}
	}


	private String getUniprotProteinId(Protein protein) {
		String id = null;
		EntityReference entity_ref = protein.getEntityReference();	
		if(entity_ref!=null) {
			Set<Xref> p_xrefs = entity_ref.getXref();				
			for(Xref xref : p_xrefs) {
				if(xref.getModelInterface().equals(UnificationXref.class)) {
					UnificationXref uref = (UnificationXref)xref;
					String db = uref.getDb();
					db = db.toLowerCase();
					// #BioPAX4
					//Reactome uses 'UniProt', Pathway Commons uses 'uniprot knowledgebase'
					//WikiPathways often uses UniProtKB
					//fun fun fun !
					//How about URI here, please..?
					if(db.contains("uniprot")) {
						id = uref.getId();
						break;//TODO consider case where there is more than one id..
					}
				}
			}
		}
		return id;
	}

	public static void countPhysical(Model biopax_model) {
		int n_all = 0; int n_complex = 0; int n_set = 0; int n_protein = 0; int n_small_molecule = 0;
		int n_dna = 0; int n_rna = 0; int n_dna_region = 0;  int n_rna_region = 0;
		int n_other = 0; int n_physical = 0; int n_sets = 0;
		int n_sets_of_complexes = 0; int n_sets_of_sets = 0;
		Set<String> set_types = new HashSet<String>();
		for (PhysicalEntity e : biopax_model.getObjects(PhysicalEntity.class)){
			n_all++;
			if(!e.getMemberPhysicalEntity().isEmpty()) {
				n_sets++;
				set_types.add(e.getModelInterface().toString());
				for(PhysicalEntity member : e.getMemberPhysicalEntity()) {
					if(member instanceof Complex) {
						n_sets_of_complexes++;
						//System.out.println("Complex set "+e.getDisplayName()+" "+BioPaxtoGO.getEntityReferenceId(e));
						break;
					}
				}
				for(PhysicalEntity member : e.getMemberPhysicalEntity()) {
					if(!member.getMemberPhysicalEntity().isEmpty()) {
						n_sets_of_sets++;
						//System.out.println("Set set "+e.getDisplayName()+" "+BioPaxtoGO.getEntityReferenceId(e));
						break;
					}
				}
			}			
			if(e instanceof Complex) {
				n_complex++;
			}else if(e instanceof Protein) {
				n_protein++;
			}else if(e instanceof SmallMolecule) {
				n_small_molecule++;
			}else if(e instanceof Dna) {
				n_dna++;
			}else if(e instanceof Rna) {
				n_rna++;
			}else if(e instanceof DnaRegion) {
				n_dna_region++;
			}else if(e instanceof RnaRegion) {
				n_rna_region++;
			}else if(e.getModelInterface().equals(PhysicalEntity.class)){
				n_physical++;
			}else {
				n_other++;
				System.out.println(e.getModelInterface());
			}
		}
		System.out.println("n_all\tn_physical\tn_sets\tn_complex\tn_set\tn_protein\tn_small_molecule"
				+"\tn_dna\tn_rna\tn_dna_region\tn_rna_region\tn_other");
		System.out.println( n_all+"\t"+n_physical+"\t"+n_sets+"\t"+n_complex+"\t"+n_set+"\t"+n_protein+"\t"+n_small_molecule 
				+"\t"+n_dna+"\t"+n_rna+"\t"+n_dna_region+"\t"+n_rna_region 
				+"\t"+n_other);
		System.out.println("n_sets_of_complexes = "+n_sets_of_complexes+" n_sets_of_sets = "+n_sets_of_sets);
		System.out.println(set_types);

	}

}