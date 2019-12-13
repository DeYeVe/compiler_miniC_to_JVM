package listener.main;

import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;

import generated.MiniCParser;
import generated.MiniCParser.Fun_declContext;
import generated.MiniCParser.Local_declContext;
import generated.MiniCParser.ParamsContext;
import generated.MiniCParser.Type_specContext;
import generated.MiniCParser.Var_declContext;
import listener.main.SymbolTable.Type;
import static listener.main.BytecodeGenListenerHelper.*;


public class SymbolTable {
	enum Type {
		INT, INTARRAY, VOID, ERROR
	}
	
	static public class VarInfo {
		Type type; 
		int id;
		int initVal;
		
		public VarInfo(Type type,  int id, int initVal) {
			this.type = type;
			this.id = id;
			this.initVal = initVal;
		}
		public VarInfo(Type type,  int id) {
			this.type = type;
			this.id = id;
			this.initVal = 0;
		}
	}
	
	static public class FInfo {
		public String sigStr;
	}
	
	private Map<String, VarInfo> _lsymtable = new HashMap<>();	// local v.
	private Map<String, VarInfo> _gsymtable = new HashMap<>();	// global v.
	private Map<String, FInfo> _fsymtable = new HashMap<>();	// function 
	
		
	private int _globalVarID = 0;
	private int _localVarID = 0;
	private int _labelID = 0;
	private int _tempVarID = 0;
	
	SymbolTable(){
		initFunDecl();
		initFunTable();
	}
	
	void initFunDecl(){		// at each func decl
		_lsymtable.clear();
		_localVarID = 0;
		_labelID = 0;
		_tempVarID = 32;		
	}
	
	void putLocalVar(String varname, Type type){
		//<Fill here1>
		VarInfo v = new VarInfo(type, _localVarID);
		_lsymtable.put(varname, v);
		_localVarID ++; //local id와 type 정보를 담은 심볼을 테이블에 추가, id +1
	}
	
	void putGlobalVar(String varname, Type type){
		//<Fill here2>
		VarInfo v = new VarInfo(type, _globalVarID);
		_gsymtable.put(varname, v);
		_globalVarID ++; //global id와 type 정보를 담은 심볼을 테이블에 추가, id +1
	}
	
	void putLocalVarWithInitVal(String varname, Type type, int initVar){
		//<Fill here3>
		VarInfo v = new VarInfo(type, _localVarID, initVar);
		_lsymtable.put(varname, v);
		_localVarID ++; //local id와 type, 초기화 값 정보를 담은 심볼을 테이블에 추가, id +1
	}
	void putGlobalVarWithInitVal(String varname, Type type, int initVar){
		//<Fill here4>
		VarInfo v = new VarInfo(type, _globalVarID, initVar);
		_gsymtable.put(varname, v);
		_globalVarID ++; //global id와 type, 초기화 값 정보를 담은 심볼을 테이블에 추가, id +1
	}
	
	void putParams(MiniCParser.ParamsContext params) {
		for(int i = 0; i < params.param().size(); i++) {
		//<Fill here5>
			String vname = getParamName(params.param(i));
			putLocalVar(vname, Type.INT);
		}
	} // int 타입의 파라미터. param이름과 intType을 인자로 local var에 put
	
	private void initFunTable() {
		FInfo printlninfo = new FInfo();
		printlninfo.sigStr = "java/io/PrintStream/println(I)V";
		
		FInfo maininfo = new FInfo();
		maininfo.sigStr = "main([Ljava/lang/String;)V";
		_fsymtable.put("_print", printlninfo);
		_fsymtable.put("main", maininfo);
	}
	
	public String getFunSpecStr(String fname) {		
		// <Fill here6>
		FInfo f = _fsymtable.get(fname);
		
		return f.sigStr; // f심볼테이블에서 해당 name 검색후 정보에서 sigStr 추출반환
	}

	public String getFunSpecStr(Fun_declContext ctx) {
		// <Fill here7>	
		String fname = putFunSpecStr(ctx); 
		FInfo f = _fsymtable.get(fname);
		
		return f.sigStr; //putFunSpecStr 함수를 통해 해당 ctx의 name 반환 후 심볼테이블에서 검색후 sigStr 반환
		
	}
	
	public String putFunSpecStr(Fun_declContext ctx) {
		String fname = getFunName(ctx);
		String argtype = "";	
		String rtype = "";
		String res = "";
		
		// <Fill here8>	
		argtype = getParamTypesText(ctx.params());
		rtype = getTypeText((Type_specContext) ctx.getChild(0));
		// argtype -> 파라미터 타입의 문자열, rtype -> 리턴타입의 문자열 저장
		res =  fname + "(" + argtype + ")" + rtype;
		
		FInfo finfo = new FInfo();
		finfo.sigStr = res;
		_fsymtable.put(fname, finfo);
		
		return res;
	}
	
	String getVarId(String name){
		// <Fill here9>	
		VarInfo lvar = (VarInfo) _lsymtable.get(name);
		if (lvar != null) {
			return Integer.toString(lvar.id);
		}
		
		VarInfo gvar = (VarInfo) _gsymtable.get(name);
		if (gvar != null) {
			return Integer.toString(gvar.id);
		}
		//지역, 글로벌 심볼테이블 검색후 존재 시 정보값에서 id 추출 반환
		return null;	
	}
	
	Type getVarType(String name){
		VarInfo lvar = (VarInfo) _lsymtable.get(name);
		if (lvar != null) {
			return lvar.type;
		}
		
		VarInfo gvar = (VarInfo) _gsymtable.get(name);
		if (gvar != null) {
			return gvar.type;
		}
		
		return Type.ERROR;	
	}
	String newLabel() {
		return "label" + _labelID++;
	}
	
	String newTempVar() {
		String id = "";
		return id + _tempVarID--;
	}

	// global
	public String getVarId(Var_declContext ctx) {
		// <Fill here10>	
		String sname = "";
		sname += getVarId(ctx.IDENT().getText());
		return sname;
	}

	// local
	public String getVarId(Local_declContext ctx) {
		String sname = "";
		sname += getVarId(ctx.IDENT().getText());
		return sname;
	}
	
}
