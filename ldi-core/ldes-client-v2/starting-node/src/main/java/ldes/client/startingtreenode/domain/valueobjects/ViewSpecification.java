package ldes.client.startingtreenode.domain.valueobjects;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;

import java.util.Optional;

import static org.apache.jena.rdf.model.ResourceFactory.createProperty;

public class ViewSpecification implements StartingNodeSpecification {
	public static final String TREE = "https://w3id.org/tree#";
	public static final Property TREE_VIEW = createProperty(TREE, "view");

	@Override
	public boolean test(Model model) {
		Optional<RDFNode> viewNode = model.listObjectsOfProperty(TREE_VIEW).nextOptional();
		return (viewNode.isPresent());
	}

	@Override
	public TreeNode extractStartingNode(Model model) {
		return model.listObjectsOfProperty(TREE_VIEW).nextOptional().map(RDFNode::asResource)
				.map(Resource::getURI)
				.map(TreeNode::new)
				.orElseThrow(() -> new RuntimeException("a"));
	}
}