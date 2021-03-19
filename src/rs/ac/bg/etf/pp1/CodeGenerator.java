package rs.ac.bg.etf.pp1;

import java.util.ArrayList;
import java.util.Stack;

import org.apache.log4j.Logger;

import rs.ac.bg.etf.pp1.CounterVisitor.FormParamCounter;
import rs.ac.bg.etf.pp1.CounterVisitor.VarCounter;
import rs.ac.bg.etf.pp1.ast.*;
import rs.ac.bg.etf.pp1.ast.VisitorAdaptor;
import rs.etf.pp1.mj.runtime.Code;
import rs.etf.pp1.symboltable.Tab;
import rs.etf.pp1.symboltable.concepts.Obj;
import rs.etf.pp1.symboltable.concepts.Scope;
import rs.etf.pp1.symboltable.concepts.Struct;

public class CodeGenerator extends VisitorAdaptor {
	
	private int mainPc;
	private int currentMulop = 0;
	private int currentAddop = 0;
	private int currentRelop = 0;
    public static ArrayList<Integer> currentCondFalse = new ArrayList<>();
    public static ArrayList<Integer> currentCondTrue = new ArrayList<>();
    public static ArrayList<Integer> currentTernaryCondFalse = new ArrayList<>();
    public static ArrayList<Integer> endOfTernary = new ArrayList<>();
    public static ArrayList<Integer> endOfElse = new ArrayList<>();
    private Stack<Integer> doAdr = new Stack<Integer>();
    //public static ArrayList<Integer> doAdr = new ArrayList<>();
    public static ArrayList<Integer> breakJumpsAdr = new ArrayList<>();
    
	public int getMainPc() {
		return mainPc;
	}	
	
	
	public void visit(MethodTypeName methodTypeName)
	{
		if("main".equalsIgnoreCase(methodTypeName.getMethName()))
		{
			mainPc = Code.pc;
		}
		
		methodTypeName.obj.setAdr(Code.pc);
	
		//Collect arguments and local variables 
	 	SyntaxNode methodNode = methodTypeName.getParent();
	 	
	 	VarCounter varCnt = new VarCounter();
	 	methodNode.traverseTopDown(varCnt);
	 	
	 	FormParamCounter fpCnt = new FormParamCounter();
	 	methodNode.traverseTopDown(fpCnt);
	 	
	 	//Generate the entry
	 	Code.put(Code.enter);
	 	Code.put(fpCnt.getCount());
	 	Code.put(fpCnt.getCount() + varCnt.getCount());
	}
	
	public void visit(MethodDecl methodDecl)
	{
		Code.put(Code.exit);
		Code.put(Code.return_);
	}
	
	//da li ovako treba read???/
	public void visit(StatementRead sRead)
	{
		Obj designatorObj = sRead.getDesignator().obj;
		Struct designatorType = designatorObj.getType();
		
		
		if(sRead.getDesignator().obj.getKind() == Obj.Elem || sRead.getDesignator().obj.getKind() == Obj.Var)
		{
			if(designatorType.getKind() == Struct.Char)
			{
				Code.put(Code.bread);
			}
			else
			{
				Code.put(Code.read);
			}
		}				
		Code.store(designatorObj);
	}
	
	public void visit(StatementPrint sPrint)
	{
		if(sPrint.getExprTO().struct == Tab.charType)
		{
			Code.loadConst(1);
			Code.put(Code.bprint);
		}
		else
		{
			Code.loadConst(5);
			Code.put(Code.print);	
		}
	}
	
	public void visit(StatementPrintTwoParams sPrint) {
		int n = sPrint.getN2();
		for(int i = 0; i < n; i++) 
		{
			Code.put(Code.dup);
			if (sPrint.getExprTO().struct == Tab.charType) {				
				Code.loadConst(1);
				Code.put(Code.bprint);
			} else {
				Code.loadConst(5);
				Code.put(Code.print);
			}	
		}			
	}
	
	public void visit(DesignatorAssign designatorAssign)
	{
		if(designatorAssign.getDesignator().obj.getType().getKind() == Struct.Array)
		{
			Struct elemType = designatorAssign.getDesignator().obj.getType();
			if (elemType.getKind() == Struct.Char)
			{
				Code.put(Code.bastore);
			}  
			else if (elemType.getKind() == Struct.Array)
			{
				Code.store(designatorAssign.getDesignator().obj);
			}
			else 
			{
				Code.put(Code.astore);
			}
		}
		else 
		{
			Code.store(designatorAssign.getDesignator().obj);
		}
	}
	
	public void visit(DesignatorActPars funcCall)
	{
		Obj funcObj = funcCall.getDesignator().obj;
		int offset = funcObj.getAdr() - Code.pc;
		
		Code.put(Code.call);		
		Code.put2(offset);
		if(funcCall.getDesignator().obj.getType() != Tab.noType)
		{
			Code.put(Code.pop);
		}
	}
	
