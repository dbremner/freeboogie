package freeboogie.tc;

import java.util.*;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import genericutils.SimpleGraph;

import freeboogie.ast.*;

/**
 * Collects information about global names.
 * Reports duplicate (global) names and cycles in type synonyms.
 * 
 * @author rgrig 
 */
@SuppressWarnings("unused") // many unused parameters
public class GlobalsCollector extends Transformer {
  
  // the namespace of user defined types
  private HashMap<String, TypeDecl> types = Maps.newHashMap();

  // used to detect cycles in type synonyms
  private SimpleGraph<String> typeSynonyms;
  private String currentTypeDecl;
  
  // the namespace of procedures and functions
  private HashMap<String, Procedure> procs = Maps.newHashMap();
  private HashMap<String, FunctionDecl> funcs = Maps.newHashMap();
  
  // the namespace of constants and global variables
  private HashMap<String, ConstDecl> consts = Maps.newHashMap();
  private HashMap<String, VariableDecl> vars = Maps.newHashMap();
  
  // the errors that were encountered
  private List<FbError> errors = Lists.newArrayList();
  
  private void reset() {
    types.clear();
    procs.clear();
    funcs.clear();
    consts.clear();
    vars.clear();
    errors.clear();
    typeSynonyms = new SimpleGraph<String>();
  }
  
  /**
   * Returns whether there where duplicated declarations.
   * @param d the AST to be processed
   * @return whether there are name clashes in the input
   */
  public List<FbError> process(Program d) {
    reset();
    d.eval(this);
    return errors;
  }
  
  // === name query functions ==
 
  /**
   * Gets the definition for a type name.
   * @param s the type name
   * @return the definition, or {@code null} if none
   */
  public TypeDecl typeDef(String s) {
    return types.get(s);
  }
  
  /**
   * Look up a procedure definition.
   * @param s the name of the procedure
   * @return the definition of the procedure, or {@code null} if not found
   */
  public Procedure procDef(String s) {
    return procs.get(s);
  }
  
  /**
   * Look up a function definition.
   * @param s the name of the function
   * @return the definition of the function, or {@code null} if not found
   */
  public FunctionDecl funDef(String s) {
    return funcs.get(s);
  }
  
  /**
   * Looks up an id in the global (constants and variables) namespace.
   * @param s the identifier
   * @return the definition, or {@code null} if not found; it can be
   *         a {@code ConstDecl} or a {@code VariableDecl}
   */
  public IdDecl idDef(String s) {
    IdDecl r = consts.get(s);
    if (r != null) return r;
    return vars.get(s);
  }
  
  // === functions to add name-to-def links and to check for duplicates ===
  
  // if s is in h then report an error at location l
  private <D extends Declaration> 
  void check(HashMap<String, D> h, String s, Ast l) {
    if (h.get(s) == null) return;
    errors.add(new FbError(FbError.Type.GB_ALREADY_DEF, l, s));
  }
  
  private void addTypeDef(String s, TypeDecl d) {
    check(types, s, d);
    types.put(s, d);
  }
  
  private void addProcDef(String s, Procedure d) {
    check(procs, s, d);
    check(funcs, s, d);
    procs.put(s, d);
  }
  
  private void addFunDef(String s, FunctionDecl d) {
    check(procs, s, d);
    check(funcs, s, d);
    funcs.put(s, d);
  }
  
  private void addConstDef(String s, ConstDecl d) {
    check(consts, s, d);
    check(vars, s, d);
    consts.put(s, d);
  }
  
  private void addVarDef(String s, VariableDecl d) {
    check(consts, s, d);
    check(vars, s, d);
    vars.put(s, d);
  }
  
  // === dump, for debug ===
  
  private <D extends Declaration> void dump(Map<String, D> h) {
    TreeMap<FileLocation, String> ordered = Maps.newTreeMap();
    for (Map.Entry<String, D> e : h.entrySet())
      ordered.put(e.getValue().loc(), e.getKey());
    for (Map.Entry<FileLocation, String> e : ordered.entrySet())
      System.out.println(e.getValue() + " " + e.getKey());
  }
  
  /** Dumps the internal data for debug. */
  public void dump() {
    System.out.println("\n*** User defined types ***");
    dump(types);
    System.out.println("\n*** Procedures and functions ***");
    dump(procs); dump(funcs);
    System.out.println("\n*** Constants and variables ***");
    dump(consts); dump(vars);
  }
  
  
  // === the visiting functions ===
  @Override public void see(TypeDecl typeDecl) {
    addTypeDef(typeDecl.name(), typeDecl);
    typeSynonyms.node(typeDecl.name());
    if (typeDecl.type() != null) {
      currentTypeDecl = typeDecl.name();
      typeDecl.type().eval(this);
    }
  }

  @Override public void see(ConstDecl constDecl) {
    addConstDef(constDecl.name(), constDecl);
  }

  @Override public void see(FunctionDecl function) {
    addFunDef(function.sig().name(), function);
  }

  @Override public void see(VariableDecl variableDecl) {
    addVarDef(variableDecl.name(), variableDecl);
  }

  @Override public void see(Procedure procedure) {
    addProcDef(procedure.sig().name(), procedure);
  }

  @Override public void see(UserType userType) {
    // We are in the rhs of a currentTypeDecl
    typeSynonyms.edge(currentTypeDecl, userType.name());
    AstUtils.evalListOfType(userType.typeArgs(), this);
  }
  
  // === visit methods that skip places that might contain local variable decls ===
  @Override public void see(Program program) {
    AstUtils.evalListOfTypeDecl(program.types(), this);
    AstUtils.evalListOfVariableDecl(program.variables(), this);
    AstUtils.evalListOfConstDecl(program.constants(), this);
    AstUtils.evalListOfFunctionDecl(program.functions(), this);
    AstUtils.evalListOfProcedure(program.procedures(), this);
    if (typeSynonyms.hasCycle()) {
      // TODO(radugrigore): Better error message.
      errors.add(new FbError(FbError.Type.TYPE_CYCLE, program));
    }
  }
}
