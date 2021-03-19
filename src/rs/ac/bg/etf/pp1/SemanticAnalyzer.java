package rs.ac.bg.etf.pp1;

import java.util.ArrayList;
import java.util.Stack;

import javax.smartcardio.TerminalFactory;

import org.apache.log4j.Logger;

import rs.ac.bg.etf.pp1.ast.*;
import rs.etf.pp1.symboltable.*;
import rs.etf.pp1.symboltable.concepts.*;

public class SemanticAnalyzer extends VisitorAdaptor {
	
	boolean errorDetected = false;
	private Struct currentType = Tab.noType;
	Obj currentMethod = null;
	boolean returnFound = false;
	boolean doWhileDetected = false;
	private int formParsCnt = 0;
//	private int actParsCnt = 0;
	int nVars;
	//private ArrayList<Struct> actParsList = new ArrayList<Struct>();
	private Stack<ArrayList<Struct>> currentActParTypesStack = new Stack<ArrayList<Struct>>();
	
	
    int methodCnt = 0;
    int globalVarCnt = 0;
    int globalConstCnt = 0;
    int globalArrayCnt = 0;
    int mainLocalVarCnt = 0;
    int mainStatementCnt = 0;
    int mainFuncCallCnt = 0;
	
	Logger log = Logger.getLogger(getClass());		
	
	public void report_error(String message, SyntaxNode info) {
		errorDetected = true;
		StringBuilder msg = new StringBuilder(message);
		int line = (info == null) ? 0: info.getLine();
		if (line != 0)
			msg.append (" na liniji ").append(line);
		log.error(msg.toString());
	}

	public void report_info(String message, SyntaxNode info) {
		StringBuilder msg = new StringBuilder(message); 
		int line = (info == null) ? 0: info.getLine();
		if (line != 0)
			msg.append (" na liniji ").append(line);
		log.info(msg.toString());
	}
	   
    public void visit(ProgramName programName)
    {
    	Obj progNode = Tab.find(programName.getProgramName());
    	if(progNode == Tab.noObj)
    	{
    		programName.obj = Tab.insert(Obj.Prog, programName.getProgramName(), Tab.noType);
    	}
    	else
    	{
    		report_error("Greska na liniji " + programName.getLine() + ": " + programName.getProgramName() + " vec deklarisano!", programName);
    	}
    	
    	Tab.openScope();
    }
    
    public void visit(Program program)
    {
    	nVars = Tab.currentScope.getnVars();
    	Tab.chainLocalSymbols(program.getProgramName().obj);
    	Tab.closeScope();
    }
    
    public void visit(Type type)
    {
    	Obj typeNode = Tab.find(type.getTypeName());
    	if(typeNode == Tab.noObj) 
    	{
    		report_error("Nije pronadjen tip " + type.getTypeName() + " u tabeli simbola!", null);
    		type.struct = Tab.noType;
    	}
    	else
    	{
    		if(Obj.Type == typeNode.getKind())
    		{
    			type.struct = typeNode.getType();
    			//report_info("Tip na liniji (" + type.getLine() + ") je " + type.getTypeName() , type);
    		}
    		else
    		{
    			report_error("Greska: Ime " + type.getTypeName() + " ne predstavlja tip!", type);
    			type.struct = Tab.noType;
    		}
    	}    	
    	currentType = type.struct;
    }   
    
    public void visit(ConstNum constNum)
    {
    	constNum.obj = new Obj(Obj.Con, "", Tab.intType, constNum.getConstValue(), 0);
    }
    
    public void visit(ConstChar constChar)
    {
    	constChar.obj = new Obj(Obj.Con, "", Tab.charType, constChar.getConstValue(), 0);
    }
    
    public void visit(ConstBool constBool)
    {
    	constBool.obj = new Obj(Obj.Con, "", TabExtended.boolType, Boolean.valueOf(constBool.getConstValue()) ? 1 : 0, 0);
    }
    
