package freeboogie.experiments.graphgen;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;


public class Generator<T> {

  private final Random random;
  private final double splitProbability;
  private final double linkProbability; 
  
  private final List<Node<T>> allNodesList;
  
  public Generator(double splitProbability, double linkProbability) {
    random = new Random();
    allNodesList = new LinkedList<Node<T>>();
    
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
    
    //Randomly choose
    return randomlyGenerate(depth, maxDepth, maxNodes, creator, counter);
//    return splitGraph(depth, maxDepth, maxNodes, creator, counter);
//    return linkGraph(depth, maxDepth, maxNodes, creator, counter);
  }
  
  public Graph<T> randomlyGenerate(int depth, int maxDepth, int maxNodes, PayloadCreator<T> creator, Counter counter) {
    
    double randomDouble = random.nextDouble();
    
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
    Node<T> node = singleNode(creator, counter);
    return new Graph<T>(node, node);
  }

  public Graph<T> linkGraph(int depth, int maxDepth, int maxNodes, PayloadCreator<T> creator, Counter counter) {
    Graph<T> graph1 = generate(depth+1, maxDepth, maxNodes, creator, counter);
    Graph<T> graph2 = generate(depth+1, maxDepth, maxNodes, creator, counter);
    
    graph1.getEndNode().addSuccessor(graph2.getStartNode());
    
    return graph1.join(graph2); // 1->2
  }
  
  public Graph<T> splitGraph(int depth, int maxDepth, int maxNodes, PayloadCreator<T> creator, Counter counter) {
    Graph<T> graph1 = generate(depth+1, maxDepth, maxNodes, creator, counter);
    Graph<T> graph2 = generate(depth+1, maxDepth, maxNodes, creator, counter);
    Graph<T> graph3 = generate(depth+1, maxDepth, maxNodes, creator, counter);
    Graph<T> graph4 = generate(depth+1, maxDepth, maxNodes, creator, counter);
    
    graph1.getEndNode().addSuccessor(graph2.getStartNode()); // 1->2
    graph1.getEndNode().addSuccessor(graph3.getStartNode()); // 1->3
    graph2.getEndNode().addSuccessor(graph4.getStartNode()); // 2->4
    graph3.getEndNode().addSuccessor(graph4.getStartNode()); // 3->4
  
    Graph<T> result = new Graph<T>(graph1.getStartNode(), graph4.getEndNode());
//    System.out.println(result);
    return result;
  }

  public List<Node<T>> getAllNodesList() {
    return allNodesList;
  }
  
}
