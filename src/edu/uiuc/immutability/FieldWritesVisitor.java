package edu.uiuc.immutability;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jdt.core.IField;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Assignment;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ExpressionStatement;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.internal.corext.dom.ASTNodes;
import org.eclipse.text.edits.TextEditGroup;

public class FieldWritesVisitor extends ASTVisitor {
	
	private List<ExpressionStatement> removedExpressionStatements;
	
	private ASTRewrite rewriter;
	TextEditGroup editGroup;
	
	public FieldWritesVisitor(ASTRewrite rewriter, TextEditGroup editGroup) {
		this.rewriter = rewriter;
		this.editGroup = editGroup;
		
		removedExpressionStatements = new ArrayList<ExpressionStatement>();
	}
	
	@SuppressWarnings("restriction")
	public boolean visit(Assignment assignment) {
		SimpleName fieldName = null;

		Expression leftHandSide = assignment.getLeftHandSide();
		if (leftHandSide instanceof SimpleName) {
			fieldName = (SimpleName) leftHandSide;
		} else if (leftHandSide instanceof FieldAccess) {
			FieldAccess fieldAccess = (FieldAccess) leftHandSide;
			fieldName = fieldAccess.getName();
		}
		
		if (fieldName != null) {
			ExpressionStatement exprStatementToBeRemoved = 
					(ExpressionStatement) ASTNodes.getParent(fieldName, ExpressionStatement.class);
			rewriter.remove(exprStatementToBeRemoved, editGroup);
			removedExpressionStatements.add(exprStatementToBeRemoved);
		}
		return true;
	}

	public List<ExpressionStatement> getRemovedExpressionStatements() {
		return removedExpressionStatements;
	}
}