    public void visit(ConstList constList)
    {
    	Obj constNode = Tab.find(constList.getConstName());
    	if(constNode == Tab.noObj) 
    	{
    		Struct constListType = constList.getConstValue().obj.getType();
    		if(constListType.equals(currentType))
    		{
    			constNode = TabExtended.insert(Obj.Con, constList.getConstName(), currentType);
    			constNode.setAdr(constList.getConstValue().obj.getAdr());
    			 if (constNode.getLevel() == 0) {
                     globalConstCnt++;
                 }
    		}
    		else
    		{
    			report_error("Greska na liniji " + constList.getLine() + ": neispravan tip podatka!", null);
    		}
    	}
    	else
    	{
    		report_error("Greska na liniji " + constList.getLine() + ": " + constList.getConstName() + " vec deklarisano!", null);
    	}
    }
    
    public void visit(VarIdentifierBasic varIdentifierBasic)
    {	
    	String varName = varIdentifierBasic.getVarName();    
    	if(Tab.currentScope().findSymbol(varName) == null)
    	{    		
    		Obj varNode = TabExtended.insert(Obj.Var, varName, currentType);    		
    		if (varNode.getLevel() == 0) 
    		{
                globalVarCnt++;
            } else if ("main".equalsIgnoreCase(currentMethod.getName()))
            {
                mainLocalVarCnt++;
            }
    	}
    	else
    	{
    		report_error("Greska na liniji " + varIdentifierBasic.getLine() + ": " + varIdentifierBasic.getVarName() + " vec deklarisano!", null);
    	}    		
    }
    
    public void visit(VarIdentifierArray varIdentifierArray)
    {	
    	String varName = varIdentifierArray.getVarName();    
    	if(Tab.currentScope().findSymbol(varName) == null)
    	{
    		Obj varNode = TabExtended.insert(Obj.Var, varName, new Struct(Struct.Array, currentType));    		
    		  if (varNode.getLevel() == 0)
    		  {
                  globalArrayCnt++;
              }
    	}
    	else
    	{
    		report_error("Greska na liniji " + varIdentifierArray.getLine() + ": " + varIdentifierArray.getVarName() + " vec deklarisano!", null);
    	}    		
    }

    public void visit(MethodDecl methodDecl)
    {
    	methodCnt++;
    	if( !returnFound && currentMethod.getType() != Tab.noType && !"main".equalsIgnoreCase(currentMethod.getName()) )
    	{
    		report_error("Semanticka greska na liniji " + methodDecl.getLine() + ": funkcija " + currentMethod.getName() + " nema return iskaz!", null);
    	}
    	
    	if ("main".equalsIgnoreCase(currentMethod.getName()) && formParsCnt > 0)
		{   		
    		report_error("Greska na liniji " + methodDecl.getLine() + ": metod main() ne sme imati formalne parametre!", null);          
        }
    	
    	Tab.chainLocalSymbols(currentMethod);
    	//currentMethod.setLevel(formParsCnt);
    	Tab.closeScope();
    	
    	currentMethod = null;
    	returnFound = false;
    	formParsCnt = 0;
    }
    
    public void visit(MethParams fp) {
    	currentMethod.setLevel(formParsCnt);
    }
    
    public void visit(MethodTypeName methodTypeName)
    {
    	Obj methNode = TabExtended.find(methodTypeName.getMethName());
    	
    	if(methNode == TabExtended.noObj)
    	{    		
    		currentMethod = TabExtended.insert(Obj.Meth, methodTypeName.getMethName(), currentType);
    		TabExtended.openScope();
    		
    		if ("main".equalsIgnoreCase(currentMethod.getName()) && !currentMethod.getType().equals(Tab.noType))
    		{
    			report_error("Greska na liniji " + methodTypeName.getLine() + ": return vrednost metode main() mora biti void!", null);          
            }
    	}
    	else
    	{    		    		
    		currentMethod = TabExtended.insert(Obj.Meth, methodTypeName.getMethName(), methodTypeName.getReturnType().struct);
    		TabExtended.openScope();    		  	
    		
    		report_error("Greska na liniji " + methodTypeName.getLine() + ": " + methodTypeName.getMethName() + " vec deklarisano!", null);
   		
    		if ("main".equalsIgnoreCase(currentMethod.getName()) && !currentMethod.getType().equals(TabExtended.noType))
    		{
    			report_error("Greska na liniji " + methodTypeName.getLine() + ": return vrednost metode main() mora biti void!", null);          
            }
    	}
    	    	    	
    	methodTypeName.obj = currentMethod;
    }
    
