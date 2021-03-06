/*
 * The MIT License
 *
 * Copyright 2014 matthias.
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
 */

package eu.doppel_helix.netbeans.sqlformatter;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import javax.swing.text.BadLocationException;
import static junit.framework.Assert.assertEquals;
import org.junit.Test;
import org.netbeans.api.lexer.Language;
import org.netbeans.api.lexer.Token;
import org.netbeans.api.lexer.TokenHierarchy;
import org.netbeans.api.lexer.TokenSequence;
import org.netbeans.junit.NbTestCase;

/**
 *
 * @author matthias
 */
public class FormatterTest extends NbTestCase {
    private static final Logger LOG = Logger.getLogger(FormatterTest.class.getName());

    
    public FormatterTest() {
        super("Formatter");
    }
    
    private String[] readTestFile(String filename) {
        try (
                InputStream is = FormatterTest.class.getResourceAsStream(filename);
                Reader r = new InputStreamReader(is, "UTF-8")) {
            
            StringBuilder sb = new StringBuilder();
            char[] buffer = new char[1024];
            int read = 0;
            while ((read = r.read(buffer)) > 0) {
                sb.append(buffer, 0, read);
            }
            return sb.toString().split("\n-------------\n");
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }
    
    private List<Token> tokenSequence(String input) {
        Language sqlLanguage = org.netbeans.modules.db.sql.lexer.SQLTokenId.language();
        
        TokenHierarchy th = TokenHierarchy.create(input, sqlLanguage);

        TokenSequence ts = th.tokenSequence();
        
        List<Token> token = new ArrayList<>(ts.tokenCount());
        
        while(ts.moveNext()) {
            token.add(ts.token());
        }
        
        return token;
    }

    @Test
    public void testFormatting() throws BadLocationException, IOException {
        Formatter f = new Formatter(null);
        String[] testSql = readTestFile("input.sql");
        String[] referenceSql = readTestFile("golden.sql");
        assertEquals(testSql.length, referenceSql.length);
        for(int i = 0; i < testSql.length; i++) {
            String reformatted = f.formatSQL(tokenSequence(testSql[i]), 2, true, 8);
            assertEquals("SQL Comparison failed [" + i + "]", referenceSql[i], reformatted);
        }
    }

    @Test
    public void testTabHandling() throws BadLocationException, IOException {
        Formatter f = new Formatter(null);
        String[] testSql = readTestFile("tab_replacement_input.sql");
        String[] referenceSql = readTestFile("tab_replacement_golden.sql");
        assertEquals(testSql.length, referenceSql.length);
        // The first SQL should contain one tab as indent (where indent level is 2,
        // seventh line in formatted output)
        String reformatted = f.formatSQL(tokenSequence(testSql[0]), 4, false, 8);
        assertEquals(referenceSql[0], reformatted);
        // The second SQL should contain eight spaces as indent (where indent level is 2,
        // seventh line in formatted output)
        reformatted = f.formatSQL(tokenSequence(testSql[1]), 4, true, 8);
        assertEquals(referenceSql[1], reformatted);
        // The second SQL should contain one tab and four spaces as indent 
        // (where indent level is 3, eighth line in formatted output)
        reformatted = f.formatSQL(tokenSequence(testSql[2]), 4, false, 8);
        assertEquals(referenceSql[2], reformatted);
    }
    
    @Test
    public void testLineLengthCalculation() {
        StringBuilder sb = new StringBuilder("\ntest");
        assertEquals(4, Formatter.currentLineFill(sb, 4, '\u0000'));
        sb = new StringBuilder("\ntest\u0000");
        assertEquals(8, Formatter.currentLineFill(sb, 4, '\u0000'));
        sb = new StringBuilder("\n\u0000test");
        assertEquals(8, Formatter.currentLineFill(sb, 4, '\u0000'));
        sb = new StringBuilder("uselessfill\n\u0000\u0000te");
        assertEquals(10, Formatter.currentLineFill(sb, 4, '\u0000'));
    }
    
    @Test
    public void testOperatorHandling() {
        // Verify correct operator handling the netbeans lexer lexed the not-equal
        // operator <> into two tokens ("<" and ">") - fixes for this problem
        // should get into netbeans 8.0.1/8.1 (however it will be called)
        Formatter f = new Formatter(null);
        String[] testSql = readTestFile("operator_input.sql");
        String[] referenceSql = readTestFile("operator_golden.sql");
        assertEquals(testSql.length, referenceSql.length);
        for (int i = 0; i < testSql.length; i++) {
            String reformatted = f.formatSQL(tokenSequence(testSql[i]), 4, true, 8);
            assertEquals("SQL Comparison failed [" + i + "]", referenceSql[i], reformatted);
        }
    }
}
