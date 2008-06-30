package freeboogie.vcgen;

import java.util.*;
import java.util.logging.Logger;

import freeboogie.ast.*;
import freeboogie.tc.*;
import freeboogie.util.*;

/**
 * Gets rid of assignments and "old" expressions by introducing 
 * new variables. We assume that
 *   (1) specs are desugared,
 *   (2) calls are desugared,
 *   (3) havocs are desugared,
 *   (4) the flowgraphs are computed and have no cycles.
 *
 * Each variable X is transformed into a sequence of variables
 * X, X_1, X_2, ... Each command has a read index r and a write
 * index w (for each variable), meaning that reads from X will be
 * replaced by reads from X_r and a write to X is replaced by a
 * write to X_w.
 *
 * We have:
 *   r(n) = max_{m BEFORE n} w(m)
 *   w(n) = 1 + r(n)   if n writes to X
 *   w(n) = r(n)       otherwise
 *
 * Copy operations (assumes) need to be inserted when the value
 * written by a node is not the same as the one read by one of
 * its successors (according the the scheme above).
 *
 * The "old()" is simply stripped.
 *
 * This algorithm minimizes the number of variables (think
 * coloring of comparison graphs) but not the number of copy
 * operations.
 *
 * TODO Introduce new variable declarations
 * TODO Change the out parameters of implementations to refer to the last version
 *
 * @author rgrig
 */
public class Passivator extends Transformer {
  // used mainly for debugging
  static private final Logger log = Logger.getLogger("freeboogie.vcgen");

  private TcInterface tc;
  private HashMap<VariableDecl, HashMap<Block, Integer>> readIdx;
  private HashMap<VariableDecl, HashMap<Block, Integer>> writeIdx;
  private HashMap<VariableDecl, Integer> newVarsCnt;
  private HashMap<Command, HashSet<VariableDecl>> commandWs;
  private ReadWriteSetFinder rwsf;

  private VariableDecl currentVar;
  private HashMap<Block, Integer> currentReadIdxCache;
  private HashMap<Block, Integer> currentWriteIdxCache;
  private SimpleGraph<Block> currentFG;
  private Block currentBlock;

  private Deque<Boolean> context; // true = write, false = read
  private int belowOld;

  // === public interface ===
  
  public Declaration process(Declaration ast, TcInterface tc) {
    this.tc = tc;
    readIdx = new LinkedHashMap<VariableDecl, HashMap<Block, Integer>>();
    writeIdx = new LinkedHashMap<VariableDecl, HashMap<Block, Integer>>();
    newVarsCnt = new LinkedHashMap<VariableDecl, Integer>();
    commandWs = new LinkedHashMap<Command, HashSet<VariableDecl>>();
    rwsf = new ReadWriteSetFinder(tc.getST());
    ast = (Declaration)ast.eval(this);
    for (Map.Entry<VariableDecl, Integer> e : newVarsCnt.entrySet())
      System.out.println(">" + e.getKey().getName() + ":" + e.getValue());
    // TODO Add new globals
    return ast;
  }

  // === (block) transformers ===

  @Override
  public Implementation eval(Implementation implementation, Signature sig, Body body, Declaration tail) {
    currentFG = tc.getFlowGraph(implementation);
    assert currentFG != null; // You should tell me the flowgraph beforehand
    if (currentFG.hasCycle()) {
      if (tail != null) tail = (Declaration)tail.eval(this);
      return Implementation.mk(sig, body, tail);
    }
    assert !currentFG.hasCycle(); // You should cut cycles first

    // Collect all variables that are assigned to
    Pair<CSeq<VariableDecl>, CSeq<VariableDecl>> rwIds = 
      implementation.eval(rwsf);
    HashSet<VariableDecl> allIds = new HashSet<VariableDecl>();
    for (VariableDecl vd : rwIds.second) allIds.add(vd);

    for (VariableDecl vd : allIds) {
      currentVar = vd;
      currentReadIdxCache = new LinkedHashMap<Block, Integer>();
      currentWriteIdxCache = new LinkedHashMap<Block, Integer>();
      readIdx.put(vd, currentReadIdxCache);
      writeIdx.put(vd, currentWriteIdxCache);
      currentFG.iterNode(new Closure<Block>() {
        @Override public void go(Block b) {
          compReadIdx(b); compWriteIdx(b);
        }
      });
    }

    context = new ArrayDeque<Boolean>();
    context.addFirst(false);
    currentBlock = null;
    belowOld = 0;
    body = (Body)body.eval(this);
    context = null;
    currentFG = null;

    if (tail != null) tail = (Declaration)tail.eval(this);
    return Implementation.mk(sig, body, tail);
  }


