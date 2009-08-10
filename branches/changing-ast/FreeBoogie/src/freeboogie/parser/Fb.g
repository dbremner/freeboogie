// vim:ft=java:
grammar Fb;

@annotate { @SuppressWarnings("all") }

@header {
  package freeboogie.parser; 

  import java.math.BigInteger;

  import com.google.common.collect.ImmutableList;
  import com.google.common.collect.Lists;
  import genericutils.Id;

  import freeboogie.ast.*; 
  import freeboogie.tc.TypeUtils;
}
@lexer::header {
  package freeboogie.parser;
}

@parser::members {
  public String fileName = null; // the file being processed
  private FileLocation tokLoc(Token t) {
    return new FileLocation(fileName,t.getLine(),t.getCharPositionInLine()+1);
  }
  private FileLocation fileLoc(Ast a) {
    return a == null? FileLocation.unknown() : a.loc();
  }
  
  public boolean ok = true;

  private ImmutableList.Builder<Specification> specListBuilder;
  private boolean specFree;
  private ImmutableList.Builder<Block> blockListBuilder;
  
  @Override
  public void reportError(RecognitionException x) {
    ok = false;
    super.reportError(x);
  }

}

program returns [Program v]:
  decl* EOF;

decl:
    type_decl
  | axiom_decl
  | var_decl
  | const_decl
  | fun_decl
  | proc_decl
  | impl_decl
;

type_decl:
    'type' ID (',' ID)* ';';

axiom_decl:
    'axiom' type_vars e=expr ';' 
;

type_vars returns [ImmutableList<AtomId> v]:
    { $v=ImmutableList.of(); }
  | '<' id_list '>' { if (ok) $v=$id_list.v; }
;

var_decl:
    'var' one_var_decl (',' one_var_decl)* ';'
;

one_var_decl returns [VariableDecl v]:
    ID type_vars ':' type
;

const_decl:
    'const' one_const_decl (',' one_const_decl)* ';'
;

one_const_decl:
    'unique' ID ':' type
;

fun_decl:
    'function' signature ';'
;

proc_decl:
    'procedure' signature ';'? /*spec_list*/ body?
;

impl_decl:
    'implementation' signature body
;

signature returns [Signature v]:
  ID tv=type_vars '(' (a=opt_id_type_list)? ')' 
  ('returns' '(' (b=opt_id_type_list)? ')')?
    { if(ok) $v = Signature.mk($ID.text,$tv.v,$a.v,$b.v,tokLoc($ID)); }
;

spec_list returns [ImmutableList<Specification> v]:
      { specListBuilder = ImmutableList.builder(); }
    spec*
      { $v = specListBuilder.build(); }
;

spec:
      {specFree = false;}
    (f='free' {specFree = true;})? 
        (((r='requires' | e='ensures') tv=type_vars h=expr)
      { if(ok) 
          specListBuilder.add(
              Specification.mk(
                $tv.v,
                $r!=null? Specification.SpecType.REQUIRES : Specification.SpecType.ENSURES,
                $h.v,
                specFree,
                fileLoc($h.v))); }
  | ('modifies' modified_id (',' modified_id)*)) ';'
;

modified_id:
    atom_id
      { if (ok) 
          specListBuilder.add(
              Specification.mk(
                  null,
                  Specification.SpecType.MODIFIES,
                  $atom_id.v,
                  specFree,
                  fileLoc($atom_id.v))); }
;

body returns [Body v]:
  t='{' vl=var_decl_list b=block_list '}'
    { if(ok) $v = Body.mk($vl.v,$b.v,tokLoc(t)); }
;

var_decl_list returns [ImmutableList<VariableDecl> v]
scope {
	ImmutableList.Builder<VariableDecl> b_;
}
:
      { $var_decl_list::b_ = ImmutableList.builder(); }
    'var' d=one_var_decl {if (ok) $var_decl_list::b_.add($d.v);} 
    ((',' | ';' 'var') dd=one_var_decl 
      {if (ok) $var_decl_list::b_.add($dd.v);})* ';'
    { $v=$var_decl_list::b_.build(); }
