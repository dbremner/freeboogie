package freeboogie.experiments.graphgen;
import java.util.Collection;
import java.util.LinkedHashSet;

public class Generator<T> {

  private final double splitProbability;
  private final double linkProbability; 
  
  private final Collection<Node<T>> allNodesList;
  
  public Generator(double splitProbability, double linkProbability) {
    allNodesList = new LinkedHashSet<Node<T>>();
    
    this.splitProbability = splitProbability;
    this.linkProbability = linkProbability + splitProbability;
  }
  
  public Graph<T> generate(int depth, int maxDepth, int maxNodes, PayloadCreator<T> creator, Counter counter) {
    
    if (depth >= maxDepth) {
//      System.out.println("Reached max depth");
      return singleNodeGraph(creator, counter);
    } else if (counter.getCount() > maxNodes) {
      System.out.println("Reached max nodes");
      return singleNodeGraph(creator, counter);
    }
    
    return randomlyGenerate(depth, maxDepth, maxNodes, creator, counter);
//    return splitGraph(depth, maxDepth, maxNodes, creator, counter);
//    return linkGraph(depth, maxDepth, maxNodes, creator, counter);
  }
  
  public Graph<T> randomlyGenerate(int depth, int maxDepth, int maxNodes, PayloadCreator<T> creator, Counter counter) {
    
    double randomDouble = Main.random.nextDouble();
    
    if (randomDouble < splitProbability) {
      return splitGraph(depth, maxDepth, maxNodes, creator, counter);
    } else if (randomDouble < linkProbability) {
      return linkGraph(depth, maxDepth, maxNodes, creator, counter);
    } else {
      return singleNodeGraph(creator, counter);
    }
  }
  
  public Node<T> singleNode(PayloadCreator<T> creator, Counter counter) {
    Node<T> result = new Node<T>(creator.createPayload(), counter);
    allNodesList.add(result);
    return result;
  }
  
  public Graph<T> singleNodeGraph(PayloadCreator<T> creator, Counter counter) {
    //Node<T> node = singleNode(creator, counter);
    //return new Graph<T>(node, node);
    Node<T> node1 = singleNode(creator, counter);
    Node<T> node2 = singleNode(creator, counter);
    node1.addSuccessor(node2);
    return new Graph<T>(node1,node2);
  }

  public Graph<T> linkGraph(int depth, int maxDepth, int maxNodes, PayloadCreator<T> creator, Counter counter) {
    Graph<T> graph1 = generate(depth+1, maxDepth, maxNodes, creator, counter);
    Graph<T> graph2 = generate(depth+1, maxDepth, maxNodes, creator, counter);
    
    //graph1.getEndNode().addSuccessor(graph2.getStartNode());
    graph1.getEndNode().join(graph2.getStartNode(), counter, allNodesList);
    
    //TODO is this safe?
    //it should be, since now graphs cannot have size less than 2.
    return graph1.join(graph2); 
  }
  
  public Graph<T> splitGraph(int depth, int maxDepth, int maxNodes, PayloadCreator<T> creator, Counter counter) {
    Graph<T> graph1 = generate(depth+1, maxDepth, maxNodes, creator, counter);
    Graph<T> graph2 = generate(depth+1, maxDepth, maxNodes, creator, counter);
    Graph<T> graph3 = generate(depth+1, maxDepth, maxNodes, creator, counter);
    Graph<T> graph4 = generate(depth+1, maxDepth, maxNodes, creator, counter);
    
    //graph1.getEndNode().addSuccessor(graph2.getStartNode()); // 1->2
    Node<T> joined1 = graph1.getEndNode().join(graph2.getStartNode(), counter, allNodesList);
    //graph1.getEndNode().addSuccessor(graph3.getStartNode()); // 1->3
    joined1.join(graph3.getStartNode(), counter, allNodesList);
    //graph2.getEndNode().addSuccessor(graph4.getStartNode()); // 2->4
    Node<T> joined2 = graph2.getEndNode().join(graph4.getStartNode(), counter, allNodesList);
    //graph3.getEndNode().addSuccessor(graph4.getStartNode()); // 3->4
    graph3.getEndNode().join(joined2, counter, allNodesList);
  
    //Graph<T> result = new Graph<T>(graph1.getStartNode(), graph4.getEndNode());
    
//    System.out.println(result);
    return graph1.join(graph4);
  }

  public Collection<Node<T>> getAllNodes() {
    return allNodesList;
  }
  
}
