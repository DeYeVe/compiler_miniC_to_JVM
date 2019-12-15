package listener.main;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.StringTokenizer;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ErrorNode;
import org.antlr.v4.runtime.tree.ParseTreeListener;
import org.antlr.v4.runtime.tree.ParseTreeProperty;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.stringtemplate.v4.compiler.CodeGenerator.includeExpr_return;

import generated.MiniCBaseListener;
import generated.MiniCParser;
import generated.MiniCParser.DeclContext;
import generated.MiniCParser.ExprContext;
import generated.MiniCParser.Fun_declContext;
import generated.MiniCParser.Local_declContext;
import generated.MiniCParser.ParamContext;
import generated.MiniCParser.ParamsContext;
import generated.MiniCParser.ProgramContext;
import generated.MiniCParser.StmtContext;
import generated.MiniCParser.Type_specContext;
import generated.MiniCParser.Var_declContext;

import static listener.main.BytecodeGenListenerHelper.*;
import static listener.main.SymbolTable.*;

public class BytecodeGenListener extends MiniCBaseListener implements ParseTreeListener {
	ParseTreeProperty<String> newTexts = new ParseTreeProperty<String>();
	SymbolTable symbolTable = new SymbolTable();
	
	int tab = 0;
	int label = 0;
	
	int maxStackSize = 0;
	int stackSize = 0;
	public void renewStackSize(int inc) {
		stackSize += inc;
		if(maxStackSize < stackSize)
			maxStackSize = stackSize;
	}
	
	// program	: decl+

	class basicBlock{
		int num; //block 번호, 아직 미사용
		int nextLabel = -1; //분기로 연결된 다음 label 번호
		ArrayList<Integer> nextBlockNum = new ArrayList<>();
		ArrayList<String> lines = new ArrayList<>();
		
		public basicBlock(ArrayList<String> lines, int nextLabel) {
			this.nextLabel = nextLabel;
			this.lines = lines;
			this.num = bbCount++;
		}
		public basicBlock(ArrayList<String> lines) {
			this.lines = lines;
			this.num = bbCount++;
		}
		public basicBlock() {
			this.num = bbCount++;
			}
	} // Basic Block 클래스
	
	int bbCount = 0; //Basic Block 수

	ArrayList<basicBlock> blocks = new ArrayList<>(); //Basic Block들의 집합
	
	class range{
		int start; int end;
	}
	public range funcRange (ArrayList<String> lines, String funcName) {
		range r = new range();
		for(int i=0; i<lines.size(); i++) {
			if(lines.get(i).contains(".method public static " + funcName))
				r.start = i;
			if(lines.get(i).contains(".end method")) {
				r.end = i;
				break;
			}
		}
		return r;
	} // 함수호출시 호출, 호출된 함수의 시작 라인과 마지막 라인을 반환
	
	public int getBlockNumByLabel(ArrayList<basicBlock> blocks, int Label) {
		
		String label = String.valueOf(Label);
		for(int i=0; i<blocks.size(); i++) {
			ArrayList<String> lines = blocks.get(i).lines;
			for(int j=0; j<lines.size(); j++) {
				if(lines.get(j).contains("label" + label + ":"))
					return i;
			}
		}
		return 0;
		
	} // 블럭의 다음 분기 label 정보에 따라서 블럭의 번호를 반환하는 함수
	
