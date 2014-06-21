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
            String reformatted = f.formatSQL(tokenSequence(testSql[i]));
            assertEquals("SQL Comparison failed [" + i + "]", referenceSql[i], reformatted);
        }
    } 
    
}