    public void visit(StatementListBasic statementList) {
        if ("main".equalsIgnoreCase(currentMethod.getName())) {
            mainStatementCnt++;
        }
    }
        
    public void visit(ReturnTypeBasic returnTypeBasic)
    {
    	returnTypeBasic.struct = currentType = returnTypeBasic.getType().struct;
    }
    
    public void visit(ReturnTypeVoid returnTypeVoid)
    {
    	returnTypeVoid.struct = currentType = Tab.noType;
    }
    
    public void visit(FormParsExpressionBasic formParsExpressionBasic)
    {
    	Obj formParNode = TabExtended.currentScope.findSymbol(formParsExpressionBasic.getFormParsName());
    	if(formParNode == null)
    	{
    		 formParNode = TabExtended.insert(Obj.Var, formParsExpressionBasic.getFormParsName(), currentType);
    		 formParsCnt++;
    	}
    	else
    	{
    		report_error("Greska na liniji " + formParsExpressionBasic.getLine() + ": " + formParsExpressionBasic.getFormParsName() + " vec deklarisano!", formParsExpressionBasic);
    	}
    }
    
    public void visit(FormParsExpressionArray formParsExpressionArray)
    {
    	Obj formParNode = TabExtended.currentScope.findSymbol(formParsExpressionArray.getFormParsName());
    	if(formParNode == null)
    	{
    		 formParNode = TabExtended.insert(Obj.Var, formParsExpressionArray.getFormParsName(), new Struct(Struct.Array, currentType));
    		 formParsCnt++;
    	}
    	else
    	{
    		report_error("Greska na liniji " + formParsExpressionArray.getLine() + ": " + formParsExpressionArray.getFormParsName() + " vec deklarisano!", formParsExpressionArray);
    	}
    }
    
    public void visit(CorrectAssign assign)
	{
    	assign.struct = assign.getExprTO().struct;
	}
    
    public void visit(ErrorAssign assign)
    {
    	assign.struct = Tab.noType;
    }
    
    public void visit(DesignatorAssign designatorAssign)
    {
    	Obj dsgnStmNode = designatorAssign.getDesignator().obj;   
    	
    	Struct src = designatorAssign.getAssign().struct;
    	Struct dst = dsgnStmNode.getType();    	
    	
    	if(dsgnStmNode.getKind() != Obj.Var && dsgnStmNode.getKind() != Obj.Elem)
    	{
    		report_error("Greska na liniji " + designatorAssign.getLine() + ": leva strana mora biti promenljiva ili element niza!", null);
    	}
    	else if(!src.assignableTo(dst) && src!= TabExtended.noType)
    	{
    		//log.info("src:::::" + src.getKind());
        	//log.info("dst:::::" + dst.getKind());
    		report_error("Greska na liniji " + designatorAssign.getLine() + ": nekompatibilni tipovi!", null);
    	}
    }
 
    public void visit(DesignatorInc designator)
    {
    	Obj dsgnStmNode = designator.getDesignator().obj;
    	
    	if(dsgnStmNode.getKind() != Obj.Var && dsgnStmNode.getKind() != Obj.Elem)
    	{
    		report_error("Greska na liniji " + designator.getLine() + ": (" + dsgnStmNode.getName() + ") mora biti promenljiva ili element niza!", null);
    	}
    	else if(!(dsgnStmNode.getType()).equals(Tab.intType))
    	{
    		report_error("Greska na liniji " + designator.getLine() + ": (" + dsgnStmNode.getName() + ") mora biti celobrojnog tipa!", null);
    	}
    }
    