	public void buildCFG(MiniCParser.ProgramContext ctx) { // Control Flow를 build. BasicBlock 으로 나누고 연결
		
		String text = newTexts.get(ctx);
		ArrayList<String> lines = new ArrayList<>();
		StringTokenizer st = new StringTokenizer(text);
		
		while(st.hasMoreTokens()) {
			lines.add((st.nextToken("\n")));
		} // jvm code parsing

		basicBlock start = new basicBlock();
		blocks.add(start);
		
		ArrayList<String> buf = new ArrayList<>();
		
		while(true) {
			String s = lines.get(0);
			buf.add(s);
			lines.remove(0);
			
			if(s.contains(".end method")) {
				start.lines = buf;
				break;
			}
		} // 파일 시작 블록 초기화
		
		int index = 0;
		for(int i=0; i<lines.size(); i++) {
			if(lines.get(i).contains(".method public static main([Ljava/lang/String;)V"))
				index = i;
		} // 메인함수 시작줄 index, 해당 줄부터 시작
		
		int indexBuf = 0;
		int rt = -1; //함수 호출시 return 줄 번호를 저장할 변수
		int range = 0; //함수 호출시 함수부분의 line 수, return 줄 번호의 변화 offset을 나타냄, 함수가 끝나고 caller로 돌아올 때 rt - range -1 로 계산
		
		while(!lines.isEmpty()) { //line 들을 추출해 block 단위로 변환하면서 전부다 변환될때까지 반복
			buf = new ArrayList<>();
			int size = lines.size();
			for(int i=index; i<size; i++) {
				String s = lines.get(index);
				if((!s.contains("label") || !s.contains(":")) || i==0) {
					buf.add(s);
					lines.remove(index);
				} //label?: 이 아닌경우 해당 line을 추출해 버퍼에 저장
				
				if(s.contains("label") && !s.contains(":")) {
					int nextL = Integer.parseInt(s.substring(10));
					blocks.get(blocks.size()-1).nextBlockNum.add(bbCount);
					basicBlock bb = new basicBlock(buf, nextL);
					blocks.add(bb);
					indexBuf = index;
					break;
				}//goto, ifne 등등 목적지 label이 있는 분기문 만날경우, bb 컷
				
				else if(s.contains("label") && s.contains(":") && (i!=0)){ 
					blocks.get(blocks.size()-1).nextBlockNum.add(bbCount);
					basicBlock bb = new basicBlock(buf);
					blocks.add(bb);
					indexBuf = index;
					break;
				}//label?: 을 만날경우 그 이전 라인까지 bb 컷, 그 이전 bb처리 했을경우 bb의 시작
				
				else if(s.contains("invokestatic Test/")) {
					blocks.get(blocks.size()-1).nextBlockNum.add(bbCount);
					basicBlock bb = new basicBlock(buf);
					blocks.add(bb);
					
					String funcName = s.substring(18);
					range r = funcRange(lines, funcName);
					rt = index;
					index = r.start;
					range = r.end - r.start;
					break;
					
				} //함수를 만날경우, 함수의 시작 위치와 범위, 리턴 위치를 알아낸 후 함수부분 시작
				
				else if(s.contains(".end method")) {
					blocks.get(blocks.size()-1).nextBlockNum.add(bbCount);
					basicBlock bb = new basicBlock(buf);
					blocks.add(bb);
					indexBuf = rt - range -1;
					break;
				}//.end method를 만날경우 함수 종료이므로 bb 컷
			}
			index = indexBuf;
		}
		
	
		for(int i=0; i<blocks.size(); i++) {
			basicBlock bb = blocks.get(i);
			if(bb.nextLabel!=-1) {
				int nextBB = getBlockNumByLabel(blocks, bb.nextLabel);
				bb.nextBlockNum.add(nextBB);
			}
		} // BB마다 저장한 다음 BB의 label정보를 가지고 다음 BB를 알아낸 후 연결
	}
	
	public void printCFG(MiniCParser.ProgramContext ctx) {
		
		buildCFG(ctx);
		
		System.out.println("\n\n\n\n\n\n====================================== Control Flow ======================================\n");
		for(int i=0; i<blocks.size(); i++) {
			basicBlock bb = blocks.get(i);
			System.out.printf(" BB%2d ┏━\n", i);
			for(int j=0; j<bb.lines.size(); j++){
				System.out.printf("\t %s\n", bb.lines.get(j));
			}
			int maxleng = 0;
			for(int j=0; j<bb.lines.size(); j++)
				if(maxleng < bb.lines.get(j).length())
					maxleng = bb.lines.get(j).length();
			String space = "";
			for(int j=0; j<maxleng+1; j++)
				space += String.format("%2s","");
			String nb = "";
			for(int j=0; j<bb.nextBlockNum.size(); j++) {
				nb += String.format("BB%d ", bb.nextBlockNum.get(j));
			}
			if(i+1 < blocks.size())
				System.out.printf("\t   %s━┛=> %s\n\n\n", space, nb);
			else
				System.out.printf("\t   %s━┛//End", space);
		}
	} // BB를 차례대로 출력