  // === workers ===

  private int compReadIdx(Block b) {
    if (currentReadIdxCache.containsKey(b))
      return currentReadIdxCache.get(b);
    int ri = 0;
    for (Block pre : currentFG.from(b))
      ri = Math.max(ri, compWriteIdx(pre));
    currentReadIdxCache.put(b, ri);
    return ri;
  }

  private int compWriteIdx(Block b) {
    if (currentWriteIdxCache.containsKey(b))
      return currentWriteIdxCache.get(b);
    int wi = compReadIdx(b);
    if (b.getCmd() != null) {
      HashSet<VariableDecl> ws = commandWs.get(b.getCmd());
      if (ws == null) {
        ws = new LinkedHashSet<VariableDecl>();
        System.out.println("> processing: " + b.getName());
        for (VariableDecl vd : rwsf.get(b.getCmd()).second) ws.add(vd);
        commandWs.put(b.getCmd(), ws);
      }
      if (ws.contains(currentVar)) ++wi;
    }
    currentWriteIdxCache.put(b, wi);
    int owi = newVarsCnt.containsKey(currentVar) ? newVarsCnt.get(currentVar) : 0;
    newVarsCnt.put(currentVar, Math.max(owi, wi));
    return wi;
  }


  // === visitors ===
  @Override
  public Block eval(Block block, String name, Command cmd, Identifiers succ, Block tail) {
    currentBlock = block;
    Command newCmd = cmd == null? null : (Command)cmd.eval(this);
    currentBlock = null;
    Block newTail = tail == null? null : (Block)tail.eval(this);
    if (newCmd != cmd || newTail != tail)
      block = Block.mk(name, newCmd, succ, newTail, block.loc());
    return block;
  }

  public AssertAssumeCmd eval(AssertAssumeCmd assertAssumeCmd, AssertAssumeCmd.CmdType type, Identifiers typeVars, Expr expr) {
    Expr newExpr = (Expr)expr.eval(this);
    if (newExpr != expr)
      return AssertAssumeCmd.mk(type, typeVars, newExpr);
    return assertAssumeCmd;
  }

  @Override
  public AssertAssumeCmd eval(AssignmentCmd assignmentCmd, AtomId lhs, Expr rhs) {
    AssertAssumeCmd result = null;
    Expr value = (Expr)rhs.eval(this);
    VariableDecl vd = (VariableDecl)tc.getST().ids.def(lhs);
    result = AssertAssumeCmd.mk(AssertAssumeCmd.CmdType.ASSUME, null,
        BinaryOp.mk(BinaryOp.Op.EQ,
          AtomId.mk(
            lhs.getId()+"$$"+getIdx(writeIdx, vd), 
            lhs.getTypes(), 
            lhs.loc()),
          value));
    return result;
  }

  @Override
  public Expr eval(AtomOld atomOld, Expr e) {
    ++belowOld;
    e = (Expr)e.eval(this);
    --belowOld;
    return e;
  }

  @Override
  public AtomId eval(AtomId atomId, String id, TupleType types) {
    if (currentBlock == null) return atomId;
    Declaration d = tc.getST().ids.def(atomId);
    if (!(d instanceof VariableDecl)) return atomId;
    VariableDecl vd = (VariableDecl)d;
    int idx = context.getFirst() ? getIdx(writeIdx, vd) : getIdx(readIdx, vd);
    if (idx == 0) return atomId;
    return AtomId.mk(id + "$$" + idx, types, atomId.loc());
  }

  @Override
  public VariableDecl eval(VariableDecl variableDecl, String name, Type type, Identifiers typeVars, Declaration tail) {
    int last = newVarsCnt.containsKey(variableDecl) ? newVarsCnt.get(variableDecl) : 0;
    Declaration newTail = tail == null? null : (Declaration)tail.eval(this);
    if (newTail != tail)
      variableDecl = VariableDecl.mk(name, type, typeVars, newTail, variableDecl.loc());
    for (int i = 1; i <= last; ++i)
      variableDecl = VariableDecl.mk(name+"$$"+i, type, typeVars, variableDecl);
    return variableDecl;
  }

  // === helpers ===
  private int getIdx(HashMap<VariableDecl, HashMap<Block, Integer> > cache, VariableDecl vd) {
    if (belowOld > 0) return 0;
    Map<Block, Integer> m = cache.get(vd);
    if (m == null) return 0; // this variable is never written to
    return m.get(currentBlock);
  }
}