    public void visit(DesignatorDec designator)
    {
    	Obj dsgnStmNode = designator.getDesignator().obj;
    	
    	if(dsgnStmNode.getKind() != Obj.Var && dsgnStmNode.getKind() != Obj.Elem)
    	{
    		report_error("Greska na liniji " + designator.getLine() + ": (" + dsgnStmNode.getName() + ") mora biti promenljiva ili element niza!", null);
    	}
    	else if(!(dsgnStmNode.getType()).equals(Tab.intType))
    	{
    		report_error("Greska na liniji " + designator.getLine() + ": (" + dsgnStmNode.getName() + ") mora biti celobrojnog tipa!", null);
    	}
    }
    
	public void visit(DesignatorActPars funcCall)
	{
		Obj func = funcCall.getDesignator().obj;   	
    	
		ArrayList<Struct> currentActParTypes = currentActParTypesStack.pop();
		
    	if(Obj.Meth == func.getKind())
    	{
    		//log.info("f-ja:" + func.getName());
    		//log.info("level:" + func.getLevel());
    		//log.info("actPars:" + actParsList.size());
    		if(func.getLevel() > 0 && func.getLevel() != currentActParTypes.size())
    		{
                report_error("Greska na linji " + funcCall.getLine() + ": broj stvarnih i formalnih argumenata funkcije mora biti isti!", null);
    		}
    		else
    		{
    			ArrayList<Obj> formPars = new ArrayList<Obj>(func.getLocalSymbols());
    			 if (func.equals(currentMethod)) {
                     // specijalni slucaj: rekurzija, lokalni simboli metode su jos u trenutnom scope-u
                     formPars = new ArrayList<>(Tab.currentScope().values());
                 }
    			for (int i = 0; i < func.getLevel(); i++)
    			{
    				Obj formPar = formPars.get(i);
                    
                    Struct actParType = currentActParTypes.get(i);

                    if (!actParType.assignableTo(formPar.getType()))
                    {
                        report_error("Greska na liniji " + funcCall.getLine() + ": stvarni parametri f-je nisu kompatibilni sa formalnim parametrima!", null);
                        break;
                    }
                }
    		}
    		
    		report_info("Pronadjen poziv funkcije " + func.getName() + " na liniji " + funcCall.getLine(), null);   		
    	}
    	else
    	{
    		report_error("Greska na liniji " + funcCall.getLine() + ": " + func.getName() + " nije funkcija!", null);    		
    	}
       	
    	//actParsList.removeAll(actParsList);
	}
	
	public void visit(StatementBreak stBreak)
	{
		if (!doWhileDetected) {
            report_error("Greska na liniji " + stBreak.getLine() + ": break moze biti samo unutar DO-WHILE petlje!", stBreak);
        }
	}
	
	public void visit(StatementContinue stContinue)
	{
		if (!doWhileDetected) {
            report_error("Greska na liniji " + stContinue.getLine() + ": continue moze biti samo unutar DO-WHILE petlje!", stContinue);
        }
	}

	public void visit(StatementRead stRead)
	{
		if ("main".equalsIgnoreCase(currentMethod.getName())) {
            mainFuncCallCnt++;
        }
		Obj dsgnStmNode = stRead.getDesignator().obj;
    	
    	if(dsgnStmNode.getKind() != Obj.Var && dsgnStmNode.getKind() != Obj.Elem)
    	{
    		report_error("Greska na liniji " + stRead.getLine() + ": (" + dsgnStmNode.getName() + ") mora biti promenljiva ili element niza!", stRead);
    	}
    	else if(!dsgnStmNode.getType().equals(TabExtended.intType) && !dsgnStmNode.getType().equals(TabExtended.charType) && !dsgnStmNode.getType().equals(TabExtended.boolType))
    	{   		
    		report_error("Greska na liniji " + stRead.getLine() + ": (" + dsgnStmNode.getName() + ") mora biti tipa int, char ili bool!", stRead);
    	}
	}
	