	@Override
	public void enterDecl(DeclContext ctx) {
		// TODO Auto-generated method stub
		renewStackSize(1);
	}
	@Override
	public void enterParam(ParamContext ctx) {
		// TODO Auto-generated method stub
		renewStackSize(1);
	}
	@Override
	public void exitParam(ParamContext ctx) {
		// TODO Auto-generated method stub
		renewStackSize(-1);
	}
	@Override
	public void enterFun_decl(MiniCParser.Fun_declContext ctx) {
		symbolTable.initFunDecl();
		
		String fname = getFunName(ctx);
		ParamsContext params;
		
		if (fname.equals("main")) {
			symbolTable.putLocalVar("args", Type.INTARRAY);
		} else {
			symbolTable.putFunSpecStr(ctx);
			params = (MiniCParser.ParamsContext) ctx.getChild(3);
			symbolTable.putParams(params);
		}	
		renewStackSize(1);
	}

	
	// var_decl	: type_spec IDENT ';' | type_spec IDENT '=' LITERAL ';'|type_spec IDENT '[' LITERAL ']' ';'
	@Override
	public void enterVar_decl(MiniCParser.Var_declContext ctx) {
		String varName = ctx.IDENT().getText();
		
		if (isArrayDecl(ctx)) {
			symbolTable.putGlobalVar(varName, Type.INTARRAY);
		}
		else if (isDeclWithInit(ctx)) {
			symbolTable.putGlobalVarWithInitVal(varName, Type.INT, initVal(ctx));
		}
		else  { // simple decl
			symbolTable.putGlobalVar(varName, Type.INT);
		}
		renewStackSize(1);
	}

	
	@Override
	public void enterLocal_decl(MiniCParser.Local_declContext ctx) {			
		if (isArrayDecl(ctx)) {
			symbolTable.putLocalVar(getLocalVarName(ctx), Type.INTARRAY);
		}
		else if (isDeclWithInit(ctx)) {
			symbolTable.putLocalVarWithInitVal(getLocalVarName(ctx), Type.INT, initVal(ctx));	
		}
		else  { // simple decl
			symbolTable.putLocalVar(getLocalVarName(ctx), Type.INT);
		}	
		renewStackSize(1);
	}

	
	@Override
	public void exitProgram(MiniCParser.ProgramContext ctx) {
		String classProlog = getFunProlog();
		
		String fun_decl = "", var_decl = "";
		
		for(int i = 0; i < ctx.getChildCount(); i++) {
			if(isFunDecl(ctx, i))
				fun_decl += newTexts.get(ctx.decl(i));
			else
				var_decl += newTexts.get(ctx.decl(i));
		}
		
		newTexts.put(ctx, classProlog + var_decl + fun_decl);
		
		System.out.println(newTexts.get(ctx));
		
		printCFG(ctx);
	}	
	
	
	// decl	: var_decl | fun_decl
	@Override
	public void exitDecl(MiniCParser.DeclContext ctx) {
		String decl = "";
		if(ctx.getChildCount() == 1)
		{
			if(ctx.var_decl() != null)				//var_decl
				decl += newTexts.get(ctx.var_decl());
			else							//fun_decl
				decl += newTexts.get(ctx.fun_decl());
		}
		newTexts.put(ctx, decl);
		renewStackSize(-1);
	}
	
	// stmt	: expr_stmt | compound_stmt | if_stmt | while_stmt | return_stmt
	@Override
	public void exitStmt(MiniCParser.StmtContext ctx) {
		String stmt = "";
		if(ctx.getChildCount() > 0)
		{
			if(ctx.expr_stmt() != null)				// expr_stmt
				stmt += newTexts.get(ctx.expr_stmt());
			else if(ctx.compound_stmt() != null)	// compound_stmt
				stmt += newTexts.get(ctx.compound_stmt());
			// <(0) Fill here>	
			else if(ctx.if_stmt() != null)
				stmt += newTexts.get(ctx.if_stmt());
			else if(ctx.while_stmt() != null)
				stmt += newTexts.get(ctx.while_stmt());
			else if(ctx.return_stmt() != null)
				stmt += newTexts.get(ctx.return_stmt());
	}
		newTexts.put(ctx, stmt);
		//위와 똑같이 각 case에 대해 ctx 수행 후 버퍼에 저장 후 newTexts에 put
	}
	
