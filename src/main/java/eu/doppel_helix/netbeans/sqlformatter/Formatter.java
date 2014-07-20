/**
 * Copyright (c) 2014 Matthias Bläsing <mblaesing@doppel-helix.eu>
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 * 
 * This is mostly a reimplementation of the sql-format project from
 * 
 * Jeremy Dorn <jeremy@jeremydorn.com>
 * Florin Patan <florinpatan@gmail.com>
 * 
 * The PHP version can be found on github:
 * 
 * http://github.com/jdorn/sql-formatter
 */

package eu.doppel_helix.netbeans.sqlformatter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import org.netbeans.api.lexer.Language;
import org.netbeans.api.lexer.Token;
import org.netbeans.api.lexer.TokenHierarchy;
import org.netbeans.api.lexer.TokenSequence;
import org.netbeans.modules.editor.indent.api.IndentUtils;
import org.netbeans.modules.editor.indent.spi.Context;
import org.netbeans.modules.editor.indent.spi.ExtraLock;
import org.netbeans.modules.editor.indent.spi.ReformatTask;

public class Formatter implements ReformatTask {
    static Language sqlLanguage = Language.find("text/x-sql");
    public static class Factory implements ReformatTask.Factory {

        @Override
        public ReformatTask createTask(Context cntxt) {
            return new Formatter(cntxt);
        }
    }

    private final Context context;

    public Formatter(Context context) {
        this.context = context;
    }

    @Override
    public ExtraLock reformatLock() {
        return null;
    }
    
    private static final List<List<String>> reserved_newline = Arrays.asList(
            Arrays.asList("LEFT", "OUTER", "JOIN"),
            Arrays.asList("RIGHT", "OUTER", "JOIN"),
            Arrays.asList("LEFT", "JOIN"),
            Arrays.asList("RIGHT", "JOIN"),
            Arrays.asList("OUTER", "JOIN"),
            Arrays.asList("INNER", "JOIN"),
            Arrays.asList("JOIN"),
            Arrays.asList("XOR"),
            Arrays.asList("OR"),
            Arrays.asList("AND")
    );
    
    private static final List<List<String>> reserved_toplevel = Arrays.asList(
            Arrays.asList("GROUP", "BY"),
            Arrays.asList("ORDER", "BY"), 
            Arrays.asList("ALTER", "TABLE"), 
            Arrays.asList("DELETE", "FROM"), 
            Arrays.asList("UNION", "ALL"),
            Arrays.asList("SELECT"),
            Arrays.asList("FROM"),
            Arrays.asList("WHERE"),
            Arrays.asList("SET"), 
            Arrays.asList("LIMIT"), 
            Arrays.asList("DROP"), 
            Arrays.asList("VALUES"),
            Arrays.asList("UPDATE"), 
            Arrays.asList("HAVING"), 
            Arrays.asList("ADD"), 
            Arrays.asList("AFTER"), 
            Arrays.asList("UNION"), 
            Arrays.asList("EXCEPT"), 
            Arrays.asList("INTERSECT")
    );

    @Override
    public void reformat() throws BadLocationException {
        Document d = context.document();
        
        int startPos = d.getStartPosition().getOffset();
        int endPos = d.getEndPosition().getOffset();
        
        String baseText = d.getText(startPos, endPos - 1);
        
        TokenHierarchy th = TokenHierarchy.create(baseText, sqlLanguage);
        
        TokenSequence ts = th.tokenSequence();
        
        List<Token> tokens = new ArrayList<>(ts.tokenCount());
        
        while(ts.moveNext()) {
            tokens.add(ts.token());
        }
        
        // Only one indent region is supported:
        int startSelection = context.indentRegions().get(0).getStartOffset();
        int endSelection = context.indentRegions().get(0).getEndOffset();
        
        int startIdx = 0;
        int endIdx = tokens.size() - 1;
        
        for(int i = 0; i < tokens.size(); i++) {
            Token t = tokens.get(i);
            if (SQLTokenId.OPERATOR.matches(t.id())
                    && ";".equalsIgnoreCase(t.text().toString())) {
                int tokenStart = t.offset(th);
                int tokenEnd = tokenStart + t.length();
                if (tokenStart < startSelection) {
                    startIdx = i + 1;
                    while(SQLTokenId.WHITESPACE.matches(tokens.get(startIdx).id())) {
                        startIdx++;
                        i++;
                        if(i >= tokens.size()) {
                            break;
                        }
                    }
                }
                if (tokenEnd > endSelection) {
                    endIdx = i;
                    break;
                }
            } 
        }

        int tokenStartPos = tokens.get(startIdx).offset(th);
        int tokenEndPos = tokens.get(endIdx).offset(th) + tokens.get(endIdx).length();
        
        String newSQL = formatSQL(
                tokens.subList(startIdx, endIdx + 1), 
                IndentUtils.indentLevelSize(d),
                IndentUtils.isExpandTabs(d),
                IndentUtils.tabSize(d));
        
        d.remove(tokenStartPos, tokenEndPos - tokenStartPos);
        d.insertString(tokenStartPos, newSQL, null);
    }