	public void visit(DesignatorInc inc)
	{
		Obj designatorObj = inc.getDesignator().obj;
		
		if(inc.getDesignator().obj.getKind() == Obj.Var)
		{
			Code.put(Code.const_1);
			Code.put(Code.add);
			Code.store(designatorObj);
		}
		
		if(inc.getDesignator().obj.getKind() == Obj.Elem)
		{
			Code.put(Code.dup2);
			Code.put(Code.aload);			
			Code.put(Code.const_1);
			Code.put(Code.add);
			Code.put(Code.astore);
		}		
	}
	
	public void visit(DesignatorDec dec)
	{
		Obj designatorObj = dec.getDesignator().obj;
		
		if(dec.getDesignator().obj.getKind() == Obj.Var)
		{
			Code.put(Code.const_1);
			Code.put(Code.sub);
			Code.store(designatorObj);
		}
		
		if(dec.getDesignator().obj.getKind() == Obj.Elem)
		{
			Code.put(Code.dup2);
			Code.put(Code.aload);
			Code.put(Code.const_1);
			Code.put(Code.sub);
			Code.put(Code.astore);
		}
	}
	
	public void visit(StatementBreak statementBreak) {		
		
		Code.putJump(0);
		breakJumpsAdr.add(Code.pc-2);
	}
	
	public void visit(StatementContinue statementContinue)
	{				
		Code.putJump(doAdr.peek());		
	}
	
	public void visit(AfterDo afterDo)
	{
		doAdr.push(Code.pc);
	}
	
	public void visit(AfterWhile afterWhile)
	{
		Code.putJump(doAdr.peek());
		for(Integer address : breakJumpsAdr)
		{
			Code.fixup(address);
		}
		
		breakJumpsAdr = new ArrayList<Integer>();
		for(Integer address : currentCondFalse)
		{
			Code.fixup(address);
		}	
		currentCondFalse = new ArrayList<Integer>();
		doAdr.pop();
	}
	
	public void visit(StatementIf sIf)
	{
		if(!endOfElse.isEmpty())
		{
			Code.fixup(endOfElse.remove(0));
		}
		//endOfElse = new ArrayList<Integer>();
	}
	
	public void visit(ElsePart elsePart)
	{
		//Poslednja instrukcija u IF sekciji -> treba da se ugradi JMP na kraj ELSE grane.
		Code.putJump(0);
		endOfElse.add(Code.pc-2);
		
		//Pocetak ELSE sekcije
		
		for(Integer address : currentCondFalse)
		{
			Code.fixup(address);
		}
		currentCondFalse = new ArrayList<Integer>();
	}
	
	public void visit(NoElsePart noElsePart)
	{
		for(Integer address : currentCondFalse)
		{
			Code.fixup(address);
		}
		currentCondFalse = new ArrayList<Integer>();		
	}
	
	// Kraj uslova za IF iskaz
	public void visit(ConditionEnd conditionEnd)
	{
		if (conditionEnd.getParent() instanceof StatementIf) 
		{
            for (Integer address : currentCondTrue)
            {
                Code.fixup(address);
            }
            currentCondTrue = new ArrayList<>();
        }
	}
	
	public void visit(TernaryColon ternary)
	{
		//Drugi deo treba da skoci posle treceg
		Code.putJump(0);
		endOfTernary.add(Code.pc-2);
		
		//Pocetak treceg dela; Treci deo fixup-uje adresu Condition uslovu
		
		for(Integer adr: currentCondFalse)
		{
			Code.fixup(adr);
		}		
		currentCondFalse = new ArrayList<Integer>();
	}
	
	public void visit(TernaryEnd end)
	{
		for(Integer address : endOfTernary)
		{
			Code.fixup(address);
		}
		endOfTernary = new ArrayList<Integer>();		
	}
	public void visit(OrDummy or)
	{
		//Code.putFalseJump(currentRelop, 0);
		Code.put(Code.jmp);
		Code.put2(0-Code.pc+1);
		
		currentCondTrue.add(Code.pc-2);
		
		//Code.putJump(doAdr.get(0));
		for(Integer adr: currentCondFalse)
		{
			Code.fixup(adr);
		}
		
		currentCondFalse = new ArrayList<Integer>();
	}
	
	public void visit(CondFactExpr cond)
	{
		Code.put(Code.const_1);
		Code.putFalseJump(Code.eq, 0);
		currentCondFalse.add(Code.pc-2);
		
	}
	
	public void visit(CondFactRelop cond)
	{
		Code.putFalseJump(currentRelop, 0);
		currentCondFalse.add(Code.pc-2);
		
	}
	
	public void visit(TermMulop mul)
	{
		Code.put(currentMulop);
	}
	