	// expr_stmt	: expr ';'
	@Override
	public void exitExpr_stmt(MiniCParser.Expr_stmtContext ctx) {
		String Expr_stmt = "";
		if(ctx.getChildCount() == 2)
		{
			Expr_stmt += newTexts.get(ctx.expr());	// expr
		}
		newTexts.put(ctx, Expr_stmt);
	}
	
	
	// while_stmt	: WHILE '(' expr ')' stmt
	@Override
	public void exitWhile_stmt(MiniCParser.While_stmtContext ctx) {
			// <(1) Fill here!>
		String While_stmt = "";
		
		String condExpr= newTexts.get(ctx.expr());
		String thenStmt = newTexts.get(ctx.stmt());

		String lloop = symbolTable.newLabel();
		String lend = symbolTable.newLabel();
		
				
		While_stmt += lloop + ":" + "\n"+ condExpr /*+ "\n"*/
				+ "ifeq " + lend + "\n"
				+ thenStmt + "\n"
				+ "goto " + lloop + "\n"
				+ lend + ":" + "\n";
		
		
		newTexts.put(ctx, While_stmt); 
	} // loop 시작지점 선언 후, 조건 문 검사해 거짓일경우 end로 루프 빠져나가고, 참일경우 then 수행 후 loop로 go
	
	
	@Override
	public void exitFun_decl(MiniCParser.Fun_declContext ctx) {
			// <(2) Fill here!>
		String Fun_decl = "";
		if(ctx.getChildCount() == 6)
		{
			Fun_decl += funcHeader(ctx, ctx.IDENT().getText());
			
			Fun_decl += newTexts.get(ctx.compound_stmt());
		}
		newTexts.put(ctx, Fun_decl); //함수 선언 child 수가 6일경우 앞에 헤더부 추가

		renewStackSize(-1);
	}
	

	private String funcHeader(MiniCParser.Fun_declContext ctx, String fname) {
		return ".method public static " + symbolTable.getFunSpecStr(fname) + "\n"	
				/*+ "\t"*/ + ".limit stack " 	+ String.valueOf(maxStackSize+2) + "\n"
				/*+ "\t"*/ + ".limit locals " 	+ String.valueOf(maxStackSize+2) + "\n";
				 	
	}
	
	
	
	@Override
	public void exitVar_decl(MiniCParser.Var_declContext ctx) {
		String varName = ctx.IDENT().getText();
		String varDecl = "";
		
		if (isDeclWithInit(ctx)) {
			varDecl += "putfield " + varName + "\n";  
			// v. initialization => Later! skip now..: 
		}
		newTexts.put(ctx, varDecl);
		
		renewStackSize(-1);
	}
	
	
	@Override
	public void exitLocal_decl(MiniCParser.Local_declContext ctx) {
		String varDecl = "";
		
		if (isDeclWithInit(ctx)) {
			String vId = symbolTable.getVarId(ctx);
			varDecl += "ldc " + ctx.LITERAL().getText() + "\n"
					+ "istore_" + vId + "\n"; 			
		}
		
		newTexts.put(ctx, varDecl);

		renewStackSize(-1);
	}

	
	// compound_stmt	: '{' local_decl* stmt* '}'
	@Override
	public void exitCompound_stmt(MiniCParser.Compound_stmtContext ctx) {
		// <(3) Fill here>
		String Compound_stmt = "";
		
		int local_declCount = ctx.local_decl().size();
		int stmtCount = ctx.stmt().size();

		for(int i=0; i<local_declCount; i++) {
			Compound_stmt += newTexts.get(ctx.local_decl(i));
		} //  local_decl을 차례대로 버퍼에 저장
		for(int i=0; i<stmtCount; i++) {
			Compound_stmt += newTexts.get(ctx.stmt(i));
		} //  stmt를 차례대로 버퍼에 저장
		
		newTexts.put(ctx, Compound_stmt);
	}