    // This is package access scoped to be able to directly test this method
    String formatSQL(List<Token> originalTokenList, int levelSize, boolean expandTabs, int tabSize) {
        List<Token> tokenList = new ArrayList<>();
        
        Map<Integer,Integer> originalPos = new HashMap<>();
        
        for(int i = 0; i < originalTokenList.size(); i++) {
            Token t = originalTokenList.get(i);
            if(! SQLTokenId.WHITESPACE.matches(t.id())) {
                tokenList.add(t);
                originalPos.put(tokenList.size() - 1, i);
            }
        }
        
        // Asumption: ASCII NULL is not part of the String - this is at least
        // a better asumption than using the tab character
        char tab = '\u0000';

        int indent_level = 0;
        boolean newline = false;
        boolean inline_parentheses = false;
        boolean increase_special_indent = false;
        boolean increase_block_indent = false;
        List<String> indent_types = new ArrayList<>();
        boolean added_newline;
        int inline_count = 0;
        boolean inline_indented = false;
        boolean clause_limit = false;
        
        StringBuilder sb = new StringBuilder();
        
        for(int i = 0; i < tokenList.size(); i++) {
            Token t = tokenList.get(i);
            String tokenText = t.text().toString();
            
            // If we are increasing the special indent level now
            if(increase_special_indent) {
                indent_level++;
                increase_special_indent = false;
                indent_types.add(0, "special");
            }
            
            // If we are increasing the block indent level now
            if(increase_block_indent) {
                indent_level++;
                increase_block_indent = false;
                indent_types.add(0, "block");
            }
            
            // If we need a new line before the token
            if(newline) {
                // Prevent trailing spaces on new lines
                rtrim(sb, ' ');
                sb.append("\n");
                appendIterated(sb, tab, indent_level);
                newline = false;
                added_newline = true;
            } else {
                added_newline = false;
            }
            
            // Display comments directly where they appear in the source
            if (SQLTokenId.LINE_COMMENT.matches(t.id()) || SQLTokenId.BLOCK_COMMENT.matches(t.id())) {
                tokenText = tokenText.trim();
                // Multiline comments are aligned to left
                if(tokenText.contains("\n")) {
                    rtrim(sb, null);
                    sb.append("\n");
                    sb.append(tokenText);
                } else if ((currentLineFill(sb, levelSize, tab) + tokenText.length()) < 90) {
                    if(! added_newline) {
                        rtrim(sb, ' ');
                        sb.append(" ");
                    }
                    sb.append(tokenText);
                } else {
                    rtrim(sb, null);
                    sb.append("\n");
                    appendIterated(sb, tab, indent_level);
                    sb.append(tokenText);
                }
                rtrim(sb, '\n');
                newline = true;
                continue;
            }
            
           if (inline_parentheses) {
                // End of inline parentheses
                if (SQLTokenId.RPAREN.matches(t.id())) {
                    rtrim(sb, ' ');

                    if (inline_indented) {
                        indent_types.remove(0);
                        indent_level--;
                        sb.append("\n");
                        appendIterated(sb, tab, indent_level);
                    }

                    inline_parentheses = false;

                    sb.append(tokenText);
                    sb.append(" ");
                    continue;
                }

                if (SQLTokenId.COMMA.matches(t.id())) {
                    if (inline_count >= 30) {
                        inline_count = 0;
                        newline = true;
                    }
                }

                inline_count += tokenText.length();
            }
            
           
            // Opening parentheses increase the block indent level and start a new line
            if (SQLTokenId.LPAREN.matches(t.id())) {
                // First check if this should be an inline parentheses block
                // Examples are "NOW()", "COUNT(*)", "int(10)", key(`somecolumn`), DECIMAL(7,2)
                // Allow up to 3 non-whitespace tokens inside inline parentheses
                int length = 0;
                for (int j=1;j<=250;j++) {
                    // Reached end of string
                    if((i + j) >= tokenList.size()) {
                        break;
                    }
                    
                    Token next = tokenList.get(i+j);

                    // Reached closing parentheses, able to inline it
                    if (SQLTokenId.RPAREN.matches(next.id())) {
                        inline_parentheses = true;
                        inline_count = 0;
                        inline_indented = false;
                        break;
                    }

                    // Reached an invalid token for inline parentheses
                    if ((SQLTokenId.OPERATOR.matches(next.id()) && next.text().equals(";")) 
                            || SQLTokenId.LPAREN.matches(next.id())
                            || SQLTokenId.BLOCK_COMMENT.matches(next.id())
                            || SQLTokenId.LINE_COMMENT.matches(next.id())
                            || longestCiMatchIgoreWhitespace(tokenList, i+j, reserved_newline) != null
                            || longestCiMatchIgoreWhitespace(tokenList, i+j, reserved_toplevel) != null) {
                        break;
                    }

                    length += next.text().length();
                }

                if (inline_parentheses && length > 30) {
                    increase_block_indent = true;
                    inline_indented = true;
                    newline = true;
                }

                Integer originialTokenPos = originalPos.get(i);
                if(originialTokenPos != null && originialTokenPos > 0 &&
                        (! SQLTokenId.WHITESPACE.matches(
                                originalTokenList.get(originialTokenPos - 1).id()))) {
                    rtrim(sb, ' ');
                }
                
                if (!inline_parentheses) {
                    increase_block_indent = true;
                    // Add a newline after the parentheses
                    newline = true;
                }

            } else if (SQLTokenId.RPAREN.matches(t.id())) {
                // Remove whitespace before the closing parentheses
                rtrim(sb, ' ');

                indent_level--;

                // Reset indent level
                while (true) {
                    if(indent_types.isEmpty()) {
                        break;
                    }
                    String j = indent_types.remove(0);
                    if ("special".equals(j)) {
                        indent_level--;
                    } else {
                        break;
                    }
                }

                if (indent_level < 0) {
                    // This is an error
                    indent_level = 0;

                    sb.append(tokenText);
                    continue;
                }

                // Add a newline before the closing parentheses (if not already added)
                if (! added_newline) {
                    sb.append("\n");
                    appendIterated(sb, tab, indent_level);
                }
            }
                      // Top level reserved words start a new line and increase the special indent level
            else if (longestCiMatchIgoreWhitespace(tokenList, i, reserved_toplevel) != null) {
                increase_special_indent = true;

                // If the last indent type was 'special', decrease the special indent for this round
                if (indent_types.size() > 0 && "special".equals(indent_types.get(0))) {
                    indent_level--;
                    indent_types.remove(0);
                }

                // Add a newline after the top level reserved word
                newline = true;
                // Add a newline before the top level reserved word (if not already added)
                if ((!added_newline) && sb.length() > 0) {
                    rtrim(sb, ' ');
                    sb.append("\n");
                    appendIterated(sb, tab, indent_level);
                }
                // If we already added a newline, redo the indentation since it may be different now
                else {
                    rtrim(sb, tab);
                    appendIterated(sb, tab, indent_level);
                }

                int matchlength = longestCiMatchIgoreWhitespace(tokenList, i, reserved_toplevel);
                
                StringBuilder sb2 = new StringBuilder();
                
                // If the token may have extra whitespace
                for (int j = 0; j < matchlength; j++) {
                    if(j != 0) {
                        sb2.append(" ");
                    }
                    sb2.append(tokenList.get(i + j).text());
                }
                tokenText = sb2.toString();
   
                //if SQL 'LIMIT' clause, start variable to reset newline
                if ("LIMIT".equalsIgnoreCase(tokenText) && !inline_parentheses) {
                    clause_limit = true;
                }
                
                i = i + matchlength - 1;
            }
            // Checks if we are out of the limit clause
            else if (clause_limit && (! SQLTokenId.COMMA.matches(t.id())) && (! SQLTokenId.INT_LITERAL.matches(t.id()))) {
                clause_limit = false;
            }
            // Commas start a new line (unless within inline parentheses or SQL 'LIMIT' clause)
            else if (SQLTokenId.COMMA.matches(t.id()) && ! inline_parentheses) {
                //If the previous TOKEN_VALUE is 'LIMIT', resets new line
                if (clause_limit) {
                    newline = false;
                    clause_limit = false;
                }
                // All other cases of commas
                else {
                    newline = true;
                }
            }
            // Newline reserved words start a new line
            else if (longestCiMatchIgoreWhitespace(tokenList, i, reserved_newline) != null) {
                // Add a newline before the reserved word (if not already added)
                if (! added_newline) {
                    rtrim(sb, ' ');
                    sb.append("\n");
                    appendIterated(sb, tab, indent_level);
                }

                int matchlength = longestCiMatchIgoreWhitespace(tokenList, i, reserved_newline);

                StringBuilder sb2 = new StringBuilder();
                
                // If the token may have extra whitespace
                for (int j = 0; j < matchlength; j++) {
                    if(j != 0) {
                        sb2.append(" ");
                    }
                    sb2.append(tokenList.get(i + j).text());
                }
                tokenText = sb2.toString();
                
                i = i + matchlength - 1;
            }

            // If the token shouldn't have a space before it
            if (SQLTokenId.DOT.matches(t.id())
                    || SQLTokenId.COMMA.matches(t.id())
                    || (SQLTokenId.OPERATOR.matches(t.id()) && (";".equals(t.text().toString())))) {
                rtrim(sb, ' ');
            }

            sb.append(tokenText);
            sb.append(" ");

            // If the token shouldn't have a space after it
            if (SQLTokenId.DOT.matches(t.id())
                    || SQLTokenId.LPAREN.matches(t.id())
                    || (SQLTokenId.OPERATOR.matches(t.id()) && (";".equals(t.text().toString())))) {
                rtrim(sb, ' ');
            }
        }
        
        rtrim(sb, ' ');
        
        Integer end = null;
        for(int i = sb.length() - 1; i >= 0; i--) {
            if(end == null && sb.charAt(i) == tab) {
                end = i;
            } else if (sb.charAt(i) != tab && end != null) {
                int start = i + 1;
                int levels = (end - start + 1) * levelSize;
                String replacement = IndentUtils.createIndentString(levels, expandTabs, tabSize);
                sb.replace(start, end + 1, replacement);
                end = null;
            }
        }
        if (end != null) {
            int start = 0;
            int levels = (end - start + 1) * levelSize;
            String replacement = IndentUtils.createIndentString(levels, expandTabs, tabSize);
            sb.replace(start, end + 1, replacement);
        }
        
        return sb.toString();
    }

