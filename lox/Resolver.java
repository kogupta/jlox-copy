package lox;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;

class Resolver implements Expr.Visitor<Void>, Stmt.Visitor<Void> {
    private final Interpreter interpreter;
    private final Stack<Map<String, Boolean>> scopes = new Stack<>();
    private FunctionType currentFunction = FunctionType.NONE;
    private LoopType currentLoopType = LoopType.NONE;
    private ClassType currentClass = ClassType.NONE;

    Resolver(Interpreter interpreter) {
        this.interpreter = interpreter;
    }

    @Override
    public Void visitClassStmt(Stmt.Class stmt) {
        ClassType enclosingClass = currentClass;
        currentClass = ClassType.CLASS;
        declare(stmt.name);
        if (stmt.superclass != null) {
            currentClass = ClassType.SUBCLASS;
            resolve(stmt.superclass);
        }
        define(stmt.name);
        if (stmt.superclass != null) {
            beginScope();
            scopes.peek().put("super", true);
        }
        beginScope();
        scopes.peek().put("self", true);
        for (Stmt.Function method : stmt.methods) {
            FunctionType declaration;
            if (method.name.lexeme.equals("init")) {
                declaration = FunctionType.INITIALIZER;
            } else {
                declaration = FunctionType.METHOD;
            }
            resolveFunction(method, declaration);
        }
        endScope();
        if (stmt.superclass != null) {
            endScope();
        }
        currentClass = enclosingClass;
        return null;
    }

    @Override
    public Void visitSuperExpr(Expr.Super expr) {
        if (currentClass == ClassType.NONE) {
            Lox.error(expr.keyword, "Cannot use 'super' outside of class.");
        } else if (currentClass != ClassType.SUBCLASS) {
            Lox.error(expr.keyword, "Cannot use 'super' in a class with no superclass.");
        }
        resolveLocal(expr, expr.keyword);
        return null;
    }

    @Override
    public Void visitSelfExpr(Expr.Self expr) {
        if (currentClass == ClassType.NONE) {
            Lox.error(expr.keyword, "Cannot use 'self' outside of a class.");
            return null;
        }
        resolveLocal(expr, expr.keyword);
        return null;
    }

    @Override
    public Void visitBreakStmt(Stmt.Break stmt) {
        if (currentLoopType == LoopType.NONE) {
            Lox.error(stmt.keyword, "Break can only used be inside switch cases & loops.");
        }
        return null;
    }

    @Override
    public Void visitContinueStmt(Stmt.Continue stmt) {
        if (currentLoopType == LoopType.NONE) {
            Lox.error(stmt.keyword, "Continue can only used be inside loops.");
        }
        return null;
    }

    @Override
    public Void visitBlockStmt(Stmt.Block stmt) {
        beginScope();
        resolve(stmt.statements);
        endScope();
        return null;
    }

    private void beginScope() {
        scopes.push(new HashMap<String, Boolean>());
    }

    private void endScope() {
        scopes.pop();
    }

    void resolve(List<Stmt> statements) {
        for (Stmt stmt : statements) {
            resolve(stmt);
        }
    }

    private void resolve(Stmt stmt) {
        stmt.accept(this);
    }

    @Override
    public Void visitLetStmt(Stmt.Let stmt) {
        declare(stmt.name);
        if (stmt.initializer != null) {
            resolve(stmt.initializer);
        }
        define(stmt.name);
        return null;
    }

    private void declare(Token name) {
        if (scopes.isEmpty()) {
            return;
        }
        Map<String, Boolean> scope = scopes.peek();
        if (scope.containsKey(name.lexeme)) {
            Lox.error(name, "Variable with this name already declared in scope.");
        }
        scope.put(name.lexeme, false);
    }

    private void define(Token name) {
        if (scopes.isEmpty()) {
            return;
        }
        scopes.peek().put(name.lexeme, true);
    }

    private void resolve(Expr expr) {
        expr.accept(this);
    }

    @Override
    public Void visitVarExpr(Expr.Variable expr) {
        if (!scopes.isEmpty() && scopes.peek().get(expr.name.lexeme) == Boolean.FALSE) {
            Lox.error(expr.name, "Cannot read local variable in its own initializer.");
        }
        resolveLocal(expr, expr.name);
        return null;
    }

    private void resolveLocal(Expr expr, Token name) {
        for (int i = scopes.size() - 1; i >= 0; i--) {
            if (scopes.get(i).containsKey(name.lexeme)) {
                interpreter.resolve(expr, scopes.size() - 1 - i);
                return;
            }
        }
    }

