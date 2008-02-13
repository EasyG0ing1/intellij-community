/*
 * User: anna
 * Date: 25-Jan-2008
 */
package com.intellij.psi.search.scope.packageSet;

import com.intellij.analysis.AnalysisScopeBundle;
import com.intellij.lexer.Lexer;
import com.intellij.psi.search.scope.packageSet.lexer.ScopeTokenTypes;
import org.jetbrains.annotations.Nullable;

public class FilePackageSetParserExtension implements PackageSetParserExtension {

  @Nullable
  public String parseScope(Lexer lexer) {
    if (lexer.getTokenType() != ScopeTokenTypes.IDENTIFIER) return null;
    String id = getTokenText(lexer);
    if (FilePatternPackageSet.SCOPE_FILE.equals(id)) {

      final CharSequence buf = lexer.getBufferSequence();
      final int end = lexer.getTokenEnd();
      final int bufferEnd = lexer.getBufferEnd();

      if (end >= bufferEnd || buf.charAt(end) != ':' && buf.charAt(end) != '[') {
        return null;
      }

      lexer.advance();
      return FilePatternPackageSet.SCOPE_FILE;
    }

    return null;
  }

  @Nullable
  public PackageSet parsePackageSet(final Lexer lexer, final String scope, final String modulePattern) throws ParsingException {
    if (scope != FilePatternPackageSet.SCOPE_FILE) return null;
    return new FilePatternPackageSet(modulePattern, parseFilePattern(lexer));
  }

  private static String parseFilePattern(Lexer lexer) throws ParsingException {
    StringBuffer pattern = new StringBuffer();
    boolean wasIdentifier = false;
    while (true) {
      if (lexer.getTokenType() == ScopeTokenTypes.DIV) {
        wasIdentifier = false;
        pattern.append("/");
      }
      else if (lexer.getTokenType() == ScopeTokenTypes.IDENTIFIER || lexer.getTokenType() == ScopeTokenTypes.INTEGER_LITERAL) {
        if (wasIdentifier) error(lexer, AnalysisScopeBundle.message("error.packageset.token.expectations", getTokenText(lexer)));
        wasIdentifier = true;
        pattern.append(getTokenText(lexer));
      }
      else if (lexer.getTokenType() == ScopeTokenTypes.ASTERISK) {
        wasIdentifier = false;
        pattern.append("*");
      }
      else if (lexer.getTokenType() == ScopeTokenTypes.DOT) {
        wasIdentifier = false;
        pattern.append(".");
      }
      else if (lexer.getTokenType() == ScopeTokenTypes.WHITE_SPACE) {
        wasIdentifier = false;
        pattern.append(" ");
      }
      else if (lexer.getTokenType() == ScopeTokenTypes.MINUS) {
        wasIdentifier = false;
        pattern.append("-");
      }
      else {
        break;
      }
      lexer.advance();
    }

    if (pattern.length() == 0) {
      error(lexer, AnalysisScopeBundle.message("error.packageset.pattern.expectations"));
    }

    return pattern.toString();
  }

  private static String getTokenText(Lexer lexer) {
    int start = lexer.getTokenStart();
    int end = lexer.getTokenEnd();
    return lexer.getBufferSequence().subSequence(start, end).toString();
  }

  private static void error(Lexer lexer, String message) throws ParsingException {
    throw new ParsingException(
      AnalysisScopeBundle.message("error.packageset.position.parsing.error", message, (lexer.getTokenStart() + 1)));
  }
}