	public void visit(StatementPrint sPrint)
	{
		if ("main".equalsIgnoreCase(currentMethod.getName())) {
            mainFuncCallCnt++;
        }
		Struct type = sPrint.getExprTO().struct;
		
		if (!type.equals(Tab.intType) && !type.equals(Tab.charType) && !type.equals(TabExtended.boolType))
		{
			report_error("Greska na liniji " + sPrint.getLine() + ": tipa mora biti int, char ili bool!", sPrint);
        }
	}

	public void visit(StatementReturn sReturn)
	{
		if (currentMethod == null)
		{
            report_error("Greska na liniji " + sReturn.getLine() + ": return iskaz izvan tela metode!", null);
        } 
		else 
		{
		    returnFound = true;
			if (!currentMethod.getType().equals(sReturn.getExprTOOptional().struct))
            {
                report_error("Greska na linji " + sReturn.getLine() + ": tip return iskaza nije ispravan", null);
            }           
        }
	}

	public void visit(StatementIf statementIf)
	{
		if (!statementIf.getIfCondition().struct.equals(TabExtended.boolType)) {
            report_error("Greska na liniji " + statementIf.getLine() + ": IF uslov mora biti tipa bool!", null);
        }
	}
	
	public void visit(StatementDoWhile statementdoWhile) 
	{
        if (!statementdoWhile.getCondition().struct.equals(TabExtended.boolType))
        {
            report_error("Greska na liniji " + statementdoWhile.getLine() + ": WHILE uslov mora biti tipa bool!", null);
        }
        doWhileDetected = false;
    }
	
	public void visit(AfterDo doWhileEnd)
	{
		doWhileDetected = true;
	}
	
	public void visit(ExprTOOptionalBasic expr) {
        expr.struct = expr.getExprTO().struct;
    }

    public void visit(NoExprTOOptional expr) {
        expr.struct = Tab.noType;
    }
    
    public void visit(CorrectCondition cond) {
        cond.struct = cond.getCondition().struct;
    }

    public void visit(ErrorCondition cond) {
        cond.struct = Tab.noType;
    }
	
    public void visit(ActParsStart actParsOptional)
    {
    	 currentActParTypesStack.push(new ArrayList<>());
    }
    
	public void visit(ActPar actParsSingle)
	{
	/*	if(actParsList == null)
		{
			//log.info("cmok");
			actParsList = new ArrayList<Struct>();
		}		
		//log.info("usao" + actParsSingle.getLine());
		actParsList.add(actParsSingle.getExprTO().struct);		*/
		currentActParTypesStack.peek().add(actParsSingle.getExprTO().struct);
	}
	
	public void visit(ConditionSingle conditionSingle)
	{
		conditionSingle.struct = conditionSingle.getCondTerm().struct;
	}
	
	public void visit(ConditionMultiple conditionMultiple)
	{
		conditionMultiple.struct = conditionMultiple.getCondition().struct;
	}
	
	public void visit(CondTermSingle condTermSingle)
	{
		condTermSingle.struct = condTermSingle.getCondFact().struct;
	}
	
	public void visit(CondTermMultiple condTermMultiple)
	{
		condTermMultiple.struct = condTermMultiple.getCondTerm().struct;
	}
	
	public void visit(CondFactExpr condFactExpr)
	{
		condFactExpr.struct = condFactExpr.getExpr().struct;
	}
   