	public void visit(FuncCall funcCall)
	{
		Obj funcObj = funcCall.getDesignator().obj;
		int offset = funcObj.getAdr() - Code.pc;
		Code.put(Code.call);		
		Code.put2(offset);
	}
	
	public void visit(FactorConst factorConst)
	{
		Code.load(factorConst.getConstValue().obj);
	}
	
	public void visit(FactorNewArray factorNewArray)
	{
		Struct type = factorNewArray.getType().struct;
		Code.put(Code.newarray);
		Code.put(type.getKind() == Struct.Char ? 0 : 1);
	}
	
	public void visit(FactorDesignator factorDesignator)
	{
		Obj des = factorDesignator.getDesignator().obj;

		if (factorDesignator.getDesignator() instanceof DesignatorExprTO)
		{
			Struct s = des.getType();
			
			if (s.getKind() == Struct.Char)
			{
				Code.put(Code.baload);
			} else
			{
				Code.put(Code.aload);
			}
		}

	}
	
	public void visit(DesignatorIdent designatorIdent)
	{
		SyntaxNode parent = designatorIdent.getParent();
		
		if(DesignatorAssign.class != parent.getClass() && FuncCall.class != parent.getClass())
		{
			Code.load(designatorIdent.obj);
		}
	}
		
	public void visit(NoExprTOOptional sReturn)
	{
		Code.put(Code.exit);
		Code.put(Code.return_);
	}
	
	public void visit(ExprTOOptionalBasic sReturn)
	{
		Code.put(Code.exit);
		Code.put(Code.return_);
	}
	
	public void visit(ExprAddop exprAddop)
	{
		if(exprAddop.getAddop() instanceof AddopPlus)
		{
			Code.put(Code.add);
		}
		else
		{
			Code.put(Code.sub);
		}
	}
	
	public void visit(ExprMinus minus)
	{
		Code.put(Code.neg);
	}
	
	public void visit(RelopDoubleEqual rDoubleEqual) {
		currentRelop = Code.eq;
    }

    public void visit(RelopDifferent rDifferent) {
    	currentRelop = Code.ne;
    }

    public void visit(RelopGreater rGreater) {
    	currentRelop = Code.gt;
    }

    public void visit(RelopGreaterEqual rGreaterEqual) {
    	currentRelop = Code.ge;
    }

    public void visit(RelopLess rLess) {
    	currentRelop = Code.lt;
    }

    public void visit(RelopLessEqual rLessEqual) {
        currentRelop = Code.le;
    }

    public void visit(AddopPlus addopPlus) {
    	currentAddop = Code.add;
    }

    public void visit(AddopMinus addopMinus) {
        currentAddop = Code.sub;
    }

    public void visit(MulopMul mulopMul) {
        currentMulop = Code.mul;
    }

    public void visit(MulopDiv mulopDiv) {
        currentMulop = Code.div;
    }

    public void visit(MulopMod mulopMod) {
        currentMulop = Code.rem;
    }
    
    
    private void generateCodeForPredefineMethodCHR() {
		Obj methodObj = Tab.chrObj;
		int numArgs = 1;
		int numLocls = methodObj.getLocalSymbols().size();

		methodObj.setAdr(Code.pc);

		// Ulazak u metodu i alociranje mesta za lokalne promenjive
		Code.put(Code.enter);
		Code.put(numArgs);
		Code.put(numLocls);

		Code.put(Code.load_n);

		Code.put(Code.exit);
		Code.put(Code.return_);
	}

	private void generateCodeForPredefineMethodORD() {
		Obj methodObj = Tab.ordObj;
		int numArgs = 1;
		int numLocls = methodObj.getLocalSymbols().size();

		methodObj.setAdr(Code.pc);

		// Ulazak u metodu i alociranje mesta za lokalne promenjive
		Code.put(Code.enter);
		Code.put(numArgs);
		Code.put(numLocls);

		Code.put(Code.load_n);

		Code.put(Code.exit);
		Code.put(Code.return_);
	}

	private void generateCodeForPredefineMethodLEN() {
		Obj methodObj = Tab.lenObj;
		int numArgs = 1;
		int numLocls = methodObj.getLocalSymbols().size();

		methodObj.setAdr(Code.pc);

		// Ulazak u metodu i alociranje mesta za lokalne promenjive
		Code.put(Code.enter);
		Code.put(numArgs);
		Code.put(numLocls);

		Code.put(Code.load_n);
		// ova instrukcija nam daje duzinu samog niza na expr steku, tako sto se skida
		// prethodno postavljena adresa i vraca duzina niza
		Code.put(Code.arraylength);

		Code.put(Code.exit);
		Code.put(Code.return_);
	}
	
	public void visit(ProgramName progName) {
		// Generisati kod predefinisanih metoda chr, ord, len
		generateCodeForPredefineMethodCHR();

		generateCodeForPredefineMethodORD();

		generateCodeForPredefineMethodLEN();

	}
}
