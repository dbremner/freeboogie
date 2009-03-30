package freeboogie.vcgen;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import freeboogie.backend.SmtTerm;
import freeboogie.backend.TermBuilder;
import freeboogie.util.Id;

/**
 * Remove shared nodes from a dag, making it (almost) a tree.
 * Almost, as shared nodes which start a dag below a certain size
 * are left intact.
 *
 * Original implementation in escjava.dfa.daganalysis.ReachabilityAnalysis
 * by Radu, Michal and Mikolas.
 *
 * @author Fintan 
 * @author reviewed by TODO
 */
public class DeSharifier implements FormulaProcessor<SmtTerm> {

  private TermBuilder<SmtTerm> builder;

  private Map<SmtTerm,Integer> parentsCount;
  private Map<SmtTerm,SmtTerm> dagToVarCache;
  private Map<SmtTerm,SmtTerm> varToTreeCache;
  private Map<SmtTerm,Boolean> varToParityMap;
  private Map<SmtTerm,Boolean> parityMismatchMap;

  public void setBuilder(TermBuilder<SmtTerm> termBuilder) {
    this.builder = termBuilder;
  }

  public SmtTerm process(SmtTerm t) {
    return dagToTree(t);
  }

  private void initialise(SmtTerm topLevelNode) {
    dagToVarCache = new HashMap<SmtTerm,SmtTerm>();
    varToTreeCache = new HashMap<SmtTerm,SmtTerm>();
    varToParityMap = new HashMap<SmtTerm,Boolean>();
    parityMismatchMap = new HashMap<SmtTerm,Boolean>();

    //count the parents...
    parentsCount = new HashMap<SmtTerm,Integer>();
    countParents(topLevelNode, new HashSet<SmtTerm>(), parentsCount);
  }

  /**
   * Gets (an approximation of) the print size of a tree.
   * @param tree 
   * @param treeSizeCache 
   * @return a
   */
  public static int getSize(final SmtTerm tree, Map<SmtTerm,Integer> treeSizeCache) {
    final Integer s = treeSizeCache.get(tree);
    if (s != null) return s;
    int sz = 1;
    for (SmtTerm child : tree.children) {
      sz += getSize(child, treeSizeCache);
    }
    treeSizeCache.put(tree, sz);
    return sz;
  }

  private static boolean isOrAnd(final SmtTerm t) {
    return t.id.equals("and") || t.id.equals("or") || t.id.equals("implies");
  }

  private static void countParents(final SmtTerm node, final Set<SmtTerm> seenNodes, final Map<SmtTerm,Integer> parentsCount) {
    if (seenNodes.contains(node)) return;
    seenNodes.add(node);
    for (SmtTerm child : node.children) {
      if (!isOrAnd(child)) continue;
      increaseParentsCount(child, parentsCount);
      countParents(child, seenNodes, parentsCount);
    }
  }

  private static void increaseParentsCount(final SmtTerm node, final Map<SmtTerm,Integer> parentsCount) {
    parentsCount.put(node, getParentsCount(node,parentsCount)+1);
  }

  private static int getParentsCount(final SmtTerm node, final Map<SmtTerm,Integer> parentsCount) {
    final Integer count = parentsCount.get(node);
    return count == null ? 0 : count;
  }

  private int getParentsCount(final SmtTerm node) {
    return getParentsCount(node, parentsCount);
  }

  private Set<SmtTerm> getUsedVars(SmtTerm e) {
    Set<SmtTerm> usedVars = new HashSet<SmtTerm>();
    recGetUsedVars(e, new HashSet<SmtTerm>(), usedVars);
    return usedVars;
  }

  private void recGetUsedVars(SmtTerm e, Set<SmtTerm> seenExprs, Set<SmtTerm> usedVariables) {
    if (seenExprs.contains(e)) return;
    seenExprs.add(e);
    for (SmtTerm c : e.children) {
      if (isOrAnd(c)) { 
        recGetUsedVars(c, seenExprs, usedVariables);
      } else if (isVariableAccess(c)) {
        SmtTerm tree = varToTreeCache.get(c);
        if (tree != null) {
          usedVariables.add(c);
          recGetUsedVars(tree, seenExprs, usedVariables);
        }
      }
    }
  }

  private static boolean isVariableAccess(SmtTerm t) {
    return t.id.equals("var_formula");
  }

  private static final int THRESHOLD = 0;
  private SmtTerm recDagToTree(final SmtTerm dag, Map<SmtTerm,Integer> treeSizeCache, boolean parity) {
    //Assert.notFalse(isOrAnd(dag));

    SmtTerm v = dagToVarCache.get(dag);
    if (v != null) {
      setParity(v, parity);
      return varToTreeCache.get(v);
    }

    final ArrayList<SmtTerm> newChildren = new ArrayList<SmtTerm>(dag.children.size()); // new children vector
    for (int i=0; i < dag.children.size(); i++) {
      SmtTerm child = dag.children.get(i);
      if (!isOrAnd(child)) {
        newChildren.add(child);
      } else {
        if (i == 0 && dag.id.equals("implies")) {
          //Flip the parity
          parity = !parity;
        } 
        final SmtTerm newChild = recDagToTree(child, treeSizeCache, parity); // new (plucked) child
        final int newChildSize = getSize(newChild, treeSizeCache); // print size of new child
        final int numberOfParents = getParentsCount(child); // parents count
        if (newChildSize * (numberOfParents - 1) <= numberOfParents + THRESHOLD) {
          newChildren.add(newChild);
        } else {
          newChildren.add(dagToVarCache.get(child));
        }
      }
    }

    SmtTerm tree;
    if (dag.data == null) {
      tree = builder.mk(dag.id, newChildren);
    } else {
      tree = dag;
    }
    final SmtTerm var = builder.mk("var_formula", Id.get("unshared"));
    dagToVarCache.put(dag, var);
    varToTreeCache.put(var, tree);
    setParity(var, parity);
    return tree;
  }

  private void setParity(SmtTerm var, boolean parity) {
    Boolean previousParity = varToParityMap.get(var);
    if (previousParity != null) {
      if (previousParity != parity) {
        parityMismatchMap.put(var, true);
      }
    }
    varToParityMap.put(var, parity);
  }
  
  private boolean parityMismatch(SmtTerm var) {
    return parityMismatchMap.get(var) == null ? false : true;
  }
 
  private SmtTerm dagToTree(SmtTerm dag) {
    initialise(dag);
    return recDagToTree(dag, new HashMap<SmtTerm,Integer>(), true);
  }

  public List<SmtTerm> getAxioms(SmtTerm pluckedDag) {
    //get the used variables and add their definitions
    Set<SmtTerm> usedVariables = getUsedVars(pluckedDag);

    List<SmtTerm> axioms = new ArrayList<SmtTerm>(usedVariables.size());

    for (SmtTerm v : usedVariables) {
      SmtTerm def = varToTreeCache.get(v);
      if (parityMismatch(v)) {
        axioms.add(builder.mk("iff", v, def));
      } else {
        if (varToParityMap.get(v)) {
          axioms.add(builder.mk("implies", v, def));
        } else {
          axioms.add(builder.mk("implies", def, v));
        }
      }
    }
    return axioms;
  }



}
