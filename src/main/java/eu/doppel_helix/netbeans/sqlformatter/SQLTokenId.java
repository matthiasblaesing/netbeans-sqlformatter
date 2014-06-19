/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2010 Oracle and/or its affiliates. All rights reserved.
 *
 * Oracle and Java are registered trademarks of Oracle and/or its affiliates.
 * Other names may be trademarks of their respective owners.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common
 * Development and Distribution License("CDDL") (collectively, the
 * "License"). You may not use this file except in compliance with the
 * License. You can obtain a copy of the License at
 * http://www.netbeans.org/cddl-gplv2.html
 * or nbbuild/licenses/CDDL-GPL-2-CP. See the License for the
 * specific language governing permissions and limitations under the
 * License.  When distributing the software, include this License Header
 * Notice in each file and include the License file at
 * nbbuild/licenses/CDDL-GPL-2-CP.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the GPL Version 2 section of the License file that
 * accompanied this code. If applicable, add the following below the
 * License Header, with the fields enclosed by brackets [] replaced by
 * your own identifying information:
 * "Portions Copyrighted [year] [name of copyright owner]"
 *
 * If you wish your version of this file to be governed by only the CDDL
 * or only the GPL Version 2, indicate your decision by adding
 * "[Contributor] elects to include this software in this distribution
 * under the [CDDL or GPL Version 2] license." If you do not indicate a
 * single choice of license, a recipient has the option to distribute
 * your version of this file under either the CDDL, the GPL Version 2 or
 * to extend the choice of license to its licensees as provided above.
 * However, if you add GPL Version 2 code and therefore, elected the GPL
 * Version 2 license, then the option applies only if the new code is
 * made subject to such option by the copyright holder.
 *
 * Contributor(s):
 *
 * Portions Copyrighted 2008 Sun Microsystems, Inc.
 */

package eu.doppel_helix.netbeans.sqlformatter;

import org.netbeans.api.lexer.TokenId;

/**
 * Mostly a copy from the netbeans sql lexer package
 * 
 * @author Andrei Badea
 */
public enum SQLTokenId implements TokenId {
    WHITESPACE("sql-whitespace"), // NOI18N
    LINE_COMMENT("sql-line-comment"), // NOI18N
    BLOCK_COMMENT("sql-block-comment"), // NOI18N
    STRING("sql-string-literal"), // NOI18N
    INCOMPLETE_STRING("sql-errors"), // NOI18N
    INCOMPLETE_IDENTIFIER("sql-errors"), // NOI18N
    IDENTIFIER("sql-identifier"), // NOI18N
    OPERATOR("sql-operator"), // NOI18N
    LPAREN("sql-operator"), // NOI18N
    RPAREN("sql-operator"), // NOI18N
    DOT("sql-dot"), // NOI18N
    COMMA("sql-operator"), //  // NOI18N XXX or have own category?
    INT_LITERAL("sql-int-literal"),  // NOI18N
    DOUBLE_LITERAL("sql-double-literal"), // NOI18N
    KEYWORD("sql-keyword"); // NOI18N

    private final String primaryCategory;

    private SQLTokenId(String primaryCategory) {
        this.primaryCategory = primaryCategory;

    }

    public String primaryCategory() {
        return primaryCategory;
    }
    
    public boolean matches(TokenId id) {
        return id.name().equals(name());
    }

}