	// if_stmt	: IF '(' expr ')' stmt | IF '(' expr ')' stmt ELSE stmt;
	@Override
	public void exitIf_stmt(MiniCParser.If_stmtContext ctx) {
		String stmt = "";
		String condExpr= newTexts.get(ctx.expr());
		String thenStmt = newTexts.get(ctx.stmt(0));
		
		String lend = symbolTable.newLabel();
		String lelse = symbolTable.newLabel();
		
		
		if(noElse(ctx)) {		
			stmt += condExpr /*+ "\n"*/
				+ "ifeq " + lend + "\n"
				+ thenStmt + "\n"
				+ lend + ":"  + "\n";	
		}
		else {
			String elseStmt = newTexts.get(ctx.stmt(1));
			stmt += condExpr + "\n"
					+ "ifeq " + lelse + "\n"
					+ thenStmt + "\n"
					+ "goto " + lend + "\n"
					+ lelse + ": " + elseStmt + "\n"
					+ lend + ":"  + "\n";	
		}
		
		newTexts.put(ctx, stmt);
	}
	
	
	// return_stmt	: RETURN ';' | RETURN expr ';'
	@Override
	public void exitReturn_stmt(MiniCParser.Return_stmtContext ctx) {
			// <(4) Fill here>
		String Return_stmt = "";
		if(ctx.getChildCount() == 2) {
			Return_stmt += "return" + "\n";
		}
		else if(ctx.getChildCount() == 3) {
			String idName = ctx.expr().getText();
			if(symbolTable.getVarType(idName) == Type.INT) {
				Return_stmt += "iload_" + symbolTable.getVarId(idName) + "\n";
			}
			Return_stmt += "ireturn" + "\n";
		}
		Return_stmt += ".end method" + "\n";
		newTexts.put(ctx, Return_stmt);
		renewStackSize(-1);
	} // 반환값이 없을경우 return\n을 버퍼에 저장, 반환값이 있고 int 타입인 경우 iload_vid\n ireturn\n  버퍼에 저장. 마지막으로 .end method\n 추가

	
	@Override
	public void exitExpr(MiniCParser.ExprContext ctx) {
		String expr = "";

		if(ctx.getChildCount() <= 0) {
			newTexts.put(ctx, ""); 
			return;
		}		
		
		if(ctx.getChildCount() == 1) { // IDENT | LITERAL
			if(ctx.IDENT() != null) {
				String idName = ctx.IDENT().getText();
				if(symbolTable.getVarType(idName) == Type.INT) {
					expr += "iload_" + symbolTable.getVarId(idName) + " \n";
				}
				//else	// Type int array => Later! skip now..
				//	expr += "           lda " + symbolTable.get(ctx.IDENT().getText()).value + " \n";
				} else if (ctx.LITERAL() != null) {
					String literalStr = ctx.LITERAL().getText();
					expr += "ldc " + literalStr + " \n";
				}
			} 
		else if(ctx.getChildCount() == 2) { // UnaryOperation
			expr = handleUnaryExpr(ctx, newTexts.get(ctx) + expr);			
		}
		else if(ctx.getChildCount() == 3) {	 
			if(ctx.getChild(0).getText().equals("(")) { 		// '(' expr ')'
				expr = newTexts.get(ctx.expr(0));
				
			} else if(ctx.getChild(1).getText().equals("=")) { 	// IDENT '=' expr
				expr = newTexts.get(ctx.expr(0))
						+ "istore_" + symbolTable.getVarId(ctx.IDENT().getText()) + " \n";
				
			} else { 											// binary operation
				expr = handleBinExpr(ctx, expr);
				
			}
		}
		// IDENT '(' args ')' |  IDENT '[' expr ']'
		else if(ctx.getChildCount() == 4) {
			if(ctx.args() != null){		// function calls
				expr = handleFunCall(ctx, expr);
			} else { // expr
				// Arrays: TODO  
			}
		}
		// IDENT '[' expr ']' '=' expr
		else { // Arrays: TODO			*/
		}
		newTexts.put(ctx, expr);
	}


	private String handleUnaryExpr(MiniCParser.ExprContext ctx, String expr) {
		String l1 = symbolTable.newLabel();
		String l2 = symbolTable.newLabel();
		String lend = symbolTable.newLabel();

		String vId = symbolTable.getVarId(ctx.expr(0).getText());
		
		if(expr.equals("null"))
			expr = "";
		expr += newTexts.get(ctx.expr(0));
		switch(ctx.getChild(0).getText()) {
		case "-":
			expr += "           ineg \n"; break;
		case "--":
			expr += "ldc 1" + "\n"
					+ "isub" + "\n"
					+ "istore_" + vId + "\n";
			break;
		case "++":
			expr += "ldc 1" + "\n"
					+ "iadd" + "\n"
					+ "istore_" + vId + "\n";
			break;
		case "!":
			expr += "ifeq " + l2 + "\n"
					+ l1 + ": " + "ldc 0" + "\n"
					+ "goto " + lend + "\n"
					+ l2 + ": " + "\n" + "ldc 1" + "\n"
					+ lend + ": " + "\n";
			break;
		}
		return expr;
	}