;

block_list returns [ImmutableList<Block> v]:
      { blockListBuilder = ImmutableList.builder(); }
    block*
      { $v = blockListBuilder.build(); }
;

block
scope {
  ArrayList<Command> cmds;
}
: 
    id=ID ':' 
    {$block::cmds = Lists.newArrayList();}
    (command {if (ok) $block::cmds.add($command.v);})* 
    s=block_succ
    { if (ok) {
      if ($block::cmds.isEmpty()) 
        blockListBuilder.add(Block.mk($id.text,null,$s.v,tokLoc(id)));
      else {
        String n = $id.text, nn;
        for (int i = 0; i + 1 < $block::cmds.size(); ++i) {
          nn = Id.get("block");
          blockListBuilder.add(Block.mk(
            n,
            $block::cmds.get(i),
            i+1==$block::cmds.size()?$s.v:ImmutableList.of(AtomId.mk(nn,null)),
            fileLoc($block::cmds.get(i))));
          n = nn;
        }
      }
    }};

block_succ returns [ImmutableList<AtomId> v]:
    ('goto' s=id_list | 'return') ';' 
      { $v = s!=null? $s.v : ImmutableList.<AtomId>of(); }
;

command	returns [Command v]:
    a=atom_id i=index_list ':=' b=expr ';' 
      { if(ok) {
          // a[b][c][d][e]:=f --> a:=a[b:=a[b][c:=a[b][c][d:=a[b][c][d][e:=f]]]]
          // grows quadratically
          Expr rhs = $b.v;
          ArrayList<Atom> lhs = new ArrayList<Atom>();
          lhs.add($a.v.clone());
          for (int k = 1; k < $i.v.size(); ++k)
            lhs.add(AtomMapSelect.mk(lhs.get(k-1).clone(), $i.v.get(k-1)));
          for (int k = $i.v.size()-1; k>=0; --k)
            rhs = AtomMapUpdate.mk(lhs.get(k).clone(), ImmutableList.copyOf($i.v.get(k)), rhs);
          $v=AssignmentCmd.mk($a.v,rhs,fileLoc($a.v));
      }}
  | t='assert' tv=type_vars expr ';'
      { if(ok) $v=AssertAssumeCmd.mk(AssertAssumeCmd.CmdType.ASSERT,$tv.v,$expr.v,tokLoc($t)); }
  | t='assume' tv=type_vars expr ';'
      { if(ok) $v=AssertAssumeCmd.mk(AssertAssumeCmd.CmdType.ASSUME,$tv.v,$expr.v,tokLoc($t)); }
  | t='havoc' id_list ';'
      { if(ok) $v=HavocCmd.mk($id_list.v,tokLoc($t));}
  | t='call' call_lhs
    n=ID st=quoted_simple_type_list '(' (r=expr_list)? ')' ';'
      { if(ok) $v=CallCmd.mk($n.text,$st.v,$call_lhs.v,$r.v,tokLoc($t)); }
;

call_lhs returns [ImmutableList<AtomId> v]:
    (id_list ':=')=> il=id_list ':=' {$v=$il.v;}
  | {$v=null;}
;
	
index returns [ImmutableList<Expr> v]:
  '[' expr_list ']' { $v = $expr_list.v; }
;

/* {{{ BEGIN expression grammar.

   See http://www.engr.mun.ca/~theo/Misc/exp_parsing.htm
   for a succint presentation of how to implement precedence
   and associativity in a LL-grammar, the classical way.

   The precedence increases
     <==>
     ==>
     &&, ||
     =, !=, <, <=, >=, >, <:
     +, -
     *, /, %

   <==> is associative
   ==> is right associative
   Others are left associative.
   The unary operators are ! and -.
   Typechecking takes care of booleans added to integers 
   and the like.
 */