	public void visit(CondFactRelop condFactRelop)	
	{
		if(!condFactRelop.getExpr().struct.compatibleWith(condFactRelop.getExpr1().struct))
		{
			report_error("Greska na liniji: " + condFactRelop.getLine() + ": tipovi nisu kompatibilni!", null);
		}
		else if((condFactRelop.getExpr().struct.getKind() == Struct.Array || condFactRelop.getExpr1().struct.getKind() == Struct.Array)
				&& !(condFactRelop.getRelop() instanceof RelopDoubleEqual || condFactRelop.getRelop() instanceof RelopDifferent))
		{
			report_error("Greska na liniji: " + condFactRelop.getLine() + ": operatori za niz mogu biti samo '==' i '!='!", null);
		}
		
		condFactRelop.struct = TabExtended.boolType;
	}
   
	public void visit(ExprBasic expr)
	{
		expr.struct = expr.getExpr().struct;	
	}
	
	public void visit(ExprTernary exprTernary)
	{
	
		Struct t1 = exprTernary.getExpr().struct;
		Struct t2 = exprTernary.getExprTO().struct;
		
		if(!t1.equals(t2))
		{
			report_error("Greska na liniji "+ exprTernary.getLine()+" : nekompatibilni tipovi!", null);
			exprTernary.struct = Tab.noType;
		}
		else
		{
			if(exprTernary.getCondition().struct == TabExtended.boolType)
			{				
				exprTernary.struct = t1;				
			}
			else
			{
				report_error("Greska na liniji "+ exprTernary.getLine()+" : uslov mora biti logickog tipa!", null);
				exprTernary.struct = Tab.noType;
			}
		}
	}
	
	public void visit(ExprTerm exprTerm)
    {
    	exprTerm.struct = exprTerm.getTerm().struct;
    }
    
    public void visit(ExprMinus exprMinus)
    {
    	if (!exprMinus.getTerm().struct.equals(Tab.intType))
		{
            report_error("Greska na liniji " + exprMinus.getLine() + ": tip mora biti celobrojni!", null);
        }
    	exprMinus.struct = exprMinus.getTerm().struct;
    }
    
    public void visit(ExprAddop exprAddop)
    {
    	Struct te = exprAddop.getExpr().struct;
    	Struct t  = exprAddop.getTerm().struct; 
    	
    	if(!te.equals(t) && !(te == Tab.intType))
    	{
    		report_error("Greska na liniji "+ exprAddop.getLine()+" : tipovi moraju biti celobrojne vrednosti!", null);
    		exprAddop.struct = Tab.noType;
    	}
    	else if(!te.compatibleWith(t))
    	{
    		report_error("Greska na liniji "+ exprAddop.getLine()+" : nekompatibilni tipovi!", null);
    		exprAddop.struct = Tab.noType;
    	}
    	else 
    	{
    		exprAddop.struct = te;
    	}
    }
	
	public void visit(TermFactor term)
	{
		term.struct = term.getFactor().struct;
	}
	
	public void visit(TermMulop termMulop)
	{
		if (!termMulop.getTerm().struct.equals(Tab.intType) || !termMulop.getFactor().struct.equals(Tab.intType)) 
		{
            report_error("Greska na " + termMulop.getLine() + ": operatori moraju biti celobrojni tipovi!", null);
        }

		termMulop.struct = termMulop.getTerm().struct;
	}
	
	public void visit(FactorDesignator factorDesignator)
	{
		factorDesignator.struct = factorDesignator.getDesignator().obj.getType();
	}
	
	public void visit(FactorConst factorConst) {
		factorConst.struct = factorConst.getConstValue().obj.getType();
    }
	
	public void visit(FactorExprTO factor) {
		factor.struct = factor.getExprTO().struct;
    }
	
	public void visit(FactorNew factorNew)
	{
		factorNew.struct = factorNew.getType().struct;
	}
	
	public void visit(FactorNewArray factorNewArray)
	{
		if(!factorNewArray.getExprTO().struct.equals(Tab.intType))
		{
			report_error("Greska na liniji " + factorNewArray.getLine() + ": izraz u '[ ]' mora biti celobrojnog tipa!", null);
		}
		
		factorNewArray.struct = new Struct(Struct.Array, factorNewArray.getType().struct);
	}
	