	private String handleBinExpr(MiniCParser.ExprContext ctx, String expr) {
		String l2 = symbolTable.newLabel();
		String lend = symbolTable.newLabel();
		
		expr += newTexts.get(ctx.expr(0));
		expr += newTexts.get(ctx.expr(1));
		
		switch (ctx.getChild(1).getText()) {
			case "*":
				expr += "imul \n"; break;
			case "/":
				expr += "idiv \n"; break;
			case "%":
				expr += "irem \n"; break;
			case "+":		// expr(0) expr(1) iadd
				expr += "iadd \n"; break;
			case "-":
				expr += "isub \n"; break;
				
			case "==":
				expr += "isub " + "\n"
						+ "ifeq " + l2 + "\n"
						+ "ldc 0" + "\n"
						+ "goto " + lend + "\n"
						+ l2 + ": " + "\n" + "ldc 1" + "\n"
						+ lend + ": " + "\n";
				break;
			case "!=":
				expr += "isub " + "\n"
						+ "ifne " + l2 + "\n"
						+ "ldc 0" + "\n"
						+ "goto " + lend + "\n"
						+ l2 + ": " + "\n" + "ldc 1" + "\n"
						+ lend + ": " + "\n";
				break;
			case "<=":
				// <(5) Fill here>
				expr += "isub " + "\n"
						+ "ifle " + l2 + "\n"
						+ "ldc 0" + "\n"
						+ "goto " + lend + "\n"
						+ l2 + ": " + "\n" + "ldc 1" + "\n"
						+ lend + ": " + "\n";
				break;
			case "<":
				// <(6) Fill here>
				expr += "isub " + "\n"
						+ "iflt " + l2 + "\n"
						+ "ldc 0" + "\n"
						+ "goto " + lend + "\n"
						+ l2 + ": " + "\n" + "ldc 1" + "\n"
						+ lend + ": " + "\n";
				break;

			case ">=":
				// <(7) Fill here>
				expr += "isub " + "\n"
						+ "ifge " + l2 + "\n"
						+ "ldc 0" + "\n"
						+ "goto " + lend + "\n"
						+ l2 + ": " + "\n" + "ldc 1" + "\n"
						+ lend + ": " + "\n";
				break;

			case ">":
				// <(8) Fill here>
				expr += "isub " + "\n"
						+ "ifgt " + l2 + "\n"
						+ "ldc 0" + "\n"
						+ "goto " + lend + "\n"
						+ l2 + ": " + "\n" + "ldc 1" + "\n"
						+ lend + ": " + "\n";
				break;
				// (5) ~ (8) if 부분의 비교급을 해당 기호에 맞게 수정. 
			case "and":
				expr +=  "ifne "+ lend + "\n"
						+ "pop" + "\n" + "ldc 0" + "\n"
						+ lend + ": " + "\n"; 
				break;
			case "or":
				// <(9) Fill here>
				expr +=  "ifeq "+ lend + "\n"
						+ "pop" + "\n" + "ldc 1" + "\n"
						+ lend + ": " + "\n"; 
				break; // ifeq로 top 비교 후 0일경우 거짓이므로 다음 조건 비교하도록 lend로 이동, 참일경우 다음조건 비교 필요없으므로 명시적으로 pop하고 ldc 1 선언후 lend로 이동

		}
		return expr;
	}
	private String handleFunCall(MiniCParser.ExprContext ctx, String expr) {
		String fname = getFunName(ctx);		

		if (fname.equals("_print")) {		// System.out.println	
			expr = "getstatic java/lang/System/out Ljava/io/PrintStream; " + "\n"
			  		+ newTexts.get(ctx.args()) 
			  		+ "invokevirtual " + symbolTable.getFunSpecStr("_print") + "\n";
		} else {	
			expr = newTexts.get(ctx.args()) 
					+ "invokestatic " + getCurrentClassName()+ "/" + symbolTable.getFunSpecStr(fname) + "\n";
		}	
		
		return expr;
			
	}

	// args	: expr (',' expr)* | ;
	@Override
	public void exitArgs(MiniCParser.ArgsContext ctx) {

		String argsStr = ""; // \n -> ""
		
		for (int i=0; i < ctx.expr().size() ; i++) {
			argsStr += newTexts.get(ctx.expr(i)) ; 
		}		
		newTexts.put(ctx, argsStr);
	}

}