expr returns [Expr v]:
  l=expr_a {$v=$l.v;} 
    (t='<==>' r=expr_a {if(ok) $v=BinaryOp.mk(BinaryOp.Op.EQUIV,$v,$r.v,tokLoc($t));})*
;

expr_a returns [Expr v]: 
  l=expr_b {$v=$l.v;} 
    (t='==>' r=expr_a {if(ok) $v=BinaryOp.mk(BinaryOp.Op.IMPLIES,$v,$r.v,tokLoc($t));})?
;

// TODO: these do not keep track of location quite correctly
expr_b returns [Expr v]:
  l=expr_c {$v=$l.v;} 
    (op=and_or_op r=expr_c {if(ok) $v=BinaryOp.mk($op.v,$v,$r.v,fileLoc($r.v));})*
;

expr_c returns [Expr v]:
  l=expr_d {$v=$l.v;}
    (op=comp_op r=expr_d {if(ok) $v=BinaryOp.mk($op.v,$v,$r.v,fileLoc($r.v));})*
;

expr_d returns [Expr v]:
  l=expr_e {$v=$l.v;}
    (op=add_op r=expr_e {if(ok) $v=BinaryOp.mk($op.v,$v,$r.v,fileLoc($r.v));})*
;

expr_e returns [Expr v]: 
  l=expr_f {$v=$l.v;}
    (op=mul_op r=expr_f {if(ok) $v=BinaryOp.mk($op.v,$v,$r.v,fileLoc($r.v));})*
;

expr_f returns [Expr v]:
    m=atom ('[' idx=expr_list (':=' val=expr)? ']')?
      { if (ok) {
        if ($idx.v == null) 
          $v=$m.v;
        else if ($val.v == null) 
          $v=AtomMapSelect.mk($m.v,$idx.v,fileLoc($m.v));
        else 
          $v=AtomMapUpdate.mk($m.v,$idx.v,$val.v,fileLoc($m.v));
      }}
  | '(' expr ')' {$v=$expr.v;}
  | t='-' a=expr_f   {if(ok) $v=UnaryOp.mk(UnaryOp.Op.MINUS,$a.v,tokLoc($t));}
  | t='!' a=expr_f   {if(ok) $v=UnaryOp.mk(UnaryOp.Op.NOT,$a.v,tokLoc($t));}
;

and_or_op returns [BinaryOp.Op v]:
    '&&' { $v = BinaryOp.Op.AND; }
  | '||' { $v = BinaryOp.Op.OR; }
;

comp_op returns [BinaryOp.Op v]:
    '==' { $v = BinaryOp.Op.EQ; }
  | '!=' { $v = BinaryOp.Op.NEQ; }
  | '<'  { $v = BinaryOp.Op.LT; }
  | '<=' { $v = BinaryOp.Op.LE; }
  | '>=' { $v = BinaryOp.Op.GE; }
  | '>'  { $v = BinaryOp.Op.GT; }
  | '<:' { $v = BinaryOp.Op.SUBTYPE; }
;

add_op returns [BinaryOp.Op v]:
    '+' { $v = BinaryOp.Op.PLUS; }
  | '-' { $v = BinaryOp.Op.MINUS; }
;

mul_op returns [BinaryOp.Op v]:
    '*' { $v = BinaryOp.Op.MUL; }
  | '/' { $v = BinaryOp.Op.DIV; }
  | '%' { $v = BinaryOp.Op.MOD; }
;

