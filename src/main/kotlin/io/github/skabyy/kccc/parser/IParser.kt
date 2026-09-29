package io.github.skabyy.kccc.parser

import io.github.skabyy.kccc.exceptions.ParseException
import io.github.skabyy.kccc.scanner.TokenStream

/**
 * SAM interface replacing the C# `Func<TokenStream, IParseStack, ParseResult>`
 * used by `ParserImpl`, so that parser lambdas stay callable from Java too.
 */
fun interface ParseFunction {
    operator fun invoke(toks: TokenStream, stk: IParseStack): ParseResult
}

interface IParser {
    fun parse(toks: TokenStream, stk: IParseStack): ParseResult
}

/**
 * Equivalent of the C# `IParserExtension.Fatal` extension method (the C#
 * code base also carried a byte-identical `CombinedParser.Fatal` static;
 * both threw the same ParseException, so a single top-level function covers
 * both call sites).
 */
fun fatal(message: String, parser: IParser, toks: TokenStream, stk: IParseStack): Nothing {
    throw ParseException(
        "$message\n" +
            "parser: $parser\n" +
            "rest: $toks\n" +
            "stack trace: ${stk.toSExp().toPrettyString()}"
    )
}
