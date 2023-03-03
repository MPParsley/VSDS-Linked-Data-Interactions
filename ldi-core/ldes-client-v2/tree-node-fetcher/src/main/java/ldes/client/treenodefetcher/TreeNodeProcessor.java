package ldes.client.treenodefetcher;

import ldes.client.treenodefetcher.domain.entities.TreeNode;

import static org.apache.jena.rdf.model.ResourceFactory.createProperty;

public class TreeNodeProcessor {

	TreeNodeFetcher treeNodeFetcher = new TreeNodeFetcher();

	public TreeNode process(String treeNodeUrl) {
		return treeNodeFetcher.fetchFragment(treeNodeUrl);
	}



}