    private Integer longestCiMatchIgoreWhitespace(List<Token> ts, int pos, List<List<String>> candidates) {
        // No match after end of list...
        if(pos >= ts.size()) {
            return null;
        }
        int tokenLength = 0;
        Token t;
        do {
            t = ts.get(pos);
            pos++;
            tokenLength++;
        } while (SQLTokenId.WHITESPACE.matches(t.id()) && pos < ts.size());
        if(SQLTokenId.WHITESPACE.matches(t.id())) {
            return null;
        }
        String value = t.text().toString();
        Integer longestMatch = null;
        for(List<String> candidate: candidates) {
            if(candidate.get(0).equalsIgnoreCase(value)) {
                if(candidate.size() == 1 && (longestMatch == null || longestMatch < 1)) {
                    longestMatch = tokenLength;
                } else if (candidate.size() > 1) {
                    Integer subMatch = longestCiMatchIgoreWhitespace(ts, pos, Collections.singletonList(candidate.subList(1, candidate.size())));
                    if(subMatch != null) {
                        int currentMatch = subMatch + tokenLength;
                        if(longestMatch == null || currentMatch > longestMatch) {
                            longestMatch = currentMatch;
                        }
                    }
                }
            }
        }
        return longestMatch;
    }
    
    private void rtrim(StringBuilder sb, Character c) {
        int lastIdx = sb.length();
        int deleteStart = sb.length();
        while(true) {
            if(deleteStart == 0) {
                break;
            }
            char nextChar = sb.charAt(deleteStart - 1);
            if((c == null && (Character.isWhitespace(nextChar) || nextChar == '\u0000')) 
                    || (c != null && c == nextChar)) {
                deleteStart--;
            } else {
                break;
            }
        }
        if(deleteStart < lastIdx) {
            sb.delete(deleteStart, lastIdx);
        }
    }

    static int currentLineFill(StringBuilder sb, int indentSize, char tabChar) {
        int lastIdx = sb.length() - 1;
        int fill = 0;
        for(int i = lastIdx; i > 0; i--) {
            if(sb.charAt(i) == tabChar) {
                fill += indentSize;
            } else if(sb.charAt(i) == '\n') {
                break;
            } else {
                fill++;
            }
        }
        return fill;
    }
    
    private void appendIterated(StringBuilder sb, char input, int count) {
        for(int i = 0; i < count; i++) {
            sb.append(input);
        }
    }
}