	public void visit(FuncCall funcCall)
    {
		if ("main".equalsIgnoreCase(currentMethod.getName())) {
            mainFuncCallCnt++;
        }
    	Obj func = funcCall.getDesignator().obj;   	
    	
    	ArrayList<Struct> currentActParTypes = currentActParTypesStack.pop();
    	
    	if(Obj.Meth == func.getKind())
    	{    	
    		//log.info("f-ja:" + func.getName());
    		//log.info("level:" + func.getLevel());
    		//log.info("actPars:" + actParsList.size());
    		if(func.getLevel() != currentActParTypes.size())
    		{    		
                report_error("Greska na linji " + funcCall.getLine() + ": broj stvarnih i formalnih argumenata funkcije mora biti isti!", null);
    		}
    		else
    		{
    			if(Tab.noType == func.getType())
    			{
    				report_error("Greska na liniji " + funcCall.getLine() + ": ne moze se koristiti void funkcija u izrazima!", funcCall);
    			}
    			else
    			{
	    			ArrayList<Obj> formPars = new ArrayList<Obj>(func.getLocalSymbols());
	    			 if (func.equals(currentMethod)) {
	                     // specijalni slucaj: rekurzija, lokalni simboli metode su jos u trenutnom scope-u
	                     formPars = new ArrayList<>(Tab.currentScope().values());
	                 }
	    			for (int i = 0; i < func.getLevel(); i++)
	    			{
	    				Obj formPar = formPars.get(i);
	                    
	                    Struct actParType = currentActParTypes.get(i);
	
	                    if (!actParType.assignableTo(formPar.getType()))
	                    {
	                        report_error("Greska na liniji " + funcCall.getLine() + ": stvarni parametri f-je nisu kompatibilni sa formalnim parametrima!", null);
	                        break;
	                    }
	                } 
    			}
    		}
    		
    		report_info("Pronadjen poziv funkcije " + func.getName() + " na liniji " + funcCall.getLine(), null);
    		funcCall.struct = func.getType();
    	}
    	else
    	{
    		report_error("Greska na liniji " + funcCall.getLine() + ": " + func.getName() + " nije funkcija!", null);
    		funcCall.struct = Tab.noType;
    	}
    	    	
    	//actParsList.removeAll(actParsList);
    }
	
    public void visit(DesignatorIdent designatorIdent)
    {
    	Obj obj = Tab.find(designatorIdent.getName());
    	if(obj == Tab.noObj)
    	{
    		report_error("Greska na liniji " + designatorIdent.getLine() + ": " + designatorIdent.getName() + " nije deklarisano!", null);
    	}
    	designatorIdent.obj = obj;
    	report_info("Pretraga na liniji " + designatorIdent.getLine() + " (" + obj.getName() + "), nadjeno ", null);
    }
    
    public void visit(DesignatorExprTO designatorExpr)
    {
    	Obj desigNode = designatorExpr.getDesignator().obj;
    	designatorExpr.obj = Tab.noObj;
    	
    	if (desigNode.getType().getKind() != Struct.Array)
    	{
            report_error("Greska na linji " + designatorExpr.getLine() + " (" + desigNode.getName() + ") mora biti niz!", null);
        } 
    	else
    	{        
            if (!designatorExpr.getExprTO().struct.equals(Tab.intType))
            {
                report_error("Greska na liniji " + designatorExpr.getLine() + ": izraz u '[ ]' mora biti celobrojnog tipa!", null);
            }
            designatorExpr.obj = new Obj(Obj.Elem, "", desigNode.getType().getElemType() != null ? desigNode.getType().getElemType() : Tab.noType);
        
    		report_info("Pretraga na liniji " + designatorExpr.getLine() + " (" + desigNode.getName() + "), nadjeno ", null);
    	}
    }
                 
    public boolean passed(){
    	return !errorDetected;
    }

}
