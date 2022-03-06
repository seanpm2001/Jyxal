parser grammar VyxalParser;

@header {
package io.github.seggan.jyxal.antlr;
}

options {
    tokenVocab=VyxalLexer;
}

file
    : alias* program EOF
    ;

alias
    : PREFIX element_type PIPE element_type WHITESPACE*
    ;

program
    : (literal | statement | element | WHITESPACE)+
    ;

literal
    : normal_string
    | compressed_string
    | single_char_string
    | double_char_string
    | number
    | compressed_number
    | complex_number
    | list
    ;

normal_string
    : BACKTICK (~BACKTICK .)*? BACKTICK
    ;

compressed_string
    : COMPRESSED_STRING (~COMPRESSED_STRING .)*? COMPRESSED_STRING
    ;

single_char_string
    : SINGLE_CHAR_STRING .
    ;

double_char_string
    : DOUBLE_CHAR_STRING . .
    ;

number
    : integer (PERIOD integer)?
    ;

integer
    : DIGIT+
    ;

compressed_number
    : COMPRESSED_NUMBER (~COMPRESSED_NUMBER .)*? COMPRESSED_NUMBER
    ;

complex_number
    : number COMPLEX_SEPARATOR number
    ;

list
    : LIST_OPEN program (PIPE program)* LIST_CLOSE?
    ;

statement
    : if_statement
    | for_loop
    | while_loop
    | lambda
    | function
    | variable_assn
    ;

if_statement
    : IF_OPEN program (PIPE program)? IF_CLOSE?
    ;

for_loop
    : FOR_OPEN (variable PIPE)? program FOR_CLOSE?
    ;

while_loop
    : WHILE_OPEN (program PIPE)? program WHILE_CLOSE?
    ;

lambda
    : LAMBDA_TYPE (integer PIPE)? program SEMICOLON?
    ;

function
    : AT_SIGN variable ((COLON parameter (COLON parameter)*)? PIPE program)? SEMICOLON?
    ;

parameter
    : STAR | variable | integer
    ;

variable_assn
    : ASSN_SIGN variable
    ;

variable
    : (ALPHA | DIGIT)+
    ;

element
    : MODIFIER? PREFIX? element_type
    ;

element_type
    : ALPHA | NON_ALPHA_ELEMENT
    ;