atom returns [Atom v]:
    t='false' { if(ok) $v = AtomLit.mk(AtomLit.AtomType.FALSE,tokLoc($t)); }
  | t='true'  { if(ok) $v = AtomLit.mk(AtomLit.AtomType.TRUE,tokLoc($t)); }
  | t='null'  { if(ok) $v = AtomLit.mk(AtomLit.AtomType.NULL,tokLoc($t)); }
  | t=INT     { if(ok) $v = AtomNum.mk(new BigInteger($INT.text),tokLoc($t)); }
  |	t=ID ('<' st=quoted_simple_type_list '>')? 
              { if(ok) $v = AtomId.mk($t.text,$st.v,tokLoc($t)); }
    ('(' (p=expr_list?) ')'
              { if(ok) $v = AtomFun.mk($t.text,$st.v,$p.v,tokLoc($t)); }
    )?
  | t='old' '(' expr ')'
              { if(ok) $v = AtomOld.mk($expr.v,tokLoc($t)); }
  | t='cast' '(' expr ',' type ')'
              { if(ok) $v = AtomCast.mk($expr.v, $type.v,tokLoc($t)); }
  | t='(' a=quant_op b=id_type_list '::' c=attributes d=expr ')'
              { if(ok) $v = AtomQuant.mk($a.v,$b.v,$c.v,$d.v,tokLoc($t)); }
;

atom_id returns [AtomId v]:
    ID ('<' st=quoted_simple_type_list '>')?
      { if(ok) $v = AtomId.mk($ID.text,$st.v,tokLoc($ID)); }
;

// END of the expression grammar }}}
	
quant_op returns [AtomQuant.QuantType v]:
    'forall' { $v = AtomQuant.QuantType.FORALL; }
  | 'exists' { $v = AtomQuant.QuantType.EXISTS; }
;

attributes returns [ImmutableList<Attribute> v]
scope {
  ImmutableList.Builder<Attribute> builder;
}:
    { $attributes::builder = ImmutableList.builder(); }
    (attr {if (ok) $attributes::builder.add($attr.v);})*
    { $v = $attributes::builder.build(); }
;

attr returns [Attribute v]:
    a='{' (':' ID)? c=expr_list '}'
      { if(ok) $v=Attribute.mk($ID==null?"trigger":$ID.text,$c.v,tokLoc($a)); }
;


// {{{ BEGIN list rules 
	
expr_list returns [ImmutableList<Expr> v]
scope {
  ImmutableList.Builder<Expr> builder;
}:
    { $expr_list::builder = ImmutableList.builder(); }
    e=expr {if(ok)$expr_list::builder.add($e.v);} 
    (',' ee=expr {if(ok)$expr_list::builder.add($ee.v);})*
    { $v = $expr_list::builder.build(); }
;

id_list	returns [ImmutableList<AtomId> v]
scope {
  ImmutableList.Builder<AtomId> b_;
}
:	
      { $id_list::b_ = ImmutableList.builder(); }
    (atom_id { if (ok) $id_list::b_.add($atom_id.v); })*
      { $v = $id_list::b_.build(); }
;

quoted_simple_type_list returns [ImmutableList<Type> v]
scope {
  ImmutableList.Builder<Type> builder;
}:
      { $quoted_simple_type_list::builder = ImmutableList.builder(); }
    ('<' '`' t=simple_type {if(ok)$quoted_simple_type_list::builder.add($t.v);} 
    (',' '`' tt=simple_type {if(ok)$quoted_simple_type_list::builder.add($tt.v);})* '>')?
      { $v = $quoted_simple_type_list::builder.build(); }
;

simple_type_list returns [ImmutableList<Type> v]
scope {
  ImmutableList.Builder<Type> builder;
}:
      { $simple_type_list::builder = ImmutableList.builder(); }
    ('<' t=simple_type {if(ok)$simple_type_list::builder.add($t.v);} 
    (',' tt=simple_type {if(ok)$simple_type_list::builder.add($tt.v);})* '>')?
      { $v = $simple_type_list::builder.build(); }
;

