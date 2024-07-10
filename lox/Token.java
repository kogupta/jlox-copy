package lox;

record Token(TokenType type, String lexeme, Object literal, int line, int col) {

    public String toString() {
        return STR."<\{type},\{lexeme},\{literal},\{line},\{col}>";
    }
}