    @Override
    public Void visitAssignExpr(Expr.Assign expr) {
        resolve(expr.value);
        resolveLocal(expr, expr.name);
        return null;
    }

    @Override
    public Void visitFunctionStmt(Stmt.Function stmt) {
        declare(stmt.name);
        define(stmt.name);
        resolveFunction(stmt, FunctionType.FUNCTION);
        return null;
    }

    private void resolveFunction(Stmt.Function function, FunctionType type) {
        FunctionType enclosingFunction = currentFunction;
        currentFunction = type;
        beginScope();
        for (Token param : function.params) {
            declare(param);
            define(param);
        }
        resolve(function.body);
        endScope();
        currentFunction = enclosingFunction;
    }

    @Override
    public Void visitExprStmt(Stmt.Expression stmt) {
        resolve(stmt.expr);
        return null;
    }

    @Override
    public Void visitIfStmt(Stmt.If stmt) {
        resolve(stmt.cond);
        resolve(stmt.thenBranch);
        if (stmt.elseBranch != null) {
            resolve(stmt.elseBranch);
        }
        return null;
    }

    @Override
    public Void visitPrintStmt(Stmt.Print stmt) {
        resolve(stmt.expr);
        return null;
    }

    @Override
    public Void visitReturnStmt(Stmt.Return stmt) {
        if (currentFunction == FunctionType.NONE) {
            Lox.error(stmt.keyword, "Cannot return form top-level scope.");
        }
        if (stmt.expr != null) {
            if (currentFunction == FunctionType.INITIALIZER) {
                Lox.error(stmt.keyword, "Cannot return a value from an initializer.");
            }
            resolve(stmt.expr);
        }
        return null;
    }

    @Override
    public Void visitWhileStmt(Stmt.While stmt) {
        LoopType enclosingType = currentLoopType;
        currentLoopType = LoopType.LOOP;
        resolve(stmt.cond);
        resolve(stmt.body);
        currentLoopType = enclosingType;
        return null;
    }

    @Override
    public Void visitDoWhileStmt(Stmt.DoWhile stmt) {
        LoopType enclosingType = currentLoopType;
        currentLoopType = LoopType.LOOP;
        resolve(stmt.body);
        resolve(stmt.cond);
        currentLoopType = enclosingType;
        return null;
    }

    @Override
    public Void visitForStmt(Stmt.For stmt) {
        if (stmt.init != null) {
            resolve(stmt.init);
        }
        if (stmt.cond != null) {
            resolve(stmt.cond);
        }
        if (stmt.incr != null) {
            resolve(stmt.incr);
        }
        LoopType enclosingType = currentLoopType;
        currentLoopType = LoopType.LOOP;
        resolve(stmt.body);
        currentLoopType = enclosingType;
        return null;
    }

    @Override
    public Void visitSwitchStmt(Stmt.Switch stmt) {
        LoopType enclosingType = currentLoopType;
        currentLoopType = LoopType.SWITCH;
        for (Stmt item : stmt.branches) {
            resolve(item);
        }
        currentLoopType = enclosingType;
        return null;
    }

    @Override
    public Void visitBinary(Expr.Binary expr) {
        resolve(expr.left);
        resolve(expr.right);
        return null;
    }

    @Override
    public Void visitUnary(Expr.Unary expr) {
        resolve(expr.right);
        return null;
    }

    @Override
    public Void visitLiteral(Expr.Literal expr) {
        return null;
    }

    @Override
    public Void visitGrouping(Expr.Grouping expr) {
        resolve(expr.expression);
        return null;
    }

    @Override
    public Void visitLogicalExpr(Expr.Logical expr) {
        resolve(expr.left);
        resolve(expr.right);
        return null;
    }

    @Override
    public Void visitConditionalExpr(Expr.Conditional expr) {
        resolve(expr.cond);
        resolve(expr.thenBranch);
        resolve(expr.elseBranch);
        return null;
    }

    @Override
    public Void visitCallExpr(Expr.Call expr) {
        resolve(expr.callee);
        for (Expr arg : expr.args) {
            resolve(arg);
        }
        return null;
    }

    @Override
    public Void visitGetExpr(Expr.Get expr) {
        resolve(expr.object);
        return null;
    }

    @Override
    public Void visitSetExpr(Expr.Set expr) {
        resolve(expr.value);
        resolve(expr.object);
        return null;
    }
}

enum FunctionType {
    NONE,
    FUNCTION,
    METHOD,
    INITIALIZER;
}

enum ClassType {
    NONE,
    CLASS,
    SUBCLASS
}