opt_id_type_list returns [ImmutableList<VariableDecl> v]
scope {
  ImmutableList.Builder<VariableDecl> builder;
}:
    { $opt_id_type_list::builder = ImmutableList.builder(); }
    x=opt_id_type {if(ok)$opt_id_type_list::builder.add($x.v);} 
    (',' xx=opt_id_type {if(ok)$opt_id_type_list::builder.add($xx.v);})*
    { $v = $opt_id_type_list::builder.build(); }
;

opt_id_type returns [VariableDecl v]
scope {
  String n;
  ImmutableList<AtomId> tv;
}:
    { $opt_id_type::n = Id.get("unnamed"); 
      $opt_id_type::tv = ImmutableList.of(); }
    (ID {$opt_id_type::n = $ID.text;} type_vars {$opt_id_type::tv=$type_vars.v;} ':')? type
    {if (ok) $v=VariableDecl.mk(null,$opt_id_type::n,$type.v,$opt_id_type::tv,fileLoc($type.v));}
;

id_type_list returns [ImmutableList<VariableDecl> v]
scope {
  ImmutableList.Builder<VariableDecl> builder;
}:
    x=id_type { if(ok) $id_type_list::builder.add($x.v); }
    (',' xx=id_type {if (ok) $id_type_list::builder.add($xx.v);})*
    {$v=$id_type_list::builder.build();}
;

id_type returns [VariableDecl v]:
    i=ID tv=type_vars ':' t=type
    {$v = VariableDecl.mk(null,$i.text,$t.v,$tv.v,tokLoc(i));}
;

command_list returns [ArrayList<Command> v]:
  {if (ok) $v = new ArrayList<Command>();}
  (c=command {if (ok) $v.add($c.v);})*
;

index_list returns [ArrayList<ImmutableList<Expr>> v]:
  { $v = Lists.newArrayList(); }
  (i=index {$v.add($i.v);})*
;
	
// END list rules }}}


simple_type returns [Type v]:
    t='bool' { if(ok) $v = PrimitiveType.mk(PrimitiveType.Ptype.BOOL,-1,tokLoc($t)); }
  | t='int'  { if(ok) $v = PrimitiveType.mk(PrimitiveType.Ptype.INT,-1,tokLoc($t)); }
  | t='ref'  { if(ok) $v = PrimitiveType.mk(PrimitiveType.Ptype.REF,-1,tokLoc($t)); }
  | t='name' { if(ok) $v = PrimitiveType.mk(PrimitiveType.Ptype.NAME,-1,tokLoc($t)); }
  | t='any'  { if(ok) $v = PrimitiveType.mk(PrimitiveType.Ptype.ANY,-1,tokLoc($t)); }
  | t=ID     { if(ok) $v = UserType.mk($ID.text,null,tokLoc($t)); }
  | t='[' it=simple_type_list ']' et=simple_type
             { if(ok) $v = MapType.mk($it.v,$et.v,tokLoc($t)); }
  | t='<' p=simple_type '>' st=simple_type
             { if(ok) $v = IndexedType.mk($p.v,$st.v,tokLoc($t)); }
;

type returns [Type v]:
  t=simple_type ('where' p=expr)?
    {  if (ok) {
         if ($p.v==null) $v=$t.v;
         else $v=DepType.mk($t.v,$p.v,fileLoc($t.v));
    }}
;
	
ID:
  ('a'..'z'|'A'..'Z'|'\''|'~'|'#'|'$'|'.'|'?'|'_'|'^'|'\\')
  ('a'..'z'|'A'..'Z'|'\''|'~'|'#'|'$'|'.'|'?'|'_'|'^'|'`'|'0'..'9')*
;
	
INT     : 	'0'..'9'+ ;
WS      : 	(' '|'\t'|'\n'|'\r')+ {$channel=HIDDEN;};
COMMENT
    :   '/*' ( options {greedy=false;} : . )* '*/' {$channel=HIDDEN;}
    ;

LINE_COMMENT
    : '//' ~('\n'|'\r')* ('\r'|'\n') {$channel=HIDDEN;}
    ;
